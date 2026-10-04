const { spawn, execFile } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');

function formatBytes(bytes) {
  if (!bytes || bytes === 0 || isNaN(bytes)) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatDuration(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00';
  const s = Math.floor(seconds);
  const hrs = Math.floor(s / 3600);
  const mins = Math.floor((s % 3600) / 60);
  const secs = s % 60;
  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

class MediaDownloader {
  constructor(options = {}) {
    this.streamServer = options.streamServer || null;
    this.onEvent = options.onEvent || (() => {});
    this.activeJobs = new Map(); // jobId -> Job
    this.completedJobs = [];
    this.binaries = {
      ytdlpPath: null,
      ffmpegPath: null,
      version: null,
      ready: false
    };
    this.appBinDir = path.join(
      process.env.APPDATA || (process.platform === 'darwin' ? path.join(os.homedir(), 'Library', 'Application Support') : path.join(os.homedir(), '.config')),
      'rirdrop',
      'bin'
    );
    this.init();
  }

  setStreamServer(server) {
    this.streamServer = server;
  }

  init() {
    try {
      if (!fs.existsSync(this.appBinDir)) {
        fs.mkdirSync(this.appBinDir, { recursive: true });
      }
    } catch (_) {}
    this.detectBinaries();
  }

  detectBinaries() {
    let ytdlp = null;
    let ffmpeg = null;

    // 1. Check local app bin directory
    const localExt = process.platform === 'win32' ? '.exe' : '';
    const localYtdlp = path.join(this.appBinDir, `yt-dlp${localExt}`);
    const localFfmpeg = path.join(this.appBinDir, `ffmpeg${localExt}`);

    if (fs.existsSync(localYtdlp)) ytdlp = localYtdlp;
    if (fs.existsSync(localFfmpeg)) ffmpeg = localFfmpeg;

    // 2. Check bundled resources/assets in app (Windows and Linux builds)
    if (!ytdlp) {
      const candidates = [
        path.join(process.resourcesPath || '', 'app.asar.unpacked', 'assets', 'bin', `yt-dlp${localExt}`),
        path.join(__dirname, '..', '..', 'assets', 'bin', `yt-dlp${localExt}`),
        path.join(process.resourcesPath || '', 'assets', 'bin', `yt-dlp${localExt}`),
        path.join(path.dirname(process.execPath || ''), 'resources', 'app.asar.unpacked', 'assets', 'bin', `yt-dlp${localExt}`),
        path.join(path.dirname(process.execPath || ''), 'assets', 'bin', `yt-dlp${localExt}`)
      ];
      for (const p of candidates) {
        if (p && fs.existsSync(p)) {
          ytdlp = p;
          break;
        }
      }
    }

    if (!ffmpeg) {
      const candidates = [
        path.join(process.resourcesPath || '', 'app.asar.unpacked', 'assets', 'bin', `ffmpeg${localExt}`),
        path.join(__dirname, '..', '..', 'assets', 'bin', `ffmpeg${localExt}`),
        path.join(process.resourcesPath || '', 'assets', 'bin', `ffmpeg${localExt}`)
      ];
      for (const p of candidates) {
        if (p && fs.existsSync(p)) {
          ffmpeg = p;
          break;
        }
      }
    }

    // 3. Check system PATH
    const systemCommands = process.platform === 'win32'
      ? ['where yt-dlp', 'where ffmpeg']
      : ['which yt-dlp', 'which ffmpeg'];

    try {
      const { execSync } = require('child_process');
      if (!ytdlp) {
        try {
          const out = execSync(systemCommands[0], { stdio: ['ignore', 'pipe', 'ignore'], encoding: 'utf8' }).trim();
          if (out) ytdlp = out.split('\n')[0].trim();
        } catch (_) {}
      }
      if (!ffmpeg) {
        try {
          const out = execSync(systemCommands[1], { stdio: ['ignore', 'pipe', 'ignore'], encoding: 'utf8' }).trim();
          if (out) ffmpeg = out.split('\n')[0].trim();
        } catch (_) {}
      }
    } catch (_) {}

    this.binaries.ytdlpPath = ytdlp;
    this.binaries.ffmpegPath = ffmpeg;
    this.binaries.ready = !!ytdlp;

    if (ytdlp) {
      try {
        const { execSync } = require('child_process');
        this.binaries.version = execSync(`"${ytdlp}" --version`, { stdio: ['ignore', 'pipe', 'ignore'], encoding: 'utf8' }).trim();
      } catch (_) {
        this.binaries.version = 'Detected';
      }
    }

    return this.getStatus();
  }

  getStatus() {
    const jobs = Array.from(this.activeJobs.values()).map(j => this.getJobPayload(j));
    return {
      available: !!this.binaries.ytdlpPath,
      ytdlpPath: this.binaries.ytdlpPath,
      ffmpegPath: this.binaries.ffmpegPath,
      hasFfmpeg: !!this.binaries.ffmpegPath,
      version: this.binaries.version || 'Not installed',
      activeDownloadsCount: this.activeJobs.size,
      completedDownloadsCount: this.completedJobs.length,
      jobs,
      completedList: this.getCompletedDownloads().slice(0, 30)
    };
  }

  getCompletedDownloads(customDir = null) {
    const list = [...this.completedJobs];
    const targetDir = customDir || path.join(os.homedir(), 'Downloads', 'RirDrop');
    if (fs.existsSync(targetDir)) {
      try {
        const files = fs.readdirSync(targetDir);
        for (const f of files) {
          const fullPath = path.join(targetDir, f);
          const stat = fs.statSync(fullPath);
          if (stat.isFile() && !f.startsWith('.')) {
            const alreadyInList = list.some(item => item.filePath === fullPath);
            if (!alreadyInList) {
              list.push({
                jobId: 'file_' + Buffer.from(f).toString('base64').substring(0, 10),
                title: f,
                fileName: f,
                filePath: fullPath,
                fileSize: stat.size,
                total: formatBytes(stat.size),
                completedAt: stat.mtimeMs
              });
            }
          }
        }
      } catch (_) {}
    }
    return list.sort((a, b) => (b.completedAt || 0) - (a.completedAt || 0));
  }

  /**
   * Inspect a URL to retrieve metadata without downloading
   */
  async inspect(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') {
      throw new Error('Please provide a valid media URL.');
    }
    const cleanUrl = rawUrl.trim();
    if (!/^https?:\/\//i.test(cleanUrl)) {
      throw new Error('URL must start with http:// or https://');
    }

    let status = this.detectBinaries();
    if (!status.available) {
      try {
        console.log('[MediaDownloader] yt-dlp missing, attempting auto-download...');
        await this.downloadAndInstallEngine();
        status = this.detectBinaries();
      } catch (dlErr) {
        console.warn('[MediaDownloader] Auto-install attempt error:', dlErr.message);
      }
    }
    if (!status.available) {
      throw new Error('yt-dlp engine was not found on your system. Please click "Install Engine" on the Downloader page.');
    }

    const args = [
      '--dump-json',
      '--no-playlist',
      '--no-warnings',
      cleanUrl
    ];

    return new Promise((resolve, reject) => {
      let stdout = '';
      let stderr = '';
      const child = spawn(this.binaries.ytdlpPath, args);

      const timeout = setTimeout(() => {
        child.kill();
        reject(new Error('Inspection timed out after 25 seconds. Please verify the URL.'));
      }, 25000);

      child.stdout.on('data', (d) => {
        stdout += d.toString();
      });

      child.stderr.on('data', (d) => {
        stderr += d.toString();
      });

      child.on('close', (code) => {
        clearTimeout(timeout);
        if (code !== 0) {
          const errMsg = stderr.trim().split('\n').filter(l => l.includes('ERROR:') || l.includes('Error')).join(' ') || stderr.trim() || `Inspection failed (code ${code})`;
          return reject(new Error(errMsg));
        }

        try {
          const raw = JSON.parse(stdout);
          const metadata = {
            id: raw.id || 'media',
            title: raw.title || path.basename(cleanUrl) || 'Media Download',
            thumbnail: raw.thumbnail || (raw.thumbnails && raw.thumbnails.length > 0 ? raw.thumbnails[raw.thumbnails.length - 1].url : null),
            duration: raw.duration || 0,
            durationFormatted: formatDuration(raw.duration),
            uploader: raw.uploader || raw.channel || raw.creator || raw.extractor_key || 'Web Video',
            extractor: raw.extractor_key || raw.extractor || 'Web',
            webpageUrl: raw.webpage_url || cleanUrl,
            description: (raw.description || '').substring(0, 200),
            formatsAvailable: {
              best: true,
              p720: true,
              mp3: true
            }
          };
          resolve(metadata);
        } catch (err) {
          reject(new Error('Failed to parse media metadata: ' + err.message));
        }
      });

      child.on('error', (err) => {
        clearTimeout(timeout);
        reject(err);
      });
    });
  }

  /**
   * Start a download job
   */
  async startDownload({ jobId, url, preset = 'best', targetDir, autoShare = true }) {
    if (!jobId) jobId = 'dl_' + Math.random().toString(36).substring(2, 10);
    const cleanUrl = url.trim();

    const status = this.detectBinaries();
    if (!status.available) {
      throw new Error('yt-dlp engine is not available on this machine.');
    }

    const downloadDir = targetDir && fs.existsSync(targetDir)
      ? targetDir
      : path.join(os.homedir(), 'Downloads', 'RirDrop');

    try {
      if (!fs.existsSync(downloadDir)) {
        fs.mkdirSync(downloadDir, { recursive: true });
      }
    } catch (_) {}

    // Output template
    const outTemplate = path.join(downloadDir, '%(title).150B [%(id)s].%(ext)s');

    // Build arguments
    const args = [
      '--newline',
      '--no-playlist',
      '--progress-template',
      '%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s|%(progress._downloaded_bytes_str)s|%(progress._total_bytes_str)s',
      '-o',
      outTemplate
    ];

    if (this.binaries.ffmpegPath) {
      args.push('--ffmpeg-location', path.dirname(this.binaries.ffmpegPath));
    }

    if (preset === 'mp3') {
      args.push('-x', '--audio-format', 'mp3', '--audio-quality', '0');
    } else if (preset === '720p') {
      args.push(
        '-f',
        'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720][ext=mp4]/best[height<=720]/best',
        '--merge-output-format',
        'mp4'
      );
    } else {
      // 'best' preset
      args.push(
        '-f',
        'bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best',
        '--merge-output-format',
        'mp4'
      );
    }

    args.push(cleanUrl);

    const job = {
      jobId,
      url: cleanUrl,
      preset,
      targetDir: downloadDir,
      autoShare,
      status: 'starting',
      percent: 0,
      speed: '0 B/s',
      eta: '--:--',
      downloaded: '0 B',
      total: '0 B',
      filePath: null,
      fileName: null,
      error: null,
      startedAt: Date.now(),
      completedAt: null,
      process: null
    };

    this.activeJobs.set(jobId, job);
    this.emitEvent('downloader-progress', this.getJobPayload(job));

    const child = spawn(this.binaries.ytdlpPath, args);
    job.process = child;
    job.status = 'downloading';

    let lastDestination = null;
    let finalMergedFile = null;

    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      const lines = text.split('\n');

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        // Progress template check: "percent|speed|eta|downloaded|total"
        if (trimmed.includes('|')) {
          const parts = trimmed.split('|');
          if (parts.length >= 5) {
            const rawPercent = parseFloat(parts[0].replace('%', '').trim());
            job.percent = Number.isFinite(rawPercent) ? Math.min(100, Math.max(0, rawPercent)) : job.percent;
            job.speed = parts[1].trim() !== 'NA' ? parts[1].trim() : job.speed;
            job.eta = parts[2].trim() !== 'NA' ? parts[2].trim() : job.eta;
            job.downloaded = parts[3].trim() !== 'NA' ? parts[3].trim() : job.downloaded;
            job.total = parts[4].trim() !== 'NA' ? parts[4].trim() : job.total;

            this.emitEvent('downloader-progress', this.getJobPayload(job));
            continue;
          }
        }

        // Destination detections
        const destMatch = trimmed.match(/Destination:\s*(.+)$/i);
        if (destMatch && destMatch[1]) {
          lastDestination = destMatch[1].trim();
        }

        const mergeMatch = trimmed.match(/Merging formats into "(.+)"/i);
        if (mergeMatch && mergeMatch[1]) {
          finalMergedFile = mergeMatch[1].trim();
        }

        const extractMatch = trimmed.match(/ExtractAudio\] Destination:\s*(.+)$/i);
        if (extractMatch && extractMatch[1]) {
          finalMergedFile = extractMatch[1].trim();
        }

        const alreadyMatch = trimmed.match(/\[download\]\s*(.+?)\s*has already been downloaded/i);
        if (alreadyMatch && alreadyMatch[1]) {
          finalMergedFile = alreadyMatch[1].trim();
        }
      }
    });

    child.stderr.on('data', (chunk) => {
      const errLine = chunk.toString().trim();
      if (errLine.includes('ERROR:') || errLine.includes('Error')) {
        job.error = errLine;
      }
    });

    child.on('close', (code) => {
      job.completedAt = Date.now();
      job.process = null;

      if (code === 0) {
        job.status = 'completed';
        job.percent = 100;
        job.eta = '00:00';

        // Resolve final file path
        let resolvedPath = finalMergedFile || lastDestination;
        if (!resolvedPath || !fs.existsSync(resolvedPath)) {
          try {
            const files = fs.readdirSync(downloadDir).map(name => {
              const full = path.join(downloadDir, name);
              const stat = fs.statSync(full);
              return { full, name, mtime: stat.mtimeMs, size: stat.size };
            }).sort((a, b) => b.mtime - a.mtime);

            if (files.length > 0 && files[0].mtime >= job.startedAt - 2000) {
              resolvedPath = files[0].full;
            }
          } catch (_) {}
        }

        job.filePath = resolvedPath;
        job.fileName = resolvedPath ? path.basename(resolvedPath) : 'Downloaded File';

        let fileStat = null;
        if (resolvedPath && fs.existsSync(resolvedPath)) {
          try {
            fileStat = fs.statSync(resolvedPath);
            job.total = formatBytes(fileStat.size);
          } catch (_) {}
        }

        // Automatically share to RirDrop LAN stream & Quick Drop if enabled
        let quickDropInfo = null;
        if (autoShare && resolvedPath && fs.existsSync(resolvedPath) && this.streamServer) {
          try {
            quickDropInfo = this.streamServer.addQuickDropFile(resolvedPath);
          } catch (err) {
            console.warn('Failed to auto-mount downloaded file to quick drop:', err);
          }
        }

        this.activeJobs.delete(jobId);
        this.completedJobs.unshift({
          ...this.getJobPayload(job),
          fileSize: fileStat ? fileStat.size : 0,
          quickDropInfo
        });
        if (this.completedJobs.length > 30) this.completedJobs.pop();

        this.emitEvent('downloader-complete', {
          ...this.getJobPayload(job),
          fileSize: fileStat ? fileStat.size : 0,
          quickDropInfo
        });

      } else {
        job.status = 'error';
        this.activeJobs.delete(jobId);
        this.emitEvent('downloader-error', {
          jobId,
          error: job.error || `Download failed with exit code ${code}`
        });
      }
    });

    child.on('error', (err) => {
      job.status = 'error';
      job.error = err.message;
      this.activeJobs.delete(jobId);
      this.emitEvent('downloader-error', {
        jobId,
        error: err.message
      });
    });

    return this.getJobPayload(job);
  }

  cancelDownload(jobId) {
    const job = this.activeJobs.get(jobId);
    if (!job) return false;

    if (job.process) {
      try {
        job.process.kill('SIGTERM');
      } catch (_) {
        try { job.process.kill('SIGKILL'); } catch (_) {}
      }
    }

    job.status = 'cancelled';
    this.activeJobs.delete(jobId);
    this.emitEvent('downloader-cancelled', { jobId });
    return true;
  }

  getJobPayload(job) {
    return {
      jobId: job.jobId,
      url: job.url,
      preset: job.preset,
      status: job.status,
      percent: job.percent,
      speed: job.speed,
      eta: job.eta,
      downloaded: job.downloaded,
      total: job.total,
      filePath: job.filePath,
      fileName: job.fileName,
      error: job.error,
      startedAt: job.startedAt,
      completedAt: job.completedAt
    };
  }

  emitEvent(name, data) {
    try {
      this.onEvent(name, data);
    } catch (_) {}
  }

  /**
   * Download and install yt-dlp binary from GitHub Releases
   */
  downloadAndInstallEngine(onProgress = () => {}) {
    return new Promise((resolve, reject) => {
      const ext = process.platform === 'win32' ? '.exe' : '';
      const url = process.platform === 'win32'
        ? 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe'
        : 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp';

      const destFile = path.join(this.appBinDir, `yt-dlp${ext}`);
      const tmpFile = destFile + '.tmp';

      if (!fs.existsSync(this.appBinDir)) {
        fs.mkdirSync(this.appBinDir, { recursive: true });
      }

      const downloadFile = (targetUrl, redirectCount = 0) => {
        if (redirectCount > 5) {
          return reject(new Error('Too many redirects while downloading yt-dlp engine.'));
        }

        const req = https.get(targetUrl, { headers: { 'User-Agent': 'RirDrop-Downloader/1.0' } }, (res) => {
          if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
            return downloadFile(res.headers.location, redirectCount + 1);
          }

          if (res.statusCode !== 200) {
            return reject(new Error(`Failed to download yt-dlp: HTTP ${res.statusCode}`));
          }

          const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
          let receivedBytes = 0;
          const fileStream = fs.createWriteStream(tmpFile);

          res.on('data', (chunk) => {
            receivedBytes += chunk.length;
            const pct = totalBytes > 0 ? Math.round((receivedBytes / totalBytes) * 100) : 0;
            onProgress({
              percent: pct,
              receivedBytes,
              totalBytes,
              downloadedStr: formatBytes(receivedBytes),
              totalStr: formatBytes(totalBytes)
            });
          });

          res.pipe(fileStream);

          fileStream.on('finish', () => {
            fileStream.close(() => {
              try {
                if (fs.existsSync(destFile)) {
                  fs.unlinkSync(destFile);
                }
                fs.renameSync(tmpFile, destFile);
                if (process.platform !== 'win32') {
                  fs.chmodSync(destFile, 0o755);
                }
                this.detectBinaries();
                resolve({ success: true, path: destFile, version: this.binaries.version });
              } catch (err) {
                reject(err);
              }
            });
          });

          fileStream.on('error', (err) => {
            try { fs.unlinkSync(tmpFile); } catch (_) {}
            reject(err);
          });
        });

        req.on('error', (err) => {
          try { fs.unlinkSync(tmpFile); } catch (_) {}
          reject(err);
        });

        req.setTimeout(60000, () => {
          req.destroy(new Error('Download timed out after 60 seconds.'));
        });
      };

      downloadFile(url);
    });
  }

  /**
   * Run yt-dlp -U to keep extractors up to date, or auto-install if missing
   */
  async updateEngine() {
    const status = this.detectBinaries();
    if (!status.available) {
      console.log('[MediaDownloader] Engine missing, installing newest release...');
      await this.downloadAndInstallEngine();
      this.detectBinaries();
      return 'yt-dlp media engine installed successfully!';
    }

    return new Promise((resolve) => {
      execFile(this.binaries.ytdlpPath, ['-U'], async (err, stdout, stderr) => {
        if (err) {
          // If in-place update fails (e.g. read-only bundled binary), fallback to downloading new binary in appBinDir
          try {
            console.log('[MediaDownloader] yt-dlp -U failed, falling back to clean binary download...');
            await this.downloadAndInstallEngine();
            this.detectBinaries();
            return resolve('Engine updated successfully to the latest release.');
          } catch (dlErr) {
            return resolve(stderr.trim() || stdout.trim() || err.message);
          }
        }
        this.detectBinaries();
        resolve(stdout.trim() || 'Engine updated successfully.');
      });
    });
  }
}

module.exports = MediaDownloader;

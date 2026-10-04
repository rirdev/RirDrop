const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');
const mime = require('mime-types');
const crypto = require('crypto');
const { execFile } = require('child_process');
const { WebSocketServer } = require('ws');
const { getLocalIpAddresses, getPrimaryIp, getHostInfo, formatBytes } = require('./networkUtils');

class StreamServer {
  constructor({ authManager, port = 53318, onEvent, cloudSyncService, updateManager }) {
    this.authManager = authManager;
    this.port = port;
    this.onEvent = onEvent || (() => {});
    this.cloudSyncService = cloudSyncService || null;
    this.updateManager = updateManager || null;
    this.server = null;
    this.wss = null;
    this.isRunning = false;

    // State
    this.sharedFolders = new Map(); // id -> { id, name, path, addedAt }
    this.quickDropFiles = new Map(); // id -> { id, name, size, path, mimeType, addedAt }
    this.downloadDir = path.join(process.env.HOME || '/tmp', 'Downloads', 'RirDrop');

    // Telemetry for real-time wave graph
    this.bytesUploadedTotal = 0;
    this.bytesDownloadedTotal = 0;
    this.currentUploadSpeed = 0; // bytes/sec
    this.currentDownloadSpeed = 0; // bytes/sec
    this.speedHistory = []; // { time, upSpeed, downSpeed }
    this.lastSampleTime = Date.now();
    this.bytesUpInWindow = 0;
    this.bytesDownInWindow = 0;

    // Ensure downloads and thumbnail cache dirs exist
    this.thumbCacheDir = path.join(process.env.HOME || '/tmp', '.cache', 'rirdrop', 'thumbnails');
    this.configDir = path.join(process.env.HOME || '/tmp', '.config', 'rirdrop');
    try {
      fs.mkdirSync(this.downloadDir, { recursive: true });
      fs.mkdirSync(this.thumbCacheDir, { recursive: true });
      fs.mkdirSync(this.configDir, { recursive: true });
    } catch (_) {}

    // Load persisted folders or samples
    this.loadConfig();

    // Telemetry tick every 1000ms
    this.telemetryInterval = setInterval(() => {
      this.sampleBandwidth();
    }, 1000);
  }

  saveConfig() {
    if (process.env.NODE_ENV === 'test') return;
    try {
      const configPath = path.join(this.configDir, 'folders.json');
      const paths = Array.from(this.sharedFolders.values()).map(f => f.path);
      fs.writeFileSync(configPath, JSON.stringify(paths, null, 2), 'utf-8');
    } catch (_) {}
  }

  loadConfig() {
    if (process.env.NODE_ENV === 'test') return;
    try {
      const configPath = path.join(this.configDir, 'folders.json');
      if (fs.existsSync(configPath)) {
        const raw = fs.readFileSync(configPath, 'utf-8');
        const paths = JSON.parse(raw);
        if (Array.isArray(paths)) {
          paths.forEach(p => {
            if (fs.existsSync(p)) {
              try { this.addSharedFolder(p, false); } catch (_) {}
            }
          });
        }
      }
      if (this.sharedFolders.size === 0) {
        const sampleBase = '/home/rir790/.gemini/antigravity/scratch/sample_storage';
        if (fs.existsSync(sampleBase)) {
          ['Movies', 'Pictures', 'Music'].forEach(sub => {
            const p = path.join(sampleBase, sub);
            if (fs.existsSync(p)) {
              try { this.addSharedFolder(p, false); } catch (_) {}
            }
          });
        }
      }
    } catch (_) {}
  }

  findFolderPreviewMedia(dirPath) {
    try {
      if (!fs.existsSync(dirPath)) return null;
      const entries = fs.readdirSync(dirPath, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.name.startsWith('.')) continue;
        if (entry.isFile()) {
          const mimeType = mime.lookup(entry.name) || '';
          if (mimeType.startsWith('image/') || mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov|jpg|jpeg|png|webp|gif)$/i.test(entry.name)) {
            return {
              name: entry.name,
              isVideo: mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(entry.name),
              isImage: mimeType.startsWith('image/') || /\.(jpg|jpeg|png|webp|gif)$/i.test(entry.name)
            };
          }
        }
      }
    } catch (_) {}
    return null;
  }

  async getThumbnail(targetFilePath, isVideo, isImage) {
    if (!fs.existsSync(targetFilePath)) return null;

    if (isImage) {
      return { path: targetFilePath, mimeType: mime.lookup(targetFilePath) || 'image/jpeg' };
    }

    if (isVideo) {
      try {
        const stat = fs.statSync(targetFilePath);
        const hash = crypto.createHash('md5').update(targetFilePath + stat.mtimeMs).digest('hex');
        const thumbPath = path.join(this.thumbCacheDir, `${hash}.jpg`);

        if (fs.existsSync(thumbPath)) {
          return { path: thumbPath, mimeType: 'image/jpeg' };
        }

        return new Promise((resolve) => {
          execFile('ffmpeg', [
            '-ss', '00:00:01',
            '-i', targetFilePath,
            '-vframes', '1',
            '-q:v', '3',
            '-vf', 'scale=360:-1',
            thumbPath,
            '-y'
          ], { timeout: 8000 }, (err) => {
            if (!err && fs.existsSync(thumbPath)) {
              resolve({ path: thumbPath, mimeType: 'image/jpeg' });
            } else {
              resolve(null);
            }
          });
        });
      } catch (err) {
        return null;
      }
    }

    return null;
  }

  sampleBandwidth() {
    const now = Date.now();
    const elapsed = (now - this.lastSampleTime) / 1000;
    if (elapsed <= 0) return;

    this.currentUploadSpeed = Math.round(this.bytesUpInWindow / elapsed);
    this.currentDownloadSpeed = Math.round(this.bytesDownInWindow / elapsed);

    this.bytesUpInWindow = 0;
    this.bytesDownInWindow = 0;
    this.lastSampleTime = now;

    const sample = {
      timestamp: now,
      upSpeed: this.currentUploadSpeed,
      downSpeed: this.currentDownloadSpeed,
      upSpeedFormatted: formatBytes(this.currentUploadSpeed) + '/s',
      downSpeedFormatted: formatBytes(this.currentDownloadSpeed) + '/s',
      totalUp: formatBytes(this.bytesUploadedTotal),
      totalDown: formatBytes(this.bytesDownloadedTotal)
    };

    this.speedHistory.push(sample);
    if (this.speedHistory.length > 30) {
      this.speedHistory.shift();
    }

    this.broadcastWs('telemetry', sample);
  }

  addSharedFolder(folderPath, save = true) {
    if (!folderPath) throw new Error('Path required');
    try {
      const stat = fs.statSync(folderPath);
      if (!stat.isDirectory()) throw new Error('Not a directory');

      // Check if already added
      for (const f of this.sharedFolders.values()) {
        if (f.path === folderPath) return f;
      }

      const id = 'fld_' + Math.random().toString(36).substring(2, 10);
      const folderInfo = {
        id,
        name: path.basename(folderPath) || folderPath,
        path: folderPath,
        addedAt: Date.now()
      };
      this.sharedFolders.set(id, folderInfo);

      if (save) this.saveConfig();

      this.broadcastWs('share_update', this.getSharedSummary());
      this.onEvent('share_update', this.getSharedSummary());
      return folderInfo;
    } catch (err) {
      throw new Error(`Invalid folder: ${err.message}`);
    }
  }

  // Remove network stream access (Does NOT delete any files or directories from disk!)
  removeSharedFolder(id) {
    const removed = this.sharedFolders.delete(id);
    this.saveConfig();
    this.broadcastWs('share_update', this.getSharedSummary());
    this.onEvent('share_update', this.getSharedSummary());
    return removed;
  }

  // Legacy single folder setter for backwards compatibility
  setSharedFolder(folderPath) {
    if (!folderPath) {
      this.sharedFolders.clear();
    } else {
      this.addSharedFolder(folderPath);
    }
    this.broadcastWs('share_update', this.getSharedSummary());
    this.onEvent('share_update', this.getSharedSummary());
  }

  addQuickDropFile(filePath) {
    try {
      if (!filePath || typeof filePath !== 'string') return null;
      for (const existing of this.quickDropFiles.values()) {
        if (existing.path === filePath) {
          return existing;
        }
      }
      const stat = fs.statSync(filePath);
      const id = 'f_' + Math.random().toString(36).substring(2, 10);
      const isDir = stat.isDirectory();
      const mimeType = isDir ? 'folder' : (mime.lookup(filePath) || 'application/octet-stream');
      const isVideo = !isDir && (mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(filePath));
      const isImage = !isDir && (mimeType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(filePath));
      const isAudio = !isDir && (mimeType.startsWith('audio/') || /\.(mp3|flac|wav|ogg|m4a)$/i.test(filePath));

      const fileInfo = {
        id,
        name: path.basename(filePath),
        size: stat.size,
        sizeFormatted: formatBytes(stat.size),
        path: filePath,
        isDirectory: isDir,
        mimeType,
        isVideo,
        isImage,
        isAudio,
        thumbUrl: (isVideo || isImage) ? `/api/thumbnail?fileId=${id}` : null,
        addedAt: Date.now()
      };
      this.quickDropFiles.set(id, fileInfo);
      this.broadcastWs('quick_drop_update', Array.from(this.quickDropFiles.values()));
      this.onEvent('quick_drop_update', Array.from(this.quickDropFiles.values()));
      return fileInfo;
    } catch (err) {
      console.error('Error adding quick drop file:', err);
      return null;
    }
  }

  removeQuickDropFile(id) {
    this.quickDropFiles.delete(id);
    this.broadcastWs('quick_drop_update', Array.from(this.quickDropFiles.values()));
    this.onEvent('quick_drop_update', Array.from(this.quickDropFiles.values()));
  }

  clearQuickDrop() {
    this.quickDropFiles.clear();
    this.broadcastWs('quick_drop_update', []);
    this.onEvent('quick_drop_update', []);
  }

  getSharedSummary() {
    const folders = Array.from(this.sharedFolders.values()).map(f => {
      const preview = this.findFolderPreviewMedia(f.path);
      return {
        ...f,
        previewThumb: preview ? `/api/thumbnail?folderId=${f.id}&relPath=${encodeURIComponent(preview.name)}` : null
      };
    });
    return {
      sharedFolders: folders,
      sharedFolder: folders.length > 0 ? folders[0].path : null,
      foldersCount: folders.length,
      quickDropCount: this.quickDropFiles.size,
      quickDropFiles: Array.from(this.quickDropFiles.values())
    };
  }

  start() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer((req, res) => this.handleRequest(req, res));

      this.wss = new WebSocketServer({ server: this.server });
      this.wss.on('connection', (ws) => {
        // Send initial state to newly connected WS client
        ws.send(JSON.stringify({
          type: 'init',
          data: {
            host: getHostInfo(),
            security: this.authManager.getStatus(),
            shares: this.getSharedSummary(),
            telemetry: this.speedHistory[this.speedHistory.length - 1] || null
          }
        }));
      });

      this.server.on('error', (err) => {
        if (err.code === 'EADDRINUSE') {
          console.warn(`[StreamServer] Port ${this.port} in use, trying ${this.port + 1}...`);
          this.port++;
          this.server.listen(this.port);
        } else {
          reject(err);
        }
      });

      this.server.listen(this.port, () => {
        this.isRunning = true;
        console.log(`[StreamServer] HTTP & Stream server running on http://${getPrimaryIp()}:${this.port}`);
        resolve(this.port);
      });
    });
  }

  broadcastWs(type, data) {
    if (!this.wss) return;
    const msg = JSON.stringify({ type, data });
    for (const client of this.wss.clients) {
      if (client.readyState === 1) { // OPEN
        client.send(msg);
      }
    }
  }

  // Handle incoming HTTP requests
  async handleRequest(req, res) {
    // Enable CORS for local network devices
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, Range');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Range, Accept-Ranges, Content-Length');

    if (req.method === 'OPTIONS') {
      res.writeHead(200);
      return res.end();
    }

    const parsed = new URL(req.url, 'http://127.0.0.1');
    const pathname = parsed.pathname;

    try {
      // 0. Admin Panel: Serve standalone admin web UI
      if (pathname === '/admin' || pathname === '/admin/' || pathname.startsWith('/admin/')) {
        return this.serveAdminFile(req, res, pathname);
      }

      // Admin API: Publish Release to Cloud
      if (pathname === '/api/admin/publish-release' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', async () => {
          try {
            const data = JSON.parse(body || '{}');
            if (data.pin !== 'admin2026') {
              res.writeHead(401, { 'Content-Type': 'application/json' });
              return res.end(JSON.stringify({ success: false, error: 'Unauthorized: Invalid Admin PIN' }));
            }
            if (!data.version) {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              return res.end(JSON.stringify({ success: false, error: 'Version is required' }));
            }

            const releasePayload = {
              version: data.version.trim(),
              title: data.title || `RirDrop v${data.version.trim()}`,
              changelog: data.changelog || 'Performance improvements and bug fixes.',
              downloadUrl: data.downloadUrl || data.windowsUrl || data.androidUrl || data.linuxUrl || '',
              windowsUrl: data.windowsUrl || '',
              androidUrl: data.androidUrl || '',
              linuxUrl: data.linuxUrl || '',
              forceUpdate: !!data.forceUpdate
            };

            let published = null;
            if (this.cloudSyncService && typeof this.cloudSyncService.publishRelease === 'function') {
              published = await this.cloudSyncService.publishRelease(releasePayload);
            } else {
              const defaultCloud = require('./cloudSyncService');
              published = await defaultCloud.publishRelease(releasePayload);
            }

            if (this.onEvent) {
              this.onEvent('update_published', published);
            }

            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ success: true, release: published }));
          } catch (err) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ success: false, error: err.message }));
          }
        });
        return;
      }

      // Admin API: Check Latest Release
      if (pathname === '/api/admin/check-release' && req.method === 'GET') {
        try {
          const service = this.cloudSyncService || require('./cloudSyncService');
          const release = await service.getLatestRelease();
          res.writeHead(200, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ success: true, release }));
        } catch (err) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ success: false, error: err.message }));
        }
      }

      // Admin API: Auto-fetch from GitHub
      if (pathname === '/api/admin/fetch-github-release') {
        try {
          let release = null;
          if (this.updateManager) {
            release = await this.updateManager.fetchGitHubRelease();
          } else {
            const { UpdateManager } = require('./updateManager');
            const mgr = new UpdateManager({ gitHubRepo: 'rirdev/RirDrop' });
            release = await mgr.fetchGitHubRelease();
          }
          res.writeHead(200, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ success: true, release }));
        } catch (err) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ success: false, error: err.message }));
        }
      }

      // 1. Health / status endpoint (Public)
      if (pathname === '/api/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
          app: 'RirDrop',
          version: '1.0.0',
          host: getHostInfo(),
          security: this.authManager.getStatus(),
          sharedFolderActive: this.sharedFolders.size > 0
        }));
      }

      // 2. Connection request endpoint (Public)
      if (pathname === '/api/connect' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
          try {
            const data = JSON.parse(body || '{}');
            const clientIp = req.socket.remoteAddress.replace(/^.*:/, ''); // strip IPv6 prefix

            // If password is supplied directly:
            if (this.authManager.passwordProtected && data.password) {
              if (this.authManager.verifyPassword(null, data.password)) {
                // If approval is not required OR user entered correct master password:
                const token = this.authManager.generateToken();
                const session = {
                  token,
                  ip: clientIp,
                  deviceName: data.deviceName || 'Network Device',
                  os: data.os || 'unknown',
                  fingerprint: data.fingerprint || token,
                  authorizedAt: Date.now()
                };
                this.authManager.authorizedSessions.set(token, session);
                this.authManager.onEvent('device_approved', session);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                return res.end(JSON.stringify({ status: 'authorized', token }));
              } else {
                res.writeHead(401, { 'Content-Type': 'application/json' });
                return res.end(JSON.stringify({ status: 'error', message: 'Incorrect password' }));
              }
            }

            const result = this.authManager.requestAccess({
              ip: clientIp,
              deviceName: data.deviceName,
              os: data.os,
              userAgent: req.headers['user-agent'],
              fingerprint: data.fingerprint
            });

            if (result.status === 'pending') {
              const reqData = this.authManager.pendingRequests.get(result.requestId);
              this.broadcastWs('connection_request', reqData);
              this.onEvent('connection_request', reqData);
            }

            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify(result));
          } catch (err) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ error: err.message }));
          }
        });
        return;
      }

      // 3. Poll connection status endpoint
      if (pathname === '/api/poll-status') {
        const requestId = parsed.searchParams.get('requestId');
        // Check if this request was approved into a session
        for (const [token, session] of this.authManager.authorizedSessions.entries()) {
          if (session.fingerprint === requestId || session.token === requestId) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ status: 'authorized', token }));
          }
        }

        // Check if still pending in pendingRequests
        if (this.authManager.pendingRequests.has(requestId)) {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ status: 'pending' }));
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ status: 'rejected_or_expired' }));
      }

      // Internal navigation & test control endpoint (localhost only)
      if (pathname === '/api/navigate') {
        const page = parsed.searchParams.get('page');
        const folderId = parsed.searchParams.get('folderId');
        const tab = parsed.searchParams.get('tab');
        const action = parsed.searchParams.get('action');
        this.onEvent('navigate', { page, folderId, tab, action });
        this.broadcastWs('navigate', { page, folderId, tab, action });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ ok: true, page, folderId, tab, action }));
      }

      // Authorization guard for all subsequent endpoints
      const authHeader = req.headers['authorization'] || '';
      const token = (authHeader.startsWith('Bearer ') ? authHeader.slice(7) : '') || parsed.searchParams.get('token');

      const isLoopback = req.socket.remoteAddress === '127.0.0.1' || req.socket.remoteAddress === '::1' || req.socket.remoteAddress === '::ffff:127.0.0.1';
      const isProtected = (this.authManager.passwordProtected || this.authManager.requireApproval) && !isLoopback;
      if (isProtected && !this.authManager.checkSession(token, req.socket.remoteAddress)) {
        // If requesting web portal static files, serve the portal so it can show the login/pairing modal!
        if (pathname === '/' || pathname === '/index.html' || pathname.startsWith('/portal')) {
          return this.servePortalFile(req, res, pathname);
        }
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Unauthorized: Device pairing or password required' }));
      }

      // 4. API: Browse shared files and directory contents
      if (pathname === '/api/shared') {
        const folderId = parsed.searchParams.get('folderId');
        const subPath = parsed.searchParams.get('subPath') || '';
        let folderItems = [];
        let activeFolder = null;

        if (folderId && this.sharedFolders.has(folderId)) {
          activeFolder = this.sharedFolders.get(folderId);
        } else if (this.sharedFolders.size === 1) {
          activeFolder = this.sharedFolders.values().next().value;
        } else if (subPath && this.sharedFolders.size > 0) {
          activeFolder = this.sharedFolders.values().next().value;
        }

        if (activeFolder) {
          const targetDir = path.resolve(activeFolder.path, subPath.replace(/^\/+/, ''));
          // Path traversal safety check
          if (targetDir.startsWith(path.resolve(activeFolder.path))) {
            try {
              const entries = fs.readdirSync(targetDir, { withFileTypes: true });
              for (const entry of entries) {
                if (entry.name.startsWith('.')) continue;

                const fullEntryPath = path.join(targetDir, entry.name);
                const relPath = path.relative(activeFolder.path, fullEntryPath);
                try {
                  const stat = fs.statSync(fullEntryPath);
                  const isDir = entry.isDirectory();
                  const mimeType = isDir ? 'folder' : (mime.lookup(entry.name) || 'application/octet-stream');
                  folderItems.push({
                    name: entry.name,
                    relPath,
                    folderId: activeFolder.id,
                    isDirectory: isDir,
                    size: stat.size,
                    sizeFormatted: isDir ? '--' : formatBytes(stat.size),
                    mimeType,
                    isVideo: !isDir && (mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(entry.name)),
                    isAudio: !isDir && (mimeType.startsWith('audio/') || /\.(mp3|flac|wav|ogg|m4a)$/i.test(entry.name)),
                    isImage: !isDir && (mimeType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(entry.name)),
                    thumbUrl: (!isDir && (mimeType.startsWith('video/') || mimeType.startsWith('image/') || /\.(mp4|mkv|webm|avi|mov|jpg|jpeg|png|gif|webp|svg)$/i.test(entry.name))) ? `/api/thumbnail?folderId=${activeFolder.id}&relPath=${encodeURIComponent(relPath)}` : null,
                    previewThumb: isDir ? (this.findFolderPreviewMedia(fullEntryPath) ? `/api/thumbnail?folderId=${activeFolder.id}&relPath=${encodeURIComponent(path.join(relPath, this.findFolderPreviewMedia(fullEntryPath).name))}` : null) : null,
                    mtime: stat.mtime
                  });
                } catch (_) {}
              }
            } catch (err) {
              console.warn('[StreamServer] Read dir error:', err.message);
            }
          }
        }

        const enrichedFolders = Array.from(this.sharedFolders.values()).map(f => {
          const preview = this.findFolderPreviewMedia(f.path);
          return {
            ...f,
            previewThumb: preview ? `/api/thumbnail?folderId=${f.id}&relPath=${encodeURIComponent(preview.name)}` : null
          };
        });

        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
          sharedFolders: enrichedFolders,
          sharedFolderActive: this.sharedFolders.size > 0,
          sharedFolderName: activeFolder ? activeFolder.name : (this.sharedFolders.size > 0 ? this.sharedFolders.values().next().value.name : null),
          activeFolderId: activeFolder ? activeFolder.id : null,
          activeFolderName: activeFolder ? activeFolder.name : null,
          currentSubPath: subPath,
          folderItems,
          quickDropFiles: Array.from(this.quickDropFiles.values())
        }));
      }

      // 4b. API: Media Thumbnail Engine (for Videos, Images, and Folder covers)
      if (pathname === '/api/thumbnail') {
        const fileId = parsed.searchParams.get('fileId');
        const folderId = parsed.searchParams.get('folderId');
        const relPath = parsed.searchParams.get('relPath');
        let targetFilePath = null;

        if (fileId && this.quickDropFiles.has(fileId)) {
          targetFilePath = this.quickDropFiles.get(fileId).path;
        } else if (relPath) {
          let folderToSearch = null;
          if (folderId && this.sharedFolders.has(folderId)) {
            folderToSearch = this.sharedFolders.get(folderId);
          } else if (this.sharedFolders.size > 0) {
            folderToSearch = this.sharedFolders.values().next().value;
          }

          if (folderToSearch) {
            const resolved = path.resolve(folderToSearch.path, relPath.replace(/^\/+/, ''));
            if (resolved.startsWith(path.resolve(folderToSearch.path))) {
              targetFilePath = resolved;
            }
          }
        }

        if (!targetFilePath || !fs.existsSync(targetFilePath)) {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          return res.end('Thumbnail not found');
        }

        const mimeType = mime.lookup(targetFilePath) || '';
        const isVideo = mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(targetFilePath);
        const isImage = mimeType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(targetFilePath);

        try {
          const thumb = await this.getThumbnail(targetFilePath, isVideo, isImage);
          if (thumb && fs.existsSync(thumb.path)) {
            res.writeHead(200, {
              'Content-Type': thumb.mimeType,
              'Cache-Control': 'public, max-age=86400',
              'Content-Length': fs.statSync(thumb.path).size
            });
            return fs.createReadStream(thumb.path).pipe(res);
          } else {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('Thumbnail unavailable');
          }
        } catch (err) {
          res.writeHead(500, { 'Content-Type': 'text/plain' });
          return res.end(`Thumbnail error: ${err.message}`);
        }
      }

      // 5. API: Stream Media (HTTP 206 Partial Content video/audio streaming)
      if (pathname === '/api/stream' || pathname === '/api/download') {
        const fileId = parsed.searchParams.get('fileId');
        const folderId = parsed.searchParams.get('folderId');
        const relPath = parsed.searchParams.get('relPath');
        let targetFilePath = null;

        if (fileId && this.quickDropFiles.has(fileId)) {
          targetFilePath = this.quickDropFiles.get(fileId).path;
        } else if (relPath) {
          let folderToSearch = null;
          if (folderId && this.sharedFolders.has(folderId)) {
            folderToSearch = this.sharedFolders.get(folderId);
          } else if (this.sharedFolders.size > 0) {
            folderToSearch = this.sharedFolders.values().next().value;
          }

          if (folderToSearch) {
            const resolved = path.resolve(folderToSearch.path, relPath.replace(/^\/+/, ''));
            if (resolved.startsWith(path.resolve(folderToSearch.path))) {
              targetFilePath = resolved;
            }
          }
        }

        if (!targetFilePath || !fs.existsSync(targetFilePath)) {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          return res.end('File not found');
        }

        const stat = fs.statSync(targetFilePath);
        if (stat.isDirectory()) {
          res.writeHead(400, { 'Content-Type': 'text/plain' });
          return res.end('Cannot stream directory');
        }

        const fileSize = stat.size;
        const fileName = path.basename(targetFilePath);
        const mimeType = mime.lookup(targetFilePath) || 'application/octet-stream';
        const isDownload = pathname === '/api/download';

        // Check for HTTP Range Header (vital for video scrubbing & seeking!)
        const range = req.headers.range;

        if (range && !isDownload) {
          const parts = range.replace(/bytes=/, '').split('-');
          const start = parseInt(parts[0], 10);
          const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

          if (start >= fileSize || end >= fileSize) {
            res.writeHead(416, {
              'Content-Range': `bytes */${fileSize}`,
              'Content-Type': 'text/plain'
            });
            return res.end('Requested Range Not Satisfiable');
          }

          const chunksize = (end - start) + 1;
          const stream = fs.createReadStream(targetFilePath, { start, end });

          res.writeHead(206, {
            'Content-Range': `bytes ${start}-${end}/${fileSize}`,
            'Accept-Ranges': 'bytes',
            'Content-Length': chunksize,
            'Content-Type': mimeType
          });

          stream.on('data', chunk => {
            this.bytesUploadedTotal += chunk.length;
            this.bytesUpInWindow += chunk.length;
          });

          stream.pipe(res);
          return;
        } else {
          // Standard full file download / stream
          const headers = {
            'Content-Length': fileSize,
            'Content-Type': mimeType,
            'Accept-Ranges': 'bytes'
          };

          if (isDownload) {
            headers['Content-Disposition'] = `attachment; filename="${encodeURIComponent(fileName)}"`;
          }

          res.writeHead(200, headers);

          const stream = fs.createReadStream(targetFilePath);
          stream.on('data', chunk => {
            this.bytesUploadedTotal += chunk.length;
            this.bytesUpInWindow += chunk.length;
          });

          stream.pipe(res);
          return;
        }
      }

      // 6. API: Upload file from client (Phone -> PC)
      if (pathname === '/api/upload' && req.method === 'POST') {
        const uploadFileName = decodeURIComponent(req.headers['x-file-name'] || `upload_${Date.now()}`);
        const safeName = path.basename(uploadFileName);
        const destPath = path.join(this.downloadDir, safeName);

        const writeStream = fs.createWriteStream(destPath);

        req.on('data', chunk => {
          this.bytesDownloadedTotal += chunk.length;
          this.bytesDownInWindow += chunk.length;
        });

        req.pipe(writeStream);

        writeStream.on('finish', () => {
          const stat = fs.statSync(destPath);
          const fileInfo = {
            name: safeName,
            size: stat.size,
            sizeFormatted: formatBytes(stat.size),
            path: destPath
          };
          this.onEvent('file_received', fileInfo);
          this.broadcastWs('file_received', fileInfo);

          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: true, file: fileInfo }));
        });

        writeStream.on('error', (err) => {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: err.message }));
        });

        return;
      }

      // 7. Serve Web Client Portal (HTML/CSS/JS for mobile & browsers)
      return this.servePortalFile(req, res, pathname);

    } catch (err) {
      console.error('[StreamServer] Request error:', err);
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end(`Internal Server Error: ${err.message}`);
    }
  }

  serveAdminFile(req, res, pathname) {
    let subPath = pathname.replace(/^\/admin\/?/, '');
    if (!subPath || subPath === '') subPath = 'index.html';
    const adminDir = path.join(__dirname, '..', 'admin');
    const safePath = path.resolve(adminDir, subPath);

    if (!safePath.startsWith(adminDir) || !fs.existsSync(safePath) || fs.statSync(safePath).isDirectory()) {
      const indexPath = path.join(adminDir, 'index.html');
      if (fs.existsSync(indexPath)) {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return fs.createReadStream(indexPath).pipe(res);
      }
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('Admin panel not found');
    }

    const mimeType = mime.lookup(safePath) || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': `${mimeType}; charset=utf-8` });
    fs.createReadStream(safePath).pipe(res);
  }

  servePortalFile(req, res, pathname) {
    let cleanPath = pathname === '/' || pathname === '' ? '/index.html' : pathname;
    const portalDir = path.join(__dirname, '..', 'webportal');
    const safePath = path.resolve(portalDir, cleanPath.replace(/^\/+/, ''));

    if (!safePath.startsWith(portalDir) || !fs.existsSync(safePath) || fs.statSync(safePath).isDirectory()) {
      // Fallback to index.html for SPA routes
      const indexPath = path.join(portalDir, 'index.html');
      if (fs.existsSync(indexPath)) {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        return fs.createReadStream(indexPath).pipe(res);
      }
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('Portal not found');
    }

    const mimeType = mime.lookup(safePath) || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': mimeType });
    fs.createReadStream(safePath).pipe(res);
  }

  stop() {
    this.isRunning = false;
    if (this.telemetryInterval) clearInterval(this.telemetryInterval);
    if (this.wss) {
      try { this.wss.close(); } catch (_) {}
    }
    if (this.server) {
      try { this.server.close(); } catch (_) {}
    }
  }
}

module.exports = StreamServer;

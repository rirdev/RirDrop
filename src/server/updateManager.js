// ==========================================
// RirDrop Update Engine & Release Manager
// Cloud Broadcast & GitHub Releases Integration
// ==========================================

const https = require('https');
let shell = null;
try {
  const electron = require('electron');
  if (electron && typeof electron === 'object') {
    shell = electron.shell || null;
  }
} catch (_) {}

/**
 * Compare two semver strings: v1 and v2.
 * Returns:
 *   1 if v1 > v2
 *  -1 if v1 < v2
 *   0 if v1 === v2
 */
function compareSemver(v1, v2) {
  if (!v1 || !v2) return 0;
  const clean = v => v.replace(/^v/i, '').trim();
  const p1 = clean(v1).split('.').map(n => parseInt(n, 10) || 0);
  const p2 = clean(v2).split('.').map(n => parseInt(n, 10) || 0);
  const len = Math.max(p1.length, p2.length);

  for (let i = 0; i < len; i++) {
    const a = p1[i] || 0;
    const b = p2[i] || 0;
    if (a > b) return 1;
    if (a < b) return -1;
  }
  return 0;
}

class UpdateManager {
  constructor(options = {}) {
    this.currentVersion = options.currentVersion || '1.0.0';
    this.gitHubRepo = options.gitHubRepo || 'rirdev/RirDrop';
    this.cloudSyncService = options.cloudSyncService || null;
    this.cachedRelease = null;
  }

  setCloudService(service) {
    this.cloudSyncService = service;
  }

  /**
   * Fetch latest release from GitHub API
   */
  async fetchGitHubRelease() {
    return new Promise((resolve) => {
      const url = `https://api.github.com/repos/${this.gitHubRepo}/releases/latest`;
      const req = https.get(url, {
        headers: {
          'User-Agent': 'RirDrop-UpdateManager/1.0',
          'Accept': 'application/vnd.github.v3+json'
        },
        timeout: 5000
      }, (res) => {
        if (res.statusCode !== 200) {
          resolve(null);
          return;
        }

        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
          try {
            const data = JSON.parse(body);
            const version = (data.tag_name || data.name || '').replace(/^v/i, '');
            
            // Extract platform assets
            const downloads = {};
            if (Array.isArray(data.assets)) {
              for (const asset of data.assets) {
                const name = (asset.name || '').toLowerCase();
                const url = asset.browser_download_url;
                if (name.endsWith('.exe')) downloads.windows = url;
                else if (name.endsWith('.apk')) downloads.android = url;
                else if (name.endsWith('.appimage')) downloads.linux_appimage = url;
                else if (name.endsWith('.deb')) downloads.linux_deb = url;
                else if (name.endsWith('.tar.gz') || name.endsWith('.tgz') || name.includes('linux')) downloads.linux = url;
                else if (name.endsWith('.zip')) downloads.general = url;
              }
            }

            resolve({
              source: 'github',
              version: version || '1.0.0',
              title: data.name || `RirDrop v${version}`,
              changelog: data.body || 'Performance improvements and bug fixes.',
              releaseDate: data.published_at ? data.published_at.substring(0, 10) : new Date().toISOString().substring(0, 10),
              htmlUrl: data.html_url,
              downloads
            });
          } catch (_) {
            resolve(null);
          }
        });
      });

      req.on('error', () => resolve(null));
      req.on('timeout', () => {
        req.destroy();
        resolve(null);
      });
    });
  }

  /**
   * Check for updates: checks Cloud Broadcast Database first, then falls back to GitHub Releases
   */
  async checkForUpdates() {
    let latestRelease = null;

    // 1. Check Cloud Broadcast Database
    if (this.cloudSyncService && typeof this.cloudSyncService.getLatestRelease === 'function') {
      try {
        const cloudRelease = await this.cloudSyncService.getLatestRelease();
        if (cloudRelease && cloudRelease.version) {
          latestRelease = {
            ...cloudRelease,
            source: 'cloud'
          };
        }
      } catch (err) {
        console.warn('[UpdateManager] Cloud release check warning:', err.message);
      }
    }

    // 2. Fallback to GitHub Releases if no cloud release or if GitHub has a newer version
    const gitHubRelease = await this.fetchGitHubRelease();
    if (gitHubRelease && gitHubRelease.version) {
      if (!latestRelease || compareSemver(gitHubRelease.version, latestRelease.version) > 0) {
        latestRelease = gitHubRelease;
      }
    }

    if (!latestRelease || !latestRelease.version) {
      return {
        available: false,
        currentVersion: this.currentVersion,
        latestVersion: this.currentVersion,
        checkedAt: Date.now()
      };
    }

    this.cachedRelease = latestRelease;
    const isNewer = compareSemver(latestRelease.version, this.currentVersion) > 0;

    // Resolve download URL for this platform
    const platform = process.platform;
    const downloads = latestRelease.downloads || {
      windows: latestRelease.windowsUrl || '',
      android: latestRelease.androidUrl || '',
      linux: latestRelease.linuxUrl || '',
      general: latestRelease.downloadUrl || ''
    };
    let platformDownloadUrl = null;

    if (platform === 'win32') {
      platformDownloadUrl = downloads.windows || downloads.exe || latestRelease.windowsUrl || latestRelease.downloadUrl;
    } else if (platform === 'linux') {
      platformDownloadUrl = downloads.linux_appimage || downloads.linux_deb || downloads.linux || latestRelease.linuxUrl || latestRelease.downloadUrl;
    } else if (platform === 'darwin') {
      platformDownloadUrl = downloads.mac || downloads.dmg || latestRelease.downloadUrl;
    }

    if (!platformDownloadUrl) {
      platformDownloadUrl = latestRelease.downloadUrl || latestRelease.htmlUrl;
    }

    return {
      available: isNewer,
      currentVersion: this.currentVersion,
      latestVersion: latestRelease.version,
      title: latestRelease.title || `RirDrop v${latestRelease.version}`,
      changelog: latestRelease.changelog || 'Performance improvements and optimizations.',
      releaseDate: latestRelease.releaseDate || new Date().toISOString().substring(0, 10),
      downloadUrl: platformDownloadUrl,
      allDownloads: downloads,
      source: latestRelease.source,
      checkedAt: Date.now()
    };
  }

  /**
   * Launch download / installer in external browser or download manager
   */
  async downloadUpdate(targetUrl) {
    const url = targetUrl || (this.cachedRelease && this.cachedRelease.downloadUrl);
    if (!url) return { ok: false, error: 'No download URL available' };
    try {
      if (shell && shell.openExternal) {
        await shell.openExternal(url);
      }
      return { ok: true, url };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }
}

module.exports = {
  UpdateManager,
  compareSemver
};

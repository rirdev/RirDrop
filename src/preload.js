const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('rirdropAPI', {
  getHostInfo: () => ipcRenderer.invoke('get-host-info'),
  getServerPort: () => ipcRenderer.invoke('get-server-port'),
  getSecurityStatus: () => ipcRenderer.invoke('get-security-status'),
  setRequireApproval: (enable) => ipcRenderer.invoke('set-require-approval', enable),
  setPassword: (password) => ipcRenderer.invoke('set-password', password),

  openFileDialog: () => ipcRenderer.invoke('open-file-dialog'),
  openFolderDialog: () => ipcRenderer.invoke('open-folder-dialog'),

  getPathForFile: (file) => {
    try {
      if (webUtils && typeof webUtils.getPathForFile === 'function') {
        const resolved = webUtils.getPathForFile(file);
        if (resolved) return resolved;
      }
    } catch (e) {
      console.warn('webUtils.getPathForFile error:', e);
    }
    return file ? (file.path || null) : null;
  },

  addQuickDropFile: (fileOrPath) => {
    let targetPath = fileOrPath;
    if (fileOrPath && typeof fileOrPath === 'object') {
      try {
        if (webUtils && typeof webUtils.getPathForFile === 'function') {
          targetPath = webUtils.getPathForFile(fileOrPath);
        }
      } catch (e) {
        console.warn('webUtils.getPathForFile in addQuickDropFile error:', e);
      }
      if (!targetPath && fileOrPath.path) {
        targetPath = fileOrPath.path;
      }
    }
    if (!targetPath) {
      console.warn('addQuickDropFile: could not resolve file path', fileOrPath);
      return Promise.resolve(null);
    }
    return ipcRenderer.invoke('add-quick-drop-file', targetPath);
  },
  removeQuickDropFile: (fileId) => ipcRenderer.invoke('remove-quick-drop-file', fileId),
  getQuickDropFiles: () => ipcRenderer.invoke('get-quick-drop-files'),

  setSharedFolder: (folderPath) => ipcRenderer.invoke('set-shared-folder', folderPath),
  addSharedFolder: (folderPath) => ipcRenderer.invoke('add-shared-folder', folderPath),
  removeSharedFolder: (folderId) => ipcRenderer.invoke('remove-shared-folder', folderId),
  getSharedFolders: () => ipcRenderer.invoke('get-shared-folders'),
  getSharedSummary: () => ipcRenderer.invoke('get-shared-summary'),

  getPendingRequests: () => ipcRenderer.invoke('get-pending-requests'),
  approveDevice: (requestId) => ipcRenderer.invoke('approve-device', requestId),
  rejectDevice: (requestId) => ipcRenderer.invoke('reject-device', requestId),
  getConnectedDevices: () => ipcRenderer.invoke('get-connected-devices'),
  revokeDevice: (id) => ipcRenderer.invoke('revoke-device', id),

  getDiscoveredPeers: () => ipcRenderer.invoke('get-discovered-peers'),
  scanNetwork: () => ipcRenderer.invoke('scan-network'),

  generateQr: (text) => ipcRenderer.invoke('generate-qr', text),
  getDownloadDir: () => ipcRenderer.invoke('get-download-dir'),
  setDownloadDir: (dirPath) => ipcRenderer.invoke('set-download-dir', dirPath),
  setDeviceAlias: (alias) => ipcRenderer.invoke('set-device-alias', alias),
  getFolderContents: (opts) => ipcRenderer.invoke('get-folder-contents', opts),

  openExternalUrl: (url) => ipcRenderer.invoke('open-external-url', url),
  quitApp: () => ipcRenderer.invoke('quit-app'),

  openPath: (targetPath) => ipcRenderer.invoke('open-path', targetPath),
  getStorageUsage: () => ipcRenderer.invoke('get-storage-usage'),
  clearCache: () => ipcRenderer.invoke('clear-cache'),
  getSettings: () => ipcRenderer.invoke('get-settings'),
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),

  // RirDrop Cloud Account & Sync APIs (No third-party backend branding)
  cloudGetStatus: () => ipcRenderer.invoke('cloud-get-status'),
  cloudSignUp: (email, password, displayName) => ipcRenderer.invoke('cloud-sign-up', { email, password, displayName }),
  cloudSignIn: (email, password) => ipcRenderer.invoke('cloud-sign-in', { email, password }),
  cloudSignOut: () => ipcRenderer.invoke('cloud-sign-out'),
  cloudSyncSettings: (settings) => ipcRenderer.invoke('cloud-sync-settings', settings),
  cloudFetchSettings: () => ipcRenderer.invoke('cloud-fetch-settings'),

  // Software Update & Release Management APIs
  checkForUpdates: () => ipcRenderer.invoke('update-check'),
  downloadUpdate: (url) => ipcRenderer.invoke('update-download', url),
  adminPublishRelease: (releaseData) => ipcRenderer.invoke('update-publish-admin', releaseData),
  onUpdateAvailable: (callback) => ipcRenderer.on('update-available', (_, val) => callback(val)),

  // Speed Test & Network Monitor APIs
  checkInternetConnection: () => ipcRenderer.invoke('speedtest-check-internet'),
  startLiveMonitoring: () => ipcRenderer.invoke('speedtest-start-live'),
  pauseLiveMonitoring: () => ipcRenderer.invoke('speedtest-pause-live'),
  startBenchmark: () => ipcRenderer.invoke('speedtest-start-benchmark'),
  cancelBenchmark: () => ipcRenderer.invoke('speedtest-cancel-benchmark'),

  onLiveStats: (callback) => ipcRenderer.on('speedtest-live-stats', (_, val) => callback(val)),
  onBenchmarkProgress: (callback) => ipcRenderer.on('speedtest-benchmark-progress', (_, val) => callback(val)),
  onBenchmarkStatus: (callback) => ipcRenderer.on('speedtest-benchmark-status', (_, val) => callback(val)),
  onBenchmarkDone: (callback) => ipcRenderer.on('speedtest-benchmark-done', (_, val) => callback(val)),
  onBenchmarkError: (callback) => ipcRenderer.on('speedtest-benchmark-error', (_, val) => callback(val)),

  // Universal Media Downloader APIs
  downloaderGetStatus: () => ipcRenderer.invoke('downloader-get-status'),
  downloaderGetCompleted: (dir) => ipcRenderer.invoke('downloader-get-completed', dir),
  downloaderInspect: (url) => ipcRenderer.invoke('downloader-inspect-url', url),
  downloaderStart: (options) => ipcRenderer.invoke('downloader-start-download', options),
  downloaderCancel: (jobId) => ipcRenderer.invoke('downloader-cancel-download', jobId),
  downloaderUpdateEngine: () => ipcRenderer.invoke('downloader-update-engine'),

  onDownloaderProgress: (callback) => ipcRenderer.on('downloader-progress', (_, val) => callback(val)),
  onDownloaderComplete: (callback) => ipcRenderer.on('downloader-complete', (_, val) => callback(val)),
  onDownloaderError: (callback) => ipcRenderer.on('downloader-error', (_, val) => callback(val)),
  onDownloaderCancelled: (callback) => ipcRenderer.on('downloader-cancelled', (_, val) => callback(val)),

  onAuthEvent: (callback) => ipcRenderer.on('auth-event', (_, val) => callback(val)),
  onServerEvent: (callback) => ipcRenderer.on('server-event', (_, val) => callback(val)),
  onDiscoveryEvent: (callback) => ipcRenderer.on('discovery-event', (_, val) => callback(val))
});

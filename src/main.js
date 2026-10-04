const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const mime = require('mime-types');
const QRCode = require('qrcode');
const AuthManager = require('./server/auth');
const StreamServer = require('./server/streamServer');
const DiscoveryEngine = require('./server/discovery');
const { getHostInfo, formatBytes } = require('./server/networkUtils');
const {
  checkInternetConnection,
  LiveNetworkMonitor,
  ActiveSpeedTester
} = require('./server/speedMonitor');
const cloudSyncService = require('./server/cloudSyncService');
const { UpdateManager } = require('./server/updateManager');
const MediaDownloader = require('./server/mediaDownloader');
const packageJson = require('../package.json');

let mainWindow = null;
let authManager = null;
let streamServer = null;
let mediaDownloader = null;
let discoveryEngine = null;
let liveNetworkMonitor = null;
let activeSpeedTester = null;
let updateManager = null;
let actualServerPort = 53318;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1140,
    height: 740,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: '#0e0e11',
    autoHideMenuBar: true,
    title: 'RirDrop',
    icon: path.join(__dirname, '..', 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'client', 'index.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// Initialize server engines
async function startEngines() {
  authManager = new AuthManager((eventType, data) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('auth-event', { type: eventType, data });
    }
    if (streamServer) {
      streamServer.broadcastWs(eventType, data);
    }
  });

  streamServer = new StreamServer({
    authManager,
    port: 53318,
    cloudSyncService,
    onEvent: (eventType, data) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('server-event', { type: eventType, data });
      }
    }
  });

  try {
    actualServerPort = await streamServer.start();
  } catch (err) {
    console.error('Failed to start stream server:', err);
  }

  mediaDownloader = new MediaDownloader({
    streamServer,
    onEvent: (eventType, data) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send(eventType, data);
      }
    }
  });
  streamServer.mediaDownloader = mediaDownloader;

  const host = getHostInfo();
  discoveryEngine = new DiscoveryEngine({
    alias: "JOHN DOE's PC",
    httpPort: actualServerPort,
    onPeersUpdate: (peers) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('discovery-event', peers);
      }
    }
  });

  await discoveryEngine.start();

  liveNetworkMonitor = new LiveNetworkMonitor({
    sampleInterval: 400,
    onStats: (stats) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('speedtest-live-stats', stats);
      }
    }
  });
  liveNetworkMonitor.start();

  updateManager = new UpdateManager({
    currentVersion: packageJson.version || '1.0.0',
    gitHubRepo: 'rirdev/RirDrop',
    cloudSyncService
  });
  streamServer.updateManager = updateManager;

  // Listen for real-time release broadcasts from Admin Panel
  cloudSyncService.onLatestRelease((release) => {
    if (mainWindow && !mainWindow.isDestroyed() && release && release.version) {
      mainWindow.webContents.send('update-available', release);
    }
  });
}

// IPC Handlers
ipcMain.handle('speedtest-check-internet', async () => {
  return await checkInternetConnection();
});

ipcMain.handle('speedtest-start-live', () => {
  if (liveNetworkMonitor) liveNetworkMonitor.resume();
  return true;
});

ipcMain.handle('speedtest-pause-live', () => {
  if (liveNetworkMonitor) liveNetworkMonitor.pause();
  return true;
});

ipcMain.handle('speedtest-start-benchmark', async () => {
  if (activeSpeedTester && activeSpeedTester.isRunning) {
    activeSpeedTester.cancel();
  }
  activeSpeedTester = new ActiveSpeedTester({
    onStatus: (status) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('speedtest-benchmark-status', status);
      }
    },
    onProgress: (progress) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('speedtest-benchmark-progress', progress);
      }
    },
    onFinished: (results) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('speedtest-benchmark-done', results);
      }
    },
    onError: (err) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('speedtest-benchmark-error', err);
      }
    }
  });
  activeSpeedTester.run().catch((err) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('speedtest-benchmark-error', { message: err.message });
    }
  });
  return { started: true };
});

ipcMain.handle('speedtest-cancel-benchmark', () => {
  if (activeSpeedTester) {
    activeSpeedTester.cancel();
    activeSpeedTester = null;
  }
  return true;
});

ipcMain.handle('get-host-info', () => getHostInfo());
ipcMain.handle('get-server-port', () => actualServerPort);

ipcMain.handle('get-security-status', () => authManager.getStatus());
ipcMain.handle('set-require-approval', (_, enable) => {
  authManager.setRequireApproval(enable);
  return authManager.getStatus();
});
ipcMain.handle('set-password', (_, password) => {
  authManager.setPassword(password);
  return authManager.getStatus();
});

ipcMain.handle('open-file-dialog', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openFile', 'multiSelections'],
    title: 'Select Files to Share on RirDrop'
  });
  return result.canceled ? [] : result.filePaths;
});

ipcMain.handle('open-folder-dialog', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory'],
    title: 'Select Folder / Drive to Stream'
  });
  return result.canceled ? null : result.filePaths[0];
});

ipcMain.handle('add-quick-drop-file', (_, filePath) => {
  return streamServer.addQuickDropFile(filePath);
});

ipcMain.handle('remove-quick-drop-file', (_, fileId) => {
  streamServer.removeQuickDropFile(fileId);
  return true;
});

ipcMain.handle('get-quick-drop-files', () => {
  return Array.from(streamServer.quickDropFiles.values());
});

ipcMain.handle('set-shared-folder', (_, folderPath) => {
  streamServer.setSharedFolder(folderPath);
  return streamServer.getSharedSummary();
});

ipcMain.handle('add-shared-folder', (_, folderPath) => {
  return streamServer.addSharedFolder(folderPath);
});

// Revokes stream access from RirDrop (Does NOT delete files or folders on disk!)
ipcMain.handle('remove-shared-folder', (_, folderId) => {
  return streamServer.removeSharedFolder(folderId);
});

ipcMain.handle('get-shared-folders', () => {
  return Array.from(streamServer.sharedFolders.values()).map(f => {
    const preview = streamServer.findFolderPreviewMedia(f.path);
    return {
      ...f,
      previewThumb: preview ? `http://127.0.0.1:${actualServerPort}/api/thumbnail?folderId=${f.id}&relPath=${encodeURIComponent(preview.name)}` : null
    };
  });
});

ipcMain.handle('get-shared-summary', () => {
  return streamServer.getSharedSummary();
});

ipcMain.handle('get-pending-requests', () => {
  return authManager.getPendingList();
});

ipcMain.handle('approve-device', (_, requestId) => {
  return authManager.approveRequest(requestId);
});

ipcMain.handle('reject-device', (_, requestId) => {
  return authManager.rejectRequest(requestId);
});

ipcMain.handle('get-connected-devices', () => {
  return authManager.getConnectedDevices();
});

ipcMain.handle('revoke-device', (_, id) => {
  authManager.revokeDevice(id);
  return true;
});

ipcMain.handle('get-discovered-peers', () => {
  return discoveryEngine.getPeers();
});

ipcMain.handle('scan-network', () => {
  discoveryEngine.scanNow();
  return true;
});

ipcMain.handle('generate-qr', async (_, text) => {
  try {
    return await QRCode.toDataURL(text, { width: 220, margin: 1, color: { dark: '#000000', light: '#ffffff' } });
  } catch (err) {
    console.error('Failed to generate QR:', err);
    return null;
  }
});

ipcMain.handle('get-download-dir', () => {
  return streamServer.downloadDir;
});

ipcMain.handle('set-download-dir', (_, dirPath) => {
  if (dirPath && fs.existsSync(dirPath)) {
    streamServer.downloadDir = dirPath;
  }
  return streamServer.downloadDir;
});

ipcMain.handle('set-device-alias', (_, alias) => {
  if (alias && alias.trim()) {
    discoveryEngine.alias = alias.trim();
  }
  return discoveryEngine.alias;
});

function getDirectorySize(dirPath) {
  let size = 0;
  try {
    if (!fs.existsSync(dirPath)) return 0;
    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dirPath, entry.name);
      if (entry.isDirectory()) {
        size += getDirectorySize(full);
      } else {
        const stat = fs.statSync(full);
        size += stat.size;
      }
    }
  } catch (_) {}
  return size;
}

ipcMain.handle('open-path', async (_, targetPath) => {
  const p = targetPath || streamServer.downloadDir;
  if (p && fs.existsSync(p)) {
    return await shell.openPath(p);
  }
  return false;
});

ipcMain.handle('get-storage-usage', () => {
  const downloadDir = streamServer.downloadDir;
  const cacheDir = path.join(os.homedir(), '.cache', 'rirdrop');
  const downloadSize = getDirectorySize(downloadDir);
  const cacheSize = getDirectorySize(cacheDir);
  return {
    downloadDir,
    downloadSize,
    cacheDir,
    cacheSize,
    totalSize: downloadSize + cacheSize
  };
});

ipcMain.handle('clear-cache', () => {
  const thumbDir = path.join(os.homedir(), '.cache', 'rirdrop', 'thumbnails');
  let clearedFiles = 0;
  try {
    if (fs.existsSync(thumbDir)) {
      const files = fs.readdirSync(thumbDir);
      for (const f of files) {
        fs.unlinkSync(path.join(thumbDir, f));
        clearedFiles++;
      }
    }
  } catch (err) {
    console.warn('Failed to clear cache:', err.message);
  }
  return { ok: true, clearedFiles };
});

const settingsFilePath = path.join(os.homedir(), '.config', 'rirdrop', 'settings.json');
ipcMain.handle('get-settings', () => {
  try {
    if (fs.existsSync(settingsFilePath)) {
      return JSON.parse(fs.readFileSync(settingsFilePath, 'utf8'));
    }
  } catch (_) {}
  return {};
});

ipcMain.handle('save-settings', (_, settings) => {
  try {
    const dir = path.dirname(settingsFilePath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(settingsFilePath, JSON.stringify(settings, null, 2), 'utf8');
    return { ok: true };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

// Cloud Sync & Cross-Device Account IPC Handlers (RirDrop Cloud)
ipcMain.handle('cloud-get-status', () => cloudSyncService.getStatus());
ipcMain.handle('cloud-sign-up', (_, { email, password, displayName }) => cloudSyncService.signUp(email, password, displayName));
ipcMain.handle('cloud-sign-in', (_, { email, password }) => cloudSyncService.signIn(email, password));
ipcMain.handle('cloud-sign-out', () => cloudSyncService.signOut());
ipcMain.handle('cloud-sync-settings', (_, settings) => cloudSyncService.syncSettings(settings));
ipcMain.handle('cloud-fetch-settings', () => cloudSyncService.fetchSettings());

// Software Update & Release Dispatcher IPC Handlers
ipcMain.handle('update-check', async () => {
  return updateManager ? await updateManager.checkForUpdates() : { available: false };
});

ipcMain.handle('update-download', async (_, url) => {
  return updateManager ? await updateManager.downloadUpdate(url) : { ok: false };
});

ipcMain.handle('update-publish-admin', async (_, releaseData) => {
  return await cloudSyncService.publishRelease(releaseData);
});

ipcMain.handle('get-folder-contents', (_, opts) => {
  if (streamServer.sharedFolders.size === 0) return { active: false, items: [], folders: [] };

  let folderId = null;
  let subPath = '';
  if (typeof opts === 'string') {
    subPath = opts;
  } else if (opts && typeof opts === 'object') {
    folderId = opts.folderId || null;
    subPath = opts.subPath || '';
  }

  let folder = null;
  if (folderId && streamServer.sharedFolders.has(folderId)) {
    folder = streamServer.sharedFolders.get(folderId);
  } else {
    folder = streamServer.sharedFolders.values().next().value;
  }

  if (!folder) return { active: false, items: [], folders: [] };

  const targetDir = path.resolve(folder.path, (subPath || '').replace(/^\/+/, ''));
  if (!targetDir.startsWith(path.resolve(folder.path))) {
    return { active: true, items: [], error: 'Path traversal prevented' };
  }

  try {
    const entries = fs.readdirSync(targetDir, { withFileTypes: true });
    const items = [];
    for (const entry of entries) {
      if (entry.name.startsWith('.')) continue;
      const full = path.join(targetDir, entry.name);
      try {
        const stat = fs.statSync(full);
        const isDir = entry.isDirectory();
        const mimeType = isDir ? 'folder' : (mime.lookup(entry.name) || 'application/octet-stream');
        const isVideo = !isDir && (mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(entry.name));
        const isAudio = !isDir && (mimeType.startsWith('audio/') || /\.(mp3|flac|wav|ogg|m4a)$/i.test(entry.name));
        const isImage = !isDir && (mimeType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(entry.name));
        const rel = path.relative(folder.path, full);
        const previewMedia = isDir ? streamServer.findFolderPreviewMedia(full) : null;

        items.push({
          name: entry.name,
          relPath: rel,
          folderId: folder.id,
          isDirectory: isDir,
          size: stat.size,
          sizeFormatted: isDir ? '--' : formatBytes(stat.size),
          mimeType,
          isVideo,
          isAudio,
          isImage,
          thumbUrl: (!isDir && (isVideo || isImage)) ? `http://127.0.0.1:${actualServerPort}/api/thumbnail?folderId=${folder.id}&relPath=${encodeURIComponent(rel)}` : null,
          previewThumb: previewMedia ? `http://127.0.0.1:${actualServerPort}/api/thumbnail?folderId=${folder.id}&relPath=${encodeURIComponent(path.join(rel, previewMedia.name))}` : null
        });
      } catch (_) {}
    }
    return {
      active: true,
      folderId: folder.id,
      folderName: folder.name,
      folderPath: folder.path,
      currentPath: subPath || '',
      items,
      sharedFolders: Array.from(streamServer.sharedFolders.values()).map(f => {
        const preview = streamServer.findFolderPreviewMedia(f.path);
        return {
          ...f,
          previewThumb: preview ? `http://127.0.0.1:${actualServerPort}/api/thumbnail?folderId=${f.id}&relPath=${encodeURIComponent(preview.name)}` : null
        };
      })
    };
  } catch (err) {
    return { active: true, items: [], error: err.message };
  }
});

// ==========================================
// Media Downloader IPC Handlers
// ==========================================
ipcMain.handle('downloader-get-status', () => {
  return mediaDownloader ? mediaDownloader.getStatus() : { available: false };
});

ipcMain.handle('downloader-get-completed', (_, dir) => {
  return mediaDownloader ? mediaDownloader.getCompletedDownloads(dir) : [];
});

ipcMain.handle('downloader-inspect-url', async (_, url) => {
  if (!mediaDownloader) throw new Error('Downloader service not ready.');
  return await mediaDownloader.inspect(url);
});

ipcMain.handle('downloader-start-download', async (_, options) => {
  if (!mediaDownloader) throw new Error('Downloader service not ready.');
  return await mediaDownloader.startDownload(options);
});

ipcMain.handle('downloader-cancel-download', (_, jobId) => {
  if (!mediaDownloader) return false;
  return mediaDownloader.cancelDownload(jobId);
});

ipcMain.handle('downloader-update-engine', async () => {
  if (!mediaDownloader) throw new Error('Downloader service not ready.');
  return await mediaDownloader.updateEngine();
});

ipcMain.handle('open-external-url', (_, url) => {
  shell.openExternal(url);
});

ipcMain.handle('quit-app', () => {
  app.quit();
});

app.whenReady().then(async () => {
  await startEngines();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('will-quit', () => {
  if (liveNetworkMonitor) liveNetworkMonitor.stop();
  if (activeSpeedTester) activeSpeedTester.cancel();
  if (discoveryEngine) discoveryEngine.stop();
  if (streamServer) streamServer.stop();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

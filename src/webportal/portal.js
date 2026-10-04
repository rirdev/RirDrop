let authToken = localStorage.getItem('rirdrop_token') || '';
let currentFolderId = '';
let currentSubPath = '';
let activeTab = 'stream';
let pollInterval = null;
let cachedSharedFolders = [];

// DOM Elements
const authOverlay = document.getElementById('authOverlay');
const authTitle = document.getElementById('authTitle');
const authDesc = document.getElementById('authDesc');
const passwordForm = document.getElementById('passwordForm');
const inputPassword = document.getElementById('inputPassword');
const btnSubmitPassword = document.getElementById('btnSubmitPassword');
const pendingState = document.getElementById('pendingState');
const headerSub = document.getElementById('headerSub');
const streamGrid = document.getElementById('streamGrid');
const quickDropGrid = document.getElementById('quickDropGrid');
const badgeStream = document.getElementById('badgeStream');
const badgeQuick = document.getElementById('badgeQuick');
const btnBackFolder = document.getElementById('btnBackFolder');
const folderBreadcrumb = document.getElementById('folderBreadcrumb');
const mediaModal = document.getElementById('mediaModal');
const mediaViewport = document.getElementById('mediaViewport');
const mediaTitle = document.getElementById('mediaTitle');
const btnCloseMedia = document.getElementById('btnCloseMedia');

// Tab Switching
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    activeTab = btn.getAttribute('data-tab');

    document.querySelectorAll('.tab-view').forEach(v => v.style.display = 'none');
    if (activeTab === 'stream') document.getElementById('viewStream').style.display = 'block';
    if (activeTab === 'quickdrop') document.getElementById('viewQuickDrop').style.display = 'block';
    if (activeTab === 'upload') document.getElementById('viewUpload').style.display = 'block';
  });
});

// SVG Icon Helper
function getSvg(type) {
  if (type === 'folder') {
    return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>`;
  }
  if (type === 'video') {
    return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`;
  }
  if (type === 'audio') {
    return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>`;
  }
  if (type === 'image') {
    return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>`;
  }
  return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>`;
}

function getAuthUrl(url) {
  if (!url) return '';
  if (!authToken) return url;
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}token=${encodeURIComponent(authToken)}`;
}

// Device Fingerprint generator
function getDeviceFingerprint() {
  let fp = localStorage.getItem('rirdrop_fp');
  if (!fp) {
    fp = 'dev_' + Math.random().toString(36).substring(2, 12);
    localStorage.setItem('rirdrop_fp', fp);
  }
  return fp;
}

function getDeviceName() {
  const ua = navigator.userAgent;
  if (/Android/i.test(ua)) return 'Android Device';
  if (/iPhone|iPad/i.test(ua)) return 'Apple Device';
  if (/Windows/i.test(ua)) return 'Windows PC';
  if (/Mac/i.test(ua)) return 'Mac Device';
  if (/Linux/i.test(ua)) return 'Linux Device';
  return 'Web Client';
}

// Check initial status and authenticate if needed
async function checkAuthAndLoad() {
  try {
    const res = await fetch('/api/status');
    const status = await res.json();
    headerSub.textContent = `${status.host.hostname} • ${status.host.primaryIp}`;

    const isProtected = status.security.passwordProtected || status.security.requireApproval;
    if (!isProtected) {
      authOverlay.style.display = 'none';
      loadSharedData();
      return;
    }

    // Try with existing token
    if (authToken) {
      const testRes = await fetch('/api/shared', {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      if (testRes.status === 200) {
        authOverlay.style.display = 'none';
        loadSharedData();
        return;
      }
    }

    // Must initiate connection request
    initiateConnectionRequest(status.security);
  } catch (err) {
    console.error('Failed to query status:', err);
    headerSub.textContent = 'Host offline or unreachable';
  }
}

async function initiateConnectionRequest(security, password = '') {
  authOverlay.style.display = 'flex';

  if (security.passwordProtected && !password) {
    authTitle.textContent = 'Password Required';
    authDesc.textContent = 'The host has protected this session. Please enter the password to access.';
    passwordForm.style.display = 'block';
    pendingState.style.display = 'none';
    return;
  }

  try {
    const res = await fetch('/api/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceName: getDeviceName(),
        os: navigator.platform || 'web',
        fingerprint: getDeviceFingerprint(),
        password: password
      })
    });

    const data = await res.json();

    if (data.status === 'authorized') {
      authToken = data.token;
      localStorage.setItem('rirdrop_token', authToken);
      authOverlay.style.display = 'none';
      loadSharedData();
      if (pollInterval) clearInterval(pollInterval);
      return;
    }

    if (data.status === 'pending') {
      authTitle.textContent = 'Authorization Pending';
      authDesc.textContent = `A connection request was sent to the host for "${getDeviceName()}".`;
      passwordForm.style.display = 'none';
      pendingState.style.display = 'block';

      const reqId = data.requestId;
      if (pollInterval) clearInterval(pollInterval);
      pollInterval = setInterval(async () => {
        try {
          const pollRes = await fetch(`/api/poll-status?requestId=${reqId}`);
          const pollData = await pollRes.json();
          if (pollData.status === 'authorized') {
            clearInterval(pollInterval);
            authToken = pollData.token;
            localStorage.setItem('rirdrop_token', authToken);
            authOverlay.style.display = 'none';
            loadSharedData();
          }
        } catch (_) {}
      }, 1500);
    }
  } catch (err) {
    authDesc.textContent = `Connection error: ${err.message}`;
  }
}

btnSubmitPassword.addEventListener('click', () => {
  const pwd = inputPassword.value;
  initiateConnectionRequest({ passwordProtected: true }, pwd);
});

// Load shared folders and quick drop items
async function loadSharedData(folderId = '', subPath = '') {
  try {
    let url = `/api/shared?`;
    if (folderId) url += `folderId=${encodeURIComponent(folderId)}&`;
    if (subPath) url += `subPath=${encodeURIComponent(subPath)}&`;

    const res = await fetch(url, {
      headers: authToken ? { 'Authorization': `Bearer ${authToken}` } : {}
    });

    if (res.status === 403) {
      checkAuthAndLoad();
      return;
    }

    const data = await res.json();
    cachedSharedFolders = data.sharedFolders || [];
    currentFolderId = folderId || '';
    currentSubPath = data.currentSubPath || '';

    // Update Badges
    badgeQuick.textContent = data.quickDropFiles ? data.quickDropFiles.length : 0;
    badgeStream.textContent = data.sharedFolders ? data.sharedFolders.length : 0;

    // Render Quick Drop
    renderQuickDrop(data.quickDropFiles || []);

    // Render Stream Folder Explorer
    renderFolderExplorer(data);
  } catch (err) {
    console.error('Error loading shared data:', err);
  }
}

function renderQuickDrop(files) {
  quickDropGrid.innerHTML = '';
  if (files.length === 0) {
    quickDropGrid.innerHTML = '<div style="color:var(--text-muted); padding: 20px;">No quick dropped files yet.</div>';
    return;
  }

  files.forEach(file => {
    const isVideo = file.mimeType.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/i.test(file.name);
    const isImage = file.mimeType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(file.name);
    const isAudio = file.mimeType.startsWith('audio/') || /\.(mp3|flac|wav|ogg|m4a)$/i.test(file.name);
    const streamUrl = `/api/stream?fileId=${file.id}&token=${authToken}`;

    if (isVideo) {
      const card = document.createElement('div');
      card.className = 'file-card media-card';
      const thumbHtml = file.thumbUrl
        ? `<img class="media-thumb-img" src="${getAuthUrl(file.thumbUrl)}" alt="${file.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#111;color:var(--card-purple);">${getSvg('video')}</div>`;

      card.innerHTML = `
        <div class="media-thumb-box" onclick="playMedia('${streamUrl}', '${file.name}', '${file.mimeType}')">
          ${thumbHtml}
          <div class="media-play-overlay">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          </div>
        </div>
        <div class="file-card-top">
          <div class="file-info">
            <div class="file-name" title="${file.name}">${file.name}</div>
            <div class="file-meta">${file.sizeFormatted} • Video</div>
          </div>
        </div>
        <div class="file-actions">
          <button class="btn-action btn-stream" onclick="playMedia('${streamUrl}', '${file.name}', '${file.mimeType}')" style="flex:1; display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
            <span>Stream</span>
          </button>
          <a class="btn-action" href="/api/download?fileId=${file.id}&token=${authToken}" download="${file.name}" style="display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
            <span>Save</span>
          </a>
        </div>
      `;
      quickDropGrid.appendChild(card);
      return;
    }

    if (isImage) {
      const card = document.createElement('div');
      card.className = 'file-card media-card';
      const thumbHtml = file.thumbUrl
        ? `<img class="media-thumb-img" src="${getAuthUrl(file.thumbUrl)}" alt="${file.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#111;color:var(--card-yellow);">${getSvg('image')}</div>`;

      card.innerHTML = `
        <div class="media-thumb-box" onclick="playMedia('${streamUrl}', '${file.name}', '${file.mimeType}')">
          ${thumbHtml}
        </div>
        <div class="file-card-top">
          <div class="file-info">
            <div class="file-name" title="${file.name}">${file.name}</div>
            <div class="file-meta">${file.sizeFormatted} • Image</div>
          </div>
        </div>
        <div class="file-actions">
          <button class="btn-action" onclick="playMedia('${streamUrl}', '${file.name}', '${file.mimeType}')" style="flex:1; display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <span>View</span>
          </button>
          <a class="btn-action" href="/api/download?fileId=${file.id}&token=${authToken}" download="${file.name}" style="display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
            <span>Save</span>
          </a>
        </div>
      `;
      quickDropGrid.appendChild(card);
      return;
    }

    const card = document.createElement('div');
    card.className = 'file-card';
    let iconType = 'doc';
    let iconClass = 'icon-doc';
    if (isAudio) { iconType = 'audio'; iconClass = 'icon-audio'; }

    card.innerHTML = `
      <div class="file-card-top">
        <div class="file-type-icon ${iconClass}">
          ${getSvg(iconType)}
        </div>
        <div class="file-info">
          <div class="file-name" title="${file.name}">${file.name}</div>
          <div class="file-meta">${file.sizeFormatted}</div>
        </div>
      </div>
      <div class="file-actions">
        ${isAudio
          ? `<button class="btn-action btn-stream" onclick="playMedia('${streamUrl}', '${file.name}', '${file.mimeType}')" style="display:inline-flex; align-items:center; gap:6px;">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
              <span>Play</span>
            </button>`
          : ''}
        <a class="btn-action" href="/api/download?fileId=${file.id}&token=${authToken}" download="${file.name}" style="display:inline-flex; align-items:center; gap:6px;">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          <span>Download</span>
        </a>
      </div>
    `;
    quickDropGrid.appendChild(card);
  });
}

function renderFolderExplorer(data) {
  streamGrid.innerHTML = '';
  const folders = data.sharedFolders || [];

  if (!data.sharedFolderActive || folders.length === 0) {
    folderBreadcrumb.textContent = 'Storage Stream is offline';
    streamGrid.innerHTML = '<div style="color:var(--text-muted); padding: 30px; text-align:center;">Host has not mounted any folders or drives yet.</div>';
    btnBackFolder.style.display = 'none';
    return;
  }

  // Case 1: Folder View Grid (Default when no folder is selected)
  if (!currentFolderId) {
    folderBreadcrumb.textContent = `Mounted Folders (${folders.length})`;
    btnBackFolder.style.display = 'none';

    folders.forEach(f => {
      const card = document.createElement('div');
      card.className = 'file-card folder-card-rich';
      card.onclick = () => selectFolderToBrowse(f.id);

      const coverHtml = f.previewThumb
        ? `<img class="folder-cover-img" src="${getAuthUrl(f.previewThumb)}" alt="${f.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div class="folder-cover-placeholder">
             ${getSvg('folder')}
           </div>`;

      card.innerHTML = `
        <div class="folder-cover-wrap">
          ${coverHtml}
        </div>
        <div class="file-card-top" style="padding: 12px 14px 4px;">
          <div class="file-info">
            <div class="file-name" style="font-size:14px; font-weight:700;" title="${f.name}">${f.name}</div>
            <div class="file-meta">Mounted Stream Folder</div>
          </div>
        </div>
        <div class="file-actions" style="padding: 4px 14px 12px;">
          <button class="btn-action" style="width:100%; display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <span>Browse Files</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
          </button>
        </div>
      `;
      streamGrid.appendChild(card);
    });
    return;
  }

  // Case 2: Inside a selected folder -> Stream Files & Media
  const activeFolder = folders.find(f => f.id === currentFolderId);
  const activeName = activeFolder ? activeFolder.name : (data.activeFolderName || 'Storage');
  folderBreadcrumb.textContent = `${activeName}${currentSubPath ? ' / ' + currentSubPath : ''}`;
  btnBackFolder.style.display = 'block';

  if (!data.folderItems || data.folderItems.length === 0) {
    streamGrid.innerHTML = '<div style="color:var(--text-muted); padding: 30px; text-align:center;">This folder is empty.</div>';
    return;
  }

  data.folderItems.forEach(item => {
    // If it's a subfolder
    if (item.isDirectory) {
      const card = document.createElement('div');
      card.className = 'file-card folder-card-rich';
      card.onclick = () => loadSharedData(item.folderId, item.relPath);

      const coverHtml = item.previewThumb
        ? `<img class="folder-cover-img" src="${getAuthUrl(item.previewThumb)}" alt="${item.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div class="folder-cover-placeholder">
             ${getSvg('folder')}
           </div>`;

      card.innerHTML = `
        <div class="folder-cover-wrap" style="height:110px;">
          ${coverHtml}
        </div>
        <div class="file-card-top" style="padding: 10px 12px 4px;">
          <div class="file-info">
            <div class="file-name" title="${item.name}">${item.name}</div>
            <div class="file-meta">Folder</div>
          </div>
        </div>
        <div class="file-actions" style="padding: 4px 12px 10px;">
          <button class="btn-action" style="width:100%; display:inline-flex; align-items:center; justify-content:center; gap:6px;">
            <span>Open</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
          </button>
        </div>
      `;
      streamGrid.appendChild(card);
      return;
    }

    // If it's a Video file
    if (item.isVideo) {
      const card = document.createElement('div');
      card.className = 'file-card media-card';
      const streamUrl = `/api/stream?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}`;

      const thumbHtml = item.thumbUrl
        ? `<img class="media-thumb-img" src="${getAuthUrl(item.thumbUrl)}" alt="${item.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#111;color:var(--card-purple);">${getSvg('video')}</div>`;

      card.innerHTML = `
        <div class="media-thumb-box" onclick="playMedia('${streamUrl}', '${item.name}', '${item.mimeType}')">
          ${thumbHtml}
          <div class="media-play-overlay">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          </div>
        </div>
        <div class="file-card-top" style="padding: 10px 12px 4px;">
          <div class="file-info">
            <div class="file-name" title="${item.name}">${item.name}</div>
            <div class="file-meta">${item.sizeFormatted} • Video</div>
          </div>
        </div>
        <div class="file-actions" style="padding: 4px 12px 10px;">
          <button class="btn-action btn-stream" onclick="playMedia('${streamUrl}', '${item.name}', '${item.mimeType}')" style="flex:1; display:inline-flex; align-items:center; justify-content:center; gap:5px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
            <span>Stream</span>
          </button>
          <a class="btn-action" href="/api/download?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}" download="${item.name}" style="display:inline-flex; align-items:center; justify-content:center; gap:5px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
            <span>Save</span>
          </a>
        </div>
      `;
      streamGrid.appendChild(card);
      return;
    }

    // If it's an Image file
    if (item.isImage) {
      const card = document.createElement('div');
      card.className = 'file-card media-card';
      const streamUrl = `/api/stream?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}`;

      const thumbHtml = item.thumbUrl
        ? `<img class="media-thumb-img" src="${getAuthUrl(item.thumbUrl)}" alt="${item.name}" loading="lazy" onerror="this.style.display='none'">`
        : `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#111;color:var(--card-yellow);">${getSvg('image')}</div>`;

      card.innerHTML = `
        <div class="media-thumb-box" onclick="playMedia('${streamUrl}', '${item.name}', '${item.mimeType}')">
          ${thumbHtml}
        </div>
        <div class="file-card-top" style="padding: 10px 12px 4px;">
          <div class="file-info">
            <div class="file-name" title="${item.name}">${item.name}</div>
            <div class="file-meta">${item.sizeFormatted} • Image</div>
          </div>
        </div>
        <div class="file-actions" style="padding: 4px 12px 10px;">
          <button class="btn-action" onclick="playMedia('${streamUrl}', '${item.name}', '${item.mimeType}')" style="flex:1; display:inline-flex; align-items:center; justify-content:center; gap:5px;">
            <span>View</span>
          </button>
          <a class="btn-action" href="/api/download?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}" download="${item.name}" style="display:inline-flex; align-items:center; justify-content:center; gap:5px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
            <span>Save</span>
          </a>
        </div>
      `;
      streamGrid.appendChild(card);
      return;
    }

    // Other files (audio, documents, zip, etc.)
    const card = document.createElement('div');
    card.className = 'file-card';
    let iconType = 'doc';
    let iconClass = 'icon-doc';
    if (item.isAudio) { iconType = 'audio'; iconClass = 'icon-audio'; }

    card.innerHTML = `
      <div class="file-card-top" onclick="handleItemClick('${item.relPath}', false, '${item.mimeType}', '${item.name}', '${item.folderId}')">
        <div class="file-type-icon ${iconClass}">
          ${getSvg(iconType)}
        </div>
        <div class="file-info">
          <div class="file-name" title="${item.name}">${item.name}</div>
          <div class="file-meta">${item.sizeFormatted}</div>
        </div>
      </div>
      <div class="file-actions">
        ${item.isAudio
          ? `<button class="btn-action btn-stream" onclick="playMedia('/api/stream?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}', '${item.name}', '${item.mimeType}')" style="display:inline-flex; align-items:center; gap:5px;">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
              <span>Play</span>
            </button>`
          : ''}
        <a class="btn-action" href="/api/download?relPath=${encodeURIComponent(item.relPath)}&folderId=${encodeURIComponent(item.folderId)}&token=${authToken}" download="${item.name}" style="display:inline-flex; align-items:center; gap:5px;">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          <span>Save</span>
        </a>
      </div>
    `;
    streamGrid.appendChild(card);
  });
}

function selectFolderToBrowse(folderId) {
  currentFolderId = folderId;
  currentSubPath = '';
  loadSharedData(folderId, '');
}

function handleItemClick(relPath, isDir, mimeType, name, folderId) {
  if (isDir) {
    loadSharedData(folderId, relPath);
  } else if (mimeType.startsWith('video/') || mimeType.startsWith('audio/') || mimeType.startsWith('image/')) {
    playMedia(`/api/stream?relPath=${encodeURIComponent(relPath)}&folderId=${encodeURIComponent(folderId)}&token=${authToken}`, name, mimeType);
  }
}

btnBackFolder.addEventListener('click', () => {
  if (currentSubPath) {
    const parts = currentSubPath.split('/').filter(Boolean);
    parts.pop();
    loadSharedData(currentFolderId, parts.join('/'));
  } else {
    currentFolderId = '';
    currentSubPath = '';
    loadSharedData('', '');
  }
});

// Media Player modal
function playMedia(streamUrl, title, mimeType) {
  mediaTitle.textContent = title;
  mediaViewport.innerHTML = '';

  if (mimeType.startsWith('video/')) {
    const video = document.createElement('video');
    video.src = streamUrl;
    video.controls = true;
    video.autoplay = true;
    video.playsInline = true;
    mediaViewport.appendChild(video);
  } else if (mimeType.startsWith('audio/')) {
    const audio = document.createElement('audio');
    audio.src = streamUrl;
    audio.controls = true;
    audio.autoplay = true;
    mediaViewport.appendChild(audio);
  } else if (mimeType.startsWith('image/')) {
    const img = document.createElement('img');
    img.src = streamUrl;
    mediaViewport.appendChild(img);
  }

  mediaModal.style.display = 'flex';
}

btnCloseMedia.addEventListener('click', () => {
  mediaModal.style.display = 'none';
  mediaViewport.innerHTML = '';
});

// File Upload from client (mobile/browser -> PC)
const dropzone = document.getElementById('uploadDropzone');
const fileInput = document.getElementById('portalFileInput');
const uploadProgress = document.getElementById('uploadProgressContainer');
const uploadFileName = document.getElementById('uploadFileName');
const uploadStatusText = document.getElementById('uploadStatusText');

dropzone.addEventListener('click', () => fileInput.click());
dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
dropzone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropzone.classList.remove('dragover');
  if (e.dataTransfer.files.length > 0) {
    uploadFiles(e.dataTransfer.files);
  }
});

fileInput.addEventListener('change', () => {
  if (fileInput.files.length > 0) {
    uploadFiles(fileInput.files);
  }
});

async function uploadFiles(files) {
  uploadProgress.style.display = 'block';

  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    uploadFileName.textContent = `Uploading ${file.name} (${i + 1}/${files.length})`;
    uploadStatusText.textContent = 'Streaming to PC...';

    try {
      const res = await fetch('/api/upload', {
        method: 'POST',
        headers: {
          'x-file-name': encodeURIComponent(file.name),
          'Authorization': `Bearer ${authToken}`
        },
        body: file
      });
      const data = await res.json();
      if (data.success) {
        uploadStatusText.textContent = `Completed! Saved to Downloads/RirDrop.`;
      }
    } catch (err) {
      uploadStatusText.textContent = `Failed: ${err.message}`;
    }
  }

  setTimeout(() => {
    uploadProgress.style.display = 'none';
  }, 3500);
}

// Initial Boot
checkAuthAndLoad();

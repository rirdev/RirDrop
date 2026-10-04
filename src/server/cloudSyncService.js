// ==========================================
// RirDrop Cloud Sync Service
// Authentication & Cross-Device Synchronization
// ==========================================

const fs = require('fs');
const path = require('path');
const { initializeApp } = require('firebase/app');
const { 
  getAuth, 
  createUserWithEmailAndPassword, 
  signInWithEmailAndPassword, 
  signOut, 
  updateProfile,
  onAuthStateChanged
} = require('firebase/auth');
const { 
  getFirestore, 
  doc, 
  setDoc, 
  getDoc, 
  onSnapshot,
  serverTimestamp 
} = require('firebase/firestore');

const cloudConfig = {
  apiKey: "AIzaSyAobhF4zQlvVoi3GzJVmz2yQUpS9xVT9i8",
  authDomain: "acc-119fc.firebaseapp.com",
  projectId: "acc-119fc",
  storageBucket: "acc-119fc.firebasestorage.app",
  messagingSenderId: "418265134509",
  appId: "1:418265134509:web:e2cf8346916b6082c89d24"
};

class CloudSyncService {
  constructor() {
    this.app = null;
    this.auth = null;
    this.db = null;
    this.currentUser = null;
    this.initialized = false;
    this.releaseListeners = new Set();
    this.localReleasePath = path.join(process.env.HOME || '/tmp', '.config', 'rirdrop', 'latest_release.json');
    this.localCachedRelease = null;
    this.loadLocalRelease();
    this.init();
  }

  loadLocalRelease() {
    try {
      if (fs.existsSync(this.localReleasePath)) {
        const data = fs.readFileSync(this.localReleasePath, 'utf8');
        this.localCachedRelease = JSON.parse(data);
      }
    } catch (_) {}
  }

  saveLocalRelease(release) {
    try {
      const dir = path.dirname(this.localReleasePath);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(this.localReleasePath, JSON.stringify(release, null, 2), 'utf8');
      this.localCachedRelease = release;
    } catch (_) {}
  }

  init() {
    try {
      this.app = initializeApp(cloudConfig);
      this.auth = getAuth(this.app);
      this.db = getFirestore(this.app);
      this.initialized = true;

      onAuthStateChanged(this.auth, (user) => {
        if (user) {
          this.currentUser = {
            uid: user.uid,
            email: user.email,
            displayName: user.displayName || user.email.split('@')[0],
            photoURL: user.photoURL || null
          };
        } else {
          this.currentUser = null;
        }
      });
    } catch (err) {
      console.warn('[CloudSync] Initialization warning:', err.message);
    }
  }

  getStatus() {
    return {
      initialized: this.initialized,
      projectId: cloudConfig.projectId,
      loggedIn: !!this.currentUser,
      user: this.currentUser
    };
  }

  async signUp(email, password, displayName) {
    if (!this.initialized) throw new Error('Cloud sync service is not initialized');
    try {
      const cred = await createUserWithEmailAndPassword(this.auth, email, password);
      const user = cred.user;

      if (displayName) {
        await updateProfile(user, { displayName });
      }

      const userData = {
        uid: user.uid,
        email: user.email,
        displayName: displayName || user.email.split('@')[0],
        photoURL: user.photoURL || null,
        createdAt: serverTimestamp()
      };

      try {
        await setDoc(doc(this.db, 'users', user.uid), userData, { merge: true });
      } catch (fErr) {
        console.warn('[CloudSync] Could not write initial doc:', fErr.message);
      }

      this.currentUser = {
        uid: user.uid,
        email: user.email,
        displayName: displayName || user.email.split('@')[0],
        photoURL: user.photoURL || null
      };

      return { ok: true, user: this.currentUser };
    } catch (err) {
      return { ok: false, error: err.message, code: err.code };
    }
  }

  async signIn(email, password) {
    if (!this.initialized) throw new Error('Cloud sync service is not initialized');
    try {
      const cred = await signInWithEmailAndPassword(this.auth, email, password);
      const user = cred.user;

      this.currentUser = {
        uid: user.uid,
        email: user.email,
        displayName: user.displayName || user.email.split('@')[0],
        photoURL: user.photoURL || null
      };

      let cloudSettings = null;
      try {
        const docSnap = await getDoc(doc(this.db, 'users', user.uid));
        if (docSnap.exists()) {
          cloudSettings = docSnap.data().settings || null;
        }
      } catch (fErr) {
        console.warn('[CloudSync] Failed to fetch settings on login:', fErr.message);
      }

      return { ok: true, user: this.currentUser, settings: cloudSettings };
    } catch (err) {
      return { ok: false, error: err.message, code: err.code };
    }
  }

  async signOut() {
    if (!this.initialized) return { ok: true };
    try {
      await signOut(this.auth);
      this.currentUser = null;
      return { ok: true };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

  async syncSettings(settings) {
    if (!this.initialized || !this.currentUser) {
      return { ok: false, error: 'Not signed in' };
    }
    try {
      const ref = doc(this.db, 'users', this.currentUser.uid);
      await setDoc(ref, {
        settings,
        updatedAt: serverTimestamp()
      }, { merge: true });
      return { ok: true, syncedAt: Date.now() };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

  async fetchSettings() {
    if (!this.initialized || !this.currentUser) {
      return { ok: false, error: 'Not signed in' };
    }
    try {
      const ref = doc(this.db, 'users', this.currentUser.uid);
      const snap = await getDoc(ref);
      if (snap.exists()) {
        const data = snap.data();
        return { ok: true, settings: data.settings || null };
      }
      return { ok: true, settings: null };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

  // ==========================================
  // RELEASE BROADCASTING & NOTIFICATIONS
  // ==========================================

  async publishRelease(releaseData) {
    if (!releaseData || !releaseData.version) throw new Error('Missing release version');

    const cleanVer = releaseData.version.replace(/^v/i, '').trim();
    const payload = {
      version: cleanVer,
      title: releaseData.title || `RirDrop v${cleanVer}`,
      changelog: releaseData.changelog || 'Performance improvements and bug fixes.',
      releaseDate: releaseData.releaseDate || new Date().toISOString().substring(0, 10),
      downloads: releaseData.downloads || {
        windows: releaseData.windowsUrl || '',
        android: releaseData.androidUrl || '',
        linux: releaseData.linuxUrl || '',
        general: releaseData.downloadUrl || ''
      },
      windowsUrl: releaseData.windowsUrl || '',
      androidUrl: releaseData.androidUrl || '',
      linuxUrl: releaseData.linuxUrl || '',
      downloadUrl: releaseData.downloadUrl || releaseData.windowsUrl || releaseData.androidUrl || releaseData.linuxUrl || '',
      forceUpdate: !!releaseData.forceUpdate,
      publishedAt: Date.now(),
      publisher: (this.currentUser && this.currentUser.email) || 'Admin'
    };

    // 1. Immediately save locally & broadcast to in-app listeners
    this.saveLocalRelease(payload);
    for (const cb of this.releaseListeners) {
      try { cb(payload); } catch (_) {}
    }

    // 2. Sync to cloud database if available
    if (this.initialized && this.db) {
      try {
        const ref = doc(this.db, 'system', 'latest_release');
        await setDoc(ref, payload, { merge: true });
      } catch (err) {
        console.warn('[CloudSync] Firestore broadcast push notice:', err.message);
      }
    }

    return { ok: true, release: payload };
  }

  async getLatestRelease() {
    if (this.initialized && this.db) {
      try {
        const ref = doc(this.db, 'system', 'latest_release');
        const snap = await getDoc(ref);
        if (snap.exists()) {
          const data = snap.data();
          this.saveLocalRelease(data);
          return data;
        }
      } catch (err) {
        console.warn('[CloudSync] Firestore getLatestRelease notice:', err.message);
      }
    }
    return this.localCachedRelease;
  }

  onLatestRelease(callback) {
    if (typeof callback !== 'function') return () => {};
    this.releaseListeners.add(callback);

    if (this.localCachedRelease) {
      setTimeout(() => {
        try { callback(this.localCachedRelease); } catch (_) {}
      }, 50);
    }

    let unsubscribe = () => {};
    if (this.initialized && this.db) {
      try {
        const ref = doc(this.db, 'system', 'latest_release');
        unsubscribe = onSnapshot(ref, (snap) => {
          if (snap.exists()) {
            const data = snap.data();
            this.saveLocalRelease(data);
            callback(data);
          }
        }, (err) => {
          console.warn('[CloudSync] onLatestRelease Firestore snapshot notice:', err.message);
        });
      } catch (err) {
        console.warn('[CloudSync] onLatestRelease setup notice:', err.message);
      }
    }

    return () => {
      this.releaseListeners.delete(callback);
      try { unsubscribe(); } catch (_) {}
    };
  }
}

module.exports = new CloudSyncService();

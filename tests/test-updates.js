process.env.NODE_ENV = 'test';
const assert = require('assert');
const http = require('http');
const { UpdateManager, compareSemver } = require('../src/server/updateManager');
const StreamServer = require('../src/server/streamServer');
const AuthManager = require('../src/server/auth');

async function runUpdateTests() {
  console.log('🧪 Starting RirDrop Update System Test Suite...\n');

  // Test 1: Semver Comparison
  console.log('--- Test 1: Semver Comparison Logic ---');
  assert.strictEqual(compareSemver('1.1.0', '1.0.0'), 1, '1.1.0 should be greater than 1.0.0');
  assert.strictEqual(compareSemver('1.0.0', '1.1.0'), -1, '1.0.0 should be less than 1.1.0');
  assert.strictEqual(compareSemver('1.0.0', '1.0.0'), 0, '1.0.0 should equal 1.0.0');
  assert.strictEqual(compareSemver('v1.2.3', '1.2.3'), 0, 'v prefix should be ignored');
  assert.strictEqual(compareSemver('2.0.0', '1.99.99'), 1, 'Major 2.0.0 > 1.99.99');
  assert.strictEqual(compareSemver('1.0.1', '1.0.0'), 1, 'Patch 1.0.1 > 1.0.0');
  console.log('✅ Semver comparison passed all assertions.');

  // Test 2: UpdateManager Mock Cloud Check
  console.log('\n--- Test 2: UpdateManager Cloud Integration ---');
  const mockCloud = {
    cachedRelease: null,
    async getLatestRelease() {
      return this.cachedRelease || {
        version: '1.1.0',
        title: 'RirDrop v1.1.0 - Next-Gen Performance',
        changelog: '• Supercharged LAN throughput\n• Custom user profiles',
        windowsUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.1.0/RirDrop.exe',
        linuxUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.1.0/RirDrop.AppImage',
        androidUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.1.0/RirDrop.apk',
        downloadUrl: 'https://github.com/rirdev/RirDrop/releases/latest',
        publishedAt: Date.now()
      };
    },
    async publishRelease(data) {
      this.cachedRelease = { ...data, publishedAt: Date.now() };
      return this.cachedRelease;
    }
  };

  const updateMgr = new UpdateManager({
    currentVersion: '1.0.0',
    gitHubRepo: 'rirdev/RirDrop',
    cloudSyncService: mockCloud
  });

  const checkResult = await updateMgr.checkForUpdates();
  console.log('Check result:', {
    available: checkResult.available,
    currentVersion: checkResult.currentVersion,
    latestVersion: checkResult.latestVersion,
    downloadUrl: checkResult.downloadUrl
  });

  assert.strictEqual(checkResult.available, true, 'Update should be available');
  assert.strictEqual(checkResult.latestVersion, '1.1.0');
  assert.ok(checkResult.downloadUrl, 'Platform download URL should be resolved');
  console.log('✅ UpdateManager detected and parsed update correctly.');

  // Test 3: StreamServer Admin Endpoints & Security PIN
  console.log('\n--- Test 3: StreamServer Admin Panel & API Endpoints ---');
  const auth = new AuthManager();
  const testPort = 54399;
  const streamServer = new StreamServer({
    authManager: auth,
    port: testPort,
    cloudSyncService: mockCloud,
    updateManager: updateMgr
  });

  await streamServer.start();
  console.log(`StreamServer listening on test port ${testPort}`);

  // 3a. GET /admin -> should serve admin HTML
  const getAdminRes = await new Promise((resolve) => {
    http.get(`http://127.0.0.1:${testPort}/admin`, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
  });
  assert.strictEqual(getAdminRes.status, 200, 'GET /admin should return HTTP 200');
  assert.ok(getAdminRes.body.includes('RirDrop Admin Center'), 'Should serve RirDrop Admin Center HTML');
  console.log('✅ GET /admin served valid HTML interface.');

  // 3b. POST /api/admin/publish-release with wrong PIN -> should reject with 401
  const postBadPinRes = await new Promise((resolve) => {
    const postData = JSON.stringify({ pin: 'wrongpin', version: '1.2.0' });
    const req = http.request({
      hostname: '127.0.0.1',
      port: testPort,
      path: '/api/admin/publish-release',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(body) }));
    });
    req.write(postData);
    req.end();
  });
  assert.strictEqual(postBadPinRes.status, 401, 'Wrong PIN must return 401 Unauthorized');
  assert.strictEqual(postBadPinRes.body.success, false);
  console.log('✅ Admin PIN security protection verified (rejected unauthorized PIN).');

  // 3c. POST /api/admin/publish-release with correct PIN -> should succeed with 200
  const postGoodPinRes = await new Promise((resolve) => {
    const postData = JSON.stringify({
      pin: 'admin2026',
      version: '1.2.0',
      title: 'RirDrop v1.2.0 - Broadcast Test',
      changelog: '• Unit test release verification',
      windowsUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.2.0/RirDrop.exe',
      androidUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.2.0/RirDrop.apk',
      linuxUrl: 'https://github.com/rirdev/RirDrop/releases/download/v1.2.0/RirDrop.AppImage'
    });
    const req = http.request({
      hostname: '127.0.0.1',
      port: testPort,
      path: '/api/admin/publish-release',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(body) }));
    });
    req.write(postData);
    req.end();
  });
  assert.strictEqual(postGoodPinRes.status, 200, 'Correct PIN must publish successfully');
  assert.strictEqual(postGoodPinRes.body.success, true);
  assert.strictEqual(postGoodPinRes.body.release.version, '1.2.0');
  console.log('✅ Admin published release v1.2.0 successfully via API.');

  // 3d. GET /api/admin/check-release -> should return newly published release
  const getReleaseRes = await new Promise((resolve) => {
    http.get(`http://127.0.0.1:${testPort}/api/admin/check-release`, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(body) }));
    });
  });
  assert.strictEqual(getReleaseRes.status, 200);
  assert.strictEqual(getReleaseRes.body.release.version, '1.2.0');
  console.log('✅ GET /api/admin/check-release verified live broadcast state.');

  streamServer.stop();
  console.log('\n🎉 ALL UPDATE SYSTEM TESTS PASSED SUCCESSFULLY!\n');
}

runUpdateTests().catch((err) => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});

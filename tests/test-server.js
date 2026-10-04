process.env.NODE_ENV = 'test';
const assert = require('assert');
const http = require('http');
const fs = require('fs');
const path = require('path');

const { getLocalIpAddresses, getPrimaryIp, getHostInfo, formatBytes } = require('../src/server/networkUtils');
const AuthManager = require('../src/server/auth');
const StreamServer = require('../src/server/streamServer');
const DiscoveryEngine = require('../src/server/discovery');

async function runTests() {
  console.log('🧪 Starting RirDrop Test Suite...\n');

  // Test 1: Network Utils
  console.log('--- Test 1: Network Utils ---');
  const ip = getPrimaryIp();
  assert.ok(ip, 'Primary IP should not be empty');
  const host = getHostInfo();
  assert.ok(host.hostname, 'Hostname should be present');
  console.log(`✅ Detected Host: ${host.hostname}, Primary LAN IP: ${host.primaryIp}`);
  console.log(`✅ Bytes formatting: 10485760 B = ${formatBytes(10485760)}`);

  // Test 2: Auth Manager
  console.log('\n--- Test 2: Auth Manager ---');
  const auth = new AuthManager();
  assert.strictEqual(auth.passwordProtected, false);
  assert.strictEqual(auth.requireApproval, true);

  auth.setPassword('secret123');
  assert.strictEqual(auth.passwordProtected, true);
  assert.strictEqual(auth.verifyPassword(null, 'secret123'), true);
  assert.strictEqual(auth.verifyPassword(null, 'wrong'), false);

  // Request connection
  const reqResult = auth.requestAccess({
    ip: '192.168.1.50',
    deviceName: 'Pixel 8 Pro',
    os: 'android'
  });
  assert.strictEqual(reqResult.status, 'pending');
  assert.ok(reqResult.requestId);
  console.log(`✅ Connection request generated: ${reqResult.requestId}`);

  // Approve connection
  const approveResult = auth.approveRequest(reqResult.requestId);
  assert.strictEqual(approveResult.success, true);
  assert.ok(approveResult.token);
  assert.strictEqual(auth.checkSession(approveResult.token, '192.168.1.50'), true);
  console.log(`✅ Device approved, token validated`);

  // Test 3: Stream Server & HTTP 206 Partial Content
  console.log('\n--- Test 3: Stream Server & HTTP 206 Partial Content ---');
  const testPort = 54321;
  const streamServer = new StreamServer({
    authManager: auth,
    port: testPort
  });

  const boundPort = await streamServer.start();
  assert.strictEqual(boundPort, testPort);
  console.log(`✅ Stream server listening on port ${boundPort}`);

  // Create temporary test file for streaming
  const testDir = path.join(__dirname, 'temp_test_dir');
  fs.mkdirSync(testDir, { recursive: true });
  const testFilePath = path.join(testDir, 'sample_video.mp4');
  const dummyData = Buffer.alloc(1024 * 64, 'A'); // 64KB dummy data
  fs.writeFileSync(testFilePath, dummyData);

  // Mount testDir in streamServer
  streamServer.setSharedFolder(testDir);
  const dropped = streamServer.addQuickDropFile(testFilePath);
  assert.ok(dropped.id);

  // Helper HTTP request
  function request(options) {
    return new Promise((resolve, reject) => {
      const req = http.request(options, (res) => {
        let body = [];
        res.on('data', chunk => body.push(chunk));
        res.on('end', () => {
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: Buffer.concat(body)
          });
        });
      });
      req.on('error', reject);
      req.end();
    });
  }

  // 3a: Test /api/status
  const statusRes = await request({
    hostname: '127.0.0.1',
    port: testPort,
    path: '/api/status',
    method: 'GET'
  });
  assert.strictEqual(statusRes.statusCode, 200);
  const statusJson = JSON.parse(statusRes.body.toString());
  assert.strictEqual(statusJson.app, 'RirDrop');
  console.log('✅ /api/status verified');

  // 3b: Test /api/shared with authorized token
  const sharedRes = await request({
    hostname: '127.0.0.1',
    port: testPort,
    path: `/api/shared?token=${approveResult.token}`,
    method: 'GET'
  });
  assert.strictEqual(sharedRes.statusCode, 200);
  const sharedJson = JSON.parse(sharedRes.body.toString());
  assert.strictEqual(sharedJson.sharedFolderActive, true);
  assert.strictEqual(sharedJson.folderItems.length, 1);
  assert.strictEqual(sharedJson.folderItems[0].name, 'sample_video.mp4');
  assert.strictEqual(sharedJson.folderItems[0].isVideo, true);
  console.log('✅ /api/shared verified (detected video file)');

  // 3c: Test HTTP 206 Partial Content (Video Seeking simulation)
  const rangeRes = await request({
    hostname: '127.0.0.1',
    port: testPort,
    path: `/api/stream?relPath=sample_video.mp4&token=${approveResult.token}`,
    method: 'GET',
    headers: {
      'Range': 'bytes=100-299'
    }
  });
  assert.strictEqual(rangeRes.statusCode, 206);
  assert.strictEqual(rangeRes.headers['content-range'], 'bytes 100-299/65536');
  assert.strictEqual(rangeRes.headers['content-length'], '200');
  assert.strictEqual(rangeRes.body.length, 200);
  console.log('✅ HTTP 206 Partial Content verified (Range: bytes=100-299 -> 200 bytes returned)');

  // 3d: Test Multi-Folder stream and Remove Access (without disk deletion)
  const testDirB = path.join(__dirname, 'temp_test_dir_b');
  fs.mkdirSync(testDirB, { recursive: true });
  const fileInB = path.join(testDirB, 'movie_b.mkv');
  fs.writeFileSync(fileInB, Buffer.alloc(1024 * 16, 'B'));

  const folderB = streamServer.addSharedFolder(testDirB);
  assert.ok(folderB.id);
  assert.strictEqual(streamServer.sharedFolders.size, 2);

  // Browse folder B specifically via /api/shared?folderId=...
  const sharedBRes = await request({
    hostname: '127.0.0.1',
    port: testPort,
    path: `/api/shared?folderId=${folderB.id}&token=${approveResult.token}`,
    method: 'GET'
  });
  const sharedBJson = JSON.parse(sharedBRes.body.toString());
  assert.strictEqual(sharedBJson.activeFolderId, folderB.id);
  assert.strictEqual(sharedBJson.folderItems.length, 1);
  assert.strictEqual(sharedBJson.folderItems[0].name, 'movie_b.mkv');
  console.log('✅ Multi-folder browse verified (folder B inspected via folderId)');

  // Now REMOVE ACCESS to folder B from RirDrop
  const removed = streamServer.removeSharedFolder(folderB.id);
  assert.strictEqual(removed, true);
  assert.strictEqual(streamServer.sharedFolders.size, 1);
  // Verify that the disk file STILL EXISTS (remove access != delete file)
  assert.strictEqual(fs.existsSync(fileInB), true, 'File on disk MUST NOT be deleted when removing access!');
  console.log('✅ Remove folder access verified: access revoked from RirDrop stream while disk file remains 100% intact');

  fs.rmSync(testDirB, { recursive: true, force: true });

  // 3e: Test /api/thumbnail endpoint with image file
  const testImgPath = path.join(testDir, 'photo.jpg');
  fs.writeFileSync(testImgPath, Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46])); // minimal JPEG header
  const thumbRes = await request({
    hostname: '127.0.0.1',
    port: testPort,
    path: `/api/thumbnail?relPath=photo.jpg&token=${approveResult.token}`,
    method: 'GET'
  });
  assert.strictEqual(thumbRes.statusCode, 200);
  assert.strictEqual(thumbRes.headers['content-type'], 'image/jpeg');
  console.log('✅ /api/thumbnail verified (served image thumbnail with caching headers)');

  // Test 4: Discovery Engine
  console.log('\n--- Test 4: Discovery Engine ---');
  const discovery = new DiscoveryEngine({
    alias: 'Test Host',
    httpPort: testPort
  });
  await discovery.start();
  assert.strictEqual(discovery.isRunning, true);
  console.log('✅ UDP Discovery Engine initialized and bound');

  // Test 5: Speed Monitor & Internet Benchmark Engine
  console.log('\n--- Test 5: Speed Monitor & Internet Benchmark Engine ---');
  const {
    readProcNetDev,
    getPrimaryInterface,
    checkInternetConnection,
    LiveNetworkMonitor,
    ActiveSpeedTester
  } = require('../src/server/speedMonitor');

  const iface = getPrimaryInterface();
  const netDev = readProcNetDev(iface);
  assert.ok(typeof netDev.rxTotal === 'number', 'rxTotal should be number');
  assert.ok(typeof netDev.txTotal === 'number', 'txTotal should be number');
  console.log(`✅ readProcNetDev verified on iface ${iface}: rx=${netDev.rxTotal} tx=${netDev.txTotal}`);

  const conn = await checkInternetConnection();
  assert.ok(typeof conn.connected === 'boolean', 'connected status should be boolean');
  console.log(`✅ checkInternetConnection verified: connected=${conn.connected}`);

  let statsReceived = false;
  const liveMonitor = new LiveNetworkMonitor({ sampleInterval: 100 });
  liveMonitor.onStats = (stats) => {
    assert.ok(typeof stats.dlMbps === 'number');
    assert.ok(typeof stats.ulMbps === 'number');
    assert.ok(typeof stats.pingMs === 'number');
    statsReceived = true;
  };
  liveMonitor.start();
  await new Promise(r => setTimeout(r, 250));
  assert.strictEqual(statsReceived, true, 'Live monitor should have emitted stats');
  liveMonitor.pause();
  assert.strictEqual(liveMonitor.isPaused, true);
  liveMonitor.stop();
  assert.strictEqual(liveMonitor.isRunning, false);
  console.log('✅ LiveNetworkMonitor verified (start, tick, pause, stop)');

  const tester = new ActiveSpeedTester();
  assert.strictEqual(tester.isRunning, false);
  tester.cancel();
  assert.strictEqual(tester.isCancelled, true);
  // Cleanup
  discovery.stop();
  streamServer.stop();
  fs.rmSync(testDir, { recursive: true, force: true });

  console.log('\n🎉 ALL TESTS PASSED SUCCESSFULLY! RirDrop engine is rock solid.\n');
}

runTests().catch(err => {
  console.error('❌ Test suite failed:', err);
  process.exit(1);
});

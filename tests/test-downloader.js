const MediaDownloader = require('../src/server/mediaDownloader');
const StreamServer = require('../src/server/streamServer');
const AuthManager = require('../src/server/auth');
const assert = require('assert');
const path = require('path');
const fs = require('fs');

async function runDownloaderTests() {
  console.log('🧪 Starting Media Downloader Test Suite...\n');

  // Test 1: Binary Detection
  console.log('--- Test 1: Binary Detection ---');
  const auth = new AuthManager();
  const server = new StreamServer({ authManager: auth, port: 59995 });
  const downloader = new MediaDownloader({ streamServer: server });

  const status = downloader.getStatus();
  console.log('Detected Binaries:', status);
  assert.strictEqual(status.available, true, 'yt-dlp should be detected on host');
  assert.strictEqual(status.hasFfmpeg, true, 'ffmpeg should be detected on host');
  console.log('✅ yt-dlp and ffmpeg detection verified.\n');

  // Test 2: Inspect URL
  console.log('--- Test 2: Inspect Video URL ---');
  const sampleUrl = 'https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c0/Big_Buck_Bunny_4K.webm/Big_Buck_Bunny_4K.webm.480p.vp9.webm';
  const meta = await downloader.inspect(sampleUrl);
  console.log('Inspected Metadata:', {
    id: meta.id,
    title: meta.title,
    durationFormatted: meta.durationFormatted,
    extractor: meta.extractor
  });
  assert.ok(meta.title, 'Should extract video title');
  assert.ok(meta.extractor, 'Should extract video extractor');
  console.log('✅ Inspect URL verified.\n');

  // Test 3: Download & Auto-mount to Quick Drop
  console.log('--- Test 3: Download & Quick Drop Auto-mount ---');
  const testDir = '/tmp/rirdrop_test_dl';
  if (!fs.existsSync(testDir)) fs.mkdirSync(testDir, { recursive: true });

  const progressEvents = [];
  downloader.onEvent = (evt, data) => {
    if (evt === 'downloader-progress') {
      progressEvents.push(data);
    }
  };

  const initialQuickDropCount = server.quickDropFiles.size;

  const job = await downloader.startDownload({
    jobId: 'test_job_1',
    url: sampleUrl,
    preset: '720p',
    targetDir: testDir,
    autoShare: true
  });

  console.log('Started job:', job.jobId, 'Status:', job.status);

  // Wait for completion (timeout 30s)
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Download test timed out')), 35000);
    const check = setInterval(() => {
      if (downloader.completedJobs.some(j => j.jobId === 'test_job_1')) {
        clearTimeout(timer);
        clearInterval(check);
        resolve();
      }
    }, 500);
  });

  const completedJob = downloader.completedJobs.find(j => j.jobId === 'test_job_1');
  assert.ok(completedJob, 'Job should complete successfully');
  assert.strictEqual(completedJob.status, 'completed');
  assert.ok(completedJob.filePath && fs.existsSync(completedJob.filePath), 'File should exist on disk');
  console.log('Downloaded File:', completedJob.filePath, 'Size:', completedJob.fileSize, 'Progress events captured:', progressEvents.length);

  // Verify auto-mount into Quick Drop
  assert.strictEqual(server.quickDropFiles.size, initialQuickDropCount + 1, 'Quick Drop should have 1 new item');
  const addedQuickDrop = Array.from(server.quickDropFiles.values()).find(f => f.path === completedJob.filePath);
  assert.ok(addedQuickDrop, 'Quick Drop item should match downloaded file path');
  console.log('✅ Download and auto-share into Quick Drop verified:', addedQuickDrop.name, addedQuickDrop.sizeFormatted, '\n');

  // Clean up
  try {
    fs.rmSync(testDir, { recursive: true, force: true });
  } catch (_) {}

  console.log('🎉 ALL MEDIA DOWNLOADER TESTS PASSED SUCCESSFULLY!\n');
  process.exit(0);
}

runDownloaderTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});

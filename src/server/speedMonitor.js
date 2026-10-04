/**
 * RirDrop Network Speed Monitor & Benchmark Engine
 * - checkInternetConnection: Detects active WAN connectivity
 * - LiveNetworkMonitor: Real-time network interface bandwidth & ping (/proc/net/dev)
 * - ActiveSpeedTester: Synthetic internet benchmark using Cloudflare CDN edge nodes
 */

const fs = require('fs');
const https = require('https');
const http = require('http');
const { execFile } = require('child_process');
const dns = require('dns');

function getPrimaryInterface() {
  try {
    // Attempt 1: check /proc/net/route for default gateway destination (00000000)
    if (fs.existsSync('/proc/net/route')) {
      const routeContent = fs.readFileSync('/proc/net/route', 'utf8');
      const lines = routeContent.trim().split('\n');
      for (let i = 1; i < lines.length; i++) {
        const parts = lines[i].trim().split(/\s+/);
        if (parts[1] === '00000000') {
          return parts[0];
        }
      }
    }
  } catch (_) {}

  // Attempt 2: Pick first non-loopback active interface from /proc/net/dev
  try {
    if (fs.existsSync('/proc/net/dev')) {
      const devContent = fs.readFileSync('/proc/net/dev', 'utf8');
      const lines = devContent.trim().split('\n');
      for (let i = 2; i < lines.length; i++) {
        const colonIdx = lines[i].indexOf(':');
        if (colonIdx === -1) continue;
        const iface = lines[i].substring(0, colonIdx).trim();
        if (iface === 'lo' || iface.startsWith('docker') || iface.startsWith('br-') || iface.startsWith('veth')) {
          continue;
        }
        return iface;
      }
    }
  } catch (_) {}

  return null;
}

function readProcNetDev(targetIface) {
  let rxTotal = 0;
  let txTotal = 0;
  try {
    if (!fs.existsSync('/proc/net/dev')) return { rxTotal, txTotal };
    const devContent = fs.readFileSync('/proc/net/dev', 'utf8');
    const lines = devContent.trim().split('\n');
    for (let i = 2; i < lines.length; i++) {
      const colonIdx = lines[i].indexOf(':');
      if (colonIdx === -1) continue;
      const iface = lines[i].substring(0, colonIdx).trim();
      if (iface === 'lo' || iface.startsWith('docker') || iface.startsWith('br-') || iface.startsWith('veth')) {
        continue;
      }
      if (targetIface && iface !== targetIface) {
        continue;
      }
      const data = lines[i].substring(colonIdx + 1).trim().split(/\s+/);
      rxTotal += parseInt(data[0], 10) || 0;
      txTotal += parseInt(data[8], 10) || 0;
    }
  } catch (_) {}
  return { rxTotal, txTotal };
}

function probeLatency(host = '1.1.1.1', timeoutSec = 1.2) {
  return new Promise((resolve) => {
    execFile('ping', ['-c', '1', '-W', String(Math.ceil(timeoutSec)), host], { timeout: 2000 }, (err, stdout) => {
      if (!err && stdout) {
        const m = stdout.match(/time=([\d.]+)\s*ms/);
        if (m) {
          return resolve(parseFloat(m[1]));
        }
      }
      // Fallback: fast TCP connect probe
      const start = Date.now();
      const net = require('net');
      const socket = new net.Socket();
      socket.setTimeout(1500);
      socket.connect(53, '1.1.1.1', () => {
        const diff = Date.now() - start;
        socket.destroy();
        resolve(diff);
      });
      socket.on('error', () => {
        socket.destroy();
        resolve(null);
      });
      socket.on('timeout', () => {
        socket.destroy();
        resolve(null);
      });
    });
  });
}

function checkInternetConnection() {
  return new Promise((resolve) => {
    dns.lookup('cloudflare.com', (err) => {
      if (!err) {
        return resolve({ connected: true });
      }
      // Fallback: check 1.1.1.1
      probeLatency('1.1.1.1', 1.0).then((lat) => {
        if (lat !== null) {
          resolve({ connected: true, latencyMs: lat });
        } else {
          resolve({ connected: false });
        }
      }).catch(() => resolve({ connected: false }));
    });
  });
}

class LiveNetworkMonitor {
  constructor(options = {}) {
    this.sampleInterval = options.sampleInterval || 400; // ms
    this.targetIface = options.targetIface || getPrimaryInterface();
    this.onStats = options.onStats || null;
    this.timer = null;
    this.isRunning = false;
    this.isPaused = false;
    this.prevRx = 0;
    this.prevTx = 0;
    this.prevTime = 0;
    this.lastPing = 8.0;
    this.lastPingTime = 0;
  }

  start() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.isPaused = false;

    if (!this.targetIface) {
      this.targetIface = getPrimaryInterface();
    }

    const initial = readProcNetDev(this.targetIface);
    this.prevRx = initial.rxTotal;
    this.prevTx = initial.txTotal;
    this.prevTime = Date.now();
    this.lastPingTime = 0;

    this.timer = setInterval(() => this._tick(), this.sampleInterval);
  }

  pause() {
    this.isPaused = true;
  }

  resume() {
    this.isPaused = false;
    const current = readProcNetDev(this.targetIface);
    this.prevRx = current.rxTotal;
    this.prevTx = current.txTotal;
    this.prevTime = Date.now();
  }

  stop() {
    this.isRunning = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  async _tick() {
    if (!this.isRunning || this.isPaused) return;

    const now = Date.now();
    const dt = (now - this.prevTime) / 1000.0;
    if (dt <= 0) return;

    const curr = readProcNetDev(this.targetIface);
    const rxDiff = Math.max(0, curr.rxTotal - this.prevRx);
    const txDiff = Math.max(0, curr.txTotal - this.prevTx);

    // Convert to Megabits per second
    const dlMbps = (rxDiff * 8.0) / (dt * 1000000.0);
    const ulMbps = (txDiff * 8.0) / (dt * 1000000.0);

    this.prevRx = curr.rxTotal;
    this.prevTx = curr.txTotal;
    this.prevTime = now;

    // Ping check every 2.5 seconds
    if (now - this.lastPingTime > 2500) {
      this.lastPingTime = now;
      probeLatency('1.1.1.1', 1.0).then((p) => {
        if (p !== null) this.lastPing = p;
      }).catch(() => {});
    }

    if (typeof this.onStats === 'function') {
      this.onStats({
        dlMbps: Math.round(dlMbps * 100) / 100,
        ulMbps: Math.round(ulMbps * 100) / 100,
        dlBytesPerSec: Math.round(rxDiff / dt),
        ulBytesPerSec: Math.round(txDiff / dt),
        pingMs: Math.round(this.lastPing * 10) / 10,
        iface: this.targetIface || 'default',
        timestamp: now
      });
    }
  }
}

class ActiveSpeedTester {
  constructor(options = {}) {
    this.onProgress = options.onProgress || null;
    this.onStatus = options.onStatus || null;
    this.onFinished = options.onFinished || null;
    this.onError = options.onError || null;
    this.isCancelled = false;
    this.isRunning = false;
    this.currentReq = null;
  }

  cancel() {
    this.isCancelled = true;
    this.isRunning = false;
    if (this.currentReq) {
      try { this.currentReq.destroy(); } catch (_) {}
      this.currentReq = null;
    }
  }

  async run() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.isCancelled = false;

    // 0. Verify Internet Connection
    if (this.onStatus) this.onStatus('Checking internet connection...');
    const conn = await checkInternetConnection();
    if (!conn.connected) {
      this.isRunning = false;
      if (this.onError) {
        this.onError({
          code: 'NO_INTERNET',
          message: 'Internet connection is required to run the speed test. Please connect to the internet.'
        });
      }
      return;
    }

    try {
      // 1. Latency / Ping Phase
      if (this.onStatus) this.onStatus('Measuring latency...');
      const pings = [];
      for (let i = 0; i < 3; i++) {
        if (this.isCancelled) return;
        const p = await probeLatency('1.1.1.1', 1.0);
        if (p !== null) pings.push(p);
        await new Promise((r) => setTimeout(r, 120));
      }
      const pingVal = pings.length > 0 ? Math.min(...pings) : 10.0;

      if (this.onProgress) {
        this.onProgress({
          phase: 'ping',
          dlMbps: 0,
          ulMbps: 0,
          pingMs: Math.round(pingVal * 10) / 10,
          progress: 15
        });
      }

      if (this.isCancelled) return;

      // 2. Download Speed Test Phase
      if (this.onStatus) this.onStatus('Testing Download Speed...');
      const dlSpeeds = [];
      const dlDurationMs = 5500;
      const dlStartTime = Date.now();
      const dlEndTime = dlStartTime + dlDurationMs;

      while (Date.now() < dlEndTime && !this.isCancelled) {
        const chunkSpeed = await this._downloadChunk(20000000, pingVal, dlStartTime, dlDurationMs);
        if (chunkSpeed !== null && chunkSpeed > 0) {
          dlSpeeds.push(chunkSpeed);
        }
      }

      if (this.isCancelled) return;

      const finalDl = dlSpeeds.length > 0
        ? Math.round((dlSpeeds.reduce((a, b) => a + b, 0) / dlSpeeds.length) * 100) / 100
        : 0;

      // 3. Upload Speed Test Phase
      if (this.onStatus) this.onStatus('Testing Upload Speed...');
      const ulSpeeds = [];
      const ulDurationMs = 4500;
      const ulStartTime = Date.now();
      const ulEndTime = ulStartTime + ulDurationMs;

      while (Date.now() < ulEndTime && !this.isCancelled) {
        const chunkSpeed = await this._uploadChunk(4000000, finalDl, pingVal, ulStartTime, ulDurationMs);
        if (chunkSpeed !== null && chunkSpeed > 0) {
          ulSpeeds.push(chunkSpeed);
        }
      }

      if (this.isCancelled) return;

      const finalUl = ulSpeeds.length > 0
        ? Math.round((ulSpeeds.reduce((a, b) => a + b, 0) / ulSpeeds.length) * 100) / 100
        : 0;

      this.isRunning = false;
      if (this.onStatus) this.onStatus('Speed test completed');
      if (this.onFinished) {
        this.onFinished({
          dlMbps: finalDl,
          ulMbps: finalUl,
          pingMs: Math.round(pingVal * 10) / 10
        });
      }
    } catch (err) {
      this.isRunning = false;
      if (!this.isCancelled && this.onError) {
        this.onError({ code: 'TEST_ERROR', message: err.message });
      }
    }
  }

  _downloadChunk(bytes, pingVal, overallStart, totalDuration) {
    return new Promise((resolve) => {
      if (this.isCancelled) return resolve(null);
      const url = `https://speed.cloudflare.com/__down?bytes=${bytes}`;
      const startTime = Date.now();
      let receivedBytes = 0;

      const req = https.get(url, { headers: { 'User-Agent': 'RirDrop-SpeedTester/1.0' } }, (res) => {
        if (res.statusCode !== 200) {
          res.resume();
          return resolve(null);
        }

        res.on('data', (chunk) => {
          if (this.isCancelled) {
            req.destroy();
            return resolve(null);
          }
          receivedBytes += chunk.length;
          const dtSec = (Date.now() - startTime) / 1000.0;
          if (dtSec > 0.08 && this.onProgress) {
            const instantMbps = (receivedBytes * 8.0) / (dtSec * 1000000.0);
            const overallElapsed = Date.now() - overallStart;
            const progress = Math.min(50, 15 + Math.round((overallElapsed / totalDuration) * 35));
            this.onProgress({
              phase: 'download',
              dlMbps: Math.round(instantMbps * 100) / 100,
              ulMbps: 0,
              pingMs: pingVal,
              progress
            });
          }
        });

        res.on('end', () => {
          const dtSec = (Date.now() - startTime) / 1000.0;
          if (dtSec > 0) {
            const speed = (receivedBytes * 8.0) / (dtSec * 1000000.0);
            resolve(speed);
          } else {
            resolve(null);
          }
        });

        res.on('error', () => resolve(null));
      });

      req.on('error', () => resolve(null));
      req.setTimeout(6000, () => {
        req.destroy();
        resolve(null);
      });
      this.currentReq = req;
    });
  }

  _uploadChunk(chunkSizeBytes, currentDl, pingVal, overallStart, totalDuration) {
    return new Promise((resolve) => {
      if (this.isCancelled) return resolve(null);
      const startTime = Date.now();
      const payload = Buffer.alloc(chunkSizeBytes, '0');

      const options = {
        hostname: 'speed.cloudflare.com',
        path: '/__up',
        method: 'POST',
        headers: {
          'Content-Type': 'application/octet-stream',
          'Content-Length': payload.length,
          'User-Agent': 'RirDrop-SpeedTester/1.0'
        }
      };

      const req = https.request(options, (res) => {
        res.resume();
        res.on('end', () => {
          const dtSec = (Date.now() - startTime) / 1000.0;
          if (dtSec > 0) {
            const speed = (payload.length * 8.0) / (dtSec * 1000000.0);
            resolve(speed);
          } else {
            resolve(null);
          }
        });
      });

      req.on('error', () => resolve(null));
      req.setTimeout(5000, () => {
        req.destroy();
        resolve(null);
      });

      // Emulate chunked writing to provide smooth progress
      const sliceSize = 256 * 1024;
      let offset = 0;

      const writeNext = () => {
        if (this.isCancelled) {
          req.destroy();
          return resolve(null);
        }
        if (offset < payload.length) {
          const next = Math.min(offset + sliceSize, payload.length);
          const chunk = payload.slice(offset, next);
          offset = next;
          req.write(chunk);

          const dtSec = (Date.now() - startTime) / 1000.0;
          if (dtSec > 0.05 && this.onProgress) {
            const instantMbps = (offset * 8.0) / (dtSec * 1000000.0);
            const overallElapsed = Date.now() - overallStart;
            const progress = Math.min(100, 50 + Math.round((overallElapsed / totalDuration) * 50));
            this.onProgress({
              phase: 'upload',
              dlMbps: currentDl,
              ulMbps: Math.round(instantMbps * 100) / 100,
              pingMs: pingVal,
              progress
            });
          }
          setImmediate(writeNext);
        } else {
          req.end();
        }
      };

      this.currentReq = req;
      writeNext();
    });
  }
}

module.exports = {
  getPrimaryInterface,
  readProcNetDev,
  probeLatency,
  checkInternetConnection,
  LiveNetworkMonitor,
  ActiveSpeedTester
};

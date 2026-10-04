const dgram = require('dgram');
const { getLocalIpAddresses } = require('./networkUtils');

const DISCOVERY_PORT = 53317;
const BROADCAST_ADDR = '255.255.255.255';

class DiscoveryEngine {
  constructor({ alias, httpPort, onPeersUpdate }) {
    this.alias = alias || 'Linux Host';
    this.httpPort = httpPort || 53318;
    this.onPeersUpdate = onPeersUpdate || (() => {});
    this.peers = new Map(); // ip:port -> { alias, os, ip, httpPort, lastSeen }
    this.socket = null;
    this.broadcastTimer = null;
    this.cleanupTimer = null;
    this.isRunning = false;
  }

  start() {
    if (this.isRunning) return Promise.resolve();

    return new Promise((resolve) => {
      this.socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

      this.socket.on('error', (err) => {
        console.warn('[Discovery] UDP socket error:', err.message);
        try { this.socket.close(); } catch (_) {}
      });

      this.socket.on('message', (msg, rinfo) => {
        try {
          const data = JSON.parse(msg.toString());
          if (!data || !data.protocol || data.protocol !== 'RIRDROP') return;

          // Skip our own broadcasts
          const localIps = getLocalIpAddresses().map(i => i.address);
          if (localIps.includes(rinfo.address) && data.httpPort === this.httpPort) {
            return;
          }

          const peerKey = `${rinfo.address}:${data.httpPort || rinfo.port}`;
          const peerInfo = {
            alias: data.alias || 'RirDrop Peer',
            os: data.os || 'unknown',
            ip: rinfo.address,
            httpPort: data.httpPort || 53318,
            deviceType: data.deviceType || (data.os === 'android' ? 'mobile' : 'desktop'),
            lastSeen: Date.now()
          };

          const isNew = !this.peers.has(peerKey);
          this.peers.set(peerKey, peerInfo);

          if (isNew) {
            this.emitPeers();
          }

          // If peer sent a probe/discovery, reply immediately with our announcement
          if (data.type === 'DISCOVER') {
            this.announce(rinfo.address, rinfo.port);
          }
        } catch (err) {
          // Ignore non-json or malformed UDP packets
        }
      });

      this.socket.on('listening', () => {
        try {
          this.socket.setBroadcast(true);
        } catch (err) {
          console.warn('[Discovery] setBroadcast error:', err.message);
        }
        this.isRunning = true;
        console.log(`[Discovery] UDP listener running on port ${DISCOVERY_PORT}`);

        // Broadcast immediately on start
        this.broadcast();
        resolve();
      });

      try {
        this.socket.bind(DISCOVERY_PORT);
      } catch (err) {
        console.warn('[Discovery] Bind error:', err.message);
        this.isRunning = true;
        resolve();
      }
    });

    // Broadcast announcement every 4 seconds
    this.broadcastTimer = setInterval(() => {
      this.broadcast();
    }, 4000);

    // Prune stale peers every 5 seconds
    this.cleanupTimer = setInterval(() => {
      const now = Date.now();
      let changed = false;
      for (const [key, peer] of this.peers.entries()) {
        if (now - peer.lastSeen > 12000) { // 12 seconds timeout
          this.peers.delete(key);
          changed = true;
        }
      }
      if (changed) {
        this.emitPeers();
      }
    }, 5000);
  }

  broadcast() {
    if (!this.socket || !this.isRunning) return;

    const payload = Buffer.from(JSON.stringify({
      protocol: 'RIRDROP',
      type: 'ANNOUNCE',
      alias: this.alias,
      os: 'linux',
      deviceType: 'desktop',
      httpPort: this.httpPort,
      version: '1.0.0',
      timestamp: Date.now()
    }));

    // Broadcast to global subnet
    try {
      this.socket.send(payload, 0, payload.length, DISCOVERY_PORT, BROADCAST_ADDR, () => {});
    } catch (_) {}

    // Also send specifically to broadcast addresses of active interfaces
    const ifaces = getLocalIpAddresses();
    for (const iface of ifaces) {
      try {
        const parts = iface.address.split('.');
        if (parts.length === 4) {
          const subnetBroadcast = `${parts[0]}.${parts[1]}.${parts[2]}.255`;
          this.socket.send(payload, 0, payload.length, DISCOVERY_PORT, subnetBroadcast, () => {});
        }
      } catch (_) {}
    }
  }

  announce(targetIp, targetPort) {
    if (!this.socket || !this.isRunning) return;
    const payload = Buffer.from(JSON.stringify({
      protocol: 'RIRDROP',
      type: 'ANNOUNCE',
      alias: this.alias,
      os: 'linux',
      deviceType: 'desktop',
      httpPort: this.httpPort,
      version: '1.0.0',
      timestamp: Date.now()
    }));

    try {
      this.socket.send(payload, 0, payload.length, targetPort, targetIp, () => {});
    } catch (_) {}
  }

  scanNow() {
    if (!this.socket || !this.isRunning) return;
    const probe = Buffer.from(JSON.stringify({
      protocol: 'RIRDROP',
      type: 'DISCOVER',
      alias: this.alias,
      os: 'linux',
      httpPort: this.httpPort
    }));
    try {
      this.socket.send(probe, 0, probe.length, DISCOVERY_PORT, BROADCAST_ADDR, () => {});
    } catch (_) {}
  }

  emitPeers() {
    const list = Array.from(this.peers.values());
    this.onPeersUpdate(list);
  }

  getPeers() {
    return Array.from(this.peers.values());
  }

  stop() {
    this.isRunning = false;
    if (this.broadcastTimer) clearInterval(this.broadcastTimer);
    if (this.cleanupTimer) clearInterval(this.cleanupTimer);
    if (this.socket) {
      try {
        this.socket.close();
      } catch (_) {}
      this.socket = null;
    }
  }
}

module.exports = DiscoveryEngine;

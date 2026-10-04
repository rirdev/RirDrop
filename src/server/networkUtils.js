const os = require('os');

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];

  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      // Skip internal (i.e. 127.0.0.1) and non-ipv4 addresses
      if (iface.family === 'IPv4' && !iface.internal) {
        addresses.push({
          interface: name,
          address: iface.address,
          netmask: iface.netmask,
          mac: iface.mac
        });
      }
    }
  }

  // Prioritize typical LAN interfaces (wlan, eth, enp, wlp) over virtual/docker ones
  addresses.sort((a, b) => {
    const isPreferred = (name) => /^(wl|en|eth)/i.test(name);
    if (isPreferred(a.interface) && !isPreferred(b.interface)) return -1;
    if (!isPreferred(a.interface) && isPreferred(b.interface)) return 1;
    return 0;
  });

  return addresses;
}

function getPrimaryIp() {
  const list = getLocalIpAddresses();
  return list.length > 0 ? list[0].address : '127.0.0.1';
}

function getHostInfo() {
  return {
    hostname: os.hostname(),
    username: os.userInfo().username || 'User',
    platform: process.platform,
    arch: process.arch,
    primaryIp: getPrimaryIp(),
    interfaces: getLocalIpAddresses()
  };
}

function formatBytes(bytes, decimals = 1) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

module.exports = {
  getLocalIpAddresses,
  getPrimaryIp,
  getHostInfo,
  formatBytes
};

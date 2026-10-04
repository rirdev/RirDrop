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

  // Prioritize genuine physical LAN interfaces over virtual/docker/WSL adapters
  addresses.sort((a, b) => {
    const isVirtual = (name) => /(virtual|vmware|vbox|wsl|pseudo|hyper-v|tailscale|zerotier|docker|tap|tun|bridge)/i.test(name);
    const isPhysical = (name) => /^(wi-?fi|wireless|ethernet|eth|en|wl|lan|local area)/i.test(name);

    // De-prioritize virtual adapters (e.g. WSL, Hyper-V, VMware)
    const aVirt = isVirtual(a.interface);
    const bVirt = isVirtual(b.interface);
    if (aVirt && !bVirt) return 1;
    if (!aVirt && bVirt) return -1;

    // Prioritize standard Wi-Fi and Ethernet
    const aPhys = isPhysical(a.interface);
    const bPhys = isPhysical(b.interface);
    if (aPhys && !bPhys) return -1;
    if (!aPhys && bPhys) return 1;

    // Prioritize common private LAN subnets (192.168.x.x, 10.x.x.x) over APIPA (169.254.x.x)
    const aLan = /^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)/.test(a.address);
    const bLan = /^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)/.test(b.address);
    if (aLan && !bLan) return -1;
    if (!aLan && bLan) return 1;

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

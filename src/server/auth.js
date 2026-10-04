const crypto = require('crypto');

class AuthManager {
  constructor(onEventCallback) {
    this.onEvent = onEventCallback || (() => {});
    this.passwordProtected = false;
    this.password = '';
    this.requireApproval = true;
    this.pendingRequests = new Map();
    this.authorizedSessions = new Map();
    this.trustedDevices = new Map(); // fingerprint -> { deviceName, ip, approvedAt }
  }

  setPassword(pwd) {
    if (!pwd || pwd.trim() === '') {
      this.passwordProtected = false;
      this.password = '';
    } else {
      this.passwordProtected = true;
      this.password = pwd.trim();
    }
    this.onEvent('security_updated', this.getStatus());
  }

  setRequireApproval(enable) {
    this.requireApproval = !!enable;
    this.onEvent('security_updated', this.getStatus());
  }

  getStatus() {
    return {
      passwordProtected: this.passwordProtected,
      requireApproval: this.requireApproval,
      hasPassword: this.password.length > 0
    };
  }

  generateToken() {
    return crypto.randomBytes(24).toString('hex');
  }

  requestAccess({ ip, deviceName, os, userAgent, fingerprint }) {
    // If this fingerprint was already trusted and authorized:
    if (fingerprint && this.trustedDevices.has(fingerprint)) {
      const token = this.generateToken();
      this.authorizedSessions.set(token, {
        ip,
        deviceName,
        os,
        fingerprint,
        authorizedAt: Date.now()
      });
      return { status: 'authorized', token };
    }

    // If completely open (no approval required AND no password):
    if (!this.requireApproval && !this.passwordProtected) {
      const token = this.generateToken();
      this.authorizedSessions.set(token, {
        ip,
        deviceName,
        os,
        fingerprint,
        authorizedAt: Date.now()
      });
      return { status: 'authorized', token };
    }

    // If password is set and approval is not required, client can supply password directly:
    if (this.passwordProtected && !this.requireApproval) {
      return { status: 'password_required' };
    }

    // Otherwise, create a pending connection request for the host UI to accept/deny:
    const requestId = crypto.randomBytes(12).toString('hex');
    const request = {
      requestId,
      ip,
      deviceName: deviceName || 'Unknown Device',
      os: os || 'Unknown OS',
      userAgent: userAgent || '',
      fingerprint: fingerprint || requestId,
      passwordRequired: this.passwordProtected,
      timestamp: Date.now()
    };

    this.pendingRequests.set(requestId, request);

    // Notify Desktop UI about the incoming connection request
    this.onEvent('connection_request', request);

    return {
      status: 'pending',
      requestId,
      passwordRequired: this.passwordProtected
    };
  }

  verifyPassword(tokenOrRequestId, providedPassword) {
    if (!this.passwordProtected) return true;
    return this.password === (providedPassword || '').trim();
  }

  approveRequest(requestId, providedPassword = null) {
    const request = this.pendingRequests.get(requestId);
    if (!request) return { success: false, error: 'Request not found or expired' };

    // If password protected, check password if provided
    if (this.passwordProtected && providedPassword) {
      if (!this.verifyPassword(requestId, providedPassword)) {
        return { success: false, error: 'Incorrect password' };
      }
    }

    this.pendingRequests.delete(requestId);

    const token = this.generateToken();
    const session = {
      token,
      ip: request.ip,
      deviceName: request.deviceName,
      os: request.os,
      fingerprint: request.fingerprint,
      authorizedAt: Date.now()
    };

    this.authorizedSessions.set(token, session);
    if (request.fingerprint) {
      this.trustedDevices.set(request.fingerprint, session);
    }

    this.onEvent('device_approved', session);
    return { success: true, token, session };
  }

  rejectRequest(requestId) {
    const request = this.pendingRequests.get(requestId);
    if (!request) return { success: false };

    this.pendingRequests.delete(requestId);
    this.onEvent('device_rejected', { requestId, ip: request.ip, deviceName: request.deviceName });
    return { success: true };
  }

  checkSession(token, ip) {
    if (!token) return false;
    const session = this.authorizedSessions.get(token);
    if (!session) return false;
    return true;
  }

  revokeDevice(fingerprintOrToken) {
    for (const [token, session] of this.authorizedSessions.entries()) {
      if (token === fingerprintOrToken || session.fingerprint === fingerprintOrToken) {
        this.authorizedSessions.delete(token);
      }
    }
    if (this.trustedDevices.has(fingerprintOrToken)) {
      this.trustedDevices.delete(fingerprintOrToken);
    }
    this.onEvent('device_revoked', { id: fingerprintOrToken });
  }

  getPendingList() {
    return Array.from(this.pendingRequests.values());
  }

  getConnectedDevices() {
    const devices = [];
    const seen = new Set();
    for (const session of this.authorizedSessions.values()) {
      const key = `${session.ip}_${session.deviceName}`;
      if (!seen.has(key)) {
        seen.add(key);
        devices.push(session);
      }
    }
    return devices;
  }
}

module.exports = AuthManager;

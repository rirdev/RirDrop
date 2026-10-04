package com.rirdev.rirdrop.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rirdev.rirdrop.network.DiscoveryEngine
import com.rirdev.rirdrop.network.NetworkUtils
import com.rirdev.rirdrop.network.PeerDevice
import com.rirdev.rirdrop.server.LocalHttpServer
import com.rirdev.rirdrop.server.SharedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class NavTab {
    DASHBOARD,
    QUICK_DROP,
    RADAR,
    STORAGE,
    SETTINGS
}

data class SpeedStats(
    val upSpeedStr: String = "0.0 MB/s",
    val downSpeedStr: String = "0.0 MB/s",
    val upBytesPerSec: Long = 0L,
    val downBytesPerSec: Long = 0L
)

data class PairedPc(
    val ip: String,
    val port: Int,
    val name: String,
    val token: String? = null,
    val status: String = "Connected"
)

class RirDropViewModel : ViewModel() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // 1. Navigation State
    private val _currentTab = MutableStateFlow(NavTab.DASHBOARD)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    // 2. Network & Identity
    private val _deviceName = MutableStateFlow(NetworkUtils.getDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    val port: Int = 53318

    // 3. Discovered & Connected Peers
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _activePairedPc = MutableStateFlow<PairedPc?>(null)
    val activePairedPc: StateFlow<PairedPc?> = _activePairedPc.asStateFlow()

    // 4. Quick Drop Shared Files
    private val _sharedFiles = MutableStateFlow<List<SharedItem>>(emptyList())
    val sharedFiles: StateFlow<List<SharedItem>> = _sharedFiles.asStateFlow()

    // 5. Transfer Telemetry
    private val _speedStats = MutableStateFlow(SpeedStats())
    val speedStats: StateFlow<SpeedStats> = _speedStats.asStateFlow()

    // 6. UI Notification / Modal State
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _showMyQrModal = MutableStateFlow(false)
    val showMyQrModal: StateFlow<Boolean> = _showMyQrModal.asStateFlow()

    private var discoveryEngine: DiscoveryEngine? = null
    private var httpServer: LocalHttpServer? = null

    init {
        startSpeedTelemetrySimulation()
    }

    fun attachEngines(discovery: DiscoveryEngine, server: LocalHttpServer?) {
        this.discoveryEngine = discovery
        this.httpServer = server
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun refreshNetworkInfo() {
        _localIp.value = NetworkUtils.getLocalIpAddress()
        _deviceName.value = NetworkUtils.getDeviceName()
    }

    fun updateDiscoveredPeers(peers: List<PeerDevice>) {
        _discoveredPeers.value = peers
    }

    fun scanNetworkNow() {
        discoveryEngine?.scanNow()
        refreshNetworkInfo()
        showMessage("Scanning Wi-Fi radar on UDP 53317...")
    }

    fun setShowMyQrModal(show: Boolean) {
        _showMyQrModal.value = show
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    // Connect to a PC given IP and Port
    fun connectToPc(ip: String, port: Int = 53318, pcName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("deviceName", _deviceName.value)
                    put("os", "Android")
                    put("fingerprint", "android-${System.currentTimeMillis()}")
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://$ip:$port/api/connect")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val respBody = response.body?.string() ?: "{}"
                val respJson = JSONObject(respBody)
                val status = respJson.optString("status", "pending")
                val token = if (respJson.has("token")) respJson.getString("token") else null

                val paired = PairedPc(
                    ip = ip,
                    port = port,
                    name = pcName ?: "PC ($ip)",
                    token = token,
                    status = if (status == "authorized") "Connected" else "Waiting Approval"
                )
                _activePairedPc.value = paired

                val peer = PeerDevice(
                    alias = pcName ?: "PC ($ip)",
                    os = "desktop",
                    ip = ip,
                    httpPort = port,
                    deviceType = "desktop",
                    lastSeen = System.currentTimeMillis()
                )
                val updatedConnected = _connectedPeers.value.toMutableList()
                if (updatedConnected.none { it.ip == ip }) {
                    updatedConnected.add(peer)
                    _connectedPeers.value = updatedConnected
                }

                if (status == "authorized") {
                    showMessage("✔ Connected with PC ($ip)")
                } else {
                    showMessage("Approval request sent to PC ($ip) screen")
                }
            } catch (e: Exception) {
                showMessage("Could not connect to PC at $ip:$port. Check Wi-Fi connection.")
            }
        }
    }

    // Handles result from CameraX QR Scanner
    fun handleScannedQr(qrData: String) {
        val trimmed = qrData.trim()
        var targetIp: String? = null
        var targetPort = 53318

        val httpRegex = Regex("""https?://([^:/]+)(?::(\d+))?""", RegexOption.IGNORE_CASE)
        val match = httpRegex.find(trimmed)
        if (match != null) {
            targetIp = match.groupValues[1]
            targetPort = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 53318
        } else {
            val ipPortRegex = Regex("""^([0-9.]+):(\d+)$""")
            val ipPortMatch = ipPortRegex.find(trimmed)
            if (ipPortMatch != null) {
                targetIp = ipPortMatch.groupValues[1]
                targetPort = ipPortMatch.groupValues[2].toIntOrNull() ?: 53318
            }
        }

        if (targetIp != null) {
            showMessage("Pairing with PC at $targetIp:$targetPort...")
            connectToPc(targetIp, targetPort)
        } else {
            showMessage("Scanned QR: $trimmed")
        }
    }

    // Shared Items for Quick Drop
    fun addSharedFiles(items: List<SharedItem>) {
        val current = _sharedFiles.value.toMutableList()
        current.addAll(items)
        _sharedFiles.value = current
        httpServer?.setSharedItems(current)
        showMessage("Added ${items.size} file(s) to Quick Drop!")
    }

    fun removeSharedFile(item: SharedItem) {
        val current = _sharedFiles.value.toMutableList()
        current.removeAll { it.id == item.id }
        _sharedFiles.value = current
        httpServer?.setSharedItems(current)
    }

    fun clearAllSharedFiles() {
        _sharedFiles.value = emptyList()
        httpServer?.setSharedItems(emptyList())
        showMessage("Cleared Quick Drop files")
    }

    // Generate pairing QR code bitmap for phone screen
    fun generatePairingQr(size: Int = 512): Bitmap? {
        val url = "http://${_localIp.value}:$port"
        return try {
            val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bits[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun startSpeedTelemetrySimulation() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1500)
                if (_activePairedPc.value != null || _sharedFiles.value.isNotEmpty()) {
                    val activeShares = _sharedFiles.value.size
                    val upMb = (activeShares * 2.4f)
                    val downMb = (0.5f)
                    _speedStats.value = SpeedStats(
                        upSpeedStr = String.format("%.1f MB/s", upMb),
                        downSpeedStr = String.format("%.1f MB/s", downMb),
                        upBytesPerSec = (upMb * 1024 * 1024).toLong(),
                        downBytesPerSec = (downMb * 1024 * 1024).toLong()
                    )
                } else {
                    _speedStats.value = SpeedStats(
                        upSpeedStr = "0.0 MB/s",
                        downSpeedStr = "0.0 MB/s",
                        upBytesPerSec = 0L,
                        downBytesPerSec = 0L
                    )
                }
            }
        }
    }
}

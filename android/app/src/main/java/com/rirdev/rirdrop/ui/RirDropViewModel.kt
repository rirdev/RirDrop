package com.rirdev.rirdrop.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rirdev.rirdrop.network.DiscoveryEngine
import com.rirdev.rirdrop.network.NetworkUtils
import com.rirdev.rirdrop.network.PeerDevice
import com.rirdev.rirdrop.server.LocalHttpServer
import com.rirdev.rirdrop.server.SharedItem
import com.rirdev.rirdrop.service.TransferService
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
import org.json.JSONArray
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

data class PcQuickDropFile(
    val id: String,
    val name: String,
    val sizeFormatted: String,
    val rawSize: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val streamUrl: String,
    val downloadUrl: String
)

data class PcSharedFolder(
    val id: String,
    val name: String,
    val path: String,
    val previewThumb: String? = null
)

data class PcFolderItem(
    val name: String,
    val relPath: String,
    val folderId: String,
    val isDirectory: Boolean,
    val sizeFormatted: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean,
    val thumbUrl: String? = null,
    val streamUrl: String,
    val downloadUrl: String
)

data class StreamMediaItem(
    val title: String,
    val mimeType: String,
    val url: String,
    val isVideo: Boolean
)

class RirDropViewModel : ViewModel() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // 1. Boot / Splash State
    private val _isBooting = MutableStateFlow(true)
    val isBooting: StateFlow<Boolean> = _isBooting.asStateFlow()

    private val _bootProgress = MutableStateFlow(0.1f)
    val bootProgress: StateFlow<Float> = _bootProgress.asStateFlow()

    private val _bootStatusText = MutableStateFlow("Starting RirDrop Engine...")
    val bootStatusText: StateFlow<String> = _bootStatusText.asStateFlow()

    // 2. Navigation State
    private val _currentTab = MutableStateFlow(NavTab.DASHBOARD)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    // 3. Network & Identity
    private val _deviceName = MutableStateFlow(NetworkUtils.getDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    val port: Int = 53318

    // 4. Discovered & Connected Peers
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _activePairedPc = MutableStateFlow<PairedPc?>(null)
    val activePairedPc: StateFlow<PairedPc?> = _activePairedPc.asStateFlow()

    // 5. Shared Files (Phone Host & PC Synced)
    private val _sharedFiles = MutableStateFlow<List<SharedItem>>(emptyList())
    val sharedFiles: StateFlow<List<SharedItem>> = _sharedFiles.asStateFlow()

    private val _pcQuickDropFiles = MutableStateFlow<List<PcQuickDropFile>>(emptyList())
    val pcQuickDropFiles: StateFlow<List<PcQuickDropFile>> = _pcQuickDropFiles.asStateFlow()

    private val _pcSharedFolders = MutableStateFlow<List<PcSharedFolder>>(emptyList())
    val pcSharedFolders: StateFlow<List<PcSharedFolder>> = _pcSharedFolders.asStateFlow()

    private val _currentBrowsingFolder = MutableStateFlow<PcSharedFolder?>(null)
    val currentBrowsingFolder: StateFlow<PcSharedFolder?> = _currentBrowsingFolder.asStateFlow()

    private val _pcFolderItems = MutableStateFlow<List<PcFolderItem>>(emptyList())
    val pcFolderItems: StateFlow<List<PcFolderItem>> = _pcFolderItems.asStateFlow()

    private val _isBrowsingFolderLoading = MutableStateFlow(false)
    val isBrowsingFolderLoading: StateFlow<Boolean> = _isBrowsingFolderLoading.asStateFlow()

    // 6. In-App Media Streaming Player State
    private val _activeStreamMedia = MutableStateFlow<StreamMediaItem?>(null)
    val activeStreamMedia: StateFlow<StreamMediaItem?> = _activeStreamMedia.asStateFlow()

    // 7. Telemetry & User Message
    private val _speedStats = MutableStateFlow(SpeedStats())
    val speedStats: StateFlow<SpeedStats> = _speedStats.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _showMyQrModal = MutableStateFlow(false)
    val showMyQrModal: StateFlow<Boolean> = _showMyQrModal.asStateFlow()

    private var discoveryEngine: DiscoveryEngine? = null
    private var httpServer: LocalHttpServer? = null

    init {
        runBootSequence()
        startSpeedTelemetrySimulation()
        startPcContinuousSync()
    }

    private fun runBootSequence() {
        viewModelScope.launch(Dispatchers.Default) {
            delay(250)
            _bootProgress.value = 0.35f
            _bootStatusText.value = "Acquiring Wi-Fi Multicast Lock..."
            delay(350)
            _bootProgress.value = 0.70f
            _bootStatusText.value = "Binding UDP 53317 Radar & HTTP 53318..."
            delay(350)
            _bootProgress.value = 1.0f
            _bootStatusText.value = "Ready to Share"
            delay(250)
            _isBooting.value = false
        }
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
        // Auto-pair or trigger sync if active PC is not yet set but we found a desktop peer
        if (_activePairedPc.value == null && peers.isNotEmpty()) {
            val desktopPeer = peers.firstOrNull { it.os.contains("win", true) || it.os.contains("linux", true) || it.os.contains("mac", true) }
            if (desktopPeer != null) {
                connectToPc(desktopPeer.ip, desktopPeer.httpPort, desktopPeer.alias)
            }
        }
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

    // In-App Media Player
    fun playMedia(item: StreamMediaItem) {
        _activeStreamMedia.value = item
    }

    fun closeMedia() {
        _activeStreamMedia.value = null
    }

    // Download file to phone using TransferService
    fun downloadToPhone(context: Context, url: String, fileName: String) {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_START_DOWNLOAD
            putExtra(TransferService.EXTRA_URL, url)
            putExtra(TransferService.EXTRA_FILE_NAME, fileName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        showMessage("Downloading $fileName to phone Downloads...")
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

                // Immediately fetch shared data from this PC
                fetchPcSharedData(ip, port, token)

                if (status == "authorized") {
                    showMessage("✔ Connected with PC ($ip)")
                } else {
                    showMessage("Approval request sent to PC ($ip)")
                }
            } catch (e: Exception) {
                // If direct connect fails, still try fetching shared data
                fetchPcSharedData(ip, port, null)
            }
        }
    }

    // Fetches Quick Drop files & Shared folders from PC
    private fun fetchPcSharedData(ip: String, port: Int, token: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokenParam = if (!token.isNullOrBlank()) "?token=$token" else ""
                val url = "http://$ip:$port/api/shared$tokenParam"
                val reqBuilder = Request.Builder().url(url)
                if (!token.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val res = httpClient.newCall(reqBuilder.build()).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: "{}"
                    val json = JSONObject(body)

                    // 1. Parse quickDropFiles from PC
                    val qdArray = json.optJSONArray("quickDropFiles") ?: JSONArray()
                    val pcFiles = mutableListOf<PcQuickDropFile>()
                    for (i in 0 until qdArray.length()) {
                        val obj = qdArray.getJSONObject(i)
                        val id = obj.optString("id", "$i")
                        val name = obj.optString("name", "File")
                        val rawSize = obj.optLong("size", 0L)
                        val sizeFormatted = obj.optString("sizeFormatted", formatBytes(rawSize))
                        val mimeType = obj.optString("mimeType", "application/octet-stream")
                        val isVideo = mimeType.startsWith("video/") || name.matches(Regex(""".*\.(mp4|mkv|webm|avi|mov)$""", RegexOption.IGNORE_CASE))
                        val isAudio = mimeType.startsWith("audio/") || name.matches(Regex(""".*\.(mp3|flac|wav|ogg|m4a|aac)$""", RegexOption.IGNORE_CASE))

                        val streamUrl = "http://$ip:$port/api/stream?fileId=$id$tokenParam"
                        val downloadUrl = "http://$ip:$port/api/download?fileId=$id$tokenParam"

                        pcFiles.add(
                            PcQuickDropFile(
                                id = id,
                                name = name,
                                sizeFormatted = sizeFormatted,
                                rawSize = rawSize,
                                mimeType = mimeType,
                                isVideo = isVideo,
                                isAudio = isAudio,
                                streamUrl = streamUrl,
                                downloadUrl = downloadUrl
                            )
                        )
                    }
                    _pcQuickDropFiles.value = pcFiles

                    // 2. Parse sharedFolders from PC
                    val foldersArray = json.optJSONArray("sharedFolders") ?: JSONArray()
                    val folders = mutableListOf<PcSharedFolder>()
                    for (i in 0 until foldersArray.length()) {
                        val obj = foldersArray.getJSONObject(i)
                        val fId = obj.optString("id", "$i")
                        val fName = obj.optString("name", "Shared Folder")
                        val fPath = obj.optString("path", "")
                        val previewThumb = if (obj.has("previewThumb") && !obj.isNull("previewThumb")) obj.optString("previewThumb") else null
                        val fullThumbUrl = if (!previewThumb.isNullOrBlank()) "http://$ip:$port$previewThumb$tokenParam" else null
                        folders.add(PcSharedFolder(fId, fName, fPath, fullThumbUrl))
                    }
                    _pcSharedFolders.value = folders
                }
            } catch (_: Exception) {}
        }
    }

    fun browsePcFolder(folder: PcSharedFolder, subPath: String = "") {
        _currentBrowsingFolder.value = folder
        _isBrowsingFolderLoading.value = true
        val pc = _activePairedPc.value
        val targetIp = pc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val targetPort = pc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        val token = pc?.token

        if (targetIp == null) {
            _isBrowsingFolderLoading.value = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokenParam = if (!token.isNullOrBlank()) "&token=$token" else ""
                val url = "http://$targetIp:$targetPort/api/shared?folderId=${folder.id}&subPath=${subPath}$tokenParam"
                val res = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val itemsArray = json.optJSONArray("folderItems") ?: JSONArray()
                    val items = mutableListOf<PcFolderItem>()
                    for (i in 0 until itemsArray.length()) {
                        val obj = itemsArray.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val relPath = obj.optString("relPath", "")
                        val isDir = obj.optBoolean("isDirectory", false)
                        val sizeFormatted = obj.optString("sizeFormatted", "--")
                        val isVideo = obj.optBoolean("isVideo", false)
                        val isAudio = obj.optBoolean("isAudio", false)
                        val isImage = obj.optBoolean("isImage", false)
                        val thumb = if (obj.has("thumbUrl") && !obj.isNull("thumbUrl")) obj.optString("thumbUrl") else null
                        val fullThumb = if (!thumb.isNullOrBlank()) "http://$targetIp:$targetPort$thumb$tokenParam" else null
                        val streamUrl = "http://$targetIp:$targetPort/api/stream?folderId=${folder.id}&relPath=${relPath}$tokenParam"
                        val downloadUrl = "http://$targetIp:$targetPort/api/download?folderId=${folder.id}&relPath=${relPath}$tokenParam"

                        items.add(
                            PcFolderItem(
                                name = name,
                                relPath = relPath,
                                folderId = folder.id,
                                isDirectory = isDir,
                                sizeFormatted = sizeFormatted,
                                isVideo = isVideo,
                                isAudio = isAudio,
                                isImage = isImage,
                                thumbUrl = fullThumb,
                                streamUrl = streamUrl,
                                downloadUrl = downloadUrl
                            )
                        )
                    }
                    _pcFolderItems.value = items
                }
            } catch (e: Exception) {
                showMessage("Could not browse folder: ${e.message}")
            } finally {
                _isBrowsingFolderLoading.value = false
            }
        }
    }

    fun closeBrowsingFolder() {
        _currentBrowsingFolder.value = null
        _pcFolderItems.value = emptyList()
        _isBrowsingFolderLoading.value = false
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

    // Shared Items for Quick Drop (Local Phone Files)
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

    private fun startPcContinuousSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2000)
                val targetPc = _activePairedPc.value
                val peer = _discoveredPeers.value.firstOrNull { it.os.contains("win", true) || it.os.contains("linux", true) || it.os.contains("mac", true) }
                val ip = targetPc?.ip ?: peer?.ip
                val port = targetPc?.port ?: peer?.httpPort ?: 53318
                val token = targetPc?.token

                if (ip != null) {
                    fetchPcSharedData(ip, port, token)
                }
            }
        }
    }

    private fun startSpeedTelemetrySimulation() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1500)
                if (_activePairedPc.value != null || _sharedFiles.value.isNotEmpty() || _pcQuickDropFiles.value.isNotEmpty()) {
                    val activeShares = _sharedFiles.value.size + _pcQuickDropFiles.value.size
                    val upMb = (activeShares * 2.8f).coerceAtLeast(1.2f)
                    val downMb = (activeShares * 3.4f).coerceAtLeast(2.1f)
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

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

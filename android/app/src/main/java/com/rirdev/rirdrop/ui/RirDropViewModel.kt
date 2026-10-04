package com.rirdev.rirdrop.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Job
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class NavTab {
    DASHBOARD,
    QUICK_DROP,
    STORAGE,
    SPEED_TEST,
    DOWNLOADER,
    RADAR,
    DOWNLOADS,
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

data class DownloadedFileItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val lastModifiedFormatted: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String
)

data class YtDlpFormatOption(
    val id: String,
    val label: String,
    val isAudio: Boolean
)

data class YtDlpInspectResult(
    val title: String,
    val durationFormatted: String,
    val thumbnail: String?,
    val uploader: String,
    val url: String,
    val formats: List<YtDlpFormatOption> = emptyList()
)

data class YtDlpJob(
    val id: String,
    val title: String,
    val progress: Float,
    val speedStr: String,
    val status: String,
    val isFinished: Boolean,
    val filePath: String? = null,
    val fileName: String? = null,
    val streamUrl: String? = null
)

data class YtDlpCompletedItem(
    val jobId: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val totalFormatted: String,
    val isVideo: Boolean,
    val isAudio: Boolean
)

enum class BenchmarkPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    DONE,
    ERROR
}

class RirDropViewModel : ViewModel() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val speedTestClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    private val _profileName = MutableStateFlow("JOHN DOE")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    val port: Int = 53318

    // Device stable fingerprint & cached token
    private var persistentFingerprint: String = "android-rd-" + UUID.randomUUID().toString().take(12)
    private var cachedAuthToken: String? = null

    // 4. Discovered & Connected Peers
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _activePairedPc = MutableStateFlow<PairedPc?>(null)
    val activePairedPc: StateFlow<PairedPc?> = _activePairedPc.asStateFlow()

    private val _isApprovalPending = MutableStateFlow(false)
    val isApprovalPending: StateFlow<Boolean> = _isApprovalPending.asStateFlow()

    private var approvalPollingJob: Job? = null

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

    // 8. Speed Test: Live Network Monitor State (from TrafficStats)
    private val _liveHistoryDl = MutableStateFlow<List<Float>>(List(30) { 0f })
    val liveHistoryDl: StateFlow<List<Float>> = _liveHistoryDl.asStateFlow()

    private val _liveHistoryUl = MutableStateFlow<List<Float>>(List(30) { 0f })
    val liveHistoryUl: StateFlow<List<Float>> = _liveHistoryUl.asStateFlow()

    private val _livePeakDl = MutableStateFlow(0f)
    val livePeakDl: StateFlow<Float> = _livePeakDl.asStateFlow()

    private val _livePeakUl = MutableStateFlow(0f)
    val livePeakUl: StateFlow<Float> = _livePeakUl.asStateFlow()

    // 9. Speed Test: Internet Speed Benchmark State
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkPhase = MutableStateFlow(BenchmarkPhase.IDLE)
    val benchmarkPhase: StateFlow<BenchmarkPhase> = _benchmarkPhase.asStateFlow()

    private val _benchmarkStatusText = MutableStateFlow("Ready to test connection")
    val benchmarkStatusText: StateFlow<String> = _benchmarkStatusText.asStateFlow()

    private val _benchmarkProgress = MutableStateFlow(0f)
    val benchmarkProgress: StateFlow<Float> = _benchmarkProgress.asStateFlow()

    private val _benchPingMs = MutableStateFlow(0f)
    val benchPingMs: StateFlow<Float> = _benchPingMs.asStateFlow()

    private val _benchJitterMs = MutableStateFlow(0f)
    val benchJitterMs: StateFlow<Float> = _benchJitterMs.asStateFlow()

    private val _benchDownloadMbps = MutableStateFlow(0f)
    val benchDownloadMbps: StateFlow<Float> = _benchDownloadMbps.asStateFlow()

    private val _benchUploadMbps = MutableStateFlow(0f)
    val benchUploadMbps: StateFlow<Float> = _benchUploadMbps.asStateFlow()

    private val _benchGaugeTarget = MutableStateFlow(0f)
    val benchGaugeTarget: StateFlow<Float> = _benchGaugeTarget.asStateFlow()

    private var benchmarkJob: Job? = null

    // 10. Downloads Manager State
    private val _downloadedFiles = MutableStateFlow<List<DownloadedFileItem>>(emptyList())
    val downloadedFiles: StateFlow<List<DownloadedFileItem>> = _downloadedFiles.asStateFlow()

    // 11. In-App GitHub Update Notification State
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    // 12. YT-DLP Downloader State
    private val _ytDlpInspectResult = MutableStateFlow<YtDlpInspectResult?>(null)
    val ytDlpInspectResult: StateFlow<YtDlpInspectResult?> = _ytDlpInspectResult.asStateFlow()

    private val _isInspectingYtDlp = MutableStateFlow(false)
    val isInspectingYtDlp: StateFlow<Boolean> = _isInspectingYtDlp.asStateFlow()

    private val _activeYtDlpJobs = MutableStateFlow<List<YtDlpJob>>(emptyList())
    val activeYtDlpJobs: StateFlow<List<YtDlpJob>> = _activeYtDlpJobs.asStateFlow()

    private val _ytDlpCompletedList = MutableStateFlow<List<YtDlpCompletedItem>>(emptyList())
    val ytDlpCompletedList: StateFlow<List<YtDlpCompletedItem>> = _ytDlpCompletedList.asStateFlow()

    private val _isEngineAvailable = MutableStateFlow(true)
    val isEngineAvailable: StateFlow<Boolean> = _isEngineAvailable.asStateFlow()

    private var discoveryEngine: DiscoveryEngine? = null
    private var httpServer: LocalHttpServer? = null

    init {
        runBootSequence()
        startLiveNetworkTrafficPolling()
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

    fun initPreferences(context: Context) {
        val prefs = context.getSharedPreferences("rirdrop_prefs", Context.MODE_PRIVATE)
        val savedFp = prefs.getString("device_fingerprint", null)
        if (savedFp.isNullOrBlank()) {
            val newFp = "android-rd-" + UUID.randomUUID().toString().take(12)
            persistentFingerprint = newFp
            prefs.edit().putString("device_fingerprint", newFp).apply()
        } else {
            persistentFingerprint = savedFp
        }

        cachedAuthToken = prefs.getString("rirdrop_token", null)

        val savedDevice = prefs.getString("device_name", null)
        if (!savedDevice.isNullOrBlank()) {
            _deviceName.value = savedDevice
        }
        val savedProfile = prefs.getString("profile_name", null)
        if (!savedProfile.isNullOrBlank()) {
            _profileName.value = savedProfile
        }
        refreshDownloadedFiles(context)
        checkForUpdates(silent = true)
    }

    fun setDeviceName(context: Context, name: String) {
        val trimmed = name.trim().take(32)
        if (trimmed.isNotEmpty()) {
            _deviceName.value = trimmed
            context.getSharedPreferences("rirdrop_prefs", Context.MODE_PRIVATE)
                .edit().putString("device_name", trimmed).apply()
            showMessage("Device name set to $trimmed")
        }
    }

    fun setProfileName(context: Context, name: String) {
        val trimmed = name.trim().take(32)
        if (trimmed.isNotEmpty()) {
            _profileName.value = trimmed
            context.getSharedPreferences("rirdrop_prefs", Context.MODE_PRIVATE)
                .edit().putString("profile_name", trimmed).apply()
            showMessage("Display name updated")
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

    fun appendToken(url: String, token: String?): String {
        if (token.isNullOrBlank()) return url
        return if (url.contains("?")) "$url&token=$token" else "$url?token=$token"
    }

    // In-App Media Player
    fun playMedia(item: StreamMediaItem) {
        val targetPc = _activePairedPc.value
        val streamWithToken = appendToken(item.url, targetPc?.token ?: cachedAuthToken)
        _activeStreamMedia.value = item.copy(url = streamWithToken)
    }

    fun closeMedia() {
        _activeStreamMedia.value = null
    }

    // Download file to phone using TransferService
    fun downloadToPhone(context: Context, url: String, fileName: String) {
        val targetPc = _activePairedPc.value
        val urlWithToken = appendToken(url, targetPc?.token ?: cachedAuthToken)
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_START_DOWNLOAD
            putExtra(TransferService.EXTRA_URL, urlWithToken)
            putExtra(TransferService.EXTRA_FILE_NAME, fileName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        showMessage("Downloading $fileName to phone Downloads...")
        viewModelScope.launch {
            delay(1500)
            refreshDownloadedFiles(context)
        }
    }

    // Connect to a PC with official RirDrop Android client handshake
    fun connectToPc(ip: String, port: Int = 53318, pcName: String? = null, password: String? = null) {
        approvalPollingJob?.cancel()
        _isApprovalPending.value = false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("clientType", "rirdrop-android")
                    put("deviceName", _deviceName.value)
                    put("os", "Android")
                    put("fingerprint", persistentFingerprint)
                    if (!password.isNullOrBlank()) {
                        put("password", password)
                    }
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://$ip:$port/api/connect")
                    .header("X-RirDrop-Client", "android")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val respBody = response.body?.string() ?: "{}"
                val respJson = JSONObject(respBody)
                val status = respJson.optString("status", "pending")
                val token = if (respJson.has("token")) respJson.getString("token") else cachedAuthToken
                val requestId = respJson.optString("requestId", "")

                if (!token.isNullOrBlank()) {
                    cachedAuthToken = token
                }

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
                    _isApprovalPending.value = false
                    fetchPcSharedData(ip, port, token)
                    showMessage("✔ Connected with PC ($ip)")
                } else if (status == "pending" && requestId.isNotEmpty()) {
                    _isApprovalPending.value = true
                    showMessage("Approval request sent! Click 'Accept' on your PC desktop")
                    startApprovalPolling(ip, port, pcName, requestId)
                } else {
                    fetchPcSharedData(ip, port, token)
                }
            } catch (e: Exception) {
                // If direct connect fails, still try fetching shared data
                fetchPcSharedData(ip, port, cachedAuthToken)
            }
        }
    }

    private fun startApprovalPolling(ip: String, port: Int, pcName: String?, requestId: String) {
        approvalPollingJob = viewModelScope.launch(Dispatchers.IO) {
            var attempts = 0
            val maxAttempts = 60
            while (attempts < maxAttempts && _isApprovalPending.value) {
                delay(1200)
                attempts++
                try {
                    val pollUrl = "http://$ip:$port/api/poll-status?requestId=$requestId"
                    val res = httpClient.newCall(Request.Builder().url(pollUrl).build()).execute()
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        if (status == "authorized") {
                            val token = json.optString("token")
                            cachedAuthToken = token
                            val paired = PairedPc(
                                ip = ip,
                                port = port,
                                name = pcName ?: "PC ($ip)",
                                token = token,
                                status = "Connected"
                            )
                            _activePairedPc.value = paired
                            _isApprovalPending.value = false
                            fetchPcSharedData(ip, port, token)
                            showMessage("✔ PC Approved! 3 shared folders unlocked.")
                            break
                        } else if (status == "rejected_or_expired") {
                            _isApprovalPending.value = false
                            showMessage("Connection request rejected on PC")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            if (_isApprovalPending.value && attempts >= maxAttempts) {
                _isApprovalPending.value = false
                showMessage("Approval request timed out. Please retry.")
            }
        }
    }

    fun cancelApprovalRequest() {
        approvalPollingJob?.cancel()
        _isApprovalPending.value = false
    }

    fun refreshPcShared() {
        val pc = _activePairedPc.value
        val ip = pc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = pc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        val token = pc?.token ?: cachedAuthToken
        if (ip != null) {
            fetchPcSharedData(ip, port, token)
            showMessage("Refreshing PC shared storage...")
        } else {
            showMessage("No PC detected yet. Tap 'Connect IP' or scan radar.")
        }
    }

    // Fetches Quick Drop files & Shared folders from PC
    private fun fetchPcSharedData(ip: String, port: Int, token: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = appendToken("http://$ip:$port/api/shared", token)
                val reqBuilder = Request.Builder().url(url).header("X-RirDrop-Client", "android")
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

                        val streamUrl = appendToken("http://$ip:$port/api/stream?fileId=$id", token)
                        val downloadUrl = appendToken("http://$ip:$port/api/download?fileId=$id", token)

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
                        val fullThumbUrl = if (!previewThumb.isNullOrBlank()) {
                            appendToken("http://$ip:$port$previewThumb", token)
                        } else null
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
        val token = pc?.token ?: cachedAuthToken

        if (targetIp == null) {
            _isBrowsingFolderLoading.value = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = "http://$targetIp:$targetPort/api/shared?folderId=${folder.id}&subPath=${subPath}"
                val url = appendToken(baseUrl, token)
                val req = Request.Builder().url(url).header("X-RirDrop-Client", "android")
                if (!token.isNullOrBlank()) {
                    req.header("Authorization", "Bearer $token")
                }
                val res = httpClient.newCall(req.build()).execute()
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
                        val fullThumb = if (!thumb.isNullOrBlank()) appendToken("http://$targetIp:$targetPort$thumb", token) else null
                        val streamUrl = appendToken("http://$targetIp:$targetPort/api/stream?folderId=${folder.id}&relPath=${relPath}", token)
                        val downloadUrl = appendToken("http://$targetIp:$targetPort/api/download?folderId=${folder.id}&relPath=${relPath}", token)

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

    // Continuous background synchronization with PC
    private fun startPcContinuousSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2000)
                val targetPc = _activePairedPc.value
                val peer = _discoveredPeers.value.firstOrNull { it.os.contains("win", true) || it.os.contains("linux", true) || it.os.contains("mac", true) }
                val ip = targetPc?.ip ?: peer?.ip
                val port = targetPc?.port ?: peer?.httpPort ?: 53318
                val token = targetPc?.token ?: cachedAuthToken

                if (ip != null) {
                    fetchPcSharedData(ip, port, token)
                }
            }
        }
    }

    // Real TrafficStats Polling for Live Network Monitor (Measures REAL network interface throughput)
    private fun startLiveNetworkTrafficPolling() {
        viewModelScope.launch(Dispatchers.Default) {
            var lastRxBytes = TrafficStats.getTotalRxBytes()
            var lastTxBytes = TrafficStats.getTotalTxBytes()
            var lastTime = System.currentTimeMillis()

            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dt = (now - lastTime) / 1000f
                lastTime = now

                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()

                var rxBytesPerSec = 0L
                var txBytesPerSec = 0L

                if (lastRxBytes != TrafficStats.UNSUPPORTED.toLong() && currentRx >= lastRxBytes && dt > 0) {
                    rxBytesPerSec = ((currentRx - lastRxBytes) / dt).toLong()
                    txBytesPerSec = ((currentTx - lastTxBytes) / dt).toLong()
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx

                val dlMbps = (rxBytesPerSec * 8f) / 1000000f
                val ulMbps = (txBytesPerSec * 8f) / 1000000f

                val downMb = rxBytesPerSec / (1024f * 1024f)
                val upMb = txBytesPerSec / (1024f * 1024f)

                _speedStats.value = SpeedStats(
                    upSpeedStr = String.format(Locale.US, "%.1f MB/s", upMb),
                    downSpeedStr = String.format(Locale.US, "%.1f MB/s", downMb),
                    upBytesPerSec = rxBytesPerSec,
                    downBytesPerSec = txBytesPerSec
                )

                // Update rolling graph history for Live Network tab
                val currentDl = _liveHistoryDl.value.toMutableList()
                val currentUl = _liveHistoryUl.value.toMutableList()

                if (currentDl.size >= 30) currentDl.removeAt(0)
                currentDl.add(dlMbps)
                _liveHistoryDl.value = currentDl

                if (currentUl.size >= 30) currentUl.removeAt(0)
                currentUl.add(ulMbps)
                _liveHistoryUl.value = currentUl

                _livePeakDl.value = max(_livePeakDl.value, dlMbps)
                _livePeakUl.value = max(_livePeakUl.value, ulMbps)
            }
        }
    }

    // Active Speed Benchmark Engine (Using Cloudflare speed nodes matching Desktop)
    fun startBenchmarkTest() {
        if (_isBenchmarking.value) return
        benchmarkJob?.cancel()

        benchmarkJob = viewModelScope.launch(Dispatchers.IO) {
            _isBenchmarking.value = true
            _benchmarkPhase.value = BenchmarkPhase.PING
            _benchmarkProgress.value = 0.05f
            _benchmarkStatusText.value = "Measuring latency (Ping & Jitter)..."
            _benchGaugeTarget.value = 10f

            try {
                // 1. Latency & Jitter Phase
                val pings = mutableListOf<Float>()
                for (i in 0 until 4) {
                    val t0 = System.currentTimeMillis()
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=0")
                        .header("User-Agent", "RirDrop-SpeedTester/1.0")
                        .build()
                    val resp = speedTestClient.newCall(req).execute()
                    resp.close()
                    val elapsed = (System.currentTimeMillis() - t0).toFloat()
                    pings.add(elapsed)
                    delay(120)
                }

                val minPing = if (pings.isNotEmpty()) pings.minOrNull() ?: 12f else 12f
                val jitter = if (pings.size >= 2) {
                    var diffSum = 0f
                    for (i in 1 until pings.size) {
                        diffSum += Math.abs(pings[i] - pings[i - 1])
                    }
                    diffSum / (pings.size - 1)
                } else 2.5f

                _benchPingMs.value = minPing
                _benchJitterMs.value = jitter
                _benchmarkProgress.value = 0.20f

                // 2. Download Throughput Phase
                _benchmarkPhase.value = BenchmarkPhase.DOWNLOAD
                _benchmarkStatusText.value = "Testing Download Speed..."
                val dlSpeeds = mutableListOf<Float>()
                val dlDurationMs = 5000L
                val dlStartTime = System.currentTimeMillis()
                val dlEndTime = dlStartTime + dlDurationMs

                while (System.currentTimeMillis() < dlEndTime && _isBenchmarking.value) {
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=20000000")
                        .header("User-Agent", "RirDrop-SpeedTester/1.0")
                        .build()

                    val call = speedTestClient.newCall(req)
                    val resp = call.execute()
                    val body = resp.body
                    if (body != null) {
                        val input = body.byteStream()
                        val buffer = ByteArray(64 * 1024)
                        var chunkBytes = 0L
                        val chunkStart = System.currentTimeMillis()

                        var read: Int
                        while (input.read(buffer).also { read = it } != -1 && _isBenchmarking.value && System.currentTimeMillis() < dlEndTime) {
                            chunkBytes += read
                            val dt = (System.currentTimeMillis() - chunkStart) / 1000f
                            if (dt > 0.1f) {
                                val instantMbps = (chunkBytes * 8f) / (dt * 1000000f)
                                _benchDownloadMbps.value = instantMbps
                                _benchGaugeTarget.value = instantMbps
                                val elapsed = System.currentTimeMillis() - dlStartTime
                                val p = 0.20f + (elapsed.toFloat() / dlDurationMs) * 0.40f
                                _benchmarkProgress.value = min(0.60f, p)
                            }
                        }
                        val finalDt = (System.currentTimeMillis() - chunkStart) / 1000f
                        if (finalDt > 0.05f) {
                            val spd = (chunkBytes * 8f) / (finalDt * 1000000f)
                            dlSpeeds.add(spd)
                        }
                        resp.close()
                    }
                }

                val avgDl = if (dlSpeeds.isNotEmpty()) dlSpeeds.average().toFloat() else _benchDownloadMbps.value
                _benchDownloadMbps.value = avgDl

                // 3. Upload Throughput Phase
                _benchmarkPhase.value = BenchmarkPhase.UPLOAD
                _benchmarkStatusText.value = "Testing Upload Speed..."
                val ulSpeeds = mutableListOf<Float>()
                val ulDurationMs = 4000L
                val ulStartTime = System.currentTimeMillis()
                val ulEndTime = ulStartTime + ulDurationMs

                val payload = ByteArray(4 * 1024 * 1024) // 4MB
                val body = payload.toRequestBody("application/octet-stream".toMediaType())

                while (System.currentTimeMillis() < ulEndTime && _isBenchmarking.value) {
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__up")
                        .header("User-Agent", "RirDrop-SpeedTester/1.0")
                        .post(body)
                        .build()

                    val t0 = System.currentTimeMillis()
                    val resp = speedTestClient.newCall(req).execute()
                    resp.close()
                    val dt = (System.currentTimeMillis() - t0) / 1000f
                    if (dt > 0.05f) {
                        val instantMbps = (payload.size * 8f) / (dt * 1000000f)
                        ulSpeeds.add(instantMbps)
                        _benchUploadMbps.value = instantMbps
                        _benchGaugeTarget.value = instantMbps
                    }
                    val elapsed = System.currentTimeMillis() - ulStartTime
                    val p = 0.60f + (elapsed.toFloat() / ulDurationMs) * 0.38f
                    _benchmarkProgress.value = min(0.98f, p)
                }

                val avgUl = if (ulSpeeds.isNotEmpty()) ulSpeeds.average().toFloat() else _benchUploadMbps.value
                _benchUploadMbps.value = avgUl

                // 4. Completed Phase
                _benchmarkProgress.value = 1.0f
                _benchmarkPhase.value = BenchmarkPhase.DONE
                _benchmarkStatusText.value = "Speed test completed successfully"
                _isBenchmarking.value = false
            } catch (e: Exception) {
                _benchmarkPhase.value = BenchmarkPhase.ERROR
                _benchmarkStatusText.value = "Test error: ${e.message ?: "Network timeout"}"
                _isBenchmarking.value = false
            }
        }
    }

    fun cancelBenchmarkTest() {
        benchmarkJob?.cancel()
        _isBenchmarking.value = false
        _benchmarkPhase.value = BenchmarkPhase.IDLE
        _benchmarkStatusText.value = "Benchmark cancelled"
        _benchmarkProgress.value = 0f
        _benchGaugeTarget.value = 0f
    }

    // YT-DLP Downloader Engine Integration
    fun inspectYtDlpUrl(url: String) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318

        if (ip == null) {
            showMessage("Please connect to your PC to use the YT-DLP engine.")
            return
        }

        _isInspectingYtDlp.value = true
        _ytDlpInspectResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("url", url.trim()) }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://$ip:$port/api/downloader/inspect")
                    .post(body)
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "{}")
                    val title = respJson.optString("title", "Media Video")
                    val duration = respJson.optString("durationFormatted", "0:00")
                    val thumb = if (respJson.isNull("thumbnail")) null else respJson.optString("thumbnail").takeIf { it.isNotBlank() }
                    val uploader = respJson.optString("uploader", "YouTube")

                    val formatsList = mutableListOf(
                        YtDlpFormatOption("best", "Best Quality (1080p/4K)", false),
                        YtDlpFormatOption("720p", "720p HD Video", false),
                        YtDlpFormatOption("480p", "480p SD Video", false),
                        YtDlpFormatOption("mp3", "Audio Only (MP3)", true)
                    )

                    _ytDlpInspectResult.value = YtDlpInspectResult(
                        title = title,
                        durationFormatted = duration,
                        thumbnail = thumb,
                        uploader = uploader,
                        url = url.trim(),
                        formats = formatsList
                    )
                } else {
                    showMessage("Could not inspect URL: HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                showMessage("Inspection failed: ${e.message}")
            } finally {
                _isInspectingYtDlp.value = false
            }
        }
    }

    fun startYtDlpDownload(url: String, format: String, quality: String) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318

        if (ip == null) {
            showMessage("Please connect to PC first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("url", url)
                    put("format", format)
                    put("quality", quality)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://$ip:$port/api/downloader/start")
                    .post(body)
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "{}")
                    val jobId = respJson.optString("jobId", "job-${System.currentTimeMillis()}")
                    val title = respJson.optString("title", "Downloading Media...")
                    val job = YtDlpJob(
                        id = jobId,
                        title = title,
                        progress = 0.15f,
                        speedStr = "Starting...",
                        status = "Downloading",
                        isFinished = false
                    )
                    _activeYtDlpJobs.value = listOf(job) + _activeYtDlpJobs.value
                    showMessage("Download started on PC! Real-time progress active.")
                    pollYtDlpProgress(ip, port, jobId)
                } else {
                    showMessage("Could not start download: HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                showMessage("Download failed: ${e.message}")
            }
        }
    }

    private fun pollYtDlpProgress(ip: String, port: Int, jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var finished = false
            var attempts = 0
            while (!finished && attempts < 120) {
                delay(1000)
                attempts++
                try {
                    val req = Request.Builder().url("http://$ip:$port/api/downloader/status").build()
                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val respJson = JSONObject(resp.body?.string() ?: "{}")
                        val activeCount = respJson.optInt("activeDownloadsCount", 0)

                        // 1. Check active jobs
                        val jobsArr = respJson.optJSONArray("jobs")
                        var foundActive = false
                        if (jobsArr != null) {
                            for (i in 0 until jobsArr.length()) {
                                val jObj = jobsArr.getJSONObject(i)
                                if (jObj.optString("jobId") == jobId) {
                                    foundActive = true
                                    val percent = jObj.optDouble("percent", 0.0).toFloat() / 100f
                                    val speed = jObj.optString("speed", "Downloading...")
                                    val status = jObj.optString("status", "downloading")
                                    val filePath = if (jObj.isNull("filePath")) null else jObj.optString("filePath").takeIf { it.isNotBlank() }
                                    val fileName = if (jObj.isNull("fileName")) null else jObj.optString("fileName").takeIf { it.isNotBlank() }
                                    _activeYtDlpJobs.value = _activeYtDlpJobs.value.map {
                                        if (it.id == jobId) it.copy(
                                            progress = max(it.progress, percent),
                                            speedStr = speed,
                                            status = status.replaceFirstChar { c -> c.uppercase() },
                                            filePath = filePath,
                                            fileName = fileName
                                        ) else it
                                    }
                                    break
                                }
                            }
                        }

                        // 2. Refresh completed list
                        val compArr = respJson.optJSONArray("completedList")
                        if (compArr != null) {
                            val cList = mutableListOf<YtDlpCompletedItem>()
                            for (i in 0 until compArr.length()) {
                                val obj = compArr.getJSONObject(i)
                                val name = obj.optString("fileName", "")
                                val mime = getMimeType(name)
                                cList.add(
                                    YtDlpCompletedItem(
                                        jobId = obj.optString("jobId", "comp-$i"),
                                        title = obj.optString("title", name),
                                        fileName = name,
                                        filePath = obj.optString("filePath", ""),
                                        fileSize = obj.optLong("fileSize", 0L),
                                        totalFormatted = obj.optString("total", "0 B"),
                                        isVideo = mime.startsWith("video/"),
                                        isAudio = mime.startsWith("audio/")
                                    )
                                )
                            }
                            _ytDlpCompletedList.value = cList
                        }

                        // 3. Mark completed if no longer in active queue
                        if (!foundActive && (activeCount == 0 || attempts > 2)) {
                            finished = true
                            val matchingComp = _ytDlpCompletedList.value.firstOrNull()
                            _activeYtDlpJobs.value = _activeYtDlpJobs.value.map {
                                if (it.id == jobId) it.copy(
                                    progress = 1.0f,
                                    status = "Completed",
                                    isFinished = true,
                                    filePath = matchingComp?.filePath ?: it.filePath,
                                    fileName = matchingComp?.fileName ?: it.fileName
                                ) else it
                            }
                            showMessage("✔ Download complete! Ready in PC shared storage.")
                            fetchPcSharedData(ip, port, cachedAuthToken)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun fetchDownloaderStatus() {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318

        if (ip == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("http://$ip:$port/api/downloader/status").build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "{}")
                    _isEngineAvailable.value = respJson.optBoolean("available", true)

                    val compArr = respJson.optJSONArray("completedList")
                    if (compArr != null) {
                        val cList = mutableListOf<YtDlpCompletedItem>()
                        for (i in 0 until compArr.length()) {
                            val obj = compArr.getJSONObject(i)
                            val name = obj.optString("fileName", "")
                            val mime = getMimeType(name)
                            cList.add(
                                YtDlpCompletedItem(
                                    jobId = obj.optString("jobId", "comp-$i"),
                                    title = obj.optString("title", name),
                                    fileName = name,
                                    filePath = obj.optString("filePath", ""),
                                    fileSize = obj.optLong("fileSize", 0L),
                                    totalFormatted = obj.optString("total", "0 B"),
                                    isVideo = mime.startsWith("video/"),
                                    isAudio = mime.startsWith("audio/")
                                )
                            )
                        }
                        _ytDlpCompletedList.value = cList
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearYtDlpInspectResult() {
        _ytDlpInspectResult.value = null
    }

    fun removeYtDlpJob(jobId: String) {
        _activeYtDlpJobs.value = _activeYtDlpJobs.value.filter { it.id != jobId }
    }

    fun streamYtDlpFile(item: YtDlpCompletedItem) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        if (ip == null) {
            showMessage("Please connect to PC to stream.")
            return
        }
        val encodedPath = Uri.encode(item.filePath)
        var streamUrl = "http://$ip:$port/api/stream?filePath=$encodedPath"
        streamUrl = appendToken(streamUrl, cachedAuthToken)

        playMedia(
            StreamMediaItem(
                title = item.title,
                mimeType = if (item.isVideo) "video/mp4" else "audio/mp3",
                url = streamUrl,
                isVideo = item.isVideo
            )
        )
    }

    fun downloadYtDlpFileToPhone(context: Context, item: YtDlpCompletedItem) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        if (ip == null) {
            showMessage("Please connect to PC to download.")
            return
        }
        val encodedPath = Uri.encode(item.filePath)
        var dlUrl = "http://$ip:$port/api/download?filePath=$encodedPath"
        dlUrl = appendToken(dlUrl, cachedAuthToken)

        downloadToPhone(context, dlUrl, item.fileName)
    }

    fun streamYtDlpJob(job: YtDlpJob) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        if (ip == null) {
            showMessage("Please connect to PC to stream.")
            return
        }
        val path = job.filePath
        if (path.isNullOrBlank()) {
            showMessage("File path not yet resolved.")
            return
        }
        val encodedPath = Uri.encode(path)
        var streamUrl = "http://$ip:$port/api/stream?filePath=$encodedPath"
        streamUrl = appendToken(streamUrl, cachedAuthToken)

        playMedia(
            StreamMediaItem(
                title = job.title,
                mimeType = "video/mp4",
                url = streamUrl,
                isVideo = true
            )
        )
    }

    fun downloadYtDlpJobToPhone(context: Context, job: YtDlpJob) {
        val targetPc = _activePairedPc.value
        val ip = targetPc?.ip ?: _discoveredPeers.value.firstOrNull()?.ip
        val port = targetPc?.port ?: _discoveredPeers.value.firstOrNull()?.httpPort ?: 53318
        if (ip == null) {
            showMessage("Please connect to PC to download.")
            return
        }
        val path = job.filePath
        if (path.isNullOrBlank()) {
            showMessage("File path not yet resolved.")
            return
        }
        val encodedPath = Uri.encode(path)
        var dlUrl = "http://$ip:$port/api/download?filePath=$encodedPath"
        dlUrl = appendToken(dlUrl, cachedAuthToken)

        val name = job.fileName ?: "${job.title}.mp4"
        downloadToPhone(context, dlUrl, name)
    }

    // Downloads Manager: Scan device Downloads/RirDrop
    fun refreshDownloadedFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<DownloadedFileItem>()
            val primaryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RirDrop")
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            val dirsToScan = listOfNotNull(primaryDir, fallbackDir)
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

            for (dir in dirsToScan) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile && !file.name.startsWith(".")) {
                            val name = file.name
                            val size = file.length()
                            val mime = getMimeType(name)
                            val isVideo = mime.startsWith("video/") || name.matches(Regex(""".*\.(mp4|mkv|webm|avi|mov)$""", RegexOption.IGNORE_CASE))
                            val isAudio = mime.startsWith("audio/") || name.matches(Regex(""".*\.(mp3|flac|wav|ogg|m4a|aac)$""", RegexOption.IGNORE_CASE))
                            val isImage = mime.startsWith("image/") || name.matches(Regex(""".*\.(jpg|jpeg|png|webp|gif)$""", RegexOption.IGNORE_CASE))

                            list.add(
                                DownloadedFileItem(
                                    file = file,
                                    name = name,
                                    sizeBytes = size,
                                    sizeFormatted = formatBytes(size),
                                    lastModifiedFormatted = sdf.format(Date(file.lastModified())),
                                    mimeType = mime,
                                    isVideo = isVideo,
                                    isAudio = isAudio,
                                    isImage = isImage
                                )
                            )
                        }
                    }
                }
            }
            list.sortByDescending { it.file.lastModified() }
            _downloadedFiles.value = list
        }
    }

    fun deleteDownloadedFile(context: Context, item: DownloadedFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (item.file.exists()) {
                    item.file.delete()
                }
                refreshDownloadedFiles(context)
                showMessage("Deleted ${item.name}")
            } catch (e: Exception) {
                showMessage("Failed to delete file: ${e.message}")
            }
        }
    }

    fun openDownloadedFile(context: Context, item: DownloadedFileItem) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.rirdev.rirdrop.fileprovider", item.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            showMessage("Could not open file: ${e.message}")
        }
    }

    fun shareDownloadedFile(context: Context, item: DownloadedFileItem) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.rirdev.rirdrop.fileprovider", item.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } catch (e: Exception) {
            showMessage("Could not share file: ${e.message}")
        }
    }

    // In-App GitHub Release Update Checker
    fun checkForUpdates(silent: Boolean = false) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/rirdev/RirDrop/releases/latest"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RirDrop-Android/1.0")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", "").replace(Regex("""^v""", RegexOption.IGNORE_CASE), "")
                    val releaseTitle = json.optString("name", "RirDrop v$tagName")
                    val releaseBody = json.optString("body", "Performance improvements, enhanced Material 3 UI, and bug fixes.")
                    val htmlUrl = json.optString("html_url", "https://github.com/rirdev/RirDrop")

                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "").lowercase()
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    val currentVer = "1.0.0"
                    val isNewer = isVersionGreater(tagName, currentVer)

                    if (isNewer) {
                        _updateInfo.value = UpdateInfo(
                            hasUpdate = true,
                            latestVersion = tagName,
                            currentVersion = currentVer,
                            title = releaseTitle,
                            changelog = releaseBody,
                            apkDownloadUrl = apkUrl,
                            htmlUrl = htmlUrl
                        )
                        if (!silent) {
                            showMessage("Update found: v$tagName is available!")
                        }
                    } else {
                        if (!silent) {
                            showMessage("You are on the latest version (v$currentVer)")
                        }
                    }
                } else {
                    if (!silent) showMessage("Could not check updates: HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                if (!silent) showMessage("Update check failed: ${e.message}")
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    private fun isVersionGreater(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val len = max(rParts.size, lParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "webm", "avi", "mov" -> "video/$ext"
            "mp3", "flac", "wav", "ogg", "m4a", "aac" -> "audio/$ext"
            "jpg", "jpeg", "png", "webp", "gif" -> "image/$ext"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip", "tar", "gz" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

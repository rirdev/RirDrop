package com.rirdev.rirdrop

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rirdev.rirdrop.bridge.AndroidBridge
import com.rirdev.rirdrop.network.DiscoveryEngine
import com.rirdev.rirdrop.network.NetworkUtils
import com.rirdev.rirdrop.scanner.QrScannerActivity
import com.rirdev.rirdrop.server.LocalHttpServer
import com.rirdev.rirdrop.server.SharedItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var discoveryEngine: DiscoveryEngine
    private var httpServer: LocalHttpServer? = null
    private val deviceName by lazy { NetworkUtils.getDeviceName() }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedData = result.data?.getStringExtra("SCANNED_QR")
            if (!scannedData.isNullOrBlank()) {
                handleScannedQr(scannedData)
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            val singleUri = result.data?.data
            val pickedItems = mutableListOf<SharedItem>()

            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    resolveUriToSharedItem(uri)?.let { pickedItems.add(it) }
                }
            } else if (singleUri != null) {
                resolveUriToSharedItem(singleUri)?.let { pickedItems.add(it) }
            }

            if (pickedItems.isNotEmpty()) {
                httpServer?.setSharedItems(pickedItems)
                notifyWebFilesSelected(pickedItems)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set modern full-screen immersive dark layout
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        webView = WebView(this)
        setContentView(webView)

        checkPermissions()
        startEngines()
        setupWebView()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startEngines() {
        // 1. Discovery Engine
        discoveryEngine = DiscoveryEngine(this, deviceName, 53318) { peers ->
            runOnUiThread {
                notifyWebPeersUpdated(peers)
            }
        }
        discoveryEngine.start()

        // 2. Embedded HTTP Server
        try {
            httpServer = LocalHttpServer(this, 53318, deviceName).apply {
                start()
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(0xFF0A0B0E.toInt())

        // Register Native JavaScript Bridge
        val bridge = AndroidBridge(this, discoveryEngine)
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAndroidBridgeShim()
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    private fun injectAndroidBridgeShim() {
        val shimScript = """
            (function() {
                if (window.AndroidBridge && !window.rirdropAPI) {
                    window.rirdropAndroid = window.AndroidBridge;
                    window.rirdropAPI = {
                        isAndroid: true,
                        scanQrCode: function() { window.AndroidBridge.scanQrCode(); },
                        getHostInfo: function() {
                            return Promise.resolve(JSON.parse(window.AndroidBridge.getHostInfo()));
                        },
                        getPeers: function() {
                            return Promise.resolve(JSON.parse(window.AndroidBridge.getPeers()));
                        },
                        scanNow: function() { window.AndroidBridge.scanNow(); },
                        pickFiles: function() { window.AndroidBridge.pickFiles(); },
                        startDownload: function(opts) {
                            window.AndroidBridge.startDownload(opts.url, opts.fileName || 'download');
                        },
                        openDownloadsFolder: function() { window.AndroidBridge.openDownloadsFolder(); },
                        showToast: function(msg) { window.AndroidBridge.showToast(msg); },
                        vibrate: function(ms) { window.AndroidBridge.vibrate(ms || 60); },
                        generateQr: function(text) {
                            if (window.AndroidBridge && window.AndroidBridge.generateQr) {
                                return Promise.resolve(window.AndroidBridge.generateQr(text));
                            }
                            return Promise.resolve(null);
                        }
                    };
                    console.log('[RirDrop Android] Native bridge injected successfully.');
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(shimScript, null)
    }

    fun launchQrScanner() {
        val intent = Intent(this, QrScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }

    fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        filePickerLauncher.launch(intent)
    }

    private fun handleScannedQr(data: String) {
        val escaped = JSONObject.quote(data)
        val js = """
            if (typeof window.handleScannedQrCode === 'function') {
                window.handleScannedQrCode($escaped);
            } else {
                console.log('Scanned QR:', $escaped);
                if (window.rirdropAPI && window.rirdropAPI.showToast) {
                    window.rirdropAPI.showToast('Scanned PC QR Code!');
                }
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun notifyWebPeersUpdated(peers: List<com.rirdev.rirdrop.network.PeerDevice>) {
        val arr = JSONArray()
        for (p in peers) {
            arr.put(JSONObject().apply {
                put("alias", p.alias)
                put("os", p.os)
                put("ip", p.ip)
                put("httpPort", p.httpPort)
                put("deviceType", p.deviceType)
                put("lastSeen", p.lastSeen)
            })
        }
        val js = """
            if (typeof window.onAndroidPeersUpdated === 'function') {
                window.onAndroidPeersUpdated($arr);
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun notifyWebFilesSelected(items: List<SharedItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("size", item.size)
                put("mimeType", item.mimeType)
            })
        }
        val js = """
            if (typeof window.onAndroidFilesSelected === 'function') {
                window.onAndroidFilesSelected($arr);
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun resolveUriToSharedItem(uri: Uri): SharedItem? {
        var name = "Shared_File"
        var size = 0L
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) name = it.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = it.getLong(sizeIndex)
            }
        }

        return SharedItem(
            id = UUID.randomUUID().toString().substring(0, 8),
            name = name,
            size = size,
            mimeType = mimeType,
            uri = uri
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryEngine.stop()
        try { httpServer?.stop() } catch (_: Exception) {}
    }
}

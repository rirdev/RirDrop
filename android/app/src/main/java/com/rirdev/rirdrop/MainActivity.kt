package com.rirdev.rirdrop

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.rirdev.rirdrop.network.DiscoveryEngine
import com.rirdev.rirdrop.network.NetworkUtils
import com.rirdev.rirdrop.scanner.QrScannerActivity
import com.rirdev.rirdrop.server.LocalHttpServer
import com.rirdev.rirdrop.server.SharedItem
import com.rirdev.rirdrop.ui.RirDropApp
import com.rirdev.rirdrop.ui.RirDropViewModel
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<RirDropViewModel>()
    private lateinit var discoveryEngine: DiscoveryEngine
    private var httpServer: LocalHttpServer? = null
    private val deviceName by lazy { NetworkUtils.getDeviceName() }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedData = result.data?.getStringExtra("SCANNED_QR")
            if (!scannedData.isNullOrBlank()) {
                viewModel.handleScannedQr(scannedData)
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
                viewModel.addSharedFiles(pickedItems)
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.addSharedFolder(this, uri)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        checkPermissions()
        startEngines()

        setContent {
            RirDropApp(
                viewModel = viewModel,
                onScanQrClicked = { launchQrScanner() },
                onPickFilesClicked = { launchFilePicker() },
                onPickFolderClicked = { launchFolderPicker() }
            )
        }
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
        // 1. Discovery Engine (UDP 53317 with Wifi MulticastLock)
        discoveryEngine = DiscoveryEngine(this, deviceName, 53318) { peers ->
            runOnUiThread {
                viewModel.updateDiscoveredPeers(peers)
            }
        }
        discoveryEngine.start()

        // 2. Embedded HTTP Server (TCP 53318)
        try {
            httpServer = LocalHttpServer(this, 53318, deviceName).apply {
                start()
            }
        } catch (_: Exception) {}

        viewModel.attachEngines(discoveryEngine, httpServer)
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

    fun launchFolderPicker() {
        folderPickerLauncher.launch(null)
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

    override fun onDestroy() {
        super.onDestroy()
        discoveryEngine.stop()
        try { httpServer?.stop() } catch (_: Exception) {}
    }
}

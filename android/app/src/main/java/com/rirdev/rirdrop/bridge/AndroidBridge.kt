package com.rirdev.rirdrop.bridge

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rirdev.rirdrop.MainActivity
import com.rirdev.rirdrop.network.DiscoveryEngine
import com.rirdev.rirdrop.network.NetworkUtils
import com.rirdev.rirdrop.service.TransferService
import org.json.JSONArray
import org.json.JSONObject

class AndroidBridge(
    private val activity: MainActivity,
    private val discoveryEngine: DiscoveryEngine
) {

    @JavascriptInterface
    fun scanQrCode() {
        activity.runOnUiThread {
            activity.launchQrScanner()
        }
    }

    @JavascriptInterface
    fun getHostInfo(): String {
        val ip = NetworkUtils.getLocalIpAddress()
        val deviceName = NetworkUtils.getDeviceName()
        return JSONObject().apply {
            put("hostname", deviceName)
            put("username", deviceName)
            put("platform", "android")
            put("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64")
            put("primaryIp", ip)
            put("interfaces", JSONArray().apply {
                put(JSONObject().apply {
                    put("interface", "wlan0")
                    put("address", ip)
                })
            })
        }.toString()
    }

    @JavascriptInterface
    fun getPeers(): String {
        val peers = discoveryEngine.getPeers()
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
        return arr.toString()
    }

    @JavascriptInterface
    fun scanNow() {
        discoveryEngine.scanNow()
    }

    @JavascriptInterface
    fun pickFiles() {
        activity.runOnUiThread {
            activity.launchFilePicker()
        }
    }

    @JavascriptInterface
    fun startDownload(url: String, fileName: String) {
        val intent = Intent(activity, TransferService::class.java).apply {
            action = TransferService.ACTION_START_DOWNLOAD
            putExtra(TransferService.EXTRA_URL, url)
            putExtra(TransferService.EXTRA_FILE_NAME, fileName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }

    @JavascriptInterface
    fun openDownloadsFolder() {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            showToast("Open Downloads folder in Files app")
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun vibrate(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun generateQr(text: String): String {
        return try {
            val size = 300
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()
            "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }
}

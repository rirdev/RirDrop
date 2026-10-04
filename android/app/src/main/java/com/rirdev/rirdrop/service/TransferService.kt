package com.rirdev.rirdrop.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.rirdev.rirdrop.MainActivity
import com.rirdev.rirdrop.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class TransferService : Service() {

    private val channelId = "rirdrop_transfers"
    private val notificationId = 1001
    private var wakeLock: PowerManager.WakeLock? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RirDrop:TransferWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "downloaded_file"
                startForegroundNotification("Downloading $fileName...", 0)
                wakeLock?.acquire(30 * 60 * 1000L) // 30 min max

                scope.launch {
                    downloadFile(url, fileName)
                }
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadFile(url: String, fileName: String) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                updateNotification("Download failed: HTTP ${response.code}", 0, false)
                delay(3000)
                stopForegroundService()
                return
            }

            val body = response.body ?: run {
                stopForegroundService()
                return
            }

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "RirDrop"
            ).apply { mkdirs() }

            val destFile = File(downloadDir, fileName)
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            lastUpdate = now
                            val pct = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
                            val mbDownloaded = downloadedBytes / (1024 * 1024)
                            val mbTotal = totalBytes / (1024 * 1024)
                            updateNotification("Downloading $fileName (${mbDownloaded}MB / ${mbTotal}MB)", pct, true)
                        }
                    }
                }
            }

            updateNotification("Saved $fileName to Downloads/RirDrop", 100, false)
            delay(2000)
        } catch (e: Exception) {
            updateNotification("Download error: ${e.message}", 0, false)
            delay(3000)
        } finally {
            stopForegroundService()
        }
    }

    private fun startForegroundNotification(text: String, progress: Int) {
        val notification = buildNotification(text, progress, progress in 0..99)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(text: String, progress: Int, ongoing: Boolean) {
        val notification = buildNotification(text, progress, ongoing)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private fun buildNotification(text: String, progress: Int, ongoing: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RirDrop File Transfer")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else if (ongoing) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun stopForegroundService() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RirDrop File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active local file transfers"
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_DOWNLOAD = "com.rirdev.rirdrop.START_DOWNLOAD"
        const val ACTION_STOP = "com.rirdev.rirdrop.STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "fileName"
    }
}

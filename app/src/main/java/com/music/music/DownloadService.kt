package com.music.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val notificationId = 1001
    private val channelId = "download_channel"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra("video_id") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "Video"
        val url = intent.getStringExtra("url") ?: return START_NOT_STICKY

        startForeground(notificationId, createNotification("Téléchargement: $title", 0))

        scope.launch {
            downloadVideo(videoId, title, url)
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadVideo(videoId: String, title: String, url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                showNotification("Échec du téléchargement", -1)
                stopSelf()
                return
            }

            val body = response.body ?: run {
                showNotification("Fichier vide", -1)
                stopSelf()
                return
            }

            // Créer le dossier de téléchargement
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Nettoyer le titre pour le nom de fichier
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), "").take(50).trim()
            val fileName = "${safeTitle}_$videoId.mp4"
            val file = File(downloadsDir, fileName)

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgress = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (progress != lastProgress && progress % 5 == 0) {
                                lastProgress = progress
                                withContext(Dispatchers.Main) {
                                    showNotification("$title - $progress%", progress)
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                showNotification("Téléchargement terminé: $title", 100)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showNotification("Erreur: ${e.message}", -1)
            }
        } finally {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Téléchargements",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Int): android.app.Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dailymotion Downloader")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..99) {
            builder.setProgress(100, progress, false)
            builder.setOngoing(true)
        } else if (progress == 100) {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
        } else {
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
        }

        return builder.build()
    }

    private fun showNotification(text: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, createNotification(text, progress))
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

package com.music.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
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
import java.io.OutputStream
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
        .followRedirects(true)
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
            if (url.contains(".m3u8")) {
                downloadHlsVideo(videoId, title, url)
            } else {
                downloadDirectVideo(videoId, title, url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showNotification("Erreur: ${e.message}", -1)
            }
            stopSelf()
        }
    }

    private fun getOutputStream(fileName: String, mimeType: String): Pair<OutputStream, Uri?> {
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\s-]"), "").take(100)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : utiliser MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeFileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Dailymotion")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Impossible de créer le fichier")

            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw Exception("Impossible d'ouvrir le fichier")

            Pair(outputStream, uri)
        } else {
            // Android 9 et moins : dossier Téléchargements classique
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dailymotionDir = File(downloadsDir, "Dailymotion")
            if (!dailymotionDir.exists()) {
                dailymotionDir.mkdirs()
            }
            val file = File(dailymotionDir, safeFileName)
            Pair(FileOutputStream(file), null)
        }
    }

    private fun finalizeDownload(uri: Uri?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, contentValues, null, null)
        }
    }

    private suspend fun downloadHlsVideo(videoId: String, title: String, m3u8Url: String) {
        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            withContext(Dispatchers.Main) {
                showNotification("Récupération des segments...", 0)
            }

            val request = Request.Builder()
                .url(m3u8Url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: throw Exception("Manifest vide")

            val segments = mutableListOf<String>()
            val baseUrl = m3u8Url.substringBeforeLast("/")

            content.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val segmentUrl = if (trimmed.startsWith("http")) {
                        trimmed
                    } else {
                        "$baseUrl/$trimmed"
                    }
                    segments.add(segmentUrl)
                }
            }

            if (segments.isEmpty()) {
                throw Exception("Aucun segment trouvé")
            }

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), "").take(50).trim()
            val fileName = "${safeTitle}_$videoId.ts"

            val (stream, fileUri) = getOutputStream(fileName, "video/mp2t")
            outputStream = stream
            uri = fileUri

            segments.forEachIndexed { index, segmentUrl ->
                val progress = ((index + 1) * 100) / segments.size

                withContext(Dispatchers.Main) {
                    showNotification("$title - $progress%", progress)
                }

                val segmentRequest = Request.Builder()
                    .url(segmentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()

                val segmentResponse = client.newCall(segmentRequest).execute()
                segmentResponse.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream?.write(buffer, 0, bytesRead)
                    }
                }
            }

            outputStream?.close()
            finalizeDownload(uri)

            withContext(Dispatchers.Main) {
                showNotification("Terminé: $title (dans Téléchargements/Dailymotion)", 100)
                sendDownloadCompleteBroadcast(title, true, null)
            }

        } catch (e: Exception) {
            outputStream?.close()
            sendDownloadCompleteBroadcast(title, false, e.message)
            throw e
        } finally {
            stopSelf()
        }
    }

    private fun sendDownloadCompleteBroadcast(title: String, success: Boolean, error: String?) {
        val intent = Intent("com.music.music.DOWNLOAD_COMPLETE").apply {
            putExtra("title", title)
            putExtra("success", success)
            putExtra("error", error ?: "")
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private suspend fun downloadDirectVideo(videoId: String, title: String, url: String) {
        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Erreur HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Fichier vide")

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), "").take(50).trim()
            val fileName = "${safeTitle}_$videoId.mp4"

            val (stream, fileUri) = getOutputStream(fileName, "video/mp4")
            outputStream = stream
            uri = fileUri

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgress = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream?.write(buffer, 0, bytesRead)
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

            outputStream?.close()
            finalizeDownload(uri)

            withContext(Dispatchers.Main) {
                showNotification("Terminé: $title (dans Téléchargements/Dailymotion)", 100)
                sendDownloadCompleteBroadcast(title, true, null)
            }

        } catch (e: Exception) {
            outputStream?.close()
            sendDownloadCompleteBroadcast(title, false, e.message)
            throw e
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

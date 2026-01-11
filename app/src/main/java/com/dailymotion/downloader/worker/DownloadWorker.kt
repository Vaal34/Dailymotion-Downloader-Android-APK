package com.dailymotion.downloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailymotion.downloader.R
import com.dailymotion.downloader.data.local.AppDatabase
import com.dailymotion.downloader.data.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val downloadDao = AppDatabase.getDatabase(context).downloadDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return@withContext Result.failure()

        val download = downloadDao.getDownloadById(downloadId)
            ?: return@withContext Result.failure()

        try {
            createNotificationChannel()
            setForeground(createForegroundInfo(download.title, 0))

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(download.url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                updateDownloadStatus(downloadId, DownloadStatus.FAILED, "Erreur de téléchargement")
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                updateDownloadStatus(downloadId, DownloadStatus.FAILED, "Fichier vide")
                return@withContext Result.failure()
            }

            val totalBytes = body.contentLength()
            val downloadsDir = File(context.getExternalFilesDir(null), "Dailymotion")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val fileName = "${download.videoId}.mp4"
            val file = File(downloadsDir, fileName)

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)

            var downloadedBytes = 0L
            val buffer = ByteArray(8192)
            var lastProgress = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                updateProgress(downloadId, progress, downloadedBytes, totalBytes)
                                setForeground(createForegroundInfo(download.title, progress))
                            }
                        }
                    }
                }
            }

            // Mise à jour finale
            downloadDao.updateDownload(
                download.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    filePath = file.absolutePath,
                    fileSize = totalBytes,
                    downloadedSize = downloadedBytes,
                    completedAt = System.currentTimeMillis()
                )
            )

            showCompletedNotification(download.title)
            Result.success()

        } catch (e: Exception) {
            updateDownloadStatus(downloadId, DownloadStatus.FAILED, e.message ?: "Erreur inconnue")
            Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Erreur inconnue"))
            )
        }
    }

    private suspend fun updateProgress(
        downloadId: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val download = downloadDao.getDownloadById(downloadId) ?: return
        downloadDao.updateDownload(
            download.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = progress,
                downloadedSize = downloadedBytes,
                fileSize = totalBytes
            )
        )
    }

    private suspend fun updateDownloadStatus(
        downloadId: Long,
        status: DownloadStatus,
        errorMessage: String? = null
    ) {
        val download = downloadDao.getDownloadById(downloadId) ?: return
        downloadDao.updateDownload(
            download.copy(
                status = status,
                errorMessage = errorMessage
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Téléchargements",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications de téléchargement de vidéos"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Téléchargement en cours")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompletedNotification(title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Téléchargement terminé")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
    }
}

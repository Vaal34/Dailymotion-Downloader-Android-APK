package com.dailymotion.downloader.data.repository

import com.dailymotion.downloader.data.local.DownloadDao
import com.dailymotion.downloader.data.model.Download
import com.dailymotion.downloader.data.model.DownloadStatus
import com.dailymotion.downloader.data.remote.DailymotionApi
import kotlinx.coroutines.flow.Flow

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val dailymotionApi: DailymotionApi
) {
    fun getAllDownloads(): Flow<List<Download>> {
        return downloadDao.getAllDownloads()
    }

    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>> {
        return downloadDao.getDownloadsByStatus(status)
    }

    suspend fun getDownloadById(id: Long): Download? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun insertDownload(download: Download): Long {
        return downloadDao.insertDownload(download)
    }

    suspend fun updateDownload(download: Download) {
        downloadDao.updateDownload(download)
    }

    suspend fun deleteDownload(download: Download) {
        downloadDao.deleteDownload(download)
    }

    suspend fun deleteDownloadById(id: Long) {
        downloadDao.deleteDownloadById(id)
    }

    suspend fun getVideoInfo(videoId: String) = dailymotionApi.getVideoInfo(videoId)

    fun extractVideoId(url: String): String? {
        // Extraire l'ID de la vidéo depuis différents formats d'URL Dailymotion
        val patterns = listOf(
            Regex("""dailymotion\.com/video/([a-zA-Z0-9]+)"""),
            Regex("""dai\.ly/([a-zA-Z0-9]+)"""),
            Regex("""dailymotion\.com/embed/video/([a-zA-Z0-9]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }
}

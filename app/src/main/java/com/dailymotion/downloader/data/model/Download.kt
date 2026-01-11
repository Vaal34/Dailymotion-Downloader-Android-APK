package com.dailymotion.downloader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val url: String,
    val filePath: String?,
    val status: DownloadStatus,
    val progress: Int = 0,
    val fileSize: Long = 0,
    val downloadedSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

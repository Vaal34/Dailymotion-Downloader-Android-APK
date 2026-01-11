package com.dailymotion.downloader.data.local

import androidx.room.*
import com.dailymotion.downloader.data.model.Download
import com.dailymotion.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): Download?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: Download): Long

    @Update
    suspend fun updateDownload(download: Download)

    @Delete
    suspend fun deleteDownload(download: Download)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteDownloadsByStatus(status: DownloadStatus)
}

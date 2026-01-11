package com.dailymotion.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dailymotion.downloader.data.local.AppDatabase
import com.dailymotion.downloader.data.model.Download
import com.dailymotion.downloader.data.model.DownloadStatus
import com.dailymotion.downloader.data.remote.DailymotionExtractor
import com.dailymotion.downloader.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val extractor = DailymotionExtractor()

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadDao.getAllDownloads().collect { downloadList ->
                _downloads.value = downloadList
            }
        }
    }

    fun downloadVideo(url: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val videoId = extractor.extractVideoId(url)
                if (videoId == null) {
                    _uiState.value = UiState.Error("URL invalide. Veuillez entrer une URL Dailymotion valide.")
                    return@launch
                }

                val videoInfo = try {
                    extractor.extractVideoInfo(videoId)
                } catch (e: Exception) {
                    _uiState.value = UiState.Error("Impossible de récupérer les infos de la vidéo: ${e.message}")
                    return@launch
                }

                val download = Download(
                    videoId = videoId,
                    title = videoInfo.title,
                    thumbnailUrl = videoInfo.thumbnailUrl,
                    url = videoInfo.downloadUrl,
                    filePath = null,
                    status = DownloadStatus.PENDING
                )

                val downloadId = downloadDao.insertDownload(download)

                // Lancer le téléchargement avec WorkManager
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to downloadId)
                    )
                    .build()

                WorkManager.getInstance(getApplication()).enqueue(workRequest)

                _uiState.value = UiState.Success("Téléchargement démarré: ${videoInfo.title}")

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur: ${e.message ?: "Erreur inconnue"}")
            }
        }
    }

    fun deleteDownload(download: Download) {
        viewModelScope.launch {
            downloadDao.deleteDownload(download)
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }
}

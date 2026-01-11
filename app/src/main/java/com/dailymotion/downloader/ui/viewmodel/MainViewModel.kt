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
import com.dailymotion.downloader.data.repository.DownloadRepository
import com.dailymotion.downloader.di.RetrofitInstance
import com.dailymotion.downloader.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val downloadDao = AppDatabase.getDatabase(application).downloadDao()
        repository = DownloadRepository(downloadDao, RetrofitInstance.dailymotionApi)

        viewModelScope.launch {
            repository.getAllDownloads().collect { downloadList ->
                _downloads.value = downloadList
            }
        }
    }

    fun downloadVideo(url: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val videoId = repository.extractVideoId(url)
                if (videoId == null) {
                    _uiState.value = UiState.Error("URL invalide. Veuillez entrer une URL Dailymotion valide.")
                    return@launch
                }

                val videoInfo = repository.getVideoInfo(videoId)

                // Récupérer la meilleure qualité disponible
                val downloadUrl = getBestQualityUrl(videoInfo.qualities)
                if (downloadUrl == null) {
                    _uiState.value = UiState.Error("Aucun lien de téléchargement trouvé")
                    return@launch
                }

                val download = Download(
                    videoId = videoId,
                    title = videoInfo.title,
                    thumbnailUrl = videoInfo.thumbnailUrl,
                    url = downloadUrl,
                    filePath = null,
                    status = DownloadStatus.PENDING
                )

                val downloadId = repository.insertDownload(download)

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

    private fun getBestQualityUrl(qualities: Map<String, List<com.dailymotion.downloader.data.remote.QualityInfo>>?): String? {
        if (qualities == null) return null

        // Ordre de préférence des qualités
        val preferredQualities = listOf("1080", "720", "480", "380", "240")

        for (quality in preferredQualities) {
            val qualityList = qualities[quality]
            if (!qualityList.isNullOrEmpty()) {
                val mp4Quality = qualityList.find { it.type == "video/mp4" || it.type == "application/x-mpegURL" }
                if (mp4Quality != null) {
                    return mp4Quality.url
                }
            }
        }

        // Si aucune qualité préférée n'est trouvée, prendre la première disponible
        return qualities.values.firstOrNull()?.firstOrNull()?.url
    }

    fun deleteDownload(download: Download) {
        viewModelScope.launch {
            repository.deleteDownload(download)
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

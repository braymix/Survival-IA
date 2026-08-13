package com.survivalwiki.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survivalwiki.app.data.DownloadState
import com.survivalwiki.app.data.ModelDownloader
import com.survivalwiki.app.data.ModelRepository
import com.survivalwiki.app.data.SettingsStore
import com.survivalwiki.core.data.saved.SavedAnswerDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Artefatti scaricabili (URL congelati nel download_manifest; qui i default noti). */
enum class DownloadTarget(val displayName: String, val url: String) {
    EMBEDDING(
        "Modello semantico (e5-small, ~118 MB)",
        "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/onnx/model_quantized.onnx",
    ),
    GENERATIVE(
        "Modello generativo (Qwen2.5-0.5B, ~398 MB)",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
    ),
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: SettingsStore,
    private val models: ModelRepository,
    private val downloader: ModelDownloader,
    private val savedDao: SavedAnswerDao,
) : ViewModel() {

    private val _downloads = MutableStateFlow<Map<DownloadTarget, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<DownloadTarget, DownloadState>> = _downloads.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    init { refreshStorage() }

    fun hasEmbedding() = models.hasEmbeddingModel()
    fun hasGenerative() = models.hasGenerativeModel()

    fun setThreshold(v: Float) = settings.setConfidenceThreshold(v)
    fun setExtractsOnly(v: Boolean) = settings.setExtractsOnly(v)
    fun setLowPower(v: Boolean) = settings.setLowPower(v)

    fun startDownload(target: DownloadTarget) {
        val dest = when (target) {
            DownloadTarget.EMBEDDING -> models.embeddingModel
            DownloadTarget.GENERATIVE -> models.generativeModel
        }
        viewModelScope.launch {
            // sha256 vuoto qui: da riempire nel download_manifest per la release.
            downloader.download(target.url, dest, expectedSha256 = "").collect { st ->
                _downloads.value = _downloads.value.toMutableMap().apply { put(target, st) }
                if (st is DownloadState.Done) refreshStorage()
            }
        }
    }

    fun deleteModels() {
        models.embeddingModel.delete()
        models.generativeModel.delete()
        refreshStorage()
    }

    fun wipeSaved() {
        viewModelScope.launch { savedDao.deleteAll() }
    }

    private fun refreshStorage() {
        var total = 0L
        if (models.embeddingModel.exists()) total += models.embeddingModel.length()
        if (models.generativeModel.exists()) total += models.generativeModel.length()
        _storageBytes.value = total
    }
}

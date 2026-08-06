package com.survivalwiki.app.ui.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survivalwiki.app.data.ModelRepository
import com.survivalwiki.app.data.RetrieverFactory
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.RetrievalOutcome
import com.survivalwiki.core.retrieval.ScoredChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stato della schermata Risposta. In Fase 3 la "risposta" è l'insieme di estratti citati. */
sealed interface AnswerUiState {
    data object Loading : AnswerUiState
    data class Blocked(val reason: String) : AnswerUiState
    data class Grounded(
        val passages: List<ScoredChunk>,
        val hasMedical: Boolean,
        val embeddingModelAvailable: Boolean,
    ) : AnswerUiState
    data class Empty(val nearbyTopics: List<String>) : AnswerUiState
    data class Error(val message: String) : AnswerUiState
}

@HiltViewModel
class RetrievalViewModel @Inject constructor(
    private val factory: RetrieverFactory,
    private val models: ModelRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: String = savedStateHandle.get<String>("q").orEmpty()
    private val category: Category? =
        savedStateHandle.get<String>("cat")?.takeIf { it.isNotBlank() }?.let { Category.fromId(it) }

    private val _state = MutableStateFlow<AnswerUiState>(AnswerUiState.Loading)
    val state: StateFlow<AnswerUiState> = _state.asStateFlow()

    init { search() }

    fun search() {
        viewModelScope.launch {
            _state.value = AnswerUiState.Loading
            _state.value = try {
                when (val outcome = factory.current().retrieve(query, category)) {
                    is RetrievalOutcome.Blocked -> AnswerUiState.Blocked(outcome.reason)
                    is RetrievalOutcome.Empty -> AnswerUiState.Empty(outcome.nearbyTopics)
                    is RetrievalOutcome.Grounded -> AnswerUiState.Grounded(
                        passages = outcome.passages,
                        hasMedical = outcome.passages.any { it.chunk.category == Category.MEDICO },
                        embeddingModelAvailable = models.hasEmbeddingModel(),
                    )
                }
            } catch (e: Exception) {
                AnswerUiState.Error(e.message ?: "Errore imprevisto")
            }
        }
    }
}

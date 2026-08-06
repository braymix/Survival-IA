package com.survivalwiki.app.ui.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survivalwiki.app.data.ModelRepository
import com.survivalwiki.app.data.RetrieverFactory
import com.survivalwiki.app.data.SettingsStore
import com.survivalwiki.core.llm.LlmEngine
import com.survivalwiki.core.retrieval.AnswerValidation
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.CitationValidator
import com.survivalwiki.core.retrieval.PromptBuilder
import com.survivalwiki.core.retrieval.RetrievalOutcome
import com.survivalwiki.core.retrieval.ScoredChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sotto-stato della generazione LLM, sovrapposto agli estratti già mostrati. */
sealed interface GenerationState {
    /** Nessuna generazione: modalità "solo estratti" (modello assente o disattivato). */
    data object Disabled : GenerationState
    data object Loading : GenerationState
    data class Streaming(val text: String) : GenerationState
    data class Done(val text: String, val validation: AnswerValidation) : GenerationState
    data class Failed(val message: String) : GenerationState
}

sealed interface AnswerUiState {
    data object Loading : AnswerUiState
    data class Blocked(val reason: String) : AnswerUiState
    data class Grounded(
        val passages: List<ScoredChunk>,
        val hasMedical: Boolean,
        val embeddingModelAvailable: Boolean,
        val generation: GenerationState = GenerationState.Disabled,
    ) : AnswerUiState
    data class Empty(val nearbyTopics: List<String>) : AnswerUiState
    data class Error(val message: String) : AnswerUiState
}

@HiltViewModel
class RetrievalViewModel @Inject constructor(
    private val factory: RetrieverFactory,
    private val models: ModelRepository,
    private val settings: SettingsStore,
    private val engine: LlmEngine,
    private val promptBuilder: PromptBuilder,
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

            // Generazione vincolata opzionale (solo se modello presente e non in modalità solo-estratti).
            val grounded = _state.value as? AnswerUiState.Grounded ?: return@launch
            if (models.hasGenerativeModel() && !settings.extractsOnly.value) {
                generate(grounded)
            }
        }
    }

    private fun setGeneration(state: GenerationState) {
        val g = _state.value as? AnswerUiState.Grounded ?: return
        _state.value = g.copy(generation = state)
    }

    private suspend fun generate(grounded: AnswerUiState.Grounded) {
        setGeneration(GenerationState.Loading)
        try {
            engine.load(models.generativeModel.absolutePath)
            val prompt = promptBuilder.build(query, grounded.passages)
            val buffer = StringBuilder()
            engine.generate(prompt.text).collect { token ->
                buffer.append(token)
                setGeneration(GenerationState.Streaming(buffer.toString()))
            }
            val validation = CitationValidator.validate(buffer.toString(), grounded.passages.size)
            setGeneration(GenerationState.Done(buffer.toString(), validation))
        } catch (e: Exception) {
            // Fallimento (es. libreria nativa assente): si resta sugli estratti.
            setGeneration(GenerationState.Failed(e.message ?: "Generazione non disponibile"))
        }
    }
}

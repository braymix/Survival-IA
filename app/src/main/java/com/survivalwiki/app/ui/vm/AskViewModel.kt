package com.survivalwiki.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.CorpusManifest
import com.survivalwiki.core.retrieval.CorpusReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AskViewModel @Inject constructor(
    private val corpus: CorpusReader,
) : ViewModel() {

    private val _manifest = MutableStateFlow<CorpusManifest?>(null)
    val manifest: StateFlow<CorpusManifest?> = _manifest.asStateFlow()

    private val _categories = MutableStateFlow<List<Pair<Category, Int>>>(emptyList())
    val categories: StateFlow<List<Pair<Category, Int>>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            _manifest.value = runCatching { corpus.manifest() }.getOrNull()
            _categories.value = runCatching { corpus.categories() }.getOrDefault(emptyList())
        }
    }
}

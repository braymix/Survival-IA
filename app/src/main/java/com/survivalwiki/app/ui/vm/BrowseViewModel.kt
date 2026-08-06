package com.survivalwiki.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.CorpusDocument
import com.survivalwiki.core.retrieval.CorpusReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val corpus: CorpusReader,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Pair<Category, Int>>>(emptyList())
    val categories: StateFlow<List<Pair<Category, Int>>> = _categories.asStateFlow()

    private val _selected = MutableStateFlow<Category?>(null)
    val selected: StateFlow<Category?> = _selected.asStateFlow()

    private val _documents = MutableStateFlow<List<CorpusDocument>>(emptyList())
    val documents: StateFlow<List<CorpusDocument>> = _documents.asStateFlow()

    init {
        viewModelScope.launch {
            _categories.value = runCatching { corpus.categories() }.getOrDefault(emptyList())
        }
    }

    fun select(category: Category?) {
        _selected.value = category
        if (category == null) {
            _documents.value = emptyList()
        } else {
            viewModelScope.launch {
                _documents.value = runCatching { corpus.documents(category) }.getOrDefault(emptyList())
            }
        }
    }
}

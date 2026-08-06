package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survivalwiki.app.ui.components.label
import com.survivalwiki.app.ui.vm.AskViewModel
import com.survivalwiki.core.retrieval.Category

@Composable
fun AskScreen(
    onAsk: (query: String, category: Category?) -> Unit,
    viewModel: AskViewModel = hiltViewModel(),
) {
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Category?>(null) }

    fun submit() {
        val q = text.trim()
        if (q.isNotEmpty()) onAsk(q, selected)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SurvivalWiki AI", style = MaterialTheme.typography.headlineSmall)

        val info = manifest?.let {
            "Archivio: ${it.documentCount} documenti, ${it.chunkCount} sezioni" +
                if (it.builtAtIso.isNotBlank()) " · aggiornato il ${it.builtAtIso.take(10)}" else ""
        } ?: "Archivio in caricamento…"
        Text(info, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Cosa ti serve sapere?") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )

        // Chip di categoria (filtro opzionale).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { (cat, count) ->
                FilterChip(
                    selected = selected == cat,
                    onClick = { selected = if (selected == cat) null else cat },
                    label = { Text("${cat.label()} ($count)") },
                )
            }
        }

        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cerca nell'archivio")
        }
    }
}

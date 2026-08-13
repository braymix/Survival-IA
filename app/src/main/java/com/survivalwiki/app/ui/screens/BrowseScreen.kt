package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survivalwiki.app.ui.components.label
import com.survivalwiki.app.ui.vm.BrowseViewModel

/**
 * Modalità wiki pura, senza LLM: categoria → documenti → sezioni del documento.
 * Istantanea e sempre disponibile offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(viewModel: BrowseViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val openDoc by viewModel.openDoc.collectAsStateWithLifecycle()
    val docChunks by viewModel.docChunks.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            // Livello 3: documento aperto → sezioni leggibili.
            openDoc != null -> {
                Header(openDoc!!.title) { viewModel.closeDocument() }
                Text(
                    listOfNotNull(openDoc!!.author, openDoc!!.year?.toString(),
                        "Licenza: ${openDoc!!.license}").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docChunks, key = { it.chunkId }) { chunk ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                chunk.section?.let {
                                    Text(it, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall)
                                }
                                Text(chunk.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Livello 2: documenti di una categoria.
            selected != null -> {
                Header(selected!!.label()) { viewModel.select(null) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(documents, key = { it.docId }) { doc ->
                        Card(
                            Modifier.fillMaxWidth().clickable { viewModel.openDocument(doc) },
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(doc.title, fontWeight = FontWeight.SemiBold)
                                Text("Licenza: ${doc.license}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }

            // Livello 1: categorie.
            else -> {
                Text("Sfoglia per categoria", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp))
                if (categories.isEmpty()) {
                    Text("Archivio non disponibile.", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories, key = { it.first.id }) { (cat, count) ->
                        Card(Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text(cat.label()) },
                                trailingContent = { Text("$count") },
                                modifier = Modifier.clickable { viewModel.select(cat) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

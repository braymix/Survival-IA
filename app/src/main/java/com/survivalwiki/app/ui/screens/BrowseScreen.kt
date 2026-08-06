package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survivalwiki.app.ui.components.label
import com.survivalwiki.app.ui.vm.BrowseViewModel

/**
 * Modalità wiki pura, senza LLM: categoria → documenti. Istantanea e sempre disponibile offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(viewModel: BrowseViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (selected == null) {
            Text("Sfoglia per categoria", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn {
                items(categories) { (cat, count) ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(cat.label()) },
                            trailingContent = { Text("$count") },
                            modifier = Modifier.clickable { viewModel.select(cat) },
                        )
                    }
                }
            }
        } else {
            Column {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.select(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                    Text(selected!!.label(), style = MaterialTheme.typography.headlineSmall)
                }
                LazyColumn {
                    items(documents) { doc ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(doc.title, fontWeight = FontWeight.SemiBold)
                                val meta = listOfNotNull(
                                    doc.author,
                                    doc.year?.toString(),
                                    "Licenza: ${doc.license}",
                                ).joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

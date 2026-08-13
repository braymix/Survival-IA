package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survivalwiki.app.ui.vm.SavedViewModel

@Composable
fun SavedScreen(viewModel: SavedViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Salvati", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text("Cerca nei salvati") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (items.isEmpty()) {
            Text("Nessuna scheda salvata.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { saved ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(saved.question, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 8.dp))
                                IconButton(onClick = { viewModel.delete(saved.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                                }
                            }
                            Text(
                                saved.answerText.take(400),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.survivalwiki.app.ui.components.PassageCard
import com.survivalwiki.app.ui.vm.AnswerUiState
import com.survivalwiki.app.ui.vm.RetrievalViewModel

@Composable
fun AnswerScreen(viewModel: RetrievalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "“${viewModel.query}”",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        when (val s = state) {
            is AnswerUiState.Loading -> Loading()
            is AnswerUiState.Blocked -> PolicyBanner(s.reason)
            is AnswerUiState.Empty -> EmptyState(s.nearbyTopics)
            is AnswerUiState.Error -> Text("Errore: ${s.message}")
            is AnswerUiState.Grounded -> Grounded(s)
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text("Cerco nelle fonti…", Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Grounded(s: AnswerUiState.Grounded) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (s.hasMedical) {
            item { MedicalBanner() }
        }
        if (!s.embeddingModelAvailable) {
            item { InfoCard(
                "Modalità solo-lessicale: il modello semantico non è ancora scaricato. " +
                    "I risultati migliorano dopo il download in Impostazioni.",
            ) }
        }
        item {
            Text(
                "Passaggi rilevanti dall'archivio (modalità estratti):",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        itemsIndexed(s.passages) { i, scored ->
            PassageCard(index = i + 1, chunk = scored.chunk)
        }
    }
}

@Composable
private fun EmptyState(nearby: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard("Non ho informazioni affidabili su questo argomento nel mio archivio.")
        if (nearby.isNotEmpty()) {
            Text("Argomenti vicini presenti nell'archivio:",
                style = MaterialTheme.typography.labelLarge)
            nearby.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun PolicyBanner(reason: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Richiesta non consentita", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Questa app è materiale di consultazione per la sopravvivenza e non fornisce " +
                    "contenuti che possono causare danno (rilevato: “$reason”).",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun MedicalBanner() {
    // Banner permanente e non dismissibile per la categoria medico.
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            "⚠ Informazione medica di sola consultazione. Non sostituisce formazione o soccorso: " +
                "in emergenza contatta i servizi di soccorso.",
            Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

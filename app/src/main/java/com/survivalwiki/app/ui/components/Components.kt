package com.survivalwiki.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.RetrievedChunk

/** Etichette italiane leggibili per le categorie. */
val categoryLabels: Map<Category, String> = mapOf(
    Category.ACQUA to "Acqua",
    Category.FUOCO to "Fuoco",
    Category.RIFUGIO to "Rifugio",
    Category.CIBO to "Cibo",
    Category.MEDICO to "Medico",
    Category.NAVIGAZIONE to "Navigazione",
    Category.SEGNALAZIONE to "Segnalazione",
    Category.ATTREZZATURA to "Attrezzatura",
    Category.CLIMA to "Clima",
    Category.SICUREZZA to "Sicurezza",
)

fun Category.label(): String = categoryLabels[this] ?: id

/** Riferimento breve alla fonte, es. "Documento — Sezione (p. 3)". */
fun RetrievedChunk.sourceLabel(): String = buildString {
    append(docTitle)
    section?.let { append(" — $it") }
    if (pageStart != null) {
        append(" (p. $pageStart")
        if (pageEnd != null && pageEnd != pageStart) append("–$pageEnd")
        append(")")
    }
}

/**
 * Card di un passaggio citato. Il marcatore [Fn] è cliccabile e apre un bottom sheet con il testo
 * integrale del chunk, il documento, la pagina e la licenza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassageCard(index: Int, chunk: RetrievedChunk, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showSheet = true }, label = { Text("[F$index]") })
                chunk.category?.let { AssistChip(onClick = { showSheet = true }, label = { Text(it.label()) }) }
            }
            Text(chunk.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                chunk.sourceLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Fonte [F$index]", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(chunk.sourceLabel(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
                Text("Licenza: ${chunk.license}", style = MaterialTheme.typography.labelMedium)
                Text(chunk.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

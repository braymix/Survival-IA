package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Schermate stub della Fase 1 (Scaffold). La logica reale (retrieval, LLM, Room)
 * viene collegata nelle fasi 3–5. Ogni stub è già una destinazione di navigazione valida.
 */

@Composable
private fun ScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        content()
    }
}

@Composable
fun OnboardingScreen() = ScreenScaffold(
    title = "SurvivalWiki AI",
    subtitle = "Materiale di consultazione offline. Non sostituisce formazione, soccorso medico " +
        "o servizi di emergenza. In caso di emergenza reale contatta i servizi di soccorso.",
)

@Composable
fun AskScreen(onAsk: () -> Unit = {}) = ScreenScaffold(
    title = "Chiedi",
    subtitle = "Interroga l'archivio in linguaggio naturale. (Fase 4: risposta vincolata alle fonti.)",
) {
    Button(onClick = onAsk) { Text("Cerca (stub)") }
}

@Composable
fun AnswerScreen() = ScreenScaffold(
    title = "Risposta",
    subtitle = "Qui comparirà la risposta in streaming con citazioni [F1], [F2]… (Fase 4).",
)

@Composable
fun BrowseScreen() = ScreenScaffold(
    title = "Sfoglia",
    subtitle = "Navigazione wiki per categoria → documento → sezioni, senza LLM. (Fase 3.)",
)

@Composable
fun SavedScreen() = ScreenScaffold(
    title = "Salvati",
    subtitle = "Risposte e schede salvate offline, con ricerca full-text. (Fase 5.)",
)

@Composable
fun SettingsScreen() = ScreenScaffold(
    title = "Impostazioni",
    subtitle = "Gestione modello, soglia di confidenza, lingua, modalità solo estratti, storage. (Fase 5.)",
)

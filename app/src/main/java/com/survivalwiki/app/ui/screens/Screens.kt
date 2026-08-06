package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Stub residui (Saved/Settings/Onboarding): completati in Fase 5.
 * Ask/Answer/Browse hanno file dedicati e sono funzionanti in Fase 3.
 */

@Composable
private fun ScreenScaffold(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun OnboardingScreen() = ScreenScaffold(
    title = "SurvivalWiki AI",
    subtitle = "Materiale di consultazione offline. Non sostituisce formazione, soccorso medico " +
        "o servizi di emergenza. In caso di emergenza reale contatta i servizi di soccorso.",
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

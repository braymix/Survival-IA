package com.survivalwiki.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinazioni di navigazione dell'app. Le rotte "top-level" compaiono nella bottom bar;
 * Onboarding e Answer sono raggiunte per flusso, non dalla barra.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector?,
    val topLevel: Boolean,
) {
    Onboarding("onboarding", "Introduzione", null, topLevel = false),
    Ask("ask", "Chiedi", Icons.Filled.Search, topLevel = true),
    Answer("answer", "Risposta", null, topLevel = false),
    Browse("browse", "Sfoglia", Icons.AutoMirrored.Filled.MenuBook, topLevel = true),
    Saved("saved", "Salvati", Icons.Filled.Bookmark, topLevel = true),
    Settings("settings", "Impostazioni", Icons.Filled.Settings, topLevel = true);

    companion object {
        val bottomBar: List<Destination> = entries.filter { it.topLevel }
    }
}

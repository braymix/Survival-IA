package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survivalwiki.app.data.DownloadState
import com.survivalwiki.app.ui.vm.DownloadTarget
import com.survivalwiki.app.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val threshold by viewModel.settings.confidenceThreshold.collectAsStateWithLifecycle()
    val extractsOnly by viewModel.settings.extractsOnly.collectAsStateWithLifecycle()
    val lowPower by viewModel.settings.lowPower.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storage by viewModel.storageBytes.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)

        Section("Modelli") {
            DownloadTarget.entries.forEach { target ->
                ModelRow(
                    name = target.displayName,
                    state = downloads[target],
                    onDownload = { viewModel.startDownload(target) },
                )
            }
            Text("Spazio modelli: ${storage / (1024 * 1024)} MB",
                style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { viewModel.deleteModels() }) { Text("Elimina i modelli") }
        }

        Section("Risposte") {
            SwitchRow("Solo estratti (nessuna generazione)", extractsOnly, viewModel::setExtractsOnly)
            Text("Soglia di confidenza: ${"%.3f".format(threshold)}",
                style = MaterialTheme.typography.labelMedium)
            Slider(
                value = threshold,
                onValueChange = { viewModel.setThreshold(it) },
                valueRange = 0.005f..0.05f,
            )
            Text(
                "Più alta = più selettiva (più spesso 'nessuna fonte affidabile').",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Dispositivo") {
            SwitchRow("Modalità basso consumo (schermo dimmerato)", lowPower, viewModel::setLowPower)
        }

        Section("Dati") {
            OutlinedButton(onClick = { viewModel.wipeSaved() }) { Text("Cancella i salvati") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ModelRow(name: String, state: DownloadState?, onDownload: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        when (state) {
            is DownloadState.Progress -> {
                LinearProgressIndicator(
                    progress = { state.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.bytesDownloaded / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall)
            }
            is DownloadState.Verifying -> Text("Verifica hash…", style = MaterialTheme.typography.labelSmall)
            is DownloadState.Done -> Text("✓ Scaricato", style = MaterialTheme.typography.labelSmall)
            is DownloadState.Failed -> {
                Text("Errore: ${state.message}", style = MaterialTheme.typography.labelSmall)
                Button(onClick = onDownload) { Text("Riprova") }
            }
            null -> Button(onClick = onDownload) { Text("Scarica") }
        }
    }
}

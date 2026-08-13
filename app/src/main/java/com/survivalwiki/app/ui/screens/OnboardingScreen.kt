package com.survivalwiki.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.survivalwiki.app.ui.vm.OnboardingViewModel

/**
 * Onboarding di prima esecuzione: spiega i limiti e richiede l'accettazione OBBLIGATORIA
 * del disclaimer prima di poter procedere. L'accettazione è persistita.
 */
@Composable
fun OnboardingScreen(
    onAccepted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var checked by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SurvivalWiki AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Consultazione offline di manuali di sopravvivenza. L'app cerca nelle fonti e cita " +
                "sempre documento e pagina. Non inventa: se non trova nulla di affidabile, lo dice.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avvertenza importante", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Questa app è materiale di consultazione e NON sostituisce la formazione, il " +
                        "soccorso medico o i servizi di emergenza. In caso di emergenza reale, " +
                        "contatta immediatamente i servizi di soccorso.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text("Ho letto e compreso l'avvertenza.")
            }
        }

        Button(
            onClick = { viewModel.accept(); onAccepted() },
            enabled = checked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continua")
        }
    }
}

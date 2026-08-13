package com.survivalwiki.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferenze utente persistite (SharedPreferences) esposte come [StateFlow].
 * Minimo per la Fase 3; ampliato in Fase 5 (lingua, wipe, ecc.).
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("survivalwiki-settings", Context.MODE_PRIVATE)

    private val _confidenceThreshold = MutableStateFlow(prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD))
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold.asStateFlow()

    private val _extractsOnly = MutableStateFlow(prefs.getBoolean(KEY_EXTRACTS_ONLY, false))
    /** "Solo estratti": disattiva del tutto la generazione LLM e mostra solo i passaggi. */
    val extractsOnly: StateFlow<Boolean> = _extractsOnly.asStateFlow()

    private val _lowPower = MutableStateFlow(prefs.getBoolean(KEY_LOW_POWER, false))
    val lowPower: StateFlow<Boolean> = _lowPower.asStateFlow()

    private val _disclaimerAccepted = MutableStateFlow(prefs.getBoolean(KEY_DISCLAIMER, false))
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted.asStateFlow()

    fun setConfidenceThreshold(value: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply(); _confidenceThreshold.value = value
    }

    fun setExtractsOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_EXTRACTS_ONLY, value).apply(); _extractsOnly.value = value
    }

    fun setLowPower(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_POWER, value).apply(); _lowPower.value = value
    }

    fun acceptDisclaimer() {
        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply(); _disclaimerAccepted.value = true
    }

    companion object {
        // Soglia RRF di default: un singolo match lessicale in cima (1/(60+1) ≈ 0.0164) deve poter
        // passare; l'anti-allucinazione è garantita dal requisito "in dominio" nel ConfidenceGate.
        const val DEFAULT_THRESHOLD = 0.010f
        private const val KEY_THRESHOLD = "confidence_threshold"
        private const val KEY_EXTRACTS_ONLY = "extracts_only"
        private const val KEY_LOW_POWER = "low_power"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
    }
}

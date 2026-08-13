package com.survivalwiki.app.ui.vm

import androidx.lifecycle.ViewModel
import com.survivalwiki.app.data.SettingsStore
import com.survivalwiki.app.ui.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsStore,
) : ViewModel() {
    /** Prima esecuzione → onboarding; altrimenti direttamente alla ricerca. */
    val startDestination: String =
        if (settings.disclaimerAccepted.value) Destination.Ask.route else Destination.Onboarding.route

    val lowPower: StateFlow<Boolean> = settings.lowPower
}

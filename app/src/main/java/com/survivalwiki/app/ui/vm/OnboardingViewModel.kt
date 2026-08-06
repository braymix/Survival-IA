package com.survivalwiki.app.ui.vm

import androidx.lifecycle.ViewModel
import com.survivalwiki.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {
    fun accept() = settings.acceptDisclaimer()
}

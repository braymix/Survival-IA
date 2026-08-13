package com.survivalwiki.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application root. Hilt entry point per l'intero grafo DI.
 * Nessuna inizializzazione di rete: l'app parte offline.
 */
@HiltAndroidApp
class SurvivalWikiApp : Application()

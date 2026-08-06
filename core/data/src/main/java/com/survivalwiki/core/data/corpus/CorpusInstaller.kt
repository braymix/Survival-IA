package com.survivalwiki.core.data.corpus

import android.content.Context
import java.io.File

/**
 * Copia il corpus SQLite dagli asset alla storage app-private (dove SQLite può aprirlo per path).
 * Idempotente: ricopia se assente o se cambia la versione bundlata.
 *
 * NOTA: NON si usa `assets.openFd()` — lancia un'eccezione sugli asset compressi (il `.db` viene
 * compresso nell'APK). Si usa sempre `assets.open()` (stream), che funziona a prescindere.
 * La "versione" è il codice versione dell'app: a ogni aggiornamento il DB viene rinfrescato.
 */
object CorpusInstaller {
    private const val ASSET_DB = "corpus/survival_corpus.db"
    private const val ASSET_MANIFEST = "corpus/corpus_manifest.json"
    const val DB_FILENAME = "survival_corpus.db"

    fun ensureInstalled(context: Context): String {
        val outDir = File(context.filesDir, "corpus").apply { mkdirs() }
        val dbFile = File(outDir, DB_FILENAME)
        val versionFile = File(outDir, ".version")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        }.getOrDefault("1")

        val installed = versionFile.takeIf { it.exists() }?.readText()?.trim()
        if (!dbFile.exists() || dbFile.length() == 0L || installed != appVersion) {
            context.assets.open(ASSET_DB).use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
            versionFile.writeText(appVersion)
        }
        return dbFile.absolutePath
    }

    /** Manifest JSON grezzo (per mostrare data/hash in UI). Vuoto se assente. */
    fun readManifestJson(context: Context): String =
        runCatching { context.assets.open(ASSET_MANIFEST).use { it.readBytes().decodeToString() } }
            .getOrDefault("{}")
}

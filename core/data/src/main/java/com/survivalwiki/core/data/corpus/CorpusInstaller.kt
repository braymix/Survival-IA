package com.survivalwiki.core.data.corpus

import android.content.Context
import java.io.File

/**
 * Copia il corpus SQLite dagli asset alla storage app-private (dove SQLite può aprirlo per path).
 * Idempotente: ricopia solo se assente o se cambia la dimensione dell'asset.
 */
object CorpusInstaller {
    private const val ASSET_DB = "corpus/survival_corpus.db"
    private const val ASSET_MANIFEST = "corpus/corpus_manifest.json"
    const val DB_FILENAME = "survival_corpus.db"

    fun ensureInstalled(context: Context): String {
        val outDir = File(context.filesDir, "corpus").apply { mkdirs() }
        val dbFile = File(outDir, DB_FILENAME)
        val assetSize = context.assets.openFd(ASSET_DB).use { it.length }
        if (!dbFile.exists() || dbFile.length() != assetSize) {
            context.assets.open(ASSET_DB).use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dbFile.absolutePath
    }

    /** Manifest JSON grezzo (per mostrare data/hash in UI). Vuoto se assente. */
    fun readManifestJson(context: Context): String =
        runCatching { context.assets.open(ASSET_MANIFEST).use { it.readBytes().decodeToString() } }
            .getOrDefault("{}")
}

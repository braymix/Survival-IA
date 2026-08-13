package com.survivalwiki.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Percorsi e stato dei modelli scaricati a runtime (mai nell'APK), in storage app-private.
 * Il download effettivo con verifica SHA-256 e ripresa è implementato in Fase 4 (ModelDownloader).
 */
@Singleton
class ModelRepository @Inject constructor(@ApplicationContext context: Context) {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    val embeddingModel: File = File(modelsDir, "e5-small-int8.onnx")
    val generativeModel: File = File(modelsDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")

    fun hasEmbeddingModel(): Boolean = embeddingModel.exists() && embeddingModel.length() > 0
    fun hasGenerativeModel(): Boolean = generativeModel.exists() && generativeModel.length() > 0
}

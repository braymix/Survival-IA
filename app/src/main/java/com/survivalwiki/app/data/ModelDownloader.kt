package com.survivalwiki.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Stato del download di un modello. */
sealed interface DownloadState {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState {
        val fraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }
    data class Verifying(val nothing: Unit = Unit) : DownloadState
    data class Done(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Scarica un artefatto (modello) con **ripresa** (HTTP Range) e verifica **SHA-256**.
 *
 * Scrive su un file temporaneo `<dest>.part`; alla fine verifica l'hash (se fornito) e rinomina.
 * Se lo sha atteso è vuoto, salta la verifica (utile finché non si congela una release).
 */
@Singleton
class ModelDownloader @Inject constructor() {

    fun download(url: String, dest: File, expectedSha256: String): Flow<DownloadState> = flow {
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            var existing = if (part.exists()) part.length() else 0L

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            conn.connect()

            val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!resumed) existing = 0L // il server ignora Range: riparti da capo

            val contentLength = conn.contentLengthLong.let { if (it < 0) -1 else it }
            val total = if (contentLength >= 0) existing + contentLength else -1

            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(if (resumed) existing else 0L)
                conn.inputStream.use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var downloaded = existing
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        emit(DownloadState.Progress(downloaded, total))
                    }
                }
            }

            if (expectedSha256.isNotBlank()) {
                emit(DownloadState.Verifying())
                val actual = sha256(part)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    part.delete()
                    emit(DownloadState.Failed("SHA-256 non corrispondente"))
                    return@flow
                }
            }

            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                emit(DownloadState.Failed("Impossibile finalizzare il file"))
                return@flow
            }
            emit(DownloadState.Done(dest))
        } catch (e: Exception) {
            // Il .part resta su disco per consentire la ripresa al tentativo successivo.
            emit(DownloadState.Failed(e.message ?: "Errore di download"))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

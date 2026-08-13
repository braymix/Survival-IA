package com.survivalwiki.core.embedding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import java.io.File
import org.junit.Test

/**
 * Verifica di PARITÀ del tokenizer Kotlin contro le fixture prodotte dal tokenizer HuggingFace
 * (tools/ingest export). Se questo test passa, gli embedding delle query calcolati on-device
 * corrispondono a quelli calcolati in ingest.
 */
class SpmUnigramTokenizerTest {

    companion object {
        private lateinit var tokenizer: SpmUnigramTokenizer

        @BeforeClass
        @JvmStatic
        fun load() {
            // Working dir del test = directory del modulo core:embedding.
            val vocab = File("src/main/assets/tokenizer/e5_unigram_vocab.tsv.gz")
            assertTrue("vocab asset mancante: ${vocab.absolutePath}", vocab.exists())
            tokenizer = SpmUnigramTokenizer.fromGzip(vocab.inputStream())
        }
    }

    private fun fixtures(): List<Pair<String, List<Int>>> {
        val stream = javaClass.getResourceAsStream("/tokenizer_fixtures.json")
            ?: error("fixtures mancanti")
        val arr = Json.parseToJsonElement(stream.reader().readText()).jsonArray
        return arr.map { el ->
            val o = el.jsonObject
            val text = o["text"]!!.jsonPrimitive.content
            val ids = o["ids"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            text to ids
        }
    }

    @Test
    fun `parita con tokenizer HuggingFace su fixture reali`() {
        val cases = fixtures()
        val mismatches = StringBuilder()
        var ok = 0
        for ((text, expected) in cases) {
            val got = tokenizer.encode(text).toList()
            if (got == expected) {
                ok++
            } else {
                mismatches.append("\n  '$text'\n    atteso: $expected\n    ottenuto: $got")
                mismatches.append("\n    pezzi: ${tokenizer.encodePieces(text)}")
            }
        }
        println("[tokenizer] parità: $ok/${cases.size}")
        assertTrue("Mismatch di tokenizzazione:$mismatches", mismatches.isEmpty())
    }

    @Test
    fun `aggiunge sempre BOS e EOS`() {
        val ids = tokenizer.encode("acqua")
        assertTrue(ids.first() == SpmUnigramTokenizer.BOS_ID)
        assertTrue(ids.last() == SpmUnigramTokenizer.EOS_ID)
    }
}

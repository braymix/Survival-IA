package com.survivalwiki.app.data

import android.content.Context
import com.survivalwiki.core.embedding.SpmUnigramTokenizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Wrapper per iniettare i sinonimi via Hilt senza qualificatori sul tipo Map. */
data class Synonyms(val map: Map<String, List<String>>)

object AssetLoaders {
    private const val TOKENIZER_ASSET = "tokenizer/e5_unigram_vocab.tsv.gz"
    private const val SYNONYMS_ASSET = "synonyms.json"
    private const val PROMPT_ASSET = "prompts/grounded_answer.txt"

    /** Il vocab del tokenizer è un asset del modulo core:embedding, unito negli asset dell'app. */
    fun loadTokenizer(context: Context): SpmUnigramTokenizer =
        context.assets.open(TOKENIZER_ASSET).use { SpmUnigramTokenizer.fromGzip(it) }

    fun loadSynonyms(context: Context): Synonyms {
        val text = context.assets.open(SYNONYMS_ASSET).use { it.readBytes().decodeToString() }
        val obj = Json.parseToJsonElement(text)
        val map = LinkedHashMap<String, List<String>>()
        for ((key, value) in obj.jsonObjectOrEmpty()) {
            if (key.startsWith("_")) continue // salta i commenti "_comment"
            if (value is JsonArray) {
                map[key] = value.jsonArray.map { it.jsonPrimitive.content }
            }
        }
        return Synonyms(map)
    }

    fun loadGroundedPrompt(context: Context): String =
        context.assets.open(PROMPT_ASSET).use { it.readBytes().decodeToString() }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty() =
        (this as? kotlinx.serialization.json.JsonObject) ?: kotlinx.serialization.json.JsonObject(emptyMap())
}

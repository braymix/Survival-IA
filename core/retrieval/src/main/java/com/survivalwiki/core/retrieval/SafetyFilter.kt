package com.survivalwiki.core.retrieval

import java.text.Normalizer
import java.util.Locale

/** Esito del filtro di sicurezza su una query in ingresso. */
sealed interface SafetyVerdict {
    /** La query può proseguire nella pipeline di retrieval. */
    data object Allowed : SafetyVerdict

    /** La query è bloccata: [reason] è una chiave di policy per il messaggio da mostrare. */
    data class Blocked(val reason: String) : SafetyVerdict
}

/**
 * Blocklist di intent: l'app è materiale di consultazione per la sopravvivenza, non un canale
 * per la produzione di danno. Blocca richieste su armi da fuoco/esplosivi/veleni/sintesi di
 * sostanze pericolose, indipendentemente dalla presenza di fonti.
 *
 * Nota progettuale: match su parole intere dopo normalizzazione (lowercase + rimozione accenti),
 * per evitare falsi positivi da sottostringhe (es. "fuoco" NON deve attivare "uoco"/nulla) e
 * falsi negativi da accenti. La lista è volutamente conservativa e testata; va estesa con cautela
 * per non bloccare argomenti legittimi (fuoco per riscaldarsi, coltello come attrezzo, ecc.).
 */
class SafetyFilter(
    private val blockedTerms: Set<String> = DEFAULT_BLOCKED_TERMS,
) {

    // I termini bloccati sono pre-tokenizzati una volta sola (parola singola o frase multi-parola).
    private val blockedPhrases: List<List<String>> =
        blockedTerms.map { tokenize(it) }.filter { it.isNotEmpty() }

    fun check(query: String): SafetyVerdict {
        val tokens = tokenize(query)
        // Match su n-grammi di lunghezza pari a quella del termine bloccato: così un intent
        // composto come "arma da fuoco" (trigramma) o "gas nervino" (bigramma) viene intercettato,
        // ma senza falsi positivi da sottostringhe (il match è su token interi).
        val hit = blockedPhrases.firstOrNull { phrase -> containsSequence(tokens, phrase) }
        return if (hit != null) SafetyVerdict.Blocked(reason = hit.joinToString(" ")) else SafetyVerdict.Allowed
    }

    /** True se [phrase] compare come sottosequenza contigua di [tokens]. */
    private fun containsSequence(tokens: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty() || phrase.size > tokens.size) return false
        for (start in 0..tokens.size - phrase.size) {
            var matched = true
            for (offset in phrase.indices) {
                if (tokens[start + offset] != phrase[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    private fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "") // rimuove i segni diacritici
        return normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }

    companion object {
        /**
         * Termini/bigrammi bloccati (IT/EN). Focus su danno intenzionale a persone,
         * non su attività di sopravvivenza legittime.
         */
        val DEFAULT_BLOCKED_TERMS: Set<String> = setOf(
            // Esplosivi / ordigni
            "esplosivo", "esplosivi", "explosive", "bomba", "bomb", "ied", "detonatore",
            "detonator", "tritolo", "tnt", "nitroglicerina",
            // Armi da fuoco / munizioni fatte in casa
            "arma da fuoco", "pistola", "fucile", "firearm", "gun", "silenziatore", "silencer",
            "munizioni artigianali", "ghost gun",
            // Veleni / agenti
            "veleno", "poison", "avvelenare", "gas nervino", "nerve agent", "ricina", "ricin",
            "cianuro", "cyanide", "antrace", "anthrax",
        )
    }
}

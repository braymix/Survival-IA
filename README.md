# SurvivalWiki AI

App Android **100% offline**: una knowledge base di sopravvivenza (acqua, fuoco, rifugio, cibo,
medico, navigazione, segnalazione, attrezzatura, clima, sicurezza) interrogabile in linguaggio
naturale.

**Non è un chatbot generativo.** È un **motore di retrieval** con una sottile passata di sintesi:
il valore è nei documenti, non nel modello. Il modello serve solo a riformulare i passaggi
recuperati, senza inventare nulla, citando sempre la fonte (documento + pagina/sezione).

> **Regola d'oro, imposta a livello di codice:** se il retrieval non trova nulla sopra la soglia di
> rilevanza, l'app risponde *"Non ho informazioni affidabili su questo argomento nel mio archivio"*
> e **non invoca nemmeno l'LLM**. Zero allucinazioni per costruzione — verificato al 100% sul golden
> set (`docs/BENCHMARKS.md`).

## Come funziona (pipeline RAG)
```
query → SafetyFilter → riscrittura (sinonimi, no LLM)
      → dense (e5-small ONNX) + lexical (FTS5/BM25) → RRF → gate "in dominio"
          ├─ sopra soglia → generazione vincolata (llama.cpp) → post-validazione citazioni [Fn]
          └─ sotto soglia → "nessuna fonte affidabile" (+ argomenti vicini)
```
- **Embedding**: `multilingual-e5-small` INT8 via ONNX Runtime; il tokenizer SentencePiece è
  reimplementato in Kotlin con **parità verificata** col Python (test).
- **Vector store**: SQLite (bundled) — dense in brute-force cosine, lexical in FTS5/BM25 (vedi
  `docs/ADR-0002`).
- **Generazione**: `Qwen2.5-0.5B-Instruct` Q4_K_M via **llama.cpp** (JNI), con post-validazione delle
  citazioni; se una risposta non cita fonti valide, si mostrano i passaggi grezzi.

## Stato per fase
1. Scaffold multi-modulo · 2. Ingest (Python) · 3. Retrieval + Browse (wiki) · 4. LLM llama.cpp ·
5. Onboarding/Settings/Salvati/Safety · 6. Test & benchmark · 7. Release. Dettagli in `PLAN.md`.

## Build
Richiede JDK 17 e Android SDK (platform-35, build-tools 35.0.0).
```bash
./gradlew testDebugUnitTest     # unit test (tokenizer parità, RRF, SafetyFilter, gate, citazioni…)
./gradlew assembleDebug         # APK debug (senza LLM: modalità "solo estratti")
./gradlew assembleRelease       # APK release (R8, ABI split arm64-v8a)
```

### Generazione LLM (opt-in)
La libreria nativa llama.cpp è **opt-in** per non richiedere l'NDK nel build di default:
```bash
git submodule update --init --depth 1 native/llama.cpp
./gradlew assembleRelease -PwithLlama    # include libsurvivalllm/llama/ggml (arm64-v8a)
```
Senza il flag, l'app funziona in **modalità solo estratti** (retrieval + citazioni, niente
generazione). Vedi `native/README.md`.

### Corpus
La pipeline di ingest (Python) è in `tools/ingest/` (vedi il suo README): estrae PDF/EPUB/TXT/MD,
fa chunking strutturale, calcola gli embedding e5 e produce `survival_corpus.db`. Il corpus seed
(CC0, 6 schede) è già negli asset. Per aggiungere fonti reali verifica sempre la licenza
(`tools/ingest/sources.yaml`, `docs/LICENSES.md`).

## Privacy e permessi
- **Nessuna telemetria.** L'unico permesso di rete (`INTERNET`) è usato **solo** per il download
  una-tantum di modello/corpus; dopo il setup l'app non effettua alcuna chiamata di rete a runtime.
- Modelli (e5 ONNX, GGUF) sono **scaricati a parte** con verifica **SHA-256** e ripresa, mai
  inclusi nell'APK. Il corpus (<100 MB) è negli asset.

## Sicurezza e responsabilità
- Onboarding con **disclaimer obbligatorio** persistito: l'app è materiale di consultazione, **non**
  sostituisce formazione, soccorso medico o servizi di emergenza — in emergenza reale contatta i
  soccorsi.
- Banner **permanente e non dismissibile** sulle risposte in categoria `medico`.
- `SafetyFilter`: blocklist di intent (armi/esplosivi/sostanze pericolose) con test dedicati.

## Documentazione
`docs/ARCHITECTURE.md`, `docs/ADR-*.md`, `docs/LICENSES.md`, `docs/BENCHMARKS.md`, `PLAN.md`.

# PLAN.md — SurvivalWiki AI

> App Android **100% offline**: knowledge base di sopravvivenza interrogabile in linguaggio naturale.
> Non è un chatbot generativo: è un **motore di retrieval con una sottile passata di sintesi linguistica**.
> **Regola d'oro (a livello di codice):** se il retrieval non supera la soglia di rilevanza,
> l'app risponde *"Non ho informazioni affidabili…"* e **non invoca l'LLM**. Zero allucinazioni per costruzione.

Questo documento è la **fase 0**. Va confermato prima di scrivere codice di produzione.

---

## 1. Verifiche già fatte sul web (fatti, non memoria)

| Punto a rischio | Esito verifica | Conseguenza sul piano |
|---|---|---|
| sqlite-vec caricabile su Android | `androidx.sqlite` `BundledSQLiteDriver` espone `addExtension(...)` per il caricamento estensioni | Percorso primario **confermato**; fallback brute-force resta documentato |
| multilingual-e5-small ONNX INT8 | Esistono build INT8 pronte (~112 MB) su HF | Percorso embedding **confermato**; niente conversione obbligatoria in-house |
| Qwen2.5-0.5B-Instruct Q4_K_M GGUF | Disponibile su repo ufficiale Qwen + bartowski, quantizzazioni ARM | Modello **confermato**; scaricato a runtime, mai nell'APK |
| llama.cpp Android | Build via CMake + Android NDK documentata | JNI wrapper via submodule + `externalNativeBuild` |

> Le versioni esatte (AGP, Gradle, NDK, ONNX Runtime, revisione submodule llama.cpp, schema sqlite-vec `vec0`)
> verranno **ri-verificate sul web al momento dell'uso** in ciascuna fase, non fissate a memoria adesso.

---

## 2. Stack tecnico (come da vincolo, con note)

- **UI/Lang**: Kotlin + Jetpack Compose (Material 3, tema scuro default). minSdk 26, targetSdk 35.
- **Architettura**: MVVM + Clean-ish, Hilt (DI), Coroutines/Flow.
- **LLM**: llama.cpp via JNI (submodule + CMake/NDK), dietro interfaccia `LlmEngine`. Fallback documentato: MediaPipe LLM Inference.
- **Modello generativo**: Qwen2.5-0.5B-Instruct Q4_K_M (~350 MB), scaricato a parte.
- **Embedding**: multilingual-e5-small ONNX INT8 via ONNX Runtime Mobile; tokenizer (SentencePiece/XLM-R) in Kotlin.
- **Vector store**: SQLite + sqlite-vec (`vec0`). Fallback: cosine brute-force su `FloatArray` in memoria (<50k chunk).
- **Lexical**: FTS5 (BM25) sulla stessa base SQLite.
- **DB app**: Room (cronologia, preferiti, note).
- **Build**: Gradle Kotlin DSL + version catalog (`libs.versions.toml`).
- **Test**: JUnit + Turbine (unit), Compose UI test, Robolectric.

Ogni scelta e ogni fallback attivato → motivati in `docs/ADR-*.md`.

---

## 3. Struttura repo attesa

```
survivalwiki/               (root del progetto; verrà creato in questa repo)
├── app/                    # Compose UI, ViewModel, DI, navigation, onboarding
├── core/
│   ├── llm/                # LlmEngine (interface) + LlamaCppEngine (JNI)
│   ├── embedding/          # OnnxEmbedder + tokenizer
│   ├── retrieval/          # HybridRetriever, RRF, soglie, SafetyFilter, QueryRewriter
│   └── data/               # SQLite corpus reader, Room, repository
├── native/                 # llama.cpp submodule + CMakeLists + wrapper JNI
├── tools/
│   ├── ingest/             # pipeline Python (build_corpus.py) + sources.yaml + categories.yaml
│   └── eval/               # golden set + script recall@5 / citazioni valide
├── docs/                   # ADR-*, LICENSES.md, ARCHITECTURE.md, BENCHMARKS.md
└── .github/workflows/      # build + test CI
```

---

## 4. Pipeline RAG (sintesi implementativa)

**Ingest (Python, offline)** → `build_corpus.py`: estrazione PyMuPDF (pagina + sezione), OCR opzionale,
pulizia (header/footer ripetuti, de-hyphenation, whitespace), **chunking strutturale** (~450 token, overlap 80,
mai spezzare liste numerate/procedure), categoria da lista chiusa (keyword + override `categories.yaml`),
embedding e5 con prefisso `passage: `. Output: `survival_corpus.db` (`documents`, `chunks`, `chunks_fts`,
`chunk_vectors`) + `corpus_manifest.json` (hash, conteggi, schema). `--validate` = sanity check
(vuoti, duplicati MinHash, norme anomale, orfani).

**Retrieval (Kotlin runtime)**:
1. Query rewriting locale **senza LLM** (normalizzazione + sinonimi IT/EN da `assets/synonyms.json`).
2. Dense: embedding query (prefisso `query: `), top-K 20 (cosine).
3. Lexical: FTS5 BM25, top-K 20.
4. Fusione **RRF (k=60)** → top-8.
5. Boost categoria se filtro attivo.
6. **Soglia di confidenza**: sotto soglia → "nessuna fonte affidabile" + 3 argomenti vicini. **LLM non invocato.**

**Generazione vincolata**: system prompt in `assets/prompts/grounded_answer.txt`
(rispondi SOLO dai passaggi, ogni affermazione con marcatore `[Fn]`, altrimenti `NESSUNA_FONTE`).
`temperature=0.2, top_p=0.9, repeat_penalty=1.1, n_ctx=4096, max 400 token`, streaming `Flow<String>`.
**Post-validazione Kotlin**: se 0 citazioni o indice inesistente → scarta e mostra passaggi grezzi con banner;
se output `NESSUNA_FONTE` → schermata fallback.

---

## 5. UI/UX (Compose, Material 3, dark default)

Home/Ask · Answer (chip `[Fn]` → bottom sheet con chunk integrale/documento/pagina/licenza) · Browse (wiki pura, no LLM) ·
Saved (full-text sui salvati) · Settings (gestione modello, soglia, lingua, "solo estratti", storage, wipe) ·
First run (onboarding + disclaimer obbligatorio + download modello con ripresa).
Accessibilità: font scalabile, alto contrasto, target ≥48dp, **modalità basso consumo**. Tutto in modalità aereo.

---

## 6. Sicurezza e responsabilità (non negoziabile)

- Disclaimer onboarding, accettazione **persistita**.
- Banner permanente non-dismissibile su categoria `medico`.
- `SafetyFilter` con blocklist intent (armi/esplosivi/sostanze pericolose) + test dedicati.
- Nessuna telemetria; `INTERNET` dichiarato ma non usato a runtime dopo il setup (documentato nel README).

---

## 7. Piano per fasi (mi fermo e riepilogo a fine di ognuna)

| Fase | Contenuto | Verifica ("done") |
|---|---|---|
| **0** | `PLAN.md` (questo) | Tua conferma |
| **1 Scaffold** | Gradle multi-modulo, Hilt, navigation, tema, moduli vuoti | `./gradlew assembleDebug` compila |
| **2 Ingest** | `build_corpus.py` + 3–5 documenti public domain → `survival_corpus.db` | `--validate` verde, manifest coerente |
| **3 Retrieval senza LLM** | OnnxEmbedder + HybridRetriever + UI Browse/ricerca | App **già utile** (modalità wiki), unit test retrieval verdi |
| **4 LLM** | llama.cpp JNI, download modello, streaming, prompt vincolato, post-validazione citazioni | Risposta con citazioni valide su domanda in-dominio |
| **5 Rifinitura** | Saved, Settings, basso consumo, onboarding, safety | Flussi completi, SafetyFilter test verdi |
| **6 Test & benchmark** | Golden set ≥40 Q, recall@5, % citazioni valide, anti-allucinazione 10/10, perf mid-range | `docs/BENCHMARKS.md` compilato |
| **7 Release** | ABI split (arm64-v8a), R8, README, screenshot | APK ottimizzato prodotto |

---

## 8. Test obbligatori (promemoria)

Unit: chunking, RRF, parser citazioni, soglia, SafetyFilter.
Golden set `tools/eval/questions.yaml` (≥40) → recall@5 + % citazioni valide → `docs/BENCHMARKS.md`.
Anti-allucinazione: 10 domande fuori dominio → **tutte** `NESSUNA_FONTE` (100%).
Perf mid-range: tempo embedding query, retrieval, token/s, RAM picco, dimensione APK+asset.

---

## 9. Distribuzione

APK ABI split (arm64-v8a primario). Corpus <100 MB → `assets`, oltre → download primo avvio con SHA-256 + ripresa.
Modello GGUF **sempre** scaricato a parte (mai nell'APK), storage app-private, hash-checked.

---

## 10. Decisioni tecniche aperte — mi servono le tue conferme

1. **Ambiente di build/CI.** Questo container non ha (probabilmente) Android SDK/NDK né emulatore.
   Propongo: scrivere tutto il codice + configurare CI GitHub Actions che compila e testa; la verifica
   "compila davvero" avverrà in CI, non localmente. **OK?** In alternativa indicami un ambiente con SDK.

2. **Corpus di prova.** Per la Fase 2 useremo **US Army FM 21-76 (Survival)** come primo documento public
   domain certo (opera del governo USA). Le altre fonti (FAO/OMS/ICRC/Wikibooks) verranno incluse **solo se
   la licenza è verificabile**; altrimenti `license: UNKNOWN` ed escluse. **Confermi FM 21-76 come seed principale?**
   Nota: il *download effettivo* dei PDF dipende dalla policy di rete di questo ambiente; se il download non è
   possibile qui, preparo la pipeline + un **mini-corpus sintetico public-domain** per far girare i test, e i
   documenti reali si aggiungono quando l'ambiente lo consente.

3. **Sequenza di consegna.** Preferisci che proceda **fase per fase fermandomi ogni volta** (come da tua
   richiesta esplicita), oppure vuoi che tiri dritto fino a un checkpoint più grande (es. fine Fase 3, "app
   wiki utile")? Di default seguo il fermarmi a ogni fase.

4. **Tokenizer e5 in Kotlin.** e5-small usa il tokenizer XLM-RoBERTa (SentencePiece unigram). Propongo di
   portare il vocabolario/merges come asset e implementare un tokenizer minimale in Kotlin (con test di
   parità contro l'output Python). **OK**, oppure preferisci valutare `onnxruntime-extensions` per il
   tokenizer lato ONNX?

5. **Sqlite-vec vs fallback.** Parto dal percorso primario (sqlite-vec). Se in fase 3 il caricamento
   estensione su un device/emulatore reale si rivela problematico, **attivo il fallback brute-force** e
   scrivo l'ADR relativo, senza chiederti conferma ulteriore. **Va bene questa delega?**

---

### Prossimo passo
Alla tua conferma (o alle tue risposte ai punti 1–5), parto dalla **Fase 1 (Scaffold)** e mi fermo appena compila.

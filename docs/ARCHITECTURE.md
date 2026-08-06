# Architettura — SurvivalWiki AI

## Principio guida
L'app **non è un chatbot generativo**: è un motore di **retrieval** con una sottile passata di sintesi.
La **regola d'oro** è imposta a livello di codice: se il retrieval non supera la soglia di confidenza,
si mostra il fallback "nessuna fonte affidabile" e **l'LLM non viene neppure invocato**.

## Moduli Gradle
```
:app                 UI Compose, navigation, ViewModel, DI, onboarding
:core:llm            LlmEngine (interfaccia) + LlamaCppEngine (JNI, Fase 4)
:core:embedding      Embedder (interfaccia) + OnnxEmbedder e5-small (Fase 3)
:core:retrieval      Modelli, ReciprocalRankFusion, SafetyFilter, ConfidenceGate, HybridRetriever (Fase 3)
:core:data           CorpusReader (SQLite sola lettura) + Room (salvati/cronologia)
```
Dipendenze: `app` → tutti i `core`; `core:data` → `core:retrieval`; `core:retrieval` → `core:embedding`.
Nessun `core` dipende da `app` (regola di direzione).

## Pipeline a runtime (obiettivo finale)
```
query utente
  └─ SafetyFilter (blocklist intent)         [core:retrieval]
       └─ QueryRewriter (sinonimi IT/EN, no LLM)
            ├─ dense:  Embedder(query:) → CorpusReader.searchDense (sqlite-vec)
            └─ lexical: CorpusReader.searchLexical (FTS5/BM25)
                 └─ ReciprocalRankFusion(k=60) → top-8
                      └─ ConfidenceGate(threshold)
                           ├─ Proceed  → LlmEngine.generate(prompt vincolato) → post-validazione citazioni
                           └─ NoReliableSource → schermata fallback + argomenti vicini
```

## Stato per fase
- **Fase 1 (fatta):** scaffold multi-modulo compilante; RRF + SafetyFilter + ConfidenceGate implementati e testati;
  interfacce `LlmEngine`/`Embedder`/`CorpusReader`; Room `SavedAnswer`; UI stub delle 6 schermate; CI.
- **Fase 2:** pipeline ingest Python → `survival_corpus.db`.
- **Fase 3:** OnnxEmbedder + HybridRetriever + CorpusReader reale + modalità Browse (app "wiki" utile).
- **Fase 4:** llama.cpp JNI + generazione vincolata + post-validazione citazioni.
- **Fase 5–7:** salvati/settings/onboarding/safety UI, test&benchmark, release.

## Comandi
```bash
./gradlew testDebugUnitTest   # unit test (JVM)
./gradlew assembleDebug       # APK debug arm64-v8a
```
Richiede Android SDK (platform-35, build-tools 35.0.0) e JDK 17. `local.properties` con `sdk.dir=...`
non è committato.

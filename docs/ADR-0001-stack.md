# ADR-0001 — Stack tecnico e architettura di base

- **Stato:** Accettato (Fase 1)
- **Data:** 2026-08-06

## Contesto
SurvivalWiki AI è un'app Android **100% offline** che è, in sostanza, un **motore di retrieval**
con una sottile passata di sintesi linguistica. Il valore è nei documenti, non nel modello. Le scelte
tecniche devono privilegiare: funzionamento offline garantito, controllo delle allucinazioni, footprint
gestibile su dispositivi mid-range.

## Decisioni

| Ambito | Scelta | Motivazione |
|---|---|---|
| UI/Lang | Kotlin + Jetpack Compose (Material 3) | Standard moderno Android; tema scuro/alto contrasto per uso sul campo |
| minSdk / targetSdk | 26 / 35 | 26 copre la stragrande maggioranza del parco installato e semplifica gli asset (adaptive icon anydpi-v26) |
| Architettura | MVVM + moduli `core:*`, Hilt DI, Coroutines/Flow | Separazione netta retrieval/LLM/UI; testabilità; streaming naturale via Flow |
| LLM | llama.cpp via JNI dietro `LlmEngine` | Controllo totale, GGUF quantizzato, nessuna dipendenza cloud. Astratto per consentire fallback |
| Modello | Qwen2.5-0.5B-Instruct Q4_K_M | Piccolo (~350 MB), multilingue, adatto a sola riformulazione vincolata. **Scaricato a runtime**, mai nell'APK |
| Embedding | multilingual-e5-small ONNX INT8 (~112 MB) via ONNX Runtime Mobile | Buon multilingue IT/EN a costo contenuto; INT8 per RAM/latency su mobile |
| Vector store | SQLite + sqlite-vec (`vec0`) | Un solo file DB per dense+lexical+metadati; `BundledSQLiteDriver.addExtension` verificato disponibile |
| Lexical | FTS5 (BM25) | Nativo SQLite, nessuna dipendenza extra |
| DB app | Room | Cronologia/salvati/note; codegen via **KSP** |
| Build | Gradle KTS + version catalog, wrapper 8.13, AGP 8.7.3 | Riproducibilità; catalog centralizza le versioni |

## Fallback previsti (da attivare con ADR dedicato se necessario)
- **sqlite-vec non caricabile su un device reale** → cosine brute-force su `FloatArray` in memoria (accettabile <50k chunk).
- **llama.cpp JNI problematico su un'ABI** → MediaPipe LLM Inference API.

## Verifica (Fase 1)
- `./gradlew testDebugUnitTest` → verde (RRF, SafetyFilter, ConfidenceGate).
- `./gradlew assembleDebug` → APK arm64-v8a prodotto.
- Toolchain: JDK 17, Gradle 8.13, Android SDK platform-35/build-tools 35.0.0.

## Note ambiente di build
Il container di sviluppo non aveva Android SDK/NDK preinstallati: sono stati installati per validare
localmente. La verifica canonica "compila & test verdi" è comunque replicata in CI
(`.github/workflows/android-ci.yml`).

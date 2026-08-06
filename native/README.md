# native/ — libreria nativa llama.cpp (opt-in)

L'inferenza generativa usa **llama.cpp** via JNI. La compilazione nativa è **opt-in** per non
richiedere l'NDK nel build/CI di default: senza, l'app funziona in modalità "solo estratti".

## Struttura
- `llama.cpp/` — submodule git (pinnato a un commit preciso).
- Il wrapper JNI e il CMake stanno in `core/llm/src/main/cpp/` (`llama_jni.cpp`, `CMakeLists.txt`).
- Il codice Kotlin è `core/llm/.../LlamaCppEngine.kt`.

## Prerequisiti
- Android NDK `26.3.11579264` e CMake `3.22.1` (installabili da `sdkmanager`).
- Inizializzare il submodule:
  ```bash
  git submodule update --init --depth 1 native/llama.cpp
  ```

## Compilare con la libreria nativa
Aggiungi il flag `-PwithLlama` a qualsiasi task:
```bash
./gradlew :app:assembleDebug -PwithLlama          # APK con libsurvivalllm.so (+llama/ggml) arm64-v8a
./gradlew :core:llm:externalNativeBuildDebug -PwithLlama
```
Senza il flag, `core:llm` compila solo Kotlin/JVM e `System.loadLibrary("survivalllm")` fallisce
con grazia (l'app resta in modalità estratti).

## API llama.cpp
Il wrapper è scritto per il commit del submodule attualmente pinnato. Se aggiorni il submodule e
l'API cambia (es. firme dei sampler), aggiorna `llama_jni.cpp` di conseguenza — è normale, l'API
di llama.cpp evolve rapidamente.

## Design "token-pull"
Il JNI espone `nativeInit / nativeStart / nativeNextToken / nativeFree`: Kotlin guida il ciclo di
generazione ed emette i token in un `Flow<String>`, mantenendo streaming e cancellazione lato JVM
(il contesto llama.cpp non è thread-safe: tutte le chiamate girano su un singolo thread dedicato).

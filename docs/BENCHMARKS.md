# BENCHMARKS

## Retrieval — golden set (`tools/eval/questions.yaml`)

Valutato con `tools/eval/run_eval.py`, che replica la pipeline Kotlin (dense e5 + FTS5/BM25 + RRF
+ gate "in dominio") sullo **stesso** DB e **stesso** modello e5 spediti nell'app.

| Metrica | Risultato |
|---|---|
| Domande in dominio | 41 |
| Domande fuori dominio | 10 |
| **recall@5** (in dominio) | **41/41 = 100%** |
| **gate-pass** (in dominio) | **41/41 = 100%** |
| **Anti-allucinazione** (fuori dominio → NESSUNA_FONTE) | **10/10 = 100%** |

Il test anti-allucinazione è **superato al 100%**, come richiesto.

### Calibrazione della soglia di dominio
- coseno denso in dominio: min **0,843**, mediana 0,890
- coseno denso fuori dominio: max **0,821**, mediana 0,801
- **`denseFloor = 0,835`** separa nettamente le due distribuzioni.
- La ricerca lessicale usa **match esatto** su token di contenuto (stopword ed elisioni rimosse):
  una query fuori dominio non produce match, quindi non "entra" dal lato lessicale.

> Nota: la calibrazione è tarata sul corpus seed (6 documenti, 30 chunk). Con un corpus reale più
> ampio i valori vanno ri-verificati con lo stesso script; `denseFloor` e la soglia RRF sono
> parametri (quest'ultima è anche esposta in Impostazioni).

## Performance del retrieval (proxy desktop)

Misurato in `run_eval.py` su CPU desktop x86 (proxy — i numeri on-device arm differiscono):

| Fase | Tempo medio |
|---|---|
| Embedding della query (e5 INT8, ONNX) | ~5 ms |
| Retrieval ibrido (brute-force su 30 chunk + FTS) | ~5 ms |

Il costo del brute-force cresce linearmente col numero di chunk; resta trascurabile fino a ~50k
(cache dei vettori in memoria: 50k × 384 × 4 B ≈ 76 MB).

## Dimensioni

| Artefatto | Dimensione |
|---|---|
| Corpus DB (in assets) | 164 KB |
| Vocab tokenizer (gzip, in assets) | 2,3 MB |
| APK debug (default, senza llama.cpp) | ~41 MB |
| **APK release (R8, ABI split arm64, senza llama.cpp)** | **~23 MB** |
| **APK release con `-PwithLlama`** (arm64) | **~71 MB** |
| Modello e5 INT8 (scaricato) | ~118 MB |
| Modello GGUF Qwen2.5-0.5B Q4_K_M (scaricato) | ~398 MB |

Librerie native nell'APK release+llama (arm64): `libllama.so` ~38 MB, `libonnxruntime.so` ~18 MB,
`libggml*` ~10 MB, `libsurvivalllm.so` ~0,2 MB. R8 riduce l'APK default da ~41 MB (debug) a ~23 MB.

## Da misurare su dispositivo (mid-range, arm64)

Questi numeri richiedono un device reale e non sono misurabili nell'ambiente di build:
- token/s in generazione (Qwen2.5-0.5B Q4_K_M via llama.cpp);
- RAM di picco durante la generazione;
- tempo di embedding query on-device;
- dimensione APK+asset finale (release, ABI split).

Procedura suggerita: build `assembleRelease -PwithLlama`, installare su device, misurare con
Android Studio Profiler + log applicativi (i tempi di embedding/retrieval sono già strumentabili).

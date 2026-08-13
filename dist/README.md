# dist — APK precompilati

## survivalwiki-ai-llama.apk (build con generazione AI)
APK **release** firmato con chiave debug, **arm64-v8a**, con la libreria nativa **llama.cpp**
inclusa (`-PwithLlama`). ~72 MB.

**Download diretto:**
`https://github.com/braymix/Survival-IA/raw/claude/survivalwiki-ai-android-bs54ul/dist/survivalwiki-ai-llama.apk`

### Installazione
1. Scaricalo su un telefono Android arm64 (Android 8.0+).
2. Consenti "installa app sconosciute" per l'app da cui lo apri.
3. Installalo (anche sopra una versione precedente: stessa chiave di firma).

### Per usare la generazione AI (risposte sintetizzate, non solo estratti)
Dentro l'app: **Impostazioni → Modelli**:
1. Scarica il **modello semantico** e5 (~118 MB) — abilita la ricerca "per significato".
2. Scarica il **modello generativo** Qwen2.5-0.5B GGUF (~398 MB) — abilita le risposte generate
   con citazioni [F1], [F2]…

> Nota: la generazione di un modello 0.5B su CPU di un telefono è lenta e a bassa potenza è
> disattivabile ("solo estratti" in Impostazioni). Tutto resta **offline** dopo i download.

### Firma
Chiave *debug* standard: adatta a test e sideload personale, **non** alla pubblicazione su store.

---
Nota repo: questo APK è un binario di comodo; può essere rimosso in futuro e sostituito da un
artifact di CI (vedi `.github/workflows/android-ci.yml`).

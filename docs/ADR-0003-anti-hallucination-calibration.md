# ADR-0003 — Calibrazione anti-allucinazione

- **Stato:** Accettato (Fase 6)
- **Data:** 2026-08-06

## Contesto
Il requisito impone che **10 domande fuori dominio restituiscano tutte NESSUNA_FONTE (100%)** e che
il retrieval abbia buona recall sulle domande in dominio. La prima versione del gate lasciava passare
3/10 domande fuori dominio.

## Cause individuate (dal golden set)
1. **Prefix match FTS** (`"token"*`): "ripara" (di "come si ripara un motore") faceva match con
   "riparo"/"riparato" del documento rifugio → falso positivo lessicale.
2. **Elisioni non filtrate**: "dell'", "qual" venivano tokenizzati come "dell"/"qual" e, col prefix,
   matchavano "della/delle" e "qualunque" → falsi positivi.
3. **`denseFloor` troppo basso** (0,84): il coseno e5 di base per testo non correlato arriva a ~0,82;
   alcune query fuori dominio superavano il floor.

## Decisione
1. FTS a **match esatto** (niente `*`), token di contenuto con lunghezza ≥ 3.
2. **Stopword estese con le elisioni** italiane (dell, all, nell, sull, dall, quest, quell, qual…).
3. **`denseFloor = 0,835`**, calibrato sulla separazione misurata:
   - in dominio: coseno min **0,843**;
   - fuori dominio: coseno max **0,821**.

## Esito
Con queste modifiche il golden set dà **recall@5 100%**, **gate-pass 100%**, **anti-allucinazione
100%** (`tools/eval/run_eval.py`). Le stesse costanti sono replicate in Kotlin
(`SqliteCorpusReader.buildMatchQuery`/`STOPWORDS`, `HybridRetriever.denseFloor`).

## Limiti
La calibrazione è sul corpus seed (30 chunk). Va **ri-verificata** con lo stesso script quando si
aggiunge un corpus reale più ampio; `denseFloor` e la soglia RRF restano parametri (la soglia è anche
regolabile dall'utente in Impostazioni).

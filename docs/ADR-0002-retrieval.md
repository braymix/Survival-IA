# ADR-0002 — Retrieval: vector store, tokenizer e anti-allucinazione

- **Stato:** Accettato (Fase 3)
- **Data:** 2026-08-06

## 1. Vector store: brute-force cosine invece di sqlite-vec (per ora)

**Contesto.** Il piano prevedeva sqlite-vec come store vettoriale primario. `androidx.sqlite`
espone `BundledSQLiteDriver.addExtension(...)`, quindi sqlite-vec è caricabile *in teoria*, ma il
caricamento dell'estensione non è verificabile nell'ambiente di sviluppo attuale (nessun device/emulatore).

**Decisione.** Spedire il **fallback documentato**: dense retrieval con **cosine brute-force** sui
vettori `float32` letti da `chunk_vectors`. I vettori sono L2-normalizzati in ingest, quindi la
similarità coseno è un semplice prodotto scalare. Per il nostro ordine di grandezza (≤ ~50k chunk)
il costo è trascurabile e la cache in memoria è caricata una sola volta.

**Conseguenze.** FTS5/BM25 resta nativo (bundled SQLite). Quando si vorrà attivare sqlite-vec basterà
implementare un `CorpusReader` alternativo che crea una tabella `vec0` e delega la KNN all'estensione,
dietro la stessa interfaccia. Nessun cambiamento a valle.

## 2. Tokenizer SentencePiece Unigram in Kotlin (parità verificata)

**Decisione.** Implementare in puro Kotlin il tokenizer Unigram di e5 (`SpmUnigramTokenizer`):
normalizzazione NFKC + Metaspace + Viterbi sul lattice. Il vocab (250k pezzi) è un asset gzip (~2,3 MB).

**Perché non una libreria nativa.** La parità esatta con il tokenizer Python è **verificata da test**
(`SpmUnigramTokenizerTest`, 15/15 fixture reali IT/EN). Garantire che l'embedding della query on-device
sia identico a quello calcolato in ingest è la condizione per un retrieval denso corretto, e un
tokenizer in-process senza dipendenze native è più semplice da distribuire e testare.

## 3. Anti-allucinazione: perché la soglia RRF da sola non basta

**Problema.** Il dense retrieval restituisce **sempre** i k vicini più prossimi, anche per query fuori
dominio; e5 ha un coseno di base alto (~0,85) per qualunque testo. Quindi il punteggio fuso RRF, da
solo, lascerebbe passare "chi ha vinto il mondiale 2022".

**Decisione.** La `ConfidenceGate` procede solo se **entrambe** le condizioni valgono:
1. il miglior punteggio fuso supera la soglia (grounding minimo), **e**
2. la query è "in dominio": c'è **supporto lessicale** (BM25 su token di contenuto, con stopword IT/EN
   rimosse) **oppure** il coseno denso del primo risultato supera un *floor* assoluto (default 0,84,
   calibrato in Fase 6).

Il segnale forte è quello lessicale: una query fuori dominio non condivide vocabolario di contenuto
con il corpus, quindi FTS non restituisce nulla e — se anche il coseno denso è sotto il floor — si
risponde "nessuna fonte affidabile" **senza invocare l'LLM**. Questo è il meccanismo che i test
anti-allucinazione della Fase 6 devono validare al 100%.

## 4. Miglioramento di qualità: header contestuali nei chunk

In ingest ogni chunk viene embeddato con un **header contestuale** (titolo documento + sezione) davanti
al testo. Senza, un chunk fatto di sole voci di lista ("1. …, 2. …") perde il contesto e produce
embedding generici. Con l'header, il dense retrieval sui 6 documenti seed diventa corretto
(acqua→acqua, emorragia→medico, navigazione→navigazione, fuoco→fuoco).

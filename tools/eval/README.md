# tools/eval — valutazione del retrieval

## Golden set
`questions.yaml`: domande **in dominio** (con `doc` atteso) e **fuori dominio** (devono restituire
NESSUNA_FONTE). Serve a misurare recall e anti-allucinazione.

## Eseguire
```bash
. ../.venv/bin/activate          # stesse dipendenze di tools/ingest
python run_eval.py               # usa il DB in app/src/main/assets/corpus/
```
Esce con codice ≠ 0 se il test anti-allucinazione non è al 100% (utile in CI).

## Metriche
- **recall@5**: il documento atteso è tra i primi 5 chunk fusi.
- **gate-pass**: la query supererebbe soglia+dominio (verrebbe passata all'LLM).
- **anti-allucinazione**: percentuale di query fuori dominio correttamente respinte (deve essere 100%).

Lo script replica la logica del `HybridRetriever` Kotlin (stessa soglia, `denseFloor`, stopword,
match esatto), così le metriche riflettono il comportamento on-device. Risultati in
`docs/BENCHMARKS.md`.

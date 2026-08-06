# tools/ingest — pipeline di costruzione del corpus

Estrae testo da PDF/EPUB/TXT/Markdown → chunking strutturale → categoria → embedding e5
(ONNX, identico all'on-device) → `survival_corpus.db` (SQLite) + `corpus_manifest.json`.

## Setup
```bash
python3 -m venv ../.venv && . ../.venv/bin/activate
pip install -r requirements.txt
```

## Scaricare il modello di embedding (una tantum)
Il modello ONNX + tokenizer NON sono nel repo. Scaricali in `../models/e5-small/`:
```bash
python - <<'PY'
from huggingface_hub import hf_hub_download
import shutil, os
repo="Xenova/multilingual-e5-small"; dst="../models/e5-small"; os.makedirs(dst, exist_ok=True)
for src,name in [("onnx/model_quantized.onnx","model_int8.onnx"),("tokenizer.json","tokenizer.json")]:
    shutil.copy(hf_hub_download(repo_id=repo, filename=src), os.path.join(dst,name))
PY
```

## Costruire il corpus
```bash
python build_corpus.py --input seed_corpus --out out/survival_corpus.db --validate
# poi copia il DB tra gli asset dell'app:
cp out/survival_corpus.db  ../../app/src/main/assets/corpus/
cp out/corpus_manifest.json ../../app/src/main/assets/corpus/
```

## Opzioni principali
- `--input DIR` cartella con i documenti (default `seed_corpus`).
- `--ocr` OCR (pytesseract) per PDF scansionati.
- `--validate` sanity check dopo il build (chunk vuoti, categorie fuori lista, orfani,
  conteggi FTS/vettori, norme embedding, quasi-duplicati MinHash).
- `--validate-only` valida un `--out` già esistente.

## Aggiungere fonti reali
1. Metti i file (PDF/EPUB/…) in una cartella e passala con `--input`.
2. Verifica la licenza in `sources.yaml` (le `UNKNOWN` sono escluse).
3. Aggiorna `docs/LICENSES.md` con l'attribuzione.

## Schema del DB
`documents`, `chunks`, `chunks_fts` (FTS5/BM25), `chunk_vectors` (float32 BLOB, dim 384),
`meta`. Gli embedding usano un **header contestuale** (titolo doc + sezione) + testo, con
prefisso `passage: ` (obbligatorio per e5). Le query a runtime usano prefisso `query: `.

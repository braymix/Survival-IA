#!/usr/bin/env python3
"""build_corpus.py — pipeline di ingest offline per SurvivalWiki AI.

Estrae testo da PDF/EPUB/TXT/Markdown, pulisce, applica un chunking STRUTTURALE
(per heading, finestre ~450 token, overlap ~80, senza mai spezzare una lista
numerata), classifica ogni chunk in una categoria chiusa, calcola gli embedding
e5-small (via ONNX, identici a quelli on-device) e produce un DB SQLite portabile
(documents / chunks / chunks_fts / chunk_vectors) + un manifest.

Uso tipico:
    python build_corpus.py --input seed_corpus --out out/survival_corpus.db --validate

Vedi README in tools/ingest per i dettagli.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sqlite3
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple

import numpy as np
import yaml

from embedder import EMBED_DIM, E5Embedder

SCHEMA_VERSION = 1
TARGET_TOKENS = 450
OVERLAP_TOKENS = 80
VALID_CATEGORIES = [
    "acqua", "fuoco", "rifugio", "cibo", "medico",
    "navigazione", "segnalazione", "attrezzatura", "clima", "sicurezza",
]


# --------------------------------------------------------------------------- #
# Modelli dati
# --------------------------------------------------------------------------- #
@dataclass
class Block:
    """Unità di testo minima (paragrafo o blocco-lista) con pagina di origine."""
    text: str
    page: Optional[int]
    is_list: bool = False


@dataclass
class Section:
    title: Optional[str]
    blocks: List[Block] = field(default_factory=list)


@dataclass
class Document:
    doc_id: str
    title: str
    author: Optional[str]
    year: Optional[int]
    license: str
    lang: str
    category: Optional[str]      # categoria dichiarata (default), può essere sovrascritta per chunk
    source_path: str
    sections: List[Section] = field(default_factory=list)


@dataclass
class Chunk:
    chunk_id: str
    doc: Document
    section: Optional[str]
    page_start: Optional[int]
    page_end: Optional[int]
    text: str
    category: Optional[str] = None


# --------------------------------------------------------------------------- #
# Lettura front-matter e file
# --------------------------------------------------------------------------- #
FRONT_MATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


def parse_front_matter(text: str) -> Tuple[dict, str]:
    m = FRONT_MATTER_RE.match(text)
    if not m:
        return {}, text
    meta = yaml.safe_load(m.group(1)) or {}
    return meta, text[m.end():]


def clean_text(s: str) -> str:
    """De-hyphenation a fine riga, unione delle righe di un paragrafo, whitespace."""
    s = re.sub(r"(\w)-\n(\w)", r"\1\2", s)   # de-hyphenation
    s = s.replace("\n", " ")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


NUM_LIST_RE = re.compile(r"^\s*\d+[.)]\s+")


def split_blocks_from_markdown(body: str) -> List[Section]:
    """Divide un Markdown in sezioni (per heading) e blocchi (paragrafi/liste numerate)."""
    sections: List[Section] = []
    current = Section(title=None)
    sections.append(current)

    # Raggruppa per paragrafi separati da riga vuota, preservando le liste numerate come blocco unico.
    lines = body.splitlines()
    buffer: List[str] = []
    buffer_is_list = False

    def flush():
        nonlocal buffer, buffer_is_list
        if buffer:
            text = clean_text("\n".join(buffer))
            if text:
                current.blocks.append(Block(text=text, page=None, is_list=buffer_is_list))
        buffer = []
        buffer_is_list = False

    for line in lines:
        heading = re.match(r"^(#{1,6})\s+(.*)$", line)
        if heading:
            flush()
            current = Section(title=heading.group(2).strip())
            sections.append(current)
            continue
        if line.strip() == "":
            flush()
            continue
        if NUM_LIST_RE.match(line):
            buffer_is_list = True
        buffer.append(line)
    flush()

    # Rimuovi sezioni vuote (es. la prima sezione "None" senza contenuto).
    return [s for s in sections if s.blocks]


def read_markdown(path: str, meta: dict, body: str) -> Document:
    doc = Document(
        doc_id=meta.get("doc_id") or os.path.splitext(os.path.basename(path))[0],
        title=meta.get("title") or os.path.basename(path),
        author=meta.get("author"),
        year=meta.get("year"),
        license=meta.get("license", "UNKNOWN"),
        lang=meta.get("lang", "it"),
        category=meta.get("category"),
        source_path=os.path.basename(path),
        sections=split_blocks_from_markdown(body),
    )
    return doc


def read_txt(path: str) -> Document:
    with open(path, encoding="utf-8", errors="replace") as f:
        raw = f.read()
    meta, body = parse_front_matter(raw)
    if body is raw and not meta:
        body = raw
    section = Section(title=None)
    for para in re.split(r"\n\s*\n", body):
        t = clean_text(para)
        if t:
            section.blocks.append(Block(text=t, page=None, is_list=bool(NUM_LIST_RE.match(para))))
    doc = Document(
        doc_id=meta.get("doc_id") or os.path.splitext(os.path.basename(path))[0],
        title=meta.get("title") or os.path.basename(path),
        author=meta.get("author"), year=meta.get("year"),
        license=meta.get("license", "UNKNOWN"), lang=meta.get("lang", "it"),
        category=meta.get("category"), source_path=os.path.basename(path),
        sections=[section] if section.blocks else [],
    )
    return doc


def _drop_repeated_headers(pages: List[str]) -> List[str]:
    """Rimuove righe che ricorrono su molte pagine (header/footer ripetuti)."""
    if len(pages) < 3:
        return pages
    from collections import Counter
    counter: Counter = Counter()
    for p in pages:
        for line in {ln.strip() for ln in p.splitlines() if ln.strip()}:
            counter[line] += 1
    threshold = max(3, int(0.5 * len(pages)))
    repeated = {line for line, c in counter.items() if c >= threshold and len(line) < 80}
    cleaned = []
    for p in pages:
        cleaned.append("\n".join(ln for ln in p.splitlines() if ln.strip() not in repeated))
    return cleaned


def read_pdf(path: str, ocr: bool) -> Document:
    import fitz  # PyMuPDF
    meta_doc = Document(
        doc_id=os.path.splitext(os.path.basename(path))[0],
        title=os.path.basename(path), author=None, year=None,
        license="UNKNOWN", lang="it", category=None,
        source_path=os.path.basename(path),
    )
    pdf = fitz.open(path)
    if pdf.metadata:
        meta_doc.title = pdf.metadata.get("title") or meta_doc.title
        meta_doc.author = pdf.metadata.get("author") or None

    page_texts: List[str] = []
    page_sizes: List[list] = []
    for page in pdf:
        text = page.get_text("text")
        if ocr and len(text.strip()) < 20:
            try:
                import pytesseract  # lazy: opzionale
                from PIL import Image
                pix = page.get_pixmap(dpi=200)
                img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
                text = pytesseract.image_to_string(img, lang="ita+eng")
            except Exception as e:  # pragma: no cover
                print(f"  [ocr] fallita su {path} p{page.number}: {e}", file=sys.stderr)
        page_texts.append(text)
        # Raccogli le dimensioni dei font per la euristica heading.
        sizes = []
        for b in page.get_text("dict").get("blocks", []):
            for ln in b.get("lines", []):
                for sp in ln.get("spans", []):
                    sizes.append((sp.get("size", 0.0), sp.get("text", "")))
        page_sizes.append(sizes)

    page_texts = _drop_repeated_headers(page_texts)

    all_sizes = [s for sizes in page_sizes for s, _ in sizes if s > 0]
    body_size = float(np.median(all_sizes)) if all_sizes else 0.0
    heading_threshold = body_size * 1.15

    sections: List[Section] = [Section(title=None)]
    for pageno, text in enumerate(page_texts, start=1):
        # Euristica heading: prima riga "grande e corta" apre una sezione.
        big_lines = {t.strip() for s, t in page_sizes[pageno - 1]
                     if s >= heading_threshold and 0 < len(t.strip()) < 80}
        for para in re.split(r"\n\s*\n", text):
            stripped = para.strip()
            if not stripped:
                continue
            if stripped in big_lines:
                sections.append(Section(title=clean_text(stripped)))
                continue
            t = clean_text(para)
            if t:
                sections[-1].blocks.append(
                    Block(text=t, page=pageno, is_list=bool(NUM_LIST_RE.match(para)))
                )
    meta_doc.sections = [s for s in sections if s.blocks]
    return meta_doc


def read_epub(path: str) -> Document:
    import ebooklib
    from ebooklib import epub
    from bs4 import BeautifulSoup

    book = epub.read_epub(path)
    title = (book.get_metadata("DC", "title") or [["", ""]])[0][0] or os.path.basename(path)
    author = None
    creators = book.get_metadata("DC", "creator")
    if creators:
        author = creators[0][0]

    sections: List[Section] = [Section(title=None)]
    for item in book.get_items_of_type(ebooklib.ITEM_DOCUMENT):
        soup = BeautifulSoup(item.get_content(), "html.parser")
        for el in soup.find_all(["h1", "h2", "h3", "p", "li"]):
            txt = clean_text(el.get_text(" "))
            if not txt:
                continue
            if el.name in ("h1", "h2", "h3"):
                sections.append(Section(title=txt))
            else:
                sections[-1].blocks.append(
                    Block(text=txt, page=None, is_list=(el.name == "li"))
                )
    return Document(
        doc_id=os.path.splitext(os.path.basename(path))[0], title=title, author=author,
        year=None, license="UNKNOWN", lang="it", category=None,
        source_path=os.path.basename(path), sections=[s for s in sections if s.blocks],
    )


def read_document(path: str, ocr: bool) -> Optional[Document]:
    ext = os.path.splitext(path)[1].lower()
    if ext in (".md", ".markdown"):
        with open(path, encoding="utf-8", errors="replace") as f:
            raw = f.read()
        meta, body = parse_front_matter(raw)
        return read_markdown(path, meta, body)
    if ext == ".txt":
        return read_txt(path)
    if ext == ".pdf":
        return read_pdf(path, ocr)
    if ext == ".epub":
        return read_epub(path)
    return None


# --------------------------------------------------------------------------- #
# Chunking strutturale
# --------------------------------------------------------------------------- #
def chunk_document(doc: Document, embedder: E5Embedder) -> List[Chunk]:
    chunks: List[Chunk] = []
    idx = 0
    for section in doc.sections:
        for text, p_start, p_end in _chunk_section(section, embedder):
            idx += 1
            chunks.append(Chunk(
                chunk_id=f"{doc.doc_id}::{idx:04d}",
                doc=doc, section=section.title,
                page_start=p_start, page_end=p_end, text=text,
            ))
    return chunks


def _chunk_section(section: Section, embedder: E5Embedder) -> List[Tuple[str, Optional[int], Optional[int]]]:
    """Accumula blocchi fino a ~TARGET_TOKENS; una lista numerata non viene mai spezzata.
    Genera overlap ripartendo dagli ultimi blocchi entro OVERLAP_TOKENS."""
    results: List[Tuple[str, Optional[int], Optional[int]]] = []
    cur_blocks: List[Block] = []
    cur_tokens = 0

    def token_len(b: Block) -> int:
        return embedder.token_count(b.text)

    def emit():
        if not cur_blocks:
            return
        text = " ".join(b.text for b in cur_blocks).strip()
        pages = [b.page for b in cur_blocks if b.page is not None]
        results.append((text, min(pages) if pages else None, max(pages) if pages else None))

    for block in section.blocks:
        bt = token_len(block)
        # Se il blocco da solo supera il target (tipico di una lista lunga), lo emettiamo intero:
        # meglio un chunk più grande che una procedura spezzata.
        if bt >= TARGET_TOKENS and cur_blocks:
            emit()
            cur_blocks, cur_tokens = [], 0
        if cur_tokens + bt > TARGET_TOKENS and cur_blocks:
            emit()
            # Overlap: mantieni gli ultimi blocchi entro OVERLAP_TOKENS (ma mai spezzando nulla).
            carry: List[Block] = []
            carry_tokens = 0
            for b in reversed(cur_blocks):
                tl = token_len(b)
                if carry_tokens + tl > OVERLAP_TOKENS:
                    break
                carry.insert(0, b)
                carry_tokens += tl
            cur_blocks = carry
            cur_tokens = carry_tokens
        cur_blocks.append(block)
        cur_tokens += bt
    emit()
    return results


# --------------------------------------------------------------------------- #
# Classificazione categoria
# --------------------------------------------------------------------------- #
def classify(chunk: Chunk, keyword_rules: Dict[str, List[str]], overrides: Dict[str, str]) -> str:
    """Priorità: override manuale > categoria dichiarata dal documento (front-matter,
    autorevole per i doc che la forniscono) > classificazione a keyword > fallback.

    Nota: per i documenti che dichiarano una categoria (es. le schede seed) quella è la
    verità dell'autore; la classificazione a keyword serve ai documenti che NON la
    dichiarano (tipicamente PDF/EPUB importati)."""
    if chunk.doc.doc_id in overrides:
        return overrides[chunk.doc.doc_id]
    if chunk.doc.category in VALID_CATEGORIES:
        return chunk.doc.category
    text = chunk.text.lower()
    best_cat, best_score = None, 0
    for cat, kws in keyword_rules.items():
        score = sum(text.count(kw.lower()) for kw in kws)
        if score > best_score:
            best_cat, best_score = cat, score
    return best_cat or "sicurezza"


def embed_input(chunk: Chunk) -> str:
    """Testo effettivamente dato all'embedder: header contestuale (titolo doc + sezione)
    + testo. Senza questo header, un chunk fatto di sole voci di lista ("1. …, 2. …")
    perde il contesto e produce embedding generici che confondono il retrieval denso."""
    header_parts = [chunk.doc.title]
    if chunk.section:
        header_parts.append(chunk.section)
    header = " — ".join(header_parts)
    return f"passage: {header}\n{chunk.text}"


# --------------------------------------------------------------------------- #
# Scrittura DB
# --------------------------------------------------------------------------- #
DDL = """
CREATE TABLE documents (
    doc_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT,
    year INTEGER,
    license TEXT NOT NULL,
    category TEXT,
    lang TEXT,
    source_path TEXT,
    n_chunks INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE chunks (
    rowid INTEGER PRIMARY KEY,
    chunk_id TEXT UNIQUE NOT NULL,
    doc_id TEXT NOT NULL REFERENCES documents(doc_id),
    titolo_doc TEXT,
    autore TEXT,
    anno INTEGER,
    licenza TEXT,
    sezione TEXT,
    pagina_inizio INTEGER,
    pagina_fine INTEGER,
    testo TEXT NOT NULL,
    categoria TEXT,
    lingua TEXT
);
CREATE VIRTUAL TABLE chunks_fts USING fts5(
    chunk_id UNINDEXED,
    testo,
    sezione,
    titolo_doc,
    tokenize='unicode61 remove_diacritics 2'
);
CREATE TABLE chunk_vectors (
    chunk_id TEXT PRIMARY KEY REFERENCES chunks(chunk_id),
    dim INTEGER NOT NULL,
    vector BLOB NOT NULL
);
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
CREATE INDEX idx_chunks_doc ON chunks(doc_id);
CREATE INDEX idx_chunks_cat ON chunks(categoria);
"""


def write_db(db_path: str, docs: List[Document], chunks: List[Chunk], vectors: np.ndarray) -> None:
    if os.path.exists(db_path):
        os.remove(db_path)
    os.makedirs(os.path.dirname(db_path) or ".", exist_ok=True)
    con = sqlite3.connect(db_path)
    try:
        con.executescript(DDL)
        counts: Dict[str, int] = {}
        for d in docs:
            counts[d.doc_id] = sum(1 for c in chunks if c.doc.doc_id == d.doc_id)
            con.execute(
                "INSERT INTO documents(doc_id,title,author,year,license,category,lang,source_path,n_chunks)"
                " VALUES(?,?,?,?,?,?,?,?,?)",
                (d.doc_id, d.title, d.author, d.year, d.license, d.category, d.lang,
                 d.source_path, counts[d.doc_id]),
            )
        for i, c in enumerate(chunks):
            rowid = i + 1
            con.execute(
                "INSERT INTO chunks(rowid,chunk_id,doc_id,titolo_doc,autore,anno,licenza,sezione,"
                "pagina_inizio,pagina_fine,testo,categoria,lingua) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (rowid, c.chunk_id, c.doc.doc_id, c.doc.title, c.doc.author, c.doc.year,
                 c.doc.license, c.section, c.page_start, c.page_end, c.text, c.category, c.doc.lang),
            )
            con.execute(
                "INSERT INTO chunks_fts(rowid,chunk_id,testo,sezione,titolo_doc) VALUES(?,?,?,?,?)",
                (rowid, c.chunk_id, c.text, c.section or "", c.doc.title),
            )
            con.execute(
                "INSERT INTO chunk_vectors(chunk_id,dim,vector) VALUES(?,?,?)",
                (c.chunk_id, EMBED_DIM, vectors[i].astype(np.float32).tobytes()),
            )
        con.execute("INSERT INTO meta(key,value) VALUES('schema_version',?)", (str(SCHEMA_VERSION),))
        con.execute("INSERT INTO meta(key,value) VALUES('embedding_model',?)",
                    ("multilingual-e5-small-int8",))
        con.execute("INSERT INTO meta(key,value) VALUES('embedding_dim',?)", (str(EMBED_DIM),))
        con.execute("INSERT INTO meta(key,value) VALUES('built_at',?)",
                    (datetime.now(timezone.utc).isoformat(),))
        con.commit()
    finally:
        con.close()


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


# --------------------------------------------------------------------------- #
# Validazione
# --------------------------------------------------------------------------- #
def validate_db(db_path: str) -> int:
    """Sanity check: chunk vuoti, duplicati (MinHash), norme anomale, orfani, conteggi FTS/vettori."""
    con = sqlite3.connect(db_path)
    errors, warnings = [], []
    try:
        rows = con.execute("SELECT chunk_id,doc_id,testo,categoria FROM chunks").fetchall()
        n = len(rows)
        # 1) chunk vuoti
        empty = [r[0] for r in rows if not (r[2] or "").strip()]
        if empty:
            errors.append(f"{len(empty)} chunk vuoti (es. {empty[:3]})")

        # 2) categoria valida
        bad_cat = [r[0] for r in rows if r[3] not in VALID_CATEGORIES]
        if bad_cat:
            errors.append(f"{len(bad_cat)} chunk con categoria fuori lista (es. {bad_cat[:3]})")

        # 3) orfani (doc_id inesistente in documents)
        doc_ids = {r[0] for r in con.execute("SELECT doc_id FROM documents").fetchall()}
        orphans = [r[0] for r in rows if r[1] not in doc_ids]
        if orphans:
            errors.append(f"{len(orphans)} chunk orfani (es. {orphans[:3]})")

        # 4) conteggi coerenti FTS / vettori
        fts_n = con.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
        vec_n = con.execute("SELECT count(*) FROM chunk_vectors").fetchone()[0]
        if fts_n != n:
            errors.append(f"chunks_fts ({fts_n}) != chunks ({n})")
        if vec_n != n:
            errors.append(f"chunk_vectors ({vec_n}) != chunks ({n})")

        # 5) norme embedding ~ 1
        anomal = 0
        for cid, dim, blob in con.execute("SELECT chunk_id,dim,vector FROM chunk_vectors"):
            v = np.frombuffer(blob, dtype=np.float32)
            if v.shape[0] != dim or abs(float(np.linalg.norm(v)) - 1.0) > 0.05:
                anomal += 1
        if anomal:
            errors.append(f"{anomal} embedding con norma anomala o dimensione errata")

        # 6) near-duplicati con MinHash
        try:
            from datasketch import MinHash, MinHashLSH
            lsh = MinHashLSH(threshold=0.9, num_perm=64)
            mh_by_id = {}
            dups = []
            for cid, _did, testo, _cat in rows:
                mh = MinHash(num_perm=64)
                for tok in set((testo or "").lower().split()):
                    mh.update(tok.encode())
                near = lsh.query(mh)
                if near:
                    dups.append((cid, near[0]))
                lsh.insert(cid, mh)
                mh_by_id[cid] = mh
            if dups:
                warnings.append(f"{len(dups)} coppie di chunk quasi-duplicati (es. {dups[:2]})")
        except Exception as e:  # pragma: no cover
            warnings.append(f"MinHash non eseguito: {e}")

        print(f"[validate] {n} chunk, {len(doc_ids)} documenti, fts={fts_n}, vettori={vec_n}")
        for w in warnings:
            print(f"  [warn] {w}")
        for e in errors:
            print(f"  [ERR ] {e}")
        if not errors:
            print("[validate] OK — nessun errore bloccante.")
        return 1 if errors else 0
    finally:
        con.close()


# --------------------------------------------------------------------------- #
# Orchestrazione
# --------------------------------------------------------------------------- #
def collect_input_files(input_dir: str) -> List[str]:
    exts = (".md", ".markdown", ".txt", ".pdf", ".epub")
    files = []
    for root, _dirs, names in os.walk(input_dir):
        for name in sorted(names):
            if name.lower().endswith(exts):
                files.append(os.path.join(root, name))
    return files


def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description="Costruisce il corpus SQLite per SurvivalWiki AI.")
    ap.add_argument("--input", default=os.path.join(here, "seed_corpus"),
                    help="Cartella con PDF/EPUB/TXT/Markdown (default: seed_corpus).")
    ap.add_argument("--out", default=os.path.join(here, "out", "survival_corpus.db"))
    ap.add_argument("--model", default=os.path.join(here, "..", "models", "e5-small"))
    ap.add_argument("--categories", default=os.path.join(here, "categories.yaml"))
    ap.add_argument("--ocr", action="store_true", help="Abilita OCR (pytesseract) sui PDF scansionati.")
    ap.add_argument("--validate", action="store_true", help="Esegue i sanity check dopo il build.")
    ap.add_argument("--validate-only", action="store_true", help="Solo validazione di --out esistente.")
    args = ap.parse_args()

    if args.validate_only:
        return validate_db(args.out)

    with open(args.categories, encoding="utf-8") as f:
        cat_cfg = yaml.safe_load(f) or {}
    keyword_rules = cat_cfg.get("keywords", {})
    overrides = cat_cfg.get("overrides", {}) or {}

    print(f"[ingest] modello embedding: {args.model}")
    embedder = E5Embedder(args.model)

    files = collect_input_files(args.input)
    if not files:
        print(f"[ingest] nessun file in {args.input}", file=sys.stderr)
        return 2
    print(f"[ingest] {len(files)} file trovati in {args.input}")

    docs: List[Document] = []
    all_chunks: List[Chunk] = []
    for path in files:
        doc = read_document(path, args.ocr)
        if doc is None or not doc.sections:
            print(f"  [skip] {os.path.basename(path)} (nessun contenuto estratto)")
            continue
        chunks = chunk_document(doc, embedder)
        for c in chunks:
            c.category = classify(c, keyword_rules, overrides)
        if doc.category is None and chunks:
            # categoria del documento = moda delle categorie dei suoi chunk
            from collections import Counter
            doc.category = Counter(c.category for c in chunks).most_common(1)[0][0]
        docs.append(doc)
        all_chunks.extend(chunks)
        print(f"  [ok] {doc.doc_id}: {len(chunks)} chunk, categoria doc={doc.category}, "
              f"licenza={doc.license}")

    if not all_chunks:
        print("[ingest] nessun chunk prodotto", file=sys.stderr)
        return 2

    print(f"[ingest] embedding di {len(all_chunks)} chunk (prefisso 'passage: ' + header contestuale)…")
    vectors = embedder.embed_batch([embed_input(c) for c in all_chunks])

    write_db(args.out, docs, all_chunks, vectors)
    print(f"[ingest] DB scritto: {args.out}")

    # Manifest
    from collections import Counter
    manifest = {
        "schema_version": SCHEMA_VERSION,
        "built_at": datetime.now(timezone.utc).isoformat(),
        "embedding_model": "multilingual-e5-small-int8",
        "embedding_dim": EMBED_DIM,
        "document_count": len(docs),
        "chunk_count": len(all_chunks),
        "categories": dict(Counter(c.category for c in all_chunks)),
        "sha256": sha256_of(args.out),
    }
    manifest_path = os.path.join(os.path.dirname(args.out), "corpus_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(f"[ingest] manifest: {manifest_path}")
    print(json.dumps(manifest["categories"], ensure_ascii=False))

    if args.validate:
        return validate_db(args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

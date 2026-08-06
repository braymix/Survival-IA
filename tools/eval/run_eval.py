#!/usr/bin/env python3
"""run_eval.py — valutazione del retrieval sul golden set.

Replica la pipeline Kotlin (dense e5 + FTS5/BM25 + RRF + gate 'in dominio') usando lo STESSO
DB e lo STESSO modello e5, così le metriche riflettono il comportamento on-device.

Metriche:
  - recall@5 (in dominio): il documento atteso è tra i primi 5 chunk fusi;
  - gate-pass (in dominio): la query supera la soglia+dominio (verrebbe passata all'LLM);
  - anti-allucinazione (fuori dominio): la query viene respinta (NESSUNA_FONTE). Atteso 100%.

Uso:
    python run_eval.py [--db ../../app/src/main/assets/corpus/survival_corpus.db]
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import time

import numpy as np
import yaml

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "ingest"))
from embedder import E5Embedder  # noqa: E402

RRF_K = 60
THRESHOLD = 0.010
# Floor calibrato: in-domain dense cos min=0.843, OOD max=0.821 → 0.835 separa nettamente.
DENSE_FLOOR = 0.835
TOP_K = 20

# Deve combaciare con SqliteCorpusReader.STOPWORDS (IT/EN). Include le ELISIONI (dell', qual, all'…)
# che, se lasciate passare, causano falsi positivi lessicali.
STOPWORDS = {
    "il", "lo", "la", "le", "gli", "un", "uno", "una", "di", "da", "del", "dei", "della",
    "delle", "degli", "dell", "al", "allo", "alla", "all", "con", "su", "sul", "sull", "per",
    "tra", "fra", "come", "che", "chi", "cosa", "quando", "dove", "perche", "e", "ed", "o",
    "ma", "se", "si", "no", "non", "mi", "ti", "ci", "vi", "ne", "nel", "nell", "in", "a",
    "ha", "ho", "hai", "sono", "essere", "fare", "posso", "devo", "vorrei", "qual", "quale",
    "quali", "quest", "questo", "questa", "quell", "dall", "coi", "col", "ai", "agli", "dai",
    "ultimo", "ultima", "ultimi", "ultime", "primo", "prima", "cosa", "modo", "così",
    "the", "an", "of", "to", "on", "for", "and", "or", "how", "what", "who",
    "when", "where", "why", "is", "are", "do", "does", "i", "can", "should", "with",
}


def content_tokens(query: str):
    import re
    return [t for t in re.findall(r"[\wàèéìòù]+", query.lower())
            if len(t) >= 3 and t not in STOPWORDS]


def build_match(query: str) -> str:
    # Match ESATTO (niente prefix `*`): il prefix causava collisioni tipo "ripara"→"riparo".
    toks = content_tokens(query)
    return " OR ".join(f'"{t}"' for t in toks)


def load_synonyms(here: str):
    path = os.path.join(here, "..", "..", "app", "src", "main", "assets", "synonyms.json")
    if not os.path.exists(path):
        return {}
    raw = json.load(open(path, encoding="utf-8"))
    return {k: v for k, v in raw.items() if not k.startswith("_") and isinstance(v, list)}


class Retriever:
    def __init__(self, db_path: str, model_dir: str, synonyms: dict | None = None):
        self.con = sqlite3.connect(db_path)
        self.emb = E5Embedder(model_dir)
        self.synonyms = synonyms or {}
        rows = self.con.execute("SELECT chunk_id, doc_id FROM chunks").fetchall()
        self.doc_of = {cid: did for cid, did in rows}
        self.vecs = {cid: np.frombuffer(b, dtype=np.float32)
                     for cid, _dim, b in self.con.execute("SELECT chunk_id,dim,vector FROM chunk_vectors")}
        self.all_ids = list(self.vecs.keys())

    def expand_lexical(self, query: str) -> str:
        # Mirror di QueryRewriter: aggiunge i sinonimi delle chiavi contenute nella query.
        low = query.lower()
        extra = []
        for key, syns in self.synonyms.items():
            if key.lower() in low:
                extra.extend(syns)
        return query + " " + " ".join(extra) if extra else query

    def dense(self, query: str):
        qv = self.emb.embed(f"query: {query}")
        scored = sorted(((float(qv @ self.vecs[cid]), cid) for cid in self.all_ids), reverse=True)[:TOP_K]
        return scored  # list of (cos, chunk_id)

    def lexical(self, query: str):
        match = build_match(self.expand_lexical(query))
        if not match:
            return []
        cur = self.con.execute(
            "SELECT chunk_id FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT ?",
            (match, TOP_K),
        )
        return [r[0] for r in cur.fetchall()]

    def retrieve(self, query: str):
        dense = self.dense(query)
        dense_ids = [cid for _cos, cid in dense]
        top_cos = dense[0][0] if dense else 0.0
        lexical_ids = self.lexical(query)

        # RRF
        scores = {}
        for lst in (dense_ids, lexical_ids):
            for rank, cid in enumerate(lst):
                scores[cid] = scores.get(cid, 0.0) + 1.0 / (RRF_K + rank + 1)
        fused = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)

        best = fused[0][1] if fused else 0.0
        has_lexical = len(lexical_ids) > 0
        in_domain = has_lexical or top_cos >= DENSE_FLOOR
        proceed = best >= THRESHOLD and in_domain
        return fused, proceed


def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=os.path.join(here, "..", "..", "app", "src", "main",
                                                 "assets", "corpus", "survival_corpus.db"))
    ap.add_argument("--model", default=os.path.join(here, "..", "models", "e5-small"))
    ap.add_argument("--questions", default=os.path.join(here, "questions.yaml"))
    args = ap.parse_args()

    with open(args.questions, encoding="utf-8") as f:
        gold = yaml.safe_load(f)

    r = Retriever(args.db, args.model, synonyms=load_synonyms(here))

    # --- in dominio ---
    in_dom = gold["in_domain"]
    recall_hits = 0
    gate_pass = 0
    t_embed, t_retr = [], []
    failures = []
    for item in in_dom:
        q, expected = item["q"], item["doc"]
        t0 = time.perf_counter()
        r.emb.embed(f"query: {q}")
        t_embed.append(time.perf_counter() - t0)
        t1 = time.perf_counter()
        fused, proceed = r.retrieve(q)
        t_retr.append(time.perf_counter() - t1)
        top5_docs = [r.doc_of[cid] for cid, _ in fused[:5]]
        hit = expected in top5_docs
        recall_hits += hit
        gate_pass += proceed
        if not hit:
            failures.append((q, expected, top5_docs))

    # --- fuori dominio (anti-allucinazione) ---
    ood = gold["out_of_domain"]
    ood_rejected = 0
    ood_leaks = []
    for q in ood:
        _fused, proceed = r.retrieve(q)
        if not proceed:
            ood_rejected += 1
        else:
            ood_leaks.append(q)

    n = len(in_dom)
    print(f"== Golden set: {n} in dominio, {len(ood)} fuori dominio ==")
    print(f"recall@5 (in dominio):     {recall_hits}/{n} = {recall_hits / n:.1%}")
    print(f"gate-pass (in dominio):    {gate_pass}/{n} = {gate_pass / n:.1%}")
    print(f"anti-allucinazione (OOD):  {ood_rejected}/{len(ood)} = {ood_rejected / len(ood):.1%} respinte")
    print(f"tempo medio embedding query: {1000 * np.mean(t_embed):.1f} ms")
    print(f"tempo medio retrieval:       {1000 * np.mean(t_retr):.1f} ms (brute-force, {len(r.all_ids)} chunk)")
    if failures:
        print("\nMiss recall@5:")
        for q, exp, got in failures:
            print(f"  '{q}' atteso={exp} top5={got}")
    if ood_leaks:
        print("\n[ATTENZIONE] Query fuori dominio NON respinte:")
        for q in ood_leaks:
            print(f"  '{q}'")

    # Il test anti-allucinazione deve passare al 100%.
    return 0 if ood_rejected == len(ood) else 1


if __name__ == "__main__":
    raise SystemExit(main())

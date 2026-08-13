"""Embedder e5-small via ONNX Runtime (nessun torch).

Usa ESATTAMENTE lo stesso modello ONNX INT8 e lo stesso tokenizer che l'app
carica on-device: così gli embedding calcolati in ingest sono identici a quelli
calcolati dalla query a runtime, condizione necessaria per un retrieval denso
corretto.
"""
from __future__ import annotations

import os
from typing import List

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

# multilingual-e5-small: 384 dimensioni, lunghezza massima 512 token.
EMBED_DIM = 384
MAX_TOKENS = 512


class E5Embedder:
    def __init__(self, model_dir: str):
        model_path = os.path.join(model_dir, "model_int8.onnx")
        tok_path = os.path.join(model_dir, "tokenizer.json")
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Modello ONNX assente: {model_path}")
        self.session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
        self.input_names = {i.name for i in self.session.get_inputs()}
        self.tokenizer = Tokenizer.from_file(tok_path)
        self.tokenizer.enable_truncation(max_length=MAX_TOKENS)

    def token_count(self, text: str) -> int:
        """Numero di token WordPiece/SentencePiece del testo (senza troncamento)."""
        # Disabilita momentaneamente il troncamento per contare la lunghezza reale.
        return len(self.tokenizer.encode(text, add_special_tokens=True).ids)

    def embed(self, text: str) -> np.ndarray:
        return self.embed_batch([text])[0]

    def embed_batch(self, texts: List[str], batch_size: int = 16) -> np.ndarray:
        out = np.zeros((len(texts), EMBED_DIM), dtype=np.float32)
        for start in range(0, len(texts), batch_size):
            chunk = texts[start : start + batch_size]
            out[start : start + len(chunk)] = self._run(chunk)
        return out

    def _run(self, texts: List[str]) -> np.ndarray:
        encs = self.tokenizer.encode_batch(texts)
        max_len = max(len(e.ids) for e in encs)
        ids = np.zeros((len(texts), max_len), dtype=np.int64)
        mask = np.zeros((len(texts), max_len), dtype=np.int64)
        for i, e in enumerate(encs):
            ids[i, : len(e.ids)] = e.ids
            mask[i, : len(e.attention_mask)] = e.attention_mask

        feeds = {"input_ids": ids, "attention_mask": mask}
        if "token_type_ids" in self.input_names:
            feeds["token_type_ids"] = np.zeros_like(ids)

        last_hidden = self.session.run(None, feeds)[0]  # (B, T, H)
        # Mean pooling mascherato + normalizzazione L2.
        m = mask[:, :, None].astype(np.float32)
        summed = (last_hidden * m).sum(axis=1)
        counts = np.clip(m.sum(axis=1), 1e-9, None)
        pooled = summed / counts
        norms = np.linalg.norm(pooled, axis=1, keepdims=True)
        return (pooled / np.clip(norms, 1e-12, None)).astype(np.float32)

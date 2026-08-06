#!/usr/bin/env python3
"""Test unitari del chunking/pulizia (eseguibili con `python test_chunking.py` o pytest).

Non caricano ONNX: usano uno stub del contatore di token (conteggio parole)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_corpus import (  # noqa: E402
    Block, Chunk, Document, Section, TARGET_TOKENS,
    _chunk_section, classify, clean_text, parse_front_matter, split_blocks_from_markdown,
)


class StubEmbedder:
    """token_count = numero di parole (sufficiente per pilotare la finestra)."""
    def token_count(self, text: str) -> int:
        return len(text.split())


def test_front_matter():
    meta, body = parse_front_matter("---\ntitle: X\ncategory: acqua\n---\nCorpo.")
    assert meta["title"] == "X"
    assert meta["category"] == "acqua"
    assert body.strip() == "Corpo."


def test_dehyphenation_and_whitespace():
    assert clean_text("acqua-\npotabile") == "acquapotabile"
    assert clean_text("troppi    spazi\n\tqui") == "troppi spazi qui"


def test_markdown_sections_and_list_block():
    md = "# Titolo\nIntro.\n\n## Passi\n1. Uno\n2. Due\n3. Tre\n"
    sections = split_blocks_from_markdown(md)
    titles = [s.title for s in sections]
    assert "Titolo" in titles and "Passi" in titles
    passi = next(s for s in sections if s.title == "Passi")
    # La lista numerata è UN unico blocco marcato is_list.
    assert len(passi.blocks) == 1
    assert passi.blocks[0].is_list


def test_chunk_never_splits_numbered_list():
    # Una lista lunghissima (oltre TARGET_TOKENS) deve restare in un solo chunk.
    long_list = " ".join(f"{i}. passo numero {i} con parole extra parole" for i in range(1, 200))
    section = Section(title="Procedura", blocks=[Block(text=long_list, page=None, is_list=True)])
    chunks = _chunk_section(section, StubEmbedder())
    assert len(chunks) == 1, "una lista numerata non deve mai essere spezzata"


def test_chunk_windows_and_overlap():
    # Tre blocchi da ~300 parole: devono generare più chunk (target 450) con overlap.
    blocks = [Block(text=("parola " * 300).strip(), page=None) for _ in range(3)]
    section = Section(title="S", blocks=blocks)
    chunks = _chunk_section(section, StubEmbedder())
    assert len(chunks) >= 2


def test_classify_override_and_declared():
    doc = Document(doc_id="d1", title="t", author=None, year=None, license="CC0",
                   lang="it", category="acqua", source_path="d1.md")
    chunk = Chunk(chunk_id="d1::0001", doc=doc, section=None, page_start=None,
                  page_end=None, text="testo qualsiasi")
    # categoria dichiarata dal documento vince sulla classificazione a keyword
    assert classify(chunk, {"fuoco": ["testo"]}, {}) == "acqua"
    # override manuale vince su tutto
    assert classify(chunk, {}, {"d1": "medico"}) == "medico"


def _run_all():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print(f"  ok {t.__name__}")
    print(f"{len(tests)} test superati.")


if __name__ == "__main__":
    _run_all()

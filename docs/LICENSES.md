# Licenze e attribuzioni

## Corpus incluso nell'app (`app/src/main/assets/corpus/survival_corpus.db`)

| doc_id | Titolo | Autore | Licenza | Note |
|---|---|---|---|---|
| seed-acqua-01 | Potabilizzazione dell'acqua in emergenza | SurvivalWiki AI | CC0-1.0 | Opera propria, dedicata al pubblico dominio |
| seed-fuoco-01 | Accensione e gestione del fuoco | SurvivalWiki AI | CC0-1.0 | Opera propria |
| seed-medico-01 | Controllo delle emorragie esterne | SurvivalWiki AI | CC0-1.0 | Opera propria |
| seed-nav-01 | Orientarsi senza bussola | SurvivalWiki AI | CC0-1.0 | Opera propria |
| seed-rifugio-01 | Costruire un riparo di emergenza | SurvivalWiki AI | CC0-1.0 | Opera propria |
| seed-segn-01 | Segnali di soccorso | SurvivalWiki AI | CC0-1.0 | Opera propria |

Il corpus seed è **materiale sintetico originale** scritto per il progetto e dedicato al
pubblico dominio (CC0). Serve a far funzionare e testare l'intera pipeline end-to-end. Non è
un sostituto di manuali autorevoli: va ampliato con fonti reali (vedi sotto).

## Fonti reali candidate (da aggiungere con licenza verificata)

Vedi `tools/ingest/sources.yaml`. Regola inderogabile: **nessun documento con licenza non
verificabile viene incluso nel build di default** (`license: UNKNOWN` → escluso).

- **US Army FM 21-76 Survival** — opera del governo federale USA, **pubblico dominio negli USA**.
- **Wikibooks — Wilderness Survival** — CC BY-SA 3.0: se incluso, richiede attribuzione qui e
  ridistribuzione con la stessa licenza.
- Materiali WHO/ICRC di primo soccorso — licenza da verificare documento per documento.

## Modelli (scaricati a runtime, non ridistribuiti nel repo)

| Artefatto | Fonte | Licenza |
|---|---|---|
| multilingual-e5-small (ONNX INT8) | intfloat / Xenova | MIT |
| Tokenizer e5 | intfloat / Xenova | MIT |
| Qwen2.5-0.5B-Instruct (GGUF) | Qwen | Apache-2.0 (verificare il file della release) |

## Librerie native
- **llama.cpp** — MIT.
- **ONNX Runtime** — MIT.

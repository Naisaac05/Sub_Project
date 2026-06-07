# 코퍼스 규모/구성 실험 — 30장 DENSE vs DIVERSE

> 자동 생성. `python evals/retrieval_poc/corpus_scaling.py`

질문: 카드를 30장으로 늘리면 hybrid가 살아나는가? → 코퍼스 '구성'이 가른다.

## DENSE (근접개념 집중) — 카드 30장, 질의 120개

| 리트리버 | recall@1 | recall@3 | MRR | exact | mixed | semantic | typo |
|---|---:|---:|---:|---:|---:|---:|---:|
| bm25 | 72% | 80% | 0.783 | 100% | 100% | 50% | 40% |
| bge | 92% | 99% | 0.952 | 100% | 100% | 77% | 90% |
| hybrid | 76% | 93% | 0.845 | 100% | 100% | 57% | 47% |

→ hybrid recall@1 − bge recall@1 = -15.8% (hybrid가 bge보다 낮음 → 하이브리드가 깎아먹음)

## DIVERSE (다양 분포) — 카드 30장, 질의 120개

| 리트리버 | recall@1 | recall@3 | MRR | exact | mixed | semantic | typo |
|---|---:|---:|---:|---:|---:|---:|---:|
| bm25 | 78% | 82% | 0.813 | 100% | 77% | 70% | 63% |
| bge | 97% | 100% | 0.983 | 100% | 100% | 90% | 97% |
| hybrid | 82% | 90% | 0.873 | 100% | 77% | 77% | 77% |

→ hybrid recall@1 − bge recall@1 = -14.2% (hybrid가 bge보다 낮음 → 하이브리드가 깎아먹음)

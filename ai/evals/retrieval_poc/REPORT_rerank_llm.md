# LLM-리랭커 실측 — bge top-3 재정렬 (모델 qwen2.5:3b)

> 자동 생성. `python evals/retrieval_poc/rerank_llm.py`

리랭킹 천장 = recall@3(후보 안에 정답이 있어야 올림). rescued=bge 틀린 걸 살림, demoted=bge 맞은 걸 망침.

| 코퍼스 | bge recall@1 | **rerank recall@1** | Δ | 천장(recall@3) | rescued | demoted |
|---|---:|---:|---:|---:|---:|---:|
| DENSE (근접개념) | 91.7% | 95.8% | +4.2% | 99.2% | 6 | 1 |
| DIVERSE (다양) | 96.7% | 99.2% | +2.5% | 100.0% | 3 | 0 |

- **DENSE (근접개념)**: 리랭킹이 bge보다 높음 → 도움됨! (rescued 6 − demoted 1 = 순이득 5)
- **DIVERSE (다양)**: 리랭킹이 bge보다 높음 → 도움됨! (rescued 3 − demoted 0 = 순이득 3)

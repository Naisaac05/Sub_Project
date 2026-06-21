---
type: eval
category: evaluation
status: active
updated: 2026-06-18
description: "hybrid 변형 비교 — bge를 안 깎는 hybrid가 있는가? 모델 성능 평가 및 POC 실험 결과 분석"

---

# hybrid 변형 비교 — bge를 안 깎는 hybrid가 있는가?

> 자동 생성. `python evals/retrieval_poc/hybrid_variants.py`

recall@1 기준. oracle = 질의마다 bge·bm25 중 하나라도 top1 맞히면 정답(모든 fusion의 천장).

## DENSE (근접개념) — 질의 120개

| 변형 | recall@1 | bge 대비 |
|---|---:|---:|
| bge | 91.7% |  |
| bm25 | 72.5% | -19.2% |
| rrf 1:1 | 75.8% | -15.8% |
| rrf 1:2 | 83.3% | -8.3% |
| rrf 1:3 | 85.8% | -5.8% |
| rrf 1:5 | 90.0% | -1.7% |
| cascade τ=0.03 | 89.2% | -2.5% |
| cascade τ=0.05 | 85.8% | -5.8% |
| cascade τ=0.08 | 83.3% | -8.3% |

**오라클 분해 (recall@1):**

- 둘 다 맞음: 86 / bge만 맞음: 24 / **bm25만 맞음(= hybrid가 살릴 여지): 1** / 둘 다 틀림: 9
- **oracle 천장 = 92.5%** (bge 단독 91.7%)
- → bm25-only 정답 1개 존재. 완벽 fusion이면 +0.8%까지 가능. 살릴 질의: ['빈 생성 규칙을 코드로 모아두는 설정 클래스 표시']

## DIVERSE (다양) — 질의 120개

| 변형 | recall@1 | bge 대비 |
|---|---:|---:|
| bge | 96.7% |  |
| bm25 | 77.5% | -19.2% |
| rrf 1:1 | 82.5% | -14.2% |
| rrf 1:2 | 85.8% | -10.8% |
| rrf 1:3 | 89.2% | -7.5% |
| rrf 1:5 | 91.7% | -5.0% |
| cascade τ=0.03 | 95.8% | -0.8% |
| cascade τ=0.05 | 95.0% | -1.7% |
| cascade τ=0.08 | 95.0% | -1.7% |

**오라클 분해 (recall@1):**

- 둘 다 맞음: 92 / bge만 맞음: 24 / **bm25만 맞음(= hybrid가 살릴 여지): 1** / 둘 다 틀림: 3
- **oracle 천장 = 97.5%** (bge 단독 96.7%)
- → bm25-only 정답 1개 존재. 완벽 fusion이면 +0.8%까지 가능. 살릴 질의: ['앱을 통째로 싸서 어디서나 똑같이 실행하는 방식']

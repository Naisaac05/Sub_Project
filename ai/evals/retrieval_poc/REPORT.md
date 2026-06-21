---
type: eval
category: evaluation
status: active
updated: 2026-06-18
description: "검색 비교 PoC 리포트 — bm25 / bge / hybrid 모델 성능 평가 및 POC 실험 결과 분석"

---

# 검색 비교 PoC 리포트 — bm25 / bge / hybrid

> 자동 생성. `python evals/retrieval_poc/evaluate.py` 로 재생성.

- 코퍼스: 지식카드 11장
- 평가 행: golden_dataset.jsonl 43행 (전체 71행 중 expected_concepts 가 카드에 존재하는 행만; 나머지는 검색 무관)
- 정답: 질문의 `expected_concepts`. recall@k = 정답 개념 중 하나라도 상위 k 안에 있으면 hit.

## 1. 결과

| 리트리버 | recall@1 | recall@3 | MRR |
|---|---:|---:|---:|
| bm25 (lexical, 단어) | 65.1% | 100.0% | 0.806 |
| bge (임베딩, 의미) | 95.3% | 100.0% | 0.973 |
| hybrid (RRF 융합) | 65.1% | 100.0% | 0.822 |

## 2. recall@3 에서 놓친 질문 (리트리버별)

### bm25 (lexical, 단어) — 0건 놓침
- (없음)

### bge (임베딩, 의미) — 0건 놓침
- (없음)

### hybrid (RRF 융합) — 0건 놓침
- (없음)

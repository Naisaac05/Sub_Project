---
type: eval
category: evaluation
status: active
updated: 2026-06-18
---

# 검색 비교 PoC (retrieval)

RAG 검색 단계에서 **bm25(단어) vs bge(의미) vs hybrid(RRF 융합)**를 같은 정답지로 비교한다.
정답지는 기존 [`../golden_dataset.jsonl`](../golden_dataset.jsonl)의 `expected_concepts`("이 질문엔 어느 카드가 나와야 하나").

## 경계 규칙 (배포 안전)

- dev 전용. 앱(`app/`)은 이 폴더를 import 하지 않는다(단방향 PoC → app).
- 앱 결합은 `retrievers.py` 한 곳에만(카드 로더 + BM25 어댑터). bge는 외부 API/torch 없이 **로컬 Ollama bge-m3**.

## 구성

| 파일 | 역할 |
|---|---|
| `retrievers.py` | bm25 / bge / hybrid 3종 (앱 결합 단일점) |
| `hybrid_queries.jsonl` | 목적형 질의 40개 (개념 10 × 4유형: exact_token/semantic/typo/mixed) |
| `evaluate.py` | recall@1·recall@3·MRR + 질의 유형별 분해 → `REPORT*.md` |
| `REPORT.md` / `REPORT_hybrid.md` | 결과(자동 생성) |

## 목적형 데이터 설계 원칙

하이브리드의 가치는 **lexical과 semantic이 엇갈릴 때만** 드러난다. 그래서 `hybrid_queries.jsonl`은
질의를 4유형으로 축을 펼친다: `exact_token`(정확 용어) / `semantic`(우회 표현) / `typo`(오타) / `mixed`(쉬움).

## 실행

```bash
# ai/ 에서 (Ollama 실행 + bge-m3 pull 필요)
python evals/retrieval_poc/evaluate.py
```

## 측정 정의

- 평가 대상: golden 71행 중 `expected_concepts`가 코퍼스 카드(11장)에 존재하는 **43행**(follow-up/off-topic 제외).
- recall@k = 정답 개념 중 하나라도 상위 k 안에 있으면 hit. MRR = 첫 정답 개념 순위의 역수.

## 핵심 결과 (요약)

| 리트리버 | recall@1 | recall@3 | MRR |
|---|---:|---:|---:|
| bm25 (단어) | 65.1% | 100% | 0.806 |
| **bge (의미)** | **95.3%** | 100% | **0.973** |
| hybrid (RRF) | 65.1% | 100% | 0.822 |

→ **이 11장 코퍼스에선 bge 단독이 최고.** 동일가중 hybrid는 강한 bge를 약한 bm25 수준으로 끌어내렸다
(= "하이브리드가 항상 낫다"는 틀림). recall@3가 다 100%인 건 코퍼스가 작아서. 자세한 해석은 윗단 설명 참고.

## 목적형 질의(`hybrid_queries.jsonl`) 결과 — 질의 유형별 recall@1

| query_type | bm25 | bge | hybrid |
|---|---:|---:|---:|
| exact_token | 100% | 100% | 100% |
| mixed | 80% | 100% | 80% |
| semantic | 70% | 100% | 80% |
| **typo** | **10%** | **90%** | 80% |
| **전체** | 65% | **97.5%** | 85% |

→ **bge-m3가 모든 유형에서 최고**(정확용어까지 잘함) → BM25가 이기는 유형이 없어 hybrid는 깎아먹기만 함.
핵심: **한글 음역 오타("패치조인","엔플러스원")는 BM25 토큰 매칭을 0으로 만들고(typo 10%), 임베딩만 잡는다.**
결론: 이 도메인은 **bge 단독**이 정답. (재현: `evaluate.py --dataset hybrid_queries.jsonl`)

## 코퍼스 30장 실험 (`corpus_scaling.py`) — "30장이면 hybrid가 살아나나?"

생성 코퍼스 2종(각 30장): DENSE(근접개념 덩어리=hybrid 최선의 조건) vs DIVERSE(무관 주제). recall@1:

| 코퍼스 | bm25 | bge | hybrid | hybrid−bge |
|---|---:|---:|---:|---:|
| DENSE | 72% | **92%** | 76% | **−16%p** |
| DIVERSE | 78% | **97%** | 82% | **−14%p** |

→ **가설(DENSE에선 hybrid가 이긴다) 틀림. 30장에서도 bge 단독이 최고, hybrid는 둘 다 깎아먹음.**
이유: exact_token은 bge도 100%라 BM25가 이기는 구간이 여전히 없음. DENSE가 bge의 semantic을 90→77%로 흔들긴 했지만 BM25는 거기서 더 약해 도움 안 됨. **3번의 실험 모두 bge 단독 결론.**

## hybrid 변형 + 오라클 (`hybrid_variants.py`) — "bge를 살리는 hybrid가 있나?"

equal-weight RRF 말고 **bge-가중 RRF / 캐스케이드**도 다 측정. 그리고 **오라클**(질의마다 bge·bm25 중 하나라도 맞히면 정답 = 모든 fusion의 천장):

| | bge | 최선 변형 | **oracle 천장** | bm25-only 정답 |
|---|---:|---:|---:|---:|
| DENSE | 91.7% | rrf 1:5 = 90.0% | **92.5%** | **1/120** |
| DIVERSE | 96.7% | cascade τ=0.03 = 95.8% | **97.5%** | **1/120** |

→ **어떤 변형도 bge를 못 넘음.** bge에 가중치를 줄수록 bge에 *수렴*할 뿐 초과 못 함. **oracle 천장도 bge+0.8%p**(bm25가 bge를 구제하는 질의가 120개 중 단 1개). 즉 **이 도메인엔 bge를 능가하는 hybrid가 존재하지 않음. 최적 hybrid = 그냥 bge.** (재현: `hybrid_variants.py`, 임베딩 디스크 캐시로 즉시)

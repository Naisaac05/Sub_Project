---
type: eval
category: evaluation
status: active
updated: 2026-06-18
description: "의도분류 PoC 리포트 — classifier=embed 모델 성능 평가 및 POC 실험 결과 분석"

---

# 의도분류 PoC 리포트 — classifier=`embed`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `intent_golden.jsonl` (80행, 의도당 8개, dev/holdout 50:50)
- 측정 대상: 임베딩 최근접중심 — bge-m3(Ollama) dev 예시 centroid, 코사인 분류 (예시만 추가)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 92.5%** (74/80)
- macro-F1: 0.925

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 8 | 8 | 0.89 | 1.00 | 0.94 |
| WRONG_ANSWER_REASON | 8 | 7 | 1.00 | 0.88 | 0.93 |
| CONCEPT_DEFINITION | 8 | 6 | 0.86 | 0.75 | 0.80 |
| COMPARISON | 8 | 7 | 1.00 | 0.88 | 0.93 |
| EXAMPLE_REQUEST | 8 | 8 | 0.89 | 1.00 | 0.94 |
| PRACTICAL_USAGE | 8 | 7 | 0.88 | 0.88 | 0.88 |
| DEBUG_OR_ERROR | 8 | 8 | 1.00 | 1.00 | 1.00 |
| FOLLOW_UP | 8 | 8 | 0.80 | 1.00 | 0.89 |
| OFF_TOPIC | 8 | 8 | 1.00 | 1.00 | 1.00 |
| UNKNOWN | 8 | 7 | 1.00 | 0.88 | 0.93 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 8 | . | . | . | . | . | . | . | . | . |
| **WAR** | 1 | 7 | . | . | . | . | . | . | . | . |
| **DEF** | . | . | 6 | . | . | 1 | . | 1 | . | . |
| **CMP** | . | . | . | 7 | 1 | . | . | . | . | . |
| **EX** | . | . | . | . | 8 | . | . | . | . | . |
| **PRAC** | . | . | 1 | . | . | 7 | . | . | . | . |
| **DBG** | . | . | . | . | . | . | 8 | . | . | . |
| **FU** | . | . | . | . | . | . | . | 8 | . | . |
| **OFF** | . | . | . | . | . | . | . | . | 8 | . |
| **UNK** | . | . | . | . | . | . | . | 1 | . | 7 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `WRONG_ANSWER_REASON` → `ANSWER_REASON` : 1건
  - `CONCEPT_DEFINITION` → `PRACTICAL_USAGE` : 1건
  - `CONCEPT_DEFINITION` → `FOLLOW_UP` : 1건
  - `COMPARISON` → `EXAMPLE_REQUEST` : 1건
  - `PRACTICAL_USAGE` → `CONCEPT_DEFINITION` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| WRONG_ANSWER_REASON | ANSWER_REASON | intent_golden | 이 답안이 오답 처리되는 근거가 뭐야? |
| CONCEPT_DEFINITION | PRACTICAL_USAGE | intent_golden | 레이트 리미터는 왜 쓰는 거야? |
| CONCEPT_DEFINITION | FOLLOW_UP | intent_golden | 그래프 데이터베이스를 쉽게 설명해줘 |
| COMPARISON | EXAMPLE_REQUEST | intent_golden | 캐시와 버퍼를 비교해줘 |
| PRACTICAL_USAGE | CONCEPT_DEFINITION | intent_golden | 커넥션 풀 크기는 현장에서 어떻게 정해? |
| UNKNOWN | FOLLOW_UP | intent_golden | .... ???? .... |

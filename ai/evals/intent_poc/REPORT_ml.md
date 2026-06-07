# 의도분류 PoC 리포트 — classifier=`ml`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: 학습형 — TF-IDF 문자 n-gram + 로지스틱 회귀 (dev 120 학습, 규칙 미작성)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 97.9%** (235/240)
- dev 정확도: 100.0%  /  holdout 정확도: 95.8%
- macro-F1: 0.979

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| WRONG_ANSWER_REASON | 24 | 23 | 0.96 | 0.96 | 0.96 |
| CONCEPT_DEFINITION | 24 | 24 | 1.00 | 1.00 | 1.00 |
| COMPARISON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 24 | 24 | 1.00 | 1.00 | 1.00 |
| PRACTICAL_USAGE | 24 | 24 | 1.00 | 1.00 | 1.00 |
| DEBUG_OR_ERROR | 24 | 23 | 0.96 | 0.96 | 0.96 |
| FOLLOW_UP | 24 | 23 | 0.88 | 0.96 | 0.92 |
| OFF_TOPIC | 24 | 24 | 1.00 | 1.00 | 1.00 |
| UNKNOWN | 24 | 22 | 1.00 | 0.92 | 0.96 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 100.0% | 30/30 |
| typo | 100.0% | 80/80 |
| filler | 100.0% | 30/30 |
| paraphrase | 95.0% | 95/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 24 | . | . | . | . | . | . | . | . | . |
| **WAR** | . | 23 | . | . | . | . | . | 1 | . | . |
| **DEF** | . | . | 24 | . | . | . | . | . | . | . |
| **CMP** | . | . | . | 24 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 24 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 24 | . | . | . | . |
| **DBG** | . | 1 | . | . | . | . | 23 | . | . | . |
| **FU** | . | . | . | . | . | . | 1 | 23 | . | . |
| **OFF** | . | . | . | . | . | . | . | . | 24 | . |
| **UNK** | . | . | . | . | . | . | . | 2 | . | 22 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `UNKNOWN` → `FOLLOW_UP` : 2건
  - `WRONG_ANSWER_REASON` → `FOLLOW_UP` : 1건
  - `DEBUG_OR_ERROR` → `WRONG_ANSWER_REASON` : 1건
  - `FOLLOW_UP` → `DEBUG_OR_ERROR` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| WRONG_ANSWER_REASON | FOLLOW_UP | paraphrase | 이 문제 제가 왜 틀린 거예요? |
| DEBUG_OR_ERROR | WRONG_ANSWER_REASON | paraphrase | 왜 계속 안 되는 거지? |
| FOLLOW_UP | DEBUG_OR_ERROR | paraphrase | 왜 그래요? |
| UNKNOWN | FOLLOW_UP | paraphrase | ㅋㅋㅋㅋ |
| UNKNOWN | FOLLOW_UP | paraphrase | ........ |

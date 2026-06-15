# 의도분류 PoC 리포트 — classifier=`embed`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (120행, 의도당 12개, dev/holdout 50:50)
- 측정 대상: 임베딩 최근접중심 — bge-m3(Ollama) dev 예시 centroid, 코사인 분류 (예시만 추가)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 100.0%** (120/120)
- split filter: `dev`
- macro-F1: 1.000

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 12 | 12 | 1.00 | 1.00 | 1.00 |
| WRONG_ANSWER_REASON | 12 | 12 | 1.00 | 1.00 | 1.00 |
| CONCEPT_DEFINITION | 12 | 12 | 1.00 | 1.00 | 1.00 |
| COMPARISON | 12 | 12 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 12 | 12 | 1.00 | 1.00 | 1.00 |
| PRACTICAL_USAGE | 12 | 12 | 1.00 | 1.00 | 1.00 |
| DEBUG_OR_ERROR | 12 | 12 | 1.00 | 1.00 | 1.00 |
| FOLLOW_UP | 12 | 12 | 1.00 | 1.00 | 1.00 |
| OFF_TOPIC | 12 | 12 | 1.00 | 1.00 | 1.00 |
| UNKNOWN | 12 | 12 | 1.00 | 1.00 | 1.00 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 100.0% | 30/30 |
| typo | 100.0% | 29/29 |
| filler | 100.0% | 22/22 |
| paraphrase | 100.0% | 39/39 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 12 | . | . | . | . | . | . | . | . | . |
| **WAR** | . | 12 | . | . | . | . | . | . | . | . |
| **DEF** | . | . | 12 | . | . | . | . | . | . | . |
| **CMP** | . | . | . | 12 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 12 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 12 | . | . | . | . |
| **DBG** | . | . | . | . | . | . | 12 | . | . | . |
| **FU** | . | . | . | . | . | . | . | 12 | . | . |
| **OFF** | . | . | . | . | . | . | . | . | 12 | . |
| **UNK** | . | . | . | . | . | . | . | . | . | 12 |

## 5. 자동 진단 — 어디서 무너지는가


## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|

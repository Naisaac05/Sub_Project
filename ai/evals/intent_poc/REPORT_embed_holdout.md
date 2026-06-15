# 의도분류 PoC 리포트 — classifier=`embed`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (192행, 의도당 19개, dev/holdout 50:50)
- 측정 대상: 임베딩 최근접중심 — bge-m3(Ollama) dev 예시 centroid, 코사인 분류 (예시만 추가)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 98.4%** (189/192)
- split filter: `holdout`
- macro-F1: 0.987

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 18 | 18 | 0.95 | 1.00 | 0.97 |
| WRONG_ANSWER_REASON | 18 | 18 | 1.00 | 1.00 | 1.00 |
| CONCEPT_DEFINITION | 30 | 28 | 0.97 | 0.93 | 0.95 |
| COMPARISON | 18 | 18 | 0.95 | 1.00 | 0.97 |
| EXAMPLE_REQUEST | 18 | 18 | 1.00 | 1.00 | 1.00 |
| PRACTICAL_USAGE | 18 | 18 | 1.00 | 1.00 | 1.00 |
| DEBUG_OR_ERROR | 18 | 18 | 1.00 | 1.00 | 1.00 |
| FOLLOW_UP | 18 | 18 | 1.00 | 1.00 | 1.00 |
| OFF_TOPIC | 18 | 18 | 1.00 | 1.00 | 1.00 |
| UNKNOWN | 18 | 17 | 1.00 | 0.94 | 0.97 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| typo | 100.0% | 52/52 |
| filler | 100.0% | 7/7 |
| paraphrase | 100.0% | 61/61 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 18 | . | . | . | . | . | . | . | . | . |
| **WAR** | . | 18 | . | . | . | . | . | . | . | . |
| **DEF** | 1 | . | 28 | 1 | . | . | . | . | . | . |
| **CMP** | . | . | . | 18 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 18 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 18 | . | . | . | . |
| **DBG** | . | . | . | . | . | . | 18 | . | . | . |
| **FU** | . | . | . | . | . | . | . | 18 | . | . |
| **OFF** | . | . | . | . | . | . | . | . | 18 | . |
| **UNK** | . | . | 1 | . | . | . | . | . | . | 17 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `CONCEPT_DEFINITION` → `ANSWER_REASON` : 1건
  - `CONCEPT_DEFINITION` → `COMPARISON` : 1건
  - `UNKNOWN` → `CONCEPT_DEFINITION` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| CONCEPT_DEFINITION | ANSWER_REASON | pattern | 동시성 제어은 왜 생겨? |
| CONCEPT_DEFINITION | COMPARISON | pattern | 스레드이랑 프로세스는 무슨 관계야? |
| UNKNOWN | CONCEPT_DEFINITION | pattern | 인덱스 관련해서 뭔가 좀 알려줘 |

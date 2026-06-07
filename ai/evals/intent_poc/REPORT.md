# 의도분류 PoC 리포트 — classifier=`current`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: 현재 `ai/app/workflow/intent.py` 의 `classify_free_question` (4-intent 규칙기반)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 37.5%** (90/240)
- dev 정확도: 39.2%  /  holdout 정확도: 35.8%
- macro-F1: 0.312

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 0 | 0.00 | 0.00 | 0.00 |
| WRONG_ANSWER_REASON | 24 | 22 | 0.44 | 0.92 | 0.59 |
| CONCEPT_DEFINITION | 24 | 24 | 0.18 | 1.00 | 0.30 |
| COMPARISON | 24 | 18 | 1.00 | 0.75 | 0.86 |
| EXAMPLE_REQUEST | 24 | 0 | 0.00 | 0.00 | 0.00 |
| PRACTICAL_USAGE | 24 | 16 | 1.00 | 0.67 | 0.80 |
| DEBUG_OR_ERROR | 24 | 0 | 0.00 | 0.00 | 0.00 |
| FOLLOW_UP | 24 | 3 | 0.75 | 0.12 | 0.21 |
| OFF_TOPIC | 24 | 0 | 0.00 | 0.00 | 0.00 |
| UNKNOWN | 24 | 7 | 0.44 | 0.29 | 0.35 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 40.0% | 12/30 |
| typo | 36.2% | 29/80 |
| filler | 40.0% | 12/30 |
| paraphrase | 37.0% | 37/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | . | 20 | 4 | . | . | . | . | . | . | . |
| **WAR** | . | 22 | 2 | . | . | . | . | . | . | . |
| **DEF** | . | . | 24 | . | . | . | . | . | . | . |
| **CMP** | . | . | 6 | 18 | . | . | . | . | . | . |
| **EX** | . | . | 24 | . | . | . | . | . | . | . |
| **PRAC** | . | . | 8 | . | . | 16 | . | . | . | . |
| **DBG** | . | 1 | 22 | . | . | . | . | . | . | 1 |
| **FU** | . | 7 | 7 | . | . | . | . | 3 | . | 7 |
| **OFF** | . | . | 23 | . | . | . | . | . | . | 1 |
| **UNK** | . | . | 16 | . | . | . | . | 1 | . | 7 |

## 5. 자동 진단 — 어디서 무너지는가

- **재현율 0% 클래스: ANSWER_REASON, EXAMPLE_REQUEST, DEBUG_OR_ERROR, OFF_TOPIC**
- 가장 흔한 오분류 방향 Top 5:
  - `EXAMPLE_REQUEST` → `CONCEPT_DEFINITION` : 24건
  - `OFF_TOPIC` → `CONCEPT_DEFINITION` : 23건
  - `DEBUG_OR_ERROR` → `CONCEPT_DEFINITION` : 22건
  - `ANSWER_REASON` → `WRONG_ANSWER_REASON` : 20건
  - `UNKNOWN` → `CONCEPT_DEFINITION` : 16건

## 부록 A. 사용한 매핑 (현재 출력 → 10-class)

| 현재 (intent, sub_intent) | 10-class |
|---|---|
| (concept_definition, comparison) | COMPARISON |
| (concept_definition, practical) | PRACTICAL_USAGE |
| (concept_definition, definition) | CONCEPT_DEFINITION |
| (concept_definition, related) | CONCEPT_DEFINITION |
| (wrong_answer_explanation, explanation) | WRONG_ANSWER_REASON |
| (follow_up, follow_up) | FOLLOW_UP |
| 그 외 intent 기본값 | concept_definition→DEF, wrong_answer_explanation→WAR, follow_up→FU, general_question→UNK |

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| ANSWER_REASON | WRONG_ANSWER_REASON | seed | 이 문제 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이 무제 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이 문재 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이문제 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | filler | 혹시 이 문제 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 이거 정답 왜 3번임? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 3번이 답이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 3번이 정답인 이유 뭐야 |
| ANSWER_REASON | WRONG_ANSWER_REASON | seed | 정답이 B인 이유가 뭐예요? |
| ANSWER_REASON | CONCEPT_DEFINITION | typo | 저답이 B인 이유가 뭐예요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 정답이 B인 이유가 뭐얘요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 정답이B인 이유가 뭐예요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | filler | 혹시 정답이 B인 이유가 뭐예요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 정답 B인 이유가 뭐임? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 B가 정답이에요? |
| ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | B가 답인 까닭이 뭐죠? |
| ANSWER_REASON | WRONG_ANSWER_REASON | seed | 이게 맞는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이게 마는 근거가 뭐지? |
| ANSWER_REASON | CONCEPT_DEFINITION | typo | 이개 맞는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이게맞는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | filler | 혹시 이게 맞는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 이게 왜 맞는 거임? |
| ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | 이거 맞는 이유가 뭐야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 이게 정답인 근거 뭐예요? |
| WRONG_ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | 제가 쓴 답이 틀린 이유가 뭐죠? |

# 의도분류 PoC 리포트 — classifier=`llm`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: 로컬 Ollama LLM 분류기 (기본 qwen2.5:3b, format=json, temperature=0)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 92.9%** (223/240)
- dev 정확도: 92.5%  /  holdout 정확도: 93.3%
- macro-F1: 0.928

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 17 | 1.00 | 0.71 | 0.83 |
| WRONG_ANSWER_REASON | 24 | 22 | 0.76 | 0.92 | 0.83 |
| CONCEPT_DEFINITION | 24 | 24 | 1.00 | 1.00 | 1.00 |
| COMPARISON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 24 | 24 | 0.96 | 1.00 | 0.98 |
| PRACTICAL_USAGE | 24 | 24 | 0.92 | 1.00 | 0.96 |
| DEBUG_OR_ERROR | 24 | 24 | 0.96 | 1.00 | 0.98 |
| FOLLOW_UP | 24 | 23 | 0.92 | 0.96 | 0.94 |
| OFF_TOPIC | 24 | 21 | 0.88 | 0.88 | 0.88 |
| UNKNOWN | 24 | 20 | 0.95 | 0.83 | 0.89 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 96.7% | 29/30 |
| typo | 92.5% | 74/80 |
| filler | 93.3% | 28/30 |
| paraphrase | 92.0% | 92/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 17 | 7 | . | . | . | . | . | . | . | . |
| **WAR** | . | 22 | . | . | . | . | 1 | 1 | . | . |
| **DEF** | . | . | 24 | . | . | . | . | . | . | . |
| **CMP** | . | . | . | 24 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 24 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 24 | . | . | . | . |
| **DBG** | . | . | . | . | . | . | 24 | . | . | . |
| **FU** | . | . | . | . | . | . | . | 23 | 1 | . |
| **OFF** | . | . | . | . | . | 2 | . | . | 21 | 1 |
| **UNK** | . | . | . | . | 1 | . | . | 1 | 2 | 20 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `ANSWER_REASON` → `WRONG_ANSWER_REASON` : 7건
  - `OFF_TOPIC` → `PRACTICAL_USAGE` : 2건
  - `UNKNOWN` → `OFF_TOPIC` : 2건
  - `WRONG_ANSWER_REASON` → `DEBUG_OR_ERROR` : 1건
  - `WRONG_ANSWER_REASON` → `FOLLOW_UP` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 이거 정답 왜 3번임? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 3번이 답이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 저답이 B인 이유가 뭐예요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 B가 정답이에요? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이게 마는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이개 맞는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | filler | 혹시 이게 맞는 근거가 뭐지? |
| WRONG_ANSWER_REASON | DEBUG_OR_ERROR | typo | 이거 내가 왜 트린 거지? |
| WRONG_ANSWER_REASON | FOLLOW_UP | typo | 이거 네가 왜 틀린 거지? |
| FOLLOW_UP | OFF_TOPIC | typo | 외요? |
| OFF_TOPIC | PRACTICAL_USAGE | seed | 개발자 되면 연봉 많이 받아요? |
| OFF_TOPIC | PRACTICAL_USAGE | filler | 혹시 개발자 되면 연봉 많이 받아요? |
| OFF_TOPIC | UNKNOWN | paraphrase | 개발자 되면 돈 많이 벌어? |
| UNKNOWN | OFF_TOPIC | paraphrase | ㅋㅋㅋㅋ |
| UNKNOWN | FOLLOW_UP | paraphrase | 아 그거 뭐였지 |
| UNKNOWN | EXAMPLE_REQUEST | paraphrase | 그거 좀 해줘 |
| UNKNOWN | OFF_TOPIC | paraphrase | 자바 막 이것저것 |

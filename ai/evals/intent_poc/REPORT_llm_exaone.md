# 의도분류 PoC 리포트 — classifier=`llm`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: 로컬 Ollama LLM 분류기 (기본 qwen2.5:3b, format=json, temperature=0)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 91.2%** (219/240)
- dev 정확도: 93.3%  /  holdout 정확도: 89.2%
- macro-F1: 0.906

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 20 | 1.00 | 0.83 | 0.91 |
| WRONG_ANSWER_REASON | 24 | 24 | 0.89 | 1.00 | 0.94 |
| CONCEPT_DEFINITION | 24 | 24 | 0.92 | 1.00 | 0.96 |
| COMPARISON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 24 | 23 | 0.88 | 0.96 | 0.92 |
| PRACTICAL_USAGE | 24 | 24 | 0.86 | 1.00 | 0.92 |
| DEBUG_OR_ERROR | 24 | 24 | 1.00 | 1.00 | 1.00 |
| FOLLOW_UP | 24 | 22 | 0.92 | 0.92 | 0.92 |
| OFF_TOPIC | 24 | 12 | 0.92 | 0.50 | 0.65 |
| UNKNOWN | 24 | 22 | 0.79 | 0.92 | 0.85 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 96.7% | 29/30 |
| typo | 88.8% | 71/80 |
| filler | 96.7% | 29/30 |
| paraphrase | 90.0% | 90/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 20 | 3 | . | . | . | . | . | 1 | . | . |
| **WAR** | . | 24 | . | . | . | . | . | . | . | . |
| **DEF** | . | . | 24 | . | . | . | . | . | . | . |
| **CMP** | . | . | . | 24 | . | . | . | . | . | . |
| **EX** | . | . | 1 | . | 23 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 24 | . | . | . | . |
| **DBG** | . | . | . | . | . | . | 24 | . | . | . |
| **FU** | . | . | 1 | . | . | . | . | 22 | . | 1 |
| **OFF** | . | . | . | . | 3 | 4 | . | . | 12 | 5 |
| **UNK** | . | . | . | . | . | . | . | 1 | 1 | 22 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `OFF_TOPIC` → `UNKNOWN` : 5건
  - `OFF_TOPIC` → `PRACTICAL_USAGE` : 4건
  - `ANSWER_REASON` → `WRONG_ANSWER_REASON` : 3건
  - `OFF_TOPIC` → `EXAMPLE_REQUEST` : 3건
  - `ANSWER_REASON` → `FOLLOW_UP` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이 문재 정답이 왜 3번이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 이거 정답 왜 3번임? |
| ANSWER_REASON | FOLLOW_UP | paraphrase | 왜 3번이 답이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 저답이 B인 이유가 뭐예요? |
| EXAMPLE_REQUEST | CONCEPT_DEFINITION | typo | 이거 시제 케이스로 설명해줄 수 있어? |
| FOLLOW_UP | UNKNOWN | typo | 외요? |
| FOLLOW_UP | CONCEPT_DEFINITION | paraphrase | 그 말이 무슨 뜻이죠? |
| OFF_TOPIC | EXAMPLE_REQUEST | paraphrase | 점심 메뉴 추천 좀 |
| OFF_TOPIC | EXAMPLE_REQUEST | typo | 주말애 영화 뭐 볼까? |
| OFF_TOPIC | EXAMPLE_REQUEST | typo | 주말에영화 뭐 볼까? |
| OFF_TOPIC | UNKNOWN | paraphrase | 주말에 볼 영화 추천해줘 |
| OFF_TOPIC | PRACTICAL_USAGE | seed | 개발자 되면 연봉 많이 받아요? |
| OFF_TOPIC | UNKNOWN | typo | 개바자 되면 연봉 많이 받아요? |
| OFF_TOPIC | UNKNOWN | typo | 게발자 되면 연봉 많이 받아요? |
| OFF_TOPIC | PRACTICAL_USAGE | typo | 개발자되면 연봉 많이 받아요? |
| OFF_TOPIC | PRACTICAL_USAGE | filler | 혹시 개발자 되면 연봉 많이 받아요? |
| OFF_TOPIC | PRACTICAL_USAGE | paraphrase | 개발자 연봉 많이 받나요? |
| OFF_TOPIC | UNKNOWN | paraphrase | 개발자 되면 돈 많이 벌어? |
| OFF_TOPIC | UNKNOWN | paraphrase | 개발자 월급 어느 정도예요? |
| UNKNOWN | OFF_TOPIC | paraphrase | ㅋㅋㅋㅋ |
| UNKNOWN | FOLLOW_UP | paraphrase | 아 그거 뭐였지 |

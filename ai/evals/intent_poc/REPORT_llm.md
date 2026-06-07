# 의도분류 PoC 리포트 — classifier=`llm`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: 로컬 Ollama LLM 분류기 (기본 qwen2.5:3b, format=json, temperature=0)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 87.1%** (209/240)
- dev 정확도: 89.2%  /  holdout 정확도: 85.0%
- macro-F1: 0.869

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 15 | 1.00 | 0.62 | 0.77 |
| WRONG_ANSWER_REASON | 24 | 23 | 0.88 | 0.96 | 0.92 |
| CONCEPT_DEFINITION | 24 | 23 | 0.74 | 0.96 | 0.84 |
| COMPARISON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 24 | 24 | 0.96 | 1.00 | 0.98 |
| PRACTICAL_USAGE | 24 | 22 | 0.96 | 0.92 | 0.94 |
| DEBUG_OR_ERROR | 24 | 23 | 1.00 | 0.96 | 0.98 |
| FOLLOW_UP | 24 | 19 | 0.70 | 0.79 | 0.75 |
| OFF_TOPIC | 24 | 21 | 0.88 | 0.88 | 0.88 |
| UNKNOWN | 24 | 15 | 0.68 | 0.62 | 0.65 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 93.3% | 28/30 |
| typo | 85.0% | 68/80 |
| filler | 93.3% | 28/30 |
| paraphrase | 85.0% | 85/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 15 | 3 | 6 | . | . | . | . | . | . | . |
| **WAR** | . | 23 | . | . | . | . | . | 1 | . | . |
| **DEF** | . | . | 23 | . | . | . | . | . | . | 1 |
| **CMP** | . | . | . | 24 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 24 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 22 | . | . | . | 2 |
| **DBG** | . | . | . | . | . | 1 | 23 | . | . | . |
| **FU** | . | . | 2 | . | . | . | . | 19 | 2 | 1 |
| **OFF** | . | . | . | . | . | . | . | . | 21 | 3 |
| **UNK** | . | . | . | . | 1 | . | . | 7 | 1 | 15 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `UNKNOWN` → `FOLLOW_UP` : 7건
  - `ANSWER_REASON` → `CONCEPT_DEFINITION` : 6건
  - `ANSWER_REASON` → `WRONG_ANSWER_REASON` : 3건
  - `OFF_TOPIC` → `UNKNOWN` : 3건
  - `PRACTICAL_USAGE` → `UNKNOWN` : 2건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 3번이 답이야? |
| ANSWER_REASON | WRONG_ANSWER_REASON | paraphrase | 왜 B가 정답이에요? |
| ANSWER_REASON | CONCEPT_DEFINITION | seed | 이게 맞는 근거가 뭐지? |
| ANSWER_REASON | CONCEPT_DEFINITION | typo | 이게 마는 근거가 뭐지? |
| ANSWER_REASON | WRONG_ANSWER_REASON | typo | 이개 맞는 근거가 뭐지? |
| ANSWER_REASON | CONCEPT_DEFINITION | typo | 이게맞는 근거가 뭐지? |
| ANSWER_REASON | CONCEPT_DEFINITION | filler | 혹시 이게 맞는 근거가 뭐지? |
| ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | 이거 맞는 이유가 뭐야? |
| ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | 이게 정답인 근거 뭐예요? |
| WRONG_ANSWER_REASON | FOLLOW_UP | typo | 이거 내가 왜 트린 거지? |
| CONCEPT_DEFINITION | UNKNOWN | typo | REST API의 저의를 알려줘 |
| PRACTICAL_USAGE | UNKNOWN | typo | 이거 시제로 많이 쓰나요? |
| PRACTICAL_USAGE | UNKNOWN | paraphrase | 실제로 많이들 쓰는 거예요? |
| DEBUG_OR_ERROR | PRACTICAL_USAGE | typo | 스프링 빈 주입이 안 돼는데 왜 그래요? |
| FOLLOW_UP | OFF_TOPIC | typo | 외요? |
| FOLLOW_UP | UNKNOWN | paraphrase | 왜 그런 거예요? |
| FOLLOW_UP | OFF_TOPIC | typo | 그게 무스 말이에요? |
| FOLLOW_UP | CONCEPT_DEFINITION | paraphrase | 방금 그거 무슨 뜻이에요? |
| FOLLOW_UP | CONCEPT_DEFINITION | paraphrase | 그 말이 무슨 뜻이죠? |
| OFF_TOPIC | UNKNOWN | typo | 개발자되면 연봉 많이 받아요? |
| OFF_TOPIC | UNKNOWN | paraphrase | 개발자 연봉 많이 받나요? |
| OFF_TOPIC | UNKNOWN | paraphrase | 개발자 월급 어느 정도예요? |
| UNKNOWN | OFF_TOPIC | paraphrase | ㅋㅋㅋㅋ |
| UNKNOWN | FOLLOW_UP | seed | 그거 있잖아 그거 좀 |
| UNKNOWN | FOLLOW_UP | typo | 그거 이잖아 그거 좀 |

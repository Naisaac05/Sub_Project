# 의도분류 PoC 리포트 — classifier=`rule10`

> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.

- 데이터셋: `dataset.jsonl` (240행, 의도당 24개, dev/holdout 50:50)
- 측정 대상: PoC 참조 구현 `rule10` — 트리거 신호 기반 10-class 규칙 분류기 (순수 PoC, app 무관)
- 비교 기준: Phase 1 의 10-class taxonomy

## 1. 한눈에 보는 결과

- **전체 정확도: 95.0%** (228/240)
- dev 정확도: 98.3%  /  holdout 정확도: 91.7%
- macro-F1: 0.951

## 2. 클래스별 정밀도/재현율/F1

| 의도 | support | 맞춘 수 | precision | recall | F1 |
|---|---:|---:|---:|---:|---:|
| ANSWER_REASON | 24 | 20 | 1.00 | 0.83 | 0.91 |
| WRONG_ANSWER_REASON | 24 | 23 | 1.00 | 0.96 | 0.98 |
| CONCEPT_DEFINITION | 24 | 23 | 0.92 | 0.96 | 0.94 |
| COMPARISON | 24 | 24 | 1.00 | 1.00 | 1.00 |
| EXAMPLE_REQUEST | 24 | 24 | 1.00 | 1.00 | 1.00 |
| PRACTICAL_USAGE | 24 | 23 | 1.00 | 0.96 | 0.98 |
| DEBUG_OR_ERROR | 24 | 23 | 1.00 | 0.96 | 0.98 |
| FOLLOW_UP | 24 | 20 | 0.87 | 0.83 | 0.85 |
| OFF_TOPIC | 24 | 24 | 1.00 | 1.00 | 1.00 |
| UNKNOWN | 24 | 24 | 0.77 | 1.00 | 0.87 |

## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)

| variation_type | 정확도 | (맞춘/전체) |
|---|---:|---|
| seed | 100.0% | 30/30 |
| typo | 88.8% | 71/80 |
| filler | 100.0% | 30/30 |
| paraphrase | 97.0% | 97/100 |

## 4. 혼동 행렬 (행=정답, 열=예측)

약어: AR=ANSWER_REASON, WAR=WRONG_ANSWER_REASON, DEF=CONCEPT_DEFINITION, CMP=COMPARISON, EX=EXAMPLE_REQUEST, PRAC=PRACTICAL_USAGE, DBG=DEBUG_OR_ERROR, FU=FOLLOW_UP, OFF=OFF_TOPIC, UNK=UNKNOWN

| 정답\예측 | AR | WAR | DEF | CMP | EX | PRAC | DBG | FU | OFF | UNK |
|---|---|---|---|---|---|---|---|---|---|---|
| **AR** | 20 | . | 2 | . | . | . | . | 1 | . | 1 |
| **WAR** | . | 23 | . | . | . | . | . | 1 | . | . |
| **DEF** | . | . | 23 | . | . | . | . | . | . | 1 |
| **CMP** | . | . | . | 24 | . | . | . | . | . | . |
| **EX** | . | . | . | . | 24 | . | . | . | . | . |
| **PRAC** | . | . | . | . | . | 23 | . | . | . | 1 |
| **DBG** | . | . | . | . | . | . | 23 | 1 | . | . |
| **FU** | . | . | . | . | . | . | . | 20 | . | 4 |
| **OFF** | . | . | . | . | . | . | . | . | 24 | . |
| **UNK** | . | . | . | . | . | . | . | . | . | 24 |

## 5. 자동 진단 — 어디서 무너지는가

- 가장 흔한 오분류 방향 Top 5:
  - `FOLLOW_UP` → `UNKNOWN` : 4건
  - `ANSWER_REASON` → `CONCEPT_DEFINITION` : 2건
  - `ANSWER_REASON` → `FOLLOW_UP` : 1건
  - `ANSWER_REASON` → `UNKNOWN` : 1건
  - `WRONG_ANSWER_REASON` → `FOLLOW_UP` : 1건

## 부록 B. 오분류 샘플 (최대 25개)

| 정답 | 예측 | variation | 질문 |
|---|---|---|---|
| ANSWER_REASON | FOLLOW_UP | paraphrase | 왜 3번이 답이야? |
| ANSWER_REASON | CONCEPT_DEFINITION | typo | 저답이 B인 이유가 뭐예요? |
| ANSWER_REASON | CONCEPT_DEFINITION | paraphrase | B가 답인 까닭이 뭐죠? |
| ANSWER_REASON | UNKNOWN | typo | 이게 마는 근거가 뭐지? |
| WRONG_ANSWER_REASON | FOLLOW_UP | typo | 이거 내가 왜 트린 거지? |
| CONCEPT_DEFINITION | UNKNOWN | typo | REST API의 저의를 알려줘 |
| PRACTICAL_USAGE | UNKNOWN | paraphrase | 트랜잭션 격리수준 실제로 어떻게 결정해? |
| DEBUG_OR_ERROR | FOLLOW_UP | typo | 이거 왜 자꾸 아 되지? |
| FOLLOW_UP | UNKNOWN | typo | 외요? |
| FOLLOW_UP | UNKNOWN | typo | 다시 쉬게 설명해줄 수 있어요? |
| FOLLOW_UP | UNKNOWN | typo | 다시 쉽개 설명해줄 수 있어요? |
| FOLLOW_UP | UNKNOWN | typo | 그게 무스 말이에요? |

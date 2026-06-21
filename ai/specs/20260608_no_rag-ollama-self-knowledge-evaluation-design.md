---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "No-RAG Ollama Self-Knowledge Evaluation Design 상세 요구사항 및 기능 동작 명세서"

---

# No-RAG Ollama Self-Knowledge Evaluation Design

## Goal

학습 카드가 검색되지 않았을 때 Ollama 생성 모델의 자체 지식만으로 답변해도 되는 범위를 데이터로 검증한다.

이 명세는 구현 순서상 `2026-06-07-ollama-bge-m3-rag-retrieval-design.md` 이후에 실행한다. 먼저 RAG 검색을 BGE-M3 단독 검색으로 완성하고 검증한 뒤, 카드가 없는 질문을 별도 평가셋으로 측정한다. 질문 의도 분류 BGE-M3 연결은 그 다음 단계로 진행한다.

## Problem

현재 AI Review 워크플로는 관련 학습 카드가 있으면 `rag_generation`, 없으면 `generation` 경로로 답변한다. `generation` 경로는 Ollama 자체 지식에 의존한다.

이 경로는 동작하지만, 운영에 믿고 열어둘 만큼의 품질 검증은 부족하다. 기존 실험은 follow-up 프롬프트의 소규모 비교나 스트리밍 속도 측정에 가깝고, 카드 없는 다양한 기술 질문에서 정답률과 환각률을 충분히 측정하지 않았다.

## Scope

### In Scope

- 카드 없는 질문 전용 No-RAG golden dataset 설계
- RAG 검색 결과를 강제로 비우는 `no_retrieval_forced` 평가 모드
- 실제 RAG 검색을 실행했지만 컨텍스트가 비는 `no_retrieval_real` 평가 모드
- 의도별 품질 지표 수집
- Rule Judge, reference facts 기반 claim 검증, Local Semantic Judge, 사람 리뷰를 함께 쓰는 판정 기준
- 카드 없이 허용할 의도와 제한할 의도 결정
- 의도 분류 결과가 답변 프롬프트와 RAG 정책에 전달되어야 한다는 요구사항 고정

### Out of Scope

- RAG BGE-M3 검색 구현
- BGE-M3 의도 분류 구현
- No-RAG 평가 결과에 따른 운영 정책 적용
- 새로운 생성 모델 도입
- 관리자 승인 카드 구조 변경

## Selected Approach

No-RAG 전용 평가셋을 만들고 두 평가 모드를 분리한다.

첫 번째 모드는 `no_retrieval_forced`다. 평가 실행 중 `retrieve_context()` 결과를 빈 목록으로 고정한다. 이 모드는 답변 품질이 학습 카드나 검색 정확도에 섞이지 않게 하여 Ollama 생성 모델 자체 능력만 측정한다.

두 번째 모드는 `no_retrieval_real`이다. 실제 RAG 검색을 그대로 실행하고, 검색 결과가 없거나 threshold 미달로 컨텍스트가 비는 질문만 평가한다. 이 모드는 운영에서 실제로 사용자가 만나는 No-RAG 경로를 재현한다.

정상 워크플로의 prompt, intent, validation, semantic judge, fallback은 그대로 사용한다. `no_retrieval_forced`에서만 검색 결과를 강제로 비우고, `no_retrieval_real`에서는 검색기를 그대로 둔다.

```text
사용자 질문
→ 의도 분류
→ no_retrieval_forced: RAG 검색 강제 비움
  no_retrieval_real: 실제 RAG 검색 후 컨텍스트 없음 확인
→ build_prompt(..., intent=분류 결과, context="")
→ Ollama 생성 모델 답변
→ Rule Judge + Claim Judge + Local Semantic Judge + 사람 리뷰
```

두 모드의 hallucination rate, fallback rate, latency는 같은 표에 섞지 않고 분리 기록한다.

## Judge Strategy

No-RAG 평가는 키워드 포함 여부만으로 통과시키지 않는다. `required_keywords`와 `forbidden_claims`는 빠른 회귀 보조 지표로 유지하되, 사실 정확성과 환각 판정의 주 지표로 쓰지 않는다.

Judge 우선순위는 다음과 같다.

1. Rule Judge
   - 한국어 여부, 길이, 금지 표현, 과도한 확신 표현, `required_keywords`, `forbidden_claims`를 검사한다.
   - 빠르고 재현성이 높지만 의미 오류를 완전히 잡지 못한다.

2. Reference Facts Claim Judge
   - 데이터셋의 `reference_facts`, `acceptable_variants`, `critical_false_claims`를 기준으로 답변의 주장을 비교한다.
   - hard hallucination과 soft hallucination을 구분하는 주 판정 기준이다.

3. Local Semantic Judge
   - 기본 Judge 모델은 로컬 Ollama의 `PYTHON_AI_JUDGE_MODEL`이며, 설정이 없으면 현재 기본 생성 모델인 `exaone3.5:2.4b`를 사용할 수 있다.
   - EXAONE은 한국어 평가와 로컬 재현성 측면에서 유용하지만, 생성 모델과 같을 수 있으므로 최종 Pass/Fail을 단독 결정하지 않는다.
   - Local Semantic Judge는 의도 적합성, 관련성, 자연스러움, 답변 구조를 평가하는 보조 신호다.

4. Human Review
   - hard hallucination 의심, soft hallucination 의심, judge 간 불일치, threshold 경계, 낮은 confidence 사례는 사람이 검토한다.

5. External Judge
   - 외부 Judge는 전체 평가가 아니라 샘플링된 기준 모델 비교에만 사용한다.
   - 비용, 재현성, 로컬 실행 제약 때문에 운영 gate의 필수 의존성으로 두지 않는다.

현재 코드의 Semantic Judge는 실패 시 안전한 결과로 fallback할 수 있고, No-RAG에서는 grounding evidence가 비어 있다. 따라서 No-RAG 운영 통과 판단은 Local Semantic Judge 하나에 의존하면 안 된다.

## Dataset Design

새 데이터셋은 `ai/evals/no_rag_self_knowledge/` 아래에 둔다.

권장 파일:

- `dataset.jsonl`: 평가 질문과 기대 조건
- `README.md`: 평가 목적과 실행 방법
- `REPORT.md`: 최신 평가 결과
- `human_review.md`: 사람이 검토한 샘플 점수

각 행은 다음 필드를 가진다.

```json
{
  "id": "no-rag-rest-api-definition-001",
  "question": "REST API가 뭐야?",
  "expected_intent": "CONCEPT_DEFINITION",
  "expected_sub_intent": "definition",
  "reference_facts": [
    "REST API는 HTTP를 주로 사용한다",
    "자원을 URI로 표현한다",
    "HTTP 메서드로 자원에 대한 동작을 표현한다"
  ],
  "acceptable_variants": [
    "클라이언트와 서버가 정해진 규칙으로 통신한다"
  ],
  "required_keywords": ["HTTP", "클라이언트", "서버", "자원"],
  "critical_false_claims": ["데이터베이스 인덱스 프로토콜이다"],
  "forbidden_claims": ["N+1"],
  "expected_route": "generation",
  "eligible_modes": ["no_retrieval_forced", "no_retrieval_real"],
  "known_card_available": false,
  "critical_intent": false,
  "human_review_required": true
}
```

데이터셋은 의도별로 균형 있게 구성한다.

| 의도 | 목적 | No-RAG 허용 가능성 | 기준 |
|---|---|---|---|
| `CONCEPT_DEFINITION` | 개념 정의 | 높음 | reference facts 대부분 충족 |
| `COMPARISON` | 개념 비교 | 중간 | 양쪽 개념의 차이를 균형 있게 설명 |
| `EXAMPLE_REQUEST` | 예시 요청 | 중간 | 예시가 질문 의도와 맞고 과장 없음 |
| `PRACTICAL_USAGE` | 실무 사용 맥락 | 중간 | 일반론과 한계를 함께 설명 |
| `DEBUG_OR_ERROR` | 오류 원인 추정 | 낮음 | critical intent, 근거 없는 단정 금지 |
| `ANSWER_REASON` | 정답 이유 | 낮음 | critical intent, 원문 문제 근거 필요 |
| `WRONG_ANSWER_REASON` | 오답 이유 | 낮음 | critical intent, 원문 문제 근거 필요 |
| `FOLLOW_UP` | 이전 답변 의존 | 낮음 | 이전 맥락 없으면 제한 답변 우선 |
| `OFF_TOPIC` | 서비스 외 질문 | 답변 제한 | 서비스 범위 안내 |
| `UNKNOWN` | 의도 불명 | 답변 제한 | clarification 우선 |

데이터셋은 단계적으로 확장한다.

- Smoke: 의도당 5개, 총 50개
- Calibration: 의도당 10개, 총 100개
- Release gate: 의도당 최소 25개, 총 250개 이상
- 권장 운영 검증: 의도당 30개 이상, 총 300~400개

각 단계에는 애매한 질문, intent 경계 질문, 오타, 짧은 질문, follow-up, 한국어/영어 혼합 질문을 포함한다.

## Evaluation Metrics

자동 평가는 다음 지표를 계산한다.

| 지표 | 의미 |
|---|---|
| `evaluation_mode` | `no_retrieval_forced` 또는 `no_retrieval_real` |
| `route_generation_rate` | 카드 없이 실제 `generation` 경로로 갔는지 |
| `intent_accuracy` | 질문 의도가 기대값과 맞는지 |
| `required_keyword_rate` | 핵심 키워드를 포함했는지 |
| `forbidden_claim_absent_rate` | 금지 주장을 하지 않았는지 |
| `hard_hallucination_rate` | `critical_false_claims` 또는 reference facts와 명백히 충돌하는 주장 비율 |
| `soft_hallucination_rate` | 근거는 약하지만 치명적 오류는 아닌 불확실 주장 비율 |
| `korean_answer_rate` | 한국어 답변 품질을 유지했는지 |
| `refusal_rate` | 답변 제한 또는 clarification으로 전환한 비율 |
| `over_confident_wrong_rate` | 틀린 답변을 확신형 표현으로 제시한 비율 |
| `intent_hallucination_heatmap` | 의도별 hallucination 분포 |
| `rag_vs_no_rag_delta` | RAG 사용 답변 대비 No-RAG 품질 차이 |
| `uncertainty_calibration` | judge 불일치, 반복 답변 일관성, 낮은 confidence와 실제 오류의 관계 |
| `fallback_rate` | 생성 실패나 품질 문제로 fallback 되었는지 |
| `hallucination_suspected_rate` | 근거 없는 구체 주장 위험이 있는지 |
| `latency_p50_ms`, `latency_p95_ms` | 실제 응답 시간 |

자동 채점은 답변이 틀렸음을 완전히 판정하지 못한다. 따라서 사람 리뷰를 별도로 둔다.

사람 리뷰는 전체 20~30%를 무작정 검토하지 않는다. 다음 대상을 우선 검토한다.

- Random sample 10%
- hard hallucination 의심 전체
- soft hallucination 의심 전체
- threshold 경계 사례 전체
- 낮은 confidence 사례 전체
- Rule Judge와 Local Semantic Judge가 불일치한 사례 전체

사람 리뷰는 다음 1~5점 항목을 본다.

- 정확도
- 설명 자연스러움
- 의도에 맞는 답변 형식
- 운영 노출 가능 여부

## Pass Criteria

No-RAG 자체 지식 경로는 실험 단계와 운영 단계의 기준을 분리한다.

초기 실험 기준:

- 전체 사람 리뷰 평균 정확도 4.3/5 이상
- 의도별 최저 평균 정확도 3.8/5 이상
- `hard_hallucination_rate` 3% 이하
- `soft_hallucination_rate` 8% 이하
- `forbidden_claim_absent_rate` 95% 이상
- `fallback_rate` 10% 이하
- p95 응답 시간이 현재 로컬 운영 허용 범위 안에 있음

운영 허용 기준:

- 전체 사람 리뷰 평균 정확도 4.5/5 이상
- 의도별 최저 평균 정확도 4.2/5 이상
- `hard_hallucination_rate` 1.5% 이하
- `soft_hallucination_rate` 5% 이하
- `forbidden_claim_absent_rate` 95% 이상
- `fallback_rate` 10% 이하
- critical intent에서 관측된 hard hallucination 0건

Critical intent는 다음이다.

- `DEBUG_OR_ERROR`
- `ANSWER_REASON`
- `WRONG_ANSWER_REASON`

Critical intent의 hard hallucination 0건 기준은 의도당 최소 25개 release-gate 표본에서 관측된 오류가 0건이라는 뜻이다. 표본 수가 부족하면 0건이어도 운영 통과로 간주하지 않는다.

기준을 만족하지 못한 의도는 카드 없이 자유 생성하지 않는다.

## Policy Output

평가 결과는 구현 정책으로 이어져야 한다.

예상 정책은 다음과 같다.

```text
카드 없이 허용 가능:
- 단순 개념 정의
- 간단 비교
- 짧은 예시 요청

카드 없으면 제한 또는 후보 생성:
- 정답 이유
- 오답 이유
- 이전 맥락 의존 follow-up
- 디버깅/에러 원인 추정
- UNKNOWN
- OFF_TOPIC
```

정책은 평가 결과가 나온 뒤 수치로 확정한다. 명세 단계에서 특정 의도를 영구 허용하거나 영구 차단하지 않는다.

## Intent Result Propagation Requirement

질문 의도 분류 결과는 답변 생성까지 반드시 전달되어야 한다.

현재 워크플로도 `FreeQuestionIntent`를 `build_prompt(..., intent=...)`에 전달하지만, 구조가 10-class 의도 체계와 완전히 맞지는 않는다. BGE-M3 의도 분류 단계에서는 다음 정보를 답변 생성까지 전달해야 한다.

```text
intent_label
sub_intent
topic
confidence
context_dependent
rag_policy
answer_policy
fallback_reason
```

이 데이터는 두 곳에서 사용된다.

1. RAG 정책
   - 어떤 텍스트로 검색할지
   - 카드가 없을 때 자유 생성해도 되는지
   - 이전 문제 맥락을 포함해야 하는지

2. 답변 프롬프트 정책
   - 정의형 답변인지
   - 비교형 답변인지
   - 예시를 포함해야 하는지
   - 오답/정답 이유처럼 원문 문제 근거가 필요한지
   - 카드가 없으면 제한 답변을 해야 하는지

따라서 BGE-M3 의도 분류 구현은 단순 label 반환으로 끝나면 안 된다. 답변 생성이 사용할 수 있는 정책 객체로 변환되어 workflow state와 응답 metadata에 남아야 한다.

## Execution Order

실행 순서는 다음으로 고정한다.

1. Ollama BGE-M3 RAG 검색 구현 및 검증
2. No-RAG 자체 지식 평가셋과 평가 스크립트 구현
3. No-RAG 평가 실행 및 허용/제한 정책 결정
4. BGE-M3 의도 분류 단독 적용 설계
5. 의도 결과를 RAG 정책과 답변 프롬프트 정책에 연결

이 순서를 지키는 이유는 RAG 검색 품질이 먼저 안정되어야 카드가 있는 질문과 카드가 없는 질문을 명확히 분리할 수 있기 때문이다.

## Verification

No-RAG 평가 구현 후 검증 명령은 다음을 포함한다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests
python scripts/evaluate_no_rag_self_knowledge.py
```

Ollama가 필요한 live 평가는 별도 명령으로 둔다. Ollama가 실행되지 않은 개발 환경에서도 단위 테스트는 통과해야 한다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/evaluate_no_rag_self_knowledge.py --live-ollama --mode no_retrieval_forced
python scripts/evaluate_no_rag_self_knowledge.py --live-ollama --mode no_retrieval_real
```

평가 결과는 `ai/evals/no_rag_self_knowledge/REPORT.md`에 기록한다.

## Acceptance Criteria

- 각 데이터 행이 `eligible_modes`와 `known_card_available`을 명시한다.
- `no_retrieval_forced`는 카드 존재 여부와 무관하게 생성 모델 자체 능력을 측정한다.
- `no_retrieval_real`은 실제 검색 결과가 없거나 threshold 미달인 질문만 평가한다.
- `no_retrieval_forced`와 `no_retrieval_real`이 별도 결과로 기록된다.
- `no_retrieval_forced`에서는 RAG 검색 결과가 강제로 비워진다.
- `no_retrieval_real`에서는 실제 RAG 검색 후 컨텍스트가 없는 질문만 평가된다.
- 실제 답변 route가 `generation`인지 확인한다.
- `reference_facts`, `acceptable_variants`, `critical_false_claims` 기반 claim 검증이 포함된다.
- Rule Judge, Local Semantic Judge, 사람 리뷰 항목이 모두 기록된다.
- Local Semantic Judge는 단독 Pass/Fail 기준으로 쓰지 않는다.
- 결과에 따라 카드 없이 허용할 의도와 제한할 의도를 결정할 수 있다.
- BGE-M3 의도 분류 결과가 답변 프롬프트와 RAG 정책에 전달되어야 한다는 요구사항이 후속 의도 분류 명세에 반영된다.
- 이 단계에서는 실제 의도 분류 구현을 변경하지 않는다.

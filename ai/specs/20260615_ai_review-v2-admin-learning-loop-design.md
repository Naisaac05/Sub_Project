---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "Ollama 기반 AI Review v2 Admin Learning Loop Design 상세 요구사항 및 기능 동작 명세서"

---

# AI Review v2 Admin Learning Loop Design

## Goal

관리자가 AI 답변 후보를 승인하면 검토한 개념 정의만 포함한 `concepts_v2` JSON 카드가 즉시 발행되고, 다음 질문부터 v2 Fast Path가 사용할 수 있게 한다. 카드가 없는 질문은 Ollama가 답하고 검토 후보로 축적하며, 로컬 개발 환경에서는 추가 Ollama judge 호출을 기본 비활성화한다.

## Selected Approach

관리자 승인을 v2 지식 발행 트랜잭션으로 취급한다.

1. 관리자가 후보의 정의를 검토하고 승인한다.
2. 백엔드는 후보를 기반으로 최소 v2 카드를 staging 파일에 생성한다.
3. JSON 구조와 필수 필드를 검증한다.
4. 검증된 파일을 `ai/app/knowledge/concepts_v2/<category>/<card_id>.json`에 원자적으로 반영한다.
5. 파일 발행이 성공한 경우에만 DB 후보를 `APPROVED`로 확정한다.
6. 이후 정의 질문은 v2 Fast Path가 답하고, 승인되지 않은 payload intent는 Ollama로 fallback한다.

기존 `LoggingAiReviewKnowledgeReindexer`의 v1 Markdown 생성과 Chroma 재색인은 제거한다. v2 Fast Path는 실행 시 승인된 JSON 카드를 직접 읽으므로 이번 범위에서 Chroma 갱신은 필요하지 않다.

## Minimal v2 Card

관리자 승인으로 생성하는 카드는 검토된 정보만 승인한다.

```json
{
  "card_id": "java-arraylist",
  "category": "java",
  "term": "ArrayList",
  "aliases": ["ArrayList"],
  "source_question_ids": ["auto:<external-candidate-id>"],
  "retrieval": {
    "embedding_text": "ArrayList <source question> <resolved query>",
    "embedding_hash": "",
    "boost_keywords": ["ArrayList"],
    "intent_types": ["CONCEPT_DEFINITION"]
  },
  "payloads": {
    "CONCEPT_DEFINITION": {
      "content": "<reviewer-approved definition>",
      "examples": []
    }
  },
  "review": {
    "card_status": "approved",
    "payload_status": {
      "CONCEPT_DEFINITION": "approved"
    },
    "approved_at": "<UTC timestamp>",
    "reviewer": "<reviewer>",
    "rejected_reason": null
  },
  "related_card_ids": [],
  "tags": [],
  "created_at": "<UTC timestamp>",
  "updated_at": "<UTC timestamp>"
}
```

`ANSWER_REASON`, `WRONG_ANSWER_REASON`, `EXAMPLE_REQUEST` 등 검토하지 않은 payload는 생성하지 않는다. 해당 intent 요청은 기존 Fast Path의 `payload_not_approved` 처리에 따라 Ollama로 넘어간다.

## Card Identity And Collision Rules

- `card_id`는 정규화된 `<category>-<term>`을 기본값으로 사용한다.
- 같은 `card_id`의 기존 카드가 있으면 자동 덮어쓰기하지 않는다.
- 기존 카드가 동일 후보에서 발행된 카드이면 정의 payload만 갱신할 수 있다.
- 다른 출처 카드와 충돌하면 발행을 중단하고 후보를 `PENDING`으로 유지한다.
- `category`, `term`, `aliases`, `retrieval.*`은 관리자 승인 초안에서 결정적으로 생성하며 불필요한 질문 문장이나 stopword를 포함하지 않는다.

## Approval State And Failure Handling

운영 상태는 DB 후보의 status와 workflow phase로 구분한다.

- `PENDING / DRAFTED`: 수집되어 검토를 기다리는 후보
- `PENDING / HUMAN_REVIEW`: 관리자가 검토 중인 후보
- `PENDING / PUBLISH_FAILED`: 승인을 시도했지만 v2 JSON 발행에 실패한 후보
- `APPROVED / APPROVED`: v2 카드 발행까지 완료된 후보
- `REJECTED / REJECTED`: 거절된 후보
- `MERGED / MERGED`: 다른 후보로 병합된 후보

발행 실패 시 `status`는 `PENDING`을 유지하고 `publishError`와 마지막 실패 시각을 저장한다. 관리자는 내용을 수정한 뒤 재시도할 수 있다. 승인 감사 로그는 발행 성공 후 기록하며, 실패 시에는 별도 실패 감사 로그를 기록한다.

파일 쓰기는 같은 디렉터리의 임시 파일에 먼저 기록한 후 atomic move를 사용한다. JSON 검증 또는 move가 실패하면 임시 파일을 제거하고 기존 카드는 보존한다.

## Admin UI

관리자 페이지는 후보의 운영 상태와 발행 결과를 중심으로 정리한다.

- 필터: `승인 대기`, `검토 중`, `반영 완료`, `반영 실패`, `거절`, `병합`
- 상세 정보: 원문 질문, 해석 질문, Fast Path 실패 이유, Ollama 답변 초안, 검토 정의
- 발행 정보: 생성될 `card_id`, 승인 payload `CONCEPT_DEFINITION`, 발행 경로, 발행 오류
- 액션: `검토 시작`, `정의 승인 및 v2 반영`, `재시도`, `거절`, `병합`
- 일반 운영에서 JSONL 가져오기 버튼과 일괄 승인은 제거한다.

한 번의 승인 요청은 한 카드만 처리한다. UI는 성공 응답을 받은 카드만 반영 완료로 표시한다.

## Missing Card Learning Loop

카드가 없는 자유 질문은 다음 흐름을 사용한다.

1. v2 approved Fast Path를 조회한다.
2. Fast Path가 실패하면 Ollama가 답변한다.
3. Ollama 답변이 비어 있지 않고 후보 수집 조건을 통과하면 HTTP sink로 DB 후보를 저장한다.
4. 후보 ID는 정규화된 질문에서 결정적으로 생성하여 중복을 방지한다.
5. 후보 저장 실패는 사용자 답변을 실패시키지 않고 `candidate_capture_failed` 관측 플래그를 남긴다.

후보 수집 허용 사유는 v2 Fast Path의 `retrieval_miss`, `score_gate`, `anchor_miss`, `payload_not_approved`, `payload_empty`로 제한한다. follow-up, fallback template, 빈 답변, 오류 응답은 후보로 저장하지 않는다.

## Follow-Up Flow

follow-up은 새 지식 후보 생성 경로가 아니라 학습 진단 경로다. 현재 구현은 세션 DB에 전체 메시지를 저장하지만 Python/Ollama 요청에는 직전 AI 질문을 전달하지 않는다. 따라서 학습자가 `네`, `모르겠어요`처럼 짧게 답하면 어떤 질문에 대한 답인지 모델이 정확히 판단하기 어렵다.

- v1 또는 v2 카드 검색을 수행하지 않는다.
- 전체 대화 이력 대신 원문 문제, 직전 AI 질문, 최신 학습자 답변, 평가 결과, 활성 개념, 단계 번호만 전달한다.
- 한 번에 질문 하나만 생성하며 기존 최대 길이 제한을 유지한다.
- follow-up 응답과 학습자 답변은 후보 수집 대상에서 제외한다.

follow-up 역할을 다음 세 가지로 분리한다.

- `DIAGNOSTIC_FOLLOW_UP`
  - 오답 진단 과정에서 직전 AI 질문에 대한 답을 평가한다.
  - 부족한 부분 하나만 설명하고 다음 확인 질문 하나를 생성한다.
  - 기존 제한대로 최대 3단계까지만 진행한다.

- `FREE_QUESTION`
  - 사용자가 직접 입력한 질문에 답한다.
  - v2 카드가 없고 Ollama 답변이 성공하면 지식 후보를 생성할 수 있다.

- `FREE_QUESTION_CHECK`
  - 자유 질문 답변 뒤 짧은 이해 확인 질문을 생성한다.
  - RAG 후보를 생성하지 않고 semantic/grounding judge도 실행하지 않는다.

Python 요청에는 다음 필드를 추가한다.

```text
follow_up_type
previous_ai_question
active_concept
```

백엔드는 세션에 저장된 최신 AI 질문에서 `previous_ai_question`을 가져온다. `active_concept`는 최근 AI 응답의 `matchedConceptId`, `resolvedQuery`, 현재 문제의 핵심어 순으로 선택한다. 전체 대화 이력 또는 별도 장기 메모리는 이번 범위에 추가하지 않는다.

## Local Judge Policy

로컬 개발 환경에서는 답변 생성 외 추가 Ollama 호출을 기본적으로 끈다.

- `AI_REVIEW_SEMANTIC_JUDGE_ENABLED=false`
- `AI_REVIEW_GROUNDING_JUDGE_ENABLED=false`

비활성화 시 judge 함수는 Ollama를 호출하지 않고 안전한 skipped 결과를 반환한다. 관측 정보에는 `semantic_judge_skipped=true`, `grounding_judge_skipped=true`와 비활성화 사유를 남긴다.

운영 환경에서는 두 환경 변수를 명시적으로 `true`로 설정해야 judge가 실행된다. 규칙 기반 validation과 confidence gate는 judge 설정과 무관하게 유지한다.

## Components

### Backend

- `AiReviewCandidateApprovalV2Service`
  - 승인 요청을 v2 발행 성공 후 DB 승인으로 확정한다.
  - 발행 실패 상태와 감사 로그를 관리한다.

- `AiReviewKnowledgeReindexer`
  - 이름은 호환성을 위해 유지하되 의미를 v2 카드 publisher로 변경한다.

- `LoggingAiReviewKnowledgeReindexer`
  - v1 Markdown 대신 최소 v2 JSON 카드를 staging, 검증, atomic move 방식으로 발행한다.

- Candidate entity/DTO
  - `PUBLISH_FAILED`, `publishError`, `publishedCardId`, `publishedCardPath`를 노출한다.

### AI Runtime

- 후보 수집 판단을 v2 Fast Path 실패 사유에 맞춘다.
- follow-up 후보 수집을 명시적으로 차단한다.
- semantic/grounding judge 환경 플래그를 추가한다.

### Frontend

- 후보 운영 상태와 v2 발행 결과를 표시한다.
- 깨진 한글 문자열을 정상 UTF-8 한국어로 교체한다.
- JSONL 가져오기와 일괄 승인을 일반 운영 화면에서 제거한다.

## Testing Strategy

모든 동작 변경은 TDD로 구현한다.

### Backend Tests

- 승인된 후보가 최소 v2 JSON 카드로 발행되는지 검증한다.
- 생성 카드에서 `CONCEPT_DEFINITION`만 승인되는지 검증한다.
- JSON 검증 또는 파일 쓰기 실패 시 후보가 `PENDING / PUBLISH_FAILED`로 남는지 검증한다.
- 기존 다른 카드와 ID가 충돌하면 덮어쓰지 않는지 검증한다.
- 발행 성공 전에는 `APPROVED` 응답을 반환하지 않는지 검증한다.

### AI Tests

- 새로 발행된 최소 카드가 정의 질문의 v2 Fast Path에서 사용되는지 검증한다.
- 미승인 payload intent가 Ollama fallback으로 이동하는지 검증한다.
- 허용된 Fast Path miss만 후보로 저장되는지 검증한다.
- follow-up이 후보를 저장하지 않는지 검증한다.
- 진단 follow-up 요청에 직전 AI 질문과 활성 개념이 포함되는지 검증한다.
- `FREE_QUESTION_CHECK`가 후보와 judge를 모두 건너뛰는지 검증한다.
- judge 비활성화 시 Ollama judge 호출이 없는지 검증한다.

### Frontend Tests And Manual Check

- 운영 상태 필터와 발행 오류 표시를 검증한다.
- 승인 성공과 실패 응답이 올바른 상태로 표시되는지 검증한다.
- `http://localhost:3000/admin/ai-review-candidates`에서 관리자 인증 후 수동 확인한다.

### End-To-End

1. 카드에 없는 질문을 전송한다.
2. Ollama 응답과 DB 후보 생성을 확인한다.
3. 관리자에서 정의를 승인한다.
4. 생성된 v2 JSON과 DB 승인 상태를 확인한다.
5. 같은 정의 질문이 v2 Fast Path로 답변되는지 확인한다.
6. 다른 미승인 intent가 Ollama로 fallback하는지 확인한다.

## Documentation

구현 후 다음 내용을 하나의 운영 문서로 정리한다.

- 관리자 후보 검토와 승인 절차
- 카드가 없는 질문의 후보 축적 절차
- follow-up 데이터 흐름
- 로컬 judge OFF와 운영 judge ON 설정
- 발행 실패 진단 및 재시도 방법

## Out Of Scope

- 승인 시 전체 payload 자동 생성
- Chroma 재색인
- 장기 대화 메모리
- 후보 일괄 승인
- 기존 `concepts_v2` 카드의 대량 품질 수정
- 승인 후보의 자동 fact-check

## Success Criteria

- 관리자 승인 성공 응답은 유효한 v2 JSON 카드가 실제 저장된 경우에만 반환된다.
- 승인된 정의 질문은 다음 요청에서 v2 Fast Path로 답한다.
- 미승인 payload 질문은 Ollama로 fallback한다.
- 카드 미존재 질문의 Ollama 답변은 중복 없이 후보로 축적된다.
- follow-up은 후보를 생성하지 않는다.
- 로컬 기본 설정에서 semantic/grounding judge가 Ollama를 호출하지 않는다.
- 관리자 화면에서 승인 대기, 검토 중, 반영 완료, 반영 실패, 거절, 병합을 구분할 수 있다.

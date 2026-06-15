# AI Review v2 관리자 학습 루프

## 운영 흐름

1. 사용자의 자유 질문을 v2 approved Fast Path에서 검색한다.
2. 승인된 payload가 없으면 Ollama가 답변한다.
3. 허용된 Fast Path 실패 사유이면 DB 검토 후보를 생성한다.
4. 관리자가 `/admin/ai-review-candidates`에서 정의를 검토하고 승인한다.
5. 백엔드는 `ai/app/knowledge/concepts_v2/<category>/<card_id>.json`에 최소 v2 카드를 발행한다.
6. JSON 발행 성공 후에만 후보 상태가 `APPROVED`가 된다.

승인된 최소 카드는 `CONCEPT_DEFINITION`만 승인한다. 아직 검토하지 않은 payload 질문은 Ollama로 fallback한다.

## 관리자 상태

- `승인 대기`: 수집된 후보
- `검토 중`: 관리자가 검토 중인 후보
- `반영 완료`: v2 카드 발행 완료
- `반영 실패`: 충돌, JSON 오류, 파일 쓰기 실패
- `거절`: 지식 카드로 만들지 않는 후보
- `병합`: 기존 후보와 중복된 후보

반영 실패 후보는 `PENDING / PUBLISH_FAILED` 상태를 유지한다.

## 꼬리질문

진단 꼬리질문에는 전체 대화 대신 원문 문제, 직전 AI 질문, 최신 학습자 답변, 평가 결과, 활성 개념, 단계 번호만 전달한다. 진단 꼬리질문과 자유 질문 뒤 이해 확인 질문은 RAG 후보를 생성하지 않는다.

## 로컬 judge 설정

```text
AI_REVIEW_SEMANTIC_JUDGE_ENABLED=false
AI_REVIEW_GROUNDING_JUDGE_ENABLED=false
```

운영 환경에서 judge가 필요하면 각 값을 명시적으로 `true`로 설정한다. judge가 꺼져도 규칙 기반 validation과 confidence gate는 계속 실행된다.

## 장애 확인

- 후보가 생성되지 않음: `candidate_capture_failed` 또는 Fast Path miss 사유를 확인한다.
- 승인 후 반영 실패: 관리자 화면의 `publishError`와 충돌한 `card_id`를 확인한다.
- 승인했지만 Fast Path 미사용: 질문 intent와 `CONCEPT_DEFINITION` 승인 상태를 확인한다.

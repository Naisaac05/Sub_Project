---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "RAG 기반 관리자 후보 승인이 삭제된 v1 카드를 발행함 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# 관리자 후보 승인이 삭제된 v1 카드를 발행함

- 발생 일시: 2026-06-15
- 영역: backend / AI RAG / frontend admin
- 심각도: high

## 증상

관리자에서 후보를 승인해도 현재 운영 중인 `concepts_v2` Fast Path에 카드가 추가되지 않았다. DB 상태는 승인 완료로 보였지만 실제 발행물은 삭제된 v1 Markdown 형식이었다.

## 원인

`LoggingAiReviewKnowledgeReindexer`가 `concepts/generated/*.md`, v1 manifest, Chroma 재색인을 계속 사용했다. v1 런타임 제거 이후 승인 publisher 계약과 관리자 화면이 함께 전환되지 않았다.

## 해결 방법

- `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`: 최소 v2 JSON publisher로 교체
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`: 발행 실패를 `PUBLISH_FAILED`로 기록
- `frontend/src/app/admin/ai-review-candidates/page.tsx`: 발행 상태와 오류 표시

## 재발 방지·메모

승인 성공의 정의는 DB 상태 변경이 아니라 유효한 v2 카드의 실제 저장 완료다. v1 발행 동작을 요구하는 테스트는 v2 publisher 계약 테스트로 교체했다.

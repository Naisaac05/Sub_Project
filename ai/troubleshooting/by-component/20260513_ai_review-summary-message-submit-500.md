---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review submit failed after summary message 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review submit failed after summary message

- 발생 일시: 2026-05-13
- 영역: backend / frontend
- 심각도: medium

## 증상
AI 복습 화면에서 확인 질문 답변 제출 시 브라우저 콘솔에 `POST /api/ai-review/sessions/1/messages 500 (Internal Server Error)`가 표시되고, 화면에는 "백엔드가 AI 응답을 받는 중 오류가 났습니다. Python AI와 Ollama가 모두 켜져 있는지 확인해주세요." 메시지가 표시됐다.

## 원인
문제 요약 또는 전체 리포트 생성 후에는 `QUESTION_SUMMARY`, `REVIEW_REPORT` 같은 AI 메시지도 세션의 최신 AI 메시지가 될 수 있다. 기존 제출 로직은 `findTopBySessionIdAndRoleOrderByCreatedAtDesc(..., AI)`로 최신 AI 메시지를 무조건 현재 복습 질문으로 사용했기 때문에, 요약/리포트 메시지를 잡으면 현재 문제 식별이 꼬여 제출 처리 중 500으로 이어질 수 있었다.

관련 파일:
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:158
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:508

## 해결 방법
답변 제출 시 최신 AI 메시지를 직접 조회하지 않고 `latestQuestionMessage`를 통해 실제 질문에 연결된 AI 메시지만 역순으로 찾도록 수정했다. `QUESTION_SUMMARY`, `REVIEW_REPORT`, question이 없는 AI 메시지는 현재 질문 후보에서 제외한다.

## 재발 방지 / 메모
대화 로그에 시스템성 AI 메시지와 학습 질문 AI 메시지가 함께 저장될 때는 "마지막 AI 메시지"를 곧바로 현재 질문으로 간주하면 안 된다. 현재 질문을 정할 때는 `questionId` 요청값을 우선 사용하고, fallback도 question이 있는 학습 메시지로 제한해야 한다.

---
type: troubleshooting
category: ollama
status: active
updated: 2026-06-18
description: "AI review Ollama submit timed out before saved response was returned 발생 원인 분석..."

---

# AI review Ollama submit timed out before saved response was returned

- 발생 날짜: 2026-04-30
- 영역: frontend / backend
- 심각도: medium

## 증상

Ollama `qwen2.5:1.5b`를 연결한 뒤 스마트 복습에서 새 자유 질문을 제출하면 화면에 `답변을 제출하지 못했습니다.`가 표시됐다. 백엔드를 재실행하거나 새로고침하면 이전 요청의 AI 답변이 뒤늦게 저장되어 보일 수 있었다.

## 원인

프론트 공통 API 클라이언트의 기본 timeout이 10초였다. 로컬 Ollama 모델은 첫 응답이나 자유 질문 답변 생성이 10초를 넘길 수 있는데, 이 경우 백엔드는 계속 처리해서 DB에 답변을 저장하지만 프론트는 먼저 timeout으로 실패 처리했다.

추가로 AI 답변이 길어질 경우 `ai_review_messages.content` 길이 제한에 걸릴 가능성이 있어 저장 전 길이 제한이 필요했다.

## 해결 방법

- AI 복습 API 요청만 timeout을 60초로 늘렸다.
  - `frontend/src/lib/ai-review.ts:8`
  - `frontend/src/lib/ai-review.ts:14`
  - `frontend/src/lib/ai-review.ts:22`
  - `frontend/src/lib/ai-review.ts:35`
- AI 답변 저장 전 최대 길이를 제한해 DB 저장 오류 가능성을 줄였다.
  - `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:21`
  - `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:267`
  - `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:271`

## 재발 방지·메모

로컬 LLM 호출은 일반 API보다 응답 시간이 길 수 있으므로 공통 10초 timeout을 그대로 쓰면 안 된다. AI 복습처럼 모델 추론을 기다리는 요청은 별도 timeout을 둔다.

추후 스트리밍 응답을 도입하면 사용자가 기다리는 동안 진행 상태를 볼 수 있어 UX가 더 좋아진다.


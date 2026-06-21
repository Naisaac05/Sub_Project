---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review streaming duplicate save and done DTO gap 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review streaming duplicate save and done DTO gap

- 발생 일시: 2026-05-25
- 영역: backend
- 심각도: medium

## 증상

스트리밍 자유 질문이 Python upstream에서 첫 chunk 전에 실패하면 서버가 USER 메시지를 먼저 저장하고, 프론트엔드가 non-streaming fallback을 호출하면서 같은 사용자 입력이 중복 저장될 수 있었다. 정상 완료 시에도 Spring SSE `done` 이벤트가 저장된 메시지 목록을 포함한 `AiReviewSubmitResponse`가 아니라 upstream 원본 이벤트 형태에 가까워, 프론트엔드가 세션 reload fallback에 의존해야 했다. upstream이 명시적인 `done` 이벤트 없이 chunk 전송 후 Flux만 완료하는 경우에도 빈 `done` 이벤트가 내려가 최종 응답 계약이 깨질 수 있었다.

## 원인

`AiReviewStreamingService.streamAnswer(...)`가 Python 스트림을 구독하기 전에 `saveUserMessage(...)`를 선저장하는 구조였다. 또한 `done` 이벤트 처리에서 `saveCompletedAiMessage(...)` 후 저장된 USER/AI 메시지로 응답 DTO를 조립하지 않아 non-streaming submit 응답과 계약이 달랐다. Reactor completion callback도 `{"type":"done"}`만 전송하고 저장/DTO 조립을 수행하지 않았다.

## 해결 방법

- USER 메시지 저장을 첫 `chunk` 수신 또는 정상 `done` 처리 직전으로 지연하고 `AtomicReference`로 1회만 저장되도록 보장했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:177`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:204`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:358`
- 첫 chunk 전 error event에서는 누적 답변이 없으면 AI/USER 메시지를 저장하지 않도록 기존 partial-failed 저장 조건과 맞췄다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:226`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:539`
- 정상 `done` 이벤트는 저장된 USER/AI 메시지를 포함한 `AiReviewSubmitResponse`로 정규화했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:211`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:377`
- 명시적 `done` 없이 Flux가 완료되는 경로도 누적 답변이 있으면 저장 후 `AiReviewSubmitResponse`를 담은 `done` 이벤트로 정규화했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:268`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:286`
- 회귀 테스트를 추가해 첫 chunk 전 실패 시 저장이 없는지, done 응답이 저장 메시지를 포함한 DTO인지, chunk 후 암묵적 완료도 DTO 응답인지 고정했다: `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:268`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:296`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:346`

## 재발 방지 / 메모

SSE 경로는 프론트엔드의 sync fallback과 함께 동작하므로 upstream이 실제 답변 생성을 시작하기 전에는 사용자 제출을 DB에 확정하지 않아야 한다. `done` 이벤트는 프론트엔드가 reload 없이 세션 상태를 갱신할 수 있도록 non-streaming submit과 같은 응답 DTO 형태를 유지한다.

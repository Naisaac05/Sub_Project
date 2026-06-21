---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI stream terminal idempotency guard 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI stream terminal idempotency guard

- 발생 일시: 2026-05-26
- 영역: backend / DB
- 심각도: high

## 증상
Streaming AI review에서 `COMPLETED`, `DISCONNECTED`, `ERROR` 같은 terminal 상태는 한 요청당 하나만 남아야 한다. 기존 regression test는 duplicate done, late done, disconnect 후 done 같은 인메모리 race를 막고 있었지만, DB에 terminal request id가 없어 같은 stream 요청의 terminal 저장을 DB 수준에서 구분하거나 unique guard로 막을 근거가 부족했다.

## 원인
`AiReviewStreamingService`는 `correlationId`를 stream 단위로 생성했지만, 저장되는 `AiReviewMessage`에는 이 값을 남기지 않았다. Terminal 상태도 `aiQualityFlags` 문자열의 `STATUS:*` 토큰으로만 표현되어 있어서 `COMPLETED`, `DISCONNECTED`, `ERROR` 중 하나만 저장되어야 한다는 제약을 DB schema나 repository lookup으로 표현할 수 없었다.

## 해결 방법
`backend/src/main/java/com/devmatch/entity/AiReviewMessage.java:11`에 `stream_request_id` unique constraint를 추가하고, `backend/src/main/java/com/devmatch/entity/AiReviewMessage.java:69`와 `backend/src/main/java/com/devmatch/entity/AiReviewMessage.java:73`에 terminal request id/status 컬럼을 추가했다. Terminal status enum은 `backend/src/main/java/com/devmatch/entity/AiReviewStreamTerminalStatus.java:3`에 정의했다.

`backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java:22`에 `findByStreamRequestId`를 추가했고, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:538`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:621`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:662`의 terminal save path가 `correlationId`를 `streamRequestId`로 저장하도록 바꿨다. `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:703`와 `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:710`에서 기존 terminal 재사용 및 DB unique race fallback을 처리한다.

Regression coverage는 `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:734`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:766`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:803`에 추가했다.

## 재발 방지 / 메모
현재 프로젝트는 별도 Flyway/Liquibase migration 체계가 없고 dev는 `ddl-auto=update`, prod는 `ddl-auto=validate`이다. 운영 DB에는 `stream_request_id`, `stream_terminal_status`, 그리고 `uk_ai_review_stream_request_terminal` unique constraint를 배포 절차에 맞춰 선반영해야 한다.

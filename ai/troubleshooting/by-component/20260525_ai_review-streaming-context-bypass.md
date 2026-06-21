---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review streaming context bypass 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review streaming context bypass

- 발생 일시: 2026-05-25
- 영역: backend
- 심각도: high

## 증상
AI 리뷰 스트리밍 엔드포인트가 non-streaming 제출 경로와 다르게 동작했다. 스트리밍 요청은 세션 소유자 검증, 자유 질문 3회 제한, 실제 문제/정답/선택지 컨텍스트 구성을 거치지 않고 Python AI 서버로 전달될 수 있었다.

## 원인
`AiReviewStreamingService`가 `userId`를 인자로 받지만 스트리밍 활성화 경로에서 세션 소유자 확인에 사용하지 않았다. 또한 Python 요청을 만들 때 실제 `Question`과 `TestAnswer`를 조회하지 않고 `stub question`, `stub correct`, `stub selected` 값을 사용했으며, 모든 스트리밍 요청을 `/api/review/follow-up`으로 보냈다. 이 때문에 `RuleBasedAiReviewService.submitAnswer()`가 수행하던 소유자 검증, 현재 문제 해석, 자유 질문 횟수 제한, mode별 처리와 스트리밍 경로가 갈라졌다.

## 해결 방법
- `FREE_QUESTION`이 아닌 스트리밍 요청은 기존 동기 제출 경로로 위임해 평가/다음 문제 이동 로직이 분기되지 않게 했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:75`
- 스트리밍 활성화 경로에서 세션 소유자 검증을 추가했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:79`
- 현재 문제와 오답 정보를 조회해 실제 question/options/correct/selected/user_answer를 Python streaming request에 넣고 `/api/review/free-question`으로 전송하도록 수정했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:89`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:143`, `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:162`
- 자유 질문 3회 제한을 스트리밍 경로에도 적용했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:91`
- 회귀 테스트에 소유자 검증, 질문 제한, non-free mode 동기 위임, Python request payload 검증을 추가했다: `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:136`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:153`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:176`, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:249`

## 재발 방지 / 메모
스트리밍 경로를 추가할 때는 기존 동기 경로의 인증/인가, 세션 상태, 질문 제한, 컨텍스트 구성 로직을 우회하지 않는지 테스트로 먼저 고정해야 한다. 특히 SSE는 프론트에서 같은 제출처럼 보이더라도 서버 구현이 별도 서비스로 갈라지기 쉬우므로, Python request payload까지 캡처해 검증하는 테스트가 필요하다.

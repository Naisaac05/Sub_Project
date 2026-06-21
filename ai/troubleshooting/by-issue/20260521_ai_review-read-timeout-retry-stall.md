---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review read timeout retry stall 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review read timeout retry stall

- 발생 날짜: 2026-05-21
- 범위: backend / frontend
- 심각도: medium

## 증상

AI 복습 자유질문 제출 후 로딩 점이 계속 표시되고 답변이 도착하지 않는 것처럼 보였다. 10초 이후 지연 안내는 보였지만, "대체 답변을 준비 중"이라는 문구와 달리 실제 fallback 응답은 바로 표시되지 않았다.

## 원인

`Spring -> Python AI -> Ollama fallback` 호출 체인에서 AI read timeout도 retryable 오류로 분류했다. 그 결과 read timeout 30초가 동일 생성 요청에 최대 3회 반복될 수 있었고, Python 경로가 모두 지연된 뒤 Ollama fallback까지 이어지면 사용자는 긴 시간 동안 submit 요청이 끝나지 않는 상태를 보게 됐다.

관련 위치:

- `backend/src/main/java/com/devmatch/service/ai/AiRetrySupport.java:56`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:434`
- `frontend/src/app/tests/results/[id]/review/page.tsx:57`

## 해결 방법

`ResourceAccessException` 중 메시지가 `Read timed out`인 경우는 retryable에서 제외했다. 로컬 LLM 생성 read timeout은 같은 요청을 재시도해도 다시 느릴 가능성이 높으므로, 즉시 상위 fallback 판단으로 넘기는 편이 사용자 체감 지연과 Tomcat thread 점유를 줄인다.

또한 프론트의 `RETRYING` 안내 문구를 "대체 답변 준비 중"에서 "AI 서버 상태 확인 중"으로 조정해, 실제 fallback이 아직 실행되지 않은 상태를 과장해서 보여주지 않도록 했다.

## 재발 방지·메모

- 네트워크 연결 실패나 5xx/429는 retry 대상이지만, 로컬 LLM read timeout은 retry보다 fallback이 우선이다.
- retry 정책을 바꿀 때는 provider 체인 전체 최악 시간을 함께 계산해야 한다.
- 관련 테스트: `AiRetrySupportTest.readTimeoutFallsBackWithoutRetryingTheSameSlowGeneration`

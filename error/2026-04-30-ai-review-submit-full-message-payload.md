# AI review submit full message payload

- 작성 일자: 2026-04-30
- 영역: backend / frontend
- 심각도: medium

## 증상

AI 복습의 꼬리질문을 여러 번 주고받을수록 응답과 브라우저 상태에 전체 대화 메시지가 반복해서 실렸다. 사용자는 "세션이 남아있다", "프로세스가 누적된다", "꼬리질문 쪽에서 RAM을 많이 잡아먹는다"는 현상으로 인지할 수 있었다.

## 원인

답변 제출 API가 매번 세션의 전체 메시지를 다시 조회해서 내려줬다. 프론트도 그 응답의 `messages`로 세션 상태를 통째로 교체했다. 대화가 길어질수록 네트워크 payload, JSON 역직렬화, React state 크기가 함께 증가했다.

- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:92`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:494`
- `frontend/src/app/tests/results/[id]/review/page.tsx:106`

## 해결 방법

백엔드에서 답변 처리 시작 전 마지막 메시지 ID를 cursor로 잡고, 처리 완료 후에는 그 이후에 생성된 메시지만 응답하도록 변경했다. 프론트는 응답으로 받은 신규 메시지를 기존 메시지 목록에 중복 없이 append하도록 변경했다.

- `backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java:16`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:168`
- `frontend/src/app/tests/results/[id]/review/page.tsx:111`

## 재발 방지·메모

세션 시작/조회처럼 전체 대화가 필요한 API와, 답변 제출처럼 증분만 필요한 API를 분리해서 생각해야 한다. 꼬리질문 기능에서 RAM이 계속 커지는 느낌이 다시 보이면 DB 세션 수보다 먼저 응답 payload 크기, React state 크기, Ollama 모델 상주 메모리를 함께 확인한다.

검증 메모:
- `backend`의 `compileJava`는 Gradle 캐시 접근 권한을 해제해 재실행한 뒤 통과했다.
- `frontend`의 `npx.cmd tsc --noEmit`은 기존 admin/chart/ui 타입 오류와 Radix 타입 모듈 누락으로 실패했다.

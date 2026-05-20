# AI review timeout shown as error

- 발생 일자: 2026-05-16
- 영역: frontend / backend
- 심각도: medium

## 증상

AI 복습 답변이 10초대를 넘기면 시간 초과 오류 메시지가 표시되거나, 로딩이 끝났는데 질문에 AI 답변이 붙지 않았다. 이후 프론트 개발 서버 로그에는 `Failed to proxy http://localhost:8080/api/ai-review/sessions/{id}/messages Error: socket hang up`가 출력됐다.

## 원인

로컬 개발 프론트는 기본 API base URL이 `/api`라서 Next.js rewrite proxy를 통해 백엔드(`http://localhost:8080/api`)로 요청을 넘긴다. AI 복습처럼 동기 HTTP 요청이 길어질 수 있는 경로에서는 Next dev proxy가 백엔드 연결을 끝까지 유지하지 못하고 `ECONNRESET`/`socket hang up`으로 끊을 수 있었다.

또한 백엔드 `application.yml`의 AI read timeout 기본값이 150초였다. 루트 `.env`가 실행 프로세스에 주입되지 않는 방식으로 백엔드를 켜면 Python/Ollama AI 응답을 너무 오래 기다려 proxy hang up 가능성이 커졌다.

관련 파일:

- frontend/.env.local:1
- frontend/src/lib/ai-review.ts:9
- frontend/src/app/tests/results/[id]/review/page.tsx:57
- backend/src/main/resources/application.yml:77
- backend/src/main/resources/application.yml:89
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:371

## 해결 방법

로컬 프론트가 Next rewrite proxy를 거치지 않고 백엔드에 직접 요청하도록 `frontend/.env.local`에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api`를 추가했다. 이 설정은 프론트 dev 서버 재시작 후 적용된다.

백엔드의 Python/Ollama AI 기본 생성량을 120 tokens로 줄이고, read timeout 기본값을 12초로 낮췄다. `.env`가 주입되지 않아도 느린 로컬 AI를 오래 기다리지 않고 규칙 기반 fallback으로 돌아오게 하기 위해서다.

프론트는 요청 자체를 너무 빨리 abort하지 않도록 AI 요청 timeout을 길게 두고, 10초가 지나면 안내 배너만 띄운다. 백엔드는 AI 생성 중 런타임 예외가 발생해도 전체 복습 요청이 500으로 죽지 않고 fallback 답변으로 이어지도록 방어했다.

## 재발 방지·메모

로컬 LLM은 CPU/RAM 상태와 모델 warm-up에 따라 10초대를 넘길 수 있다. 짧은 사용자 대기 시간을 목표로 하더라도 Next dev proxy를 긴 AI 요청 경로에 두면 `socket hang up`이 재발할 수 있다.

로컬 개발에서는 프론트가 백엔드에 직접 붙도록 `NEXT_PUBLIC_API_BASE_URL`을 명시하고, 긴 동기 AI 호출은 짧은 provider timeout과 fallback을 기본으로 둔다. 고품질 긴 답변이 필요하면 동기 HTTP 요청 대신 백그라운드 작업/폴링 구조로 분리한다.

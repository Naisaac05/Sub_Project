# AI review Docker prod config blocked candidate E2E

- 발생 일시: 2026-06-25
- 영역: docker / backend / AI review
- 심각도: medium

## 증상

AI 승인 카드 새로고침 E2E를 위해 Docker Compose로 backend를 띄운 뒤 내부 후보 캡처 API와 관리자 후보 목록 API를 호출하면 연결이 닫혔다. 재빌드 전에는 구버전 이미지가 떠서 내부 캡처가 403을 반환했고, 재빌드 후에는 backend 컨테이너가 부팅 직후 종료되어 API 요청이 처리되지 않았다.

## 원인

backend Dockerfile이 실행 프로파일을 prod로 강제한다. 관련 위치: `backend/Dockerfile:30`.

현재 prod validator는 prod 프로파일에서 `app.ai-review.python.service-token`이 비어 있거나 후보 경로가 JSONL이면 애플리케이션을 실패시킨다. 관련 위치: `backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java:35`, `backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java:49`, `backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java:50`.

반면 `docker-compose.yml`의 backend 서비스는 `.env`를 읽지만 compose environment 블록에는 AI review prod 필수 값이 전달되지 않는다. 관련 위치: `docker-compose.yml:41`, `docker-compose.yml:45`.

## 해결 방법

E2E 확인은 기존 `devmatch-backend` 서비스를 멈춘 뒤, 같은 이미지로 테스트용 one-off backend를 실행하면서 prod 필수 환경변수를 직접 주는 방식으로 우회했다.

```powershell
docker compose stop backend
docker compose run -d --name devmatch-backend-smoke --service-ports `
  -e AI_REVIEW_SERVICE_TOKEN=codex-smoke-token `
  -e AI_REVIEW_CANDIDATES_PATH=/tmp/course-candidates `
  -e AI_REVIEW_AUTO_CANDIDATES_PATH=/tmp/auto-candidates `
  backend
```

이후 내부 캡처 API에는 `X-AI-Service-Token: codex-smoke-token` 헤더를 붙여 호출했고, 관리자 로그인 후 `/api/admin/ai-review/candidates/v2` 목록에서 방금 캡처한 후보가 조회되는 것을 확인했다.

## 재발 방지 / 메모

Docker Compose로 prod 프로파일 backend를 실행할 때는 AI review prod 필수 환경변수를 compose `environment`에 명시하거나 `.env`와 compose 매핑을 맞춰야 한다. 개발용 compose라면 Dockerfile의 prod 하드코딩 대신 `SPRING_PROFILES_ACTIVE`를 외부에서 주입할 수 있게 바꾸는 것이 안전하다.

테스트 중 생성된 후보 ID 예시: `auto-codex-smoke-20260625144200`. 목록 조회 결과 `workflowPhase=DRAFTED`, `status=PENDING`으로 관리자 AI 승인 카드 새로고침 API에 노출됐다.

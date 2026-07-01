# 운영 AI 카드 경로와 후보 수집 URL 연결 누락

- 발생 일시: 2026-07-01
- 영역: backend / infra / docker
- 심각도: high

## 증상

운영 관리자 화면의 모든 후보 탭이 0건으로 보였고, Ollama가 생성한 답변도 `PENDING` 후보로 쌓이지 않았다.

## 원인

운영 백엔드 컨테이너에 `concepts_v2` 카드 bind와 컨테이너 내부 카드 경로 설정이 없어 발행된 카드를 읽을 수 없었다. 동시에 AI 후보 수집기는 기본값인 `http://localhost:8080`으로 전송했는데, 컨테이너의 localhost는 백엔드가 아니라 AI 컨테이너 자신이므로 후보 저장 요청이 도달하지 않았다.

## 해결 방법

하나의 호스트 카드 디렉터리를 백엔드와 AI 컨테이너에 각각 bind하고, 백엔드의 카드 경로를 `/app/ai/knowledge/concepts_v2`로 지정했다 (`docker-compose.prod.yml:13`, `docker-compose.prod.yml:16`, `docker-compose.prod.yml:37`). AI 후보 수집 URL은 Docker 서비스명 기반 백엔드 URL로 지정했다 (`docker-compose.prod.yml:34`). 수집 실패 로그는 토큰이나 쿼리를 노출하지 않도록 정제된 URL과 상태/예외 유형만 남긴다 (`ai/app/knowledge/candidate_sink.py:52`, `ai/app/knowledge/candidate_sink.py:59`, `ai/app/knowledge/candidate_sink.py:66`).

## 재발 방지 / 메모

- EC2의 `./ai/app/knowledge/concepts_v2` bind 원본은 컨테이너 재생성 후에도 유지해야 한다. 배포 아카이브나 정리 작업에서 이 디렉터리를 누락·삭제하지 않는다.
- 컨테이너 사이 통신에는 localhost를 사용하지 않는다. 후보 수집 URL은 `http://backend:8080/...`처럼 Compose 서비스명을 사용한다.
- 배포 후 `docs/deploy-runbook.md`의 AI 카드·후보 연결 확인 명령으로 두 컨테이너의 카드 경로와 후보 수집 URL, 실패 로그를 함께 확인한다.

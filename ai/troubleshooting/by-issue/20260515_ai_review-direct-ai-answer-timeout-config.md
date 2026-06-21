---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review direct AI answer timeout config 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review direct AI answer timeout config

- 발생 날짜: 2026-05-15
- 영역: backend / frontend
- 심각도: medium

## 증상

스마트 개념 복습에서 사용자가 실제 AI 답변을 원해도, 로컬 Python AI나 Ollama 응답이 12초를 넘으면 백엔드가 빠르게 포기하고 규칙 기반 대체 문구를 반환했다. 또한 프론트 개발 서버가 `/api` Next rewrite 프록시를 통해 백엔드로 요청을 보내 긴 AI 응답 대기 중 연결이 끊길 여지가 있었다.

## 원인

백엔드의 Python AI와 Ollama HTTP read timeout이 코드상 12초로 고정되어 있어, 로컬 모델이 정상 동작하더라도 느린 추론은 실패로 처리됐다. 프론트 API 클라이언트도 항상 `/api` 상대 경로를 사용해 Next 개발 서버 프록시를 거쳤고, 긴 요청에서는 백엔드 직접 호출보다 불안정했다.

## 해결 방법

Python AI와 Ollama read timeout을 환경변수로 조정 가능하게 변경했다.

- `backend/src/main/java/com/devmatch/config/AiReviewProperties.java:60`
- `backend/src/main/java/com/devmatch/config/AiReviewProperties.java:72`
- `backend/src/main/resources/application.yml:80`
- `backend/src/main/resources/application.yml:89`
- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:132`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:207`

프론트 API 클라이언트는 `NEXT_PUBLIC_API_BASE_URL`이 있으면 해당 주소를 사용하게 변경해, 개발 중 `http://localhost:8080/api`로 백엔드를 직접 호출할 수 있게 했다.

- `frontend/src/lib/api.ts:5`
- `frontend/src/lib/api.ts:14`
- `frontend/src/lib/api.ts:82`

## 재발 방지 / 메모

실제 로컬 AI 답변을 기다릴 때는 백엔드 실행 전에 `PYTHON_AI_READ_TIMEOUT_SECONDS`와 `OLLAMA_READ_TIMEOUT_SECONDS`를 75초 이상으로 올리고, 프론트는 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api`로 재시작한다. 기본값 12초는 빠른 실패/대체 답변 모드로 유지했다.

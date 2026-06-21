---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review Next proxy timeout regression 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review Next proxy timeout regression

- 발생 일시: 2026-05-15
- 영역: backend / frontend / ai
- 심각도: medium

## 증상

스마트 개념 복습에서 질문을 제출하면 "백엔드가 복습 답변을 처리하는 중 오류가 났습니다. 백엔드 터미널의 스택 트레이스를 확인해주세요."가 표시됐다. Python AI와 Ollama가 켜져 있고 직접 API 호출은 성공했지만, 브라우저에서 `/api` 경로를 통해 호출할 때 500처럼 보이는 오류가 재발했다.

## 원인

프론트엔드는 Next.js dev server의 rewrite(`/api/*` -> `http://localhost:8080/api/*`)를 통해 백엔드에 요청한다. 로컬 Ollama 응답이 느릴 때 백엔드가 최대 90초까지 Python AI/Ollama 응답을 기다리도록 설정되어 있어, Next dev proxy가 긴 요청을 버티지 못하고 `socket hang up`/500 형태로 실패할 수 있었다. 실제 점검 중 Python AI 생성 요청 하나가 약 56초 걸려 같은 조건을 재현했다.

## 해결 방법

백엔드의 Python AI, Ollama RestClient read timeout을 12초로 줄였다. 로컬 AI가 짧은 시간 안에 응답하지 못하면 백엔드가 provider fallback/규칙 기반 fallback으로 빠르게 돌아와 Next proxy 장기 대기 오류를 피한다.

- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:24`
- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:133`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:24`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:208`

## 재발 방지 / 메모

개발 환경에서 프론트가 Next rewrite proxy를 거치는 동안 동기 AI 호출을 길게 유지하면 500처럼 보이는 proxy 오류가 반복될 수 있다. 로컬 LLM 호출은 짧은 timeout과 fallback을 기본값으로 두고, 긴 고품질 답변이 필요하면 동기 HTTP 요청이 아니라 백그라운드 작업/폴링 구조로 분리한다.

# AI review misleading backend 500 message

- 발생 일시: 2026-05-15
- 영역: backend / frontend / ai
- 심각도: medium

## 증상

스마트 개념 복습에서 질문을 제출하면 화면에 "백엔드가 AI 응답을 받는 중 오류가 났습니다. Python AI와 Ollama가 모두 켜져 있는지 확인해주세요."가 반복 표시됐다. 실제 점검에서는 Python AI `http://127.0.0.1:8001/health`, Ollama `http://127.0.0.1:11434/api/tags`, Python AI 생성 요청이 모두 정상 응답했다.

## 원인

프론트엔드가 모든 500 응답을 Python AI/Ollama 기동 문제로 단정하는 문구로 치환했다. 또한 백엔드의 Python/Ollama AI 클라이언트가 호출 실패, 빈 응답, 한국어가 아닌 응답을 `Optional.empty()`로 조용히 넘겨 실제 실패 원인이 백엔드 로그에 남지 않았다. 이 때문에 AI 서버가 켜져 있어도 DB, 세션, 저장, provider fallback 문제까지 같은 안내로 보였다.

## 해결 방법

백엔드 AI 클라이언트에 실패 원인을 남기는 warn 로그를 추가했다.

- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:22`
- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:112`
- `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:117`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:22`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:183`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:188`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:193`

프론트엔드 500 메시지는 서버가 내려준 메시지가 있으면 그대로 보여주고, 없을 때도 Python AI/Ollama만 원인으로 단정하지 않도록 바꿨다.

- `frontend/src/app/tests/results/[id]/review/page.tsx:60`
- `frontend/src/app/tests/results/[id]/review/page.tsx:68`

## 재발 방지 / 메모

로컬 AI 기능에서 `Optional.empty()` fallback을 사용할 때는 최소한 provider, baseUrl, model, URI, 예외 메시지를 로그에 남긴다. 프론트엔드에서는 HTTP 500을 특정 인프라 원인으로 단정하지 말고 백엔드 메시지 또는 백엔드 로그 확인 안내를 우선 보여준다.

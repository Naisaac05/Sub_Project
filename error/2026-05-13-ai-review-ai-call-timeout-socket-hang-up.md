# AI review request failed while slow local AI continued

- 발생 일시: 2026-05-13
- 영역: backend / frontend / ai
- 심각도: medium

## 증상
AI 복습 화면에서 질문에 답하면 오래 로딩된 뒤 "백엔드가 AI 응답을 받는 중 오류가 났습니다. Python AI와 Ollama가 모두 켜져 있는지 확인해주세요."가 표시됐다. Next.js dev server 로그에는 `Failed to proxy http://localhost:8080/api/ai-review/sessions/{id}/messages`와 `socket hang up`, `ECONNRESET`이 출력됐다. 새로고침 후에는 늦게 생성된 답변이 보이는 경우가 있었다.

## 원인
백엔드가 Python AI 또는 Ollama에 HTTP 요청을 보낼 때 명시적인 connect/read timeout을 두지 않았다. CPU-only 노트북에서 로컬 Qwen 응답이 늦어지면 프론트/Next 프록시 요청은 실패 처리되지만, 백엔드는 뒤늦게 AI 응답을 저장할 수 있어 "오류가 떴는데 새로고침하면 답변이 있음" 상태가 됐다.

관련 파일:
- backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:116
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:191

## 해결 방법
Python AI와 Ollama RestClient 호출에 `SimpleClientHttpRequestFactory` 기반 timeout을 추가했다. 연결은 2초, 응답 대기는 12초로 제한하고, timeout/연결 오류는 기존 `Optional.empty()` 흐름으로 떨어져 규칙 기반 fallback 질문/답변을 반환하게 했다.

## 재발 방지 / 메모
로컬 LLM 호출은 CPU 상태에 따라 지연이 크게 흔들리므로 사용자 요청-응답 경로에서는 반드시 짧은 timeout과 fallback을 둔다. 더 긴 AI 답변이 필요하면 동기 요청이 아니라 백그라운드 작업/폴링 구조로 분리하는 편이 안전하다.

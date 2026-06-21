---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review qwen3 timeout double provider wait 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review qwen3 timeout double provider wait

- 발생 날짜: 2026-05-15
- 영역: backend / frontend / ai
- 심각도: medium

## 증상

qwen3 4B 양자화 모델로 스마트 개념 복습 질문을 보냈을 때 프론트에 `로컬 AI 응답이 지연되어 시간을 초과했습니다` 메시지가 표시됐다.

## 원인

프론트 AI 복습 요청 제한은 90초였고, Python AI 내부 Ollama 요청 제한은 75초였다. qwen3 4B는 기본 thinking 모드에서 `response` 대신 `thinking` 필드에 토큰을 길게 쓰며, 최종 `response`가 빈 문자열로 끝날 수 있었다. 이 경우 Python AI가 빈 응답을 500으로 처리했고, 백엔드는 규칙 기반 fallback 답변을 저장했다. 또한 `AUTO` provider 상태에서 Python AI가 선택된 뒤 실패하면 Ollama 직접 호출까지 이어질 수 있어 전체 대기 시간이 프론트 제한을 넘었다.

## 해결 방법

qwen3 4B 양자화 모델을 기다릴 수 있도록 프론트 AI 복습 요청 제한을 180초로 늘렸다.

- `frontend/src/lib/ai-review.ts:9`

백엔드 Python/Ollama read timeout 기본값을 150초로 늘렸다.

- `backend/src/main/resources/application.yml:80`
- `backend/src/main/resources/application.yml:89`
- `backend/src/main/java/com/devmatch/config/AiReviewProperties.java:23`
- `backend/src/main/java/com/devmatch/config/AiReviewProperties.java:26`

Python AI 내부 Ollama 요청 제한도 환경변수 `OLLAMA_REQUEST_TIMEOUT_SECONDS`로 조정 가능하게 하고 기본값을 150초로 늘렸다.

- `ai/app/service.py:9`
- `ai/app/service.py:146`

qwen3 thinking 모드를 끄기 위해 Ollama `/api/generate` 요청에 `think: false`를 추가했다. 답변 생성량도 복습 UI에 맞게 줄여 과도한 CPU 시간을 줄였다.

- `ai/app/service.py:127`
- `ai/app/service.py:158`
- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:174`
- `backend/src/main/resources/application.yml:77`
- `backend/src/main/resources/application.yml:78`
- `backend/src/main/resources/application.yml:79`

`AUTO`에서 Python이 선택된 경우 Ollama 직접 fallback까지 이중 대기하지 않도록 Ollama 사용 조건을 실제 선택 provider가 `OLLAMA`일 때로 제한했다.

- `backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:156`

## 재발 방지 / 메모

qwen3 4B는 16GB RAM CPU 노트북에서 첫 요청이 특히 느릴 수 있다. 실제 AI 답변을 우선하면 긴 timeout이 필요하고, 응답성을 우선하면 `AI_REVIEW_PROVIDER=OLLAMA` 또는 `PYTHON` 중 하나만 명시해 중복 provider 대기를 피한다.

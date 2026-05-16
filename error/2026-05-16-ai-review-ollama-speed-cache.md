# AI review Ollama speed cache

- 발생 일자: 2026-05-16
- 영역: backend / ai
- 심각도: medium

## 증상

OpenAI를 쓰지 않고 로컬 Ollama Qwen 모델만 쓰는 환경에서 AI 복습 응답이 느리거나 fallback 답변으로 떨어졌다. 작은 모델로 바꾸면 속도는 개선되지만 정답 설명 품질이 떨어질 수 있어, Qwen 4B를 유지하면서 속도를 줄이는 개선이 필요했다.

## 원인

Ollama 프롬프트가 문제, 선택지 전체, 정답, 선택 답변, 학습자 답변을 장문 지시문과 함께 전달해 4코어 CPU 노트북에서 처리량이 낮았다. 또한 동일한 질문/답변 조합을 다시 요청해도 매번 Ollama를 호출해 재시도나 반복 질문에서 같은 비용을 냈다.

관련 파일:

- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:47
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:72
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:96
- backend/src/main/resources/application.yml:86
- backend/src/main/resources/application.yml:87

## 해결 방법

Qwen 4B 모델은 유지하되 Ollama 프롬프트를 짧은 grounded prompt로 바꿨다. 백엔드가 알고 있는 정답/선택 답변/규칙 평가를 authoritative facts로 먼저 주입하고, 모델은 한국어 피드백 문장만 생성하게 했다. `OllamaAiReviewClient`에 128개 bounded LRU 메모리 캐시를 추가해 동일한 모델+프롬프트 조합은 Ollama를 다시 호출하지 않도록 했다. 4코어 CPU 기본값으로 `OLLAMA_MAX_TOKENS=80`, `OLLAMA_NUM_CTX=256`, `OLLAMA_NUM_THREAD=4`를 사용한다.

수정 파일:

- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:27
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:32
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:115
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:153
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:180
- backend/src/main/resources/application.yml:86
- backend/src/main/resources/application.yml:87
- backend/src/main/java/com/devmatch/config/AiReviewProperties.java:26
- docs/superpowers/specs/2026-05-16-ai-review-ollama-speed-design.md:1
- docs/superpowers/plans/2026-05-16-ai-review-ollama-speed.md:1

## 재발 방지 / 메모

정확도와 속도를 동시에 원하면 모델 크기보다 프롬프트 업무량을 먼저 줄인다. 정답 판정과 핵심 근거는 백엔드/DB가 책임지고, LLM은 짧은 설명 생성만 맡기는 구조를 유지한다. 서버 재시작 시 메모리 캐시는 사라지므로 장기 캐시가 필요해지면 Redis나 DB 캐시를 별도 설계한다.

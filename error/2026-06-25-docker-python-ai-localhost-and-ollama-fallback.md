# Docker Python AI localhost and Ollama fallback

- 발생 일시: 2026-06-25
- 영역: backend / docker / AI review
- 심각도: medium

## 증상

AI 복습 화면에서 `hashmap이 무엇인가요?` 같은 자유 질문을 했을 때 approved RAG/LLM 답변 대신 “로컬 AI 응답이 느리거나 실패해서 자세한 답변 대신 핵심 기준만...” fallback 문구가 표시됐다.

## 원인

Docker 백엔드 컨테이너의 `PYTHON_AI_BASE_URL`이 `http://localhost:8001`로 잡혀 있었다. 컨테이너 내부의 `localhost`는 사용자 PC가 아니라 백엔드 컨테이너 자신이므로 Python AI 서버에 연결하지 못했고, 로그에 `/api/review/free-question` `Connection refused`가 반복됐다.

또한 `RuleBasedAiReviewService`의 생성 흐름은 Python 호출과 Ollama 호출을 하나의 `try` 블록으로 감싸고 있었다. Python 클라이언트가 retry 후 `RuntimeException`을 던지면 같은 요청에서 Ollama fallback을 시도하지 못하고 즉시 빈 결과로 돌아가 최종 fallback 템플릿이 표시됐다.

관련 파일:
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:386
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:408
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:461
- docker-compose.yml:55
- docker-compose.yml:56

## 해결 방법

Docker Compose의 백엔드 환경값을 로컬 PC 접근용 주소로 고정했다.

- `PYTHON_AI_BASE_URL=http://host.docker.internal:8001`
- `OLLAMA_BASE_URL=http://host.docker.internal:11434`

`RuleBasedAiReviewService`에서는 first question, follow-up, free question 생성 모두 Python 호출과 Ollama 호출의 예외 경계를 분리했다. Python이 예외를 던지거나 빈 결과를 반환하면 Ollama를 이어서 시도하고, Ollama까지 실패할 때만 기존 fallback 템플릿으로 내려간다.

검증:
- backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java:111
- backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java:138
- backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java:184
- `.\gradlew.bat test --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest`
- Docker 컨테이너 env 확인: `PYTHON_AI_BASE_URL=http://host.docker.internal:8001`, `OLLAMA_BASE_URL=http://host.docker.internal:11434`

## 재발 방지 / 메모

컨테이너에서 호스트 PC의 서비스를 호출할 때 `localhost`를 쓰면 안 된다. Windows/macOS Docker Desktop 기준으로는 `host.docker.internal`을 사용한다.

AI provider fallback은 한 provider의 예외가 전체 흐름을 중단하지 않도록 provider별 예외 경계를 분리해야 한다. 특히 retry helper가 최종 예외를 다시 던지는 경우 다음 provider로 넘어가는 테스트가 필요하다.

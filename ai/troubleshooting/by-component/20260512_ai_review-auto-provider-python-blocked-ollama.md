---
type: troubleshooting
category: ollama
status: active
updated: 2026-06-18
description: "AI review AUTO provider selected stopped Python before Ollama fallback 발생 원인 ..."

---

# AI review AUTO provider selected stopped Python before Ollama fallback

- 발생 일시: 2026-05-12
- 영역: backend / ai
- 심각도: medium

## 증상
스마트 복습의 자유 질문 응답에서 "지금은 로컬 AI 응답을 사용할 수 없어 자세한 자유 답변은 제한되지만..." 안내가 반환됐다. 사용자는 Ollama가 실행 중이라고 보았지만 앱은 로컬 AI 응답을 받지 못했다.

## 원인
`app.ai-review.provider` 기본값이 `AUTO`이고 Python AI 설정도 기본 활성화되어 있어, 백엔드 provider 선택기가 먼저 Python provider를 선택했다. 이때 Python AI 서버(`http://localhost:8001`)가 꺼져 있으면 `PythonAiReviewClient`가 실패 후 `Optional.empty()`를 반환하지만, `OllamaAiReviewClient`는 다시 `selectProvider()`가 `OLLAMA`가 아니라고 판단해 실행되지 않았다. 결과적으로 Ollama 프로세스와 `http://localhost:11434` 모델이 정상이어도 복습 서비스는 규칙 기반 fallback 문구를 보여줬다.

관련 파일:
- backend/src/main/resources/application.yml:66
- backend/src/main/resources/application.yml:72
- backend/src/main/java/com/devmatch/service/ai/AiReviewProviderSelector.java:23
- backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:90
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:140

## 해결 방법
`OllamaAiReviewClient`의 실행 조건을 `AUTO` 모드에서는 Ollama 설정이 유효하면 허용하도록 변경했다. 이제 Python AI 서버가 꺼져 있거나 Python 호출이 실패해 빈 응답이 반환되어도, `RuleBasedAiReviewService`의 기존 순서대로 Ollama 호출을 다시 시도할 수 있다.

수정 파일:
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:22
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:56
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:110
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:140

확인:
- `http://127.0.0.1:11434/api/tags`에서 `qwen2.5:1.5b` 모델 존재를 확인했다.
- `http://127.0.0.1:11434/api/generate` 호출이 응답을 반환하는 것을 확인했다.
- 사용자가 Python AI 서버를 다시 켠 뒤 `http://127.0.0.1:8001/health`가 `{"status":"ok"}`를 반환하는 것을 확인했다.
- `http://127.0.0.1:8001/api/review/free-question`도 응답을 반환하는 것을 확인했다.

## 재발 방지 / 메모
`AUTO` provider 선택은 "설정이 존재하는지"만 보지 말고 실제 가용성 실패 시 다음 로컬 provider로 넘어갈 수 있어야 한다. 장기적으로는 Python/Ollama 클라이언트 실패 로그를 남기고, 헬스 체크 기반 provider 선택 또는 명시적인 fallback chain 테스트를 추가하는 편이 좋다.

`./gradlew.bat compileJava --offline` 검증은 Gradle 캐시의 `spring-boot-autoconfigure-3.4.4.jar` 접근 권한 문제로 실패했다. 이 실패는 이번 AI provider 원인과 별개이며, 빌드 검증을 다시 하려면 Gradle 캐시 파일 잠금/권한 상태를 먼저 정리해야 한다.

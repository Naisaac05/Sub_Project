---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review Micrometer classpath and Java warning cleanup 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review Micrometer classpath and Java warning cleanup

- 발생 일시: 2026-05-27
- 영역: backend
- 심각도: low

## 증상

VS Code Problems 패널에서 `AiReviewMetricSink.java`와 `AiReviewMetricSinkTest.java`의 `io.micrometer.core` import, `MeterRegistry`, `Counter`, `Timer`, `SimpleMeterRegistry` 타입을 해석하지 못했다. 같은 패널에 streaming service unchecked cast, unused import/local variable/private method warning도 함께 표시됐다.

## 원인

`spring-boot-starter-actuator`가 Micrometer를 전이 의존성으로 제공하지만 IDE classpath가 변경된 Gradle build를 즉시 반영하지 못해 Micrometer 타입이 누락된 것처럼 표시됐다. 또한 `AiReviewStreamingService`는 SSE `response` payload를 raw `Map`에서 `Map<String, Object>`로 직접 cast하고 있었고, streaming 테스트와 rule-based service에는 더 이상 쓰이지 않는 import/helper가 남아 있었다.

## 해결 방법

- `backend/build.gradle`: Micrometer IDE/compile classpath를 명확히 하기 위해 `implementation 'io.micrometer:micrometer-core'`를 명시 추가했다.
- `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java`: raw map unchecked cast를 `responseMetadataFrom(Object)` helper로 대체해 `Map<?, ?>`를 안전하게 `Map<String, Object>`로 복사했다.
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`: 사용되지 않는 private helper 4개를 제거했다.
- `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java`: unused import, unused local variable, unused test emitter helper를 제거하고 generic varargs warning이 나던 `thenReturn(Optional.empty(), Optional.of(...))`를 chained `thenReturn`으로 바꿨다.

검증:

```powershell
.\gradlew.bat test --tests "*AiReviewMetricSinkTest" --tests "*AiReviewStreamingServiceTest"
```

결과: `BUILD SUCCESSFUL`.

## 재발 방지 / 메모

Gradle 의존성을 바꾼 뒤 IDE Problems 패널이 계속 이전 classpath를 보이면 Gradle project reload가 필요하다. Actuator 전이 의존성에 기대도 빌드는 되지만, IDE가 특정 Micrometer 타입을 직접 쓰는 파일을 늦게 해석하는 경우가 있어 명시 의존성이 더 안정적이다.

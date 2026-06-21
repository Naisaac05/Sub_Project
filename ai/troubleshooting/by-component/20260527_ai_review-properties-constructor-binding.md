---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AiReviewProperties constructor binding bootRun failure 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AiReviewProperties constructor binding bootRun failure

- 발생 일시: 2026-05-27
- 영역: backend
- 심각도: high

## 증상

`./gradlew.bat bootRun` 실행 시 Spring Boot 애플리케이션이 시작 도중 종료되고 Gradle에는 `Process 'command 'C:\Program Files\Java\jdk-17\bin\java.exe'' finished with non-zero exit value 1`만 표시됐다.

실제 stacktrace의 핵심 원인은 `com.devmatch.config.AiReviewProperties.<init>()` 기본 생성자를 찾을 수 없다는 `NoSuchMethodException`이었다.

## 원인

`AiReviewProperties`는 `@ConfigurationProperties` record인데, canonical constructor 외에 테스트 편의를 위한 보조 생성자가 추가되어 있었다. Spring Boot의 생성자 바인딩 추론은 생성자가 하나일 때는 자동으로 동작하지만, 생성자가 여러 개이면 바인딩 생성자를 명확히 선택하지 못해 JavaBean 방식 인스턴스화를 시도했다.

그 결과 기본 생성자가 없는 record를 기본 생성자로 만들려다가 애플리케이션 컨텍스트 초기화가 실패했다.

관련 파일:

- backend/src/main/java/com/devmatch/config/AiReviewProperties.java:7
- backend/src/main/java/com/devmatch/config/AiReviewProperties.java:23
- backend/src/test/java/com/devmatch/config/AiReviewPropertiesTest.java:84

## 해결 방법

canonical compact constructor에 `@ConstructorBinding`을 명시해 Spring Boot가 설정 바인딩에 사용할 생성자를 확정하도록 했다.

회귀 방지를 위해 `@ConfigurationPropertiesScan`으로 실제 설정 클래스 등록 방식을 재현하는 테스트를 추가했다. 수정 전에는 이 테스트가 동일한 빈 생성 실패로 실패했고, 수정 후 통과했다.

검증:

- `./gradlew.bat test --tests com.devmatch.config.AiReviewPropertiesTest`
- `./gradlew.bat test`
- `./gradlew.bat bootRun --args=--server.port=18080`로 `Started DevMatchApplication` 확인

## 재발 방지 / 메모

record 기반 `@ConfigurationProperties`에 생성자를 추가할 때는 canonical constructor에 `@ConstructorBinding`을 명시해야 한다. 보조 생성자가 필요한 경우에는 반드시 실제 Spring 바인딩 경로를 검증하는 `ApplicationContextRunner` 테스트를 함께 둔다.

기본 포트 `8080`으로는 추가 환경 문제가 남아 있었다. PID `25792`의 `C:\Program Files\Java\jdk-17\bin\java.exe`가 이미 8080을 점유해 `Web server failed to start. Port 8080 was already in use.`가 발생했다. 코드 수정 검증은 임시 포트 `18080`에서 완료했다.

---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review reindexer Spring constructor injection failure 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review reindexer Spring constructor injection failure

- 발생 날짜: 2026-05-20
- 범위: backend
- 심각도: medium

## 증상

`.\gradlew.bat bootRun` 실행 중 `LoggingAiReviewKnowledgeReindexer` Bean 생성에 실패하면서 backend가 기동되지 않았다. 로그에는 `Failed to instantiate [com.devmatch.service.LoggingAiReviewKnowledgeReindexer]: No default constructor found`와 `NoSuchMethodException: com.devmatch.service.LoggingAiReviewKnowledgeReindexer.<init>()`가 출력됐다.

## 원인

`LoggingAiReviewKnowledgeReindexer`에 운영용 public 생성자와 테스트용 package-private 생성자가 함께 존재했지만, 운영용 생성자에 `@Autowired`가 없었다. Spring이 명시적인 생성자 선택 힌트를 받지 못해 기본 생성자 기반 인스턴스 생성을 시도했고, 기본 생성자가 없어 ApplicationContext 초기화가 실패했다.

## 해결 방법

운영용 `@Value` 주입 생성자에 `@Autowired`를 추가해 Spring이 해당 생성자를 Bean 생성에 사용하도록 고정했다.

- `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java:48`

또한 `ApplicationContextRunner` 기반 회귀 테스트를 추가해 이 컴포넌트가 Spring context에서 생성되는지 검증했다.

- `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java:59`

## 재발 방지·메모

Spring component에 테스트 편의용 생성자를 추가할 때는 운영 생성자에 `@Autowired`를 명시하거나, 테스트 생성자를 별도 factory/helper로 분리한다. 단위 생성자 테스트만으로는 Spring의 실제 Bean 생성 경로를 검증하지 못하므로, 다중 생성자 component에는 context 생성 회귀 테스트를 함께 둔다.

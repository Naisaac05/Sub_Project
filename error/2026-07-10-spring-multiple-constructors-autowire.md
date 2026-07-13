# Spring service with multiple constructors lost automatic injection

- 발생 일시: 2026-07-10
- 영역: backend / test
- 심각도: medium

## 증상

전체 backend 테스트에서 MVC 컨텍스트를 사용하는 25개 테스트가 `AiReviewRateLimitService: No default constructor found`로 실패했다.

## 원인

운영 생성자와 테스트용 package-private 생성자가 함께 존재했지만 주입 생성자를 명시하지 않았다. Spring은 생성자가 하나일 때만 자동으로 선택하므로 기본 생성자를 찾다가 컨텍스트 생성에 실패했다.

## 해결 방법

`backend/src/main/java/com/devmatch/service/ai/AiReviewRateLimitService.java:37`의 운영 생성자에 `@Autowired`를 명시했다. 이후 전체 backend 235개 테스트와 JaCoCo 보고서 생성을 다시 실행해 통과를 확인했다.

## 재발 방지 / 메모

Spring bean에 테스트용 생성자를 추가할 때 운영 생성자를 `@Autowired`로 명시하거나 테스트용 객체 생성을 정적 팩터리로 분리한다. 대상 단위 테스트뿐 아니라 최소 한 번은 전체 애플리케이션 컨텍스트 테스트를 실행한다.

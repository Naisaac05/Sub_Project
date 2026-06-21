---
id: java-backend-controlleradvice
category: java-backend
difficulty: intermediate
version: course-candidate
last_updated: 2026-05-29
description: "@ControllerAdvice 핵심 개념 정리 및 동작 원리"

---

# @ControllerAdvice

## 핵심 설명
@ControllerAdvice는 여러 컨트롤러에서 공통으로 적용할 예외 처리나 바인딩 설정을 한곳에 모아두는 Spring MVC 어노테이션입니다. REST API에서는 전역 예외 응답 형식을 일관되게 만들 때 자주 사용합니다.

## 대표 해결
- @ExceptionHandler 메서드들을 @ControllerAdvice/@RestControllerAdvice 클래스에 모아 전역 예외 응답 형식을 한곳에서 통일한다.
- basePackages·annotations 속성으로 적용 대상 컨트롤러의 범위를 좁힐 수 있다.

## 흔한 오해
- @ControllerAdvice는 뷰 반환이 기본이므로, REST에서 응답 본문(JSON)을 직렬화하려면 @RestControllerAdvice(또는 @ResponseBody)를 사용한다.
- 같은 DispatcherServlet 컨텍스트의 컨트롤러 예외만 처리하며, 필터 단계에서 발생한 예외는 잡지 못한다.

## 평가 키워드
- 전역 예외 처리
- @ExceptionHandler
- @RestControllerAdvice
- 예외 응답 통일

## 검색 키워드
- @ControllerAdvice
- 전역 예외 처리
- source:java-backend:10

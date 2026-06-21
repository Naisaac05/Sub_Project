---
id: spring-fetch-join
category: spring-jpa
difficulty: intermediate
version: java17-springboot3
last_updated: 2026-05-16
description: "Fetch Join 핵심 개념 정리 및 동작 원리"

---

# Fetch Join

## 핵심 설명
fetch join은 JPQL에서 연관 엔티티를 한 번의 쿼리로 함께 로딩하도록 지시하는 방식이다.

## 대표 해결
- N+1 문제가 생기는 조회에서 필요한 연관 관계를 fetch join으로 가져온다.
- 컬렉션 fetch join은 페이징과 중복 row 문제를 함께 고려한다.

## 흔한 오해
- 모든 연관 관계를 항상 fetch join하는 것은 좋은 전략이 아니다.
- fetch join은 단순 join과 달리 영속성 컨텍스트에 연관 엔티티를 로딩한다.

## 평가 키워드
- JPQL
- 연관 엔티티
- 즉시 로딩
- N+1


---
id: java-equals
category: java
difficulty: beginner
version: java17
last_updated: 2026-05-16
---

# equals

## 핵심 설명
equals는 객체의 논리적 동등성을 비교하기 위해 재정의하는 메서드다.

## 대표 해결
- 값 객체는 핵심 필드를 기준으로 equals와 hashCode를 함께 재정의한다.
- 문자열 비교는 `==`가 아니라 `equals()`를 사용한다.

## 흔한 오해
- `==`는 객체 참조 비교이므로 문자열 내용 비교에 적합하지 않다.
- equals만 재정의하고 hashCode를 빼면 컬렉션에서 문제가 생길 수 있다.

## 평가 키워드
- 논리적 동등성
- 참조 비교
- hashCode
- 문자열 비교


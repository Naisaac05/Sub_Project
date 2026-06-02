---
id: java-hashcode
category: java
difficulty: beginner
version: java17
last_updated: 2026-06-01
---

# hashCode

## 핵심 설명
hashCode는 객체를 HashMap·HashSet 같은 해시 기반 컬렉션에서 빠르게 분류하고 탐색하기 위해 정수 해시값을 반환하는 메서드다.

## 대표 해결
- equals를 재정의하면 hashCode도 반드시 함께 재정의한다. equals로 같다고 판단되는 두 객체는 같은 hashCode를 가져야 한다.
- 핵심 필드를 기준으로 `Objects.hash(field1, field2)`를 사용해 구현한다.

## 흔한 오해
- hashCode가 객체의 메모리 주소를 그대로 리턴한다고 오해하기 쉽다. 실제 값은 JVM 구현에 따라 다르고, 재정의하면 메모리 주소와 무관한 값을 반환한다.
- hashCode가 같으면 두 객체가 반드시 equals true라고 오해하기 쉽다. 해시 충돌로 값이 같아도 equals는 다를 수 있다.

## 평가 키워드
- 해시값
- equals-hashCode 계약
- HashMap
- 해시 충돌

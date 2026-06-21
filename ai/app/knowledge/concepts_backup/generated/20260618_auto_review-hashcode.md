---
id: auto-review-hashcode
category: auto-review
difficulty: intermediate
version: admin-approved-candidate
last_updated: 2026-05-29
description: "hashCode 핵심 개념 정리 및 동작 원리"

---

# hashCode

## 핵심 설명
hashCode는 객체를 정수 하나로 요약하는 메서드로, HashMap·HashSet 같은 해시 기반 컬렉션이 객체를 어느 버킷에 넣을지 정하는 데 사용됩니다. 핵심은 equals/hashCode 계약입니다: equals로 같은 두 객체는 반드시 같은 hashCode를 반환해야 합니다.

## 대표 해결
- equals를 재정의하면 hashCode도 같은 핵심 필드를 기준으로 함께 재정의한다.
- IDE 자동 생성이나 Objects.hash(...)로 핵심 필드 기반 hashCode를 만든다.

## 흔한 오해
- hashCode가 같다고 해서 두 객체가 equals로 같은 것은 아니다(해시 충돌은 일어날 수 있다).
- equals만 재정의하고 hashCode를 빼면 HashMap·HashSet에서 객체를 못 찾는 문제가 생긴다.

## 평가 키워드
- 해시 코드
- equals/hashCode 계약
- HashMap 버킷
- 해시 충돌

## 사용 맥락
- 원 질문: hashCode가 무엇인가요?
- 해석된 질문: hashCode란 무엇인가?
- 승인자: admin-ui

## 주의할 점
- 승인된 후보 답변을 우선 사용하되, 더 구체적인 문제 맥락이 있으면 RAG 생성 답변에서 함께 고려한다.

## 검색 키워드
- hashCode
- auto-review
- source:auto-417b0ae92733

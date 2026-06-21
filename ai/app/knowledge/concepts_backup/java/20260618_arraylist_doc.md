---
id: java-arraylist
category: java
difficulty: beginner
version: java17
last_updated: 2026-06-01
description: "ArrayList 핵심 개념 정리 및 동작 원리"

---

# ArrayList

## 핵심 설명
ArrayList는 내부적으로 배열(Object[])에 요소를 저장하는 List 구현체로, 인덱스로 특정 위치 요소를 바로 꺼내는 임의 접근이 O(1)로 빠르다.

## 대표 해결
- 내부 배열이 가득 차면 더 큰 배열을 새로 만들어 기존 요소를 복사하며 자동으로 확장한다(보통 1.5배).
- 끝에 추가(add)는 분할상환 O(1)이지만, 중간 삽입·삭제는 뒤 요소를 밀거나 당겨야 해서 O(n)이다.

## 흔한 오해
- ArrayList가 LinkedList처럼 노드를 포인터로 연결한 구조라고 오해하기 쉽다. 실제로는 연속된 배열을 쓴다.
- 크기가 무제한이라 비용 없이 늘어난다고 오해하기 쉽다. 실제로는 확장할 때마다 더 큰 배열로 복사하는 비용이 든다.

## 평가 키워드
- 동적 배열
- 자동 확장
- 인덱스 임의 접근
- 분할상환 O(1)

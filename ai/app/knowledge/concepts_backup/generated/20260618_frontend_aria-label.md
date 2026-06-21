---
id: frontend-aria-label
category: frontend
difficulty: intermediate
version: course-candidate
last_updated: 2026-05-29
description: "aria-label 핵심 개념 정리 및 동작 원리"

---

# aria-label

## 핵심 설명
aria-label은 화면에 보이는 텍스트가 없거나 부족한 요소에 스크린리더가 읽을 수 있는 이름을 제공하는 HTML 접근성 속성입니다. 아이콘 버튼처럼 시각적으로만 의미가 전달되는 요소에 사용하면 보조기술 사용자가 버튼의 목적을 이해할 수 있습니다.

## 대표 해결
- 아이콘만 있는 버튼·링크처럼 보이는 텍스트가 없는 요소에 aria-label로 접근 가능한 이름(accessible name)을 부여한다.
- 이미 보이는 텍스트가 있으면 aria-label로 덮어쓰기보다 그 텍스트를 그대로 쓰거나 aria-labelledby로 연결한다.

## 흔한 오해
- aria-label은 시각 사용자에게는 보이지 않고, 스크린리더 등 보조기술에서만 읽힌다.
- 모든 요소에 aria-label을 붙이면 좋다는 것은 오해다. div·span 같은 비대화형 요소에는 기본적으로 노출되지 않고, 남용하면 보이는 텍스트와 불일치해 오히려 혼란을 준다.

## 평가 키워드
- 접근성
- 스크린리더
- 접근 가능한 이름
- aria-labelledby

## 검색 키워드
- aria-label
- 접근성
- source:frontend:10

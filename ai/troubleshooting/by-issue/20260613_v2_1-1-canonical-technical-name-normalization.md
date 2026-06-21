---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "v2.1.1 canonical 기술명 정규화 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# v2.1.1 canonical 기술명 정규화

- 발생 일시: 2026-06-13
- 영역: ai
- 심각도: medium

## 증상
`java-arraylist` 카드의 설명에서 canonical 기술명인 `ArrayList`와 `LinkedList`가 각각 `배열 목록`, `연결 목록`으로 번역되어 기술명 검색성과 설명 일관성이 낮아졌다.

## 원인
v2.1 패치에서 한국어 비율 제한을 맞추는 과정이 기술명과 일반 설명어를 구분하지 않아 canonical 기술명까지 번역했다.

## 해결 방법
`ai/app/knowledge/concepts_v2/java/java-arraylist.json:39`의 payload에서 `ArrayList`와 `LinkedList`를 복원했다. 두 대상 카드의 aliases와 boost keywords에서 문항 조각과 부분 기호를 제거하고, `python -m json.tool` 및 검색 평가를 통과한 뒤 승인했다.

## 재발 방지 / 메모
언어 비율 검사는 코드와 canonical 기술명을 제외한 자연어 설명을 대상으로 계산한다. 기술명 사전을 우선 적용하고, 승인 전 JSON 검증과 검색 성능 비교를 필수 게이트로 둔다.

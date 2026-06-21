---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI intent clarification and English topic routing 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI intent clarification and English topic routing

- 발생 일시: 2026-05-21
- 영역: backend / AI
- 심각도: medium

## 증상
기존 명확화 문구인 `다시 설명해줘`, `다시 설명해줘?`가 `clarification`이 아니라 `original_problem_reason`으로 분류되어 원문 맥락 의존 질문처럼 처리됐다. 영어 학습자 질문 `What is API?`도 주제가 `API`가 아니라 `What`으로 추출되고 `related_concept`로 분류됐다.

## 원인
`classify_free_question`에서 넓은 `is_context_dependent_question` 판정이 정확히 알려진 명확화 문구 집합보다 먼저 실행되어 `다시설명` 계열 문구가 원문 문제 사유 질문으로 흡수됐다. 또한 `extract_topic`은 한국어 조사 기반 정의 패턴만 지원해 영어 `what is <topic>` 형태를 정의 질문으로 인식하거나 `<topic>` 부분을 추출하지 못했다.

## 해결 방법
정확히 알려진 명확화 문구는 context-dependent 판정보다 먼저 `clarification`으로 반환하도록 순서를 조정했다: `ai/app/workflow/intent.py:53`. 영어 정의 질문용 `what is`, `what's`, `explain` 패턴을 추가해 intent와 topic 추출에 함께 사용했다: `ai/app/workflow/intent.py:38`, `ai/app/workflow/intent.py:73`, `ai/app/workflow/intent.py:148`. 회귀 테스트를 추가했다: `ai/tests/test_intent_routing.py:37`, `ai/tests/test_intent_routing.py:59`.

## 재발 방지 / 메모
문구 기반 라우팅은 넓은 휴리스틱보다 정확한 phrase allowlist를 먼저 적용해야 한다. 영어 초급 질문 패턴은 과도하게 일반화하지 말고 현재처럼 `what is <topic>` 수준의 작은 패턴으로 회귀 테스트와 함께 확장한다.

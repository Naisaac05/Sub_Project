# Payload ANSWER_REASON 오답 비교 검출 누락

- 발생 일시: 2026-06-14
- 영역: AI / RAG validation
- 심각도: medium

## 증상
ANSWER_REASON이 `O(1)`, `큐`, `인덱스 조회`처럼 오답 선택지의 핵심 용어를 직접 비교해도 `answer_wrong_comparison_missing`으로 판정됐다.

## 원인
검증기가 비교 표지어 또는 오답 선택지 전체 문장이 ANSWER_REASON에 포함된 경우만 비교 설명으로 인정했다. 긴 선택지의 핵심 용어만 정확히 언급한 설명은 누락됐다.

## 해결 방법
오답 선택지와 ANSWER_REASON의 토큰 교집합도 비교 근거로 인정하도록 검증기를 보완했다.

- 관련 파일: `ai/app/scripts/initialize_validation_policy_v212.py:154`
- 회귀 테스트: `ai/tests/test_initialize_validation_policy_v212.py:145`

## 재발 방지 / 메모
오답 비교 검증은 선택지 전체 문자열 일치보다 핵심 구별 용어가 설명에 포함됐는지를 우선 확인한다. 일반 단어로 인한 오탐 가능성은 향후 불용어 목록으로 제한한다.

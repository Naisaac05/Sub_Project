# Payload duplicate score 선택지 원문 오탐

- 발생 일시: 2026-06-14
- 영역: AI / RAG card validation
- 심각도: medium

## 증상

사실 검증과 설명 품질을 통과한 코스 균등 카드가 `duplicate_score_over_0_25`로 보류됐다. 특히 정답과 오답이 같은 기술 용어의 순서만 바꾼 문항에서 점수가 높았다.

## 원인

`ai/app/scripts/initialize_validation_policy_v212.py`의 중복 점수 계산이 설명 문장뿐 아니라 `WRONG_ANSWER_REASON.per_option.*.text`와 `ANSWER_REASON.key_points` 같은 원본 선택지·검색 라벨까지 비교했다. 선택지 간 의도적인 유사성을 설명 중복으로 잘못 판정했다.

## 해결 방법

중복 설명 점수 계산에서 원본 선택지 `text`와 검색용 `key_points`를 제외했다. 오답 이유와 정의·정답 설명 등 실제 생성 설명은 계속 검사한다.

관련 테스트: `ai/tests/test_initialize_validation_policy_v212.py`

## 재발 방지·메모

품질 점수는 작성 품질을 평가하는 필드와 원본 문제 데이터를 분리해 계산한다. 원본 선택지 유사성은 별도의 option similarity 지표로 다룬다.

# Approved 한국어 카드 정의 질문 의도 오분류

- 발생 일시: 2026-06-27
- 영역: AI RAG / intent routing
- 심각도: medium

## 증상

Approved 카드 `auto-review-`의 term인 `반응형`을 사용해 `반응형이란 무엇인가요?`라고 질문해도 Fast Path를 타지 않았다. 카드 직접 검색 점수는 8.5였지만 전체 워크플로우에서는 grounded fallback 경로로 이동하며 약 3.2초가 걸렸다.

## 원인

명백한 정의 질문을 빠르게 분류하는 규칙이 영문 기술 토큰을 필수 조건으로 사용했다. 순수 한국어 term인 `반응형`은 이 조건에서 탈락해 임베딩 분류기로 넘어갔고, 낮은 신뢰도로 `wrong_answer_explanation`으로 오분류됐다: `ai/app/workflow/embedding_intent.py:265`.

오분류된 intent는 Fast Path에서 `ANSWER_REASON` payload를 요청하게 만들었다. 해당 카드는 `CONCEPT_DEFINITION`만 승인되어 있어 검색 적중 후에도 `payload_not_approved`로 탈락했다.

## 해결 방법

정의형 문장에 영문 기술 토큰이 없으면 현재 approved 카드 전체에서 고유한 term/alias 앵커가 있는지 확인한다. 정확히 한 카드만 매칭될 때 해당 카드 term을 topic으로 사용해 `concept_definition`으로 확정한다: `ai/app/workflow/embedding_intent.py:276`, `ai/app/workflow/embedding_intent.py:291`.

Approved 한국어 카드 질문은 임베딩 호출 없이 분류되고, 일반 한국어 질문은 강제로 기술 intent가 되지 않는 회귀 테스트를 추가했다: `ai/tests/test_embedding_intent.py:30`, `ai/tests/test_embedding_intent.py:43`.

## 재발 방지 / 메모

- 기술 신호를 영문 정규식만으로 판단하지 않는다.
- 한국어 정의 질문의 규칙 분류는 approved 카드의 고유 앵커가 있을 때만 허용한다.
- 여러 카드가 동시에 매칭되면 임베딩 분류로 넘겨 잘못된 단일 카드 확정을 피한다.
- 수정 후 실제 워크플로우에서 `v2_approved_fast_path`, `model_used=v2-approved-payload`, `fallback_used=false`를 확인했다.

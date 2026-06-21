---
type: troubleshooting
category: evaluation
status: active
updated: 2026-06-18
description: "M3 전용으로 변경한 뒤 전체 AI 테스트 수집이 중단됐다"

---

# AI lightweight evaluator imported removed rule intent classifier

- 발생 일시: 2026-06-09
- 영역: ai
- 심각도: medium

## 증상

운영 질문 의도 분류를 BGE-M3 전용으로 변경한 뒤 전체 AI 테스트 수집이 중단됐다.
`tests/test_lightweight_evaluator.py`와 `tests/test_promotion_workflow.py`가
`scripts/evaluate_lightweight_rag.py`를 import할 때 존재하지 않는
`classify_free_question`을 불러오면서 `ImportError`가 발생했다.

## 원인

운영 workflow와 의도 단위 테스트는 BGE-M3 분류기로 교체했지만, 경량 평가 스크립트가
기존 규칙 분류 함수를 직접 import하고 있었다. 규칙 분류 함수 이름을 PoC 전용
`classify_free_question_rule_based`로 변경하면서 숨겨져 있던 평가 스크립트 결합이 드러났다.

## 해결 방법

- `ai/scripts/evaluate_lightweight_rag.py:15`에서 운영 BGE-M3 분류기
  `classify_free_question_with_embeddings`를 기본 분류기로 사용하도록 변경했다.
- `ai/scripts/evaluate_lightweight_rag.py:34`의 `evaluate_dataset`에 분류기 주입 지점을 추가했다.
- `ai/tests/test_lightweight_evaluator.py:18`에서 평가 메트릭 테스트가 실제 Ollama 속도에
  의존하지 않도록 기대 의도 결과를 주입했다.

## 재발 방지 / 메모

운영 의도 분류기를 변경할 때는 `ai/app/`뿐 아니라 `ai/scripts/`의 평가 도구 import도 함께
검색한다. 평가 도구의 기본값은 운영 분류기를 사용하되, 단위 테스트는 분류기 경계를 주입해
외부 Ollama 호출 없이 평가 집계 로직을 검증한다.

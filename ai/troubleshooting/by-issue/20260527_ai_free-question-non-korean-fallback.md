---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI free question fell back after model generation 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI free question fell back after model generation

- 발생 일시: 2026-05-27
- 영역: ai / backend
- 심각도: medium

## 증상

AI 복습 자유 질문에서 `train/validation/test 데이터가 어떤 의미인지 몰라`처럼 새 개념을 물으면 화면에는 실제 생성 답변 대신 `승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요` 형태의 템플릿 답변이 표시됐다.

직접 Python AI 서비스에 같은 요청을 보내면 `llm_call_avoided=false`, `model_used=qwen3:1.7b`, `route=fallback_template`, `fallback_used=true`로 나타났다. 즉 AI 호출 자체는 발생했지만, 생성 후 검증 단계에서 답변이 템플릿으로 교체되고 있었다.

## 원인

두 조건이 겹쳤다.

1. 자유 질문의 1차 모델 답변이 영어 또는 한글이 부족한 답변이면 `non_korean` 검증 실패 후 바로 템플릿 fallback으로 내려갔다.
2. fallback 모델이 한국어 답변을 생성해도, 질문 주제와 원문 문제의 단어가 겹치는 경우 `stale_original_context` 검증이 과하게 동작했다. `train/validation/test` 질문은 원문 문제와 같은 주제어를 설명해야 하므로 `train`, `데이터`, `학습` 같은 단어가 답변에 들어가는 것이 정상인데도 stale context로 판단될 수 있었다.

관련 파일:

- ai/app/workflow/nodes.py:89
- ai/app/workflow/nodes.py:321
- ai/tests/test_workflow_runner.py:680

## 해결 방법

`free-question`에서 1차 모델 답변이 한국어 검증을 통과하지 못하면 템플릿으로 떨어지기 전에 fallback 모델(`qwen3:4b-q4_K_M`)로 한 번 더 생성하도록 했다.

또한 `latest_question_only` 검증에서 주제 전체 문구가 답변에 정확히 포함되지 않더라도, `train`, `validation`, `test` 같은 핵심 topic token이 답변에 포함되면 원문 맥락 누수로 보지 않도록 완화했다.

검증:

- `.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py -k non_korean_free_question_retries_fallback_model_before_template`
- `.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py tests\test_semantic_evaluation.py tests\test_observability.py`
- Python AI 서비스 재시작 후 같은 요청이 `route=generation`, `fallback_used=false`, `llm_call_avoided=false`로 응답하는 것 확인

## 재발 방지 / 메모

자유 질문에서 `fallback_template`이 보인다고 해서 항상 AI 호출이 생략된 것은 아니다. 응답 메타데이터의 `llm_call_avoided`, `model_used`, `quality_flags`를 같이 확인해야 한다.

전체 `ai` 테스트는 195개 통과, 1개 실패였다. 남은 실패는 `ai/app/knowledge/concepts/generated/auto-review-textinput.md`의 지식 카드 린트 섹션 누락으로, 이번 생성 라우팅 수정과는 별도 데이터 품질 문제다.

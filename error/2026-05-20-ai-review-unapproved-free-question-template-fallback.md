# AI review unapproved free-question template fallback

- 발생 날짜: 2026-05-20
- 범위: ai
- 심각도: medium

## 증상

승인된 concept card가 없는 주제에 대해 자유 질문을 하면 LLM의 일반 개념 설명 대신 템플릿 답변이 반환됐다. 예를 들어 "Flutter 앱이 뭔가요?", "DAO가 뭔가요?" 같은 질문에 "승인된 지식 카드가 아직 부족해서..."라는 문구와 원래 문제의 정답 기준이 섞여 표시됐다.

## 원인

`free-question`의 독립 개념 질문은 승인된 카드나 Chroma 검색 결과가 없어도 LLM이 일반 개념 설명을 생성하고, 그 결과를 후보로 저장해야 한다. 하지만 1차 Ollama 호출이 실패하면 `ai/app/workflow/nodes.py:92`에서 바로 템플릿 fallback으로 내려가면서, "승인된 카드 없음"과 "모델 호출 실패"가 UI에서 같은 응답처럼 보였다.

## 해결 방법

독립 자유 질문(`latest_question_only`)에서 1차 모델 호출이 실패하면 템플릿으로 내려가기 전에 fallback 모델을 한 번 더 호출하도록 변경했다. fallback 모델이 답변을 만들면 `route=generation`, `fallback_used=false`로 유지하고, 기존 자동 후보 저장 흐름을 그대로 타게 했다.

- `ai/app/workflow/nodes.py:92`
- `ai/app/workflow/nodes.py:392`
- `ai/tests/test_workflow_runner.py:404`
- `ai/tests/test_workflow_runner.py:436`

## 재발 방지·메모

미승인 주제라도 일반 개념 설명은 먼저 LLM 생성으로 시도해야 한다. 템플릿 fallback은 1차 모델과 fallback 모델이 모두 실패했을 때의 최후 수단으로 유지한다. 회귀 테스트는 미승인 Flutter 질문의 LLM 답변 보존과 DAO 질문의 fallback 모델 재시도를 검증한다.

---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI Semantic Judge 도입 시 Mock 호출 횟수 오염, Knowledge Lint 오류 및 Retry 프롬프트 검증 우회 해결..."

---

# AI Semantic Judge 도입 시 Mock 호출 횟수 오염, Knowledge Lint 오류 및 Retry 프롬프트 검증 우회 해결

- 발생 일시: 2026-05-27
- 영역: backend
- 심각도: medium

## 증상
1. 기존 유닛 테스트(예: `test_contextual_json_question_uses_generator_instead_of_static_definition` 등)가 `AssertionError: 2 != 1`로 실패함.
2. `test_valid_bundled_cards_pass_lint` 지식 카드 린트 테스트가 `auto-review-textinput.md` 파일에 필수 섹션(`대표 해결`, `흔한 오해`, `평가 키워드`)이 누락되어 `AssertionError`로 실패함.
3. `test_judge_retry_uses_background_removed_prompt` 테스트에서 생성 시 background original context가 제거되었는지 검증할 때, `concept_definition` 질문의 경우 기본적으로 배경 질문을 프롬프트에 포함하지 않아 `AssertionError: '원래 배경 문제' not found in ...`가 발생함.

## 원인
1. **Mock 오염**: `app/workflow/judge.py`의 판사 평결 compatibility check에서 generator 소스코드에 `"JSON"` 또는 `"json"` 단어가 포함되어 있으면 판사 호환용으로 판정하여 judge를 돌렸음. 이로 인해 JSON 응답 테스트용 단순 mock generator도 판사 판결을 위해 한 번 더 호출되어 `calls["count"]`가 2로 증가함.
2. **Lint 오류**: `auto-review-textinput.md` 파일이 `concepts` 아래 `generated` 폴더에 있으면서도 concept card 필수 명세 포맷을 지키지 않았음.
3. **Retry 프롬프트 우회**: `concept_definition` 인텐트의 경우, RAG 생성과 달리 원래 문제의 배경 text를 1차 프롬프트에도 포함하지 않도록 설계되어 있어 `"원래 배경 문제"` 문자열의 존재 여부로 retry prompt의 background context 제거를 검증할 수 없었음. 또한 `"프록시 객체가 뭐야?"` 같은 순수 정의문은 `context_dependent=False`로 잡혀 retry 전후가 동일했음.

## 해결 방법
1. **Compatibility Check 정밀화** ([ai/app/workflow/judge.py:43](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/judge.py#L43)): `"JSON"`, `"json"` 등의 지나치게 포괄적인 키워드를 제거하고, `"relevance_score"`, `"context_bias_score"`, `"hallucination_risk"` 등 구체적인 판사 schema 키들로 pre-flight 필터를 강화하여 일반 mock generator 호출을 완전히 방지함.
2. **Knowledge Card 보완** ([ai/app/knowledge/concepts/generated/auto-review-textinput.md:14](file:///c:/Users/User/Desktop/Sub_Project/ai/app/knowledge/concepts/generated/auto-review-textinput.md#L14)): `auto-review-textinput.md` 카드 내에 누락되어 있던 `대표 해결`, `흔한 오해`, `평가 키워드` 섹션을 추가 작성함.
3. **Retry Prompt 및 검증용 질문 개선**:
   - [ai/app/prompts.py:133](file:///c:/Users/User/Desktop/Sub_Project/ai/app/prompts.py#L133) 및 하단 fallback 프롬프트에 `not intent.context_dependent`일 경우(retry 시) 원래 배경 문제 context를 완벽히 소거하여 생성하는 안전망을 추가함.
   - [ai/tests/test_workflow_runner.py:1202](file:///c:/Users/User/Desktop/Sub_Project/ai/tests/test_workflow_runner.py#L1202)의 `user_answer`를 컨텍스트 의존적인 `"이 문제에서 프록시 객체가 뭐야?"`로 변경하여 `wrong_answer_explanation` 분기에서 retry 시 배경 질문이 정상 제거되는 것을 `self.assertIn` 및 `self.assertNotIn`으로 확인 가능하도록 정밀화함.

## 재발 방지 / 메모
- Mock generator를 작성할 때는 호출 횟수나 argument 체크 시 예상치 못한 사후 레이어(예: Semantic Judge)의 호출 시나리오를 염려하여, generator 소스나 함수 이름을 엄밀히 정의하거나 filter safe paths를 지속 점검해야 합니다.
- 새로운 형태의 지식 카드(`category: auto-review` 등)를 dynamic 생성하거나 bundle할 때도 항상 `scripts/lint_knowledge_cards.py`의 스키마 명세를 엄격히 준수하여 템플릿을 생성해야 합니다.

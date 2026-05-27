# AI JSON free question used static fast path instead of model generation

- 발생 일시: 2026-05-27
- 영역: ai
- 심각도: medium

## 증상

AI 복습 자유 질문에서 `응답 JSON의 의미`를 입력하면 0.2초 내외로 `승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요` 형태의 템플릿 답변이 표시됐다.

직접 Python AI 서비스에 같은 요청을 보내면 기존에는 `model_used=lightweight-template`, `route=static_fast_path` 또는 `fallback_template`로 응답했다. 즉 이 케이스는 실제 생성 모델 호출이 생략되고 있었다.

## 원인

`ai/app/workflow/lightweight_answers.py`의 정적 fast path가 `JSON` 키워드를 흔한 단독 개념 질문으로 판단했다. 그래서 `응답 JSON`처럼 원문 문제의 맥락이 붙은 질문도 일반 JSON 정의 템플릿으로 먼저 처리됐다.

그 뒤 검증 단계에서 원문 문제와 겹치는 `응답`, `JSON` 문맥 때문에 `stale_original_context`가 붙으면 최종 답변이 fallback 템플릿으로 교체될 수 있었다.

관련 파일:

- ai/app/workflow/lightweight_answers.py:245
- ai/app/workflow/lightweight_answers.py:298
- ai/tests/test_workflow_runner.py:506

## 해결 방법

`json`, `api`, `dto`처럼 흔한 정적 개념이라도 질문에 `응답`, `요청`, `반환`, `직렬화`, `response`, `request`, `serialize` 같은 맥락 마커가 함께 있으면 static fast path를 건너뛰고 생성 모델 경로로 보내도록 했다.

`JSON이 뭐야?` 같은 단독 개념 질문은 기존 fast path를 유지하고, `응답 JSON의 의미`는 generator를 호출한다는 회귀 테스트를 추가했다.

검증:

- `.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py -k "contextual_json_question_uses_generator_instead_of_static_definition or common_programming_concepts_skip_generator"`
- `.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py tests\test_intent_routing.py tests\test_query_resolver.py tests\test_observability.py`
- Python AI 서비스를 stale reloader 없이 재시작한 뒤 같은 요청이 `route=generation`, `model_used=qwen3:1.7b`, `llm_call_avoided=false`로 응답하는 것 확인

## 재발 방지 / 메모

정적 fast path는 사용자 체감상 "AI를 안 돌림"으로 보인다. 흔한 키워드라도 주변에 업무/문제 맥락 마커가 붙어 있으면 생성 모델에 보내야 한다.

Uvicorn `--reload` 사용 중 오래된 reloader/child process가 남아 새 코드 반영 확인을 방해했다. 로컬 검증 시에는 `8001` listener와 uvicorn 자식 프로세스를 모두 정리하고 단일 프로세스로 재시작하면 원인 분리가 쉽다.

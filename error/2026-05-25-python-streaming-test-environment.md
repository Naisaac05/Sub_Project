# Python streaming test environment missing dev dependencies

- 발생 일시: 2026-05-25
- 영역: backend / Python AI / test
- 심각도: medium

## 증상

`ai/tests/test_stream.py`를 실행하려고 하면 로컬 `python` 명령이 없거나, 번들 Python에서는 `pytest`, `fastapi`, `httpx`가 없어 테스트가 collection 단계에서 실패했다. `pytest` cache도 기존 `.pytest_cache` 권한 문제로 경고를 냈다. 의존성을 설치한 뒤에는 streaming 성공 테스트가 현재 검증 파이프라인에서 fallback 답변으로 바뀌어 실패했다.

## 원인

Python AI 서비스에는 런타임용 `requirements.txt`만 있고 테스트 전용 의존성(`pytest`, FastAPI `TestClient`가 요구하는 `httpx`)이 고정되어 있지 않았다. 또한 streaming 성공 fixture가 영어 또는 topic이 맞지 않는 답변을 사용해 `validate_answer_node`/`fallback_answer_node`에서 fallback으로 교체되는 상태였다.

## 해결 방법

- 테스트 전용 의존성을 `requirements-dev.txt`로 분리해 고정했다: `ai/requirements-dev.txt:2`, `ai/requirements-dev.txt:3`
- pytest cache 권한 경고가 테스트 결과를 흐리지 않도록 cache provider를 껐다: `ai/pytest.ini:2`
- streaming 성공 테스트 fixture를 현재 free-question 검증 규칙을 통과하는 한국어 topic 일치 답변으로 수정했다: `ai/tests/test_stream.py:15`, `ai/tests/test_stream.py:21`, `ai/tests/test_stream.py:38`
- 로컬 테스트 환경 산출물은 Git에 잡히지 않도록 ignore에 추가했다: `.gitignore:22`, `.gitignore:24`
- 실행 절차를 `ai/TESTING.md`에 문서화했다.

## 재발 방지 / 메모

Python AI 테스트는 `requirements-dev.txt`를 기준으로 가상환경을 만들고 실행한다. streaming workflow 테스트의 성공 fixture는 실제 검증 파이프라인을 지나므로, chunk 수신뿐 아니라 최종 `AiGenerateResponse`가 fallback으로 바뀌지 않도록 질문 topic과 답변 topic을 맞춘다.

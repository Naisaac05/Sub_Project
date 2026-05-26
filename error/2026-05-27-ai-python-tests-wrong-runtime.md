# AI Python tests run with wrong runtime

- 발생 일시: 2026-05-27
- 영역: AI / test environment
- 심각도: low

## 증상

`tests.test_workflow_degraded_modes`를 Codex 번들 Python으로 실행했을 때 `app.ollama.client` import 중 `ModuleNotFoundError: No module named 'httpx'`가 발생했다.

## 원인

프로젝트에는 `ai/.venv`가 있고 `ai/requirements-dev.txt`에 `httpx==0.28.1`이 포함되어 있다. 실패는 프로젝트 venv가 아니라 `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`로 테스트를 실행해서 발생했다. 해당 번들 Python에는 프로젝트 dev dependency가 설치되어 있지 않았다.

## 해결 방법

AI 서비스 테스트는 프로젝트 venv로 실행한다.

- `ai/TESTING.md`: 프로젝트 테스트 런타임은 `.\.venv\Scripts\python.exe`와 `requirements-dev.txt` 기준이다.
- `ai/requirements-dev.txt`: `httpx==0.28.1` 포함.
- 검증 명령: `C:\Users\User\Desktop\Sub_Project\ai\.venv\Scripts\python.exe -m unittest tests.test_answer_cache tests.test_workflow_degraded_modes`

## 재발 방지 / 메모

Codex 번들 Python은 문서/간단 스크립트용으로는 쓸 수 있지만, AI 서비스 테스트는 repo-local venv를 우선 사용한다. `httpx`, `fastapi`, `pytest`가 필요한 테스트는 특히 `ai/.venv/Scripts/python.exe`로 실행해야 한다.

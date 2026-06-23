# AI 스크립트 도움말 확인이 실제 평가를 실행함

- 발생 일시: 2026-06-21
- 영역: AI / tooling
- 심각도: low

## 증상

RAG 카드 작업 계획을 작성하기 위해 여러 Python 스크립트에 `--help`를 전달했으나, `app/scripts` 도구는 import 오류로 종료됐고 E2E 평가 스크립트는 도움말 대신 실제 평가를 실행해 보고서를 생성했다.

## 원인

`ai/app/scripts/*.py`는 `app` 패키지를 기준으로 가져오므로 저장소 루트가 아니라 `ai`를 작업 디렉터리로 실행해야 한다. 또한 `ai/scripts/evaluate_v2_approved_ollama_e2e.py:99`의 `main()`은 `argparse`를 사용하지 않고 모든 인수를 무시하므로 `--help`도 실제 평가 실행으로 이어진다.

## 해결 방법

실행 도중 생성된 미추적 보고서 `ai/reports/v2_approved_ollama_e2e_2026-06-21.json`을 삭제했다. 계획의 실행 명령은 `ai` 작업 디렉터리를 기준으로 작성하고, E2E 평가 스크립트는 도움말 확인 없이 실제 평가 단계에서만 실행하도록 구분한다.

## 재발 방지 / 메모

- `ai/app/scripts` 도구는 `cd ai` 후 `python -m app.scripts.<module>` 형태로 실행한다.
- CLI 지원 여부를 확인하기 전 `--help`가 무해하다고 가정하지 말고 `if __name__ == "__main__"` 및 `argparse` 사용 여부를 먼저 읽는다.
- `evaluate_v2_approved_ollama_e2e.py`는 실행 자체가 보고서와 로그를 생성하는 쓰기 작업이다.

---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "RAG 기반 v2 Korean enrichment direct execution import failure 발생 원인 분석 및 트러블슈팅 ..."

---

# v2 Korean enrichment direct execution import failure

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards v2
- 심각도: low

## 증상

`python ai/app/scripts/enrich_draft_cards_korean.py`로 dry-run을 실행하면 `ModuleNotFoundError: No module named 'app'` 오류가 발생했다.

## 원인

테스트에서는 `ai` 디렉토리가 Python import 경로에 포함되지만, 스크립트 파일을 직접 실행하면 시작 경로가 `ai/app/scripts`가 되어 `app` 패키지를 찾을 수 없었다.

관련 파일:
- `ai/app/scripts/enrich_draft_cards_korean.py:11`
- `ai/tests/test_enrich_draft_cards_korean.py:56`

## 해결 방법

스크립트가 직접 실행될 때 `ai` 루트를 `sys.path`에 등록한 뒤 migration 모듈을 import하도록 변경했다. `--help` 직접 실행 회귀 테스트를 추가했다.

## 재발 방지 / 메모

`ai/app/scripts` 아래에서 `app.*`를 import하는 실행형 스크립트는 직접 실행 테스트를 포함해야 한다.

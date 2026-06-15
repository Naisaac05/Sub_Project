# priority v2 enrichment script could not import app package

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards
- 심각도: low

## 증상
`python app/scripts/enrich_priority_v2_cards.py` 실행이 카드 처리 전에 `ModuleNotFoundError: No module named 'app'`로 중단됐다.

## 원인
파일 경로로 스크립트를 실행하면 Python import 기준 경로가 `ai/app/scripts`가 되어 상위 `ai/app` 패키지를 찾을 수 없었다.

## 해결 방법
스크립트 시작 시 저장소의 `ai/` 경로를 `sys.path`에 추가한 뒤 앱 모듈을 import하도록 수정했다.

관련 파일:
- `ai/app/scripts/enrich_priority_v2_cards.py:8`

## 재발 방지 / 메모
`app/scripts` 아래 독립 실행 스크립트는 앱 모듈 import 전에 `ai/` root bootstrap을 수행한다.

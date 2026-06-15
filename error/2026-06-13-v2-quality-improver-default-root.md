# v2 quality improver default root pointed outside app knowledge

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards
- 심각도: medium

## 증상
`improve_rag_cards_v2.py` dry-run이 실제 v2 카드가 142개 존재하는데도 총 카드 수를 0개로 보고했다.

## 원인
스크립트 기준 `ROOT`는 `ai/` 디렉토리인데 기본 카드 경로를 `ROOT / "knowledge" / "concepts_v2"`로 구성해 `ai/knowledge/concepts_v2`를 조회했다. 실제 저장소는 `ai/app/knowledge/concepts_v2`이다.

## 해결 방법
기본 카드 경로를 `ROOT / "app" / "knowledge" / "concepts_v2"`로 수정했다.

관련 파일:
- `ai/app/scripts/improve_rag_cards_v2.py:13`
- `ai/tests/test_improve_rag_cards_v2.py:50`

## 재발 방지 / 메모
기본 경로가 실제 디렉토리를 가리키고 경로 끝이 `app/knowledge/concepts_v2`인지 확인하는 회귀 테스트를 추가했다. 카드 0개 dry-run은 쓰기 단계로 진행하지 않는다.

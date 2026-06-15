# RAG v2 migration skipped all SQL questions because option JSON remained escaped

- 발생 일시: 2026-06-12
- 영역: ai / migration / data
- 심각도: medium

## 증상

`migrate_rag_cards.py --limit 10`과 `--limit 30`이 오류 없이 종료됐지만 질문, 카드, payload 수가 모두 0으로 출력됐다.

## 원인

실제 SQL dump의 `questions.options` 값은 JSON 큰따옴표가 `\"` 형태로 SQL 문자열 안에 저장되어 있다. `sql_string()`이 이 이스케이프를 해제하지 않아 `json.loads()`가 실패했고, 파서가 해당 행을 모두 건너뛰었다.

## 해결 방법

`ai/app/scripts/migrate_rag_cards.py:168`에서 JSON 디코딩 전에 `\"`를 `"`로 해제했다. 실제 dump 형식을 재현하는 회귀 테스트를 `ai/tests/test_migrate_rag_cards_v2.py:45`에 추가했다.

## 재발 방지 / 메모

SQL 파서 테스트에는 사람이 단순화한 JSON뿐 아니라 실제 dump가 사용하는 SQL 이스케이프 형식을 반드시 포함한다. dry-run에서 입력 행 수가 0이면 성공으로 간주하지 말고 원본 SQL 행 수와 비교해야 한다.


# RAG next20 보고서 스키마 오인

## 증상

승인 dry-run이 후보 ID `candidate`를 카드 경로에서 찾다가 `KeyError`로 중단됐다.

## 원인

`cards_by_course`는 후보 ID 목록이 아니라 코스별 집계 객체인데, 새 승인 스크립트가 이를 ID 목록으로 해석했다.

## 해결 방법

후보 ID의 실제 원장인 `PREPARATION_BACKLOG` 객체의 키를 사용하도록 변경했다.

- `ai/app/scripts/review_rag_card_next20.py:76`

## 재발 방지·메모

보고서 간 연동에서는 표시용 집계 필드가 아니라 명시적인 원장 필드를 사용한다. 승인 스크립트 단위 테스트와 실제 dry-run을 함께 실행한다.

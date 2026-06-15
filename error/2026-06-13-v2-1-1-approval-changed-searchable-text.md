# v2.1.1 승인 전환 후 searchable text 변경

- 발생 일시: 2026-06-13
- 영역: ai
- 심각도: medium

## 증상
품질 검사와 승인 전 검색 테스트를 통과한 카드가 승인된 뒤 LOO 평균 점수가 다시 변경되어, 최초 최종 보고서 수치와 활성화 상태의 실제 수치가 달라졌다.

## 원인
`ai/app/schemas/rag_card.py:86`의 `searchable_text`는 `CONCEPT_DEFINITION` payload 상태가 `approved`일 때만 정의 내용을 검색 문서에 포함한다. 따라서 `draft` 상태 검색 테스트와 승인 후 실제 검색 입력이 서로 달랐다.

## 해결 방법
승인 후 검색 평가를 다시 실행해 Exact Hit@1/3/5와 LOO 후보율이 유지되고 LOO 평균 점수만 상승한 것을 확인했다. `ai/reports/card_quality_patch_v2_1_1_2026-06-13.json:61`에 활성화 후 실제 지표와 재검증 결과를 반영했다.

## 재발 방지 / 메모
승인 전 retrieval test는 필수로 유지하되, 승인 상태가 searchable text를 바꾸는 구조에서는 승인 후 동일 평가를 한 번 더 실행하고 최종 보고서에는 활성화 후 지표를 기록한다.

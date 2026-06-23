# RAG 후보 선정 상태·우선순위 오류

## 증상

읽기 전용 preparation 보고서에 등장한 draft 카드가 후속 후보에서 제외되고, 품질 문제가 많은 카드가 깨끗한 카드보다 먼저 선택될 수 있었다.

## 원인

preparation backlog를 완료 이력으로 간주했고 품질 문제 개수 정렬 방향이 반대로 구현돼 있었다.

## 해결 방법

승인 완료 이력만 제외 조건으로 사용하고 품질 문제 수가 적은 후보를 먼저 선택하도록 정렬을 수정했다.

- `ai/app/scripts/prepare_course_balanced_next40.py:120`
- `ai/tests/test_prepare_course_balanced_next40.py:85`

## 재발 방지·메모

후보 선정 테스트에 draft 유지, 승인 제외, 품질 우선순위 사례를 고정했다.

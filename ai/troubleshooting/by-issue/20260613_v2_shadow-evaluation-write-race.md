---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "RAG 기반 v2 Shadow 평가와 카드 write 병렬 실행으로 이전 상태 측정 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# v2 Shadow 평가와 카드 write 병렬 실행으로 이전 상태 측정

- 발생 일시: 2026-06-13
- 영역: ai / RAG 평가
- 심각도: medium

## 증상

questions 기반 보강 직후 v2 Top1 관련성이 50%로 측정됐으나, 동일 데이터를 순차 재실행하면 96%가 측정됐다.

## 원인

draft 카드 write와 Shadow 평가를 병렬 실행했다. 평가 프로세스가 일부 카드가 갱신되기 전에 기존 retrieval 필드를 읽어 이전 상태를 측정했다.

## 해결 방법

카드 write와 lint가 완료된 뒤 Shadow 평가를 순차 실행했다. 순차 재평가 결과 v2 Top1/Top3 관련성은 96%였다.

관련 파일:

- `ai/app/scripts/enrich_mapped_draft_cards.py`
- `ai/scripts/evaluate_course_question_shadow.py`

## 재발 방지·메모

카드 파일을 변경하는 작업과 이를 읽는 평가 작업은 병렬 실행하지 않는다. 평가 전 lint와 approved manifest 검증을 완료하고 평가를 시작한다.

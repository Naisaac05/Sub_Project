---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "Diagnostic test result showed pass/fail wording 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# Diagnostic test result showed pass/fail wording

- 발생 날짜: 2026-04-29
- 영역: frontend
- 심각도: medium

## 증상

진단용 실력 테스트에서 30점 결과가 `불합격`으로 표시됐다. 이 테스트는 합격/불합격 판정보다 현재 상태를 알려주는 목적이므로, 낮은 점수는 `복습이 필요한 상태`처럼 표시되어야 했다.

## 원인

`frontend/src/app/tests/results/page.tsx`와 `frontend/src/app/mypage/page.tsx`가 기존 테스트 흐름의 `result.passed` 값을 그대로 사용해 합격/불합격 배지를 렌더링하고 있었다. 결과 상세 화면은 진단 문구로 바뀌었지만 결과 목록과 마이페이지 최근 결과 카드가 아직 이전 문구를 사용했다.

## 해결 방법

- `frontend/src/app/tests/results/page.tsx`: 결과 목록 화면을 점수 기반 진단 상태 배지로 재구성했다.
- `frontend/src/app/mypage/page.tsx`: 최근 테스트 결과 카드도 `탄탄한 상태`, `기본기`, `복습 필요` 배지로 표시하도록 변경했다.
- 관련 파일 TS 변환 검사를 통과했다.

## 재발 방지·메모

진단 테스트 UI에서는 `passed`를 사용자에게 직접 노출하지 않는다. 백엔드 저장값은 유지하더라도 화면 문구는 점수 구간 기반 상태로 변환해서 보여준다.

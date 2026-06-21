---
type: troubleshooting
category: rag
status: active
updated: 2026-06-18
description: "AI follow-up fell back to template after RAG timeout 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI follow-up fell back to template after RAG timeout

- 발생 일시: 2026-05-27
- 영역: ai
- 심각도: medium

## 증상

복습 화면에서 사용자가 `@Scheduled가 뭔지 몰라서요`처럼 확인 질문에 답했을 때, AI가 생성한 설명 대신 `핵심은 @AuthenticationPrincipal와 @Scheduled가 가리키는 개념 차이를 구분하는 거예요...` 형태의 템플릿 답변이 저장됐다. 동일 입력으로 Python AI의 `/api/review/follow-up`을 직접 호출해도 `route=fallback_template`, `model_used=template`로 반환됐다.

## 원인

`follow-up` 모드가 자유 질문처럼 RAG 컨텍스트를 조회하면서 `spring-fetch-join`, `auto-review-recyclerview` 같은 현재 질문과 무관한 카드가 프롬프트에 섞였다. 이 때문에 프롬프트가 길어져 1차 모델이 30초 안에 답하지 못했고, 기존 로직은 `follow-up`에서 fallback 모델 재시도도 하지 않거나 재시도하더라도 백엔드 타임아웃보다 늦게 끝났다. 또한 검증 단계가 `follow-up`에도 검색 카드의 `평가 키워드` 포함을 요구해 정상적인 선택지 비교 답변을 `missing_required_keywords`로 탈락시킬 수 있었다.

## 해결 방법

- `follow-up` 모드는 직전 문제/정답/오답/사용자 답변만으로 짧은 피드백을 생성하도록 RAG 컨텍스트 조회를 건너뛰게 했다: `ai/app/workflow/nodes.py:30`
- RAG 컨텍스트가 없는 `follow-up`의 confidence 계산을 정상 생성 경로로 통과시키도록 조정했다: `ai/app/workflow/nodes.py:177`
- `follow-up`은 느리고 한국어 품질이 불안정한 1차 소형 모델 대신 `qwen3:4b-q4_K_M`을 처음부터 사용하게 했다: `ai/app/workflow/nodes.py:434`
- 검색 카드의 `평가 키워드` 강제 검증은 `free-question`에만 적용되도록 좁혔다: `ai/app/workflow/nodes.py:440`
- 회귀 테스트를 추가했다: `ai/tests/test_workflow_runner.py:85`, `ai/tests/test_workflow_runner.py:119`, `ai/tests/test_workflow_runner.py:156`, `ai/tests/test_workflow_runner.py:192`

## 재발 방지 / 메모

`follow-up`은 "개념 설명 검색"보다 "현재 오답에 대한 짧은 피드백" 성격이 강하므로, 무관한 RAG 카드가 들어오면 정확도와 응답 시간이 둘 다 나빠진다. 직접 검증 결과 수정 후 동일 요청은 `model_used=qwen3:4b-q4_K_M`, `fallback_used=false`, `route=generation`, 약 14.6초로 반환되어 백엔드 기본 read timeout 30초 안에 들어왔다.

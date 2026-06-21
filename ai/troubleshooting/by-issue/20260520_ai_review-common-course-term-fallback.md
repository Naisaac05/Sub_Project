---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review common course term fallback 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review common course term fallback

- 발생 날짜: 2026-05-20
- 영역: ai
- 심각도: medium

## 증상

AI 복습 화면의 "궁금한 점 질문하기"에서 `N+1이 뭐야?`, `fetch join이 뭐야?`, `환경변수는 뭐야?`, `캐시는 뭐야?` 같은 짧은 학습 용어 질문이 실제 개념 설명 대신 "승인된 지식 카드가 아직 부족" 류의 보수적 fallback 답변으로 떨어졌다.

## 원인

`@Transactional`, `계층`처럼 일부 용어는 static fast-path 또는 topic-specific fallback에 들어갔지만, 자주 묻는 강의/백엔드 기본 용어가 `ai/app/workflow/lightweight_answers.py:93`의 경량 답변 테이블에 빠져 있었다. 그 결과 `ai/app/workflow/nodes.py:278`의 contextual fallback까지 내려가면서 질문 자체는 이해했지만 실제 설명을 주지 못했다.

## 해결 방법

`ai/app/workflow/lightweight_answers.py:93`에 `N+1`, `fetch join`, `환경변수`, `캐시`의 짧은 승인형 설명과 alias를 추가해 generator 호출 전에 `static_fast_path`로 답하게 했다.

`ai/app/workflow/nodes.py:278` 주변의 contextual fallback은 유지하되, 이전 generic fallback 문장이 다시 실행될 여지가 없도록 정리했다.

`ai/tests/test_workflow_runner.py:414`에 회귀 테스트를 추가해 네 용어가 `fallback_template`이 아니라 `static_fast_path`로 처리되고, 핵심 키워드를 포함하는지 검증했다.

## 재발 방지 / 메모

짧은 자유 질문은 RAG 인덱스가 부족하거나 아직 승인되지 않은 concept card가 있어도 최소한의 학습 답변을 제공해야 한다. 앞으로 자주 나오는 단문 질문은 바로 LLM fallback으로 보내기보다 `lightweight_answers.py`의 curated fast-path 또는 승인된 concept card promotion 경로에 먼저 추가한다.

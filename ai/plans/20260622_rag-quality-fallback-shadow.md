---
type: plan
category: rag
status: completed
updated: 2026-06-22
description: "RAG 품질 4.5, 실제 Ollama fallback, 합성 Shadow 검증 실행 계획"
---

# RAG Quality Fallback Shadow Implementation Plan

> 실행 결과: 평균 품질 4.52와 합성 Shadow 라우팅은 통과했다. 실제 Ollama 필수 표본은 0/2로 실패해 전체 판정은 `NOT_READY`이다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RAG 응답 품질 4.5 이상을 달성하고 실제 Ollama fallback과 합성 Shadow 라우팅을 검증한다.

**Architecture:** 기존 50문항 평가기를 품질 진단 원장으로 사용한다. 검색 필드를 잠근 채 저점 approved payload만 보강하고, 실제 Ollama 검증과 합성 Shadow 검증은 별도 보고서로 분리한다.

**Tech Stack:** Python 3, unittest, Ollama HTTP API, JSON RAG cards

---

### Task 1: 응답 품질 4.5 달성

**Files:**
- Modify: `ai/scripts/evaluate_v2_approved_ollama_e2e.py`
- Modify: 저점 `ai/app/knowledge/concepts_v2/**/*.json`
- Test: `ai/tests/test_course_question_shadow.py`

- [x] 4점 이하 카드 목록과 평균 기준 실패 테스트를 작성한다.
- [x] 검색 필드를 변경하지 않고 저점 payload를 보강한다.
- [x] 50문항 평균 4.5 이상과 기존 approved 검색 필드 불변을 검증한다.

### Task 2: 실제 Ollama fallback 검증

**Files:**
- Create: `ai/scripts/evaluate_live_ollama_fallback.py`
- Test: `ai/tests/test_live_ollama_fallback_evaluation.py`
- Create: `ai/reports/live_ollama_fallback_2026-06-22.json`

- [x] 응답 품질 판정과 보고서 생성 실패 테스트를 작성한다.
- [x] 설치 모델 확인 후 `exaone3.5:2.4b` 실제 호출을 실행한다.
- [x] 응답·지연·오류·품질 근거를 보고서에 기록한다.

### Task 3: 합성 운영 Shadow 검증

**Files:**
- Create: `ai/scripts/evaluate_synthetic_shadow_traffic.py`
- Test: `ai/tests/test_synthetic_shadow_traffic.py`
- Create: `ai/reports/synthetic_shadow_traffic_2026-06-22.json`

- [x] hit·miss 시나리오 집계 실패 테스트를 작성한다.
- [x] `SHADOW_MODE=true` 합성 요청을 실행하고 관측 이벤트를 수집한다.
- [x] 기준 통과 여부와 실운영 미검증 조건을 readiness 문서에 갱신한다.

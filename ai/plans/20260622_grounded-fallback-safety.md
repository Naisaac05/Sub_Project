---
type: plan
category: rag
status: completed
updated: 2026-06-22
description: "근거 기반 fallback 생성·품질 게이트·안전 응답 구현 계획"
---

# Grounded Fallback Safety Implementation Plan

> 실행 결과: 실제 모델 호출은 approved 근거가 있는 경우에만 발생했고, 품질 실패 및 근거 부족 모두 안전 응답으로 종결됐다. 모델 생성 품질 자체는 여전히 `NOT_READY`이다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** approved RAG 근거가 있는 경우에만 Ollama fallback을 생성하고 품질 실패 시 안전 응답으로 대체한다.

**Architecture:** 근거 검색과 결정적 품질 게이트를 독립 모듈로 분리한다. workflow 동기·스트리밍 경로는 동일 모듈을 호출하고 근거 부족 또는 품질 실패 시 fail-closed 응답을 반환한다.

**Tech Stack:** Python 3, unittest, existing Lexical RAG, Ollama workflow

---

### Task 1: approved 근거 검색

**Files:**
- Create: `ai/app/workflow/grounded_fallback.py`
- Test: `ai/tests/test_grounded_fallback.py`

- [x] approved 정의에서 강한 단일 근거만 반환하는 실패 테스트를 작성한다.
- [x] 약한 점수와 작은 margin은 근거 없음으로 처리한다.
- [x] 근거 객체에 카드 ID, 점수, 정의 내용만 포함한다.

### Task 2: 품질 게이트와 안전 응답

**Files:**
- Modify: `ai/app/workflow/grounded_fallback.py`
- Test: `ai/tests/test_grounded_fallback.py`

- [x] 한국어·완결성·주제·근거 겹침 실패 테스트를 작성한다.
- [x] 실패 이유를 품질 플래그로 반환한다.
- [x] 생성 원문을 포함하지 않는 안전 응답을 반환한다.

### Task 3: workflow 통합

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/runner.py`
- Test: `ai/tests/test_v2_approved_fast_path.py`

- [x] 근거 없는 동기 miss에서 generator 미호출 테스트를 작성한다.
- [x] 근거 있는 동기 miss와 품질 실패 안전 응답 테스트를 작성한다.
- [x] 스트리밍 경로에서 실패 생성 chunk 미노출 테스트를 작성한다.
- [x] 실제 Ollama 표본과 전체 workflow 회귀 테스트를 실행한다.

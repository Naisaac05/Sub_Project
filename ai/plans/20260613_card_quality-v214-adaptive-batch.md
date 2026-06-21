---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "Card Quality v2.1.4 Adaptive Batch Implementation Plan 도입 계획 및 마이그레이션 작업 방향"

---

# Card Quality v2.1.4 Adaptive Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Payload-only 품질 패치를 카드별 검증과 적응형 배치 확장으로 안전하게 실행한다.

**Architecture:** v2.1.3의 production/content 분리 평가를 재사용하고, v2.1.4 전용 도구가 후보 점수, 상태 필터, 배치 확장, 신규 백업, 카드별 복원을 담당한다. 실제 파일 저장 성공 카드만 patched_cards에 포함한다.

**Tech Stack:** Python, Pydantic, unittest, JSON

---

### Task 1: Adaptive Policy

**Files:**
- Create: `ai/app/scripts/patch_payload_batch_v214.py`
- Create: `ai/tests/test_patch_payload_batch_v214.py`

- [ ] 후보 점수 우선순위, 대상 상태, `10 → 20 → 40 → 40 → remaining` 확장 규칙의 실패 테스트를 작성한다.
- [ ] 테스트를 실행해 v2.1.4 모듈 부재로 실패하는지 확인한다.
- [ ] 최소 정책 함수를 구현하고 테스트를 통과시킨다.

### Task 2: Per-card Write Pipeline

**Files:**
- Modify: `ai/app/scripts/patch_payload_batch_v214.py`

- [ ] 매 배치 고유 백업, payload-only 잠금 검사, JSON 검증, production/content 평가, 카드별 복원을 구현한다.
- [ ] 중단 조건과 상세 보고 필드를 구현한다.

### Task 3: Execute And Verify

**Files:**
- Create: `ai/reports/payload_batch_v2_1_4_2026-06-13.json`
- Modify: eligible `ai/app/knowledge/concepts_v2/**/*.json`

- [ ] 적응형 배치를 실제 실행한다.
- [ ] JSON, 잠금 필드, production/content 지표, 전체 관련 테스트를 검증한다.
- [ ] 원인과 안전장치를 `error/`에 기록한다.

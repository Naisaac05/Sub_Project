---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "Concept Verification Infrastructure and Next 40 Implementation Plan 도입 계획 및 마..."

---

# Concept Verification Infrastructure and Next 40 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 개념 실행 검증 인프라를 완성하고, 기존 처리 카드를 제외한 코스 균등 후보 40개를 안전하게 discovery 및 preparation한다.

**Architecture:** 실행 검증기를 Java, Python, Spring, React, RSC 검증기로 분리하고 공통 결과 형식으로 통합한다. 40개 확장은 원본 카드를 수정하지 않는 discovery/preparation 단계까지만 자동 수행하며, 사실 검증된 payload 초안과 실행 검증 준비 상태를 별도 보고한다.

**Tech Stack:** Python unittest, Java 17/Javac, Gradle/Spring Boot test runtime, Node.js/React/Next.js, 기존 production/content retrieval evaluator

---

### Task 1: 실행 검증기 레지스트리

**Files:**
- Create: `ai/app/scripts/concept_example_verifiers.py`
- Modify: `ai/app/scripts/approve_concept_verified_examples.py`
- Test: `ai/tests/test_concept_example_verifiers.py`

- [ ] Java/Python 검증기와 의존성 상태 결과의 실패 테스트를 작성한다.
- [ ] 검증기를 카드별 하드코딩에서 언어·런타임별 레지스트리로 분리한다.
- [ ] 기존 승인 검증 테스트와 신규 테스트를 실행한다.

### Task 2: Spring·React·RSC 실행 하네스

**Files:**
- Create: `backend/src/test/java/com/devmatch/ragverify/ConceptExampleVerificationTest.java`
- Create: `frontend/scripts/verify-rag-examples.mjs`
- Modify: `ai/app/scripts/concept_example_verifiers.py`
- Test: `ai/tests/test_concept_example_verifiers.py`

- [ ] Spring Validation과 Profile 실제 실행 테스트를 추가한다.
- [ ] React `useCallback` 참조 동일성과 Next.js RSC 서버 렌더 검증 스크립트를 추가한다.
- [ ] Resilience4j 부재는 명시적 보류로 유지한다.
- [ ] 각 하네스를 독립 실행해 결과를 확인한다.

### Task 3: 개념 검증 품질 게이트 강화

**Files:**
- Modify: `ai/app/scripts/initialize_validation_policy_v212.py`
- Test: `ai/tests/test_initialize_validation_policy_v212.py`

- [ ] 단순 모사 예시를 거부하는 실패 테스트를 작성한다.
- [ ] 개념 API 사용, 행동 assertion, 실행 문맥, simulation-only 검사를 추가한다.
- [ ] 기존 카드 품질 점수와의 호환성을 검증한다.

### Task 4: 다음 코스 균등 40개 Discovery·Preparation

**Files:**
- Create: `ai/app/scripts/prepare_course_balanced_next40.py`
- Create: `ai/tests/test_prepare_course_balanced_next40.py`
- Output: `ai/reports/course_balanced_next40_preparation_2026-06-15.json`

- [ ] 기존 적용·검토 대상 카드를 제외하는 실패 테스트를 작성한다.
- [ ] Java, Spring, Frontend, Python, Algorithm에서 각 8개를 선정한다.
- [ ] source 연결, 품질 결함, 실행 검증 타입을 포함한 preparation 보고서를 생성한다.
- [ ] concepts_v2가 수정되지 않았음을 checksum으로 검증한다.

### Task 5: 전체 검증 및 결과 보고

- [ ] 신규·관련 테스트 전체를 실행한다.
- [ ] Spring·React·RSC 하네스를 독립 실행한다.
- [ ] production/content retrieval dry-run을 실행한다.
- [ ] 다음 40개의 candidate, prepared, backlog, 실행 검증 준비 상태를 보고한다.

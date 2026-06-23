---
type: plan
category: rag
status: completed
updated: 2026-06-21
description: "RAG v2 카드 품질 향상과 승인 카드 80개 달성을 위한 작업 리스트"
---

# RAG 카드 품질 확장 구현 계획

> 실행 결과: approved 82장 달성. Shadow 50문항 검색·라우팅 기준은 통과했으나 평균 품질 4.36/5와 운영 검증 미완료로 전환 판정은 `NOT_READY`이다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 approved 63장, draft 81장인 RAG v2 카드에서 품질과 검색 성능이 검증된 카드를 최소 17장 추가 승인하고, approved 80장 이상 상태에서 v2 Fast Path 전환 기준을 다시 판정한다.

**Architecture:** 기존 `concepts_v2`와 품질·실행 검증 도구를 유지한다. 후보 선정, payload 품질 보정, 실행 예시 검증, 검색 회귀 검증, 사람 승인, 섀도 E2E를 서로 분리하고 각 단계가 실패하면 카드별로 롤백한다. v1 제거와 `SHADOW_MODE=false` 전환은 모든 게이트를 통과한 뒤 별도 결정한다.

**Tech Stack:** Python 3, Pydantic, unittest, Ollama/BGE-M3, Java 17/Gradle, React/Next.js, JSON RAG cards

---

## 현재 기준선

- 전체 카드: 144장
- approved: 63장
- draft: 81장
- draft 분포: Algorithm 18, Frontend 17, Java 14, Python 17, Spring 15
- 교체 최소 기준: approved 80장 이상
- 필요한 순증가: 최소 17장, 안전 목표 20장
- 이전 50문항 Shadow: Top1 96%, Fast Path 96%, fallback 4%, irrelevant hit 0건
- 남은 대표 검색 공백: Java `equals`, React `key`

### Task 1: 기준선과 보호 범위 고정

**Files:**
- Use: `ai/app/knowledge/concepts_v2/**/*.json`
- Use: `ai/scripts/audit_rag_cards_v2.py`
- Use: `ai/scripts/lint_knowledge_cards.py`
- Create: `ai/reports/rag_card_expansion_baseline_2026-06-21.json`

- [x] **Step 1: approved 카드와 searchable 필드의 SHA-256 기준선을 생성한다.**

보고서에는 카드 ID, `review.card_status`, payload 승인 수, 파일 SHA-256, searchable checksum을 기록한다. 이후 단계에서 기존 approved 카드 변경은 즉시 실패로 처리한다.

- [x] **Step 2: 현재 품질 감사와 린트를 실행한다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\audit_rag_cards_v2.py --output reports\rag_card_expansion_baseline_2026-06-21.json
.\.venv\Scripts\python.exe scripts\lint_knowledge_cards.py
```

Expected: JSON 파싱 오류 0건, knowledge lint 오류 0건, approved 63장 확인.

- [x] **Step 3: 기준선 보고서와 기준선 생성·회귀 수정 코드를 커밋한다.**

```powershell
git add ai/reports/rag_card_expansion_baseline_2026-06-21.json
git commit -m "test(ai): capture rag card expansion baseline"
```

### Task 2: 코스 균형 후보 20장 선정

**Files:**
- Modify: `ai/app/scripts/prepare_course_balanced_next40.py`
- Test: `ai/tests/test_prepare_course_balanced_next40.py`
- Create: `ai/reports/course_balanced_next20_candidates_2026-06-21.json`

- [x] **Step 1: 이미 approved인 카드를 제외하고 읽기 전용 preparation 카드는 재선정하는 실패 테스트를 추가한다.**

```python
def test_candidate_selection_excludes_approved_and_previously_processed_cards():
    selected = select_candidates(cards, previous_ids={"java-equals"}, per_course=4)
    assert "java-equals" not in {item["card_id"] for item in selected}
    assert all(item["current_status"] == "draft" for item in selected)
```

- [x] **Step 2: Java, Spring, Frontend, Python, Algorithm에서 각 4장씩 선정한다.**

우선순위는 source question 연결, payload 완성도, 실행 검증 가능 여부, 기존 Shadow 공백 보완 순으로 계산한다. `auto-review` 카드는 이번 승인 배치에서 제외한다.

- [x] **Step 3: preparation을 실행하고 카드 파일 무변경을 확인한다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m app.scripts.prepare_course_balanced_next40
```

Expected: `card_files_modified=false`, 20장 이상 후보 확보, 카테고리별 최소 4장.

### Task 3: payload와 실행 예시 품질 개선

**Files:**
- Modify: `ai/app/scripts/patch_payload_batch_v214.py`
- Modify: `ai/app/scripts/initialize_validation_policy_v212.py`
- Modify: selected `ai/app/knowledge/concepts_v2/**/*.json`
- Test: `ai/tests/test_patch_payload_batch_v214.py`
- Test: `ai/tests/test_initialize_validation_policy_v212.py`
- Test: `ai/tests/test_concept_example_verifiers.py`

- [x] **Step 1: 품질 게이트 실패 테스트를 먼저 작성한다.**

```python
def test_payload_gate_rejects_fake_or_answer_printing_example():
    result = validate_payload_quality(card_with_answer_printing_example())
    assert "fake_example_score_nonzero" in result["reasons"]

def test_payload_gate_requires_runtime_behavior_and_context():
    metrics = score_example("print('설명만 출력')")
    assert metrics["example_quality"] < 0.7
```

- [x] **Step 2: 후보별 `CONCEPT_DEFINITION`, `ANSWER_REASON`, `WRONG_ANSWER_REASON`을 사람이 검토한다.**

정답 재진술, 동일 근거 반복, 어색한 한국어 조사, 실행 결과를 미리 출력하는 가짜 예시를 제거한다. 검색 필드 변경과 payload 변경은 별도 커밋으로 분리한다.

- [x] **Step 3: Java/Python/Spring/React 실행 검증기를 통과시킨다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_concept_example_verifiers tests.test_initialize_validation_policy_v212 -v
cd ..
.\gradlew.bat test --tests "com.devmatch.ragverify.ConceptExampleVerificationTest"
node frontend\scripts\verify-rag-examples.mjs
```

Expected: 실행 가능한 예시는 실제 API·언어 동작을 검증하고 simulation-only 예시는 승인 대상에서 제외.

### Task 4: 검색 필드와 회귀 세트 강화

**Files:**
- Modify: selected `ai/app/knowledge/concepts_v2/**/*.json`
- Modify: `ai/evals/golden_dataset.jsonl`
- Modify: `ai/tests/test_v2_approved_fast_path.py`
- Test: `ai/tests/test_rag_retriever.py`
- Create: `ai/reports/rag_card_expansion_retrieval_2026-06-21.json`

- [x] **Step 1: Java `equals`와 React `key` 회귀 테스트를 고정한다.**

```python
def test_course_gap_queries_retrieve_expected_card_at_top1():
    assert retrieve("Java 문자열 비교 equals")[0].concept_id == "java-equals"
    assert retrieve("React 리스트 렌더링 key 속성")[0].concept_id == "frontend-react-key"
```

- [x] **Step 2: 우선순위 공백인 Java `equals`와 React `key` 질의를 평가 데이터에 추가한다.**

카드별 최소 4개 질의를 골든 세트에 기록하고 expected card ID와 intent를 명시한다. 일반 토큰 하나만 공유하는 음성 질의도 카드별 1개 이상 추가한다.

- [x] **Step 3: production/content 검색 평가를 실행한다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_rag_retriever tests.test_v2_approved_fast_path -v
.\.venv\Scripts\python.exe retrieval_eval.py --retrievers bm25,rrf,weighted --k 1,3,5
```

Expected: 신규 질의 Top1 90% 이상, 기존 50문항 Top1 96% 이상 유지, irrelevant hit 0건.

### Task 5: 승인 전 dry-run과 사람 검토

**Files:**
- Use: `ai/app/scripts/dryrun_factchecked_next20_approval.py`
- Use: `ai/app/scripts/approve_concept_verified_examples.py`
- Create: `ai/reports/rag_card_next20_approval_dryrun_2026-06-21.json`
- Create: `ai/app/knowledge/concepts_v2_backups/rag-card-next20-20260621/`

- [x] **Step 1: 승인 전 dry-run을 실행한다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m app.scripts.dryrun_factchecked_next20_approval
```

Expected: JSON·품질·실행·검색 게이트를 모두 통과한 카드만 `eligible=true`; 원본 카드 변경 0건.

- [x] **Step 2: 검토 표에 카드별 근거를 기록한다.**

각 카드에 사실 검증 근거, 실행 검증 결과, 골든 질의 결과, payload 검토자, 제외 사유를 기록한다. 단순히 정적 점수가 높다는 이유만으로 승인하지 않는다.

- [x] **Step 3: 사람이 승인한 카드만 백업 후 상태를 변경한다.**

승인 실행 전에 전체 후보 파일과 기존 approved manifest를 백업한다. `review.card_status`와 세 payload 상태를 함께 `approved`로 변경하고, 일부 payload만 통과한 카드는 draft로 유지한다.

### Task 6: 승인 후 E2E와 전환 판정

**Files:**
- Use: `ai/scripts/evaluate_v2_approved_ollama_e2e.py`
- Test: `ai/tests/test_v2_approved_fast_path.py`
- Test: `ai/tests/test_workflow_runner.py`
- Create: `ai/reports/v2_approved_ollama_e2e_2026-06-21.json`
- Create: `ai/specs/20260621_v2-replacement-readiness.md`

- [x] **Step 1: 승인 후 카드 수와 manifest를 검증한다.**

Expected: approved 80장 이상, 기존 approved SHA-256 불일치 0건, knowledge lint 오류 0건.

- [x] **Step 2: v2 approved Fast Path + Ollama fallback E2E를 실행한다.**

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\evaluate_v2_approved_ollama_e2e.py
.\.venv\Scripts\python.exe -m unittest tests.test_v2_approved_fast_path tests.test_workflow_runner -v
```

Expected: Top1 관련성 95% 이상, Fast Path 90% 이상, fallback 10% 이하, irrelevant hit 0건, 평균 응답 품질 4.5/5 이상.

- [x] **Step 3: 전환 판정을 갱신한다.**

`SHADOW_MODE=true` 운영 트래픽 검증, 캐시 무효화·프로세스 재시작, 롤백 복원 시험까지 통과해야 `READY`로 판정한다. 기준을 하나라도 충족하지 못하면 `NOT_READY`를 유지한다.

### Task 7: 문서·운영 마무리

**Files:**
- Modify: `ai/README.md`
- Modify: `ai/index.md`
- Modify: `ai/reports/index.md`
- Modify: `ai/specs/index.md`
- Modify: `error/README.md` when a root-cause fix occurs

- [x] 최신 approved/draft 수치와 실행 명령을 문서에 반영한다.
- [x] 생성 보고서와 readiness 판정을 인덱스에 연결한다.
- [x] 원인까지 해결한 오류는 같은 턴에 `error/YYYY-MM-DD-*.md`로 기록한다.
- [x] v1 제거는 이 계획에 포함하지 않고, READY 및 롤백 검증 후 별도 변경으로 진행한다.

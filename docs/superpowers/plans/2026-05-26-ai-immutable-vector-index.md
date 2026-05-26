# Immutable Vector Index Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal immutable vector index contract with `knowledge_index_version`, manifest hash, rollback manifest snapshots, and answer cache namespace invalidation.

**Architecture:** Python owns the reusable manifest helper used by reindex scripts and answer cache namespace lookup. Spring approval reindex mirrors the same v2 manifest shape because it writes generated concept cards directly. Existing v1 manifests remain readable through the `entries` field.

**Tech Stack:** Python stdlib `json`/`hashlib`, existing FastAPI workflow modules, Spring Boot Java service tests with AssertJ.

---

### Task 1: Python Manifest Contract

**Files:**
- Create: `ai/app/knowledge/index_manifest.py`
- Modify: `ai/scripts/reindex_knowledge.py`
- Test: `ai/tests/test_index_manifest.py`

- [x] **Step 1: Write the failing test**

Add tests proving that manifest payloads get deterministic hashes, versions, cache namespace values, and previous snapshot metadata.

- [x] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai/tests/test_index_manifest.py -q`
Expected: FAIL because `app.knowledge.index_manifest` does not exist.

- [x] **Step 3: Write minimal implementation**

Create `index_manifest.py` with helpers to hash entries, build v2 manifest payloads, read active cache namespace, and snapshot previous manifests.

- [x] **Step 4: Run test to verify it passes**

Run: `python -m pytest ai/tests/test_index_manifest.py -q`
Expected: PASS.

### Task 2: Answer Cache Namespace

**Files:**
- Modify: `ai/app/workflow/answer_cache.py`
- Test: `ai/tests/test_answer_cache.py`

- [x] **Step 1: Write the failing test**

Add a test showing `cache_key_for()` changes when `AI_REVIEW_CACHE_NAMESPACE_VERSION` changes.

- [x] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai/tests/test_answer_cache.py -q`
Expected: FAIL because cache keys do not include the namespace.

- [x] **Step 3: Write minimal implementation**

Prefix cache keys with `active_cache_namespace_version()` from `app.knowledge.index_manifest`.

- [x] **Step 4: Run test to verify it passes**

Run: `python -m pytest ai/tests/test_answer_cache.py -q`
Expected: PASS.

### Task 3: Spring Manifest Writer

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`
- Test: `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`

- [x] **Step 1: Write the failing test**

Add assertions that approval reindex writes `schema_version`, `knowledge_index_version`, `manifest_hash`, `cache_namespace_version`, and snapshots the previous v1 manifest.

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest`
Expected: FAIL because the Java writer still emits v1 manifest fields only.

- [x] **Step 3: Write minimal implementation**

Update `updateManifest()` to write v2 fields, compute hash over entries, and copy the old manifest to `vectorstore/manifests/<version-or-hash>.json` before overwrite.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest`
Expected: PASS.

### Task 4: Docs and TODO

**Files:**
- Create: `docs/2026-05-26-ai-review-immutable-vector-index.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document the operating contract**

Describe active manifest fields, cache namespace invalidation, previous snapshot rollback, and env overrides.

- [x] **Step 2: Mark TODO complete**

Check the four Immutable Vector Index strategy items.

- [x] **Step 3: Run focused verification**

Run Python and Java focused tests:
`python -m pytest ai/tests/test_index_manifest.py ai/tests/test_answer_cache.py ai/tests/test_chroma_reindex.py -q`
`.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest`

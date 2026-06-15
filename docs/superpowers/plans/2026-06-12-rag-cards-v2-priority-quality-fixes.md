# RAG Cards v2 Priority Quality Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve priority v2 term extraction and merge safety, then regenerate all cards as drafts without activating v2.

**Architecture:** Keep the isolated migration pipeline and add deterministic concept-pattern extraction before generic token fallback. Filter stopwords and Korean particles, generate useful category-qualified aliases, and require distinctive token overlap before embedding-similarity merges.

**Tech Stack:** Python, unittest, Pydantic, lexical retrieval audit

---

### Task 1: Add Priority Regression Tests

**Files:**
- Modify: `ai/tests/test_migrate_rag_cards_v2.py`

- [ ] Add tests proving React key and Java equals receive dedicated terms.
- [ ] Add tests proving Korean stopwords/general words do not become terms.
- [ ] Add tests proving unrelated concepts do not merge from embedding similarity alone.
- [ ] Run the focused tests and confirm they fail for the expected quality gaps.

### Task 2: Improve Extraction And Merge Safety

**Files:**
- Modify: `ai/app/scripts/migrate_rag_cards.py`

- [ ] Add deterministic priority concept patterns and stopword filtering.
- [ ] Add category-qualified aliases without adding generic query terms.
- [ ] Require distinctive retrieval-token overlap for similarity-only merges.
- [ ] Run migration and audit tests until green.

### Task 3: Regenerate Isolated Draft Cards

**Files:**
- Replace generated drafts under: `ai/app/knowledge/concepts_v2/`

- [ ] Run `python ai/app/scripts/migrate_rag_cards.py --write`.
- [ ] Confirm every card and generated payload status remains `draft`.
- [ ] Confirm v1 files and `ACTIVE_CARD_STORE` remain unchanged.

### Task 4: Audit And Document

**Files:**
- Update: `docs/superpowers/specs/2026-06-12-rag-cards-v2-quality-audit.json`
- Update: `docs/superpowers/specs/2026-06-12-rag-cards-v2-quality-audit-summary.md`
- Create: `error/2026-06-12-rag-v2-generic-term-and-false-merge.md`
- Update: `error/README.md`

- [ ] Re-run quality audit and record React key / Java equals top five.
- [ ] Compare weak-alias, broad-term, and false-merge counts.
- [ ] Run workflow regression and diff checks.
- [ ] Record the identified root cause and fix.

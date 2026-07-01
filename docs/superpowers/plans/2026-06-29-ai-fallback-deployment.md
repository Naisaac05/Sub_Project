# AI Fallback Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align production Ollama models, add approved hashCode knowledge, and verify both RAG and no-card generation on AWS.

**Architecture:** Keep the existing three-route workflow: approved Fast Path, grounded Ollama generation, and ungrounded Ollama generation. Add missing knowledge and deployment contracts without globally lowering evidence gates or disabling safety validation.

**Tech Stack:** Python 3.11, unittest, FastAPI, Spring Boot 3, Docker Compose, Ollama, PowerShell/SSH.

---

### Task 1: Secret-safe deployment template

**Files:**
- Modify: `.env.prod.example`

- [ ] Remove all real JWT, AI service token, database endpoint, and database password values.
- [ ] Add `PYTHON_AI_MODEL=exaone3.5:2.4b`, `PYTHON_AI_FALLBACK_MODEL=exaone3.5:2.4b`, `OLLAMA_MODEL=exaone3.5:2.4b`, and `AI_REVIEW_EMBEDDING_MODEL=bge-m3`.
- [ ] Verify `git diff` contains no secret values.

### Task 2: hashCode regression test and approved card

**Files:**
- Modify: `ai/tests/test_grounded_fallback.py`
- Create: `ai/app/knowledge/concepts_v2/java/java-hashcode.json`

- [ ] Add a test asserting `select_grounded_evidence("hashCode가 뭐지?")` selects `java-hashcode`.
- [ ] Run the test and confirm it fails before the card exists.
- [ ] Add an approved `java-hashcode` card containing the equals/hashCode contract and HashMap/HashSet usage.
- [ ] Re-run the focused test and confirm it passes.

### Task 3: Production model contract

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/devmatch/config/AiReviewPropertiesTest.java`

- [ ] Add or use the existing binding test expecting both Python and Ollama defaults to equal `exaone3.5:2.4b`.
- [ ] Run the focused test and confirm the current qwen defaults fail.
- [ ] Change both defaults to `exaone3.5:2.4b`.
- [ ] Re-run the focused backend test.

### Task 4: Regression and knowledge verification

**Files:**
- Test: `ai/tests/test_grounded_fallback.py`
- Test: `ai/tests/test_workflow_runner.py`
- Test: RAG card lint suite under `ai/tests/`

- [ ] Run focused grounded fallback tests.
- [ ] Run the no-approved-evidence Ollama routing test.
- [ ] Run the RAG card validation suite.
- [ ] Count approved canonical Fast Path hits and require 86/86.

### Task 5: Runbook and error record

**Files:**
- Modify: `docs/deploy-runbook.md`
- Create: `error/2026-06-29-aws-ai-fallback-model-and-card-gap.md`
- Modify: `error/README.md`

- [ ] Document separate Ollama pulls and internal AI smoke probes.
- [ ] Record symptoms, root causes, resolution, and prevention with exact file links.
- [ ] Add the error document to the index.

### Task 6: AWS deployment and live verification

**Files:**
- Runtime: workspace `.env.prod` (gitignored)
- Runtime: EC2 `~/devmatch` or `~/Sub_Project`

- [ ] Copy the user's external `.env.prod` into the workspace and add aligned model variables without printing secrets.
- [ ] Confirm the existing remote build is no longer running.
- [ ] Deploy committed/tracked code and `.env.prod` to EC2.
- [ ] Pull `exaone3.5:2.4b` and `bge-m3` separately.
- [ ] Verify Compose services and Ollama model list.
- [ ] Call the AI endpoint for an approved `hashCode` question and a no-card `CopyOnWriteArrayList` question.
- [ ] Report actual routes, model names, and fallback flags without exposing tokens.


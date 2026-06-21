---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 Chroma DB 및 bge-m3 스모크 테스트 구현 계획"

---

# AI Review Chroma bge-m3 Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Chroma/bge-m3 indexing script path and smoke test that is safe on the current laptop and ready for high-performance machines.

**Architecture:** Extend the existing changed-only manifest script instead of adding a separate indexing entrypoint. Chroma and sentence-transformers are imported only inside the Chroma indexing path; tests use fake dependencies so they do not load bge-m3 on the current laptop.

**Tech Stack:** Python unittest, optional chromadb, optional sentence-transformers bge-m3, Markdown concept cards.

---

### Task 1: Chroma Indexing API

- [x] **Step 1: Write tests for Chroma document payloads**
- [x] **Step 2: Write tests for fake Chroma upsert**
- [x] **Step 3: Implement `build_chroma_documents` and `index_chroma_knowledge`**
- [x] **Step 4: Verify unit tests pass without chromadb installed**

### Task 2: Smoke Query Path

- [x] **Step 1: Write fake smoke query test**
- [x] **Step 2: Implement `--smoke-query` query after upsert**
- [x] **Step 3: Ensure missing dependencies return `skipped` instead of failing**

### Task 3: CLI and Documentation

- [x] **Step 1: Add `--chroma`, `--chroma-path`, `--collection`, `--embedding-model`, `--smoke-query` CLI flags**
- [x] **Step 2: Run current laptop smoke command and confirm Chroma skips safely**
- [x] **Step 3: Document high-performance command in `ai_pipeline.md`**

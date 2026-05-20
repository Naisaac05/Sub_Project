# AI Review Production Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect candidate approval v2 to the admin UI and document/verify the remaining production closeout items.

**Architecture:** Keep JSONL v1 as a fallback/backfill source. Add v2 frontend API functions, update the admin candidate page for v2 states/actions, and add an operational checklist for DB schema, LangGraph dependency verification, service token rollout, builds, and log collection.

**Tech Stack:** Next.js 14, TypeScript, Spring Boot v2 candidate API, Python LangGraph optional dependency.

---

### Task 1: Frontend Candidate Approval V2

- [x] **Step 1: Add v2 frontend API types/functions**
- [x] **Step 2: Update admin candidate page for v2 lifecycle/actions**
- [x] **Step 3: Run frontend build**

### Task 2: Operational Closeout Checklist

- [x] **Step 1: Add DB/ops checklist doc**
- [x] **Step 2: Verify LangGraph optional dependency status**
- [x] **Step 3: Run backend focused build/tests**

### Task 3: Final Verification

- [x] **Step 1: Run Python focused tests**
- [x] **Step 2: Run backend focused tests**
- [x] **Step 3: Run frontend build**

---
type: plan
category: rag
status: active
updated: 2026-06-18
description: "AI 리뷰 하이브리드 RAG 런타임 도입 및 전환 계획"

---

# AI Review Hybrid RAG Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the final.md hybrid retrieval stack in a laptop-safe way while making it easy to turn on stronger Chroma/bge-m3/flashrank retrieval on a better machine.

**Architecture:** Keep the current lexical retriever as the safe fallback. Add BM25 with optional kiwipiepy tokenization, optional Chroma+bge-m3 semantic retrieval, and optional flashrank reranking behind environment-controlled lazy adapters. Low-resource defaults must avoid importing or loading heavy models at startup.

**Tech Stack:** Python unittest, optional Chroma, optional sentence-transformers bge-m3, optional kiwipiepy, optional flashrank, local Markdown concept cards.

---

### Task 1: Laptop-Safe Hybrid Configuration

- [x] **Step 1: Define low-resource and high-performance profile behavior**
- [x] **Step 2: Add tests for profile selection without importing heavy dependencies**
- [x] **Step 3: Implement profile-aware hybrid adapter construction**
- [x] **Step 4: Verify low-resource profile still passes existing retriever tests**

### Task 2: BM25 + Korean Tokenizer Adapter

- [x] **Step 1: Add tests for BM25 ranking and optional kiwipiepy fallback**
- [x] **Step 2: Implement BM25 retriever with lazy tokenizer**
- [x] **Step 3: Verify BM25 participates in hybrid merge metadata**

### Task 3: Optional Vector and Reranker Hooks

- [x] **Step 1: Add tests for optional Chroma adapter fallback behavior**
- [x] **Step 2: Add tests for flashrank reranker disabled-by-default behavior**
- [x] **Step 3: Implement Chroma/bge-m3 adapter as lazy optional class**
- [x] **Step 4: Implement flashrank reranker factory**
- [x] **Step 5: Document high-performance env switches**

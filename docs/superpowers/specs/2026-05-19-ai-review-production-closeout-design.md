# AI Review Production Closeout Design

## Goal

Finish the AI review production closeout items: connect the admin UI to candidate approval v2, document DB/ops requirements, verify LangGraph dependency behavior, configure service token rollout, run focused builds, and define log/metric collection.

## Approach

The admin UI will use the DB-backed v2 API first. JSONL remains available through an explicit `import-jsonl` action so existing queues can be migrated without removing the v1 fallback path. Operational items are captured in a deployment checklist because they depend on environment configuration and log infrastructure outside this workspace.

## UI Behavior

The candidate page displays v2 lifecycle status (`PENDING`, `APPROVED`, `REJECTED`, `MERGED`) and supports:

- edit-and-approve with `reviewerEditedAnswer`
- reject with `rejectedReason`
- merge into another candidate by id
- import JSONL rows into v2 DB

## Operations

The checklist covers Hibernate schema review, required environment variables, LangGraph optional dependency verification, service token rollout, full build commands, and log collection queries for fallback, retrieval miss, candidate capture, and backlog.

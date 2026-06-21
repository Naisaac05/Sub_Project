---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 불변(Immutable) 벡터 인덱스 마이그레이션 전략 보고서"

---

# AI Review Immutable Vector Index Strategy

> Date: 2026-05-26  
> Scope: P2 immutable vector index strategy for local LLM AI Review orchestration

## Goal

Vector index changes must be explicit, auditable, and able to invalidate stale answer cache entries without deleting the whole cache file.

## Active manifest contract

`ai/app/vectorstore/index_manifest.json` now uses the v2 manifest shape:

```json
{
  "schema_version": 2,
  "knowledge_index_version": "ki-<manifest_hash_prefix>",
  "manifest_hash": "<sha256-of-entries>",
  "cache_namespace_version": "ki-<manifest_hash_prefix>",
  "created_at": "2026-05-26T00:00:00+00:00",
  "previous_versions": [],
  "entries": {}
}
```

The `manifest_hash` is computed from canonicalized `entries`. If `AI_REVIEW_KNOWLEDGE_INDEX_VERSION` is not set, the active version is derived as `ki-<first 12 chars of manifest_hash>`.

## Cache namespace

`ai/app/workflow/answer_cache.py` includes the active cache namespace in `cache_key_for()`.

Resolution order:

1. `AI_REVIEW_CACHE_NAMESPACE_VERSION`
2. active manifest `cache_namespace_version`
3. active manifest `knowledge_index_version`
4. `ki-unversioned`

When the vector index manifest changes, the generated `knowledge_index_version` and default cache namespace change too. Old answers remain on disk, but they are no longer read for the new namespace.

## Rollback

Before overwriting an existing manifest, both Python reindex and Spring approval reindex snapshot the previous manifest under:

```text
ai/app/vectorstore/manifests/<knowledge_index_version>.json
```

Rollback procedure:

1. Stop traffic or enable `AI_REVIEW_CACHE_ONLY` / `AI_REVIEW_TEMPLATE_FALLBACK_ONLY` as needed.
2. Replace the active `index_manifest.json` with the previous snapshot.
3. Repoint or rebuild the vector store to the matching index contents.
4. Restart the AI service so answer cache namespace lookup reads the restored manifest.

## Writers

- Python bulk/changed reindex: `ai/scripts/reindex_knowledge.py`
- Spring approval reindex: `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`

Both writers preserve v1 manifest compatibility by reading `entries` from old manifests and writing v2 on the next successful update.

## Verification

Focused regression coverage:

- Python manifest helper and answer cache namespace:
  `python -m unittest tests.test_index_manifest tests.test_answer_cache`
- Chroma reindex regression:
  `python -m unittest tests.test_chroma_reindex`
- Spring approval reindex manifest writer:
  `.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest`

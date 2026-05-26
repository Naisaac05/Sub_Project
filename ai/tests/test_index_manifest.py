import json
import os
from pathlib import Path
import tempfile
import unittest

from app.knowledge.index_manifest import (
    active_cache_namespace_version,
    build_manifest_payload,
    manifest_hash_for_entries,
    snapshot_previous_manifest,
)
from scripts.reindex_knowledge import reindex_changed_knowledge


AI_ROOT = Path(__file__).resolve().parents[1]


class IndexManifestTest(unittest.TestCase):
    def setUp(self):
        self._previous_index_version = os.environ.get("AI_REVIEW_KNOWLEDGE_INDEX_VERSION")
        self._previous_cache_namespace = os.environ.get("AI_REVIEW_CACHE_NAMESPACE_VERSION")
        os.environ.pop("AI_REVIEW_KNOWLEDGE_INDEX_VERSION", None)
        os.environ.pop("AI_REVIEW_CACHE_NAMESPACE_VERSION", None)

    def tearDown(self):
        if self._previous_index_version is None:
            os.environ.pop("AI_REVIEW_KNOWLEDGE_INDEX_VERSION", None)
        else:
            os.environ["AI_REVIEW_KNOWLEDGE_INDEX_VERSION"] = self._previous_index_version
        if self._previous_cache_namespace is None:
            os.environ.pop("AI_REVIEW_CACHE_NAMESPACE_VERSION", None)
        else:
            os.environ["AI_REVIEW_CACHE_NAMESPACE_VERSION"] = self._previous_cache_namespace

    def test_build_manifest_payload_uses_deterministic_hash_version_and_cache_namespace(self):
        entries = {
            "spring-n-plus-one": {
                "concept_id": "spring-n-plus-one",
                "path": "app/knowledge/concepts/spring/n-plus-one.md",
                "content_hash": "content",
                "metadata_hash": "metadata",
            }
        }

        payload = build_manifest_payload(entries)

        expected_hash = manifest_hash_for_entries(entries)
        self.assertEqual(payload["schema_version"], 2)
        self.assertEqual(payload["manifest_hash"], expected_hash)
        self.assertEqual(payload["knowledge_index_version"], f"ki-{expected_hash[:12]}")
        self.assertEqual(payload["cache_namespace_version"], payload["knowledge_index_version"])
        self.assertEqual(payload["entries"], entries)

    def test_build_manifest_payload_allows_explicit_index_and_cache_namespace(self):
        os.environ["AI_REVIEW_KNOWLEDGE_INDEX_VERSION"] = "ki-release-2026-05-26"
        os.environ["AI_REVIEW_CACHE_NAMESPACE_VERSION"] = "answers-release-2026-05-26"

        payload = build_manifest_payload({"concept": {"content_hash": "a"}})

        self.assertEqual(payload["knowledge_index_version"], "ki-release-2026-05-26")
        self.assertEqual(payload["cache_namespace_version"], "answers-release-2026-05-26")

    def test_snapshot_previous_manifest_preserves_rollback_copy_and_previous_metadata(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            manifest_path = Path(tmpdir) / "index_manifest.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "schema_version": 2,
                        "knowledge_index_version": "ki-old",
                        "manifest_hash": "old-hash",
                        "entries": {},
                    }
                ),
                encoding="utf-8",
            )

            metadata = snapshot_previous_manifest(manifest_path)

            self.assertEqual(metadata["knowledge_index_version"], "ki-old")
            self.assertEqual(metadata["manifest_hash"], "old-hash")
            snapshot_path = Path(metadata["path"])
            self.assertTrue(snapshot_path.exists())
            self.assertEqual(json.loads(snapshot_path.read_text(encoding="utf-8"))["manifest_hash"], "old-hash")

    def test_active_cache_namespace_reads_manifest_when_env_is_absent(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            manifest_path = Path(tmpdir) / "index_manifest.json"
            manifest_path.write_text(
                json.dumps({"cache_namespace_version": "answers-from-manifest"}),
                encoding="utf-8",
            )

            self.assertEqual(active_cache_namespace_version(manifest_path), "answers-from-manifest")

    def test_reindex_changed_knowledge_writes_v2_manifest_and_previous_snapshot(self):
        with tempfile.TemporaryDirectory(dir=AI_ROOT) as tmpdir:
            temp_root = Path(tmpdir)
            concept_root = temp_root / "concepts"
            concept_root.mkdir()
            card_path = concept_root / "redis-cache.md"
            card_path.write_text(
                """
---
id: redis-cache
category: backend
---

# Redis Cache

## 핵심 설명
Redis cache keeps hot data close to the application.
""".strip(),
                encoding="utf-8",
            )
            manifest_path = temp_root / "index_manifest.json"
            manifest_path.write_text(
                json.dumps({"version": 1, "entries": {"old": {"content_hash": "old"}}}),
                encoding="utf-8",
            )

            report = reindex_changed_knowledge(concept_root, manifest_path)

            self.assertEqual(report["status"], "passed")
            self.assertEqual(report["cache_namespace_version"], report["knowledge_index_version"])
            payload = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual(payload["schema_version"], 2)
            self.assertEqual(payload["manifest_hash"], report["manifest_hash"])
            self.assertEqual(payload["entries"]["redis-cache"]["concept_id"], "redis-cache")
            self.assertTrue((manifest_path.parent / "manifests").exists())


if __name__ == "__main__":
    unittest.main()

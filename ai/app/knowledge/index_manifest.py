from __future__ import annotations

import hashlib
import json
import os
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


DEFAULT_MANIFEST_PATH = Path(__file__).resolve().parents[1] / "vectorstore" / "index_manifest.json"
UNVERSIONED_CACHE_NAMESPACE = "ki-unversioned"


def manifest_hash_for_entries(entries: dict[str, dict[str, Any]]) -> str:
    canonical = json.dumps(entries, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def build_manifest_payload(
    entries: dict[str, dict[str, Any]],
    *,
    previous_versions: list[dict[str, Any]] | None = None,
    created_at: str | None = None,
) -> dict[str, Any]:
    manifest_hash = manifest_hash_for_entries(entries)
    knowledge_index_version = (
        os.environ.get("AI_REVIEW_KNOWLEDGE_INDEX_VERSION", "").strip()
        or f"ki-{manifest_hash[:12]}"
    )
    cache_namespace_version = (
        os.environ.get("AI_REVIEW_CACHE_NAMESPACE_VERSION", "").strip()
        or knowledge_index_version
    )
    return {
        "schema_version": 2,
        "knowledge_index_version": knowledge_index_version,
        "manifest_hash": manifest_hash,
        "cache_namespace_version": cache_namespace_version,
        "created_at": created_at or datetime.now(UTC).isoformat(),
        "previous_versions": previous_versions or [],
        "entries": entries,
    }


def active_cache_namespace_version(manifest_path: Path = DEFAULT_MANIFEST_PATH) -> str:
    explicit = os.environ.get("AI_REVIEW_CACHE_NAMESPACE_VERSION", "").strip()
    if explicit:
        return explicit
    manifest = read_manifest_payload(manifest_path)
    value = manifest.get("cache_namespace_version") or manifest.get("knowledge_index_version")
    return str(value) if value else UNVERSIONED_CACHE_NAMESPACE


def read_manifest_payload(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return payload if isinstance(payload, dict) else {}


def read_manifest_entries(path: Path) -> dict[str, dict[str, Any]]:
    entries = read_manifest_payload(path).get("entries", {})
    if not isinstance(entries, dict):
        return {}
    return {str(key): value for key, value in entries.items() if isinstance(value, dict)}


def snapshot_previous_manifest(manifest_path: Path) -> dict[str, str] | None:
    if not manifest_path.exists() or not manifest_path.is_file():
        return None
    payload = read_manifest_payload(manifest_path)
    entries = payload.get("entries", {}) if isinstance(payload.get("entries", {}), dict) else {}
    manifest_hash = str(payload.get("manifest_hash") or manifest_hash_for_entries(entries))
    knowledge_index_version = str(
        payload.get("knowledge_index_version")
        or f"ki-{manifest_hash[:12]}"
    )
    snapshot_dir = manifest_path.parent / "manifests"
    snapshot_path = snapshot_dir / f"{knowledge_index_version}.json"
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    snapshot_path.write_text(manifest_path.read_text(encoding="utf-8"), encoding="utf-8")
    return {
        "knowledge_index_version": knowledge_index_version,
        "manifest_hash": manifest_hash,
        "path": str(snapshot_path),
    }

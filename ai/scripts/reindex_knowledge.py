from __future__ import annotations

import argparse
from collections.abc import Callable
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.documents import CONCEPT_ROOT, load_concept_cards
from app.rag.documents import ConceptCard
from app.knowledge.index_manifest import (
    build_manifest_payload,
    read_manifest_entries,
    read_manifest_payload,
    snapshot_previous_manifest,
)


DEFAULT_MANIFEST = ROOT / "app" / "vectorstore" / "index_manifest.json"
DEFAULT_CHROMA_PATH = ROOT / "app" / "vectorstore" / "chroma"
DEFAULT_CHROMA_COLLECTION = "devmatch_concepts"
DEFAULT_EMBEDDING_MODEL = "BAAI/bge-m3"


@dataclass(frozen=True)
class ChromaIndexDependencies:
    client_factory: Callable[[Path], object]
    embed: Callable[[list[str]], list[list[float]]]


def reindex_changed_knowledge(
    concept_root: Path = CONCEPT_ROOT,
    manifest_path: Path = DEFAULT_MANIFEST,
) -> dict[str, object]:
    previous = read_manifest_entries(manifest_path)
    previous_payload = read_manifest_payload(manifest_path)
    next_entries: dict[str, dict[str, str]] = {}
    changed: list[str] = []
    unchanged: list[str] = []

    for card in load_concept_cards(concept_root):
        relative_path = card.path.relative_to(ROOT).as_posix()
        content_hash = _hash(card.path.read_text(encoding="utf-8"))
        metadata_hash = _hash(json.dumps(card.metadata, ensure_ascii=False, sort_keys=True))
        entry = {
            "concept_id": card.concept_id,
            "path": relative_path,
            "content_hash": content_hash,
            "metadata_hash": metadata_hash,
        }
        next_entries[card.concept_id] = entry
        if previous.get(card.concept_id) == entry:
            unchanged.append(relative_path)
        else:
            changed.append(relative_path)

    try:
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        previous_version = snapshot_previous_manifest(manifest_path)
        previous_versions = list(previous_payload.get("previous_versions", []))
        if previous_version is not None:
            previous_versions.append(previous_version)
        manifest = build_manifest_payload(next_entries, previous_versions=previous_versions)
        manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True)
            + "\n",
            encoding="utf-8",
        )
    except OSError as exc:
        return {
            "status": "failed",
            "errors": [str(exc)],
            "changed": changed,
            "unchanged": unchanged,
        }

    return {
        "status": "passed",
        "manifest": str(manifest_path.relative_to(ROOT)),
        "knowledge_index_version": manifest["knowledge_index_version"],
        "manifest_hash": manifest["manifest_hash"],
        "cache_namespace_version": manifest["cache_namespace_version"],
        "changed": changed,
        "unchanged": unchanged,
    }


def build_chroma_documents(cards: list[ConceptCard]) -> list[dict[str, object]]:
    documents: list[dict[str, object]] = []
    for card in cards:
        content = _format_card_context(card)
        documents.append(
            {
                "id": card.concept_id,
                "document": content,
                "metadata": {
                    **card.metadata,
                    "concept_id": card.concept_id,
                    "title": card.title,
                    "path": str(card.path),
                },
            }
        )
    return documents


def index_chroma_knowledge(
    cards: list[ConceptCard] | None = None,
    concept_ids: list[str] | None = None,
    persist_path: Path = DEFAULT_CHROMA_PATH,
    collection_name: str = DEFAULT_CHROMA_COLLECTION,
    embedding_model: str = DEFAULT_EMBEDDING_MODEL,
    dependencies: ChromaIndexDependencies | None = None,
    smoke_query: str | None = None,
) -> dict[str, object]:
    selected_cards = cards if cards is not None else load_concept_cards()
    if concept_ids:
        requested = set(concept_ids)
        selected_cards = [card for card in selected_cards if card.concept_id in requested]
        missing = sorted(requested - {card.concept_id for card in selected_cards})
        if missing:
            return {
                "status": "failed",
                "errors": [f"concept id not found: {', '.join(missing)}"],
                "indexed": 0,
            }
    documents = build_chroma_documents(selected_cards)
    if not documents:
        return {
            "status": "skipped",
            "reason": "no concept cards found",
            "indexed": 0,
        }

    deps = dependencies or _default_chroma_dependencies(embedding_model)
    try:
        client = deps.client_factory(persist_path)
        collection = client.get_or_create_collection(collection_name)
        texts = [str(item["document"]) for item in documents]
        embeddings = deps.embed(texts)
        collection.upsert(
            ids=[str(item["id"]) for item in documents],
            documents=texts,
            metadatas=[item["metadata"] for item in documents],
            embeddings=embeddings,
        )
        smoke = _run_smoke_query(collection, deps, smoke_query) if smoke_query else None
    except (ImportError, OSError, RuntimeError) as exc:
        return {
            "status": "skipped",
            "reason": str(exc),
            "indexed": 0,
        }
    except Exception as exc:
        return {
            "status": "failed",
            "errors": [str(exc)],
            "indexed": 0,
        }

    report: dict[str, object] = {
        "status": "passed",
        "indexed": len(documents),
        "collection": collection_name,
        "persist_path": str(persist_path),
        "embedding_model": embedding_model,
    }
    if smoke is not None:
        report["smoke"] = smoke
    return report


def _default_chroma_dependencies(model_name: str) -> ChromaIndexDependencies:
    def client_factory(path: Path):
        import chromadb

        return chromadb.PersistentClient(path=str(path))

    embedder = None

    def embed(texts: list[str]) -> list[list[float]]:
        nonlocal embedder
        if embedder is None:
            from sentence_transformers import SentenceTransformer

            embedder = SentenceTransformer(model_name)
        return embedder.encode(texts, normalize_embeddings=True).tolist()

    return ChromaIndexDependencies(client_factory=client_factory, embed=embed)


def _run_smoke_query(collection, deps: ChromaIndexDependencies, query: str) -> dict[str, object]:
    query_embedding = deps.embed([query])[0]
    payload = collection.query(
        query_embeddings=[query_embedding],
        n_results=1,
        include=["documents", "metadatas", "distances"],
    )
    ids = payload.get("ids", [[]])[0]
    metadatas = payload.get("metadatas", [[]])[0]
    if not ids:
        return {"status": "failed", "reason": "no results"}
    metadata = metadatas[0] if metadatas else {}
    return {
        "status": "passed",
        "top_id": ids[0],
        "top_concept_id": metadata.get("concept_id", ids[0]),
    }


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _format_card_context(card: ConceptCard) -> str:
    sections = "\n\n".join(
        f"## {name}\n{content}" for name, content in card.sections.items()
    )
    return f"# {card.title}\n\n{sections}".strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Reindex AI review knowledge.")
    parser.add_argument("--chroma", action="store_true", help="Build/update Chroma bge-m3 index.")
    parser.add_argument("--chroma-path", default=str(DEFAULT_CHROMA_PATH))
    parser.add_argument("--collection", default=DEFAULT_CHROMA_COLLECTION)
    parser.add_argument("--embedding-model", default=DEFAULT_EMBEDDING_MODEL)
    parser.add_argument("--smoke-query", default="")
    parser.add_argument("--concept-id", action="append", default=[])
    parser.add_argument("--fail-on-chroma-skip", action="store_true")
    args = parser.parse_args()

    manifest_report = reindex_changed_knowledge()
    report: dict[str, object] = {"manifest": manifest_report}
    if args.chroma:
        report["chroma"] = index_chroma_knowledge(
            concept_ids=args.concept_id or None,
            persist_path=Path(args.chroma_path),
            collection_name=args.collection,
            embedding_model=args.embedding_model,
            smoke_query=args.smoke_query or None,
        )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    statuses = [manifest_report.get("status")]
    if "chroma" in report:
        statuses.append(report["chroma"].get("status"))  # type: ignore[index, union-attr]
    if args.fail_on_chroma_skip and report.get("chroma", {}).get("status") == "skipped":  # type: ignore[union-attr]
        return 1
    return 0 if all(status in {"passed", "skipped"} for status in statuses) else 1


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import argparse
import json
import math
import os
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from app.rag.documents import ConceptCard, load_concept_cards
from app.rag.retriever import BM25RetrieverAdapter, RetrievedContext


DATASET_PATH = Path(__file__).resolve().parent / "evals" / "golden_dataset.jsonl"
DEFAULT_K_VALUES = [1, 5, 10, 20]


@dataclass(frozen=True)
class RetrievalCase:
    case_id: str
    question: str
    expected_concepts: list[str]
    forbidden_concepts: list[str]


@dataclass(frozen=True)
class RetrieverCandidate:
    name: str
    retrieve: Callable[[str, int], list[RetrievedContext]]


class DenseBgeM3Retriever:
    def __init__(
        self,
        cards: list[ConceptCard] | None = None,
        model_name: str = "BAAI/bge-m3",
        allow_model_download: bool = False,
        model_factory: Callable[..., object] | None = None,
    ):
        self.cards = cards or load_concept_cards()
        self.model_name = model_name
        self.allow_model_download = allow_model_download
        self.model_factory = model_factory
        self._model = None
        self._doc_embeddings = None

    def retrieve(self, query: str, limit: int = 20) -> list[RetrievedContext]:
        if not query.strip() or not self.cards:
            return []
        model = self._load_model()
        doc_embeddings = self._load_doc_embeddings(model)
        query_embedding = model.encode([query], normalize_embeddings=True)[0]
        scored = []
        for card, embedding in zip(self.cards, doc_embeddings, strict=True):
            score = _dot(query_embedding, embedding)
            scored.append(
                RetrievedContext(
                    concept_id=card.concept_id,
                    title=card.title,
                    content=card.searchable_text,
                    score=float(score),
                    metadata={"retriever": "dense_bge_m3"},
                )
            )
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:limit]

    def _load_model(self):
        if self._model is None:
            if not self.allow_model_download:
                os.environ["HF_HUB_OFFLINE"] = "1"
                os.environ["TRANSFORMERS_OFFLINE"] = "1"
            if self.model_factory is None:
                from sentence_transformers import SentenceTransformer

                self.model_factory = SentenceTransformer
            self._model = self.model_factory(
                self.model_name,
                local_files_only=not self.allow_model_download,
            )
        return self._model

    def _load_doc_embeddings(self, model):
        if self._doc_embeddings is None:
            texts = [card.searchable_text for card in self.cards]
            self._doc_embeddings = model.encode(texts, normalize_embeddings=True)
        return self._doc_embeddings


def load_eval_cases(path: Path = DATASET_PATH) -> list[RetrievalCase]:
    cases = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        question = str(row.get("question") or "").strip()
        expected = [str(item) for item in row.get("expected_concepts", [])]
        forbidden = [str(item) for item in row.get("forbidden_concepts", [])]
        if not question:
            raise ValueError(f"empty question at {path}:{line_number}")
        cases.append(
            RetrievalCase(
                case_id=str(row.get("id") or line_number),
                question=question,
                expected_concepts=expected,
                forbidden_concepts=forbidden,
            )
        )
    return cases


def evaluate_ranking(
    expected_concepts: list[str],
    ranked_concepts: list[str],
    k_values: list[int] | None = None,
    forbidden_concepts: list[str] | None = None,
) -> dict[str, float]:
    k_values = k_values or DEFAULT_K_VALUES
    expected = set(expected_concepts)
    forbidden = set(forbidden_concepts or [])
    metrics: dict[str, float] = {}

    for k in k_values:
        top_k = ranked_concepts[:k]
        hits = [concept for concept in top_k if concept in expected]
        forbidden_hits = [concept for concept in top_k if concept in forbidden]
        metrics[f"recall@{k}"] = len(set(hits)) / len(expected) if expected else 1.0
        metrics[f"precision@{k}"] = len(hits) / k if k else 0.0
        metrics[f"hit@{k}"] = 1.0 if hits or not expected else 0.0
        metrics[f"ndcg@{k}"] = _ndcg_at_k(top_k, expected, k)
        metrics[f"forbidden_hit@{k}"] = 1.0 if forbidden_hits else 0.0

    first_rank = next(
        (index + 1 for index, concept in enumerate(ranked_concepts) if concept in expected),
        None,
    )
    metrics["mrr"] = 1.0 / first_rank if first_rank else (1.0 if not expected else 0.0)
    return metrics


def evaluate_candidate(
    candidate: RetrieverCandidate,
    cases: list[RetrievalCase],
    k_values: list[int],
    limit: int,
) -> dict[str, object]:
    totals: dict[str, float] = {}
    latencies = []
    evaluated = 0
    try:
        for case in cases:
            started = time.perf_counter()
            results = candidate.retrieve(case.question, limit)
            latencies.append((time.perf_counter() - started) * 1000)
            ranked = [item.concept_id for item in results]
            metrics = evaluate_ranking(
                case.expected_concepts,
                ranked,
                k_values=k_values,
                forbidden_concepts=case.forbidden_concepts,
            )
            for key, value in metrics.items():
                totals[key] = totals.get(key, 0.0) + value
            evaluated += 1
    except Exception as exc:
        return {
            "retriever": candidate.name,
            "available": False,
            "reason": f"{type(exc).__name__}: {exc}",
            "total": len(cases),
        }

    report = {
        "retriever": candidate.name,
        "available": True,
        "total": evaluated,
        "avg_latency_ms": round(sum(latencies) / len(latencies), 1) if latencies else 0.0,
    }
    for key in sorted(totals):
        report[key] = round(totals[key] / max(evaluated, 1), 3)
    return report


def fuse_rrf(
    ranked_by_source: dict[str, list[RetrievedContext]],
    limit: int,
    rrf_k: int = 60,
) -> list[RetrievedContext]:
    scores: dict[str, float] = {}
    items: dict[str, RetrievedContext] = {}
    sources: dict[str, list[str]] = {}
    for source, results in ranked_by_source.items():
        for rank, item in enumerate(results, start=1):
            scores[item.concept_id] = scores.get(item.concept_id, 0.0) + 1.0 / (rrf_k + rank)
            items.setdefault(item.concept_id, item)
            sources.setdefault(item.concept_id, []).append(source)
    return _merged_results(scores, items, sources, limit)


def fuse_weighted_sum(
    ranked_by_source: dict[str, list[RetrievedContext]],
    weights: dict[str, float],
    limit: int,
) -> list[RetrievedContext]:
    scores: dict[str, float] = {}
    items: dict[str, RetrievedContext] = {}
    sources: dict[str, list[str]] = {}
    for source, results in ranked_by_source.items():
        if not results:
            continue
        max_score = max(abs(item.score) for item in results) or 1.0
        weight = weights.get(source, 1.0)
        for item in results:
            scores[item.concept_id] = scores.get(item.concept_id, 0.0) + weight * (item.score / max_score)
            items.setdefault(item.concept_id, item)
            sources.setdefault(item.concept_id, []).append(source)
    return _merged_results(scores, items, sources, limit)


def build_candidates(
    names: list[str],
    limit: int,
    model_name: str,
    allow_model_download: bool,
    bm25_weight: float,
    dense_weight: float,
) -> list[RetrieverCandidate]:
    bm25 = BM25RetrieverAdapter()
    dense = DenseBgeM3Retriever(model_name=model_name, allow_model_download=allow_model_download)
    candidates: list[RetrieverCandidate] = []
    for name in names:
        normalized = name.strip().lower()
        if normalized == "bm25":
            candidates.append(RetrieverCandidate("bm25", lambda query, top_k: bm25.retrieve(query, top_k)))
        elif normalized in {"dense", "dense_bge_m3"}:
            candidates.append(RetrieverCandidate("dense_bge_m3", lambda query, top_k: dense.retrieve(query, top_k)))
        elif normalized in {"rrf", "hybrid_rrf"}:
            candidates.append(
                RetrieverCandidate(
                    "hybrid_rrf_bm25_dense",
                    lambda query, top_k: fuse_rrf(
                        {
                            "bm25": bm25.retrieve(query, limit),
                            "dense_bge_m3": dense.retrieve(query, limit),
                        },
                        limit=top_k,
                    ),
                )
            )
        elif normalized in {"weighted", "weighted_sum", "hybrid_weighted"}:
            candidates.append(
                RetrieverCandidate(
                    "hybrid_weighted_bm25_dense",
                    lambda query, top_k: fuse_weighted_sum(
                        {
                            "bm25": bm25.retrieve(query, limit),
                            "dense_bge_m3": dense.retrieve(query, limit),
                        },
                        weights={"bm25": bm25_weight, "dense_bge_m3": dense_weight},
                        limit=top_k,
                    ),
                )
            )
        else:
            raise ValueError(f"unknown retriever: {name}")
    return candidates


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate BM25, BGE-M3 dense, and hybrid retrieval candidates.")
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--retrievers", default="bm25,dense,rrf,weighted")
    parser.add_argument("--k", default="1,5,10,20")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--model-name", default="BAAI/bge-m3")
    parser.add_argument("--allow-model-download", action="store_true")
    parser.add_argument("--bm25-weight", type=float, default=0.5)
    parser.add_argument("--dense-weight", type=float, default=0.5)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    k_values = [int(value) for value in args.k.split(",") if value.strip()]
    names = [value for value in args.retrievers.split(",") if value.strip()]
    cases = load_eval_cases(args.dataset)
    candidates = build_candidates(
        names=names,
        limit=args.limit,
        model_name=args.model_name,
        allow_model_download=args.allow_model_download,
        bm25_weight=args.bm25_weight,
        dense_weight=args.dense_weight,
    )
    reports = [evaluate_candidate(candidate, cases, k_values, args.limit) for candidate in candidates]
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0


def _merged_results(
    scores: dict[str, float],
    items: dict[str, RetrievedContext],
    sources: dict[str, list[str]],
    limit: int,
) -> list[RetrievedContext]:
    merged = []
    for concept_id, score in scores.items():
        item = items[concept_id]
        metadata = dict(item.metadata)
        metadata["retriever_sources"] = ",".join(sources[concept_id])
        merged.append(
            RetrievedContext(
                concept_id=item.concept_id,
                title=item.title,
                content=item.content,
                score=score,
                metadata=metadata,
            )
        )
    merged.sort(key=lambda item: item.score, reverse=True)
    return merged[:limit]


def _ndcg_at_k(ranked: list[str], expected: set[str], k: int) -> float:
    if not expected:
        return 1.0
    dcg = 0.0
    for index, concept in enumerate(ranked[:k], start=1):
        if concept in expected:
            dcg += 1.0 / math.log2(index + 1)
    ideal_hits = min(len(expected), k)
    idcg = sum(1.0 / math.log2(index + 1) for index in range(1, ideal_hits + 1))
    return dcg / idcg if idcg else 0.0


def _dot(left, right) -> float:
    return float(sum(float(a) * float(b) for a, b in zip(left, right, strict=True)))


if __name__ == "__main__":
    raise SystemExit(main())

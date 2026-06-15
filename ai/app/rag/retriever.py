from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
import math
import os
from pathlib import Path
import re
import time

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard

STOPWORDS = {
    "문제",
    "이유",
    "때문",
    "때문에",
    "완전히",
    "관계없는",
}

TOKEN_ALIASES = {
    "aria": ("aria-label", "arialabel"),
    "arila": ("aria-label", "arialabel"),
    "label": ("aria-label", "arialabel"),
    "lable": ("aria-label", "arialabel"),
    "controller": ("controlleradvice", "@controlleradvice"),
    "advice": ("controlleradvice", "@controlleradvice"),
    "conrolleradvice": ("controlleradvice", "@controlleradvice"),
    "controlleradvice": ("@controlleradvice",),
    "pagnation": ("pagination",),
    "pagenation": ("pagination",),
}

Reranker = Callable[[list["RetrievedContext"], str], list["RetrievedContext"]]
RetrieverCallable = Callable[[str, int], list["RetrievedContext"]]
_KIWI = None

@dataclass(frozen=True)
class RetrievedContext:
    concept_id: str
    title: str
    content: str
    score: float
    metadata: dict[str, str]


class RetrieverAdapter:
    def retrieve(
        self,
        query: str,
        limit: int = 5,
        reranker: Reranker | None = None,
    ) -> list[RetrievedContext]:
        raise NotImplementedError


@dataclass(frozen=True)
class WeightedRetriever:
    name: str
    weight: float
    retrieve: RetrieverCallable


class LexicalRetrieverAdapter(RetrieverAdapter):
    def __init__(self, card_loader: Callable[[], list[RagCard]] = load_concept_cards):
        self.card_loader = card_loader

    def retrieve(
        self,
        query: str,
        limit: int = 5,
        reranker: Reranker | None = None,
    ) -> list[RetrievedContext]:
        query_token_list = tokenize(query)
        query_tokens = set(query_token_list)
        if not query_tokens:
            return []

        scored: list[RetrievedContext] = []
        for card in self.card_loader():
            score = score_card(card, query_tokens, query_token_list)
            if score <= 0:
                continue
            scored.append(
                RetrievedContext(
                    concept_id=card.concept_id,
                    title=card.title,
                    content=_format_card_context(card),
                    score=score,
                    metadata=card.metadata,
                )
            )

        scored.sort(key=lambda item: item.score, reverse=True)
        limited = scored[:limit]
        if reranker is not None:
            limited = reranker(limited, query)
        return limited[:limit]


class BM25RetrieverAdapter(RetrieverAdapter):
    def __init__(
        self,
        card_loader: Callable[[], list[RagCard]] = load_concept_cards,
        tokenizer: Callable[[str], list[str]] | None = None,
        k1: float = 1.5,
        b: float = 0.75,
    ):
        self.card_loader = card_loader
        self.tokenizer = tokenizer or (lambda text: select_tokenizer()(text))
        self.k1 = k1
        self.b = b

    def retrieve(
        self,
        query: str,
        limit: int = 5,
        reranker: Reranker | None = None,
    ) -> list[RetrievedContext]:
        query_tokens = self.tokenizer(query)
        if not query_tokens:
            return []

        cards = self.card_loader()
        if not cards:
            return []

        tokenized_docs = [self.tokenizer(card.searchable_text) for card in cards]
        avg_len = sum(len(doc) for doc in tokenized_docs) / max(1, len(tokenized_docs))
        doc_freq: dict[str, int] = {}
        for doc in tokenized_docs:
            for token in set(doc):
                doc_freq[token] = doc_freq.get(token, 0) + 1

        scored: list[RetrievedContext] = []
        for card, doc_tokens in zip(cards, tokenized_docs, strict=True):
            score = self._score(query_tokens, doc_tokens, doc_freq, len(cards), avg_len)
            if score <= 0:
                continue
            metadata = dict(card.metadata)
            metadata["retriever"] = "bm25"
            scored.append(
                RetrievedContext(
                    concept_id=card.concept_id,
                    title=card.title,
                    content=_format_card_context(card),
                    score=score,
                    metadata=metadata,
                )
            )

        scored.sort(key=lambda item: item.score, reverse=True)
        limited = scored[:limit]
        if reranker is not None:
            limited = reranker(limited, query)
        return limited[:limit]

    def _score(
        self,
        query_tokens: list[str],
        doc_tokens: list[str],
        doc_freq: dict[str, int],
        doc_count: int,
        avg_len: float,
    ) -> float:
        if not doc_tokens:
            return 0.0
        term_counts: dict[str, int] = {}
        for token in doc_tokens:
            term_counts[token] = term_counts.get(token, 0) + 1

        score = 0.0
        doc_len = len(doc_tokens)
        for token in set(query_tokens):
            freq = term_counts.get(token, 0)
            if freq <= 0:
                continue
            idf = math.log(1 + (doc_count - doc_freq.get(token, 0) + 0.5) / (doc_freq.get(token, 0) + 0.5))
            denom = freq + self.k1 * (1 - self.b + self.b * doc_len / max(avg_len, 1.0))
            score += idf * (freq * (self.k1 + 1)) / denom
        return score


class ChromaBgeRetrieverAdapter(RetrieverAdapter):
    def __init__(
        self,
        enabled: bool | None = None,
        persist_path: str | None = None,
        collection_name: str = "devmatch_concepts",
        model_name: str = "BAAI/bge-m3",
        card_loader: Callable[[], list[RagCard]] = load_concept_cards,
        auto_index: bool | None = None,
    ):
        self.enabled = _env_flag("AI_REVIEW_VECTOR_ENABLED", False) if enabled is None else enabled
        self.persist_path = persist_path or os.getenv("AI_REVIEW_CHROMA_PATH", "ai/app/vectorstore/chroma")
        self.collection_name = collection_name
        self.model_name = model_name
        self.card_loader = card_loader
        self.auto_index = _env_flag("AI_REVIEW_CHROMA_AUTOINDEX", False) if auto_index is None else auto_index
        self._collection = None
        self._embedder = None
        self._indexed = False
        print(f"[DEBUG] ChromaBgeRetrieverAdapter.__init__ -> enabled: {self.enabled}")

    def retrieve(
        self,
        query: str,
        limit: int = 5,
        reranker: Reranker | None = None,
    ) -> list[RetrievedContext]:
        print(f"[DEBUG] Chroma.retrieve -> query: '{query[:50]}...', limit: {limit}")
        if not self.enabled:
            print("[DEBUG] Chroma.retrieve -> empty (enabled=False)")
            return []
        try:
            collection = self._load_collection()
            query_embedding = self._embed([query])[0]
            payload = collection.query(
                query_embeddings=[query_embedding],
                n_results=limit,
                include=["documents", "metadatas", "distances"],
            )
        except Exception as e:
            print(f"[DEBUG] Chroma.retrieve -> empty (Exception: {e})")
            return []

        results: list[RetrievedContext] = []
        ids = payload.get("ids", [[]])[0]
        documents = payload.get("documents", [[]])[0]
        metadatas = payload.get("metadatas", [[]])[0]
        distances = payload.get("distances", [[]])[0]
        
        print(f"[DEBUG] Chroma.retrieve -> collection query returned ids: {ids}")
        print(f"[DEBUG] Chroma.retrieve -> collection query returned distances: {distances}")
        print(f"[DEBUG] Chroma.retrieve -> collection query returned metadatas: {metadatas}")
        
        if not ids:
            print("[DEBUG] Chroma.retrieve -> empty (ids is empty)")

        for index, concept_id in enumerate(ids):
            metadata = dict(metadatas[index] or {})
            metadata["retriever"] = "chroma_bge_m3"
            distance = float(distances[index]) if index < len(distances) else 1.0
            
            meta_concept_id = metadata.get("concept_id")
            print(f"[DEBUG] Chroma.retrieve -> Map check: id='{concept_id}', meta_concept_id='{meta_concept_id}', match={concept_id == meta_concept_id}")

            results.append(
                RetrievedContext(
                    concept_id=str(metadata.get("concept_id") or concept_id),
                    title=str(metadata.get("title") or concept_id),
                    content=str(documents[index] if index < len(documents) else ""),
                    score=max(0.0, 1.0 - distance),
                    metadata=metadata,
                )
            )
        if not results:
            print("[DEBUG] Chroma.retrieve -> empty (final results list empty)")
        if reranker is not None:
            results = reranker(results, query)
        return results[:limit]

    def _load_collection(self):
        if self._collection is not None:
            return self._collection
        import chromadb

        client = chromadb.PersistentClient(path=self.persist_path)
        self._collection = client.get_or_create_collection(self.collection_name)
        print(f"[DEBUG] Chroma._load_collection -> Collection exists: {self._collection is not None}")
        if self.auto_index and not self._indexed:
            self._index_cards(self._collection)
            self._indexed = True
        return self._collection

    def _embed(self, texts: list[str]) -> list[list[float]]:
        if self._embedder is None:
            from sentence_transformers import SentenceTransformer

            self._embedder = SentenceTransformer(self.model_name)
        return self._embedder.encode(texts, normalize_embeddings=True).tolist()

    def _index_cards(self, collection) -> None:
        cards = self.card_loader()
        print(f"[DEBUG] Chroma._index_cards -> Input card count: {len(cards) if cards else 0}")
        if not cards:
            return
        ids = [card.concept_id for card in cards]
        documents = [
            card.retrieval.embedding_text if getattr(card, "retrieval", None) and getattr(card.retrieval, "embedding_text", None) else _format_card_context(card)
            for card in cards
        ]
        for i, doc in enumerate(documents[:3]):
            print(f"[DEBUG] Chroma._index_cards -> doc[{i}] preview: {doc[:80]!r}")
            
        metadatas = [
            {
                **card.metadata,
                "concept_id": card.concept_id,
                "title": card.title,
                "path": str(card.path),
            }
            for card in cards
        ]
        embeddings = self._embed(documents)
        collection.upsert(ids=ids, documents=documents, metadatas=metadatas, embeddings=embeddings)
        print(f"[DEBUG] Chroma._index_cards -> Upserted {len(ids)} cards. Collection count: {collection.count()}")


class FlashrankReranker:
    def __init__(self, enabled: bool = True, model_name: str | None = None):
        self.enabled = enabled
        self.model_name = model_name
        self._ranker = None

    def __call__(self, items: list[RetrievedContext], query: str) -> list[RetrievedContext]:
        if not self.enabled or len(items) < 2:
            return items
        try:
            ranker = self._load_ranker()
            passages = [
                {"id": item.concept_id, "text": item.content, "meta": {"index": index}}
                for index, item in enumerate(items)
            ]
            from flashrank import RerankRequest

            ranked = ranker.rerank(RerankRequest(query=query, passages=passages))
        except Exception:
            return items

        by_id = {item.concept_id: item for item in items}
        reranked: list[RetrievedContext] = []
        for passage in ranked:
            item = by_id.get(str(passage.get("id")))
            if item is None:
                continue
            score = float(passage.get("score", item.score))
            reranked.append(
                RetrievedContext(item.concept_id, item.title, item.content, score, item.metadata)
            )
        return reranked or items

    def _load_ranker(self):
        if self._ranker is not None:
            return self._ranker
        from flashrank import Ranker

        self._ranker = Ranker(model_name=self.model_name) if self.model_name else Ranker()
        return self._ranker


class HybridRetrieverAdapter(RetrieverAdapter):
    def __init__(
        self,
        retrievers: list[WeightedRetriever],
        fallback: RetrieverAdapter | None = None,
    ):
        self.retrievers = retrievers
        self.fallback = fallback

    def retrieve(
        self,
        query: str,
        limit: int = 5,
        reranker: Reranker | None = None,
    ) -> list[RetrievedContext]:
        if not self.retrievers:
            if self.fallback is None:
                return []
            return self.fallback.retrieve(query, limit=limit, reranker=reranker)

        merged: dict[str, RetrievedContext] = {}
        source_names: dict[str, list[str]] = {}
        for retriever in self.retrievers:
            try:
                items = retriever.retrieve(query, limit)
            except Exception:
                if self.fallback is not None:
                    return self.fallback.retrieve(query, limit=limit, reranker=reranker)
                return []
            for item in items:
                weighted_score = item.score * retriever.weight
                source_names.setdefault(item.concept_id, []).append(retriever.name)
                current = merged.get(item.concept_id)
                if current is None:
                    merged[item.concept_id] = RetrievedContext(
                        concept_id=item.concept_id,
                        title=item.title,
                        content=item.content,
                        score=weighted_score,
                        metadata=dict(item.metadata),
                    )
                    continue
                merged[item.concept_id] = RetrievedContext(
                    concept_id=current.concept_id,
                    title=current.title,
                    content=current.content,
                    score=current.score + weighted_score,
                    metadata=current.metadata,
                )

        results = []
        for item in merged.values():
            metadata = dict(item.metadata)
            metadata["retriever_sources"] = ",".join(source_names[item.concept_id])
            results.append(
                RetrievedContext(
                    concept_id=item.concept_id,
                    title=item.title,
                    content=item.content,
                    score=item.score,
                    metadata=metadata,
                )
            )

        if not results and self.fallback is not None:
            return self.fallback.retrieve(query, limit=limit, reranker=reranker)

        results.sort(key=lambda item: item.score, reverse=True)
        limited = results[:limit]
        if reranker is not None:
            limited = reranker(limited, query)
        return limited[:limit]


_LEXICAL_ADAPTER = LexicalRetrieverAdapter()
_BM25_ADAPTER = BM25RetrieverAdapter()


def select_retriever_adapter(kind: str | None = None) -> RetrieverAdapter:
    selected = (kind or os.getenv("AI_REVIEW_RAG_RETRIEVER", "lexical")).lower()
    if selected.startswith("hybrid"):
        profile = _hybrid_profile(selected)
        tokenizer = select_tokenizer("kiwipiepy" if profile == "high_performance" else None)
        bm25 = BM25RetrieverAdapter(tokenizer=tokenizer)
        retrievers = [
            WeightedRetriever(
                name="lexical",
                weight=float(os.getenv("AI_REVIEW_LEXICAL_WEIGHT", "0.4")),
                retrieve=lambda query, limit: _LEXICAL_ADAPTER.retrieve(query, limit),
            ),
            WeightedRetriever(
                name="bm25",
                weight=float(os.getenv("AI_REVIEW_BM25_WEIGHT", "0.6")),
                retrieve=lambda query, limit: bm25.retrieve(query, limit),
            ),
        ]
        if profile == "high_performance":
            vector = ChromaBgeRetrieverAdapter(enabled=True)
            retrievers.append(
                WeightedRetriever(
                    name="chroma_bge_m3",
                    weight=float(os.getenv("AI_REVIEW_VECTOR_WEIGHT", "0.8")),
                    retrieve=lambda query, limit: vector.retrieve(query, limit),
                )
            )
        return HybridRetrieverAdapter(
            retrievers,
            fallback=_LEXICAL_ADAPTER,
        )
    return _LEXICAL_ADAPTER


def retrieve_context(
    query: str,
    limit: int = 5,
    reranker: Reranker | None = None,
    adapter: RetrieverAdapter | None = None,
) -> list[RetrievedContext]:
    if limit <= 0:
        return []
    selected_adapter = adapter or select_retriever_adapter()
    selected_reranker = reranker if reranker is not None else build_optional_reranker()
    results = selected_adapter.retrieve(query, limit=limit, reranker=selected_reranker)
    _run_v2_shadow(query)
    return results


def _run_v2_shadow(query: str) -> None:
    try:
        from app.rag.documents import KNOWLEDGE_ROOT
        from app.rag.parallel_config import load_parallel_rag_config
        from app.rag.shadow import log_shadow_retrieval
        from app.schemas.rag_card import PayloadStatus, RagCard

        config = load_parallel_rag_config()
        if not config.shadow_mode:
            return
        cards = [
            card for card in load_concept_cards(KNOWLEDGE_ROOT / "concepts_v2")
            if isinstance(card, RagCard)
        ]
        started = time.perf_counter()
        results = LexicalRetrieverAdapter(card_loader=lambda: cards).retrieve(query, limit=3)
        latency_ms = (time.perf_counter() - started) * 1000
        intent = _shadow_intent(query)
        top_card = next((card for card in cards if results and card.card_id == results[0].concept_id), None)
        payload_hit = bool(
            top_card
            and top_card.review.payload_status.get(intent) == PayloadStatus.APPROVED
        )
        date = datetime.now().strftime("%Y-%m-%d")
        log_shadow_retrieval(
            Path(__file__).resolve().parents[2] / "logs" / f"shadow_test_{date}.log",
            query=query,
            intent=intent,
            results=results,
            payload_hit=payload_hit,
            fast_path_hit=payload_hit,
            latency_ms=latency_ms,
        )
    except Exception:
        return


def _shadow_intent(query: str) -> str:
    lowered = query.lower()
    if any(value in lowered for value in ("wrong", "틀린", "오답")):
        return "WRONG_ANSWER_REASON"
    if any(value in lowered for value in ("correct", "맞는", "정답")):
        return "ANSWER_REASON"
    return "CONCEPT_DEFINITION"


def build_optional_reranker(kind: str | None = None) -> Reranker | None:
    selected = (kind or os.getenv("AI_REVIEW_RERANKER", "none")).lower()
    if selected != "flashrank":
        return None
    return FlashrankReranker(enabled=True, model_name=os.getenv("AI_REVIEW_FLASHRANK_MODEL") or None)


def tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[A-Za-z0-9+#.]+|[가-힣]{2,}", text.lower())
    expanded: list[str] = []
    for token in tokens:
        if token in STOPWORDS:
            continue
        expanded.append(token)
        expanded.extend(TOKEN_ALIASES.get(token, ()))
        if token == "n":
            expanded.append("n+1")
    return expanded


def select_tokenizer(kind: str | None = None) -> Callable[[str], list[str]]:
    selected = (kind or os.getenv("AI_REVIEW_KOREAN_TOKENIZER", "regex")).lower()
    if selected != "kiwipiepy":
        return tokenize
    return _kiwipiepy_tokenize


def _kiwipiepy_tokenize(text: str) -> list[str]:
    global _KIWI
    try:
        if _KIWI is None:
            from kiwipiepy import Kiwi

            _KIWI = Kiwi()
        kiwi_tokens = [
            token.form.lower()
            for token in _KIWI.tokenize(text)
            if token.form and len(token.form.strip()) > 1
        ]
    except Exception:
        return tokenize(text)

    expanded = tokenize(text)
    for token in kiwi_tokens:
        if token not in STOPWORDS:
            expanded.append(token)
            expanded.extend(TOKEN_ALIASES.get(token, ()))
    return expanded


def _hybrid_profile(selected: str) -> str:
    if ":" in selected:
        return selected.split(":", 1)[1]
    return os.getenv("AI_REVIEW_RAG_PROFILE", "low_resource").lower()


def _env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.lower() in {"1", "true", "yes", "on"}


def score_card(card: RagCard, query_tokens: set[str], query_token_list: list[str] | None = None) -> float:
    text_tokens = set(tokenize(card.searchable_text))
    overlap = query_tokens & text_tokens
    if not overlap:
        return 0.0

    keyword_text = "\n".join(p.content for p in card.payloads.model_dump().values() if getattr(p, "intent", None) and p.intent.value == "CONCEPT_DEFINITION" and getattr(p, "payload_status", None) and p.payload_status.value == "approved")
    keyword_tokens = set(tokenize(keyword_text))
    title_tokens = set(tokenize(card.title))
    score = float(len(overlap))
    score += 2.0 * len(query_tokens & keyword_tokens)
    score += 1.5 * len(query_tokens & title_tokens)
    ordered_query_tokens = query_token_list or list(query_tokens)
    if _contains_phrase(ordered_query_tokens, tokenize(card.term)):
        score += 6.0
    elif any(_contains_phrase(ordered_query_tokens, tokenize(alias)) for alias in card.aliases):
        score += 4.0
    elif any(
        _contains_phrase(ordered_query_tokens, tokenize(keyword))
        for keyword in card.retrieval.boost_keywords
    ):
        score += 2.0
    return score


def _contains_phrase(query_tokens: list[str], phrase_tokens: list[str]) -> bool:
    if not phrase_tokens or len(phrase_tokens) > len(query_tokens):
        return False
    width = len(phrase_tokens)
    return any(
        query_tokens[index:index + width] == phrase_tokens
        for index in range(len(query_tokens) - width + 1)
    )


def _format_card_context(card: RagCard) -> str:
    sections = "\n\n".join(
        f"## {p.intent.value}\n{p.content}"
        for p in card.payloads.model_dump().values()
        if p and getattr(p, "payload_status", None) and p.payload_status.value == "approved"
    )
    title = getattr(card, 'title', card.term if hasattr(card, 'term') else card.card_id)
    return f"# {title}\n\n{sections}".strip()

from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
import hashlib
import json
import os
from pathlib import Path
import re

from app.ollama.embeddings import (
    EmbeddingError,
    OllamaEmbeddingClient,
    cosine_similarity,
    normalize_vector,
)
from app.workflow.intent import FreeQuestionIntent, classify_free_question_rule_based, extract_topic


IntentPrototypes = Mapping[str, Sequence[str]]
DEFAULT_CENTROID_CACHE_PATH = (
    Path(__file__).resolve().parents[1] / "vectorstore" / "intent_centroids.json"
)

DEFAULT_INTENT_PROTOTYPES: dict[str, tuple[str, ...]] = {
    "ANSWER_REASON": (
        "정답이 왜 맞는지 근거와 이유를 설명해줘",
        "이 문제에서 올바른 선택지가 정답인 이유가 뭐야",
    ),
    "WRONG_ANSWER_REASON": (
        "내가 선택한 답이 왜 틀렸는지 설명해줘",
        "오답인 이유와 놓친 개념을 알려줘",
    ),
    "CONCEPT_DEFINITION": (
        "이 프로그래밍 개념의 정의와 의미를 설명해줘",
        "이 기술 용어가 무엇인지 알려줘",
    ),
    "COMPARISON": (
        "두 프로그래밍 개념의 차이를 비교해줘",
        "둘 중 무엇이 어떻게 다른지 설명해줘",
    ),
    "EXAMPLE_REQUEST": (
        "이 개념을 코드 예시와 함께 보여줘",
        "구체적인 사용 예제를 알려줘",
    ),
    "PRACTICAL_USAGE": (
        "이 기술을 실무에서 언제 어떻게 사용하는지 알려줘",
        "현업 적용 방법과 사용 상황을 설명해줘",
    ),
    "DEBUG_OR_ERROR": (
        "코드에서 오류가 발생하는 원인과 해결 방법을 알려줘",
        "왜 동작하지 않는지 디버깅해줘",
    ),
    "FOLLOW_UP": (
        "방금 설명을 더 쉽게 다시 설명해줘",
        "그게 무슨 말인지 이어서 설명해줘",
    ),
    "OFF_TOPIC": (
        "프로그래밍 학습과 관계없는 일상 질문",
        "개발 공부와 무관한 주제의 질문",
    ),
    "UNKNOWN": (
        "의도를 알 수 없는 불완전하고 모호한 입력",
        "무슨 질문인지 판단할 수 없는 내용",
    ),
}


class EmbeddingIntentClassifier:
    def __init__(
        self,
        embed: Callable[[str], list[float]] | None = None,
        prototypes: IntentPrototypes = DEFAULT_INTENT_PROTOTYPES,
        min_similarity: float | None = None,
        min_margin: float | None = None,
        centroid_cache_path: Path | None = None,
        model_name: str | None = None,
    ):
        client = OllamaEmbeddingClient(model=model_name) if embed is None else None
        self.embed = embed or client.embed
        self.model_name = model_name or (client.model if client is not None else "injected")
        self.prototypes = prototypes
        self.centroid_cache_path = centroid_cache_path
        if centroid_cache_path is None and embed is None:
            self.centroid_cache_path = DEFAULT_CENTROID_CACHE_PATH
        self.min_similarity = (
            _env_float("AI_REVIEW_INTENT_MIN_SIMILARITY", 0.43)
            if min_similarity is None
            else min_similarity
        )
        self.min_margin = (
            _env_float("AI_REVIEW_INTENT_MIN_MARGIN", 0.005)
            if min_margin is None
            else min_margin
        )
        self._prototype_vectors: dict[str, list[float]] | None = None

    def classify(self, question: str) -> FreeQuestionIntent:
        if not isinstance(question, str) or not question.strip():
            return intent_from_label("UNKNOWN", "", confidence=0.0)
        try:
            prototype_vectors = self._load_prototype_vectors()
            query_vector = self.embed(question)
            scores = sorted(
                (
                    (cosine_similarity(query_vector, vector), label)
                    for label, vector in prototype_vectors.items()
                ),
                reverse=True,
            )
        except (EmbeddingError, OSError, TimeoutError, ValueError, TypeError):
            return intent_from_label("UNKNOWN", question, confidence=0.0)

        if not scores:
            return intent_from_label("UNKNOWN", question, confidence=0.0)
        best_score, best_label = scores[0]
        second_score = scores[1][0] if len(scores) > 1 else -1.0
        if best_score < self.min_similarity or best_score - second_score < self.min_margin:
            return intent_from_label("UNKNOWN", question, confidence=best_score)
        return intent_from_label(best_label, question, confidence=best_score)

    def _load_prototype_vectors(self) -> dict[str, list[float]]:
        if self._prototype_vectors is not None:
            return self._prototype_vectors
        persisted = self._read_persisted_vectors()
        if persisted is not None:
            self._prototype_vectors = persisted
            return persisted

        vectors: dict[str, list[float]] = {}
        for label, examples in self.prototypes.items():
            embedded = [self.embed(example) for example in examples]
            if not embedded:
                continue
            dimensions = {len(vector) for vector in embedded}
            if len(dimensions) != 1:
                raise EmbeddingError("Intent prototype vector dimensions do not match")
            centroid = [
                sum(vector[index] for vector in embedded) / len(embedded)
                for index in range(len(embedded[0]))
            ]
            vectors[label] = normalize_vector(centroid)
        self._prototype_vectors = vectors
        self._write_persisted_vectors(vectors)
        return vectors

    def _read_persisted_vectors(self) -> dict[str, list[float]] | None:
        path = self.centroid_cache_path
        if path is None or not path.is_file():
            return None
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            if payload.get("model") != self.model_name:
                return None
            if payload.get("prototype_hash") != _prototype_hash(self.prototypes):
                return None
            raw_vectors = payload.get("vectors")
            if not isinstance(raw_vectors, dict):
                return None
            vectors = {
                str(label): normalize_vector(vector)
                for label, vector in raw_vectors.items()
            }
        except (OSError, ValueError, TypeError, json.JSONDecodeError, EmbeddingError):
            return None
        return vectors or None

    def _write_persisted_vectors(self, vectors: dict[str, list[float]]) -> None:
        path = self.centroid_cache_path
        if path is None:
            return
        payload = {
            "schema_version": 1,
            "model": self.model_name,
            "prototype_hash": _prototype_hash(self.prototypes),
            "vectors": vectors,
        }
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(
                json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                encoding="utf-8",
            )
        except OSError:
            return


_DEFAULT_CLASSIFIER: EmbeddingIntentClassifier | None = None


def classify_free_question_with_embeddings(text: str) -> FreeQuestionIntent:
    global _DEFAULT_CLASSIFIER
    rule_intent = _obvious_rule_intent(text)
    if rule_intent is not None:
        return rule_intent
    if _DEFAULT_CLASSIFIER is None:
        _DEFAULT_CLASSIFIER = EmbeddingIntentClassifier()
    return _DEFAULT_CLASSIFIER.classify(text)


def _obvious_rule_intent(text: str) -> FreeQuestionIntent | None:
    if not isinstance(text, str):
        return None
    normalized = re.sub(r"\s+", "", text).lower()
    if not re.search(r"[a-z][a-z0-9+#.-]{1,}", text, re.IGNORECASE):
        return None
    if re.search(r"(?:가|이|은|는)?(?:뭐야|무엇이야|무엇인가|뭔데|란)\??$", normalized):
        intent = classify_free_question_rule_based(text)
        return FreeQuestionIntent(
            "concept_definition",
            "latest_question_only",
            intent.topic,
            max(intent.confidence, 0.95),
            False,
            "definition",
        )
    return None


def clear_embedding_intent_cache() -> None:
    global _DEFAULT_CLASSIFIER
    _DEFAULT_CLASSIFIER = None


def intent_from_label(label: str, question: str, confidence: float) -> FreeQuestionIntent:
    topic = extract_topic(question)
    if label in {"ANSWER_REASON", "WRONG_ANSWER_REASON"}:
        return FreeQuestionIntent(
            "wrong_answer_explanation",
            "original_context_mixed",
            topic,
            confidence,
            True,
            "explanation",
        )
    if label in {
        "CONCEPT_DEFINITION",
        "COMPARISON",
        "EXAMPLE_REQUEST",
        "PRACTICAL_USAGE",
        "DEBUG_OR_ERROR",
    }:
        sub_intent = {
            "COMPARISON": "comparison",
            "PRACTICAL_USAGE": "practical",
            "EXAMPLE_REQUEST": "related",
            "DEBUG_OR_ERROR": "related",
        }.get(label, "definition")
        return FreeQuestionIntent(
            "concept_definition",
            "latest_question_only",
            topic,
            confidence,
            False,
            sub_intent,
        )
    if label == "FOLLOW_UP":
        return FreeQuestionIntent(
            "follow_up",
            "original_context_mixed",
            topic,
            confidence,
            True,
            "follow_up",
        )
    if label == "OFF_TOPIC":
        return FreeQuestionIntent(
            "off_topic",
            "no_rag",
            topic,
            confidence,
            False,
            "off_topic",
        )
    if label == "UNKNOWN":
        return FreeQuestionIntent(
            "unknown",
            "fallback",
            topic,
            confidence,
            False,
            "unknown",
        )
    return FreeQuestionIntent(
        "general_question",
        "original_context_mixed",
        topic,
        confidence,
        False,
        "general",
    )


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


def _prototype_hash(prototypes: IntentPrototypes) -> str:
    canonical = json.dumps(
        {label: list(examples) for label, examples in sorted(prototypes.items())},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

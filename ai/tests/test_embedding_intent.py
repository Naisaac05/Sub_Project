import unittest
from unittest.mock import patch
from pathlib import Path
from tempfile import TemporaryDirectory

from app.ollama.embeddings import EmbeddingError
from app.workflow.embedding_intent import (
    EmbeddingIntentClassifier,
    classify_free_question_with_embeddings,
    clear_embedding_intent_cache,
    intent_from_label,
)


class EmbeddingIntentClassifierTest(unittest.TestCase):
    def tearDown(self):
        clear_embedding_intent_cache()

    def test_obvious_technical_definition_skips_embedding(self):
        with patch(
            "app.workflow.embedding_intent.EmbeddingIntentClassifier.classify",
            side_effect=AssertionError("obvious definition must not call embedding"),
        ):
            result = classify_free_question_with_embeddings("useEffect\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.topic, "useEffect")

    def test_classifies_by_nearest_bge_embedding_and_maps_workflow_intent(self):
        vectors = {
            "definition prototype": [1.0, 0.0],
            "debug prototype": [0.0, 1.0],
            "REST API가 뭐야?": [0.9, 0.1],
        }
        classifier = EmbeddingIntentClassifier(
            embed=lambda text: vectors[text],
            prototypes={
                "CONCEPT_DEFINITION": ("definition prototype",),
                "DEBUG_OR_ERROR": ("debug prototype",),
            },
            min_similarity=0.5,
            min_margin=0.1,
        )

        result = classifier.classify("REST API가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "REST API")

    def test_reuses_cached_prototype_embeddings(self):
        calls = []
        vectors = {
            "definition prototype": [1.0, 0.0],
            "debug prototype": [0.0, 1.0],
            "first question": [1.0, 0.0],
            "second question": [0.0, 1.0],
        }

        def embed(text):
            calls.append(text)
            return vectors[text]

        classifier = EmbeddingIntentClassifier(
            embed=embed,
            prototypes={
                "CONCEPT_DEFINITION": ("definition prototype",),
                "DEBUG_OR_ERROR": ("debug prototype",),
            },
            min_similarity=0.5,
            min_margin=0.1,
        )

        classifier.classify("first question")
        classifier.classify("second question")

        self.assertEqual(calls.count("definition prototype"), 1)
        self.assertEqual(calls.count("debug prototype"), 1)
        self.assertEqual(calls.count("first question"), 1)
        self.assertEqual(calls.count("second question"), 1)

    def test_reuses_persisted_centroids_without_reembedding_prototypes(self):
        with TemporaryDirectory() as tmp:
            cache_path = Path(tmp) / "intent-centroids.json"
            vectors = {
                "definition prototype": [1.0, 0.0],
                "debug prototype": [0.0, 1.0],
                "first question": [1.0, 0.0],
                "second question": [0.0, 1.0],
            }
            first_calls = []
            first = EmbeddingIntentClassifier(
                embed=lambda text: first_calls.append(text) or vectors[text],
                prototypes={
                    "CONCEPT_DEFINITION": ("definition prototype",),
                    "DEBUG_OR_ERROR": ("debug prototype",),
                },
                min_similarity=0.5,
                min_margin=0.1,
                centroid_cache_path=cache_path,
                model_name="bge-test",
            )

            first.classify("first question")

            second_calls = []
            second = EmbeddingIntentClassifier(
                embed=lambda text: second_calls.append(text) or vectors[text],
                prototypes={
                    "CONCEPT_DEFINITION": ("definition prototype",),
                    "DEBUG_OR_ERROR": ("debug prototype",),
                },
                min_similarity=0.5,
                min_margin=0.1,
                centroid_cache_path=cache_path,
                model_name="bge-test",
            )

            result = second.classify("second question")

            self.assertEqual(result.intent, "concept_definition")
            self.assertEqual(result.sub_intent, "related")
            self.assertEqual(second_calls, ["second question"])

    def test_low_confidence_maps_to_unknown(self):
        classifier = EmbeddingIntentClassifier(
            embed=lambda text: {
                "definition prototype": [1.0, 0.0],
                "debug prototype": [0.98, 0.2],
                "ambiguous question": [0.99, 0.1],
            }[text],
            prototypes={
                "CONCEPT_DEFINITION": ("definition prototype",),
                "DEBUG_OR_ERROR": ("debug prototype",),
            },
            min_similarity=0.5,
            min_margin=0.1,
        )

        result = classifier.classify("ambiguous question")

        self.assertEqual(result.intent, "unknown")
        self.assertEqual(result.sub_intent, "unknown")
        self.assertEqual(result.rag_policy, "fallback")

    def test_embedding_failure_maps_to_unknown_without_rule_fallback(self):
        def fail(_text):
            raise EmbeddingError("offline")

        classifier = EmbeddingIntentClassifier(
            embed=fail,
            prototypes={"CONCEPT_DEFINITION": ("definition prototype",)},
        )

        result = classifier.classify("REST API가 뭐야?")

        self.assertEqual(result.intent, "unknown")
        self.assertEqual(result.sub_intent, "unknown")

    def test_ten_class_labels_map_to_existing_workflow_contract(self):
        expectations = {
            "ANSWER_REASON": ("wrong_answer_explanation", "explanation", "original_context_mixed"),
            "WRONG_ANSWER_REASON": ("wrong_answer_explanation", "explanation", "original_context_mixed"),
            "CONCEPT_DEFINITION": ("concept_definition", "definition", "latest_question_only"),
            "COMPARISON": ("concept_definition", "comparison", "latest_question_only"),
            "EXAMPLE_REQUEST": ("concept_definition", "related", "latest_question_only"),
            "PRACTICAL_USAGE": ("concept_definition", "practical", "latest_question_only"),
            "DEBUG_OR_ERROR": ("concept_definition", "related", "latest_question_only"),
            "FOLLOW_UP": ("follow_up", "follow_up", "original_context_mixed"),
            "OFF_TOPIC": ("off_topic", "off_topic", "no_rag"),
            "UNKNOWN": ("unknown", "unknown", "fallback"),
        }

        for label, expected in expectations.items():
            with self.subTest(label=label):
                result = intent_from_label(label, "REST API가 뭐야?", confidence=0.9)
                self.assertEqual(
                    (result.intent, result.sub_intent, result.rag_policy),
                    expected,
                )


if __name__ == "__main__":
    unittest.main()

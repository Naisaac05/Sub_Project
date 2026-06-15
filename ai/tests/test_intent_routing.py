import unittest

from app.workflow.embedding_intent import EmbeddingIntentClassifier, intent_from_label


class FreeQuestionIntentTest(unittest.TestCase):
    def test_definition_embedding_uses_latest_question_and_extracts_topic(self):
        classifier = _classifier_for("CONCEPT_DEFINITION")

        result = classifier.classify("REST API가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "REST API")

    def test_comparison_embedding_maps_to_comparison_answer_style(self):
        result = _classifier_for("COMPARISON").classify("세션과 JWT 차이를 비교해줘")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "comparison")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_practical_embedding_maps_to_practical_answer_style(self):
        result = _classifier_for("PRACTICAL_USAGE").classify("실무에서는 이걸 언제 사용해?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "practical")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_follow_up_embedding_keeps_original_context(self):
        result = _classifier_for("FOLLOW_UP").classify("방금 설명을 더 쉽게 말해줘")

        self.assertEqual(result.intent, "follow_up")
        self.assertEqual(result.sub_intent, "follow_up")
        self.assertEqual(result.rag_policy, "original_context_mixed")

    def test_wrong_answer_embedding_keeps_original_context(self):
        result = _classifier_for("WRONG_ANSWER_REASON").classify("내 답은 왜 틀렸어?")

        self.assertEqual(result.intent, "wrong_answer_explanation")
        self.assertEqual(result.sub_intent, "explanation")
        self.assertEqual(result.rag_policy, "original_context_mixed")
        self.assertTrue(result.context_dependent)

    def test_unknown_embedding_uses_fallback_without_rag(self):
        result = intent_from_label("UNKNOWN", "모호한 질문", confidence=0.0)

        self.assertEqual(result.intent, "unknown")
        self.assertEqual(result.sub_intent, "unknown")
        self.assertEqual(result.rag_policy, "fallback")
        self.assertFalse(result.context_dependent)

    def test_off_topic_embedding_disables_rag(self):
        result = intent_from_label("OFF_TOPIC", "오늘 점심 뭐야?", confidence=0.9)

        self.assertEqual(result.intent, "off_topic")
        self.assertEqual(result.rag_policy, "no_rag")


def _classifier_for(selected_label: str) -> EmbeddingIntentClassifier:
    prototypes = {
        "ANSWER_REASON": ("ANSWER_REASON",),
        "WRONG_ANSWER_REASON": ("WRONG_ANSWER_REASON",),
        "CONCEPT_DEFINITION": ("CONCEPT_DEFINITION",),
        "COMPARISON": ("COMPARISON",),
        "EXAMPLE_REQUEST": ("EXAMPLE_REQUEST",),
        "PRACTICAL_USAGE": ("PRACTICAL_USAGE",),
        "DEBUG_OR_ERROR": ("DEBUG_OR_ERROR",),
        "FOLLOW_UP": ("FOLLOW_UP",),
        "OFF_TOPIC": ("OFF_TOPIC",),
        "UNKNOWN": ("UNKNOWN",),
    }
    labels = list(prototypes)
    dimensions = len(labels)
    vectors = {
        label: [1.0 if index == label_index else 0.0 for index in range(dimensions)]
        for label_index, label in enumerate(labels)
    }

    def embed(text: str) -> list[float]:
        return vectors.get(text, vectors[selected_label])

    return EmbeddingIntentClassifier(
        embed=embed,
        prototypes=prototypes,
        min_similarity=0.5,
        min_margin=0.1,
    )


if __name__ == "__main__":
    unittest.main()

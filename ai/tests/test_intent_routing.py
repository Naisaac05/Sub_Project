import unittest

from app.workflow.intent import classify_free_question


HIGH_CONFIDENCE_TOPIC_THRESHOLD = 0.8
FAST_PATH_CONFIDENCE_THRESHOLD = 0.95
SPECIFIC_TOPIC_CONFIDENCE_FLOOR = 0.7


class FreeQuestionIntentTest(unittest.TestCase):
    def test_korean_concept_definition_uses_latest_question_only(self):
        result = classify_free_question("\ubd84\uc0b0\ud658\uacbd\uc774 \uc5b4\ub5a4 \ud658\uacbd\uc744 \uc758\ubbf8\ud558\ub294 \uac83\uc778\uac00\uc694?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "\ubd84\uc0b0\ud658\uacbd")

    def test_comparison_question_uses_latest_question_only(self):
        result = classify_free_question("\ud398\uc774\uc9c0\ub124\uc774\uc158\uc774\ub791 \ubb34\ud55c\uc2a4\ud06c\ub864 \ucc28\uc774\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "comparison")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_practical_question_keeps_background_as_weak_context(self):
        result = classify_free_question("\uc2e4\ubb34\uc5d0\uc11c\ub294 \uc774\uac78 \uc5b4\ub5bb\uac8c \ucc98\ub9ac\ud574?")

        self.assertEqual(result.intent, "practical_application")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_vague_clarification_uses_original_context(self):
        result = classify_free_question("\uc65c\uc694?")

        self.assertEqual(result.intent, "clarification")
        self.assertEqual(result.rag_policy, "original_context_mixed")

    def test_known_clarification_phrase_stays_clarification(self):
        for question in ("\ub2e4\uc2dc \uc124\uba85\ud574\uc918", "\ub2e4\uc2dc \uc124\uba85\ud574\uc918?"):
            with self.subTest(question=question):
                result = classify_free_question(question)

                self.assertEqual(result.intent, "clarification")
                self.assertEqual(result.rag_policy, "original_context_mixed")
                self.assertFalse(result.context_dependent)

    def test_original_problem_reason_uses_original_context(self):
        result = classify_free_question("\uc774 \ub2f5\uc774 \uc65c \ub9de\uc544?")

        self.assertEqual(result.intent, "original_problem_reason")
        self.assertEqual(result.rag_policy, "original_context_mixed")

    def test_api_definition_extracts_topic(self):
        result = classify_free_question("API\uac00 \ubb54\ub370?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "API")

    def test_english_what_is_definition_extracts_topic(self):
        result = classify_free_question("What is API?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "API")

    def test_domain_word_comparison_does_not_force_comparison_intent(self):
        result = classify_free_question("\ubb38\uc790\uc5f4 \ube44\uad50\uc5d0\uc11c equals\ub97c \uc4f0\ub294 \uc774\uc720\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_discourse_marker_is_removed_before_topic_extraction(self):
        result = classify_free_question("\ud639\uc2dc AI \ud504\ub86c\ud504\ud2b8\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "AI \ud504\ub86c\ud504\ud2b8")
        self.assertGreaterEqual(result.confidence, HIGH_CONFIDENCE_TOPIC_THRESHOLD)

    def test_context_dependent_question_keeps_original_context(self):
        result = classify_free_question("\uadf8\ub7fc \uc774\uac74 \uc65c \ud2c0\ub9b0 \uac70\uc57c?")

        self.assertEqual(result.intent, "original_problem_reason")
        self.assertEqual(result.rag_policy, "original_context_mixed")
        self.assertTrue(result.context_dependent)

    def test_generic_ai_token_does_not_get_high_confidence_fast_path(self):
        result = classify_free_question("AI\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.topic, "AI")
        self.assertLess(result.confidence, FAST_PATH_CONFIDENCE_THRESHOLD)

    def test_specific_ai_prompt_topic_is_not_collapsed_to_ai(self):
        result = classify_free_question("\uadfc\ub370 AI \ud504\ub86c\ud504\ud2b8\uac00 \ubb50\uc57c?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.topic, "AI \ud504\ub86c\ud504\ud2b8")
        self.assertGreater(result.confidence, SPECIFIC_TOPIC_CONFIDENCE_FLOOR)


if __name__ == "__main__":
    unittest.main()

import unittest

from app.workflow.intent import classify_free_question


HIGH_CONFIDENCE_TOPIC_THRESHOLD = 0.8
FAST_PATH_CONFIDENCE_THRESHOLD = 0.95
SPECIFIC_TOPIC_CONFIDENCE_FLOOR = 0.7


class FreeQuestionIntentTest(unittest.TestCase):
    def test_korean_concept_definition_uses_latest_question_only(self):
        result = classify_free_question("분산환경이 어떤 환경을 의미하는 것인가요?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "분산환경")

    def test_comparison_question_uses_latest_question_only(self):
        result = classify_free_question("페이지네이션이랑 무한스크롤 차이가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "comparison")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_practical_question_keeps_background_as_weak_context(self):
        result = classify_free_question("실무에서는 이걸 어떻게 처리해?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "practical")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_vague_clarification_uses_original_context(self):
        result = classify_free_question("왜요?")

        self.assertEqual(result.intent, "follow_up")
        self.assertEqual(result.sub_intent, "follow_up")
        self.assertEqual(result.rag_policy, "original_context_mixed")

    def test_known_clarification_phrase_stays_clarification(self):
        for question in ("다시 설명해줘", "다시 설명해줘?"):
            with self.subTest(question=question):
                result = classify_free_question(question)

                self.assertEqual(result.intent, "follow_up")
                self.assertEqual(result.sub_intent, "follow_up")
                self.assertEqual(result.rag_policy, "original_context_mixed")
                self.assertFalse(result.context_dependent)

    def test_original_problem_reason_uses_original_context(self):
        result = classify_free_question("이 답이 왜 맞아?")

        self.assertEqual(result.intent, "wrong_answer_explanation")
        self.assertEqual(result.sub_intent, "explanation")
        self.assertEqual(result.rag_policy, "original_context_mixed")

    def test_api_definition_extracts_topic(self):
        result = classify_free_question("API가 뭔데?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "API")

    def test_english_what_is_definition_extracts_topic(self):
        result = classify_free_question("What is API?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "API")

    def test_domain_word_comparison_does_not_force_comparison_intent(self):
        result = classify_free_question("문자열 비교에서 equals를 쓰는 이유가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")

    def test_discourse_marker_is_removed_before_topic_extraction(self):
        result = classify_free_question("혹시 AI 프롬프트가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.rag_policy, "latest_question_only")
        self.assertEqual(result.topic, "AI 프롬프트")
        self.assertGreaterEqual(result.confidence, HIGH_CONFIDENCE_TOPIC_THRESHOLD)

    def test_context_dependent_question_keeps_original_context(self):
        result = classify_free_question("그럼 이건 왜 틀린 거야?")

        self.assertEqual(result.intent, "wrong_answer_explanation")
        self.assertEqual(result.sub_intent, "explanation")
        self.assertEqual(result.rag_policy, "original_context_mixed")
        self.assertTrue(result.context_dependent)

    def test_generic_ai_token_does_not_get_high_confidence_fast_path(self):
        result = classify_free_question("AI가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.topic, "AI")
        self.assertLess(result.confidence, FAST_PATH_CONFIDENCE_THRESHOLD)

    def test_specific_ai_prompt_topic_is_not_collapsed_to_ai(self):
        result = classify_free_question("근데 AI 프롬프트가 뭐야?")

        self.assertEqual(result.intent, "concept_definition")
        self.assertEqual(result.sub_intent, "definition")
        self.assertEqual(result.topic, "AI 프롬프트")
        self.assertGreater(result.confidence, SPECIFIC_TOPIC_CONFIDENCE_FLOOR)


if __name__ == "__main__":
    unittest.main()

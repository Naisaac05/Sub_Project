import unittest

from app.prompts import build_prompt, prompt_version_for_mode
from app.schemas import AiGenerateRequest
from app.validation.text import compact_answer, korean_fallback, mask_pii


class ServiceHelpersTest(unittest.TestCase):
    def test_compact_answer_limits_sentences(self):
        answer = compact_answer("첫 문장입니다. 둘째 문장입니다. 셋째 문장입니다.", "first-question")

        self.assertEqual(answer, "첫 문장입니다. 둘째 문장입니다.")

    def test_prompt_version_for_mode(self):
        self.assertEqual(prompt_version_for_mode("first-question"), "first_question_v1")
        self.assertEqual(prompt_version_for_mode("follow-up"), "follow_up_v1")
        self.assertEqual(prompt_version_for_mode("free-question"), "free_question_v1")

    def test_build_prompt_contains_mode_specific_context(self):
        prompt = build_prompt("free-question", AiGenerateRequest(question="N+1?", user_answer="왜?"))

        self.assertIn("[Learner Free Question]", prompt)
        self.assertIn("Korean only", prompt)

    def test_korean_fallback_returns_korean_text(self):
        answer = korean_fallback("first-question", AiGenerateRequest())

        self.assertIn("설명", answer)

    def test_mask_pii_redacts_common_patterns(self):
        text = mask_pii("010-1234-5678 test@example.com")

        self.assertIn("[REDACTED_PHONE_KR]", text)
        self.assertIn("[REDACTED_EMAIL]", text)


if __name__ == "__main__":
    unittest.main()


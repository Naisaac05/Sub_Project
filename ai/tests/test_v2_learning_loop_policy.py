import os
import unittest
from unittest.mock import patch

from app.knowledge.auto_candidates import should_capture_auto_candidate
from app.prompts import build_prompt
from app.schemas import AiGenerateRequest
from app.workflow.grounding import validate_grounding
from app.workflow.judge import judge_answer


class V2LearningLoopPolicyTest(unittest.TestCase):
    def test_follow_up_prompt_contains_compact_conversation_context(self):
        prompt = build_prompt(
            "follow-up",
            AiGenerateRequest(
                question="ArrayList와 LinkedList의 차이는?",
                user_answer="모르겠어요",
                previous_ai_question="인덱스 조회에서 어떤 차이가 있나요?",
                active_concept="ArrayList",
                follow_up_type="DIAGNOSTIC_FOLLOW_UP",
            ),
        )

        self.assertIn("인덱스 조회에서 어떤 차이가 있나요?", prompt)
        self.assertIn("ArrayList", prompt)
        self.assertIn("DIAGNOSTIC_FOLLOW_UP", prompt)

    def test_only_approved_v2_miss_reasons_create_candidate(self):
        for reason in ("retrieval_miss", "score_gate", "anchor_miss", "payload_not_approved", "payload_empty"):
            self.assertEqual(
                should_capture_auto_candidate("free-question", "generation", 0.8, [], False, reason),
                reason,
            )
        self.assertIsNone(
            should_capture_auto_candidate("free-question", "generation", 0.8, [], False, "unsupported_intent")
        )
        self.assertIsNone(
            should_capture_auto_candidate("follow-up", "generation", 0.8, [], False, "retrieval_miss")
        )

    def test_local_judges_are_disabled_by_default(self):
        request = AiGenerateRequest(user_answer="ArrayList가 무엇인가요?")
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("AI_REVIEW_SEMANTIC_JUDGE_ENABLED", None)
            os.environ.pop("AI_REVIEW_GROUNDING_JUDGE_ENABLED", None)
            generator = unittest.mock.Mock(side_effect=AssertionError("judge must not call Ollama"))

            semantic = judge_answer("free-question", request, "답변", [], generator=generator)
            grounding = validate_grounding(request, "답변", [object()], generator=generator)

        self.assertIn("disabled", semantic.reason)
        self.assertIn("disabled", grounding.reason)
        generator.assert_not_called()


if __name__ == "__main__":
    unittest.main()

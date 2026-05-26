import json
import unittest

from app.observability import correlation_id_from, emit_observability_events
from app.schemas import AiGenerateResponse


class CapturingLogger:
    def __init__(self):
        self.messages = []

    def info(self, message: str):
        self.messages.append(message)


class ObservabilityTest(unittest.TestCase):
    def test_correlation_id_uses_header_or_generates_value(self):
        self.assertEqual(correlation_id_from("abc-123"), "abc-123")
        self.assertTrue(correlation_id_from("").startswith("ai-review-"))

    def test_emit_observability_events_adds_metrics_and_correlation_id(self):
        logger = CapturingLogger()
        response = AiGenerateResponse(
            answer="ok",
            fallback_used=True,
            retrieved_concept_ids=[],
            candidate_id="auto-123",
            route="fallback_template",
            observability_events=[{"event": "ai_review.workflow_completed"}],
        )

        emit_observability_events(response, "corr-1", logger=logger)

        event = json.loads(logger.messages[0])
        self.assertEqual(event["correlation_id"], "corr-1")
        self.assertTrue(event["fallback_used"])
        self.assertTrue(event["retrieval_miss"])
        self.assertTrue(event["candidate_captured"])

    def test_emit_observability_events_marks_cache_hit_and_llm_call_avoided(self):
        logger = CapturingLogger()
        response = AiGenerateResponse(
            answer="cached",
            fallback_used=False,
            route="cache",
            model_used="qwen3:1.7b:cache",
            observability_events=[{"event": "ai_review.workflow_completed"}],
        )

        emit_observability_events(response, "corr-2", logger=logger)

        event = json.loads(logger.messages[0])
        self.assertTrue(event["cache_hit"])
        self.assertTrue(event["llm_call_avoided"])
        self.assertEqual(event["model_used"], "qwen3:1.7b:cache")

    def test_emit_observability_events_marks_lightweight_only_miss_as_llm_avoided(self):
        logger = CapturingLogger()
        response = AiGenerateResponse(
            answer="fallback",
            fallback_used=True,
            route="lightweight_only_miss",
            model_used="template",
            observability_events=[{"event": "ai_review.workflow_completed"}],
        )

        emit_observability_events(response, "corr-lightweight", logger=logger)

        event = json.loads(logger.messages[0])
        self.assertTrue(event["llm_call_avoided"])

    def test_emit_observability_events_marks_candidate_capture_kill_switch_and_failure(self):
        logger = CapturingLogger()
        response = AiGenerateResponse(
            answer="ok",
            fallback_used=False,
            route="generation",
            quality_flags=["candidate_capture_disabled", "candidate_capture_failed"],
            observability_events=[{"event": "ai_review.workflow_completed"}],
        )

        emit_observability_events(response, "corr-3", logger=logger)

        event = json.loads(logger.messages[0])
        self.assertTrue(event["candidate_capture_disabled"])
        self.assertTrue(event["candidate_capture_failed"])


if __name__ == "__main__":
    unittest.main()

import os
import unittest

from app.schemas import AiGenerateRequest
from app.workflow.answer_cache import cache_key_for, clear_answer_cache, put_cached_answer
from app.workflow.runner import run_review_workflow, run_review_workflow_stream


class WorkflowDegradedModesTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self._previous_cache_only = os.environ.get("AI_REVIEW_CACHE_ONLY")
        self._previous_lightweight_only = os.environ.get("AI_REVIEW_LIGHTWEIGHT_ONLY")
        self._previous_template_only = os.environ.get("AI_REVIEW_TEMPLATE_FALLBACK_ONLY")
        clear_answer_cache()

    def tearDown(self):
        self._restore_env("AI_REVIEW_CACHE_ONLY", self._previous_cache_only)
        self._restore_env("AI_REVIEW_LIGHTWEIGHT_ONLY", self._previous_lightweight_only)
        self._restore_env("AI_REVIEW_TEMPLATE_FALLBACK_ONLY", self._previous_template_only)
        clear_answer_cache()

    def test_template_fallback_only_bypasses_cache_and_generator(self):
        os.environ["AI_REVIEW_TEMPLATE_FALLBACK_ONLY"] = "true"
        request = AiGenerateRequest(user_answer="API가 뭐야?")
        put_cached_answer(cache_key_for("free-question", request), "cached answer")

        def failing_generator(**kwargs):
            raise AssertionError("template_fallback_only must not call generator")

        response = run_review_workflow("free-question", request, generator=failing_generator)

        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertEqual(response.route, "template_fallback_only")
        self.assertNotEqual(response.answer, "cached answer")

    def test_cache_only_returns_cached_answer_without_generator(self):
        os.environ["AI_REVIEW_CACHE_ONLY"] = "true"
        request = AiGenerateRequest(user_answer="캐시된 질문")
        put_cached_answer(cache_key_for("free-question", request), "캐시된 답변입니다.")

        def failing_generator(**kwargs):
            raise AssertionError("cache_only hit must not call generator")

        response = run_review_workflow("free-question", request, generator=failing_generator)

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "exaone3.5:2.4b:cache")
        self.assertEqual(response.route, "cache")
        self.assertEqual(response.answer, "캐시된 답변입니다.")

    def test_cache_only_miss_uses_template_without_generator(self):
        os.environ["AI_REVIEW_CACHE_ONLY"] = "true"

        def failing_generator(**kwargs):
            raise AssertionError("cache_only miss must not call generator")

        response = run_review_workflow(
            "free-question",
            AiGenerateRequest(user_answer="캐시에 없는 질문"),
            generator=failing_generator,
        )

        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertEqual(response.route, "cache_only_miss")
        self.assertIn("cache_only_miss", response.quality_flags)

    async def test_stream_cache_only_miss_streams_template_without_generator(self):
        os.environ["AI_REVIEW_CACHE_ONLY"] = "true"

        async def failing_stream_generator(**kwargs):
            raise AssertionError("cache_only stream miss must not call generator")
            yield "never"

        events = [
            event
            async for event in run_review_workflow_stream(
                "free-question",
                AiGenerateRequest(user_answer="캐시에 없는 스트림 질문"),
                generator=failing_stream_generator,
            )
        ]

        self.assertEqual([event["type"] for event in events], ["start", "chunk", "done"])
        response = events[-1]["response"]
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertEqual(response.route, "cache_only_miss")

    def test_lightweight_only_returns_static_fast_path_without_generator(self):
        os.environ["AI_REVIEW_LIGHTWEIGHT_ONLY"] = "true"

        def failing_generator(**kwargs):
            raise AssertionError("lightweight_only fast path must not call generator")

        response = run_review_workflow(
            "free-question",
            AiGenerateRequest(user_answer="REST API\uac00 \ubb50\uc57c?"),
            generator=failing_generator,
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "lightweight-template")
        self.assertEqual(response.route, "static_fast_path")
        self.assertIn("REST", response.answer)

    def test_lightweight_only_miss_uses_template_without_cache_or_generator(self):
        os.environ["AI_REVIEW_LIGHTWEIGHT_ONLY"] = "true"
        request = AiGenerateRequest(user_answer="xqzv plumbus frobnicate")
        put_cached_answer(cache_key_for("free-question", request), "cached answer")

        def failing_generator(**kwargs):
            raise AssertionError("lightweight_only miss must not call generator")

        response = run_review_workflow("free-question", request, generator=failing_generator)

        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertEqual(response.route, "lightweight_only_miss")
        self.assertNotEqual(response.answer, "cached answer")
        self.assertIn("lightweight_only_miss", response.quality_flags)

    async def test_stream_lightweight_only_miss_streams_template_without_generator(self):
        os.environ["AI_REVIEW_LIGHTWEIGHT_ONLY"] = "true"

        async def failing_stream_generator(**kwargs):
            raise AssertionError("lightweight_only stream miss must not call generator")
            yield "never"

        events = [
            event
            async for event in run_review_workflow_stream(
                "free-question",
                AiGenerateRequest(user_answer="xqzv stream plumbus frobnicate"),
                generator=failing_stream_generator,
            )
        ]

        self.assertEqual([event["type"] for event in events], ["start", "chunk", "done"])
        response = events[-1]["response"]
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertEqual(response.route, "lightweight_only_miss")

    @staticmethod
    def _restore_env(name: str, value: str | None) -> None:
        if value is None:
            os.environ.pop(name, None)
        else:
            os.environ[name] = value


if __name__ == "__main__":
    unittest.main()

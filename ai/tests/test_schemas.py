import unittest

from app.schemas import AiGenerateRequest, AiGenerateResponse


class SchemaDefaultsTest(unittest.TestCase):
    def test_default_model_uses_small_qwen3_model(self):
        request = AiGenerateRequest()
        self.assertEqual(request.model, "qwen3:1.7b")
        self.assertEqual(request.max_tokens, 256)
        self.assertEqual(request.num_ctx, 1024)

    def test_empty_model_normalizes_to_small_qwen3_model(self):
        request = AiGenerateRequest.model_validate({"model": ""})
        self.assertEqual(request.model, "qwen3:1.7b")

    def test_empty_limits_normalize_to_rag_mvp_defaults(self):
        request = AiGenerateRequest.model_validate({"max_tokens": "", "num_ctx": ""})
        self.assertEqual(request.max_tokens, 256)
        self.assertEqual(request.num_ctx, 1024)

    def test_response_accepts_nullable_rag_metadata(self):
        response = AiGenerateResponse(answer="답변")
        self.assertIsNone(response.confidence_score)
        self.assertIsNone(response.model_used)
        self.assertIsNone(response.fallback_used)
        self.assertEqual(response.retrieved_concept_ids, [])
        self.assertIsNone(response.candidate_id)
        self.assertIsNone(response.prompt_version)
        self.assertIsNone(response.latency_ms)


if __name__ == "__main__":
    unittest.main()


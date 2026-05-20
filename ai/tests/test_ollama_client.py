import unittest

from app.ollama.client import (
    DEFAULT_MODEL,
    FALLBACK_MODEL,
    OLLAMA_MAX_CONCURRENT_GENERATIONS,
    OLLAMA_WARMUP_MODEL,
    keep_alive_for_model,
    stop_sequences,
)


class OllamaClientTest(unittest.TestCase):
    def test_stop_sequences_do_not_include_prompt_section_labels(self):
        stops = stop_sequences()

        self.assertIn("\n\n\n", stops)
        self.assertNotIn("[Learner Free Question]", stops)
        self.assertNotIn("[Original Question]", stops)
        self.assertNotIn("[Question]", stops)

    def test_default_model_policy_uses_small_model_and_4b_fallback(self):
        self.assertEqual(DEFAULT_MODEL, "qwen3:1.7b")
        self.assertEqual(FALLBACK_MODEL, "qwen3:4b-q4_K_M")
        self.assertEqual(OLLAMA_WARMUP_MODEL, "qwen3:1.7b")

    def test_keep_alive_policy_keeps_small_resident_and_fallback_bounded(self):
        # Ollama requires keep_alive as int seconds or a duration string with a unit.
        # "-1" (int) means "stay resident forever"; sending the bare string "-1" returns HTTP 400.
        self.assertEqual(keep_alive_for_model("qwen3:1.7b"), -1)
        self.assertEqual(keep_alive_for_model("qwen3:4b-q4_K_M"), "30m")

    def test_generation_concurrency_defaults_to_one(self):
        self.assertEqual(OLLAMA_MAX_CONCURRENT_GENERATIONS, 1)


if __name__ == "__main__":
    unittest.main()

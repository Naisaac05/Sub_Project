import unittest

from app.production_config import is_production_environment, validate_production_config


class ProductionConfigTest(unittest.TestCase):
    def test_is_production_environment_accepts_common_env_names(self):
        self.assertTrue(is_production_environment({"ENVIRONMENT": "prod"}))
        self.assertTrue(is_production_environment({"APP_ENV": "production"}))
        self.assertTrue(is_production_environment({"PYTHON_ENV": "PROD"}))
        self.assertFalse(is_production_environment({"ENVIRONMENT": "local"}))

    def test_validate_production_config_rejects_missing_token_jsonl_sink_and_unbounded_values(self):
        env = {
            "ENVIRONMENT": "prod",
            "AI_REVIEW_SERVICE_TOKEN": "",
            "AI_REVIEW_CANDIDATE_SINK": "jsonl",
            "OLLAMA_REQUEST_TIMEOUT_SECONDS": "0",
            "OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS": "-1",
            "AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS": "0",
            "PYTHON_AI_MAX_TOKENS": "0",
            "PYTHON_AI_NUM_CTX": "0",
            "AI_REVIEW_MAX_USER_ANSWER_LENGTH": "0",
        }

        with self.assertRaises(RuntimeError) as context:
            validate_production_config(env)

        message = str(context.exception)
        self.assertIn("AI_REVIEW_SERVICE_TOKEN is required in prod", message)
        self.assertIn("AI_REVIEW_CANDIDATE_SINK=jsonl is forbidden in prod", message)
        self.assertIn("OLLAMA_REQUEST_TIMEOUT_SECONDS must be > 0 in prod", message)
        self.assertIn("AI_REVIEW_MAX_USER_ANSWER_LENGTH must be > 0 in prod", message)

    def test_validate_production_config_accepts_bounded_production_values(self):
        env = {
            "ENVIRONMENT": "prod",
            "AI_REVIEW_SERVICE_TOKEN": "token",
            "AI_REVIEW_CANDIDATE_SINK": "http",
            "OLLAMA_REQUEST_TIMEOUT_SECONDS": "30",
            "OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS": "3",
            "AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS": "2",
            "PYTHON_AI_MAX_TOKENS": "256",
            "PYTHON_AI_NUM_CTX": "1024",
            "AI_REVIEW_MAX_USER_ANSWER_LENGTH": "700",
        }

        validate_production_config(env)

    def test_validate_production_config_skips_local_environment(self):
        validate_production_config({"ENVIRONMENT": "local", "AI_REVIEW_CANDIDATE_SINK": "jsonl"})


if __name__ == "__main__":
    unittest.main()

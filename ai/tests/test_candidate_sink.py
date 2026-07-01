import os
import unittest
from urllib import error
from unittest.mock import patch

from app.knowledge.candidate_sink import save_auto_candidate


class CandidateSinkLoggingTest(unittest.TestCase):
    def setUp(self):
        self.candidate = {
            "candidate_id": "candidate-1",
            "definition_draft": "SENSITIVE_DEFINITION_PAYLOAD",
            "source_question": "SENSITIVE_ANSWER_PAYLOAD",
        }
        self.env = patch.dict(
            os.environ,
            {
                "AI_REVIEW_CANDIDATE_SINK": "http",
                "AI_REVIEW_CANDIDATE_CAPTURE_URL": (
                    "https://SENSITIVE_USERNAME:SENSITIVE_PASSWORD@capture.example.test:8443/candidates"
                    "?token=SENSITIVE_QUERY_SECRET#SENSITIVE_FRAGMENT"
                ),
                "AI_REVIEW_SERVICE_TOKEN": "SENSITIVE_SERVICE_TOKEN",
            },
        )
        self.env.start()

    def tearDown(self):
        self.env.stop()

    @patch("app.knowledge.candidate_sink.request.urlopen")
    def test_http_403_logs_status_without_candidate_payload(self, urlopen):
        urlopen.side_effect = error.HTTPError(
            "https://capture.example.test/candidates",
            403,
            "Forbidden",
            None,
            None,
        )

        with self.assertLogs("app.knowledge.candidate_sink", level="WARNING") as captured:
            result = save_auto_candidate(self.candidate)

        message = "\n".join(captured.output)
        self.assertFalse(result)
        self.assertIn("status=403", message)
        self.assertIn("url=https://capture.example.test:8443/candidates", message)
        self.assertNotIn("SENSITIVE_USERNAME", message)
        self.assertNotIn("SENSITIVE_PASSWORD", message)
        self.assertNotIn("SENSITIVE_QUERY_SECRET", message)
        self.assertNotIn("SENSITIVE_FRAGMENT", message)
        self.assertNotIn("SENSITIVE_DEFINITION_PAYLOAD", message)
        self.assertNotIn("SENSITIVE_ANSWER_PAYLOAD", message)
        self.assertNotIn("SENSITIVE_SERVICE_TOKEN", message)

    @patch("app.knowledge.candidate_sink.request.urlopen")
    def test_url_error_logs_exception_type(self, urlopen):
        urlopen.side_effect = error.URLError("connection refused")

        with self.assertLogs("app.knowledge.candidate_sink", level="WARNING") as captured:
            result = save_auto_candidate(self.candidate)

        message = "\n".join(captured.output)
        self.assertFalse(result)
        self.assertIn("URLError", message)
        self.assertIn("url=https://capture.example.test:8443/candidates", message)
        self.assertNotIn("SENSITIVE_USERNAME", message)
        self.assertNotIn("SENSITIVE_PASSWORD", message)
        self.assertNotIn("SENSITIVE_QUERY_SECRET", message)
        self.assertNotIn("SENSITIVE_FRAGMENT", message)

    def test_malformed_capture_url_logs_value_error_without_raw_url(self):
        malformed_url = "https://SENSITIVE_USERNAME:SENSITIVE_PASSWORD@[invalid/candidates?token=SENSITIVE_QUERY_SECRET"

        with patch.dict(os.environ, {"AI_REVIEW_CANDIDATE_CAPTURE_URL": malformed_url}):
            with self.assertLogs("app.knowledge.candidate_sink", level="WARNING") as captured:
                result = save_auto_candidate(self.candidate)

        message = "\n".join(captured.output)
        self.assertFalse(result)
        self.assertIn("ValueError", message)
        self.assertIn("url=<invalid-url>", message)
        self.assertNotIn(malformed_url, message)
        self.assertNotIn("SENSITIVE_USERNAME", message)
        self.assertNotIn("SENSITIVE_PASSWORD", message)
        self.assertNotIn("SENSITIVE_QUERY_SECRET", message)


if __name__ == "__main__":
    unittest.main()

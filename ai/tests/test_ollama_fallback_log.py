import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from app.observability import emit_ollama_fallback_log


class OllamaFallbackLogTest(unittest.TestCase):
    def test_writes_daily_json_log_with_required_fields(self):
        with tempfile.TemporaryDirectory() as directory, patch(
            "app.observability.OLLAMA_FALLBACK_LOG_DIR",
            Path(directory),
        ), patch("app.observability.date") as mocked_date:
            mocked_date.today.return_value.isoformat.return_value = "2026-06-13"
            path = emit_ollama_fallback_log(
                {
                    "route": "fallback_template",
                    "ollama_duration": 101,
                    "fallback_reason": "timeout",
                    "v2_hit": False,
                }
            )

            self.assertEqual(path.name, "ollama_fallback_2026-06-13.log")
            payload = json.loads(path.read_text(encoding="utf-8").strip())
            self.assertEqual(payload["route"], "fallback_template")
            self.assertEqual(payload["ollama_duration"], 101)
            self.assertEqual(payload["fallback_reason"], "timeout")
            self.assertFalse(payload["v2_hit"])


if __name__ == "__main__":
    unittest.main()

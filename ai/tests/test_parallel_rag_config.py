import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from app.rag.parallel_config import ParallelRagConfig, load_parallel_rag_config, should_serve_v2


class ParallelRagConfigTest(unittest.TestCase):
    def test_shadow_mode_always_prevents_v2_serve(self):
        config = ParallelRagConfig(shadow_mode=True, v2_percentage=100)

        self.assertFalse(should_serve_v2(config, random_value=0.0))

    def test_percentage_is_used_only_when_shadow_is_disabled(self):
        config = ParallelRagConfig(shadow_mode=False, v2_percentage=10)

        self.assertTrue(should_serve_v2(config, random_value=0.05))
        self.assertFalse(should_serve_v2(config, random_value=0.10))

    def test_config_file_defaults_to_shadow_and_ten_percent(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "parallel.json"
            path.write_text(json.dumps({
                "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": True,
                "SHADOW_MODE": True,
                "V2_PERCENTAGE": 10,
            }), encoding="utf-8")

            config = load_parallel_rag_config(path)

        self.assertTrue(config.enabled)
        self.assertTrue(config.shadow_mode)
        self.assertEqual(config.v2_percentage, 10)


if __name__ == "__main__":
    unittest.main()

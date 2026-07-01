import unittest
from pathlib import Path


class ProdDeploymentWiringTest(unittest.TestCase):
    def test_compose_shares_ai_cards_and_routes_candidate_capture(self):
        compose = (Path(__file__).resolve().parents[2] / "docker-compose.prod.yml").read_text(
            encoding="utf-8"
        )

        expected_wiring = (
            "AI_REVIEW_CONCEPTS_V2_PATH: /app/ai/knowledge/concepts_v2",
            "./ai/app/knowledge/concepts_v2:/app/ai/knowledge/concepts_v2",
            "./ai/app/knowledge/concepts_v2:/app/app/knowledge/concepts_v2",
            "AI_REVIEW_CANDIDATE_CAPTURE_URL: http://backend:8080/api/internal/ai-review/candidates/capture",
        )
        for expected in expected_wiring:
            with self.subTest(expected=expected):
                self.assertIn(expected, compose)


if __name__ == "__main__":
    unittest.main()

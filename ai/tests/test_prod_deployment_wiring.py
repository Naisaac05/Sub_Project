import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


class ProdDeploymentWiringTest(unittest.TestCase):
    def test_compose_shares_ai_cards_and_routes_candidate_capture(self):
        source = Path(__file__).resolve().parents[2] / "docker-compose.prod.yml"

        with tempfile.TemporaryDirectory() as temp_dir:
            compose_path = Path(temp_dir) / "docker-compose.prod.yml"
            shutil.copyfile(source, compose_path)
            (Path(temp_dir) / ".env.prod").touch()
            result = subprocess.run(
                [
                    "docker",
                    "compose",
                    "-f",
                    str(compose_path),
                    "config",
                    "--format",
                    "json",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            services = json.loads(result.stdout)["services"]

        backend = services["backend"]
        ai = services["ai"]

        self.assertEqual(
            backend["environment"]["AI_REVIEW_CONCEPTS_V2_PATH"],
            "/app/ai/knowledge/concepts_v2",
        )
        self.assertEqual(
            ai["environment"]["AI_REVIEW_CANDIDATE_CAPTURE_URL"],
            "http://backend:8080/api/internal/ai-review/candidates/capture",
        )
        self.assertTrue(
            self._has_concepts_mount(backend, "/app/ai/knowledge/concepts_v2")
        )
        self.assertTrue(
            self._has_concepts_mount(ai, "/app/app/knowledge/concepts_v2")
        )

    @staticmethod
    def _has_concepts_mount(service, target):
        return any(
            volume.get("type") == "bind"
            and volume.get("target") == target
            and Path(volume.get("source", "")).as_posix().endswith(
                "/ai/app/knowledge/concepts_v2"
            )
            for volume in service.get("volumes", [])
        )


if __name__ == "__main__":
    unittest.main()

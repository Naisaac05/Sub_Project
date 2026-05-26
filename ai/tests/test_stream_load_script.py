import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "run_stream_load_profile.py"


def load_script_module():
    spec = importlib.util.spec_from_file_location("run_stream_load_profile", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class StreamLoadScriptTest(unittest.TestCase):
    def test_script_help_lists_load_scenarios(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), "--help"],
            cwd=SCRIPT_PATH.parents[1],
            capture_output=True,
            text=True,
            timeout=10,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Run AI review streaming load scenarios", result.stdout)
        self.assertIn("cache-hit", result.stdout)
        self.assertIn("stream-disconnect", result.stdout)

    def test_scenarios_cover_p1_load_profile(self):
        module = load_script_module()

        self.assertEqual(
            set(module.SCENARIOS),
            {"cache-hit", "cache-miss", "free-form-storm", "stream-disconnect", "ollama-timeout"},
        )
        self.assertEqual(module.SCENARIOS["stream-disconnect"].disconnect_after_chunks, 1)
        self.assertLess(module.SCENARIOS["ollama-timeout"].client_timeout_seconds, 2)

    def test_build_payload_marks_cache_miss_and_storm_as_unique(self):
        module = load_script_module()

        cache_hit_1 = module.build_payload(module.SCENARIOS["cache-hit"], 1)
        cache_hit_2 = module.build_payload(module.SCENARIOS["cache-hit"], 2)
        cache_miss_1 = module.build_payload(module.SCENARIOS["cache-miss"], 1)
        cache_miss_2 = module.build_payload(module.SCENARIOS["cache-miss"], 2)

        self.assertEqual(cache_hit_1["user_answer"], cache_hit_2["user_answer"])
        self.assertNotEqual(cache_miss_1["user_answer"], cache_miss_2["user_answer"])

    def test_summary_counts_status_and_latency_stats(self):
        module = load_script_module()
        samples = [
            {"status": "completed", "first_token_latency_ms": 100, "stream_duration_ms": 1000},
            {"status": "completed", "first_token_latency_ms": 300, "stream_duration_ms": 2000},
            {"status": "disconnected", "first_token_latency_ms": 50, "stream_duration_ms": 500},
            {"status": "timeout", "first_token_latency_ms": None, "stream_duration_ms": 250},
        ]

        summary = module.summarize_samples(samples)

        self.assertEqual(summary["completed"], 2)
        self.assertEqual(summary["disconnected"], 1)
        self.assertEqual(summary["timeout"], 1)
        self.assertEqual(summary["first_token_latency_ms"]["p50"], 100)
        self.assertEqual(summary["stream_duration_ms"]["max"], 2000)

    def test_markdown_report_includes_scenario_and_command(self):
        module = load_script_module()
        report = module.render_markdown_report(
            scenario=module.SCENARIOS["free-form-storm"],
            samples=[
                {
                    "run": 1,
                    "status": "completed",
                    "first_token_latency_ms": 100,
                    "stream_duration_ms": 1000,
                    "chunk_count": 2,
                    "response_chars": 20,
                    "error": "",
                }
            ],
            command="python scripts/run_stream_load_profile.py --scenario free-form-storm",
        )

        self.assertIn("# AI Review Streaming Load Profile", report)
        self.assertIn("scenario: free-form-storm", report)
        self.assertIn("stream_duration_ms", report)
        self.assertIn("| 1 | completed | 100 | 1000 | 2 | 20 |", report)


if __name__ == "__main__":
    unittest.main()

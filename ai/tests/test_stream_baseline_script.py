import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "measure_ollama_stream_baseline.py"


def load_script_module():
    spec = importlib.util.spec_from_file_location("measure_ollama_stream_baseline", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class StreamBaselineScriptTest(unittest.IsolatedAsyncioTestCase):
    def test_script_help_runs_when_executed_by_path(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), "--help"],
            cwd=SCRIPT_PATH.parents[1],
            capture_output=True,
            text=True,
            timeout=10,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Measure real Ollama streaming baseline", result.stdout)

    async def test_measure_stream_records_first_token_and_done_metadata(self):
        module = load_script_module()

        async def fake_stream(mode, request):
            yield {"type": "start"}
            yield {"type": "chunk", "chunk": "첫 "}
            yield {"type": "chunk", "chunk": "응답"}
            yield {
                "type": "done",
                "response": module.AiGenerateResponse(
                    answer="첫 응답",
                    model_used="exaone3.5:2.4b",
                    route="generation",
                    fallback_used=False,
                    latency_ms=1234,
                    prompt_version="free_question_v1",
                    retrieved_concept_ids=[],
                    quality_flags=[],
                ),
            }

        clock_values = iter([10.0, 10.25, 10.5, 11.25])
        sample = await module.measure_stream_once(
            mode="free-question",
            request=module.AiGenerateRequest(user_answer="baseline question"),
            run_index=1,
            stream_runner=fake_stream,
            clock=lambda: next(clock_values),
        )

        self.assertEqual(sample["run"], 1)
        self.assertEqual(sample["status"], "completed")
        self.assertEqual(sample["first_token_latency_ms"], 250)
        self.assertEqual(sample["stream_duration_ms"], 1250)
        self.assertEqual(sample["chunk_count"], 2)
        self.assertEqual(sample["response_chars"], 4)
        self.assertEqual(sample["model_used"], "exaone3.5:2.4b")
        self.assertEqual(sample["route"], "generation")
        self.assertFalse(sample["fallback_used"])

    def test_render_markdown_includes_summary_environment_and_samples(self):
        module = load_script_module()
        samples = [
            {
                "run": 1,
                "status": "completed",
                "first_token_latency_ms": 100,
                "stream_duration_ms": 1000,
                "chunk_count": 2,
                "response_chars": 10,
                "model_used": "exaone3.5:2.4b",
                "route": "generation",
                "fallback_used": False,
                "quality_flags": [],
                "error": "",
            },
            {
                "run": 2,
                "status": "partial_failed",
                "first_token_latency_ms": None,
                "stream_duration_ms": 500,
                "chunk_count": 0,
                "response_chars": 0,
                "model_used": "",
                "route": "",
                "fallback_used": True,
                "quality_flags": ["missing_topic"],
                "error": "boom",
            },
        ]

        markdown = module.render_markdown_report(
            samples=samples,
            environment={
                "cpu": "Test CPU",
                "ram": "16 GB",
                "model": "exaone3.5:2.4b",
                "prompt": "baseline question",
                "knowledge_version": "manifest-hash",
            },
            command="measure baseline",
        )

        self.assertIn("stream_completed: 1", markdown)
        self.assertIn("stream_partial_failed: 1", markdown)
        self.assertIn("fallback_to_sync_count: 0", markdown)
        self.assertIn("first_token_latency_ms", markdown)
        self.assertIn("Test CPU", markdown)
        self.assertIn("| 1 | completed | 100 | 1000 |", markdown)

    def test_require_llm_rejects_fast_path_samples(self):
        module = load_script_module()

        with self.assertRaisesRegex(RuntimeError, "did not reach Ollama generation"):
            module.validate_llm_required(
                [
                    {
                        "run": 1,
                        "status": "completed",
                        "model_used": "lightweight-template",
                        "route": "static_fast_path",
                    }
                ]
            )

    def test_require_llm_accepts_generation_that_later_falls_back(self):
        module = load_script_module()

        module.validate_llm_required(
            [
                {
                    "run": 1,
                    "status": "completed",
                    "model_used": "exaone3.5:2.4b",
                    "route": "fallback_template",
                }
            ]
        )

    def test_knowledge_version_uses_manifest_version_count_and_hash(self):
        module = load_script_module()

        with tempfile.TemporaryDirectory() as tmpdir:
            manifest_path = Path(tmpdir) / "index_manifest.json"
            manifest_path.write_text(
                json.dumps({"version": 7, "entries": {"a": {"content_hash": "abc"}}}),
                encoding="utf-8",
            )

            version = module.knowledge_version_for_manifest(manifest_path)

        self.assertRegex(version, r"version=7 entries=1 sha256=[0-9a-f]{12}")


if __name__ == "__main__":
    unittest.main()

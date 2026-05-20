import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from app.knowledge.approval import promote_approved_candidates
from scripts.promote_and_evaluate_knowledge import run_promotion_workflow
from scripts.reindex_knowledge import reindex_changed_knowledge


AI_ROOT = Path(__file__).resolve().parents[1]


class PromotionWorkflowTest(unittest.TestCase):
    def test_runs_promotion_lint_and_evaluator_in_order(self):
        calls = []

        report = run_promotion_workflow(
            promote=lambda: calls.append("promote") or {"written": ["card.md"]},
            lint=lambda: calls.append("lint") or [],
            reindex=lambda: calls.append("reindex") or {"changed": ["card.md"], "unchanged": []},
            evaluate=lambda: calls.append("evaluate") or {"retrieval_hit_rate": 1.0},
        )

        self.assertEqual(calls, ["promote", "lint", "reindex", "evaluate"])
        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["written"], ["card.md"])
        self.assertEqual(report["reindex"]["changed"], ["card.md"])

    def test_stops_when_lint_fails(self):
        report = run_promotion_workflow(
            promote=lambda: {"written": []},
            lint=lambda: ["missing section"],
            reindex=lambda: {"changed": []},
            evaluate=lambda: {"retrieval_hit_rate": 1.0},
        )

        self.assertEqual(report["status"], "failed")
        self.assertEqual(report["stage"], "lint")
        self.assertEqual(report["errors"], ["missing section"])

    def test_stops_when_reindex_fails(self):
        report = run_promotion_workflow(
            promote=lambda: {"written": ["card.md"]},
            lint=lambda: [],
            reindex=lambda: {"status": "failed", "errors": ["manifest write failed"]},
            evaluate=lambda: {"retrieval_hit_rate": 1.0},
        )

        self.assertEqual(report["status"], "failed")
        self.assertEqual(report["stage"], "reindex")
        self.assertEqual(report["errors"], ["manifest write failed"])

    def test_promoted_candidate_is_seen_by_changed_only_reindex(self):
        candidate = {
            "term": "Idempotent Consumer",
            "category": "backend",
            "aliases": ["idempotency key"],
            "approved": True,
            "definition": "Idempotent Consumer prevents duplicated message handling from applying the same side effect twice.",
            "source_question_ids": ["golden:promotion-e2e"],
        }

        with TemporaryDirectory(dir=AI_ROOT) as tmp:
            temp_root = Path(tmp)
            concept_root = temp_root / "concepts"
            manifest_path = temp_root / "index_manifest.json"

            def promote():
                written = promote_approved_candidates([candidate], concept_root, today="2026-05-19")
                return {
                    "written": [str(path.relative_to(AI_ROOT)) for path in written],
                    "approved_candidates": 1,
                    "candidates": 1,
                }

            report = run_promotion_workflow(
                promote=promote,
                lint=lambda: [],
                reindex=lambda: reindex_changed_knowledge(concept_root, manifest_path),
                evaluate=lambda: {"retrieval_hit_rate": 1.0},
            )

            self.assertEqual(report["status"], "passed")
            self.assertEqual(report["approved_candidates"], 1)
            self.assertEqual(len(report["reindex"]["changed"]), 1)

            second_reindex = reindex_changed_knowledge(concept_root, manifest_path)
            self.assertEqual(second_reindex["changed"], [])
            self.assertEqual(len(second_reindex["unchanged"]), 1)


if __name__ == "__main__":
    unittest.main()

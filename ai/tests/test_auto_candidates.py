import json
import tempfile
from pathlib import Path
import unittest

from app.knowledge.auto_candidates import append_auto_candidate, build_auto_candidate, should_capture_auto_candidate


class AutoCandidateTest(unittest.TestCase):
    def test_builds_candidate_from_weak_free_question(self):
        candidate = build_auto_candidate(
            source_question="서킷브레이커가 어떤 의미인가요?",
            resolved_query="서킷브레이커가 어떤 의미인가요?",
            route="fallback_template",
            confidence_score=0.42,
            needs_review_reason="fallback_used",
        )

        self.assertEqual(candidate["term"], "서킷브레이커")
        self.assertEqual(candidate["source"], "ai-review-auto-candidate")
        self.assertEqual(candidate["status"], "needs_review")
        self.assertEqual(candidate["approved"], False)
        self.assertEqual(candidate["needs_review_reason"], "fallback_used")
        self.assertIn("candidate_id", candidate)

    def test_builds_candidate_with_generated_answer_as_definition_draft(self):
        candidate = build_auto_candidate(
            source_question="RecyclerView가 뭔가요?",
            resolved_query="RecyclerView가 뭔가요?",
            route="generation",
            confidence_score=0.8,
            needs_review_reason="no_match",
            generated_answer="RecyclerView는 목록 형태의 데이터를 효율적으로 표시하는 Android 위젯입니다.",
        )

        self.assertEqual(
            candidate["definition_draft"],
            "RecyclerView는 목록 형태의 데이터를 효율적으로 표시하는 Android 위젯입니다.",
        )
        self.assertEqual(candidate["definition_status"], "drafted")

    def test_append_auto_candidate_deduplicates_by_candidate_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            candidate = build_auto_candidate(
                source_question="pagination이 뭐야?",
                resolved_query="pagination이 뭐야?",
                route="generation",
                confidence_score=0.51,
                needs_review_reason="no_match",
            )

            first = append_auto_candidate(path, candidate)
            second = append_auto_candidate(path, candidate)

            rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
            self.assertTrue(first)
            self.assertFalse(second)
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["candidate_id"], candidate["candidate_id"])

    def test_append_auto_candidate_updates_blank_existing_draft_for_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            original = build_auto_candidate(
                source_question="RecyclerView가 뭔가요?",
                resolved_query="RecyclerView가 뭔가요?",
                route="generation",
                confidence_score=0.8,
                needs_review_reason="no_match",
            )
            updated = build_auto_candidate(
                source_question="RecyclerView가 뭔가요?",
                resolved_query="RecyclerView가 뭔가요?",
                route="generation",
                confidence_score=0.8,
                needs_review_reason="no_match",
                generated_answer="RecyclerView는 목록 데이터를 효율적으로 표시하는 Android 위젯입니다.",
            )

            self.assertTrue(append_auto_candidate(path, original))
            self.assertFalse(append_auto_candidate(path, updated))

            rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(len(rows), 1)
            self.assertEqual(
                rows[0]["definition_draft"],
                "RecyclerView는 목록 데이터를 효율적으로 표시하는 Android 위젯입니다.",
            )

    def test_append_auto_candidate_updates_no_match_reason_for_static_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            original = build_auto_candidate(
                source_question="RecyclerView가 뭔가요?",
                resolved_query="RecyclerView가 뭔가요?",
                route="generation",
                confidence_score=0.8,
                needs_review_reason="no_match",
                generated_answer="RecyclerView는 목록 데이터를 효율적으로 표시하는 Android 위젯입니다.",
            )
            updated = build_auto_candidate(
                source_question="RecyclerView가 뭔가요?",
                resolved_query="RecyclerView가 뭔가요?",
                route="static_fast_path",
                confidence_score=0.8,
                needs_review_reason="static_answer_unapproved",
                generated_answer="RecyclerView는 목록 데이터를 효율적으로 표시하는 Android 위젯입니다.",
            )

            self.assertTrue(append_auto_candidate(path, original))
            self.assertFalse(append_auto_candidate(path, updated))

            rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(rows[0]["needs_review_reason"], "static_answer_unapproved")
            self.assertEqual(rows[0]["route"], "static_fast_path")

    def test_append_auto_candidate_returns_false_when_path_is_not_writable_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            candidate = build_auto_candidate(
                source_question="서킷브레이커가 뭐야?",
                resolved_query="서킷브레이커가 뭐야?",
                route="fallback_template",
                confidence_score=0.4,
                needs_review_reason="fallback_used",
            )

            written = append_auto_candidate(Path(tmp), candidate)

            self.assertFalse(written)

    def test_static_fast_path_without_retrieved_context_is_captured_for_review(self):
        reason = should_capture_auto_candidate(
            mode="free-question",
            route="static_fast_path",
            confidence_score=0.8,
            retrieved_concept_ids=[],
            fallback_used=False,
        )

        self.assertEqual(reason, "static_answer_unapproved")


if __name__ == "__main__":
    unittest.main()

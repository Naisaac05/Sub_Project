import copy
import json
from pathlib import Path
import tempfile
import unittest

from app.scripts.improve_rag_cards_v2 import (
    DEFAULT_CARD_ROOT,
    improve_card,
    improve_directory,
    score_card_quality,
)


def sample_card(status="draft"):
    return {
        "card_id": "frontend-react-key",
        "category": "frontend",
        "term": "react-key",
        "aliases": ["react-key"],
        "source_question_ids": ["frontend:65"],
        "retrieval": {
            "embedding_text": "react-key " * 30,
            "embedding_hash": "",
            "boost_keywords": ["key", "concept", "react-key"],
            "intent_types": ["CONCEPT_DEFINITION"],
        },
        "payloads": {
            "CONCEPT_DEFINITION": {"content": "???", "examples": []},
            "ANSWER_REASON": {"why_correct": "", "key_points": []},
            "WRONG_ANSWER_REASON": {
                "common_mistakes": [],
                "per_option": {
                    "q65_option_3": {"text": "index", "reason": "???"},
                },
            },
            "COMPARISON": None,
            "EXAMPLE_REQUEST": None,
            "PRACTICAL_USAGE": None,
            "DEBUG_OR_ERROR": None,
        },
        "review": {
            "card_status": status,
            "payload_status": {
                "CONCEPT_DEFINITION": status,
                "ANSWER_REASON": status,
                "WRONG_ANSWER_REASON": status,
            },
        },
        "related_card_ids": [],
        "tags": [],
        "created_at": "2026-06-12T00:00:00Z",
        "updated_at": "2026-06-12T00:00:00Z",
    }


class ImproveRagCardsV2Test(unittest.TestCase):
    def test_default_card_root_points_to_existing_v2_store(self):
        self.assertTrue(DEFAULT_CARD_ROOT.is_dir())
        self.assertEqual(DEFAULT_CARD_ROOT.as_posix().split("/")[-3:], ["app", "knowledge", "concepts_v2"])

    def test_improves_draft_without_changing_structure_or_review(self):
        original = sample_card()
        improved, changed = improve_card(copy.deepcopy(original), now="2026-06-13T00:00:00Z")

        self.assertTrue(changed)
        self.assertEqual(set(improved), set(original))
        self.assertEqual(improved["review"], original["review"])
        self.assertEqual(improved["created_at"], original["created_at"])
        self.assertEqual(improved["updated_at"], "2026-06-13T00:00:00Z")
        self.assertGreaterEqual(len(improved["aliases"]), 3)
        self.assertLessEqual(len(improved["aliases"]), 10)
        self.assertLessEqual(len(improved["retrieval"]["embedding_text"]), 150)
        expected_embedding = " ".join(dict.fromkeys([
            improved["term"],
            *improved["aliases"][:3],
            improved["category"],
            *improved["retrieval"]["boost_keywords"],
        ]))
        self.assertEqual(improved["retrieval"]["embedding_text"], expected_embedding)
        self.assertGreaterEqual(len(improved["retrieval"]["boost_keywords"]), 3)
        self.assertLessEqual(len(improved["retrieval"]["boost_keywords"]), 7)
        self.assertNotIn("key", improved["retrieval"]["boost_keywords"])
        self.assertEqual(
            list(improved["payloads"]["WRONG_ANSWER_REASON"]["per_option"]),
            ["option_0"],
        )
        self.assertTrue(all(value is not None for value in improved["payloads"].values()))
        self.assertEqual(
            set(improved["retrieval"]["intent_types"]),
            set(improved["payloads"]),
        )

    def test_approved_card_is_unchanged(self):
        original = sample_card(status="approved")

        improved, changed = improve_card(copy.deepcopy(original), now="2026-06-13T00:00:00Z")

        self.assertFalse(changed)
        self.assertEqual(improved, original)

    def test_quality_score_has_expected_dimensions(self):
        improved, _ = improve_card(sample_card(), now="2026-06-13T00:00:00Z")

        quality = score_card_quality(improved)

        self.assertEqual(
            quality["card_quality_score"],
            quality["retrieval_quality"]
            + quality["payload_quality"]
            + quality["alias_boost_quality"],
        )
        self.assertLessEqual(quality["retrieval_quality"], 40)
        self.assertLessEqual(quality["payload_quality"], 40)
        self.assertLessEqual(quality["alias_boost_quality"], 20)

    def test_report_can_compare_current_cards_with_backup_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "current"
            baseline = Path(temp) / "baseline"
            root.mkdir()
            baseline.mkdir()
            original = sample_card()
            improved, _ = improve_card(copy.deepcopy(original), now="2026-06-13T00:00:00Z")
            (baseline / "card.json").write_text(json.dumps(original), encoding="utf-8")
            (root / "card.json").write_text(json.dumps(improved), encoding="utf-8")

            report = improve_directory(root, write=False, baseline_root=baseline)

        self.assertLess(report["average_score_before"], report["average_score_after"])


if __name__ == "__main__":
    unittest.main()

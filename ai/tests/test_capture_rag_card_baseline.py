import json
import tempfile
import unittest
from pathlib import Path

from app.scripts.capture_rag_card_baseline import build_baseline


def _card(card_id: str, status: str) -> dict:
    category, term = card_id.split("-", 1)
    return {
        "card_id": card_id,
        "category": category,
        "term": term,
        "aliases": [term],
        "retrieval": {
            "embedding_text": f"{term} {category}",
            "boost_keywords": [term],
        },
        "payloads": {
            "CONCEPT_DEFINITION": {"content": f"{term} definition"},
            "ANSWER_REASON": {"why_correct": f"{term} reason", "key_points": [term]},
            "WRONG_ANSWER_REASON": {"common_mistakes": ["mistake"], "per_option": {}},
        },
        "review": {
            "card_status": status,
            "payload_status": {
                "CONCEPT_DEFINITION": status,
                "ANSWER_REASON": status,
                "WRONG_ANSWER_REASON": status,
            },
        },
    }


class CaptureRagCardBaselineTest(unittest.TestCase):
    def test_build_baseline_records_status_counts_and_checksums(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for card_id, status in (("java-equals", "approved"), ("python-mro", "draft")):
                path = root / f"{card_id}.json"
                path.write_text(
                    json.dumps(_card(card_id, status), ensure_ascii=False),
                    encoding="utf-8",
                )

            report = build_baseline(root)

        self.assertEqual(report["total_card_count"], 2)
        self.assertEqual(report["status_counts"], {"approved": 1, "draft": 1})
        self.assertEqual(len(report["cards"]), 2)
        approved = next(item for item in report["cards"] if item["card_id"] == "java-equals")
        self.assertEqual(approved["payload_status_counts"], {"approved": 3})
        self.assertRegex(approved["file_sha256"], r"^[0-9a-f]{64}$")
        self.assertRegex(approved["searchable_checksum"], r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()

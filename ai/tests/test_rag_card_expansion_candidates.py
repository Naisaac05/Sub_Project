import json
import unittest
from pathlib import Path

from app.scripts.initialize_validation_policy_v212 import validate_payload_quality


ROOT = Path(__file__).resolve().parents[1]
CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
REPORT = ROOT / "reports" / "course_balanced_next20_candidates_2026-06-21.json"
MOJIBAKE_MARKERS = ("À", "Á", "°", "½", "¿", "Ç", "¹", "Æ", "¼", "¸")


class RagCardExpansionCandidatesTest(unittest.TestCase):
    def _selected_cards(self) -> list[dict]:
        report = json.loads(REPORT.read_text(encoding="utf-8"))
        selected = set(report["PREPARATION_BACKLOG"])
        cards = []
        for path in CARD_ROOT.rglob("*.json"):
            card = json.loads(path.read_text(encoding="utf-8-sig"))
            if card.get("card_id") in selected:
                cards.append(card)
        self.assertEqual(len(cards), 20)
        return cards

    def test_selected_candidate_payloads_pass_quality_policy(self):
        failures = {
            card["card_id"]: validate_payload_quality(card)["reasons"]
            for card in self._selected_cards()
            if validate_payload_quality(card)["reasons"]
        }

        self.assertEqual(failures, {})

    def test_selected_candidate_payloads_have_no_mojibake(self):
        failures = []
        for card in self._selected_cards():
            rendered = json.dumps(card.get("payloads"), ensure_ascii=False)
            if any(marker in rendered for marker in MOJIBAKE_MARKERS):
                failures.append(card["card_id"])

        self.assertEqual(failures, [])


if __name__ == "__main__":
    unittest.main()

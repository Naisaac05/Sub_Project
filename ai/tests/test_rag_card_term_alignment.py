import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class RagCardTermAlignmentTest(unittest.TestCase):
    def test_python_asyncio_definition_leads_with_card_term(self):
        path = ROOT / "app" / "knowledge" / "concepts_v2" / "python" / "python-asyncio.json"
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        content = card["payloads"]["CONCEPT_DEFINITION"]["content"]
        first_sentence = re.split(r"(?<=[.!?。])\s+", content.strip(), maxsplit=1)[0]

        self.assertIn("asyncio", first_sentence.lower())
        self.assertLess(first_sentence.lower().index("asyncio"), 20)
        self.assertIn("await", first_sentence.lower())


if __name__ == "__main__":
    unittest.main()

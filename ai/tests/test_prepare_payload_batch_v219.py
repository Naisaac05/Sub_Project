from __future__ import annotations

import unittest

from app.scripts import prepare_payload_batch_v219 as prepare


class PreparePayloadBatchV219Test(unittest.TestCase):
    def test_extracts_confirmed_answer_from_generated_definition(self):
        content = "핵심은 정답인 “목표까지 예상 비용”가 어떤 동작을 보장하는지 이해하는 것입니다."

        self.assertEqual(prepare.extract_confirmed_answer(content), "목표까지 예상 비용")

    def test_distinct_option_reasons_do_not_repeat(self):
        reasons = prepare.option_reasons(
            ["정렬", "캐싱", "삭제"],
            "탐색 방향 안내",
        )

        self.assertEqual(len(set(reasons)), 3)


if __name__ == "__main__":
    unittest.main()

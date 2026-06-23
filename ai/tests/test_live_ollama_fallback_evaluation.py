import unittest

from scripts.evaluate_live_ollama_fallback import assess_answer, evaluate_cases


class LiveOllamaFallbackEvaluationTest(unittest.TestCase):
    def test_assess_answer_requires_korean_length_and_topic_anchor(self):
        good = "StructuredTaskScope는 여러 하위 작업의 수명주기를 하나의 범위에서 관리합니다. 작업을 fork한 뒤 join하고 결과를 확인하며, 실패가 발생하면 나머지 작업을 취소해 구조화된 동시성을 유지합니다."

        result = assess_answer(good, expected_terms=["StructuredTaskScope", "작업"])

        self.assertTrue(result["passed"])
        self.assertEqual(result["reasons"], [])

    def test_evaluate_cases_calls_ollama_only_for_fast_path_miss(self):
        calls = []

        def resolver(question):
            return {"hit": question == "known", "reason": "hit" if question == "known" else "retrieval_miss"}

        def caller(prompt):
            calls.append(prompt)
            return "새 개념은 코스의 기존 카드에 없는 내용을 설명합니다. 핵심 동작과 사용 조건을 구체적으로 안내하며 실제 적용 시 주의할 실패 조건도 함께 확인합니다."

        report = evaluate_cases(
            [
                {"id": "hit", "question": "known", "expected_terms": ["기존"]},
                {"id": "miss", "question": "unknown", "expected_terms": ["새 개념"]},
            ],
            resolver=resolver,
            caller=caller,
        )

        self.assertEqual(len(calls), 1)
        self.assertEqual(report["fallback_invocation_count"], 1)
        self.assertEqual(report["fallback_passed_count"], 1)
        self.assertEqual(report["rows"][0]["route"], "fast_path")
        self.assertEqual(report["rows"][1]["route"], "ollama_fallback")

    def test_evaluate_cases_rejects_forbidden_factual_marker(self):
        report = evaluate_cases(
            [{
                "id": "java-structured",
                "question": "StructuredTaskScope가 뭐야?",
                "expected_terms": ["StructuredTaskScope", "작업"],
                "forbidden_terms": ["Spring", "@Async"],
            }],
            resolver=lambda _: {"hit": False, "reason": "retrieval_miss"},
            caller=lambda _: "StructuredTaskScope는 Spring @Async로 작업을 실행하는 기능입니다. 여러 작업을 하나의 범위에서 관리하고 결과를 기다리는 구조를 제공합니다.",
        )

        self.assertFalse(report["rows"][0]["quality"]["passed"])
        self.assertIn("forbidden_factual_marker", report["rows"][0]["quality"]["reasons"])

    def test_fallback_prompt_requests_concise_complete_answer(self):
        prompts = []
        evaluate_cases(
            [{"id": "miss", "question": "unknown", "expected_terms": ["개념"]}],
            resolver=lambda _: {"hit": False, "reason": "retrieval_miss"},
            caller=lambda prompt: prompts.append(prompt) or "이 개념은 작업을 예측 가능하게 관리합니다. 동작 원리와 사용 조건, 실패 시 주의점을 구체적으로 설명하는 충분한 길이의 한국어 답변입니다.",
        )

        self.assertIn("500자 이내", prompts[0])

    def test_diagnostic_failure_is_reported_but_does_not_fail_required_gate(self):
        answers = iter([
            "짧음",
            "CopyOnWriteArrayList는 쓰기 시 내부 배열을 복사하고 읽기는 잠금 없이 수행하는 동시성 컬렉션입니다. 읽기가 많고 쓰기가 드문 작업에서 반복 안정성을 제공하지만 쓰기 비용과 메모리 복사 비용을 고려해야 합니다.",
        ])
        report = evaluate_cases(
            [
                {"id": "diagnostic", "question": "new api", "expected_terms": ["없는말"], "required_for_gate": False},
                {"id": "required", "question": "stable api", "expected_terms": ["CopyOnWriteArrayList", "복사"], "required_for_gate": True},
            ],
            resolver=lambda _: {"hit": False, "reason": "retrieval_miss"},
            caller=lambda _: next(answers),
        )

        self.assertEqual(report["fallback_passed_count"], 1)
        self.assertEqual(report["gate_case_count"], 1)
        self.assertEqual(report["gate_passed_count"], 1)


if __name__ == "__main__":
    unittest.main()

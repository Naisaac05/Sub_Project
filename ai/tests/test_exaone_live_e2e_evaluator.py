import unittest

from app.schemas import AiGenerateResponse
from scripts.evaluate_exaone_live_e2e import (
    evaluate_response,
    format_progress,
    render_markdown,
    run_case,
    summarize,
)


class ExaoneLiveE2EEvaluatorTest(unittest.TestCase):
    def test_evaluate_response_records_rule_checks_and_judge_event(self):
        response = AiGenerateResponse(
            answer="REST API는 HTTP로 자원을 다룹니다.",
            model_used="exaone3.5:2.4b",
            route="rag_generation",
            retrieved_concept_ids=["rest-api"],
            latency_ms=1234,
            fallback_used=False,
            quality_flags=[],
            observability_events=[
                {
                    "event": "ai_review.semantic_judge_evaluated",
                    "relevance_score": 0.9,
                    "hallucination_risk": "low",
                    "intent": "concept_definition",
                    "sub_intent": "definition",
                    "rag_policy": "latest_question_only",
                }
            ],
        )

        result = evaluate_response(
            {
                "id": "rest-api",
                "question": "REST API가 뭐야?",
                "required_keywords": ["HTTP", "자원"],
                "forbidden_claims": ["인덱스 프로토콜"],
            },
            "rag",
            response,
        )

        self.assertTrue(result["required_keywords_passed"])
        self.assertTrue(result["forbidden_claims_absent"])
        self.assertEqual(result["judge_event"]["relevance_score"], 0.9)
        self.assertEqual(result["retrieved_concept_ids"], ["rest-api"])
        self.assertEqual(result["intent"], "concept_definition")
        self.assertEqual(result["sub_intent"], "definition")
        self.assertEqual(result["rag_policy"], "latest_question_only")

    def test_summarize_separates_modes_and_counts_live_exaone(self):
        rows = [
            {
                "mode": "rag",
                "error": "",
                "model_used": "exaone3.5:2.4b",
                "route": "rag_generation",
                "retrieved_concept_ids": ["card"],
                "latency_ms": 100,
                "fallback_used": False,
                "quality_flags": [],
                "required_keywords_passed": True,
                "forbidden_claims_absent": True,
            },
            {
                "mode": "no_rag_forced",
                "error": "",
                "model_used": "template",
                "route": "generation",
                "retrieved_concept_ids": [],
                "latency_ms": 300,
                "fallback_used": True,
                "quality_flags": ["fallback"],
                "required_keywords_passed": False,
                "forbidden_claims_absent": True,
            },
        ]

        summary = summarize(rows)

        self.assertEqual(summary["rag"]["live_exaone_count"], 1)
        self.assertEqual(summary["rag"]["retrieval_rate"], 1.0)
        self.assertEqual(summary["no_rag_forced"]["retrieval_rate"], 0.0)
        self.assertEqual(summary["no_rag_forced"]["fallback_rate"], 1.0)

    def test_run_case_forced_no_rag_patches_workflow_retrieval(self):
        def fake_workflow_runner(mode, request):
            from app.workflow.nodes import retrieve_context

            contexts = retrieve_context(request.user_answer, limit=3)
            return AiGenerateResponse(
                answer="forced no rag",
                model_used="exaone3.5:2.4b",
                route="generation",
                retrieved_concept_ids=[context.concept_id for context in contexts],
            )

        result = run_case(
            {"id": "case", "question": "N+1 문제"},
            "no_rag_forced",
            workflow_runner=fake_workflow_runner,
        )

        self.assertEqual(result["retrieved_concept_ids"], [])

    def test_run_case_forces_live_generation_by_disabling_lightweight_answer(self):
        def fake_workflow_runner(mode, request):
            from app.workflow.nodes import resolve_lightweight_answer
            from app.workflow.embedding_intent import intent_from_label

            intent = intent_from_label("CONCEPT_DEFINITION", request.user_answer, confidence=0.9)
            lightweight = resolve_lightweight_answer(request.user_answer, intent)
            return AiGenerateResponse(
                answer="live generation" if lightweight is None else "fast path",
                model_used="exaone3.5:2.4b" if lightweight is None else "lightweight-template",
                route="generation" if lightweight is None else "static_fast_path",
            )

        result = run_case(
            {"id": "case", "question": "REST API가 뭐야?"},
            "rag",
            workflow_runner=fake_workflow_runner,
        )

        self.assertEqual(result["model_used"], "exaone3.5:2.4b")
        self.assertEqual(result["route"], "generation")

    def test_run_case_disables_semantic_judge_for_live_evaluation(self):
        def fake_workflow_runner(mode, request):
            from app.workflow.judge import judge_answer

            judge_result = judge_answer(
                mode,
                request,
                "answer",
                [],
                generator=lambda **kwargs: (_ for _ in ()).throw(
                    AssertionError("semantic judge generator must not run")
                ),
            )
            return AiGenerateResponse(
                answer=judge_result.reason,
                model_used="exaone3.5:2.4b",
                route="generation",
            )

        result = run_case(
            {"id": "case", "question": "질문"},
            "no_rag_forced",
            workflow_runner=fake_workflow_runner,
        )

        self.assertFalse(result["error"], result)
        self.assertEqual(result["answer"], "Skipped judge: disabled for live E2E evaluation")

    def test_render_markdown_includes_answers_and_mode_summary(self):
        rows = [
            {
                "id": "case",
                "question": "질문",
                "mode": "rag",
                "answer": "실제 답변",
                "error": "",
                "model_used": "exaone3.5:2.4b",
                "route": "rag_generation",
                "retrieved_concept_ids": ["card"],
                "latency_ms": 100,
                "fallback_used": False,
                "quality_flags": [],
                "required_keywords_passed": True,
                "forbidden_claims_absent": True,
                "judge_event": {},
            }
        ]

        markdown = render_markdown(rows, summarize(rows))

        self.assertIn("실제 답변", markdown)
        self.assertIn("rag", markdown)
        self.assertIn("Human Review", markdown)

    def test_format_progress_shows_count_percent_duration_and_eta(self):
        progress = format_progress(
            completed=3,
            total=24,
            case_id="rest-api",
            mode="rag",
            duration_seconds=12.4,
            elapsed_seconds=30.0,
        )

        self.assertIn("[3/24 12.5%]", progress)
        self.assertIn("rest-api / rag", progress)
        self.assertIn("took 12.4s", progress)
        self.assertIn("ETA 3m 30s", progress)


if __name__ == "__main__":
    unittest.main()

import unittest
import sys
import time
from unittest.mock import patch, MagicMock

from app.schemas import AiGenerateRequest
from app.rag.retriever import RetrievedContext
from app.workflow.intent import FreeQuestionIntent
from app.workflow.state import ReviewWorkflowState
from app.workflow.nodes import generate_answer_node
from app.workflow.runner import run_review_workflow
from app.workflow.grounding import GroundingResult
from app.workflow.judge import SemanticJudgeResult

class AdaptiveJudgeTest(unittest.TestCase):
    def test_tier0_fast_path(self):
        # 1. Tier 0 (No Judge) - Fast path / Cache hit
        # In Tier 0, both semantic and grounding judges are skipped.
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="지연 로딩이 뭐야?"
        )
        
        # Patch both nodes and runner cached answer to return cache hit
        with patch("app.workflow.nodes.get_cached_answer", return_value="캐시된 답변입니다"), \
             patch("app.workflow.runner.get_cached_answer", return_value="캐시된 답변입니다"):
            response = run_review_workflow("free-question", req)
            completed_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.workflow_completed"), None)
            self.assertIsNotNone(completed_event)
            self.assertEqual(completed_event["judge_tier"], "tier0")
            self.assertEqual(completed_event["semantic_judge_skipped"], True)
            self.assertEqual(completed_event["grounding_judge_skipped"], True)
            self.assertEqual(completed_event["grounding_async_executed"], False)
            self.assertEqual(completed_event["estimated_latency_saved_ms"], 4000.0)

    def test_tier1_short_concept_definition(self):
        # 2. Tier 1 (Semantic Only)
        # concept_definition intent + 답변 < 700자 + No High-Risk Keywords
        # -> Semantic Judge 동기 실행, Grounding Judge Skip
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="JPA 정의에 대해 설명해줘." # No high-risk keywords
        )
        
        def mock_generator(*args, **kwargs):
            prompt = kwargs.get("prompt", "")
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "정상 답변"}'
            return "JPA는 자바 표준 ORM 스펙입니다." # Short answer (< 700 chars)

        # Patch resolve_lightweight_answer and cache check to guarantee normal generation is triggered
        with patch("app.workflow.nodes.resolve_lightweight_answer", return_value=None), \
             patch("app.workflow.nodes.get_cached_answer", return_value=None), \
             patch("app.workflow.runner.get_cached_answer", return_value=None):
            response = run_review_workflow("free-question", req, generator=mock_generator)
            completed_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.workflow_completed"), None)
            self.assertIsNotNone(completed_event)
            self.assertEqual(completed_event["judge_tier"], "tier1")
            self.assertEqual(completed_event["semantic_judge_skipped"], False)
            self.assertEqual(completed_event["grounding_judge_skipped"], True)
            self.assertEqual(completed_event["grounding_async_executed"], False)
            self.assertEqual(completed_event["estimated_latency_saved_ms"], 2000.0)

    def test_tier2_high_risk_keyword(self):
        # 3. Tier 2 (Full Judge - forced by High-Risk keyword)
        # user_answer contains "왜 틀렸나"
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="왜 틀렸나 설명해줘." # contains High-Risk keyword "왜 틀렸나"
        )
        
        def mock_generator(*args, **kwargs):
            prompt = kwargs.get("prompt", "")
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "정상 답변"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.9, "evidence_coverage": 0.9, "unsupported_claims": [], "grounded": true, "reason": "Grounding evaluation succeeded", "prompt_version": "grounding_v1", "prompt_hash": "dummy"}'
            return "이 선지가 틀린 오답 이유는 영속성 컨텍스트 상태 변화 때문입니다."

        mock_ctx = RetrievedContext(
            concept_id="test-concept",
            title="test-title",
            content="## 평가 키워드\n- JPA\n- 영속성",
            score=10.0,
            metadata={}
        )

        with patch("app.workflow.nodes.retrieve_context", return_value=[mock_ctx]), \
             patch("app.workflow.nodes.resolve_lightweight_answer", return_value=None), \
             patch("app.workflow.nodes.get_cached_answer", return_value=None), \
             patch("app.workflow.runner.get_cached_answer", return_value=None):
            response = run_review_workflow("free-question", req, generator=mock_generator)
            completed_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.workflow_completed"), None)
            self.assertIsNotNone(completed_event)
            self.assertEqual(completed_event["judge_tier"], "tier2")
            self.assertEqual(completed_event["semantic_judge_skipped"], False)
            self.assertEqual(completed_event["grounding_judge_skipped"], False)
            self.assertEqual(completed_event["grounding_async_executed"], False) # unit test mode is sync
            self.assertEqual(completed_event["estimated_latency_saved_ms"], 0.0)

    def test_tier2_long_answer(self):
        # 3b. Tier 2 due to long answer (>= 700 chars)
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="JPA 정의에 대해 설명해줘."
        )
        
        long_answer = "JPA " * 200 # 800 chars
        
        def mock_generator(*args, **kwargs):
            prompt = kwargs.get("prompt", "")
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "정상 답변"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.9, "evidence_coverage": 0.9, "unsupported_claims": [], "grounded": true, "reason": "Grounding evaluation succeeded", "prompt_version": "grounding_v1", "prompt_hash": "dummy"}'
            return long_answer

        mock_ctx = RetrievedContext(
            concept_id="test-concept",
            title="test-title",
            content="## 평가 키워드\n- JPA\n- 영속성",
            score=10.0,
            metadata={}
        )

        with patch("app.workflow.nodes.retrieve_context", return_value=[mock_ctx]), \
             patch("app.workflow.nodes.resolve_lightweight_answer", return_value=None), \
             patch("app.workflow.nodes.get_cached_answer", return_value=None), \
             patch("app.workflow.runner.get_cached_answer", return_value=None):
            response = run_review_workflow("free-question", req, generator=mock_generator)
            completed_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.workflow_completed"), None)
            self.assertIsNotNone(completed_event)
            self.assertEqual(completed_event["judge_tier"], "tier2")
            self.assertEqual(completed_event["semantic_judge_skipped"], False)
            self.assertEqual(completed_event["grounding_judge_skipped"], False)

    def test_async_grounding_non_blocking_latency(self):
        # 4. Async grounding non-blocking latency check
        # Grounding runs in daemon background thread in production.
        # Main user response is returned immediately even if grounding LLM takes 2 seconds.
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="왜 틀렸나 설명해줘." # force Tier 2
        )
        
        def call_ollama(*args, **kwargs):
            prompt = kwargs.get("prompt", "")
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "정상 답변"}'
            if "precise Grounding Judge" in prompt:
                time.sleep(2.0) # Grounding judge takes 2 seconds!
                return '{"grounding_score": 0.9, "evidence_coverage": 0.9, "unsupported_claims": [], "grounded": true}'
            return "JPA 오답 이유 설명입니다."

        # Patch sys.modules to simulate production environment (no "unittest")
        import sys
        mods = sys.modules.copy()
        if "unittest" in mods:
            del mods["unittest"]
            
        with patch("sys.modules", mods):
            start_time = time.perf_counter()
            state = ReviewWorkflowState(mode="free-question", request=req)
            from app.rag.retriever import RetrievedContext
            state.contexts = [RetrievedContext(concept_id="test", title="test", content="## 평가 키워드\n- JPA", score=10.0, metadata={})]
            
            state = generate_answer_node(state, generator=call_ollama)
            elapsed = time.perf_counter() - start_time
            
            # Main response should be returned immediately (< 0.5s)
            self.assertLess(elapsed, 0.5)
            self.assertEqual(state.judge_tier, "tier2")
            self.assertEqual(state.grounding_async_executed, True)
            self.assertIsNotNone(state.grounding_thread)
            
            # Join the thread to clean up cleanly
            state.grounding_thread.join(timeout=3.0)

if __name__ == "__main__":
    unittest.main()

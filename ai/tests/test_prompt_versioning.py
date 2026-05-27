import unittest
from app.prompts.registry import compute_prompt_hash, lookup_prompt_version, PROMPT_REGISTRY
from app.prompts import build_prompt, prompt_version_for_mode, prompt_strategy_for_mode
from app.schemas import AiGenerateRequest
from app.workflow.intent import FreeQuestionIntent
from app.workflow.state import ReviewWorkflowState
from app.workflow.nodes import generate_answer_node
from app.workflow.runner import run_review_workflow


class PromptVersioningTest(unittest.TestCase):
    def test_deterministic_hash(self):
        # 1. deterministic hash: 동일한 프롬프트는 항상 동일한 해시 반환
        prompt1 = "Hello, this is a test prompt."
        prompt2 = "Hello, this is a test prompt."
        self.assertEqual(compute_prompt_hash(prompt1), compute_prompt_hash(prompt2))
        
        # SHA-256 해시 길이는 64자
        self.assertEqual(len(compute_prompt_hash(prompt1)), 64)

    def test_whitespace_insensitive_hashing(self):
        # 2. whitespace-insensitive hashing: 다양한 형태의 공백 및 개행이 정규화되어 동일 해시 보장
        prompt_base = "You are a programming mentor. Explain: JPA."
        prompt_with_newlines = "\n  You  are  a  programming  mentor. \n\t Explain:\tJPA. \n"
        prompt_with_trailing = "You are a programming mentor. Explain: JPA.    "
        
        hash_base = compute_prompt_hash(prompt_base)
        hash_newlines = compute_prompt_hash(prompt_with_newlines)
        hash_trailing = compute_prompt_hash(prompt_with_trailing)
        
        self.assertEqual(hash_base, hash_newlines)
        self.assertEqual(hash_base, hash_trailing)

    def test_prompt_strategy_differentiation(self):
        # 3. prompt strategy differentiation: 서로 다른 의도가 서로 다른 전략명과 고유 해시를 보장하는지 검증
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="지연 로딩에 대해 알려줘."
        )
        
        intent1 = FreeQuestionIntent(
            intent="concept_definition",
            rag_policy="latest_only",
            topic="JPA",
            confidence=0.9,
            context_dependent=False,
            sub_intent="definition"
        )
        
        intent2 = FreeQuestionIntent(
            intent="wrong_answer_explanation",
            rag_policy="mixed",
            topic="JPA",
            confidence=0.8,
            context_dependent=True,
            sub_intent="explanation"
        )
        
        prompt1 = build_prompt("free-question", req, context="retrieved data", intent=intent1)
        prompt2 = build_prompt("free-question", req, context="retrieved data", intent=intent2)
        
        strategy1 = prompt_strategy_for_mode("free-question", intent1)
        strategy2 = prompt_strategy_for_mode("free-question", intent2)
        
        hash1 = compute_prompt_hash(prompt1)
        hash2 = compute_prompt_hash(prompt2)
        
        self.assertNotEqual(strategy1, strategy2)
        self.assertNotEqual(hash1, hash2)
        self.assertEqual(strategy1, "free-question:concept_definition:context_dependent=False")
        self.assertEqual(strategy2, "free-question:wrong_answer_explanation:context_dependent=True")

    def test_retry_metadata_isolation(self):
        # 4. retry metadata isolation: 재시도 발생 시 retry_prompt_version/hash가 main과 혼동 없이 별도로 추적됨
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="지연 로딩이 뭐야?"
        )
        
        intent = FreeQuestionIntent(
            intent="concept_definition",
            rag_policy="latest_only",
            topic="JPA",
            confidence=0.9,
            context_dependent=True,
            sub_intent="definition"
        )
        
        state = ReviewWorkflowState(mode="free-question", request=req)
        state.free_question_intent = intent
        
        # 1차 답변 부합도가 낮아 재시도를 유도하도록 Semantic Judge Mocking
        def mock_generator_low_quality(*args, **kwargs):
            # Semantic Judge JSON 프로토콜 평결 Mock
            if "precise AI Semantic Judge" in kwargs.get("prompt", ""):
                return '{"relevance_score": 0.4, "context_bias_score": 0.8, "hallucination_risk": "low", "should_retry": true, "reason": "테스트용 부적격 답변 판정"}'
            return "지연 로딩은 JPA의 핵심 기술입니다."
            
        generate_answer_node(state, generator=mock_generator_low_quality)
        
        # 1차 메인 해시와 2차 리트라이 해시 분리 확인
        self.assertIsNotNone(state.prompt_hash)
        self.assertIsNotNone(state.retry_prompt_hash)
        self.assertNotEqual(state.prompt_hash, state.retry_prompt_hash)
        self.assertEqual(state.retry_prompt_version, "retry_v1")
        self.assertEqual(state.semantic_judge_prompt_version, "semantic_judge_v1")
        self.assertIsNotNone(state.semantic_judge_prompt_hash)

    def test_observability_metadata_emission(self):
        # 5. observability metadata emission: 완성 시 방출되는 Observability 이벤트에 프롬프트 정보가 온전히 탑재되는지 최종 검증
        req = AiGenerateRequest(
            question="JPA가 뭐야?",
            correct_answer="JPA",
            user_answer="지연 로딩에 대해 설명해줄래?"
        )
        
        # RAG Grounding Judge와 Semantic Judge가 모두 Mocking된 Generator 기동
        def mock_complete_generator(*args, **kwargs):
            prompt = kwargs.get("prompt", "")
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "정상 답변"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.9, "evidence_coverage": 0.9, "unsupported_claims": [], "grounded": true}'
            return "지연 로딩은 실제 사용될 때 DB를 조회하는 프록시 기술입니다."

        response = run_review_workflow("free-question", req, generator=mock_complete_generator)
        
        # observability events 분석
        completed_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.workflow_completed"), None)
        judge_event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        
        self.assertIsNotNone(completed_event)
        self.assertIsNotNone(judge_event)
        
        # completed_event 필드 검증
        self.assertEqual(completed_event["prompt_version"], "concept_definition_v1")
        self.assertIsNotNone(completed_event["prompt_hash"])
        self.assertEqual(completed_event["prompt_strategy"], "free-question:concept_definition:context_dependent=False")
        
        # judge_event 필드 검증
        self.assertEqual(judge_event["prompt_version"], "concept_definition_v1")
        self.assertIsNotNone(judge_event["prompt_hash"])
        self.assertEqual(judge_event["semantic_judge_prompt_version"], "semantic_judge_v1")
        self.assertIsNotNone(judge_event["semantic_judge_prompt_hash"])


if __name__ == "__main__":
    unittest.main()

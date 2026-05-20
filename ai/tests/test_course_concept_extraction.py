import json
from pathlib import Path
import tempfile
import unittest

from app.knowledge.extraction import (
    extract_candidate_concepts,
    parse_course_skill_questions,
    write_candidate_jsonl as write_extracted_candidate_jsonl,
)
from app.knowledge.approval import (
    load_candidate_jsonl,
    promote_approved_candidates,
)
from app.knowledge.review import (
    TemplateCandidateReviewProvider,
    enrich_candidate_with_ai_review,
    write_candidate_jsonl as write_review_candidate_jsonl,
)
from app.knowledge.registry import mark_duplicate_candidates
from app.knowledge.human_review import apply_human_review
from app.rag.documents import load_concept_cards


SAMPLE_INITIALIZER = '''
seedCourse("python-backend", "Python Backend", "desc",
        List.of(
                sq(1, "Python에서 리스트 컴프리헨션을 사용하는 장점은 무엇인가요?",
                        List.of("반복 변환 로직을 간결하게 표현", "DB 트랜잭션 생성"), 0),
                sq(2, "FastAPI에서 요청 본문을 검증하는 데 자주 쓰이는 것은 무엇인가요?",
                        List.of("Pydantic 모델", "CSS module"), 0)
        ));
seedCourse("kafka", "Kafka Deep Dive", "desc",
        List.of(
                sq(1, "Kafka에서 partition을 사용하는 이유는 무엇인가요?",
                        List.of("병렬 처리와 확장성", "HTTP 요청"), 0)
        ));
'''


class CourseConceptExtractionTest(unittest.TestCase):
    def test_parse_course_skill_questions_from_initializer_source(self):
        questions = parse_course_skill_questions(SAMPLE_INITIALIZER, source_path="CourseSkillTestInitializer.java")

        self.assertEqual(len(questions), 3)
        self.assertEqual(questions[0].category, "python-backend")
        self.assertEqual(questions[0].order, 1)
        self.assertIn("리스트 컴프리헨션", questions[0].question)
        self.assertIn("반복 변환 로직", questions[0].options[0])
        self.assertEqual(questions[2].category, "kafka")

    def test_extract_candidate_concepts_from_questions(self):
        questions = parse_course_skill_questions(SAMPLE_INITIALIZER)
        candidates = extract_candidate_concepts(questions)

        terms = {candidate["term"] for candidate in candidates}
        self.assertIn("리스트 컴프리헨션", terms)
        self.assertIn("FastAPI", terms)
        self.assertIn("Pydantic", terms)
        self.assertIn("Kafka partition", terms)
        self.assertTrue(all(candidate["approved"] is False for candidate in candidates))

    def test_write_candidate_jsonl_is_deterministic(self):
        questions = parse_course_skill_questions(SAMPLE_INITIALIZER)
        candidates = extract_candidate_concepts(questions)

        with tempfile.TemporaryDirectory() as tmp:
            output_path = Path(tmp) / "course_concepts.jsonl"
            write_extracted_candidate_jsonl(candidates, output_path)
            rows = [json.loads(line) for line in output_path.read_text(encoding="utf-8").splitlines()]

        self.assertEqual(rows, sorted(rows, key=lambda row: (row["term"].lower(), row["category"])))
        self.assertIn("source_question_ids", rows[0])

    def test_promote_only_approved_candidates_with_definitions(self):
        candidates = [
            {
                "term": "FastAPI",
                "aliases": ["fastapi"],
                "definition": "FastAPI는 Python으로 API 서버를 만들 때 쓰는 웹 프레임워크입니다.",
                "definition_status": "human_reviewed",
                "category": "python-backend",
                "source": "CourseSkillTestInitializer",
                "source_path": "backend/src/main/java/com/devmatch/config/CourseSkillTestInitializer.java",
                "source_question_ids": ["python-backend:2"],
                "approved": True,
            },
            {
                "term": "Pydantic",
                "aliases": ["pydantic"],
                "definition": "Pydantic은 타입 기반 데이터 검증 도구입니다.",
                "definition_status": "pending_human_review",
                "category": "python-backend",
                "source": "CourseSkillTestInitializer",
                "source_question_ids": ["python-backend:2"],
                "approved": False,
            },
            {
                "term": "Django ORM",
                "aliases": ["django orm"],
                "definition": "",
                "definition_status": "human_reviewed",
                "category": "python-backend",
                "source": "CourseSkillTestInitializer",
                "source_question_ids": ["python-backend:3"],
                "approved": True,
            },
        ]

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            candidate_path = root / "candidates.jsonl"
            write_extracted_candidate_jsonl(candidates, candidate_path)

            loaded = load_candidate_jsonl(candidate_path)
            written = promote_approved_candidates(
                loaded,
                output_root=root / "concepts",
                today="2026-05-18",
            )
            cards = load_concept_cards(root / "concepts")

        self.assertEqual(len(written), 1)
        self.assertEqual(len(cards), 1)
        self.assertEqual(cards[0].concept_id, "python-backend-fastapi")
        self.assertEqual(cards[0].metadata["category"], "python-backend")
        self.assertIn("FastAPI", cards[0].title)
        self.assertIn("Python으로 API 서버", cards[0].sections["핵심 설명"])
        self.assertGreaterEqual(len([line for line in cards[0].sections["평가 키워드"].splitlines() if line.startswith("-")]), 2)

    def test_enrich_candidate_adds_draft_and_critic_metadata_without_approval(self):
        candidate = {
            "term": "FastAPI",
            "aliases": ["fastapi"],
            "definition": "",
            "definition_status": "pending_human_review",
            "category": "python-backend",
            "source": "CourseSkillTestInitializer",
            "source_question_ids": ["python-backend:2"],
            "approved": False,
        }

        enriched = enrich_candidate_with_ai_review(
            candidate,
            provider=TemplateCandidateReviewProvider(),
            drafted_at="2026-05-18T10:00:00+09:00",
        )

        self.assertFalse(enriched["approved"])
        self.assertEqual(enriched["definition_status"], "critic_reviewed")
        self.assertIn("FastAPI", enriched["definition_draft"])
        self.assertEqual(enriched["draft_model"], "template-candidate-review")
        self.assertEqual(enriched["draft_version"], "candidate-draft-v1")
        self.assertIn("risk_level", enriched["critic_feedback"])
        self.assertIn(enriched["critic_risk_level"], {"low", "medium", "high"})
        self.assertEqual(enriched["critic_model"], "template-candidate-review")
        self.assertEqual(enriched["critic_version"], "candidate-critic-v1")
        self.assertEqual(enriched["sources"], ["python-backend:2"])
        self.assertEqual(enriched["rejected_reason"], "")

    def test_template_review_provider_uses_specific_definition_for_known_accessibility_term(self):
        candidate = {
            "term": "aria-label",
            "aliases": ["aria-label", "arialabel"],
            "definition": "",
            "definition_status": "pending_human_review",
            "category": "frontend",
            "source_question_ids": ["frontend:10"],
            "approved": False,
        }

        enriched = enrich_candidate_with_ai_review(
            candidate,
            provider=TemplateCandidateReviewProvider(),
            drafted_at="2026-05-18T10:00:00+09:00",
        )

        self.assertIn("스크린리더", enriched["definition_draft"])
        self.assertIn("아이콘 버튼", enriched["definition_draft"])
        self.assertNotIn("확인해야 하는 기술 개념", enriched["definition_draft"])
        self.assertIn("보이는 텍스트", enriched["critic_feedback"]["critic_feedback"])
        self.assertEqual(enriched["critic_recommendation"], "approve")

    def test_write_candidate_jsonl_preserves_review_metadata(self):
        candidate = enrich_candidate_with_ai_review(
            {
                "term": "Pydantic",
                "aliases": ["pydantic"],
                "definition": "",
                "definition_status": "pending_human_review",
                "category": "python-backend",
                "source_question_ids": ["python-backend:2"],
                "approved": False,
            },
            provider=TemplateCandidateReviewProvider(),
            drafted_at="2026-05-18T10:00:00+09:00",
        )

        with tempfile.TemporaryDirectory() as tmp:
            output_path = Path(tmp) / "candidates.jsonl"
            write_review_candidate_jsonl([candidate], output_path)
            rows = [json.loads(line) for line in output_path.read_text(encoding="utf-8").splitlines()]

        self.assertEqual(rows[0]["definition_status"], "critic_reviewed")
        self.assertEqual(rows[0]["drafted_at"], "2026-05-18T10:00:00+09:00")
        self.assertIn("critic_feedback", rows[0])

    def test_mark_duplicate_candidates_against_global_concept_registry(self):
        candidate = {
            "term": "JPA N+1",
            "aliases": ["n+1"],
            "definition": "JPA N+1은 연관 엔티티 조회가 반복되며 쿼리가 늘어나는 문제입니다.",
            "category": "spring-jpa",
            "source_question_ids": ["java-backend:4"],
            "approved": True,
        }

        with tempfile.TemporaryDirectory() as tmp:
            concept_root = Path(tmp) / "concepts"
            concept_root.mkdir()
            (concept_root / "n-plus-one.md").write_text(
                """---
id: spring-n-plus-one
category: spring-jpa
difficulty: intermediate
version: test
last_updated: 2026-05-18
---

# N+1 문제

## 핵심 설명
N+1 문제는 연관 데이터 조회 쿼리가 반복되는 상황입니다.

## 대표 해결
- fetch join

## 흔한 오해
- 모든 조회 문제가 N+1은 아닙니다.

## 평가 키워드
- n+1
- fetch join
""",
                encoding="utf-8",
            )
            cards = load_concept_cards(concept_root)

        marked = mark_duplicate_candidates([candidate], cards)

        self.assertEqual(marked[0]["duplicate_status"], "duplicate_suspected")
        self.assertEqual(marked[0]["duplicate_concept_ids"], ["spring-n-plus-one"])
        self.assertIn("spring-n-plus-one", marked[0]["duplicate_reason"])

    def test_apply_human_review_approves_draft_definition(self):
        candidate = {
            "term": "FastAPI",
            "definition": "",
            "definition_draft": "FastAPI는 Python으로 API 서버를 만들 때 사용하는 웹 프레임워크입니다.",
            "definition_status": "critic_reviewed",
            "approved": False,
        }

        reviewed = apply_human_review(
            candidate,
            action="approve",
            reviewed_at="2026-05-18T11:00:00+09:00",
            reviewer="manual-cli",
        )

        self.assertTrue(reviewed["approved"])
        self.assertEqual(reviewed["definition"], reviewed["definition_draft"])
        self.assertEqual(reviewed["human_review_status"], "approved")
        self.assertEqual(reviewed["definition_status"], "human_approved")
        self.assertEqual(reviewed["reviewer"], "manual-cli")

    def test_apply_human_review_rejects_with_reason(self):
        candidate = {
            "term": "API",
            "definition": "",
            "definition_draft": "API는 넓은 기술 용어입니다.",
            "approved": False,
        }

        reviewed = apply_human_review(
            candidate,
            action="reject",
            rejected_reason="too broad for a standalone card",
            reviewed_at="2026-05-18T11:00:00+09:00",
        )

        self.assertFalse(reviewed["approved"])
        self.assertEqual(reviewed["human_review_status"], "rejected")
        self.assertEqual(reviewed["definition_status"], "human_rejected")
        self.assertEqual(reviewed["rejected_reason"], "too broad for a standalone card")


if __name__ == "__main__":
    unittest.main()

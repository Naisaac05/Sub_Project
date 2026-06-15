import tempfile
import unittest
import copy
from datetime import timezone
from pathlib import Path

from app.scripts import migrate_rag_cards as migration
from app.schemas.rag_card import CardStatus, PayloadStatus


class RecordingRetriever:
    def __init__(self, results):
        self.results = results
        self.limits = []

    def retrieve(self, query, limit=5):
        self.limits.append(limit)
        return self.results[:limit]


class MigrationV2Test(unittest.TestCase):
    def test_output_is_isolated(self):
        self.assertEqual(migration.OUT_ROOT.name, "concepts_v2")
        self.assertNotEqual(migration.OUT_ROOT, migration.PRODUCTION_ROOT)

    def test_write_cards_atomically_writes_only_valid_draft_cards(self):
        question = migration.Question(1, "Java equals의 역할은?", ["==", "equals"], 1, 1)
        card = migration.create_cards([[migration.build_draft(question)]])[0]
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "concepts_v2"

            written = migration.write_cards_atomically([card], output)

            self.assertEqual(written, 1)
            files = list(output.rglob("*.json"))
            self.assertEqual(len(files), 1)
            loaded = migration.RagCard.model_validate_json(files[0].read_text(encoding="utf-8"))
            self.assertEqual(loaded.review.card_status, CardStatus.DRAFT)
            self.assertEqual(set(loaded.review.payload_status.values()), {PayloadStatus.DRAFT})

    def test_write_cards_atomically_rolls_back_on_validation_failure(self):
        question = migration.Question(1, "Java equals의 역할은?", ["==", "equals"], 1, 1)
        card = migration.create_cards([[migration.build_draft(question)]])[0]
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "concepts_v2"
            output.mkdir()
            sentinel = output / "sentinel.json"
            sentinel.write_text('{"existing": true}', encoding="utf-8")
            card.review.card_status = CardStatus.APPROVED

            with self.assertRaisesRegex(ValueError, "validation failed"):
                migration.write_cards_atomically([card], output)

            self.assertTrue(sentinel.exists())
            self.assertEqual(list(output.iterdir()), [sentinel])

    def test_extract_questions_preserves_zero_based_correct_answer(self):
        sql = (
            "INSERT INTO `questions` (`id`, `content`, `correct_answer`, `created_at`, "
            "`options`, `order_index`, `score`, `test_id`) VALUES "
            "(7,'Java equals의 역할은?',1,'2026-01-01','[\"==\",\"equals\"]',1,10,2);\n"
            "INSERT INTO `questions` (`id`, `content`, `correct_answer`, `created_at`, "
            "`options`, `order_index`, `score`, `test_id`) VALUES "
            "(8,'E2E 실패 질문',0,'2026-01-01','[\"a\",\"b\"]',2,10,2);"
        )
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "data.sql"
            path.write_text(sql, encoding="utf-8")
            questions = migration.extract_questions(path)

        self.assertEqual(len(questions), 1)
        self.assertEqual(questions[0].correct_answer, 1)
        self.assertEqual(questions[0].correct_text, "equals")

    def test_extract_questions_decodes_dump_escaped_option_json(self):
        sql = (
            "INSERT INTO `questions` (`id`, `content`, `correct_answer`, `created_at`, "
            "`options`, `order_index`, `score`, `test_id`) VALUES "
            r"""(1,'Java 변수 선언은?',0,'2026-01-01','[\"int value\",\"String value\"]',1,10,1);"""
        )
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "data.sql"
            path.write_text(sql, encoding="utf-8")

            questions = migration.extract_questions(path)

        self.assertEqual(questions[0].options, ["int value", "String value"])

    def test_generated_card_is_draft_and_retrieval_text_is_compact(self):
        question = migration.Question(
            id=1,
            content="React에서 리스트 key가 필요한 이유는?",
            options=["재조정 식별", "스타일 적용"],
            correct_answer=0,
            test_id=7,
        )
        draft = migration.build_draft(question)
        card = migration.create_cards([[draft]])[0]

        self.assertEqual(card.review.card_status, CardStatus.DRAFT)
        self.assertEqual(
            set(card.review.payload_status.values()),
            {PayloadStatus.DRAFT},
        )
        self.assertEqual(
            set(card.review.payload_status),
            {"CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON"},
        )
        self.assertIsNone(card.payloads.COMPARISON)
        self.assertNotIn(question.content, card.retrieval.embedding_text)
        self.assertGreaterEqual(len(card.retrieval.boost_keywords), 3)
        self.assertLessEqual(len(card.retrieval.boost_keywords), 7)
        self.assertIsNotNone(card.created_at.tzinfo)
        self.assertEqual(card.created_at.utcoffset(), timezone.utc.utcoffset(card.created_at))

    def test_lint_accepts_consistently_approved_generated_payloads(self):
        card = migration.create_cards([[migration.build_draft(
            migration.Question(1, "Java equals", ["==", "equals"], 1, 1)
        )]])[0]
        card.review.card_status = CardStatus.APPROVED
        card.review.payload_status = {
            intent: PayloadStatus.APPROVED for intent in migration.GENERATED_INTENTS
        }

        self.assertEqual(migration.lint_cards([card]), [])

    def test_lint_rejects_mixed_card_and_payload_approval_status(self):
        card = migration.create_cards([[migration.build_draft(
            migration.Question(1, "Java equals", ["==", "equals"], 1, 1)
        )]])[0]
        card.review.card_status = CardStatus.APPROVED

        errors = migration.lint_cards([card])

        self.assertTrue(any("payload_status must match card_status" in error for error in errors))

    def test_broad_term_is_specialized_and_card_id_is_bounded(self):
        question = migration.Question(1, "Spring Cache의 역할은?", ["캐시", "큐"], 0, 4)
        draft = migration.ConceptDraft(question, "spring", "cache", ["cache"], ["cache", "spring"])
        card = migration.create_cards([[draft]])[0]

        self.assertNotEqual(card.term, "cache")
        self.assertTrue(card.card_id.startswith("spring-"))
        self.assertLessEqual(len(card.card_id), 80)

    def test_extracts_dedicated_react_key_and_java_equals_terms(self):
        react_key = migration.Question(
            65,
            "React에서 리스트를 렌더링할 때 각 요소에 필요한 속성은?",
            ["id", "name", "key", "index"],
            2,
            7,
        )
        java_equals = migration.Question(
            2,
            "Java에서 문자열을 비교할 때 올바른 방법은?",
            ["str1 == str2", "str1.equals(str2)"],
            1,
            1,
        )

        self.assertEqual(migration.build_draft(react_key).term, "react-key")
        self.assertEqual(migration.build_draft(java_equals).term, "equals")

    def test_filters_general_korean_words_and_extracts_specific_terms(self):
        cases = [
            migration.Question(7, "다음 중 반복문이 아닌 것은?", ["for", "switch"], 1, 1),
            migration.Question(9, "접근 제어자 중 같은 패키지 내에서만 접근 가능한 것은?", ["private", "default"], 1, 1),
            migration.Question(22, "G1 GC의 특징으로 올바르지 않은 것은?", ["region", "fixed"], 1, 3),
            migration.Question(83, "React Server Components(RSC)의 특징이 아닌 것은?", ["server", "useState"], 1, 9),
        ]

        terms = [migration.build_draft(question).term for question in cases]

        self.assertEqual(terms, ["loop-control", "access-modifier", "g1-gc", "react-server-components"])
        self.assertFalse({"에서", "반복문이", "접근", "특징으로"} & set(terms))

    def test_extracts_specific_terms_for_remaining_held_cards(self):
        questions = [
            migration.Question(134, "BFS(너비 우선 탐색)에서 사용하는 자료구조는?", ["스택", "큐"], 1, 14),
            migration.Question(62, "JSX에서 JavaScript 표현식을 사용할 때 감싸는 기호는?", ["( )", "{ }"], 1, 7),
            migration.Question(66, "함수형 컴포넌트의 올바른 선언 방법은?", ["class App", "function App"], 1, 7),
            migration.Question(13, "다음 중 함수형 인터페이스가 아닌 것은?", ["Runnable", "List"], 1, 2),
        ]

        drafts = [migration.build_draft(question) for question in questions]

        self.assertEqual(
            [draft.term for draft in drafts],
            ["breadth-first-search", "jsx-expression", "functional-component", "functional-interface"],
        )
        self.assertTrue(all(len({migration.normalize_term(alias) for alias in draft.aliases}) > 1 for draft in drafts))

    def test_generated_aliases_include_distinctive_paraphrase(self):
        draft = migration.build_draft(migration.Question(
            65,
            "React에서 리스트를 렌더링할 때 각 요소에 필요한 속성은?",
            ["id", "name", "key", "index"],
            2,
            7,
        ))

        self.assertIn("react reconciliation key", draft.aliases)
        self.assertGreater(len({migration.normalize_term(alias) for alias in draft.aliases}), 1)

    def test_embedding_similarity_requires_distinctive_token_overlap(self):
        q1 = migration.Question(69, "React 프로젝트 생성 도구", ["Maven"], 0, 7)
        q2 = migration.Question(77, "useRef의 용도", ["DOM 접근"], 0, 8)
        left = migration.ConceptDraft(q1, "frontend", "react-project-tool", ["react project tool"], ["react", "project", "tool"])
        right = migration.ConceptDraft(q2, "frontend", "useref", ["use ref"], ["useref", "dom", "reference"])
        left.embedding = [1.0, 0.0]
        right.embedding = [1.0, 0.0]

        self.assertFalse(migration.should_merge(left, right))

    def test_generic_alias_overlap_does_not_merge_different_concepts(self):
        function_definition = migration.build_draft(migration.Question(
            96,
            "Python 함수 정의에 사용하는 키워드는?",
            ["def", "function"],
            0,
            10,
        ))
        mutable_default = migration.build_draft(migration.Question(
            109,
            "다음 코드의 출력은?\ndef f(a, b=[]): return b",
            ["[1] [1, 2]", "[1] [2]"],
            0,
            11,
        ))

        self.assertIn("python def", set(function_definition.aliases) & set(mutable_default.aliases))
        self.assertFalse(migration.should_merge(function_definition, mutable_default))

    def test_merge_does_not_use_source_question_ids(self):
        q1 = migration.Question(3, "A", ["a"], 0, 1)
        q2 = migration.Question(3, "B", ["b"], 0, 4)
        a = migration.ConceptDraft(q1, "java", "equals", ["equality"], ["equals"])
        b = migration.ConceptDraft(q2, "spring", "equals", ["equality"], ["equals"])

        clusters, merge_count = migration.cluster_drafts([a, b], migration.FakeEmbedder())

        self.assertEqual(len(clusters), 2)
        self.assertEqual(merge_count, 0)

    def test_loo_retrieves_fifty_before_removing_source_card(self):
        cards = [
            migration.create_cards([[migration.ConceptDraft(
                migration.Question(1, "equals", ["equals"], 0, 1),
                "java", "equals", ["equality"], ["equals", "java", "comparison"],
            )]])[0],
            migration.create_cards([[migration.ConceptDraft(
                migration.Question(2, "hashCode", ["hashCode"], 0, 1),
                "java", "hashcode", ["hash code"], ["hashcode", "java", "hash"],
            )]])[0],
        ]
        from app.rag.retriever import RetrievedContext
        retriever = RecordingRetriever([
            RetrievedContext(cards[0].card_id, cards[0].term, "", 0.9, {}),
            RetrievedContext(cards[1].card_id, cards[1].term, "", 0.7, {}),
        ])

        metrics = migration.evaluate_retrieval(
            [migration.Question(1, "equals", ["equals"], 0, 1)],
            cards,
            retriever,
        )

        self.assertEqual(retriever.limits, [50])
        self.assertEqual(metrics.loo_candidate_rate, 1.0)
        self.assertEqual(metrics.loo_average_score, 0.7)

    def test_production_evaluation_ignores_payload_changes(self):
        card = migration.create_cards([[migration.build_draft(
            migration.Question(1, "Java equals", ["==", "equals"], 1, 1)
        )]])[0]
        card.review.card_status = CardStatus.APPROVED
        card.review.payload_status = {
            intent: PayloadStatus.APPROVED for intent in migration.GENERATED_INTENTS
        }
        before = copy.deepcopy(card)
        production_before = migration.build_evaluation_retriever([before], "production_mode")
        content_before = migration.build_evaluation_retriever([before], "content_mode")

        card.payloads.CONCEPT_DEFINITION.content = "payload-only-marker"

        production_after = migration.build_evaluation_retriever([card], "production_mode")
        content_after = migration.build_evaluation_retriever([card], "content_mode")

        self.assertEqual(
            production_before.retrieve("payload-only-marker"),
            production_after.retrieve("payload-only-marker"),
        )
        self.assertEqual(content_before.retrieve("payload-only-marker"), [])
        self.assertEqual(content_after.retrieve("payload-only-marker")[0].concept_id, card.card_id)

    def test_production_search_text_uses_locked_retrieval_fields_only(self):
        card = migration.create_cards([[migration.build_draft(
            migration.Question(1, "Java equals", ["==", "equals"], 1, 1)
        )]])[0]
        card.payloads.CONCEPT_DEFINITION.content = "payload-only-marker"

        text = migration.production_search_text(card)

        self.assertIn(card.retrieval.embedding_text, text)
        self.assertIn(card.term, text)
        self.assertIn(card.category, text)
        self.assertNotIn("payload-only-marker", text)

    def test_payload_patch_acceptance_uses_production_and_content_modes_separately(self):
        before = migration.RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        production_after = migration.RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        content_after = migration.RetrievalMetrics(0.87, 0.9, 0.9, 1.0, 4.0, 0.8)

        accepted, reasons = migration.payload_patch_acceptance(
            before,
            before,
            production_after,
            content_after,
        )

        self.assertFalse(accepted)
        self.assertEqual(reasons, ["content_hit1_decreased_over_2_percent"])

    def test_payload_patch_acceptance_allows_content_loo_change(self):
        before = migration.RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        content_after = migration.RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 3.0, 0.8)

        accepted, reasons = migration.payload_patch_acceptance(
            before,
            before,
            before,
            content_after,
        )

        self.assertTrue(accepted)
        self.assertEqual(reasons, [])


if __name__ == "__main__":
    unittest.main()

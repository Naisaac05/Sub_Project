import pathlib
import sys


AI_ROOT = pathlib.Path(__file__).resolve().parents[1]
INTENT_POC_ROOT = AI_ROOT / "evals" / "intent_poc"
if str(INTENT_POC_ROOT) not in sys.path:
    sys.path.insert(0, str(INTENT_POC_ROOT))


def test_filter_rows_by_split_keeps_only_requested_split():
    from evaluate import filter_rows_by_split

    rows = [
        {"id": "dev-a", "split": "dev"},
        {"id": "holdout-a", "split": "holdout"},
        {"id": "holdout-b", "split": "holdout"},
    ]

    filtered = filter_rows_by_split(rows, "holdout")

    assert [row["id"] for row in filtered] == ["holdout-a", "holdout-b"]


def test_filter_rows_by_split_returns_all_rows_without_split():
    from evaluate import filter_rows_by_split

    rows = [
        {"id": "dev-a", "split": "dev"},
        {"id": "holdout-a", "split": "holdout"},
    ]

    filtered = filter_rows_by_split(rows, None)

    assert [row["id"] for row in filtered] == ["dev-a", "holdout-a"]


def test_golden_expected_label_uses_sub_intent_when_needed():
    from evaluate_golden import expected_label_from_golden

    assert expected_label_from_golden({"expected_intent": "concept_definition"}) == "CONCEPT_DEFINITION"
    assert (
        expected_label_from_golden(
            {"expected_intent": "concept_definition", "expected_sub_intent": "comparison"}
        )
        == "COMPARISON"
    )
    assert (
        expected_label_from_golden(
            {"expected_intent": "concept_definition", "expected_sub_intent": "practical"}
        )
        == "PRACTICAL_USAGE"
    )
    assert (
        expected_label_from_golden(
            {"expected_intent": "wrong_answer_explanation", "expected_sub_intent": "explanation"}
        )
        == "WRONG_ANSWER_REASON"
    )
    assert (
        expected_label_from_golden({"expected_intent": "follow_up", "expected_sub_intent": "follow_up"})
        == "FOLLOW_UP"
    )


def test_ollama_embed_uses_cache_for_same_model_and_text(monkeypatch, tmp_path):
    import classifiers

    calls = []

    def fake_fetch(model, text):
        calls.append((model, text))
        return [1.0, 2.0, 3.0]

    monkeypatch.setattr(classifiers, "_EMBED_CACHE_PATH", tmp_path / "cache.json")
    monkeypatch.setattr(classifiers, "_EMBED_CACHE", None)
    monkeypatch.setattr(classifiers, "_fetch_ollama_embedding", fake_fetch)

    first = classifiers._ollama_embed("REST API가 뭐야?")
    second = classifiers._ollama_embed("REST API가 뭐야?")

    assert first == [1.0, 2.0, 3.0]
    assert second == [1.0, 2.0, 3.0]
    assert calls == [("bge-m3", "REST API가 뭐야?")]


def test_fetch_ollama_embedding_reads_embedding_response(monkeypatch):
    import urllib.request

    import classifiers

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, traceback):
            return False

        def read(self):
            return b'{"embedding":[0.5,0.25]}'

    def fake_urlopen(request, timeout):
        assert request.full_url == "http://ollama.test/api/embeddings"
        assert timeout == 60
        return FakeResponse()

    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.test")
    monkeypatch.setattr(urllib.request, "urlopen", fake_urlopen)

    assert classifiers._fetch_ollama_embedding("bge-m3", "hello") == [0.5, 0.25]


def test_pattern_rows_include_definition_question_archetypes():
    import augment

    rows = augment.build_pattern_rows()
    definition_archetypes = {
        row["pattern_archetype"]
        for row in rows
        if row["expected_intent"] == "CONCEPT_DEFINITION"
    }

    assert {
        "pure_definition",
        "purpose",
        "cause",
        "mechanism",
        "relationship",
        "beginner_vague",
    } <= definition_archetypes


def test_pattern_rows_do_not_copy_known_golden_questions_or_concepts():
    import augment

    rows = augment.build_pattern_rows()
    questions = {row["question"] for row in rows}

    assert "N+1 문제는 뭐야?" not in questions
    assert "REST API가 뭐야?" not in questions
    assert all("N+1" not in row["question"] for row in rows)
    assert all("REST API" not in row["question"] for row in rows)


def test_augmented_rows_avoid_known_golden_concepts():
    import json

    import augment

    seeds = [
        json.loads(line)
        for line in augment.SEEDS_PATH.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    rows = []
    for seed in seeds:
        rows.extend(augment.build_rows_for_seed(seed))
    rows.extend(augment.build_pattern_rows())

    assert all("N+1" not in row["question"] for row in rows)
    assert all("REST API" not in row["question"] for row in rows)


def test_pattern_rows_have_balanced_splits():
    import augment

    rows = augment.build_pattern_rows()

    assert {row["split"] for row in rows} == {"dev", "holdout"}


def test_intent_golden_is_balanced_across_10_classes():
    import json
    from collections import Counter

    from classifiers import CLASSES

    path = INTENT_POC_ROOT / "intent_golden.jsonl"
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    counts = Counter(row["expected_intent"] for row in rows)

    assert set(counts) == set(CLASSES)
    assert all(counts[cls] >= 8 for cls in CLASSES)
    assert max(counts.values()) - min(counts.values()) <= 1


def test_intent_golden_does_not_copy_operational_golden_questions():
    import json

    operational_path = AI_ROOT / "evals" / "golden_dataset.jsonl"
    intent_path = INTENT_POC_ROOT / "intent_golden.jsonl"
    operational_questions = {
        json.loads(line)["question"]
        for line in operational_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    intent_questions = {
        json.loads(line)["question"]
        for line in intent_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }

    assert operational_questions.isdisjoint(intent_questions)

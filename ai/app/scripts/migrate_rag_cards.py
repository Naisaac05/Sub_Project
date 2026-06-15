from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import shutil
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol
from types import SimpleNamespace

sys.path.append(str(Path(__file__).resolve().parents[2]))

from app.rag.retriever import LexicalRetrieverAdapter, RetrievedContext
from app.schemas.rag_card import (
    AnswerReasonPayload,
    CardStatus,
    ConceptDefinitionPayload,
    PayloadStatus,
    RagCard,
    RagPayloads,
    RagRetrieval,
    RagReview,
    WrongAnswerOption,
    WrongAnswerReasonPayload,
)


ROOT = Path(__file__).resolve().parents[3]
SQL_PATH = ROOT / "backend" / "data" / "devmatch-data-only.sql"
PRODUCTION_ROOT = ROOT / "ai" / "app" / "knowledge" / "concepts"
OUT_ROOT = ROOT / "ai" / "app" / "knowledge" / "concepts_v2"
BROAD_TERMS = {"api", "key", "cache", "loading", "latency"}
GENERATED_INTENTS = ("CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON")
TEST_CATEGORY_MAP = {
    1: "java", 2: "java", 3: "java",
    4: "spring", 5: "spring", 6: "spring",
    7: "frontend", 8: "frontend", 9: "frontend",
    10: "python", 11: "python", 12: "python",
    13: "algorithm", 14: "algorithm", 15: "algorithm",
}
TERM_ALIASES = {
    "authenticationprincipal": ("authentication principal", "spring security principal"),
    "react-key": ("react key", "reconciliation key", "react reconciliation key"),
    "cache": ("caching", "cache management"),
    "equals": ("equality", "object equality"),
    "hashcode": ("hash code", "object hash"),
    "useeffect": ("use effect", "react effect"),
    "primarykey": ("primary key", "database identifier"),
    "loop-control": ("loop control", "java loop statement"),
    "access-modifier": ("access modifier", "java access control"),
    "g1-gc": ("g1 gc", "garbage first collector"),
    "react-server-components": ("react server components", "rsc"),
    "react-project-tool": ("react project tool", "react project setup"),
    "conditional-rendering": ("conditional rendering", "react conditional rendering"),
    "controlled-uncontrolled-components": ("controlled uncontrolled components", "react form control"),
    "dependency-injection": ("dependency injection", "spring di"),
    "spring-bean-scope": ("spring bean scope", "singleton scope"),
    "int-variable": ("integer variable", "java int declaration"),
    "array-length": ("array length", "java array length"),
    "final-keyword": ("final keyword", "java constant declaration"),
    "extends-keyword": ("extends keyword", "java inheritance"),
    "negative-indexing": ("negative indexing", "python list last item"),
    "dictionary": ("python dictionary", "dict creation"),
    "multiline-string": ("multiline string", "python triple quoted string"),
    "with-statement": ("with statement", "python context manager"),
    "list-comprehension": ("list comprehension", "python comprehension"),
    "function-definition": ("function definition", "python def"),
    "breadth-first-search": ("breadth first search", "bfs traversal", "bfs queue"),
    "jsx-expression": ("jsx expression", "javascript expression in jsx", "jsx braces"),
    "functional-component": ("react functional component", "function component"),
    "functional-interface": ("java functional interface", "single abstract method interface"),
}
GENERAL_TERM_STOPWORDS = {
    "에서", "반복문이", "접근", "특징으로", "다음", "방법은", "올바른", "것은",
    "사용하는", "역할은", "용도는", "프로젝트를", "함수", "server", "concept",
}
MERGE_GENERIC_TOKENS = {
    "java", "spring", "react", "frontend", "python", "algorithm",
    "concept", "method", "function", "class", "data",
}
CONCEPT_PATTERNS = (
    ("frontend", ("리스트를", "렌더링", "속성"), "react-key"),
    ("frontend", ("재조정", "key"), "react-key"),
    ("frontend", ("server components",), "react-server-components"),
    ("frontend", ("rsc",), "react-server-components"),
    ("frontend", ("프로젝트를", "생성", "도구"), "react-project-tool"),
    ("frontend", ("조건부 렌더링",), "conditional-rendering"),
    ("frontend", ("폼 입력", "두 가지 방식"), "controlled-uncontrolled-components"),
    ("frontend", ("jsx", "javascript 표현식"), "jsx-expression"),
    ("frontend", ("함수형 컴포넌트",), "functional-component"),
    ("java", ("문자열", "비교"), "equals"),
    ("java", ("반복문",), "loop-control"),
    ("java", ("접근 제어자",), "access-modifier"),
    ("java", ("g1 gc",), "g1-gc"),
    ("java", ("정수형 변수",), "int-variable"),
    ("java", ("배열의 길이",), "array-length"),
    ("java", ("상수를 선언",), "final-keyword"),
    ("java", ("클래스를 상속",), "extends-keyword"),
    ("java", ("함수형 인터페이스",), "functional-interface"),
    ("spring", ("의존성 주입",), "dependency-injection"),
    ("spring", ("spring bean", "기본 스코프"), "spring-bean-scope"),
    ("spring", ("내장 서버",), "embedded-tomcat"),
    ("python", ("리스트의 마지막 요소",), "negative-indexing"),
    ("python", ("딕셔너리를 생성",), "dictionary"),
    ("python", ("여러 줄 문자열",), "multiline-string"),
    ("python", ("파일을 안전하게",), "with-statement"),
    ("python", ("리스트 컴프리헨션",), "list-comprehension"),
    ("python", ("함수 정의",), "function-definition"),
    ("algorithm", ("bfs", "자료구조"), "breadth-first-search"),
)


class Embedder(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...


class FakeEmbedder:
    """Deterministic local embedder for dry-run clustering and smoke evaluation."""

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for text in texts:
            vector = [0.0] * 64
            for token in tokenize(text):
                digest = hashlib.sha256(token.encode("utf-8")).digest()
                vector[int.from_bytes(digest[:2], "big") % len(vector)] += 1.0
            norm = math.sqrt(sum(value * value for value in vector))
            vectors.append([value / norm for value in vector] if norm else vector)
        return vectors


@dataclass(frozen=True)
class Question:
    id: int
    content: str
    options: list[str]
    correct_answer: int
    test_id: int

    @property
    def category(self) -> str:
        return TEST_CATEGORY_MAP.get(self.test_id, "unknown")

    @property
    def correct_text(self) -> str:
        if 0 <= self.correct_answer < len(self.options):
            return self.options[self.correct_answer]
        return ""


@dataclass
class ConceptDraft:
    question: Question
    category: str
    term: str
    aliases: list[str]
    boost_keywords: list[str]
    embedding: list[float] = field(default_factory=list)


@dataclass
class GenerationStats:
    question_count: int = 0
    card_count: int = 0
    merge_count: int = 0
    broad_term_specializations: int = 0
    payload_count: int = 0
    card_id_collisions: int = 0


@dataclass(frozen=True)
class RetrievalMetrics:
    exact_hit1: float
    exact_hit3: float
    exact_hit5: float
    loo_candidate_rate: float
    loo_average_score: float
    loo_same_category_rate: float


def tokenize(text: str) -> list[str]:
    return re.findall(r"[a-z0-9+#@.]+|[가-힣]{2,}", text.lower())


def normalize_term(value: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]+", "", value.lower())


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return slug[:80].rstrip("-") or "concept"


def is_e2e_failure_question(content: str) -> bool:
    normalized = content.lower().replace(" ", "")
    return "e2e" in normalized and any(marker in normalized for marker in ("실패", "오류", "에러", "fail"))


def split_sql_values(value_text: str) -> list[str]:
    values: list[str] = []
    current: list[str] = []
    quoted = False
    escaped = False
    for char in value_text:
        if escaped:
            current.append(char)
            escaped = False
            continue
        if char == "\\":
            current.append(char)
            escaped = True
            continue
        if char == "'":
            quoted = not quoted
            current.append(char)
            continue
        if char == "," and not quoted:
            values.append("".join(current).strip())
            current = []
            continue
        current.append(char)
    values.append("".join(current).strip())
    return values


def sql_string(value: str) -> str:
    value = value.strip()
    if value.startswith("'") and value.endswith("'"):
        value = value[1:-1]
    return (
        value.replace('\\"', '"')
        .replace("\\'", "'")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
    )


def extract_questions(path: Path = SQL_PATH, limit: int | None = None) -> list[Question]:
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(r"INSERT INTO `questions` \([^)]+\) VALUES \((.*?)\);", re.DOTALL)
    questions: list[Question] = []
    for match in pattern.finditer(text):
        values = split_sql_values(match.group(1))
        if len(values) != 8:
            continue
        content = sql_string(values[1])
        if is_e2e_failure_question(content):
            continue
        try:
            options = json.loads(sql_string(values[4]))
            question = Question(
                id=int(values[0]),
                content=content,
                options=[str(option) for option in options],
                correct_answer=int(values[2]),
                test_id=int(values[7]),
            )
        except (ValueError, TypeError, json.JSONDecodeError):
            continue
        questions.append(question)
        if limit is not None and len(questions) >= limit:
            break
    return questions


def extract_term(content: str, category: str) -> str:
    compact = normalize_term(content)
    candidates = [
        token.lower().replace("@", "")
        for token in re.findall(r"@?[A-Za-z][A-Za-z0-9+.#_-]*", content)
        if len(token.replace("@", "")) >= 3
    ]
    ignored = {"java", "spring", "react", "python", "next", "올바른", "역할"}
    candidates = [candidate for candidate in candidates if candidate not in ignored]
    if candidates:
        return normalize_term(candidates[0])
    korean = [token for token in re.findall(r"[가-힣]{2,}", content) if token not in {"다음", "방법", "설명", "역할"}]
    return normalize_term(korean[0] if korean else f"{category} concept")


def extract_concept_term(question: Question) -> str:
    lowered = question.content.lower()
    for category, markers, term in CONCEPT_PATTERNS:
        if question.category == category and all(marker in lowered for marker in markers):
            return term

    extracted = extract_term(question.content, question.category)
    if extracted not in GENERAL_TERM_STOPWORDS and extracted not in BROAD_TERMS:
        return extracted

    answer_candidates = [
        normalize_term(token.replace("@", ""))
        for token in re.findall(r"@?[A-Za-z][A-Za-z0-9+.#_-]*", question.correct_text)
        if len(token.replace("@", "")) >= 3
    ]
    answer_candidates = [
        candidate for candidate in answer_candidates
        if candidate and candidate not in GENERAL_TERM_STOPWORDS and candidate not in BROAD_TERMS
    ]
    if answer_candidates:
        return answer_candidates[0]
    return f"{question.category}-question-{question.id}"


def build_draft(question: Question) -> ConceptDraft:
    term = extract_concept_term(question)
    aliases = list(TERM_ALIASES.get(term, ()))
    if term not in aliases:
        aliases.insert(0, term)
    spaced_term = term.replace("-", " ")
    aliases = unique([*aliases, spaced_term, f"{question.category} {spaced_term}"])
    keywords = unique([term, question.category, *aliases, *tokenize(question.correct_text)])[:7]
    while len(keywords) < 3:
        keywords.append(f"{question.category}-{len(keywords) + 1}")
    return ConceptDraft(question, question.category, term, aliases[:5], keywords)


def unique(values: list[str]) -> list[str]:
    result: list[str] = []
    for value in values:
        cleaned = value.strip()
        if cleaned and cleaned not in result:
            result.append(cleaned)
    return result


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right:
        return 0.0
    return sum(a * b for a, b in zip(left, right))


def should_merge(left: ConceptDraft, right: ConceptDraft, threshold: float = 0.88) -> bool:
    if left.category != right.category:
        return False
    if normalize_term(left.term) == normalize_term(right.term):
        return True
    shared_aliases = set(left.aliases) & set(right.aliases)
    if any(len(set(tokenize(alias)) - MERGE_GENERIC_TOKENS) >= 2 for alias in shared_aliases):
        return True
    left_tokens = set(tokenize(" ".join([left.term, *left.aliases, *left.boost_keywords]))) - MERGE_GENERIC_TOKENS
    right_tokens = set(tokenize(" ".join([right.term, *right.aliases, *right.boost_keywords]))) - MERGE_GENERIC_TOKENS
    return len(left_tokens & right_tokens) >= 2 and cosine_similarity(left.embedding, right.embedding) >= threshold


def cluster_drafts(
    drafts: list[ConceptDraft],
    embedder: Embedder | None = None,
    threshold: float = 0.88,
) -> tuple[list[list[ConceptDraft]], int]:
    selected_embedder = embedder or FakeEmbedder()
    embeddings = selected_embedder.embed([
        " ".join([draft.term, *draft.aliases, draft.category, *draft.boost_keywords])
        for draft in drafts
    ])
    for draft, embedding in zip(drafts, embeddings, strict=True):
        draft.embedding = embedding
    clusters: list[list[ConceptDraft]] = []
    merges = 0
    for draft in drafts:
        cluster = next((items for items in clusters if should_merge(items[0], draft, threshold)), None)
        if cluster is None:
            clusters.append([draft])
        else:
            cluster.append(draft)
            merges += 1
    return clusters, merges


def specialize_term(category: str, term: str) -> tuple[str, bool]:
    if normalize_term(term) not in BROAD_TERMS:
        return term, False
    return f"{category}-{term}", True


def create_cards(clusters: list[list[ConceptDraft]]) -> list[RagCard]:
    cards: list[RagCard] = []
    used_ids: set[str] = set()
    now = datetime.now(timezone.utc)
    for cluster in clusters:
        base = cluster[0]
        term, _ = specialize_term(base.category, base.term)
        aliases = unique([alias for draft in cluster for alias in draft.aliases])[:5]
        keywords = unique([keyword for draft in cluster for keyword in draft.boost_keywords])[:7]
        while len(keywords) < 3:
            keywords.append(f"{base.category}-{len(keywords) + 1}")
        card_id_base = slugify(f"{base.category}-{term}")
        card_id = card_id_base
        suffix = 2
        while card_id in used_ids:
            suffix_text = f"-{suffix}"
            card_id = f"{card_id_base[:80 - len(suffix_text)]}{suffix_text}"
            suffix += 1
        used_ids.add(card_id)
        source_ids = [f"{draft.category}:{draft.question.id}" for draft in cluster]
        wrong_options: dict[str, WrongAnswerOption] = {}
        for draft in cluster:
            for index, option in enumerate(draft.question.options):
                if index != draft.question.correct_answer:
                    wrong_options[f"q{draft.question.id}_option_{index}"] = WrongAnswerOption(
                        text=option,
                        reason=f"{term} 개념의 기준과 일치하지 않는 선택지입니다.",
                    )
        payloads = RagPayloads(
            CONCEPT_DEFINITION=ConceptDefinitionPayload(
                content=f"{term}의 핵심 개념과 책임을 설명합니다.",
                examples=[],
            ),
            ANSWER_REASON=AnswerReasonPayload(
                why_correct=f"정답은 {term}의 핵심 특성을 충족합니다.",
                key_points=keywords[:3],
            ),
            WRONG_ANSWER_REASON=WrongAnswerReasonPayload(
                common_mistakes=[],
                per_option=wrong_options,
            ),
        )
        review = RagReview(
            card_status=CardStatus.DRAFT,
            payload_status={intent: PayloadStatus.DRAFT for intent in GENERATED_INTENTS},
        )
        embedding_text = " ".join(unique([term, *aliases[:3], base.category, *keywords]))
        cards.append(RagCard(
            card_id=card_id,
            category=base.category,
            term=term,
            aliases=aliases,
            source_question_ids=source_ids,
            retrieval=RagRetrieval(
                embedding_text=embedding_text,
                boost_keywords=keywords,
                intent_types=list(GENERATED_INTENTS),
            ),
            payloads=payloads,
            review=review,
            related_card_ids=[],
            tags=[],
            created_at=now,
            updated_at=now,
        ))
    return cards


def lint_cards(cards: list[RagCard]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for card in cards:
        prefix = card.card_id
        if card.card_id in seen:
            errors.append(f"{prefix}: duplicate card_id")
        seen.add(card.card_id)
        if not re.fullmatch(r"[a-z0-9-]{1,80}", card.card_id):
            errors.append(f"{prefix}: invalid card_id")
        if card.review.card_status not in {CardStatus.DRAFT, CardStatus.APPROVED}:
            errors.append(f"{prefix}: card_status must be draft or approved")
        if set(card.review.payload_status) != set(GENERATED_INTENTS):
            errors.append(f"{prefix}: invalid generated payload intents")
        expected_payload_status = (
            PayloadStatus.APPROVED
            if card.review.card_status == CardStatus.APPROVED
            else PayloadStatus.DRAFT
        )
        if any(status != expected_payload_status for status in card.review.payload_status.values()):
            errors.append(f"{prefix}: payload_status must match card_status")
        if not 3 <= len(card.retrieval.boost_keywords) <= 7:
            errors.append(f"{prefix}: boost_keywords must contain 3-7 items")
        expected_embedding = " ".join(unique([
            card.term, *card.aliases[:3], card.category, *card.retrieval.boost_keywords
        ]))
        if card.retrieval.embedding_text != expected_embedding:
            errors.append(f"{prefix}: embedding_text must be compact retrieval fields only")
        if card.created_at.tzinfo is None or card.updated_at.tzinfo is None:
            errors.append(f"{prefix}: timestamps must include timezone")
    return errors


def evaluate_retrieval(
    questions: list[Question],
    cards: list[RagCard],
    retriever,
) -> RetrievalMetrics:
    card_by_id = {card.card_id: card for card in cards}
    exact_hits = {1: 0, 3: 0, 5: 0}
    loo_candidates = 0
    loo_score = 0.0
    loo_category = 0
    for question in questions:
        source_id = f"{question.category}:{question.id}"
        results: list[RetrievedContext] = retriever.retrieve(question.content, limit=50)
        for k in exact_hits:
            if any(source_id in card_by_id[result.concept_id].source_question_ids for result in results[:k] if result.concept_id in card_by_id):
                exact_hits[k] += 1
        alternatives = [
            result for result in results
            if result.concept_id in card_by_id
            and source_id not in card_by_id[result.concept_id].source_question_ids
        ]
        if alternatives:
            candidate = alternatives[0]
            loo_candidates += 1
            loo_score += candidate.score
            if card_by_id[candidate.concept_id].category == question.category:
                loo_category += 1
    total = len(questions) or 1
    candidate_total = loo_candidates or 1
    return RetrievalMetrics(
        exact_hit1=exact_hits[1] / total,
        exact_hit3=exact_hits[3] / total,
        exact_hit5=exact_hits[5] / total,
        loo_candidate_rate=loo_candidates / total,
        loo_average_score=loo_score / candidate_total if loo_candidates else 0.0,
        loo_same_category_rate=loo_category / candidate_total if loo_candidates else 0.0,
    )


def production_search_text(card: RagCard) -> str:
    return "\n".join(unique([
        card.retrieval.embedding_text,
        *card.aliases,
        *card.retrieval.boost_keywords,
        card.term,
        card.category,
    ]))


def build_evaluation_retriever(cards: list[RagCard], mode: str) -> LexicalRetrieverAdapter:
    if mode == "content_mode":
        return LexicalRetrieverAdapter(card_loader=lambda: cards)
    if mode != "production_mode":
        raise ValueError(f"unknown evaluation mode: {mode}")
    evaluation_cards = [
        SimpleNamespace(
            concept_id=card.concept_id,
            title=card.title,
            term=card.term,
            aliases=card.aliases,
            retrieval=card.retrieval,
            searchable_text=production_search_text(card),
            payloads=RagPayloads(),
            metadata=card.metadata,
        )
        for card in cards
    ]
    return LexicalRetrieverAdapter(card_loader=lambda: evaluation_cards)


def evaluate_retrieval_modes(
    questions: list[Question],
    cards: list[RagCard],
) -> dict[str, RetrievalMetrics]:
    return {
        mode: evaluate_retrieval(questions, cards, build_evaluation_retriever(cards, mode))
        for mode in ("production_mode", "content_mode")
    }


def payload_patch_acceptance(
    production_before: RetrievalMetrics,
    content_before: RetrievalMetrics,
    production_after: RetrievalMetrics,
    content_after: RetrievalMetrics,
) -> tuple[bool, list[str]]:
    reasons = []
    if production_after.exact_hit1 != production_before.exact_hit1:
        reasons.append("production_hit1_changed")
    if production_after.loo_candidate_rate != production_before.loo_candidate_rate:
        reasons.append("production_loo_changed")
    if (
        production_after.exact_hit1 != production_before.exact_hit1
        or production_after.exact_hit3 != production_before.exact_hit3
        or production_after.exact_hit5 != production_before.exact_hit5
    ):
        reasons.append("production_exact_changed")
    if content_before.exact_hit1 - content_after.exact_hit1 > 0.02:
        reasons.append("content_hit1_decreased_over_2_percent")
    return not reasons, reasons


def generation_stats(questions: list[Question], clusters: list[list[ConceptDraft]], cards: list[RagCard], merges: int) -> GenerationStats:
    broad_count = sum(specialize_term(cluster[0].category, cluster[0].term)[1] for cluster in clusters)
    base_ids = [slugify(f"{cluster[0].category}-{specialize_term(cluster[0].category, cluster[0].term)[0]}") for cluster in clusters]
    return GenerationStats(
        question_count=len(questions),
        card_count=len(cards),
        merge_count=merges,
        broad_term_specializations=broad_count,
        payload_count=len(cards) * len(GENERATED_INTENTS),
        card_id_collisions=len(base_ids) - len(set(base_ids)),
    )


def validate_existing_v2() -> list[str]:
    errors: list[str] = []
    for path in sorted(OUT_ROOT.rglob("*.json")) if OUT_ROOT.exists() else []:
        try:
            card = RagCard.model_validate_json(path.read_text(encoding="utf-8"))
            errors.extend(f"{path}: {error}" for error in lint_cards([card]))
        except Exception as exc:
            errors.append(f"{path}: {exc}")
    return errors


def write_cards_atomically(cards: list[RagCard], output_root: Path = OUT_ROOT) -> int:
    resolved_output = output_root.resolve()
    if resolved_output == PRODUCTION_ROOT.resolve() or resolved_output.name != "concepts_v2":
        raise ValueError("write target must be an isolated concepts_v2 directory")
    errors = lint_cards(cards)
    if errors:
        raise ValueError(f"validation failed: {'; '.join(errors)}")

    output_root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="concepts_v2_staging_", dir=output_root.parent) as temp:
        staging = Path(temp) / "concepts_v2"
        for card in cards:
            category_root = staging / card.category
            category_root.mkdir(parents=True, exist_ok=True)
            path = category_root / f"{card.card_id}.json"
            path.write_text(card.model_dump_json(indent=2, by_alias=True), encoding="utf-8")

        staged_cards: list[RagCard] = []
        invalid: list[str] = []
        for path in sorted(staging.rglob("*.json")):
            try:
                text = path.read_text(encoding="utf-8")
                if "\ufffd" in text:
                    raise ValueError("replacement character found")
                staged_cards.append(RagCard.model_validate_json(text))
            except Exception as exc:
                invalid.append(f"{path}: {exc}")
        invalid.extend(lint_cards(staged_cards))
        if invalid or len(staged_cards) != len(cards):
            raise ValueError(f"validation failed: {'; '.join(invalid)}")

        previous = output_root.parent / f"{output_root.name}.rollback"
        if previous.exists():
            shutil.rmtree(previous)
        try:
            if output_root.exists():
                output_root.rename(previous)
            staging.rename(output_root)
            if previous.exists():
                shutil.rmtree(previous)
        except Exception:
            if output_root.exists():
                shutil.rmtree(output_root)
            if previous.exists():
                previous.rename(output_root)
            raise
    return len(cards)


def parse_args(argv: list[str] | None = None):
    parser = argparse.ArgumentParser(description="Dry-run-only RAG card v2 migration")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--write", action="store_true", help="Write validated draft cards to concepts_v2")
    args = parser.parse_args(argv)
    return args


def percent(value: float) -> str:
    return f"{value * 100:.1f}%"


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.validate_only:
        errors = validate_existing_v2()
        print(f"v2 validation root: {OUT_ROOT}")
        print(f"lint errors: {len(errors)}")
        for error in errors:
            print(f" - {error}")
        return 1 if errors else 0

    questions = extract_questions(limit=args.limit)
    drafts = [build_draft(question) for question in questions]
    clusters, merges = cluster_drafts(drafts)
    cards = create_cards(clusters)
    stats = generation_stats(questions, clusters, cards, merges)
    lint_errors = lint_cards(cards)
    retriever = LexicalRetrieverAdapter(card_loader=lambda: cards)
    metrics = evaluate_retrieval(questions, cards, retriever)

    print("RAG card v2 isolated migration dry-run")
    print(f"output root (not written): {OUT_ROOT}")
    print(f"production root (untouched): {PRODUCTION_ROOT}")
    print(f"questions: {stats.question_count}")
    print(f"cards: {stats.card_count}")
    print(f"merges: {stats.merge_count}")
    print(f"broad term specializations: {stats.broad_term_specializations}")
    print(f"payloads: {stats.payload_count}")
    print(f"card_id collisions: {stats.card_id_collisions}")
    print(f"lint errors: {len(lint_errors)}")
    print(f"Exact Match Hit@1 (Data Leakage Baseline): {percent(metrics.exact_hit1)}")
    print(f"Exact Match Hit@3 (Data Leakage Baseline): {percent(metrics.exact_hit3)}")
    print(f"Exact Match Hit@5 (Data Leakage Baseline): {percent(metrics.exact_hit5)}")
    print(f"LOO alternative candidate rate: {percent(metrics.loo_candidate_rate)}")
    print(f"LOO average score: {metrics.loo_average_score:.4f}")
    print(f"LOO same category rate: {percent(metrics.loo_same_category_rate)}")
    if args.write:
        written = write_cards_atomically(cards)
        print(f"written draft cards: {written}")
    else:
        print("No files were written.")
    return 1 if lint_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

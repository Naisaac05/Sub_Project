from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re


@dataclass(frozen=True)
class SourceQuestion:
    category: str
    order: int
    question: str
    options: list[str]
    source_path: str = ""

    @property
    def source_question_id(self) -> str:
        category = self.category or "unknown"
        return f"{category}:{self.order}"


TECHNICAL_TERMS: dict[str, tuple[str, ...]] = {
    "API": ("api", "rest api"),
    "REST API": ("rest api", "rest"),
    "HTTP status code": ("http 상태 코드", "http status", "status code", "201 created", "200 ok", "204 no content", "404 not found"),
    "DTO": ("dto",),
    "JPA 엔티티": ("jpa 엔티티", "엔티티", "entity"),
    "JPA N+1": ("n+1",),
    "fetch join": ("fetch join",),
    "EntityGraph": ("entitygraph", "entity graph"),
    "트랜잭션": ("트랜잭션", "transaction", "@transactional"),
    "트랜잭션 격리": ("트랜잭션 격리", "isolation"),
    "Spring Security": ("spring security",),
    "@AuthenticationPrincipal": ("@authenticationprincipal",),
    "@ControllerAdvice": ("@controlleradvice", "controlleradvice"),
    "HashMap": ("hashmap",),
    "HashSet": ("hashset",),
    "equals/hashCode": ("equals", "hashcode", "hash code"),
    "페이지네이션": ("페이지네이션", "pagination"),
    "분산 락": ("분산 락", "distributed lock"),
    "멱등성": ("멱등성", "idempotent", "idempotency"),
    "React state": ("react state", "state"),
    "setter": ("setter",),
    "Next.js App Router": ("next.js app router", "app router"),
    "use client": ("use client",),
    "React key": ("react key", "key"),
    "useEffect": ("useeffect", "use effect"),
    "의존성 배열": ("의존성 배열", "dependency array"),
    "로딩 상태": ("로딩", "loading"),
    "에러 상태": ("에러", "error state"),
    "빈 상태": ("빈 상태", "empty state"),
    "flex/grid": ("flex", "grid"),
    "breakpoint": ("breakpoint", "브레이크포인트"),
    "전역 상태": ("전역 상태", "global state"),
    "동적 라우트": ("동적 라우트", "dynamic route"),
    "params": ("params",),
    "aria-label": ("aria-label", "arialabel"),
    "리스트 컴프리헨션": ("리스트 컴프리헨션", "list comprehension"),
    "FastAPI": ("fastapi",),
    "Pydantic": ("pydantic",),
    "Django ORM": ("django orm",),
    "select_related": ("select_related", "select related"),
    "async/await": ("async/await", "async await"),
    "블로킹 I/O": ("블로킹 i/o", "blocking i/o", "blocking io"),
    "환경변수": ("환경변수", "environment variable"),
    "ORM": ("orm",),
    "raw SQL": ("raw sql",),
    "CSV": ("csv",),
    "캐싱": ("캐싱", "caching"),
    "비동기 작업 큐": ("비동기 작업 큐", "job queue", "task queue"),
    "타임아웃": ("타임아웃", "timeout"),
    "Node.js 이벤트 루프": ("node.js 이벤트 루프", "event loop"),
    "TypeScript DTO": ("typescript", "dto 타입"),
    "NestJS": ("nestjs", "nest"),
    "관심사 분리": ("관심사", "separation of concerns"),
    "tick rate": ("tick rate",),
    "UDP": ("udp",),
    "authoritative server": ("authoritative server", "서버 권위"),
    "매치메이킹": ("매치메이킹", "matchmaking"),
    "동시성": ("동시성", "concurrency"),
    "leaderboard": ("leaderboard", "리더보드"),
    "latency": ("latency", "지연"),
    "Kafka partition": ("kafka", "partition", "파티션"),
    "consumer group": ("consumer group",),
    "offset commit": ("offset commit", "offset"),
    "at-least-once": ("at-least-once", "at least once"),
    "Kafka key": ("kafka key", "key"),
    "consumer lag": ("consumer lag", "lag"),
    "DLQ": ("dlq",),
    "Kafka acks": ("acks",),
    "rebalancing": ("rebalancing", "리밸런싱"),
    "Redis SET NX PX": ("set nx px", "redis"),
    "TTL": ("ttl",),
    "owner token": ("owner token",),
    "pessimistic lock": ("pessimistic lock", "비관적 락"),
    "optimistic lock": ("optimistic lock", "낙관적 락"),
    "unique key": ("unique key",),
    "원자적 update": ("원자적 update", "atomic update"),
}

STOP_TERMS = {"key", "state", "error"}


def parse_course_skill_questions(text: str, source_path: str = "") -> list[SourceQuestion]:
    questions: list[SourceQuestion] = []
    category = ""
    index = 0

    while index < len(text):
        seed_course_at = text.find("seedCourse(", index)
        upsert_at = text.find("upsertDiagnosticTest(", index)
        sq_at = text.find("sq(", index)
        candidates = [pos for pos in (seed_course_at, upsert_at, sq_at) if pos >= 0]
        if not candidates:
            break

        next_at = min(candidates)
        if next_at == seed_course_at:
            call = _balanced_call_at(text, seed_course_at)
            literals = _java_string_literals(call)
            if literals:
                category = literals[0]
                questions.extend(_parse_seed_questions_in_block(call, category, source_path))
            index = seed_course_at + max(len(call), 1)
            continue

        if next_at == upsert_at:
            call = _balanced_call_at(text, upsert_at)
            literals = _java_string_literals(call)
            if literals:
                category = literals[0]
            index = upsert_at + max(len(call), 1)
            continue

        call = _balanced_call_at(text, sq_at)
        question = _parse_seed_question_call(call, category, source_path)
        if question:
            questions.append(question)
        index = sq_at + max(len(call), 1)

    return questions


def _parse_seed_questions_in_block(block: str, category: str, source_path: str) -> list[SourceQuestion]:
    questions: list[SourceQuestion] = []
    index = 0
    while True:
        sq_at = block.find("sq(", index)
        if sq_at < 0:
            break
        call = _balanced_call_at(block, sq_at)
        question = _parse_seed_question_call(call, category, source_path)
        if question:
            questions.append(question)
        index = sq_at + max(len(call), 1)
    return questions


def extract_candidate_concepts(questions: list[SourceQuestion]) -> list[dict[str, object]]:
    merged: dict[tuple[str, str], dict[str, object]] = {}
    for question in questions:
        haystack = _normalize(" ".join([question.question, *question.options]))
        for term, aliases in TECHNICAL_TERMS.items():
            normalized_aliases = {_normalize(alias) for alias in aliases}
            if not any(alias and alias in haystack for alias in normalized_aliases):
                continue
            if _normalize(term) in STOP_TERMS:
                continue

            key = (term, question.category)
            candidate = merged.setdefault(
                key,
                {
                    "term": term,
                    "aliases": sorted(set(aliases)),
                    "definition": "",
                    "definition_status": "pending_human_review",
                    "category": question.category,
                    "source": "CourseSkillTestInitializer",
                    "source_path": question.source_path,
                    "source_question_ids": [],
                    "approved": False,
                },
            )
            source_ids = candidate["source_question_ids"]
            if isinstance(source_ids, list) and question.source_question_id not in source_ids:
                source_ids.append(question.source_question_id)

    rows = list(merged.values())
    for row in rows:
        row["source_question_ids"] = sorted(row["source_question_ids"])
    return sorted(rows, key=lambda row: (str(row["term"]).lower(), str(row["category"])))


def write_candidate_jsonl(candidates: list[dict[str, object]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    rows = sorted(candidates, key=lambda row: (str(row["term"]).lower(), str(row["category"])))
    with output_path.open("w", encoding="utf-8", newline="\n") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            file.write("\n")


def _parse_seed_question_call(call: str, category: str, source_path: str) -> SourceQuestion | None:
    order_match = re.search(r"sq\(\s*(\d+)\s*,", call)
    literals = _java_string_literals(call)
    if not order_match or not literals:
        return None
    return SourceQuestion(
        category=category,
        order=int(order_match.group(1)),
        question=literals[0],
        options=literals[1:],
        source_path=source_path,
    )


def _balanced_call_at(text: str, start: int) -> str:
    depth = 0
    in_string = False
    escaped = False
    seen_open = False

    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
            seen_open = True
        elif char == ")":
            depth -= 1
            if seen_open and depth == 0:
                return text[start : index + 1]

    return text[start:]


def _java_string_literals(text: str) -> list[str]:
    literals: list[str] = []
    for match in re.finditer(r'"((?:\\.|[^"\\])*)"', text, flags=re.DOTALL):
        value = match.group(1)
        value = value.replace('\\"', '"').replace("\\n", "\n").replace("\\t", "\t")
        literals.append(value)
    return literals


def _normalize(text: str) -> str:
    return re.sub(r"[\s?!?.。,/_-]+", "", text.lower())

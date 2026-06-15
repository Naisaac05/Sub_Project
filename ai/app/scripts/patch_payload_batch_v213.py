from __future__ import annotations

import copy
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import PayloadStatus, RagCard
from app.scripts.migrate_rag_cards import (
    evaluate_retrieval_modes,
    extract_questions,
    payload_patch_acceptance,
)


ROOT = Path(__file__).resolve().parents[2]
CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
BACKUP_ROOT = ROOT / "app" / "knowledge" / "concepts_v2_backups" / "payload-batch-v2-1-3-first10-20260613"
REPORT = ROOT / "reports" / "payload_batch_v2_1_3_first10_2026-06-13.json"
LOCKED = ("card_id", "term", "category", "source_question_ids", "aliases", "created_at")
LOCKED_RETRIEVAL = ("embedding_text", "boost_keywords", "intent_types")
MARKERS = ("핵심 개념입니다", "질문이 요구하는", "정답은", "유사한 선택지", "실무에서는")


DETAILS = {
    "python-range": (
        "range는 시작값부터 종료값 직전까지 일정한 증가폭으로 정수를 생성하는 Python 시퀀스다. range(1, 10, 2)는 1, 3, 5, 7, 9를 만든다.",
        "range는 종료값을 포함하지 않으므로 1부터 2씩 증가해도 10은 생성되지 않는다. 1, 3, 9는 순서에 포함되지만 10은 종료 경계라 제외된다.",
        ["1은 시작값이라 포함된다. 종료 경계인 10과 역할이 다르다.", "3은 시작값에 증가폭 2를 더한 값이라 포함된다.", "9는 종료값 10보다 작은 마지막 생성값이라 포함된다."],
        "values = list(range(1, 10, 2))\ncontains_ten = 10 in values\nlast_value = values[-1]",
        "반복 횟수 제어와 일정 간격 샘플링에서 종료값 제외 규칙을 확인할 때 사용한다.",
        ["range", "종료값 제외", "증가폭"],
    ),
    "algorithm-queue": (
        "큐(Queue)는 먼저 들어온 항목을 먼저 꺼내는 선입선출(FIFO) 자료구조다. 뒤에 항목을 추가하고 앞에서 제거하므로 A 다음 B를 넣으면 A가 먼저 나온다.",
        "큐는 가장 오래 기다린 항목을 먼저 처리하므로 FIFO가 맞다. LIFO는 스택의 규칙이며 임의 접근은 큐의 처리 순서를 설명하지 못한다.",
        ["LIFO는 마지막 항목을 먼저 꺼내는 스택의 규칙이다.", "LILO는 큐의 핵심 처리 순서를 정확히 표현하지 못한다.", "임의 접근은 원하는 위치를 직접 조회하는 성질로 FIFO와 다르다."],
        "from collections import deque\nqueue = deque([\"A\", \"B\"])\nfirst = queue.popleft()",
        "요청 대기열과 메시지 처리처럼 도착 순서를 보존해야 하는 작업에 사용한다.",
        ["Queue", "FIFO", "선입선출"],
    ),
    "algorithm-dfs": (
        "DFS(깊이 우선 탐색)는 한 경로를 끝까지 따라간 뒤 이전 분기점으로 돌아가 다른 경로를 탐색한다. 호출 스택이나 명시적 스택에 다음 방문 지점을 저장한다.",
        "DFS는 최근에 발견한 정점을 먼저 방문하므로 후입선출 구조인 스택이 맞다. 재귀도 호출 스택을 사용하며 큐는 BFS 순서를 만든다.",
        ["큐는 먼저 발견한 정점을 처리해 BFS 순서를 만든다.", "힙은 우선순위를 관리하지만 DFS의 경로 복귀 순서를 보존하지 않는다.", "해시 테이블은 방문 여부 확인에는 쓰지만 다음 경로 순서를 관리하지 못한다."],
        "stack = [\"A\"]\nstack.append(\"B\")\nnext_node = stack.pop()",
        "경로 존재 확인과 백트래킹처럼 한 분기를 깊게 조사해야 할 때 사용한다.",
        ["DFS", "스택", "재귀"],
    ),
    "algorithm-bst": (
        "이진 탐색 트리(BST)는 왼쪽에 작은 값, 오른쪽에 큰 값을 두는 트리다. 균형에 가까우면 비교할 때마다 탐색 범위가 줄어 평균 O(log n)에 검색한다.",
        "균형에 가까운 BST는 비교 뒤 한쪽 하위 트리만 따라가므로 평균 검색 시간이 O(log n)이다. O(n)은 한쪽으로 치우친 최악의 경우다.",
        ["O(1)은 주소를 직접 계산하는 조회에 가깝고 BST는 높이만큼 비교한다.", "O(n)은 트리가 한쪽으로 치우친 최악의 검색 비용이다.", "O(n log n)은 단일 평균 검색보다 큰 정렬 계열 비용이다."],
        "values = [4, 2, 6, 1, 3]\ntarget = 3\nsearch_path = [4, 2, 3]",
        "정렬 순회와 범위 조회가 함께 필요한 탐색 구조를 설계할 때 사용한다.",
        ["BST", "평균 O(log n)", "균형 트리"],
    ),
    "java-jvm": (
        "JVM Heap은 new로 생성한 객체 인스턴스와 배열이 저장되는 공유 메모리 영역이다. new User() 객체는 Heap에 놓이고 지역 변수는 그 참조를 Stack에 보관한다.",
        "객체 인스턴스는 여러 메서드에서 참조될 수 있어 JVM Heap에 저장된다. Stack은 호출 프레임을 담고 Method Area와 PC Register는 객체 본체를 저장하지 않는다.",
        ["Stack은 호출 프레임과 지역 변수를 저장하며 객체 본체는 Heap에 둔다.", "Method Area는 클래스 메타데이터를 관리하고 개별 객체는 저장하지 않는다.", "PC Register는 현재 명령 위치를 추적하며 객체 데이터를 보관하지 않는다."],
        "class User {}\nUser user = new User();\nint count = 1;",
        "메모리 누수 분석과 GC 튜닝에서 객체가 저장되는 영역을 구분할 때 사용한다.",
        ["JVM Heap", "객체 인스턴스", "Stack"],
    ),
    "spring-jpa": (
        "JPA의 @ManyToOne은 여러 엔티티가 하나의 연관 엔티티를 참조하는 관계 매핑이다. fetch를 생략하면 기본 전략은 EAGER라서 연관 대상을 즉시 로딩 대상으로 본다.",
        "@ManyToOne의 기본 fetch 값은 EAGER이므로 연관 엔티티를 즉시 로딩 대상으로 처리한다. LAZY는 직접 지정해야 하며 NONE과 SELECT는 FetchType 값이 아니다.",
        ["LAZY는 자주 권장되지만 기본값이 아니므로 직접 지정해야 한다.", "NONE은 JPA FetchType에 정의된 값이 아니다.", "SELECT는 FetchType 값이 아니며 기본 전략은 EAGER이다."],
        "@ManyToOne\nprivate Team team;\nTeam selected = member.getTeam();",
        "엔티티 연관관계를 설계하고 불필요한 즉시 로딩을 진단할 때 사용한다.",
        ["@ManyToOne", "EAGER", "fetch 전략"],
    ),
}

EXAMPLE_ONLY = {
    "spring-spring-question-59": (
        "@CacheEvict(cacheNames = \"users\", key = \"#id\")\npublic void updateUser(Long id) {}\nservice.updateUser(1L);",
        "호출 뒤 users 캐시의 해당 키가 제거되어 다음 조회가 최신 데이터를 다시 읽는다.",
    ),
    "java-extends": (
        "List<? extends Number> values = List.of(1, 2, 3);\nNumber first = values.get(0);\nint size = values.size();",
        "? extends Number 컬렉션에서 값을 Number로 안전하게 읽는 동작을 보여준다.",
    ),
    "frontend-react-key": (
        "const items = [{ id: 1, name: \"A\" }];\nconst rows = items.map(item => <li key={item.id}>{item.name}</li>);\nconst first = rows[0];",
        "각 목록 항목에 안정적인 id를 key로 지정해 React가 항목의 정체성을 추적하게 한다.",
    ),
}


def _strings(value) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [item for child in value.values() for item in _strings(child)]
    if isinstance(value, list):
        return [item for child in value for item in _strings(child)]
    return []


def _tokens(value: str) -> set[str]:
    return {item.lower() for item in re.findall(r"[가-힣]+|[A-Za-z][A-Za-z0-9()@._+-]*|O\([^)]*\)", value) if len(item) > 1}


def _similarity(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    return len(a & b) / max(1, len(a | b))


def score(card: dict) -> tuple[float, float, int]:
    values = _strings(card.get("payloads", {}))
    rendered = " ".join(values)
    generic = min(1.0, sum(rendered.count(marker) for marker in MARKERS) / 3)
    long_values = [value for value in values if len(value) > 30]
    duplicate = max((_similarity(a, b) for index, a in enumerate(long_values) for b in long_values[index + 1:]), default=0.0)
    return generic, duplicate, len(rendered)


def select_candidates(cards: list[dict], limit: int = 10) -> list[str]:
    ranked = sorted(cards, key=lambda card: (-score(card)[0], -score(card)[1], score(card)[2], card["card_id"]))
    return [card["card_id"] for card in ranked[:limit]]


def _lock(card: dict) -> dict:
    return {
        **{field: copy.deepcopy(card[field]) for field in LOCKED},
        "retrieval": {field: copy.deepcopy(card["retrieval"][field]) for field in LOCKED_RETRIEVAL},
    }


def _locked(card: dict, lock: dict) -> bool:
    return all(card[field] == lock[field] for field in LOCKED) and all(
        card["retrieval"][field] == lock["retrieval"][field] for field in LOCKED_RETRIEVAL
    )


def _payload(card: dict, detail: tuple) -> dict:
    definition, answer, wrong_reasons, code, usage, keys = detail
    wrong = card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]
    texts = [value["text"] for value in wrong.values()]
    return {
        "CONCEPT_DEFINITION": {"content": definition, "examples": [usage]},
        "ANSWER_REASON": {"why_correct": answer, "key_points": keys},
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["용어 이름만 보고 실제 원리와 비교 기준을 확인하지 않는 실수"],
            "per_option": {
                f"option_{index}": {"text": text, "reason": reason}
                for index, (text, reason) in enumerate(zip(texts, wrong_reasons, strict=True))
            },
        },
        "COMPARISON": card["payloads"].get("COMPARISON"),
        "EXAMPLE_REQUEST": {"code_example": code, "explanation": usage},
        "PRACTICAL_USAGE": {"real_world": usage, "best_practices": ["경계 조건을 작은 테스트로 확인한다."]},
        "DEBUG_OR_ERROR": card["payloads"].get("DEBUG_OR_ERROR"),
    }


def _metrics(metrics) -> dict[str, float]:
    return dict(metrics.__dict__)


def main() -> int:
    paths = sorted(CARD_ROOT.rglob("*.json"))
    raw_cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in paths]
    candidates = select_candidates(raw_cards)
    questions = extract_questions()
    before_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    before = evaluate_retrieval_modes(questions, before_cards)
    patched, rolled_back, skipped, json_failed = [], [], [], []
    BACKUP_ROOT.mkdir(parents=True, exist_ok=True)
    path_by_id = {card["card_id"]: path for card, path in zip(raw_cards, paths, strict=True)}

    for card_id in candidates:
        if card_id not in DETAILS and card_id not in EXAMPLE_ONLY:
            skipped.append(card_id)
            continue
        current_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        current = evaluate_retrieval_modes(questions, current_cards)
        path = path_by_id[card_id]
        backup = BACKUP_ROOT / path.relative_to(CARD_ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, backup)
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        lock = _lock(card)
        if card_id in DETAILS:
            card["payloads"] = _payload(card, DETAILS[card_id])
        else:
            code, explanation = EXAMPLE_ONLY[card_id]
            card["payloads"]["EXAMPLE_REQUEST"] = {"code_example": code, "explanation": explanation}
        card["review"]["card_status"] = "draft"
        card["review"]["reviewer"] = "payload_patch:v2.1.3"
        card["review"]["approved_at"] = None
        for intent in ("CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON"):
            card["review"]["payload_status"][intent] = "draft"
        path.write_text(json.dumps(card, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        try:
            validated = RagCard.model_validate_json(path.read_text(encoding="utf-8"))
            if not _locked(json.loads(path.read_text(encoding="utf-8")), lock):
                raise ValueError("searchable lock changed")
        except Exception:
            shutil.copy2(backup, path)
            json_failed.append(card_id)
            rolled_back.append(card_id)
            continue

        simulated_cards = [copy.deepcopy(item) for item in [c for c in load_concept_cards(CARD_ROOT) if isinstance(c, RagCard)]]
        simulated = next(item for item in simulated_cards if item.card_id == card_id)
        simulated.review.card_status = "approved"
        simulated.review.payload_status = {
            intent: PayloadStatus.APPROVED for intent in simulated.review.payload_status
        }
        after = evaluate_retrieval_modes(questions, simulated_cards)
        accepted, _ = payload_patch_acceptance(
            current["production_mode"], current["content_mode"],
            after["production_mode"], after["content_mode"],
        )
        if not accepted:
            shutil.copy2(backup, path)
            rolled_back.append(card_id)
            continue
        validated.review.card_status = "approved"
        validated.review.payload_status = {
            intent: PayloadStatus.APPROVED for intent in validated.review.payload_status
        }
        validated.review.reviewer = "approved_locked:v2.1.3"
        validated.review.approved_at = datetime.now(timezone.utc)
        path.write_text(validated.model_dump_json(indent=2, by_alias=True) + "\n", encoding="utf-8")
        patched.append(card_id)

    final_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    final = evaluate_retrieval_modes(questions, final_cards)
    production_accepted, _ = payload_patch_acceptance(
        before["production_mode"], before["content_mode"],
        final["production_mode"], final["content_mode"],
    )
    if not production_accepted:
        for card_id in patched:
            path = path_by_id[card_id]
            backup = BACKUP_ROOT / path.relative_to(CARD_ROOT)
            shutil.copy2(backup, path)
        rolled_back.extend(patched)
        patched = []
        final_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        final = evaluate_retrieval_modes(questions, final_cards)
    report = {
        "candidate_cards": candidates,
        "patched_cards": patched,
        "rolled_back_cards": rolled_back,
        "skipped_cards": skipped,
        "production_hit_diff": final["production_mode"].exact_hit1 - before["production_mode"].exact_hit1,
        "production_loo_diff": final["production_mode"].loo_average_score - before["production_mode"].loo_average_score,
        "content_hit_diff": final["content_mode"].exact_hit1 - before["content_mode"].exact_hit1,
        "content_loo_diff": final["content_mode"].loo_average_score - before["content_mode"].loo_average_score,
        "false_regression_detected": (
            final["production_mode"] == before["production_mode"]
            and final["content_mode"] != before["content_mode"]
        ),
        "json_failed": json_failed,
        "production_before": _metrics(before["production_mode"]),
        "production_after": _metrics(final["production_mode"]),
        "content_before": _metrics(before["content_mode"]),
        "content_after": _metrics(final["content_mode"]),
        "backup_root": str(BACKUP_ROOT),
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import copy
import json
import re
import shutil
from collections import Counter
from datetime import datetime
from pathlib import Path

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard
from app.scripts.migrate_rag_cards import evaluate_retrieval_modes, extract_questions


ROOT = Path(__file__).resolve().parents[2]
CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
BACKUP_ROOT = ROOT / "app" / "knowledge" / "concepts_v2_backups"
REPORT = ROOT / "reports" / "payload_batch_v2_1_4_2026-06-13.json"
ELIGIBLE = {"draft", "approved", "approved_locked", "needs_revision"}
TEMPLATE_PHRASES = ("정답은", "조건을 만족", "질문이 요구", "동작을 보장", "실행 결과", "유사한 선택지", "실무에서는")
LOCKED = ("card_id", "category", "term", "source_question_ids", "retrieval", "aliases", "created_at")


PATCHES = {
    "python-weakref": (
        "weakref는 객체를 소유하지 않는 약한 참조를 만든다. 강한 참조가 모두 사라지면 약한 참조가 남아 있어도 객체를 회수할 수 있어 캐시와 참조 순환 관리에 유용하다.",
        "약한 참조는 참조 카운트를 늘리지 않아 대상의 수명을 불필요하게 연장하지 않는다. 즉시 해제하거나 가비지 컬렉션을 끄는 기능과는 다르다.",
        "import weakref\nclass Item: pass\nitem = Item()\nreference = weakref.ref(item)",
        "객체 수명을 소유하지 않는 캐시나 관찰자 목록을 구현할 때 메모리 잔존을 줄이는 데 사용한다.",
        ["weakref", "약한 참조", "참조 카운트"],
    ),
    "frontend-hydration": (
        "Hydration은 서버가 만든 HTML에 클라이언트 JavaScript의 상태와 이벤트 처리를 연결하는 과정이다. 초기 화면은 기존 HTML을 활용하고 이후 상호작용을 활성화한다.",
        "서버 HTML이 이미 있으므로 클라이언트는 이벤트와 상태를 연결해 상호작용 가능한 화면으로 만든다. CSS 적용, 직렬화, 메모리 해제는 이 연결 과정이 아니다.",
        "const root = document.getElementById(\"root\");\nconst app = React.createElement(\"button\", null, \"확인\");\nReactDOM.hydrateRoot(root, app);",
        "SSR 화면의 초기 표시 속도를 유지하면서 버튼과 입력 같은 클라이언트 상호작용을 활성화할 때 사용한다.",
        ["Hydration", "SSR", "이벤트 연결"],
    ),
    "spring-async": (
        "Spring @Async는 프록시가 외부 호출을 가로채 별도 실행기에서 메서드를 수행한다. 같은 객체 내부에서 직접 호출하면 프록시를 거치지 않아 비동기 처리가 적용되지 않는다.",
        "같은 클래스 내부 호출은 프록시 경계를 통과하지 않으므로 @Async 인터셉터가 실행되지 않는다. 반환형은 void 외에도 Future 계열을 사용할 수 있다.",
        "@Async\npublic CompletableFuture<String> load() {\n    return CompletableFuture.completedFuture(\"done\");\n}",
        "메일 발송이나 외부 API 호출처럼 요청 응답과 분리할 작업을 실행할 때 프록시 호출 경계를 점검한다.",
        ["@Async", "프록시", "내부 호출"],
    ),
    "python-cpython": (
        "CPython은 자주 쓰이는 작은 정수 객체를 미리 만들고 재사용한다. 기본 빌드에서 -5부터 256 범위가 대표적이며 반복적인 객체 생성과 메모리 할당 비용을 줄인다.",
        "작은 정수 캐시는 빈번한 값의 객체 생성 비용을 줄이는 구현 최적화다. Python 문법 규칙이나 가비지 컬렉션·스레드 안전성 보장을 위한 기능은 아니다.",
        "first = 100\nsecond = 100\nsame_object = first is second\nsame_value = first == second",
        "객체 정체성과 값 동등성을 구분해 디버깅할 때 구현 세부사항에 의존하지 않도록 주의하는 사례로 사용한다.",
        ["CPython", "작은 정수 캐시", "객체 재사용"],
    ),
    "frontend-fiber": (
        "React Fiber는 렌더링 작업을 작은 단위로 나누고 우선순위를 부여할 수 있는 재조정 구조다. 긴 렌더링을 잠시 멈추고 더 중요한 업데이트를 먼저 처리할 수 있다.",
        "작업 단위를 분할하고 스케줄링할 수 있다는 점이 Fiber의 핵심이다. 번들 크기, TypeScript, CSS-in-JS 지원은 렌더링 스케줄링 구조와 별개다.",
        "const [text, setText] = React.useState(\"\");\nReact.startTransition(() => setText(\"낮은 우선순위\"));\nconst view = <p>{text}</p>;",
        "입력 반응성을 유지하면서 비용이 큰 목록이나 화면 업데이트를 처리할 때 우선순위를 조절하는 기반으로 사용된다.",
        ["React Fiber", "작업 분할", "우선순위"],
    ),
    "spring-transactional": (
        "@Transactional(readOnly = true)는 읽기 중심 트랜잭션 의도를 전달한다. JPA 구현과 설정에 따라 변경 감지 부담을 줄이고 데이터베이스 읽기 최적화 힌트를 적용할 수 있다.",
        "읽기 전용 트랜잭션은 조회를 막거나 트랜잭션을 제거하지 않으며, Hibernate에서는 flush 모드 조정으로 변경 감지 비용을 줄일 수 있다. 캐시 비활성화와도 무관하다.",
        "@Transactional(readOnly = true)\npublic List<User> findAll() {\n    return repository.findAll();\n}",
        "조회 전용 서비스에서 불필요한 변경 추적을 줄이고 읽기 의도를 명확히 할 때 사용한다.",
        ["@Transactional", "readOnly", "변경 감지"],
    ),
    "python-typing": (
        "typing.Protocol은 필요한 속성과 메서드의 형태가 맞으면 명시적 상속 없이도 타입 호환으로 보는 구조적 서브타이핑을 표현한다. ABC는 보통 명시적 상속 관계를 사용한다.",
        "Protocol은 구현 클래스가 직접 상속하지 않아도 요구한 구조를 갖추면 정적 타입 검사에서 호환된다. 단순 성능 향상이나 기본 런타임 타입 검사가 핵심은 아니다.",
        "from typing import Protocol\nclass Named(Protocol):\n    name: str\nclass User: name = \"민수\"",
        "외부 구현체에 상속을 강제하지 않고 필요한 인터페이스 형태만 타입으로 검사할 때 사용한다.",
        ["Protocol", "구조적 서브타이핑", "ABC"],
    ),
    "algorithm-np": (
        "NP-Complete 문제는 해가 주어졌을 때 다항 시간에 검증할 수 있고 모든 NP 문제를 다항 시간에 환원할 수 있는 문제다. 현재 일반적인 다항 시간 해법은 알려지지 않았다.",
        "검증은 다항 시간에 가능하지만 해를 찾는 일반 다항 시간 알고리즘은 알려지지 않았다는 설명이 핵심이다. 해의 부재나 근사 불가능성을 뜻하지 않는다.",
        "candidate = [1, 3, 4]\ntarget = 8\nverified = sum(candidate) == target",
        "정확 해법의 비용이 큰 문제에서 휴리스틱, 근사, 입력 크기 제한을 선택하는 기준으로 사용한다.",
        ["NP-Complete", "다항 시간 검증", "환원"],
    ),
    "frontend-dom": (
        "React의 가상 DOM은 UI 구조를 메모리 표현으로 만들고 이전 결과와 비교해 변경된 부분을 계산한다. React는 계산 결과를 바탕으로 실제 DOM 업데이트를 필요한 범위로 제한한다.",
        "가상 DOM 비교는 변경 지점을 찾아 실제 DOM 반영 범위를 줄인다. DOM을 없애거나 매번 전체를 다시 만들거나 WebWorker에서 직접 처리하는 방식은 아니다.",
        "const before = <p>이전</p>;\nconst after = <p>변경</p>;\nconst changedText = before.props.children !== after.props.children;",
        "상태 변화가 잦은 화면에서 선언적으로 UI를 갱신하고 변경 범위를 추적할 때 사용한다.",
        ["가상 DOM", "diff", "최소 업데이트"],
    ),
    "python-slots": (
        "__slots__는 클래스 인스턴스가 가질 속성 이름을 선언하고 일반적인 인스턴스 __dict__ 생성을 생략할 수 있게 한다. 인스턴스가 많을 때 메모리 사용량을 줄일 수 있다.",
        "__slots__는 인스턴스 속성 저장 방식을 제한해 메모리를 절약한다. 메서드나 상속을 차단하거나 직렬화를 자동 지원하는 기능과는 다르다.",
        "class Point:\n    __slots__ = (\"x\", \"y\")\npoint = Point()\npoint.x = 1",
        "동일한 형태의 작은 객체를 대량 생성할 때 속성 유연성과 메모리 절약의 trade-off를 판단하는 데 사용한다.",
        ["__slots__", "__dict__", "메모리 절약"],
    ),
    "python-def": (
        "Python의 가변 기본 인자는 함수 정의 시 한 번 생성되어 이후 호출에서도 같은 객체를 공유한다. 기본 리스트에 1을 넣은 뒤 다시 2를 넣으면 누적된 [1, 2]가 된다.",
        "두 호출이 같은 기본 리스트를 재사용하므로 첫 호출 뒤 [1], 두 번째 호출 뒤 [1, 2]가 된다. 호출마다 새 리스트가 생긴다는 보기는 이 초기화 시점을 잘못 본 것이다.",
        "def f(a, b=[]):\n    b.append(a)\n    return b\nfirst, second = f(1), f(2)",
        "함수 인자의 상태 공유 버그를 디버깅할 때 기본값으로 None을 두고 내부에서 새 객체를 만드는 이유를 설명한다.",
        ["가변 기본 인자", "객체 재사용", "함수 정의 시점"],
    ),
    "frontend-jsx-expression": (
        "JSX에서는 중괄호 안에 JavaScript 표현식을 넣어 값이나 계산 결과를 렌더링한다. 예를 들어 <span>{user.name}</span>은 name 값을 화면에 표시한다.",
        "중괄호는 JSX 문맥에서 JavaScript 표현식 평가 영역을 연다. 소괄호와 대괄호는 이 역할이 없고, 꺾쇠괄호는 요소 태그를 구성한다.",
        "const user = { name: \"민수\" };\nconst label = <span>{user.name}</span>;\nconst enabled = <button>{user.name.length}</button>;",
        "동적 텍스트, 계산값, 조건 표현식을 JSX 요소 안에 삽입할 때 사용한다.",
        ["JSX", "중괄호", "JavaScript 표현식"],
    ),
    "python-fstring": (
        "Python f-string은 문자열 앞에 f를 붙이고 중괄호 안의 표현식을 평가해 문자열에 삽입한다. name이 '민수'라면 f'{name}'은 해당 값을 포함한 문자열이 된다.",
        "f 접두사와 중괄호를 함께 사용해야 표현식이 평가된다. ${name}, (name), #{name} 표기는 Python f-string 문법이 아니다.",
        "name = \"민수\"\nmessage = f\"이름은 {name}입니다\"\nlength = f\"글자 수는 {len(name)}\"",
        "로그 메시지와 사용자 안내 문구에 변수나 계산 결과를 읽기 쉽게 삽입할 때 사용한다.",
        ["f-string", "중괄호", "문자열 포매팅"],
    ),
    "python-dictionary": (
        "Python 딕셔너리는 키와 값을 연결해 저장하는 매핑 자료구조이며 중괄호 안에 key: value 쌍을 작성한다. {'name': '민수'}는 name 키로 값을 조회할 수 있다.",
        "key: value를 중괄호로 감싼 형태가 딕셔너리 리터럴이다. 대괄호는 리스트, 소괄호 쌍은 튜플이며 꺾쇠괄호 표기는 Python 문법이 아니다.",
        "user = {\"name\": \"민수\", \"age\": 20}\nname = user[\"name\"]\nuser[\"age\"] = 21",
        "식별 키로 설정값이나 객체 속성을 빠르게 조회하고 갱신할 때 사용한다.",
        ["dictionary", "key-value", "매핑"],
    ),
    "java-hashmap": (
        "HashMap은 키의 hashCode로 버킷 위치를 찾고 equals로 키를 구분하는 Java 자료구조다. 해시가 고르게 분산되면 검색과 삽입은 평균 O(1)에 수행된다.",
        "평균적으로 버킷 위치를 바로 계산하므로 검색과 삽입 모두 O(1)이다. O(n)과 O(log n)은 해시 기반의 평균 접근 특성을 설명하지 못한다.",
        "Map<String, Integer> scores = new HashMap<>();\nscores.put(\"A\", 10);\nInteger score = scores.get(\"A\");",
        "키 기반 캐시와 인덱스를 설계할 때 충돌 분포와 키의 equals/hashCode 구현을 함께 점검한다.",
        ["HashMap", "평균 O(1)", "hashCode"],
    ),
    "algorithm-11": (
        "우선순위 큐를 사용하는 다익스트라는 정점의 최소 거리를 꺼내고 각 간선을 완화한다. 정점과 간선 처리에 힙 연산이 붙어 O((V + E) log V)가 된다.",
        "각 정점과 간선을 처리하면서 우선순위 큐의 삽입·삭제에 log V가 들기 때문에 O((V + E) log V)다. O(V²)는 배열 기반 구현에 가까운 비용이다.",
        "import heapq\nqueue = [(0, \"A\")]\nheapq.heappush(queue, (3, \"B\"))\ndistance, node = heapq.heappop(queue)",
        "희소 그래프의 최단 경로를 계산할 때 인접 리스트와 우선순위 큐 조합을 선택하는 기준으로 사용한다.",
        ["다익스트라", "우선순위 큐", "O((V + E) log V)"],
    ),
    "python-multiline-string": (
        "Python의 삼중 따옴표는 줄바꿈을 포함한 문자열 리터럴을 만든다. 큰따옴표 세 개와 작은따옴표 세 개 모두 여러 줄 텍스트를 표현할 수 있다.",
        "두 종류의 삼중 따옴표가 모두 여러 줄 문자열을 만든다. <<텍스트>>는 문자열 구분자가 아니며, 한 종류만 가능하다는 해석도 범위를 좁힌다.",
        "message = \"\"\"첫째 줄\n둘째 줄\"\"\"\nlines = message.splitlines()\nsecond = lines[1]",
        "긴 안내문, SQL, 테스트 입력처럼 원래 줄바꿈을 유지해야 하는 텍스트에 사용한다.",
        ["삼중 따옴표", "여러 줄 문자열", "줄바꿈"],
    ),
    "java-main": (
        "Java 애플리케이션의 시작점은 public static void main(String[] args) 메서드다. JVM은 클래스 인스턴스 없이 이 정적 메서드를 찾아 문자열 배열 인자를 전달한다.",
        "JVM이 외부에서 인스턴스 없이 호출하려면 public과 static이 필요하고 반환형은 void여야 한다. 다른 보기는 접근 지정자, static, 반환형 중 하나가 빠져 있다.",
        "public class App {\n    public static void main(String[] args) {\n        int argumentCount = args.length;\n    }\n}",
        "명령행 애플리케이션의 진입점을 만들고 전달된 실행 인자를 처리할 때 사용한다.",
        ["main", "public static void", "String[] args"],
    ),
    "python-list-comprehension": (
        "리스트 컴프리헨션은 표현식, 반복절, 선택적 조건절을 한 대괄호 안에 작성해 새 리스트를 만든다. [x for x in range(10) if x > 5]는 6부터 9를 모은다.",
        "결과 표현식 x 뒤에 for 반복절과 if 필터가 오는 순서가 올바르다. 다른 보기는 콜론을 넣거나 표현식과 조건절 위치를 바꿔 문법이 성립하지 않는다.",
        "numbers = range(10)\nfiltered = [x for x in numbers if x > 5]\nlast = filtered[-1]",
        "컬렉션 변환과 필터링을 짧고 명확하게 표현할 때 사용한다.",
        ["리스트 컴프리헨션", "for 절", "if 필터"],
    ),
    "python-multiprocessing": (
        "multiprocessing은 별도 프로세스로 CPU 코어를 활용하고, threading은 같은 프로세스에서 I/O 대기 중 다른 작업을 진행한다. 일반적으로 CPU-bound와 I/O-bound 작업에 각각 대응한다.",
        "CPU 연산은 여러 프로세스로 병렬화하고 I/O 대기는 스레드 전환으로 숨기는 조합이 적절하다. 반대 조합은 Python 실행 특성과 대기 비용을 제대로 활용하지 못한다.",
        "from multiprocessing import Pool\nwith Pool(2) as pool:\n    squares = pool.map(lambda x: x * x, [1, 2])",
        "연산 집약 작업과 네트워크·파일 대기 작업을 구분해 동시성 모델을 선택할 때 사용한다.",
        ["multiprocessing", "CPU-bound", "I/O-bound"],
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
    return {token.lower() for token in re.findall(r"[가-힣]+|[A-Za-z][A-Za-z0-9()@._+-]*", value) if len(token) > 1}


def _similarity(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    return len(a & b) / max(1, len(a | b))


def is_eligible(card: dict) -> bool:
    return card.get("review", {}).get("card_status") in ELIGIBLE


def candidate_score(card: dict) -> dict[str, float | int]:
    payloads = card.get("payloads", {})
    values = _strings(payloads)
    rendered = " ".join(values)
    answer = (payloads.get("ANSWER_REASON") or {}).get("why_correct", "")
    definition = (payloads.get("CONCEPT_DEFINITION") or {}).get("content", "")
    code = (payloads.get("EXAMPLE_REQUEST") or {}).get("code_example", "")
    long_values = [value for value in values if len(value) > 30]
    return {
        "template_phrase_count": sum(rendered.count(phrase) for phrase in TEMPLATE_PHRASES),
        "answer_reason_overlap": _similarity(answer, definition),
        "example_quality": float("\n" in code and len(code.splitlines()) >= 3 and "print(" not in code),
        "generic_score": min(1.0, sum(rendered.count(phrase) for phrase in TEMPLATE_PHRASES) / 4),
        "duplicate_score": max((_similarity(a, b) for i, a in enumerate(long_values) for b in long_values[i + 1:]), default=0.0),
        "payload_length": len(rendered),
    }


def rank_candidates(cards: list[dict]) -> list[dict]:
    ranked = [{"card_id": card["card_id"], "score": candidate_score(card), "card": card} for card in cards if is_eligible(card)]
    return sorted(ranked, key=lambda item: (
        -item["score"]["template_phrase_count"],
        -item["score"]["answer_reason_overlap"],
        item["score"]["example_quality"],
        -item["score"]["generic_score"],
        -item["score"]["duplicate_score"],
        item["score"]["payload_length"],
        item["card_id"],
    ))


def batch_sizes(total: int) -> list[int]:
    result, consumed = [], 0
    for size in (10, 20, 40, 40):
        if consumed >= total:
            break
        actual = min(size, total - consumed)
        result.append(actual)
        consumed += actual
    if consumed < total:
        result.append(total - consumed)
    return result


def expansion_stop_reasons(report: dict) -> list[str]:
    reasons = []
    if report["patch_rate"] < 0.15:
        reasons.append("patch_rate_below_15_percent")
    if report["production_hit_diff"] < 0:
        reasons.append("production_hit_regression")
    if report["production_loo_diff"] < 0:
        reasons.append("production_loo_regression")
    if report["json_failed"]:
        reasons.append("json_failed")
    if report["content_hit_diff"] < -0.01:
        reasons.append("content_hit_regression_over_1_percent")
    return reasons


def _payload(card: dict, detail: tuple[str, str, str, str, list[str]]) -> dict:
    definition, answer, code, usage, key_points = detail
    old = card["payloads"]
    per_option = old["WRONG_ANSWER_REASON"]["per_option"]
    answer_summary = answer.split(".")[0]
    return {
        "CONCEPT_DEFINITION": {"content": definition, "examples": [usage]},
        "ANSWER_REASON": {"why_correct": answer, "key_points": key_points},
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["표면적인 문법이나 용어만 보고 내부 원리와 선택 기준을 확인하지 않는 실수"],
            "per_option": {
                key: {
                    "text": value["text"],
                    "reason": f"{value['text']}은 해당 원리의 핵심 처리 방식을 설명하지 못한다. 반면 {answer_summary}.",
                }
                for key, value in per_option.items()
            },
        },
        "COMPARISON": old.get("COMPARISON"),
        "EXAMPLE_REQUEST": {"code_example": code, "explanation": usage},
        "PRACTICAL_USAGE": {"real_world": usage, "best_practices": ["경계 사례를 작은 실행 예제로 검증한다."]},
        "DEBUG_OR_ERROR": old.get("DEBUG_OR_ERROR"),
    }


def _metrics(metrics) -> dict[str, float]:
    return dict(metrics.__dict__)


def _diff(before, after) -> dict[str, float]:
    return {
        "production_hit_diff": after["production_mode"].exact_hit1 - before["production_mode"].exact_hit1,
        "production_loo_diff": after["production_mode"].loo_average_score - before["production_mode"].loo_average_score,
        "content_hit_diff": after["content_mode"].exact_hit1 - before["content_mode"].exact_hit1,
        "content_loo_diff": after["content_mode"].loo_average_score - before["content_mode"].loo_average_score,
    }


def main() -> int:
    paths = sorted(CARD_ROOT.rglob("*.json"))
    raw_cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in paths]
    ranked = rank_candidates(raw_cards)
    path_by_id = {card["card_id"]: path for card, path in zip(raw_cards, paths, strict=True)}
    questions = extract_questions()
    baseline_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    baseline = evaluate_retrieval_modes(questions, baseline_cards)
    approval_distribution = Counter(card.get("review", {}).get("card_status", "missing") for card in raw_cards)
    patched, skipped, rolled_back, json_failed, failed_cards = [], [], [], [], []
    patch_reasons, skip_reasons, batches = {}, {}, []
    offset = 0

    for size in batch_sizes(len(ranked)):
        candidates = ranked[offset:offset + size]
        offset += size
        timestamp = datetime.now().strftime("%Y%m%dT%H%M%S%f")
        backup = BACKUP_ROOT / f"v214_batch_{size}_{timestamp}"
        backup.mkdir(parents=True, exist_ok=False)
        batch_patched, batch_skipped, batch_rolled = [], [], []
        batch_before_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        batch_before = evaluate_retrieval_modes(questions, batch_before_cards)

        for item in candidates:
            card_id = item["card_id"]
            if card_id not in PATCHES:
                skipped.append(card_id)
                batch_skipped.append(card_id)
                skip_reasons[card_id] = "no_fact_checked_payload_patch"
                continue
            path = path_by_id[card_id]
            backup_path = backup / path.relative_to(CARD_ROOT)
            backup_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup_path)
            original = json.loads(path.read_text(encoding="utf-8-sig"))
            locked = {key: copy.deepcopy(original.get(key)) for key in LOCKED}
            candidate = copy.deepcopy(original)
            candidate["payloads"] = _payload(candidate, PATCHES[card_id])
            path.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            try:
                RagCard.model_validate_json(path.read_text(encoding="utf-8"))
                written = json.loads(path.read_text(encoding="utf-8"))
                if any(written.get(key) != value for key, value in locked.items()):
                    raise ValueError("locked field changed")
            except Exception as exc:
                shutil.copy2(backup_path, path)
                json_failed.append(card_id)
                failed_cards.append({"card_id": card_id, "reason": f"json_or_lock:{exc}"})
                rolled_back.append(card_id)
                batch_rolled.append(card_id)
                continue
            current_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
            current = evaluate_retrieval_modes(questions, current_cards)
            diffs = _diff(baseline, current)
            if diffs["production_hit_diff"] < 0 or diffs["production_loo_diff"] < 0 or diffs["content_hit_diff"] < -0.01:
                shutil.copy2(backup_path, path)
                failed_cards.append({"card_id": card_id, "reason": "retrieval_break"})
                rolled_back.append(card_id)
                batch_rolled.append(card_id)
                continue
            patched.append(card_id)
            batch_patched.append(card_id)
            patch_reasons[card_id] = "template_payload_replaced_with_fact_checked_explanation"

        batch_after_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        batch_after = evaluate_retrieval_modes(questions, batch_after_cards)
        summary = {
            "size": size,
            "backup": str(backup),
            "candidate_cards": [item["card_id"] for item in candidates],
            "patched_cards": batch_patched,
            "skipped_cards": batch_skipped,
            "rolled_back_cards": batch_rolled,
            "patch_rate": len(batch_patched) / max(1, len(candidates)),
            "json_failed": [card_id for card_id in json_failed if card_id in {item["card_id"] for item in candidates}],
            **_diff(batch_before, batch_after),
        }
        summary["stop_reasons"] = expansion_stop_reasons(summary)
        batches.append(summary)
        if summary["stop_reasons"]:
            break

    final_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    final = evaluate_retrieval_modes(questions, final_cards)
    report = {
        "candidate_cards": [item["card_id"] for batch in batches for item in ranked if item["card_id"] in batch["candidate_cards"]],
        "candidate_score_top10": [{"card_id": item["card_id"], **item["score"]} for item in ranked[:10]],
        "patched_cards": patched,
        "skipped_cards": skipped,
        "rolled_back_cards": rolled_back,
        "patch_rate": len(patched) / max(1, sum(len(batch["candidate_cards"]) for batch in batches)),
        **_diff(baseline, final),
        "json_failed": json_failed,
        "failed_cards": failed_cards,
        "patch_reasons": patch_reasons,
        "skip_reasons": skip_reasons,
        "approval_distribution": dict(approval_distribution),
        "cards_by_status": dict(approval_distribution),
        "batches": batches,
        "production_before": _metrics(baseline["production_mode"]),
        "production_after": _metrics(final["production_mode"]),
        "content_before": _metrics(baseline["content_mode"]),
        "content_after": _metrics(final["content_mode"]),
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

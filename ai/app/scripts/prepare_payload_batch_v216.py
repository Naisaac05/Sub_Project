from __future__ import annotations

import json
from pathlib import Path

from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_payload_batch_v215 import example_metrics, same_reason_ratio


ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPORT = ROOT / "reports" / "payload_batch_v2_1_5_preparation_2026-06-13.json"
REPORT = ROOT / "reports" / "payload_batch_v2_1_6_ready_2026-06-13.json"


SPECS = {
    "python-with": {
        "definition": "Python의 with 문은 컨텍스트 매니저의 __enter__와 __exit__를 호출해 자원의 획득과 정리를 블록 범위로 관리한다. 파일 처리 중 예외가 발생해도 종료 단계에서 자원을 닫는다.",
        "answer": "with 문이 사용하는 프로토콜은 __enter__와 __exit__다. __init__과 __del__은 객체 수명 주기용이며, 나머지 메서드 쌍은 컨텍스트 매니저 프로토콜에 정의되지 않는다.",
        "reasons": ["__init__과 __del__은 객체 생성·소멸 훅이며 with 블록 진입과 종료를 담당하지 않는다.", "__open__과 __close__는 Python 컨텍스트 매니저 프로토콜에 존재하지 않는다.", "__start__와 __end__도 with 문이 호출하는 특수 메서드가 아니다."],
        "code": "class Resource:\n    def __enter__(self): self.opened = True; return self\n    def __exit__(self, *args): self.opened = False\nwith Resource() as resource: active = resource.opened",
        "usage": "파일, 락, 데이터베이스 연결처럼 사용 후 반드시 정리해야 하는 자원의 수명 관리에 사용한다.",
        "keys": ["with", "__enter__", "__exit__"],
        "facts": ["with 문은 컨텍스트 매니저 프로토콜을 사용한다.", "__exit__는 정상 종료와 예외 종료 모두에서 호출된다."],
    },
    "java-equals": {
        "definition": "Java의 equals는 객체가 표현하는 값의 논리적 동등성을 비교하는 메서드다. String은 내용을 기준으로 equals를 구현하므로 서로 다른 인스턴스라도 같은 문자열이면 true가 된다.",
        "answer": "문자열 내용 비교에는 str1.equals(str2)를 사용한다. ==는 참조 동일성을 비교하며 compare와 match는 String의 동등성 비교 메서드가 아니다.",
        "reasons": ["str1 == str2는 두 변수가 같은 객체를 가리키는지 비교하므로 내용 동등성과 다르다.", "String에는 str1.compare(str2) 메서드가 없으며 정렬 비교에는 compareTo를 사용한다.", "String의 match 메서드는 존재하지 않고 matches는 정규식 일치 여부를 검사한다."],
        "code": "String first = new String(\"A\");\nString second = new String(\"A\");\nboolean sameValue = first.equals(second);\nboolean sameReference = first == second;",
        "usage": "값 객체 비교와 컬렉션 검색에서 논리적으로 같은 객체를 판단할 때 사용한다.",
        "keys": ["equals", "논리적 동등성", "참조 동일성"],
        "facts": ["Object.equals 기본 구현은 참조 동일성을 사용한다.", "String.equals는 문자열 내용을 비교한다."],
    },
    "frontend-react-key": {
        "definition": "React key는 형제 목록 항목의 정체성을 렌더 간 안정적으로 식별하는 값이다. React는 key로 이전 항목과 다음 항목을 대응시켜 추가, 삭제, 순서 변경을 조정한다.",
        "answer": "목록 렌더링에서 필요한 속성은 key다. 안정적인 key는 항목 상태가 다른 항목으로 이동하는 문제를 줄이며 스타일링, 이벤트, 접근성 속성과 역할이 다르다.",
        "reasons": ["id는 데이터 식별자로 key 값에 활용할 수 있지만 React가 요구하는 속성 이름 자체는 key다.", "name은 중복되거나 변경될 수 있어 항목 정체성을 안정적으로 보장하지 않는다.", "index는 순서가 바뀌는 목록에서 다른 항목에 재사용되어 상태 연결이 어긋날 수 있다.", "key는 reconciliation 식별자이며 CSS 스타일 적용을 위한 속성이 아니다.", "이벤트 바인딩은 onClick 같은 이벤트 prop이 담당하고 key는 이벤트를 연결하지 않는다.", "접근성은 aria-* 속성과 의미 있는 HTML이 담당하며 key는 DOM에 전달되지 않는다."],
        "code": "const items = [{ id: 7, name: \"A\" }, { id: 9, name: \"B\" }];\nconst rows = items.map(item => <li key={item.id}>{item.name}</li>);\nconst firstKey = rows[0].key;",
        "usage": "추가·삭제·정렬이 발생하는 목록에서 컴포넌트 상태와 DOM 재사용을 안정적으로 유지할 때 사용한다.",
        "keys": ["React key", "reconciliation", "항목 정체성"],
        "facts": ["key는 형제 항목 사이에서 고유해야 한다.", "key는 렌더 간 안정적인 값을 사용해야 한다."],
    },
    "java-extends": {
        "definition": "Java 제네릭의 ? extends Number는 Number 또는 그 하위 타입을 원소로 갖는 타입을 허용하는 상한 경계 와일드카드다. 원소를 Number로 읽을 수 있지만 구체 타입을 몰라 null 외에는 추가할 수 없다.",
        "answer": "? extends Number는 Number와 Integer, Double 같은 하위 타입 컬렉션을 받을 수 있다. 부모 타입이나 모든 타입을 허용하는 표현이 아니며 생산자에서 안전한 읽기에 적합하다.",
        "reasons": ["Number만 허용하는 것이 아니라 Number의 모든 하위 타입 컬렉션도 받을 수 있다.", "Number의 부모 타입을 허용하려면 소비자 관점의 ? super Number를 검토해야 한다.", "상한이 Number로 제한되므로 String 같은 무관한 타입은 허용되지 않는다."],
        "code": "List<? extends Number> values = List.of(1, 2, 3);\nNumber first = values.get(0);\ndouble total = values.stream().mapToDouble(Number::doubleValue).sum();",
        "usage": "여러 Number 하위 타입 컬렉션에서 값을 안전하게 읽는 API를 설계할 때 사용한다.",
        "keys": ["? extends Number", "상한 경계", "생산자"],
        "facts": ["extends 와일드카드는 상한 경계를 표현한다.", "구체 원소 타입을 알 수 없어 null 외 값 추가는 안전하지 않다."],
    },
    "spring-spring-question-59": {
        "definition": "Spring의 @CacheEvict는 메서드 실행 시 지정한 캐시 항목을 제거하는 애너테이션이다. 데이터 변경 후 오래된 값이 다시 반환되지 않도록 특정 key 또는 전체 항목을 무효화한다.",
        "answer": "@CacheEvict의 역할은 기존 캐시 항목 제거다. 값 저장·재사용은 @Cacheable 계열의 책임이며 캐시 생성이나 상태 조회를 수행하는 애너테이션이 아니다.",
        "reasons": ["캐시에 결과를 저장하고 재사용하는 역할은 주로 @Cacheable이 담당한다.", "캐시 저장소 생성은 CacheManager 설정이 담당하며 @CacheEvict가 만들지 않는다.", "@CacheEvict는 상태를 조회하지 않고 지정 조건에 따라 항목을 제거한다."],
        "code": "@CacheEvict(cacheNames = \"users\", key = \"#id\")\npublic void updateUser(Long id) { repository.update(id); }\nservice.updateUser(1L);",
        "usage": "데이터 갱신 뒤 오래된 캐시를 제거해 다음 조회가 최신 원본을 읽도록 할 때 사용한다.",
        "keys": ["@CacheEvict", "캐시 무효화", "key"],
        "facts": ["@CacheEvict는 지정 캐시 항목을 제거한다.", "allEntries=true로 캐시 전체를 비울 수 있다."],
    },
    "frontend-streaming": {
        "definition": "Streaming SSR은 서버가 전체 HTML 완성을 기다리지 않고 준비된 UI 조각부터 응답 스트림으로 전송하는 렌더링 방식이다. 사용자는 느린 영역을 기다리는 동안 먼저 도착한 화면을 볼 수 있다.",
        "answer": "준비된 부분부터 점진적으로 전송해 초기 응답과 화면 표시를 앞당기는 것이 장점이다. 번들 크기 감소, SEO 제거, CDN 가능 여부는 스트리밍 자체가 보장하는 효과가 아니다.",
        "reasons": ["스트리밍은 HTML 전송 시점을 나누지만 JavaScript 번들 크기를 직접 줄이지 않는다.", "서버 렌더링 결과가 검색에 도움을 줄 수 있어 SEO가 불필요해지는 것은 아니다.", "CDN 캐싱은 응답 정책과 인프라 설정에 달려 있으며 스트리밍 도입만으로 가능해지지 않는다."],
        "code": "const stream = new ReadableStream({ start(controller) { controller.enqueue(\"<header>준비됨</header>\"); controller.close(); } });\nconst reader = stream.getReader();\nconst firstChunk = reader.read();",
        "usage": "느린 데이터 영역이 있는 SSR 페이지에서 준비된 셸과 콘텐츠를 먼저 전달해 체감 대기 시간을 줄일 때 사용한다.",
        "keys": ["Streaming SSR", "점진적 전송", "초기 응답"],
        "facts": ["스트리밍 SSR은 준비된 HTML 조각부터 전송한다.", "번들 크기나 CDN 캐싱을 자동으로 해결하지 않는다."],
    },
    "frontend-concurrent": {
        "definition": "React의 useTransition은 상태 업데이트를 낮은 우선순위의 전환 작업으로 표시한다. 긴 렌더링 중에도 입력처럼 긴급한 업데이트를 먼저 처리해 UI 반응성을 유지하도록 돕는다.",
        "answer": "useTransition은 비긴급 상태 업데이트의 우선순위를 낮추고 pending 상태를 제공한다. 페이지 애니메이션, 데이터베이스 트랜잭션, CSS transition을 제어하는 API가 아니다.",
        "reasons": ["페이지 전환 애니메이션은 라우터나 CSS 애니메이션이 담당하며 useTransition의 스케줄링 목적과 다르다.", "데이터베이스 트랜잭션은 서버 저장소의 원자성을 관리하며 React 상태 우선순위와 무관하다.", "CSS transition은 스타일 속성 변화의 애니메이션을 제어하고 React 렌더 우선순위를 바꾸지 않는다."],
        "code": "const [isPending, startTransition] = React.useTransition();\nconst [query, setQuery] = React.useState(\"\");\nstartTransition(() => setQuery(\"검색어\"));",
        "usage": "검색 결과나 큰 목록처럼 비용이 큰 상태 갱신을 입력 반응보다 낮은 우선순위로 처리할 때 사용한다.",
        "keys": ["useTransition", "낮은 우선순위", "isPending"],
        "facts": ["useTransition은 상태 업데이트를 transition으로 표시한다.", "긴급 업데이트를 차단하지 않도록 스케줄링한다."],
    },
    "python-asyncio": {
        "definition": "asyncio의 await는 awaitable 작업이 완료될 때까지 현재 코루틴 실행을 일시 중단하고 이벤트 루프에 제어권을 돌려준다. 대기 중에는 다른 준비된 코루틴이 실행될 수 있다.",
        "answer": "await는 코루틴을 일시 중단해 awaitable 완료를 기다리고 결과를 받는다. 새 스레드를 만들거나 함수를 동기식으로 변환하거나 예외를 의도적으로 발생시키는 키워드가 아니다.",
        "reasons": ["await는 이벤트 루프에 제어권을 넘기며 새로운 운영체제 스레드를 생성하지 않는다.", "await를 사용한 함수는 여전히 async 코루틴이며 동기 함수로 변환되지 않는다.", "awaitable이 실패하면 예외가 전파될 수 있지만 await 자체의 목적은 예외 발생이 아니다."],
        "code": "import asyncio\nasync def load():\n    await asyncio.sleep(0.01)\n    return 1\nresult = asyncio.run(load())",
        "usage": "네트워크와 파일 대기처럼 I/O 동안 다른 코루틴을 진행해 동시성을 높일 때 사용한다.",
        "keys": ["asyncio", "await", "이벤트 루프"],
        "facts": ["await는 awaitable 완료까지 코루틴을 중단한다.", "대기 중 이벤트 루프는 다른 작업을 실행할 수 있다."],
    },
    "frontend-suspense": {
        "definition": "React Suspense는 하위 트리가 준비되지 않은 동안 fallback UI를 표시하는 경계다. React.lazy 기반 코드 로딩이나 Suspense 지원 데이터 소스와 함께 준비 상태를 선언적으로 다룬다.",
        "answer": "하위 콘텐츠가 준비될 때까지 fallback을 보여주는 것이 Suspense의 역할이다. 오류 처리는 Error Boundary가 담당하며 상태 초기화나 지연 로딩 함수 자체와도 역할이 다르다.",
        "reasons": ["렌더 오류를 잡아 대체 화면을 보여주는 책임은 Error Boundary에 있으며 Suspense는 대기 상태를 처리한다.", "React.lazy가 컴포넌트 로딩을 지연하고 Suspense는 로딩 중 fallback 경계를 제공한다.", "Suspense는 하위 컴포넌트 상태를 임의로 초기화하는 API가 아니다."],
        "code": "const Profile = React.lazy(() => import(\"./Profile\"));\nconst view = <React.Suspense fallback={<span>로딩 중</span>}><Profile /></React.Suspense>;\nconst fallback = view.props.fallback;",
        "usage": "코드 분할이나 지원되는 비동기 데이터 로딩에서 준비되지 않은 영역에 대체 UI를 표시할 때 사용한다.",
        "keys": ["Suspense", "fallback", "대기 경계"],
        "facts": ["Suspense는 준비되지 않은 하위 트리에 fallback을 표시한다.", "오류 처리는 Error Boundary의 책임이다."],
    },
    "algorithm-heap": {
        "definition": "힙은 완전 이진 트리 형태를 유지하며 부모와 자식 사이에 힙 속성을 만족하는 자료구조다. 최소 힙은 부모가 자식보다 작거나 같아 루트에서 최솟값을 빠르게 꺼낸다.",
        "answer": "완전 이진 트리이며 부모·자식 간 대소 관계를 유지한다는 설명이 힙의 핵심이다. 전체 정렬이나 BST 순서 규칙을 보장하지 않으며 논리 구조는 비선형 트리다.",
        "reasons": ["힙은 루트 우선순위만 보장하며 모든 원소가 완전히 정렬되어 있지는 않다.", "이진 탐색 트리는 왼쪽·오른쪽 서브트리의 순서를 보장하지만 힙은 부모·자식 관계만 보장한다.", "배열로 구현할 수 있어도 힙의 논리 구조는 완전 이진 트리이므로 선형 자료구조가 아니다."],
        "code": "import heapq\nvalues = [7, 2, 5]\nheapq.heapify(values)\nsmallest = heapq.heappop(values)",
        "usage": "우선순위 큐와 상위 k개 선택처럼 최솟값이나 최댓값을 반복해서 꺼내는 작업에 사용한다.",
        "keys": ["Heap", "완전 이진 트리", "힙 속성"],
        "facts": ["힙은 완전 이진 트리 형태를 유지한다.", "최소 힙 루트에는 최솟값이 위치한다."],
    },
}


def _payload(card: dict, spec: dict) -> dict:
    wrong = card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]
    return {
        "CONCEPT_DEFINITION": {"content": spec["definition"], "examples": [spec["usage"]]},
        "ANSWER_REASON": {"why_correct": spec["answer"], "key_points": spec["keys"]},
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["보기의 용어만 확인하지 말고 각 보기의 실제 책임과 정답 개념의 선택 기준을 비교한다."],
            "per_option": {
                key: {"text": option["text"], "reason": reason}
                for (key, option), reason in zip(wrong.items(), spec["reasons"], strict=True)
            },
        },
        "COMPARISON": card["payloads"].get("COMPARISON"),
        "EXAMPLE_REQUEST": {"code_example": spec["code"], "explanation": spec["usage"]},
        "PRACTICAL_USAGE": {"real_world": spec["usage"], "best_practices": ["작은 실행 예제와 경계 사례로 동작을 검증한다."]},
        "DEBUG_OR_ERROR": card["payloads"].get("DEBUG_OR_ERROR"),
    }


def validate_ready_patch(patch: dict) -> list[str]:
    reasons = []
    if not patch.get("fact_check_notes"):
        reasons.append("missing_fact_check_notes")
    if not patch.get("patch_reason"):
        reasons.append("missing_patch_reason")
    payloads = patch.get("payloads") or {}
    wrong = (payloads.get("WRONG_ANSWER_REASON") or {}).get("per_option", {})
    ratio = same_reason_ratio([item.get("reason", "") for item in wrong.values()])
    if ratio > 0.25:
        reasons.append("same_reason_ratio_over_25_percent")
    code = (payloads.get("EXAMPLE_REQUEST") or {}).get("code_example", "")
    metrics = example_metrics(code)
    if metrics["print_answer"] or metrics["fake_example_score"] > 0.5:
        reasons.append("fake_or_print_answer_example")
    return reasons


def main() -> int:
    source = json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))
    backlog = source["PREPARATION_BACKLOG"][:10]
    cards = {
        card["card_id"]: card
        for path in CARD_ROOT.rglob("*.json")
        if (card := json.loads(path.read_text(encoding="utf-8-sig")))["card_id"] in backlog
    }
    ready, remaining, failed = {}, [], {}
    for card_id in backlog:
        spec = SPECS.get(card_id)
        if not spec:
            remaining.append(card_id)
            failed[card_id] = ["fact_checked_payload_patch_not_prepared"]
            continue
        patch = {
            "payloads": _payload(cards[card_id], spec),
            "fact_check_notes": spec["facts"],
            "patch_reason": "반복 템플릿과 선택지별 동일 오답 근거를 제거하고 실제 동작 예시를 제공한다.",
        }
        validation = validate_ready_patch(patch)
        if validation:
            remaining.append(card_id)
            failed[card_id] = validation
            continue
        wrong = patch["payloads"]["WRONG_ANSWER_REASON"]["per_option"]
        metrics = example_metrics(patch["payloads"]["EXAMPLE_REQUEST"]["code_example"])
        patch["quality"] = {
            "same_reason_ratio": same_reason_ratio([item["reason"] for item in wrong.values()]),
            "fake_example_score": metrics["fake_example_score"],
            "example_quality": metrics["example_quality"],
        }
        ready[card_id] = patch
    report = {
        "ready_count": len(ready),
        "backlog_count": len(remaining),
        "PATCHES_READY": ready,
        "PREPARATION_BACKLOG": remaining,
        "failed_preparation": failed,
        "same_reason_ratio": {card_id: patch["quality"]["same_reason_ratio"] for card_id, patch in ready.items()},
        "fake_example_score": {card_id: patch["quality"]["fake_example_score"] for card_id, patch in ready.items()},
        "execution_performed": False,
        "card_files_modified": False,
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

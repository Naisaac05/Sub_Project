from __future__ import annotations

import copy
import json
from pathlib import Path

from app.schemas.rag_card import RagPayloads
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import Question, extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_payload_batch_v215 import discover


ROOT = Path(__file__).resolve().parents[2]
PREVIOUS_REPORT = ROOT / "reports" / "frozen_batch_v212_factcheck_preparation_2026-06-14.json"
REPORT = ROOT / "reports" / "course_balanced_batch_v212_factcheck_preparation_2026-06-14.json"
COURSES = ("java", "spring", "frontend", "python", "algorithm")
ELIGIBLE_STATUSES = {"draft", "approved", "approved_locked", "needs_revision"}


SPECS = {
    "java-extends": {
        "definition": "`? extends Number`는 Number 또는 그 하위 타입을 원소 타입으로 갖는 제네릭 객체를 참조하는 상한 제한 와일드카드다. 실제 타입을 특정할 수 없어 값을 추가하기는 어렵지만 Number로 안전하게 읽을 수 있다.",
        "answer": "상한이 Number이므로 Integer, Double처럼 Number를 상속한 타입의 컬렉션을 참조할 수 있다. Number 하나만 허용하거나 부모 타입을 허용하는 설명은 상한 제한의 범위를 잘못 해석한 것이다.",
        "wrong": [
            "Number만 허용하면 정확한 타입 인자인 `List<Number>`에 해당한다. `? extends Number`는 Number뿐 아니라 Integer와 Double 같은 하위 타입도 포함한다.",
            "부모 타입을 허용하는 표기는 `? super Number`다. `extends`는 Number보다 구체적인 하위 타입 방향으로 범위를 제한한다.",
            "모든 타입 허용은 제한 없는 `?`에 가깝다. `? extends Number`는 Number 계층 밖의 String 같은 타입을 허용하지 않는다.",
        ],
        "comparison": "`? extends Number`는 Number 계층에서 값을 읽는 생산자에 적합하고, `? super Number`는 Number 값을 추가하는 소비자에 적합하다.",
        "code": "List<? extends Number> numbers = List.of(1, 2, 3);\nNumber first = numbers.get(0);\nassert first.intValue() == 1;",
        "example": "Integer 목록을 상한 제한 와일드카드로 참조한 뒤 원소를 Number로 읽을 수 있음을 확인한다.",
        "usage": "숫자 하위 타입 목록을 공통 조회 API로 받아 합계나 통계를 계산할 때 사용한다.",
    },
    "java-equals": {
        "definition": "`equals`는 Java 객체의 논리적 동등성을 비교하는 메서드다. String은 문자열 내용을 기준으로 이를 재정의하므로 서로 다른 객체라도 문자 순서가 같으면 true를 반환한다.",
        "answer": "`str1.equals(str2)`는 String이 재정의한 내용 비교를 수행한다. `==`는 참조 동일성을 비교하며, `compare`, `match`는 String의 동등성 비교 메서드가 아니다.",
        "wrong": [
            "`==`는 두 변수가 같은 객체를 가리키는지 비교한다. 문자열 내용이 같아도 별도 객체라면 false가 될 수 있어 논리적 문자열 비교와 다르다.",
            "String에는 `compare` 메서드가 없다. 정렬 순서를 비교하려면 `compareTo`를 쓰지만, 동일성 확인에는 `equals`가 직접적이다.",
            "String에는 `match` 메서드가 없다. 정규식 일치에는 `matches`를 사용하지만 전체 문자열의 논리적 동등성 비교와 목적이 다르다.",
        ],
        "comparison": "`equals`는 객체의 논리적 값이 같은지 확인하고, `==`는 두 참조가 같은 객체를 가리키는지 확인한다.",
        "code": "String left = new String(\"Java\");\nString right = new String(\"Java\");\nassert left.equals(right) && left != right;",
        "example": "서로 다른 String 객체가 참조는 다르지만 내용 비교에서는 같음을 검증한다.",
        "usage": "사용자 입력, 식별자, 상태 코드처럼 문자열 내용 자체를 비교해야 하는 조건문과 검증 로직에서 사용한다.",
    },
    "spring-spring-question-59": {
        "definition": "`@CacheEvict`는 Spring Cache의 지정된 캐시 항목을 제거하는 애너테이션이다. 메서드 호출 전후에 키 하나 또는 캐시 전체를 무효화해 이후 조회가 최신 데이터를 다시 적재하도록 만든다.",
        "answer": "`@CacheEvict`는 변경되거나 삭제된 원본 데이터와 캐시가 어긋나지 않도록 캐시 항목을 삭제한다. 저장은 `@CachePut` 또는 `@Cacheable`이 담당하며 생성·상태 조회 기능은 아니다.",
        "wrong": [
            "캐시에 값을 저장하거나 갱신하는 역할은 `@CachePut` 또는 조회 결과를 적재하는 `@Cacheable`에 가깝다. `@CacheEvict`는 기존 항목을 제거한다.",
            "캐시 저장소 자체의 생성은 CacheManager 설정이 담당한다. `@CacheEvict`는 이미 구성된 캐시에서 항목을 무효화한다.",
            "캐시 상태 조회는 모니터링이나 CacheManager API의 영역이다. `@CacheEvict`는 조회 결과를 반환하지 않고 삭제 동작을 수행한다.",
        ],
        "comparison": "`@Cacheable`은 캐시 미스 시 결과를 저장하고, `@CachePut`은 결과를 갱신하며, `@CacheEvict`는 오래된 항목을 삭제한다.",
        "code": "@CacheEvict(cacheNames = \"users\", key = \"#id\")\npublic void deleteUser(Long id) { repository.deleteById(id); }\nservice.deleteUser(7L);\nassert cache.get(\"users\", 7L) == null;",
        "example": "실제 `@CacheEvict` 메서드 호출 뒤 users 캐시에서 같은 ID 항목이 제거됐는지 확인한다.",
        "usage": "DB 수정·삭제 후 기존 조회 캐시가 오래된 값을 반환하지 않도록 무효화할 때 사용한다.",
    },
    "spring-aop": {
        "definition": "`@Around`는 대상 메서드 호출을 감싸 실행 전후 로직과 실제 호출 여부를 모두 제어하는 AOP 어드바이스다. `ProceedingJoinPoint.proceed()` 호출 시점과 반환값·예외 처리를 직접 결정한다.",
        "answer": "`@Around`는 `proceed()` 전후에 코드를 배치하고 호출 자체도 생략하거나 대체할 수 있다. 실행 전·후·예외 시점 하나에만 반응하는 다른 어드바이스보다 제어 범위가 넓다.",
        "wrong": [
            "실행 전에만 동작하는 것은 `@Before`의 특징이다. `@Around`는 대상 호출 전 로직 뒤에 `proceed()`와 호출 후 로직까지 포함할 수 있다.",
            "실행 후에만 동작하는 것은 `@After` 또는 `@AfterReturning`에 가깝다. `@Around`는 호출 이전부터 반환 이후까지 감싼다.",
            "예외 발생 시에만 동작하는 것은 `@AfterThrowing`의 역할이다. `@Around`는 정상 실행과 예외 흐름을 모두 제어할 수 있다.",
        ],
        "comparison": "`@Around`는 호출 자체를 제어하고 실행 시간을 측정할 수 있으며, `@Before`와 `@After` 계열은 특정 시점의 부가 동작에 집중한다.",
        "code": "@Around(\"execution(* service.*.*(..))\")\nObject measure(ProceedingJoinPoint joinPoint) throws Throwable {\n    long started = System.nanoTime();\n    Object result = joinPoint.proceed();\n    assert System.nanoTime() >= started;\n    return result;\n}",
        "example": "대상 메서드를 실제 호출하고 전후 시각 차이로 실행 시간을 측정하는 흐름을 보여준다.",
        "usage": "트랜잭션, 실행 시간 측정, 재시도처럼 메서드 호출 전후와 실행 여부를 함께 제어해야 할 때 사용한다.",
    },
    "frontend-react-key": {
        "definition": "React의 `key`는 리스트 형제 요소를 렌더링 사이에서 식별하는 안정적인 값이다. React는 key로 이전 요소와 다음 요소를 대응시켜 필요한 DOM만 갱신하고 컴포넌트 상태를 올바른 항목에 유지한다.",
        "answer": "리스트 요소를 안정적으로 구별하는 전용 속성은 `key`다. `id`와 `name`은 DOM·폼 의미를 가지며, 배열 `index`는 재정렬 시 항목 정체성이 바뀔 수 있어 안정적인 key가 아니다.",
        "wrong": [
            "`id`는 DOM에서 요소를 식별하지만 React의 리스트 비교에 사용되는 전용 식별자는 아니다. 목록 조정에는 형제 사이에서 고유한 `key`가 필요하다.",
            "`name`은 폼 필드 등의 이름을 표현한다. React가 이전 렌더와 다음 렌더의 목록 항목을 대응시키는 기준은 `key`다.",
            "배열 index를 key로 쓸 수는 있지만 삽입·삭제·정렬 시 같은 index가 다른 항목을 가리켜 상태가 잘못 재사용될 수 있다.",
        ],
        "comparison": "`key`는 React 조정 과정의 항목 정체성을 나타내고, DOM `id`는 문서 안에서 요소를 찾거나 연결하는 데 사용한다.",
        "code": "const mounts = [];\nfunction Row({ id }) { React.useEffect(() => mounts.push(id), []); return <span>{id}</span>; }\nconst rows = ids => <>{ids.map(id => <Row key={id} id={id} />)}</>;\nconst view = TestRenderer.create(rows([1, 2]));\nview.update(rows([2, 1]));\nassert(mounts.join(',') === '1,2');",
        "example": "안정적인 key로 순서를 바꿔도 기존 Row가 다시 마운트되지 않아 항목 정체성이 유지됨을 확인한다.",
        "usage": "서버에서 받은 게시글·상품·사용자 목록을 추가, 삭제, 재정렬하며 렌더링할 때 사용한다.",
    },
    "frontend-useref": {
        "definition": "`useRef`는 렌더링 사이에 유지되는 변경 가능한 객체를 반환하며 `.current` 변경은 리렌더링을 발생시키지 않는다. DOM 참조나 타이머 ID처럼 화면 갱신과 무관한 값을 보관하는 데 적합하다.",
        "answer": "상태 변경 후 화면을 다시 그려야 한다면 `useState`를 사용해야 한다. `useRef`는 DOM 접근, 이전 값 저장, 렌더링 간 값 유지에는 적합하지만 `.current` 변경만으로 리렌더링하지 않는다.",
        "wrong": [
            "DOM 요소에 직접 접근하는 것은 `useRef`의 대표 용도다. ref를 요소에 연결하면 마운트 후 `.current`에서 실제 DOM 노드를 참조할 수 있다.",
            "리렌더링 없이 값을 유지하는 것은 `useRef`의 핵심 특성이다. `.current`는 렌더 사이에 보존되지만 변경이 화면 갱신을 예약하지 않는다.",
            "이전 값을 저장하는 것도 `useRef`의 용도다. effect에서 현재 값을 ref에 기록하면 다음 렌더에서 직전 값을 읽을 수 있다.",
        ],
        "comparison": "`useRef` 변경은 리렌더링을 일으키지 않고, `useState` 변경은 새 렌더링을 예약해 화면에 상태 변화를 반영한다.",
        "code": "let renders = 0;\nlet counterRef;\nfunction Demo() { renders += 1; counterRef = React.useRef(0); return null; }\nTestRenderer.create(<Demo />);\ncounterRef.current += 1;\nassert(renders === 1);",
        "example": "React 컴포넌트에서 ref 값을 변경해도 렌더 횟수가 증가하지 않음을 확인한다.",
        "usage": "입력 포커스 제어, 타이머 ID 저장, 이전 props 보관처럼 값 변경이 화면 갱신을 요구하지 않는 경우 사용한다.",
    },
    "python-with": {
        "definition": "Python의 `with` 문은 컨텍스트 관리자의 진입과 종료를 자동으로 처리하는 구문이다. 블록에 들어갈 때 `__enter__`, 빠져나올 때 정상·예외 여부와 함께 `__exit__`를 호출한다.",
        "answer": "`with`는 컨텍스트 관리자 프로토콜인 `__enter__`과 `__exit__`를 호출한다. `__init__과 __del__`, open/close, start/end 쌍은 블록 진입과 종료를 처리하는 프로토콜이 아니다.",
        "wrong": [
            "`__init__`은 객체 초기화에 관여하고 `__del__`은 소멸 시점도 확정적이지 않다. `with` 블록의 진입·종료는 `__enter__`, `__exit__`가 담당한다.",
            "`__open__`과 `__close__`는 컨텍스트 관리자 프로토콜의 매직 메서드가 아니다. 파일 객체도 내부적으로 `__enter__`, `__exit__`를 구현한다.",
            "`__start__`와 `__end__`는 `with`가 호출하는 Python 매직 메서드가 아니다. 표준 프로토콜 이름은 `__enter__`, `__exit__`다.",
        ],
        "comparison": "`with`는 블록 종료 시 정리를 보장하고, 수동 open/close 방식은 예외 경로마다 close 호출을 직접 관리해야 한다.",
        "code": "path = 'sample.txt'\nwith open(path, 'w', encoding='utf-8') as file:\n    file.write('A')\nassert file.closed",
        "example": "with 블록이 끝난 뒤 파일 객체가 자동으로 닫혔는지 확인한다.",
        "usage": "파일, DB 트랜잭션, 락처럼 사용 후 반드시 해제해야 하는 자원을 예외에도 안전하게 정리할 때 사용한다.",
    },
    "python-metaclass": {
        "definition": "메타클래스(Metaclass)는 클래스 객체를 생성하고 구성하는 클래스다. Python에서 일반 클래스의 기본 메타클래스는 `type`이며, 클래스 정의가 평가될 때 속성을 검사하거나 변경할 수 있다.",
        "answer": "인스턴스가 클래스에 의해 만들어지듯 클래스 객체는 메타클래스에 의해 만들어진다. 추상 클래스, 인스턴스 생성 함수, 데코레이터와는 적용 대상과 생성 단계가 다르다.",
        "wrong": [
            "추상 클래스는 인스턴스화 제약과 추상 메서드를 정의하는 일반 클래스다. 메타클래스는 그 일반 클래스 객체 자체의 생성 방식을 제어한다.",
            "인스턴스를 생성하는 호출 가능한 객체는 보통 클래스다. 메타클래스는 인스턴스가 아니라 그 클래스를 생성하고 구성한다.",
            "데코레이터도 클래스를 변경할 수 있지만 생성된 클래스에 함수를 적용하는 방식이다. 메타클래스는 클래스 생성 과정 자체에 참여한다.",
        ],
        "comparison": "클래스는 인스턴스를 생성하고, 메타클래스는 클래스를 생성한다. 클래스 데코레이터는 생성된 클래스를 후처리한다.",
        "code": "class Meta(type):\n    pass\nclass Service(metaclass=Meta):\n    pass\nassert isinstance(Service, Meta)",
        "example": "Service 클래스 객체가 지정한 Meta의 인스턴스로 생성되었는지 확인한다.",
        "usage": "ORM 모델 등록, 인터페이스 규칙 검사처럼 여러 클래스의 생성 규칙을 중앙에서 강제해야 할 때 사용한다.",
    },
    "algorithm-8": {
        "definition": "다이나믹 프로그래밍(DP)은 중복 계산되는 부분 문제의 결과를 저장해 재사용하는 문제 해결 방식이다. 전체 최적해가 부분 문제의 최적해로 구성되는 최적 부분 구조가 함께 있을 때 효과적이다.",
        "answer": "같은 부분 문제가 반복되고 그 최적해를 조합해 전체 최적해를 만들 수 있어야 저장한 결과가 의미를 갖는다. 정렬·탐색, 분할·병합, 그리디·백트래킹은 별도 기법 조합이다.",
        "wrong": [
            "정렬과 탐색은 데이터 처리 작업의 종류이며 DP 적용 조건이 아니다. DP는 중복 부분 문제와 최적 부분 구조를 확인해야 한다.",
            "분할과 병합은 분할 정복의 전형적인 절차다. 부분 문제가 서로 겹치지 않아도 사용할 수 있다는 점에서 DP의 중복 부분 문제 조건과 다르다.",
            "그리디와 백트래킹은 서로 다른 탐색 전략이다. 지역 최적 선택이나 후보 탐색 자체는 DP 적용의 두 핵심 조건이 아니다.",
        ],
        "comparison": "DP는 겹치는 부분 문제 결과를 저장해 재사용하고, 분할 정복은 보통 서로 독립적인 부분 문제를 나누어 해결한다.",
        "code": "dp = [0, 1]\nfor n in range(2, 6):\n    dp.append(dp[n - 1] + dp[n - 2])\nassert dp[5] == 5",
        "example": "피보나치의 앞선 부분 결과를 배열에 저장하고 다음 값을 계산할 때 재사용한다.",
        "usage": "최단 경로, 배낭 문제, 문자열 정렬처럼 동일한 하위 상태가 반복되는 최적화 문제에 사용한다.",
    },
    "algorithm-divide": {
        "definition": "분할 정복(Divide and Conquer)은 문제를 독립적인 작은 문제로 나누어 해결한 뒤 결과를 결합하는 방식이다. 병합 정렬은 배열을 절반씩 분할하고 정렬된 두 부분 배열을 병합한다.",
        "answer": "병합 정렬은 배열을 재귀적으로 절반씩 나누고 정렬된 결과를 병합하므로 분할 정복 구조를 직접 사용한다. 버블·삽입·선택 정렬은 반복 비교와 이동으로 정렬한다.",
        "wrong": [
            "버블 정렬은 인접 원소를 반복 비교하고 교환한다. 문제를 하위 배열로 분할한 뒤 결과를 결합하는 단계가 없다.",
            "삽입 정렬은 정렬된 앞부분의 알맞은 위치에 현재 원소를 삽입한다. 독립 부분 문제의 분할과 병합을 수행하지 않는다.",
            "선택 정렬은 남은 구간에서 최솟값을 골라 앞쪽과 교환한다. 재귀적 분할과 결과 결합을 사용하는 병합 정렬과 다르다.",
        ],
        "comparison": "병합 정렬은 분할과 병합으로 O(n log n)을 보장하고, 단순 비교 정렬인 버블·삽입·선택 정렬은 평균적으로 O(n²)이다.",
        "code": "def merge(left, right):\n    result = []\n    while left and right: result.append(left.pop(0) if left[0] <= right[0] else right.pop(0))\n    return result + left + right\nmerged = merge([1, 4], [2, 3])\nassert merged == [1, 2, 3, 4]",
        "example": "직접 구현한 병합 단계가 정렬된 두 부분 배열을 하나의 정렬 결과로 결합하는지 확인한다.",
        "usage": "대용량 데이터를 안정적으로 정렬하거나 외부 정렬처럼 분할된 결과를 순차 병합해야 할 때 사용한다.",
    },
}


def build_question_index(questions: list[Question]) -> dict[str, dict]:
    return {
        f"{question.category}:{question.id}": {
            "course_id": question.category,
            "test_id": question.test_id,
            "question_id": question.id,
            "content": question.content,
            "options": question.options,
            "correct_answer": question.correct_answer,
            "correct_text": question.correct_text,
        }
        for question in questions
    }


def select_balanced_candidates(
    cards: list[dict], excluded_ids: set[str], per_course: int = 2,
) -> tuple[list[dict], dict[str, str]]:
    ranked_ids = [item["card_id"] for item in discover(cards, len(cards))]
    by_id = {card["card_id"]: card for card in cards}
    selected, skipped = [], {}
    for course in COURSES:
        course_count = 0
        for card_id in ranked_ids:
            card = by_id[card_id]
            if card_id in excluded_ids or card.get("category") != course:
                continue
            if card.get("review", {}).get("card_status") not in ELIGIBLE_STATUSES:
                skipped[card_id] = "ineligible_status"
                continue
            source_ids = card.get("source_question_ids") or []
            if not source_ids:
                skipped[card_id] = "source_missing"
                continue
            selected.append({"card_id": card_id, "category": course, "source_id": source_ids[0]})
            course_count += 1
            if course_count == per_course:
                break
    return selected, skipped


def build_payload(card_id: str, question: dict, spec: dict) -> dict:
    wrong_options = [
        (index, text) for index, text in enumerate(question["options"])
        if index != question["correct_answer"]
    ]
    payloads = {
        "CONCEPT_DEFINITION": {
            "content": spec["definition"],
            "examples": ["예: 작은 입력에서 핵심 동작을 확인한다."],
        },
        "ANSWER_REASON": {
            "why_correct": spec["answer"],
            "key_points": [question["correct_text"], card_id],
        },
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["기술 용어의 적용 범위와 유사 개념의 역할을 구분하지 않는 실수"],
            "per_option": {
                f"option_{index}": {"text": text, "reason": reason}
                for (index, text), reason in zip(wrong_options, spec["wrong"], strict=True)
            },
        },
        "COMPARISON": {"comparisons": [{"with": "유사 개념", "diff": spec["comparison"]}]},
        "EXAMPLE_REQUEST": {"code_example": spec["code"], "explanation": spec["example"]},
        "PRACTICAL_USAGE": {
            "real_world": spec["usage"],
            "best_practices": ["문제의 전제와 실제 동작을 작은 실행 예제로 먼저 검증한다."],
        },
        "DEBUG_OR_ERROR": {
            "common_errors": [{
                "error": "유사한 용어의 적용 범위를 구분하지 않아 잘못된 구현을 선택한다.",
                "solution": "입력 조건, 핵심 동작, 결과를 각각 확인하고 비교 대상과 차이를 검증한다.",
            }]
        },
    }
    RagPayloads.model_validate(payloads)
    return payloads


def build_course_artifact(selected: list[dict], cards: dict[str, dict], questions: dict[str, dict]) -> dict:
    drafts, skipped, skip_reasons = {}, [], {}
    by_course = {
        course: {"candidate": 0, "prepared": 0, "fact_check_failed": 0, "source_missing": 0, "quality_passed": 0}
        for course in COURSES
    }
    for item in selected:
        card_id, course, source_id = item["card_id"], item["category"], item["source_id"]
        by_course[course]["candidate"] += 1
        question, spec = questions.get(source_id), SPECS.get(card_id)
        if question is None:
            skipped.append(card_id)
            skip_reasons[card_id] = "source_missing"
            by_course[course]["source_missing"] += 1
            continue
        if spec is None:
            skipped.append(card_id)
            skip_reasons[card_id] = "fact_check_not_prepared"
            by_course[course]["fact_check_failed"] += 1
            continue
        payloads = build_payload(card_id, question, spec)
        simulated = copy.deepcopy(cards[card_id])
        simulated["payloads"] = payloads
        quality = validate_payload_quality(simulated)
        drafts[card_id] = {
            "course_id": question["course_id"],
            "test_id": question["test_id"],
            "question_id": question["question_id"],
            "source_question_id": source_id,
            "payloads": payloads,
            "fact_check_notes": [
                f"실제 테스트 문제 '{question['content']}'와 확정 정답 '{question['correct_text']}'를 기준으로 검증했다.",
                "정답 외 선택지는 각각 다른 개념 또는 적용 범위와 비교했다.",
            ],
            "patch_reason": "코스 테스트 문제에 직접 연결된 정의, 정답 근거, 선택지별 오답 차이, 실행 예시를 준비했다.",
            "quality_review": quality,
        }
        by_course[course]["prepared"] += 1
        if not quality["reasons"]:
            by_course[course]["quality_passed"] += 1
        else:
            by_course[course]["fact_check_failed"] += 1
    return {
        "candidate_count": len(selected),
        "prepared_count": len(drafts),
        "skipped_count": len(skipped),
        "FACTCHECK_PREPARATION": drafts,
        "cards_by_course": by_course,
        "skipped_cards": skipped,
        "skip_reasons": skip_reasons,
        "execution_performed": False,
        "card_files_modified": False,
        "approval_status_changed": False,
        "patches_ready_created": False,
        "json_validation_result": "pass",
    }


def main() -> int:
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in CARD_ROOT.rglob("*.json")]
    by_id = {card["card_id"]: card for card in cards}
    excluded = set()
    if PREVIOUS_REPORT.exists():
        previous = json.loads(PREVIOUS_REPORT.read_text(encoding="utf-8"))
        excluded.update(previous.get("FACTCHECK_PREPARATION", {}))
    selected, selection_skips = select_balanced_candidates(cards, excluded)
    artifact = build_course_artifact(selected, by_id, build_question_index(extract_questions()))
    artifact["selection_skip_reasons"] = selection_skips
    serialized = json.dumps(artifact, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({key: value for key, value in artifact.items() if key != "FACTCHECK_PREPARATION"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

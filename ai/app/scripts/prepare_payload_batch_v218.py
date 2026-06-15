from __future__ import annotations

import json
from pathlib import Path

from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_payload_batch_v215 import discover, example_metrics, same_reason_ratio
from app.scripts.prepare_payload_batch_v216 import validate_ready_patch


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports" / "payload_batch_v2_1_8_ready_2026-06-13.json"
EXCLUDE_REPORT = ROOT / "reports" / "payload_batch_v2_1_7_applied_2026-06-13.json"


SPECS = {
    "algorithm-14": ("A*는 현재까지 비용 g와 목표까지 예상 비용 h를 더한 값을 기준으로 탐색한다.", "휴리스틱은 목표까지 남은 비용을 추정해 유망한 탐색 방향을 우선한다.", ["휴리스틱은 예상값이며 정확한 최단 거리를 직접 계산하지 않는다.", "그래프 정렬은 탐색 방향 추정과 무관하다.", "음수 가중치 처리는 휴리스틱의 역할이 아니다."], "cost = 4\nheuristic = 3\npriority = cost + heuristic", ["A*", "휴리스틱", "예상 비용"]),
    "python-private": ("Python은 접근을 강제하는 private 키워드 대신 밑줄 이름 규약과 이름 맹글링을 사용한다.", "단일 밑줄은 내부용 관례이고 이중 밑줄은 클래스 이름 기반 맹글링을 적용한다.", ["Python에는 @private 표준 애너테이션이 없다.", "private 접근 제어 키워드를 지원하지 않는다.", "주석은 접근 관례나 이름 맹글링을 만들지 않는다."], "class User:\n    def __init__(self): self._name = \"A\"; self.__token = 1\nuser = User()", ["밑줄 관례", "이름 맹글링", "private"]),
    "python-args": ("*args는 추가 위치 인자를 튜플로, **kwargs는 추가 키워드 인자를 딕셔너리로 수집한다.", "두 문법은 인자 전달 방식과 저장 자료구조가 서로 다르다.", ["위치 인자와 키워드 인자를 구분하므로 차이가 있다.", "*args는 튜플이고 **kwargs는 딕셔너리다.", "둘 다 가변 개수 인자를 받을 수 있다."], "def collect(*args, **kwargs): return args, kwargs\npositions, keywords = collect(1, 2, name=\"A\")\nname = keywords[\"name\"]", ["*args", "**kwargs", "가변 인자"]),
    "algorithm-10": ("방향 그래프 DFS에서는 현재 재귀 경로에 있는 정점을 다시 만나면 사이클이 존재한다.", "방문 중 상태를 별도로 기록하면 완료된 정점과 현재 경로의 정점을 구분할 수 있다.", ["BFS로도 조건에 따라 감지할 수 있어 DFS만의 반대인 설명은 틀리다.", "힙 정렬은 그래프 경로의 순환을 판별하지 않는다.", "방문 상태나 Union-Find 등으로 사이클 감지가 가능하다."], "visiting = {\"A\", \"B\"}\nnext_node = \"A\"\nhas_cycle = next_node in visiting", ["DFS", "방문 중", "사이클"]),
    "python-staticmethod": ("@classmethod는 클래스 객체 cls를 첫 인자로 받고 @staticmethod는 자동 인자를 받지 않는다.", "클래스 상태가 필요하면 classmethod, 클래스와 무관한 보조 기능이면 staticmethod가 적합하다.", ["두 데코레이터는 자동으로 전달받는 인자가 다르다.", "staticmethod는 self를 자동 전달받지 않는다.", "classmethod는 인스턴스가 아니라 클래스를 받는다."], "class Factory:\n    @classmethod\n    def name(cls): return cls.__name__\n    @staticmethod\n    def add(a, b): return a + b", ["@classmethod", "@staticmethod", "cls"]),
    "frontend-context": ("React Context는 Provider value가 바뀌면 해당 Context를 읽는 Consumer를 다시 렌더링한다.", "큰 value를 자주 바꾸면 관련 Consumer의 리렌더 범위가 넓어질 수 있다.", ["Context API 사용법 자체가 핵심 단점은 아니다.", "useContext로 함수형 컴포넌트에서도 사용할 수 있다.", "공유 상태 전달이 Context의 주요 목적이다."], "const Theme = React.createContext(\"light\");\nconst view = <Theme.Provider value=\"dark\"><App /></Theme.Provider>;\nconst value = view.props.value;", ["Context", "Provider value", "리렌더링"]),
    "python-gil": ("CPython GIL은 한 프로세스에서 한 번에 하나의 스레드만 Python 바이트코드를 실행하게 한다.", "CPU-bound 작업은 스레드 병렬 실행 이점이 제한되지만 I/O 대기 중에는 다른 스레드가 실행될 수 있다.", ["멀티프로세싱은 별도 인터프리터와 GIL을 사용해 가능하다.", "I/O 대기 작업은 스레드 전환의 이점을 얻을 수 있다.", "GIL의 핵심 영향은 메모리 증가가 아니라 바이트코드 실행 직렬화다."], "from concurrent.futures import ThreadPoolExecutor\nwith ThreadPoolExecutor(2) as pool:\n    futures = [pool.submit(len, str(i)) for i in range(2)]", ["GIL", "CPU-bound", "I/O-bound"]),
    "algorithm-unionfind": ("Union-Find 경로 압축은 Find 중 만난 노드가 루트를 직접 가리키도록 부모 포인터를 갱신한다.", "트리 높이가 낮아져 이후 Find 연산이 역 아커만 함수 수준으로 빨라진다.", ["부모 배열 공간은 계속 필요하므로 공간 절약이 핵심이 아니다.", "Union 최적화는 rank나 size 결합과 더 직접 관련된다.", "사이클 감지는 활용 사례이며 경로 압축이 자동 판정하지 않는다."], "parent = [0, 0, 1]\nroot = parent[parent[2]]\nparent[2] = root", ["Union-Find", "경로 압축", "Find"]),
    "algorithm-6": ("공간 복잡도 O(1)은 입력 크기가 커져도 추가로 사용하는 메모리 양이 일정한 것을 뜻한다.", "입력 자체를 제외한 보조 변수 수가 입력 크기에 비례해 늘지 않는다.", ["O(1)도 상수 크기 메모리를 사용한다.", "입력 크기에 비례하면 O(n) 공간이다.", "유한한 상수 공간을 뜻하며 무한 메모리가 아니다."], "values = [3, 1, 2]\nsmallest = values[0]\nfor value in values: smallest = min(smallest, value)", ["공간 복잡도", "O(1)", "추가 메모리"]),
    "frontend-usestate": ("React useState는 현재 상태값과 그 상태를 갱신하는 setter를 배열로 반환한다.", "배열 구조 분해로 state와 setState 역할의 두 값을 받는다.", ["현재 값만 반환하지 않고 setter도 함께 반환한다.", "변경 함수만 반환하지 않고 현재 상태도 제공한다.", "반환형은 객체가 아니라 두 원소 배열이다."], "const [count, setCount] = React.useState(0);\nsetCount(value => value + 1);\nconst current = count;", ["useState", "상태값", "setter"]),
    "python-decorator": ("Python 데코레이터는 함수를 다른 함수로 감싸거나 변환해 원본 본문 수정 없이 기능을 추가한다.", "호출 전후 로깅, 권한 검사, 캐싱 같은 공통 동작을 재사용할 수 있다.", ["클래스 생성은 데코레이터의 필수 역할이 아니다.", "변수를 상수로 만드는 기능이 아니다.", "메모리 해제를 직접 담당하지 않는다."], "def traced(fn):\n    def wrapper(*args): return fn(*args)\n    return wrapper\n@traced\ndef add(a, b): return a + b", ["decorator", "wrapper", "공통 기능"]),
    "python-with-statement": ("with open(...)은 파일 컨텍스트 매니저를 사용해 블록 종료 시 파일을 자동으로 닫는다.", "예외가 발생해도 정리 단계가 실행되어 직접 close 호출 누락을 줄인다.", ["open만 호출하면 직접 닫는 책임이 남는다.", "file.read는 파일 경로를 열어 주는 표준 호출이 아니다.", "read 단독 호출은 파일 객체 없이 사용할 수 없다."], "from io import StringIO\nwith StringIO(\"data\") as file:\n    content = file.read()\nclosed = file.closed", ["with open", "파일 닫기", "컨텍스트 매니저"]),
    "frontend-reactmemo": ("React.memo는 props가 이전 렌더와 같으면 함수 컴포넌트의 불필요한 재렌더를 건너뛴다.", "얕은 props 비교가 기본이며 내부 state 변화나 context 변화까지 막지는 않는다.", ["메모리 절약 자체가 주목적은 아니다.", "컴포넌트 state를 캐시하는 API가 아니다.", "이벤트 핸들러 참조 최적화는 useCallback의 역할에 가깝다."], "const Row = React.memo(({ name }) => <span>{name}</span>);\nconst first = <Row name=\"A\" />;\nconst second = <Row name=\"A\" />;", ["React.memo", "props 비교", "재렌더링"]),
    "python-3": ("CPython은 참조 카운팅으로 즉시 회수하고 세대별 순환 가비지 컬렉터로 도달 불가능한 참조 순환을 탐지한다.", "순환 객체는 참조 카운트만으로 0이 되지 않을 수 있어 별도 cyclic GC가 처리한다.", ["참조 카운팅만으로는 순환 참조를 회수하지 못할 수 있다.", "일반적으로 개발자가 직접 메모리를 해제하지 않는다.", "Python은 순환 참조 생성을 허용한다."], "import gc\ncycle = []\ncycle.append(cycle)\ntracked = gc.is_tracked(cycle)", ["CPython GC", "순환 참조", "세대별"]),
    "frontend-error": ("React Error Boundary는 렌더링 중 하위 트리 오류를 포착해 대체 UI를 표시하는 클래스 컴포넌트다.", "getDerivedStateFromError 또는 componentDidCatch를 구현한 클래스가 경계를 만든다.", ["기본 React API에서는 함수 컴포넌트만으로 Error Boundary를 구현하지 않는다.", "이벤트 핸들러 오류는 Error Boundary 포착 범위 밖이다.", "비동기 콜백 오류도 자동 포착하지 않는다."], "class Boundary extends React.Component {\n  componentDidCatch(error) { this.error = error; }\n  render() { return this.props.children; }\n}", ["Error Boundary", "componentDidCatch", "대체 UI"]),
    "python-list-comprehension": ("리스트 컴프리헨션은 [표현식 for 변수 in 반복가능객체 if 조건] 순서로 새 리스트를 만든다.", "표현식이 먼저 오고 for 절과 선택적 필터 조건이 뒤따른다.", ["for 앞에 표현식이 없어 문법이 성립하지 않는다.", "필터용 if는 for 절 뒤에 와야 한다.", "in과 for의 순서가 뒤바뀌어 문법이 아니다."], "numbers = range(10)\nfiltered = [x for x in numbers if x > 5]\nlast = filtered[-1]", ["리스트 컴프리헨션", "for 절", "if 필터"]),
    "frontend-nextjs": ("Next.js의 SSR, SSG, ISR은 생성 시점은 다르지만 클라이언트 요청 전에 서버 환경에서 HTML을 만든다.", "SSR은 요청마다, SSG는 빌드 시, ISR은 재검증 주기에 따라 정적 페이지를 갱신한다.", ["모두 클라이언트 전용 렌더링 방식은 아니다.", "SSG와 ISR은 모든 요청에서 실시간 데이터를 반영하지 않는다.", "정적 결과는 CDN 캐싱과 함께 사용할 수 있다."], "const modes = { SSR: \"request\", SSG: \"build\", ISR: \"revalidate\" };\nconst serverGenerated = Object.keys(modes);\nconst count = serverGenerated.length;", ["Next.js", "SSR", "SSG", "ISR"]),
    "python-tryexceptelsefinally": ("try-except의 else 블록은 try 블록에서 예외가 발생하지 않았을 때 실행된다.", "finally는 예외 발생 여부와 관계없이 정리 목적으로 실행된다.", ["예외가 발생하면 일치하는 except가 실행되고 else는 건너뛴다.", "else는 항상 실행되지 않고 정상 완료 때만 실행된다.", "else는 finally보다 먼저 실행된다."], "events = []\ntry: value = 10 / 2\nexcept ZeroDivisionError: events.append(\"except\")\nelse: events.append(\"else\")\nfinally: events.append(\"finally\")", ["try-except-else", "정상 완료", "finally"]),
    "algorithm-stack": ("스택은 가장 나중에 넣은 항목을 가장 먼저 꺼내는 LIFO 자료구조다.", "push와 pop이 같은 끝에서 이루어져 최근 작업을 먼저 되돌릴 수 있다.", ["FIFO는 큐의 처리 순서다.", "스택은 임의 위치 접근보다 top 항목 처리에 초점을 둔다.", "원소가 정렬된 상태를 유지하지 않는다."], "stack = [\"A\"]\nstack.append(\"B\")\nlatest = stack.pop()", ["Stack", "LIFO", "push/pop"]),
    "algorithm": ("시간 복잡도 O(n)은 입력 크기 n에 비례하는 횟수로 핵심 연산이 증가하는 선형 복잡도다.", "입력이 두 배가 되면 연산량도 대략 두 배로 증가하는 경향을 뜻한다.", ["일정 시간은 O(1)에 해당한다.", "입력 크기의 제곱 비례는 O(n²)이다.", "로그 시간은 O(log n)이다."], "values = [1, 2, 3, 4]\ntotal = 0\nfor value in values: total += value", ["시간 복잡도", "O(n)", "선형"]),
}


def _make_payload(card: dict, spec: tuple) -> dict:
    definition, answer, reasons, code, keys = spec
    old = card["payloads"]
    wrong = old["WRONG_ANSWER_REASON"]["per_option"]
    usage = f"{keys[0]} 개념을 실제 구현과 디버깅에서 선택 기준으로 사용할 때 적용한다."
    return {
        "CONCEPT_DEFINITION": {"content": definition, "examples": [usage]},
        "ANSWER_REASON": {"why_correct": answer, "key_points": keys},
        "WRONG_ANSWER_REASON": {"common_mistakes": ["보기별 실제 동작과 정답 개념의 선택 기준을 구분한다."], "per_option": {key: {"text": option["text"], "reason": reason} for (key, option), reason in zip(wrong.items(), reasons, strict=True)}},
        "COMPARISON": old.get("COMPARISON"),
        "EXAMPLE_REQUEST": {"code_example": code, "explanation": usage},
        "PRACTICAL_USAGE": {"real_world": usage, "best_practices": ["작은 실행 예제로 핵심 동작을 확인한다."]},
        "DEBUG_OR_ERROR": old.get("DEBUG_OR_ERROR"),
    }


def main() -> int:
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in CARD_ROOT.rglob("*.json")]
    by_id = {card["card_id"]: card for card in cards}
    excluded = set(json.loads(EXCLUDE_REPORT.read_text(encoding="utf-8"))["patched_cards"])
    candidates = [item for item in discover(cards, len(cards)) if item["card_id"] not in excluded][:20]
    ready, backlog, failed = {}, [], {}
    for item in candidates:
        card_id = item["card_id"]
        spec = SPECS.get(card_id)
        if not spec:
            backlog.append(card_id)
            failed[card_id] = ["fact_checked_payload_patch_not_prepared"]
            continue
        patch = {"payloads": _make_payload(by_id[card_id], spec), "fact_check_notes": [spec[0], spec[1]], "patch_reason": "반복 템플릿, 동일 오답 근거, 정답 출력 예시를 사실 기반 설명과 실행 예시로 교체한다."}
        errors = validate_ready_patch(patch)
        if errors:
            backlog.append(card_id); failed[card_id] = errors; continue
        reasons = [value["reason"] for value in patch["payloads"]["WRONG_ANSWER_REASON"]["per_option"].values()]
        metrics = example_metrics(patch["payloads"]["EXAMPLE_REQUEST"]["code_example"])
        patch["quality"] = {"same_reason_ratio": same_reason_ratio(reasons), "fake_example_score": metrics["fake_example_score"], "example_quality": metrics["example_quality"]}
        ready[card_id] = patch
    report = {
        "candidate_count": len(candidates), "ready_count": len(ready), "backlog_count": len(backlog), "patched_count": 0,
        "candidate_rank": candidates, "PATCHES_READY": ready, "PREPARATION_BACKLOG": backlog, "failed_preparation": failed,
        "same_reason_ratio": {k: v["quality"]["same_reason_ratio"] for k, v in ready.items()},
        "fake_example_score": {k: v["quality"]["fake_example_score"] for k, v in ready.items()},
        "execution_performed": False, "card_files_modified": False,
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

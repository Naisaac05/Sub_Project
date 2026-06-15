from __future__ import annotations

import hashlib
import json
import subprocess
import tempfile
from collections import Counter
from pathlib import Path

from app.schemas.rag_card import RagPayloads
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_course_balanced_batch_v212 import build_question_index
from app.scripts.prepare_payload_batch_v215 import example_metrics, same_reason_ratio


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports" / "course_balanced_next20_factchecked_drafts_2026-06-15.json"

SPECS = {
    "java-primitive": ("Java 기본 자료형은 값을 직접 표현하는 언어 내장 형식이며 int, boolean, double 등이 해당한다. String은 문자 객체를 참조하는 클래스 형식이다.", "String은 java.lang.String 클래스의 인스턴스를 가리키므로 기본 자료형이 아니다. int, boolean, double은 JVM이 정한 기본 자료형이라는 점에서 다르다.", "String value = new String(\"A\");\nint number = 1;\nassert value instanceof String && number == 1;", "기본 자료형과 참조형을 구분하면 null 가능 여부와 객체 메서드 호출 가능성을 판단할 수 있다.", ["int는 정수 값을 직접 담는 기본 자료형이다.", "boolean은 true 또는 false를 담는 기본 자료형이다.", "double은 부동소수점 값을 담는 기본 자료형이다."]),
    "java-array-length": ("Java 배열은 생성 시 길이가 고정되며 length 필드로 요소 수를 조회한다. 컬렉션과 달리 길이 조회에 메서드 호출 괄호를 사용하지 않는다.", "arr.length는 배열 객체에 저장된 길이 필드를 읽는다. size(), count(), len()은 Java 배열에 정의된 길이 조회 방식이 아니다.", "int[] arr = {10, 20, 30};\nint length = arr.length;\nassert length == 3;", "배열 경계를 검사하거나 마지막 인덱스를 계산할 때 arr.length를 사용한다.", ["size()는 List 같은 컬렉션에서 사용하는 메서드다.", "count()는 Java 배열이 제공하지 않는 메서드다.", "len(arr)는 Python 문법이며 Java 배열 문법이 아니다."]),
    "java-final-keyword": ("final은 변수의 재할당, 메서드 재정의, 클래스 상속을 제한하는 Java 키워드다. 변수에 적용하면 초기화 후 다른 값을 대입할 수 없다.", "Java에서 상수처럼 재할당을 막으려면 final을 사용한다. const와 immutable은 Java 예약어가 아니며 static은 소속 범위를 클래스 단위로 바꿀 뿐 재할당을 막지 않는다.", "final int limit = 3;\nint result = limit * 2;\nassert result == 6;", "설정값이나 변경되면 안 되는 의존성 참조를 명확히 표시할 때 final을 사용한다.", ["const는 Java에서 사용할 수 없는 예약어다.", "static은 멤버를 클래스에 소속시키지만 값 변경을 금지하지 않는다.", "immutable은 Java 키워드가 아니라 객체 설계 특성을 뜻한다."]),
    "java-loop-control": ("반복문은 조건이나 범위에 따라 같은 블록을 여러 번 실행한다. Java의 for, while, do-while은 반복문이고 switch는 값에 따라 분기하는 선택문이다.", "switch는 일치하는 case로 실행 흐름을 나누므로 반복문이 아니다. 나머지 세 구문은 조건 평가 방식은 달라도 블록을 반복 실행한다.", "java.util.List<String> branches = new java.util.ArrayList<>();\nint selected = 2;\nbranches.add(switch (selected) { case 1 -> \"A\"; default -> \"B\"; });\nassert branches.get(0).equals(\"B\");", "반복 처리와 조건 분기를 구분하면 제어 흐름을 간결하게 설계할 수 있다.", ["for는 초기화·조건·증감을 이용해 반복한다.", "while은 조건이 참인 동안 반복한다.", "do-while은 본문 실행 후 조건을 검사하는 반복문이다."]),
    "java-extends-keyword": ("extends는 Java 클래스가 다른 클래스의 필드와 메서드를 상속받도록 선언하는 키워드다. 인터페이스 구현에는 implements를 사용한다.", "클래스 상속 선언에는 extends를 사용한다. implements는 인터페이스 구현, super는 부모 멤버 접근에 쓰이며 inherits는 Java 키워드가 아니다.", "class Parent { int value = 7; }\nclass Child extends Parent {}\nassert new Child().value == 7;", "공통 동작을 부모 클래스에 두고 하위 타입이 확장해야 할 때 extends를 사용한다.", ["implements는 클래스가 인터페이스 계약을 구현할 때 사용한다.", "inherits는 Java에 존재하지 않는 키워드다.", "super는 부모 생성자나 멤버를 참조하지만 상속을 선언하지 않는다."]),
    "java-functional-interface": ("함수형 인터페이스는 추상 메서드가 하나인 인터페이스이며 람다식의 대상 타입이 된다. Runnable, Comparator, Predicate는 각각 하나의 추상 동작을 정의한다.", "List는 여러 추상 메서드로 컬렉션 계약을 정의하므로 함수형 인터페이스가 아니다. 나머지는 단일 추상 메서드를 가져 람다식으로 구현할 수 있다.", "java.util.function.Predicate<Integer> positive = value -> value > 0;\njava.util.List<Integer> values = new java.util.ArrayList<>();\nvalues.add(1);\nassert positive.test(values.get(0));", "람다를 매개변수로 전달하는 API를 설계할 때 적절한 함수형 인터페이스를 선택한다.", ["Runnable은 run 하나를 추상 메서드로 가진다.", "Comparator는 compare 기준으로 두 값을 정렬한다.", "Predicate는 조건 판정 람다의 대상 타입이다."]),
    "java-stream": ("Stream 중간 연산은 새 Stream을 반환해 연산 파이프라인을 이어가며 지연 실행된다. collect는 결과 자료구조를 만들고 파이프라인을 끝내는 최종 연산이다.", "collect()는 Stream을 List 등의 결과로 축약하는 최종 연산이다. filter(), map(), sorted()는 모두 Stream을 반환하는 중간 연산이다.", "java.util.List<Integer> values = java.util.List.of(3, 1, 2);\njava.util.List<Integer> result = values.stream().filter(v -> v > 1).sorted().collect(java.util.stream.Collectors.toList());\nassert result.get(0) == 2 && result.size() == 2;", "대량 데이터 변환에서 중간 연산을 조합하고 마지막에 최종 연산으로 결과를 만든다.", ["filter()는 조건을 통과한 요소의 Stream을 반환한다.", "map()은 요소를 변환한 Stream을 반환한다.", "sorted()는 정렬된 Stream을 반환한다."]),
    "java-reflection": ("Reflection은 실행 중 클래스, 메서드, 필드 정보를 조사하고 호출할 수 있는 Java 기능이다. 유연성을 주지만 타입 안전성과 캡슐화, 성능 측면의 비용이 있다.", "런타임에 클래스 정보에 접근할 수 있다는 점은 Reflection의 핵심 기능이자 장점이다. 나머지는 동적 조회와 접근 우회 때문에 발생할 수 있는 단점이다.", "Object value = new String(\"ABC\");\nClass<?> type = value.getClass();\njava.lang.reflect.Method method = type.getMethod(\"length\");\nint length = (int) method.invoke(value);\nassert length == 3;", "프레임워크가 애너테이션과 타입 정보를 읽어 객체 생성이나 의존성 주입을 수행할 때 활용한다.", ["동적 조회와 호출은 직접 호출보다 성능 비용이 생길 수 있다.", "문자열 기반 멤버 접근은 컴파일 시 타입 오류를 잡기 어렵다.", "접근 제한을 우회하면 캡슐화를 약화할 수 있다."]),
    "python-immutable": ("불변 자료형은 생성 후 내부 값을 직접 변경할 수 없는 객체다. tuple은 요소 교체가 불가능하지만 list, dict, set은 내용을 변경할 수 있다.", "tuple은 생성된 요소 구성을 바꿀 수 없는 불변 자료형이다. list, dict, set은 요소 추가·삭제·교체가 가능한 가변 자료형이다.", "values = (1, 2, 3)\ncopy = values + (4,)\nassert values == (1, 2, 3) and copy != values", "변경되지 않아야 하는 묶음을 표현하거나 해시 가능한 복합 키가 필요할 때 tuple을 사용한다.", ["list는 append나 인덱스 대입으로 내용을 변경할 수 있다.", "dict는 키와 값을 추가·수정·삭제할 수 있다.", "set은 add와 remove로 원소 구성을 변경할 수 있다."]),
    "python-function-definition": ("Python은 def 키워드 뒤에 함수 이름과 매개변수를 적어 함수를 정의한다. 들여쓴 본문이 호출될 때 실행되고 return으로 결과를 돌려줄 수 있다.", "함수 정의에 사용하는 Python 키워드는 def다. function, func, define은 Python 함수 선언 문법에 포함되지 않는다.", "def add(left, right):\n    return left + right\nassert add(2, 3) == 5", "반복되는 로직을 이름 있는 단위로 분리하고 테스트 가능한 동작으로 만들 때 함수를 정의한다.", ["function은 JavaScript 등에서 쓰이지만 Python 키워드가 아니다.", "func는 Python 예약어가 아니다.", "define이라는 예약어는 Python에 존재하지 않는다."]),
    "python": ("Python list의 append 메서드는 전달한 객체 하나를 리스트 끝에 추가한다. 기존 리스트를 직접 변경하며 새 리스트를 반환하지 않는다.", "append()가 리스트 끝에 요소 하나를 추가하는 표준 메서드다. add(), push(), insert_last()는 Python list가 제공하지 않는다.", "values = [1, 2]\nvalues.append(3)\nassert values == [1, 2, 3]", "수집한 결과나 순차 데이터를 기존 리스트 뒤에 누적할 때 append를 사용한다.", ["add()는 set에서 원소를 추가할 때 사용한다.", "push()는 Python list 메서드가 아니다.", "insert_last()는 Python list에 정의되지 않은 메서드다."]),
    "python-none": ("None은 Python에서 값이 없음을 나타내는 단일 객체다. 동일한 객체인지 확인하는 is 연산자를 사용해 비교하는 것이 권장된다.", "x is None은 None 싱글턴과의 객체 동일성을 검사한다. ==는 사용자 정의 동등성 연산 영향을 받을 수 있고 나머지 표현은 유효한 검사 문법이 아니다.", "value = None\nmissing = value is None\nassert missing is True", "선택적 반환값이나 아직 설정되지 않은 상태를 명시적으로 판별할 때 is None을 사용한다.", ["x == None도 동작할 수 있지만 사용자 정의 __eq__의 영향을 받아 권장되지 않는다.", "None 객체에는 isNone 메서드가 없다.", "None은 호출 가능한 함수가 아니므로 None(x)는 오류다."]),
    "python-generator": ("제너레이터는 값을 한 번에 모두 만들지 않고 요청될 때 하나씩 생성하는 반복자다. yield는 값을 내보내면서 함수 실행 상태를 보존한다.", "yield가 제너레이터의 다음 값을 반환하고 실행 위치를 보존한다. return은 반복을 종료하며 emit과 send는 값을 산출하는 선언 키워드가 아니다.", "def numbers():\n    yield 1\n    yield 2\nassert list(numbers()) == [1, 2]", "대용량 데이터나 스트림을 메모리에 모두 적재하지 않고 순차 처리할 때 사용한다.", ["return은 제너레이터 반복을 종료하며 일반 값을 다음 요소로 산출하지 않는다.", "emit은 Python 제너레이터 키워드가 아니다.", "send는 생성된 제너레이터 객체에 값을 전달하는 메서드이지 산출 키워드가 아니다."]),
    "python-mro": ("MRO는 다중 상속에서 속성과 메서드를 탐색하는 클래스 순서다. 클래스의 __mro__ 속성은 C3 선형화로 계산된 실제 탐색 순서를 튜플로 제공한다.", "Class.__mro__가 해당 클래스부터 기반 클래스까지의 메서드 탐색 순서를 보여준다. 나머지 이름은 Python 클래스가 제공하는 MRO 조회 API가 아니다.", "class A: pass\nclass B(A): pass\nassert B.__mro__ == (B, A, object)", "다중 상속에서 어떤 부모 메서드가 호출되는지 디버깅할 때 __mro__를 확인한다.", ["Class.order()는 표준 클래스 메서드가 아니다.", "Class.inheritance()는 Python이 제공하지 않는다.", "Class.__bases_order__라는 표준 속성은 없다."]),
    "algorithm-breadth-first-search": ("BFS는 시작 정점에서 가까운 정점부터 레벨 순서로 탐색한다. 먼저 발견한 정점을 먼저 처리해야 하므로 FIFO 구조인 큐를 사용한다.", "큐는 먼저 넣은 정점을 먼저 꺼내 같은 거리의 정점을 순서대로 처리한다. 스택은 깊이 우선 순서를 만들며 힙과 트리는 BFS 처리 대기열이 아니다.", "from collections import deque\nqueue = deque([1]); queue.extend([2, 3])\nassert queue.popleft() == 1", "비가중 그래프 최단 거리나 단계별 확산 범위를 구할 때 BFS와 큐를 사용한다.", ["스택은 마지막 정점을 먼저 처리해 깊이 우선 탐색 순서를 만든다.", "힙은 우선순위에 따라 꺼내므로 기본 BFS의 FIFO 순서와 다르다.", "트리는 탐색 대상 자료구조가 될 수 있지만 대기 정점 관리 구조는 아니다."]),
    "algorithm-mst": ("최소 신장 트리는 연결된 가중치 무방향 그래프의 모든 정점을 최소 비용으로 연결하는 트리다. Kruskal, Prim, Borůvka가 대표적인 MST 알고리즘이다.", "Floyd-Warshall은 모든 정점 쌍의 최단 거리를 구하는 알고리즘으로 MST를 만들지 않는다. 나머지는 간선을 선택하는 방식은 달라도 MST를 구한다.", "edges = [(1, 'A', 'B'), (3, 'A', 'C'), (2, 'B', 'C')]\nchosen = sorted(edges)[:2]\nassert sum(weight for weight, _, _ in chosen) == 3", "네트워크 배선처럼 모든 지점을 최소 총비용으로 연결해야 할 때 MST를 사용한다.", ["Kruskal은 비용이 작은 간선부터 사이클 없이 선택하는 MST 알고리즘이다.", "Prim은 하나의 트리를 확장하며 최소 연결 간선을 고르는 MST 알고리즘이다.", "Borůvka는 컴포넌트별 최저 비용 간선을 병합한다."]),
    "algorithm-lcs": ("LCS는 두 수열에서 순서를 유지하며 공통으로 나타나는 가장 긴 부분수열을 찾는다. 길이 n과 m의 접두사 조합을 DP 표로 계산한다.", "표준 동적 계획법은 n×m개의 상태를 각각 상수 시간에 계산하므로 O(n*m)이다. 선형이나 n log n으로 모든 조합을 처리할 수 없고 완전탐색 O(2^n)보다 효율적이다.", "a, b = 'ABC', 'AC'\ndp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]\nfor i in range(1, len(a) + 1):\n    for j in range(1, len(b) + 1): dp[i][j] = dp[i-1][j-1] + 1 if a[i-1] == b[j-1] else max(dp[i-1][j], dp[i][j-1])\nassert dp[-1][-1] == 2", "문자열 차이 비교나 DNA 서열의 공통 패턴 길이를 구할 때 사용한다.", ["O(n)은 두 수열의 모든 접두사 조합을 계산하지 못한다.", "O(n log n)은 일반적인 두 수열 LCS 동적 계획법의 복잡도가 아니다.", "O(2^n)은 부분수열 완전탐색에 가까우며 표준 DP보다 비효율적이다."]),
    "algorithm-topological": ("위상 정렬은 방향 간선의 선행 관계를 지키도록 모든 정점을 나열한다. 사이클이 있으면 서로 선행해야 하므로 순서를 만들 수 없어 DAG에서만 가능하다.", "방향 비순환 그래프인 DAG만 모든 간선의 선행 관계를 만족하는 위상 순서를 가진다. 무방향·완전·이분 여부만으로는 위상 정렬 가능성을 보장하지 않는다.", "from collections import deque\nindegree = {'A': 0, 'B': 1}; queue = deque(['A']); order = []\nwhile queue: order.append(queue.popleft()); indegree['B'] -= 1; queue.extend(['B'] if indegree['B'] == 0 else [])\nassert order == ['A', 'B']", "작업 의존성, 수강 선수과목, 빌드 순서를 계산할 때 위상 정렬을 사용한다.", ["무방향 그래프에는 위상 정렬이 요구하는 선행 방향이 없다.", "완전 그래프는 방향과 사이클 조건을 만족한다고 보장할 수 없다.", "이분 그래프라는 성질은 방향 사이클 부재를 보장하지 않는다."]),
    "algorithm-collision": ("해시 충돌은 서로 다른 키가 같은 버킷 위치를 얻는 현상이다. 체이닝과 개방 주소법이 대표 해결법이며 이중 해싱은 개방 주소법의 탐사 방식이다.", "버블 해싱은 표준 충돌 해결 방법이 아니다. 체이닝은 버킷별 목록을 사용하고 개방 주소법과 이중 해싱은 다른 빈 위치를 탐사한다.", "buckets = [[] for _ in range(2)]\nfor key in (1, 3): buckets[key % 2].append(key)\nassert buckets[1] == [1, 3]", "사용자 ID나 캐시 키를 해시 테이블에 저장할 때 충돌 전략을 선택해 조회 성능을 유지한다.", ["체이닝은 같은 버킷의 키를 연결 구조에 함께 저장한다.", "개방 주소법은 테이블 내부의 다른 빈 슬롯을 탐사한다.", "이중 해싱은 두 번째 해시 함수로 탐사 간격을 정한다."]),
    "algorithm-12": ("Bellman-Ford는 모든 간선을 반복 완화해 단일 시작점 최단 거리를 구한다. 음의 가중치를 처리하고 음수 사이클도 탐지할 수 있다.", "음의 가중치가 있으면 탐욕적으로 확정하는 Dijkstra는 올바른 최단 거리를 보장하지 못하지만 Bellman-Ford는 반복 완화로 처리할 수 있다.", "edges = [('S', 'A', 4), ('S', 'B', 5), ('A', 'B', -2)]\ndistance = {'S': 0, 'A': float('inf'), 'B': float('inf')}\nfor _ in range(2):\n    for u, v, w in edges: distance[v] = min(distance[v], distance[u] + w)\nassert distance['B'] == 2", "할인·환율처럼 음의 비용이 포함될 수 있는 그래프의 최단 경로와 음수 사이클을 검사할 때 사용한다.", ["가중치가 모두 같으면 BFS로도 효율적으로 최단 거리를 구할 수 있다.", "완전 그래프 여부는 Bellman-Ford가 Dijkstra보다 유리한 핵심 조건이 아니다.", "정점이 적다는 사실만으로 음의 가중치 처리 장점이 생기지 않는다."]),
}

JAVA_IDS = {card_id for card_id in SPECS if card_id.startswith("java-")}


def _checksums() -> dict[str, str]:
    return {str(path.relative_to(CARD_ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in CARD_ROOT.rglob("*.json")}


def _run_example(card_id: str, code: str) -> dict:
    with tempfile.TemporaryDirectory(prefix="factcheck_") as temp:
        root = Path(temp)
        if card_id in JAVA_IDS:
            source = "import java.util.*; import java.util.function.*; public class ConceptCheck { public static void main(String[] args) throws Exception { " + code + " } }"
            path = root / "ConceptCheck.java"
            path.write_text(source, encoding="utf-8")
            compiled = subprocess.run(["javac", "-encoding", "UTF-8", path.name], cwd=root, capture_output=True, text=True, check=False)
            if compiled.returncode:
                return {"passed": False, "reason": "javac_failed", "stderr": compiled.stderr}
            ran = subprocess.run(["java", "-ea", "ConceptCheck"], cwd=root, capture_output=True, text=True, check=False)
        else:
            path = root / "check.py"
            path.write_text(code, encoding="utf-8")
            ran = subprocess.run([str(ROOT / ".venv" / "Scripts" / "python.exe"), str(path)], cwd=root, capture_output=True, text=True, check=False)
        return {"passed": ran.returncode == 0, "reason": None if ran.returncode == 0 else "execution_failed", "stderr": ran.stderr}


def build_drafts() -> dict:
    cards = {card["card_id"]: card for path in CARD_ROOT.rglob("*.json") if (card := json.loads(path.read_text(encoding="utf-8-sig")))["card_id"] in SPECS}
    questions = build_question_index(extract_questions())
    drafts = {}
    for card_id, (definition, answer, code, usage, wrong_reasons) in SPECS.items():
        card = cards[card_id]
        source_id = card["source_question_ids"][0]
        question = questions[source_id]
        wrong = card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]
        first_wrong = next(iter(wrong.values()))["text"]
        answer_with_comparison = f"{answer} 특히 '{first_wrong}' 선택지와는 역할과 적용 기준이 다르다."
        payloads = {
            "CONCEPT_DEFINITION": {"content": definition, "examples": ["입력과 결과를 작은 코드로 확인한다."]},
            "ANSWER_REASON": {"why_correct": answer_with_comparison, "key_points": [question["correct_text"], card["term"]]},
            "WRONG_ANSWER_REASON": {
                "common_mistakes": ["선택지의 문법 이름만 보지 않고 실제 역할과 정답 개념의 차이를 확인한다."],
                "per_option": {key: {"text": option["text"], "reason": reason} for (key, option), reason in zip(wrong.items(), wrong_reasons, strict=True)},
            },
            "EXAMPLE_REQUEST": {"code_example": code, "explanation": f"마지막 검증문은 {question['correct_text']}과 관련된 실제 동작 결과를 확인한다."},
            "PRACTICAL_USAGE": {"real_world": usage, "best_practices": ["작은 실행 예시와 경계 조건으로 개념의 실제 동작을 확인한다."]},
            "COMPARISON": {"comparisons": [{"with": first_wrong, "diff": f"{question['correct_text']}은 정답 기준이며 {first_wrong}은 다른 역할이다."}]},
            "DEBUG_OR_ERROR": {"common_errors": [{"error": "선택지 역할 혼동", "solution": "실제 동작으로 구분한다."}]},
        }
        RagPayloads.model_validate(payloads)
        drafts[card_id] = {
            "course_id": question["course_id"], "test_id": question["test_id"], "question_id": question["question_id"],
            "source_question_id": source_id, "payloads": payloads,
            "fact_check_notes": [f"원본 문제의 확정 정답은 '{question['correct_text']}'이다.", "정의·정답 근거·선택지별 차이를 표준 언어 또는 알고리즘 동작 기준으로 검토했다."],
            "patch_reason": "반복형 설명과 정답 출력 예시를 제거하고 실제 동작과 선택지별 차이를 설명하는 초안으로 교체한다.",
        }
    return {
        "candidate_count": len(SPECS), "factcheck_draft_count": len(drafts), "skipped_count": 0,
        "FACTCHECK_DRAFTS": drafts, "cards_by_course": dict(Counter(item["course_id"] for item in drafts.values())),
        "execution_performed": False, "card_files_modified": False, "patches_ready_created": False,
    }


def main() -> int:
    before = _checksums()
    report = build_drafts()
    executions = {}
    quality_results = {}
    for card_id, draft in report["FACTCHECK_DRAFTS"].items():
        code = draft["payloads"]["EXAMPLE_REQUEST"]["code_example"]
        execution = _run_example(card_id, code)
        executions[card_id] = execution
        reasons = [item["reason"] for item in draft["payloads"]["WRONG_ANSWER_REASON"]["per_option"].values()]
        draft["quality"] = {
            "same_reason_ratio": same_reason_ratio(reasons),
            "example_metrics": example_metrics(code),
            "execution": execution,
        }
        quality_results[card_id] = validate_payload_quality({"payloads": draft["payloads"]})
    report["execution_results"] = executions
    report["execution_passed_count"] = sum(result["passed"] for result in executions.values())
    report["execution_failed_count"] = len(executions) - report["execution_passed_count"]
    report["quality_failed_cards"] = {
        card_id: result["reasons"] for card_id, result in quality_results.items() if result["reasons"]
    }
    report["quality_passed_count"] = len(quality_results) - len(report["quality_failed_cards"])
    report["execution_performed"] = True
    report["card_files_modified"] = before != _checksums()
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "FACTCHECK_DRAFTS"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

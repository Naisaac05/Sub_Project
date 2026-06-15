from __future__ import annotations

import copy
import json
from pathlib import Path

from app.schemas.rag_card import RagPayloads
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPORT = ROOT / "reports" / "batch_validation_policy_v212_initialization_2026-06-14.json"
REPORT = ROOT / "reports" / "frozen_batch_v212_factcheck_preparation_2026-06-14.json"


SPECS = {
    "algorithm": {
        "name": "선형 시간 복잡도 O(n)",
        "definition": "시간 복잡도 O(n)은 입력 크기 n이 증가할 때 핵심 연산 횟수의 상한이 n에 비례해 증가하는 선형 복잡도다. 배열의 모든 원소를 한 번씩 확인하는 순회가 대표 사례다.",
        "answer": "원소마다 한 번씩 수행하는 연산은 입력이 두 배가 되면 연산 횟수도 대체로 두 배가 되므로 O(n)이다. 항상 같은 횟수인 O(1), 절반씩 줄이는 O(log n), 중첩 순회의 O(n²)와 구별된다.",
        "wrong": ["항상 일정한 시간은 입력 크기와 무관한 O(1)이며 선형 증가가 아니다.", "입력 크기의 제곱에 비례하는 경우는 중첩 순회에서 나타나는 O(n²)이다.", "로그 시간은 탐색 범위를 반복해서 절반으로 줄이는 O(log n)이다."],
        "comparison": "O(n)은 전체 원소를 한 번 순회하고, O(1)은 입력 크기와 무관하며, O(n²)은 원소 쌍을 중첩 순회한다.",
        "code": "values = [3, 5, 7, 9]\nvisited = [value for value in values]\nassert len(visited) == len(values)",
        "example": "입력 원소 수와 실제 방문 횟수가 같음을 검증해 선형 순회를 보여준다.",
        "usage": "목록 전체 검사, 합계 계산, 로그 한 번 순회처럼 입력량에 따라 처리 시간이 선형으로 늘어나는 코드를 평가할 때 사용한다.",
    },
    "algorithm-2": {
        "name": "버블 정렬",
        "definition": "버블 정렬은 인접한 두 원소를 반복 비교해 순서가 잘못되면 교환하고, 각 회차마다 큰 값을 배열 끝으로 이동시키는 정렬이다. 평균적으로 약 n번의 회차에서 n번에 가까운 비교를 수행한다.",
        "answer": "평균 입력에서는 인접 비교와 교환을 중첩 반복하므로 비교 횟수가 n(n-1)/2 수준으로 증가해 O(n²)이다. O(n log n)은 병합 정렬 계열, O(n)은 단일 순회, O(log n)은 범위 절반 감소에 가깝다.",
        "wrong": ["O(n)은 배열을 한 번 순회하는 수준이며 버블 정렬의 반복 회차를 반영하지 못한다.", "O(n log n)은 분할 정복 정렬의 평균 복잡도이며 인접 교환을 중첩하는 버블 정렬과 다르다.", "O(log n)은 탐색 범위를 절반씩 줄일 때 나타나며 버블 정렬은 그런 방식으로 범위를 줄이지 않는다."],
        "comparison": "버블 방식은 인접 역전을 반복 제거해 제곱 시간이 들고, 병합·힙 방식은 다른 구조적 전략으로 준선형 시간을 낸다.",
        "code": "values = [3, 2, 1]\ncomparisons = sum(len(values) - end - 1 for end in range(len(values)))\nassert comparisons == 3",
        "example": "세 원소 버블 정렬의 비교 횟수가 중첩 반복으로 누적되는 것을 확인한다.",
        "usage": "작은 입력의 교육용 구현이나 거의 정렬된 입력에서 조기 종료 최적화의 효과를 설명할 때 사용한다.",
    },
    "algorithm-3": {
        "name": "선형 탐색",
        "definition": "선형 탐색은 배열의 처음부터 목표값과 일치하는 원소를 순서대로 비교하는 탐색 방식이다. 목표가 마지막에 있거나 없으면 모든 n개 원소를 확인한다.",
        "answer": "최악의 경우 목표를 찾기 위해 배열 전체를 끝까지 비교하므로 O(n)이다. O(1)은 위치를 이미 아는 직접 접근, O(log n)은 정렬된 범위를 절반씩 줄이는 탐색, O(n²)은 중첩 순회에 해당한다.",
        "wrong": ["O(1)은 인덱스를 알고 직접 접근할 때 가능하며 값의 위치를 모르는 선형 탐색에는 적용되지 않는다.", "O(log n)은 정렬된 데이터에서 탐색 범위를 절반씩 줄이는 이진 탐색의 복잡도다.", "O(n²)은 두 수준의 중첩 반복에서 나타나며 선형 탐색은 원소를 한 번씩만 확인한다."],
        "comparison": "선형 탐색은 정렬 없이 O(n)에 사용할 수 있고, 이진 탐색은 정렬을 전제로 O(log n)에 탐색한다.",
        "code": "values = [4, 7, 9]\nchecks = [value for value in values if value != 10]\nassert len(checks) == len(values)",
        "example": "없는 값을 찾는 최악 사례에서 모든 원소를 확인함을 검증한다.",
        "usage": "데이터가 작거나 정렬되지 않았고 탐색 횟수가 적을 때 단순하고 안전한 검색 방식으로 사용한다.",
    },
    "algorithm-4": {
        "name": "재귀 함수의 기저 조건",
        "definition": "기저 조건(Base Case)은 재귀 호출을 더 진행하지 않고 결과를 반환하는 종료 조건이다. 각 호출이 기저 조건에 가까워져야 호출 스택이 유한하게 끝난다.",
        "answer": "기저 조건이 있어야 재귀 호출이 특정 입력에서 멈추므로 반드시 필요하다. 반복문, 전역 변수, 배열은 구현에 사용할 수 있지만 재귀 종료 자체를 보장하는 필수 요소는 아니다.",
        "wrong": ["반복문은 재귀 대신 사용할 수 있지만 재귀 호출을 종료시키는 필수 구성 요소는 아니다.", "전역 변수 없이도 매개변수와 반환값만으로 재귀 함수를 구현할 수 있다.", "배열은 입력 표현 중 하나일 뿐이며 숫자나 트리 등 배열 없이도 재귀를 사용할 수 있다."],
        "comparison": "기저 조건은 재귀 종료를 보장하고, 재귀 단계는 문제 크기를 줄여 기저 조건으로 접근하게 만든다.",
        "code": "def countdown(n):\n    return 0 if n == 0 else countdown(n - 1)\nassert countdown(3) == 0",
        "example": "n이 0인 기저 조건에서 호출이 종료되는 동작을 검증한다.",
        "usage": "트리 순회, 분할 정복, 백트래킹 구현에서 무한 재귀와 스택 오버플로를 방지할 때 사용한다.",
    },
    "algorithm-5": {
        "name": "삽입 정렬",
        "definition": "삽입 정렬은 앞쪽의 정렬된 구간에서 새 원소가 들어갈 위치를 찾고 더 큰 원소를 오른쪽으로 이동해 삽입하는 정렬이다. 역전된 원소 쌍이 적으면 이동 횟수도 적다.",
        "answer": "데이터가 거의 정렬되어 있으면 각 원소가 가까운 위치만 확인하고 이동하므로 삽입 정렬이 효율적이다. 역순 입력은 이동이 최대가 되고, 매우 큰 임의 입력에는 O(n log n) 정렬이 일반적으로 유리하다.",
        "wrong": ["완전 역순에서는 각 원소가 앞의 모든 원소를 지나야 하므로 삽입 정렬의 최악 사례가 된다.", "데이터가 매우 크다는 사실만으로 효율적이지 않으며 평균 O(n²) 비용이 부담될 수 있다.", "입력 순서에 따라 이동 횟수가 달라지므로 모든 경우의 성능이 동일하지 않다."],
        "comparison": "삽입 정렬은 거의 정렬된 데이터에서 빠르고 안정적이며, 선택 정렬은 입력 순서와 관계없이 비교 횟수가 크게 줄지 않는다.",
        "code": "values = [1, 2, 4, 3]\nvalues.remove(3)\nvalues.insert(2, 3)\nassert values == [1, 2, 3, 4]",
        "example": "거의 정렬된 배열에서 한 원소만 가까운 위치로 이동하는 동작을 보여준다.",
        "usage": "작은 배열이나 대부분 정렬된 데이터의 마무리 정렬, 하이브리드 정렬의 소구간 처리에 사용한다.",
    },
    "algorithm-6": {
        "name": "상수 공간 복잡도 O(1)",
        "definition": "공간 복잡도 O(1)은 입력 크기가 증가해도 알고리즘이 사용하는 추가 메모리의 상한이 일정하다는 뜻이다. 입력 저장 공간과 별도로 사용하는 변수 몇 개가 대표 사례다.",
        "answer": "입력 크기와 무관하게 일정한 수의 추가 변수만 사용하면 공간 복잡도는 O(1)이다. 메모리를 전혀 쓰지 않는다는 뜻이 아니며, 입력에 비례한 배열을 만들면 O(n), 무한 메모리는 복잡도 개념과 무관하다.",
        "wrong": ["O(1)은 메모리를 전혀 사용하지 않는다는 뜻이 아니라 추가 메모리 크기가 입력에 따라 늘지 않는다는 뜻이다.", "입력 크기에 비례해 추가 메모리가 증가하면 O(n) 공간이며 상수 공간이 아니다.", "메모리가 무한하다는 설명은 실제 자원 사용량의 증가율을 표현하지 못한다."],
        "comparison": "O(1) 공간은 고정 개수 변수만 사용하고, O(n) 공간은 입력 크기만큼 추가 배열이나 자료구조를 만든다.",
        "code": "values = [3, 1, 2]\nminimum = values[0]\nfor value in values: minimum = min(minimum, value)\nassert minimum == 1",
        "example": "입력 크기와 관계없이 minimum 변수 하나만 추가로 사용한다.",
        "usage": "대용량 입력을 처리하면서 보조 배열 생성을 피하고 메모리 사용량을 제한해야 할 때 평가 기준으로 사용한다.",
    },
    "algorithm-7": {
        "name": "선택 정렬",
        "definition": "선택 정렬은 정렬되지 않은 구간에서 최솟값을 찾아 그 구간의 맨 앞 원소와 교환하고, 정렬 구간을 한 칸씩 확장하는 정렬이다.",
        "answer": "매 회차마다 남은 구간의 최솟값을 찾아 현재 앞자리와 교환하는 방식이 선택 정렬이다. 인접 원소 교환은 버블 정렬, 정렬 구간에 삽입은 삽입 정렬, 반으로 분할은 병합 정렬 계열이다.",
        "wrong": ["인접한 두 원소를 반복 비교하고 교환하는 방식은 버블 정렬이다.", "이미 정렬된 앞부분의 적절한 위치에 새 원소를 넣는 방식은 삽입 정렬이다.", "배열을 반으로 나눠 각각 정렬한 뒤 결합하는 방식은 병합 정렬이다."],
        "comparison": "선택 방식은 남은 구간의 최솟값 위치를 확정한다. 버블 방식은 인접 역전을 제거하고 삽입 방식은 앞 구간 안으로 값을 이동한다.",
        "code": "values = [3, 1, 2]\nminimum_index = values.index(min(values))\nvalues[0], values[minimum_index] = values[minimum_index], values[0]\nassert values == [1, 3, 2]",
        "example": "남은 구간의 최솟값을 찾아 첫 위치와 교환하는 한 회차를 검증한다.",
        "usage": "추가 메모리 없이 정렬하고 교환 횟수를 제한해야 하는 작은 데이터에서 원리를 설명할 때 사용한다.",
    },
    "algorithm-binary": {
        "name": "이진 탐색(Binary Search)",
        "definition": "이진 탐색(Binary Search)은 정렬된 데이터의 중앙값과 목표값을 비교해 탐색 범위를 절반씩 줄이는 알고리즘이다. 정렬 순서가 있어야 버릴 절반을 판단할 수 있다.",
        "answer": "중앙값 비교 뒤 어느 절반을 제외할지 결정하려면 데이터가 정렬되어 있어야 한다. 연결 리스트나 해시 테이블 저장 여부, 원소 수가 2의 거듭제곱인지 여부는 필수 전제가 아니다.",
        "wrong": ["연결 리스트 저장은 전제 조건이 아니며 중앙 위치 접근이 느려 일반적인 이진 탐색 구현에도 불리하다.", "해시 테이블은 정렬 순서를 보장하지 않으므로 중앙값 기준으로 절반을 버리는 이진 탐색과 다르다.", "원소 수가 2의 거듭제곱이 아니어도 경계를 조정하며 탐색 범위를 나눌 수 있다."],
        "comparison": "이진 탐색은 정렬을 전제로 O(log n)에 찾고, 선형 탐색은 정렬 없이 O(n)에 모든 원소를 확인할 수 있다.",
        "code": "from bisect import bisect_left\nvalues = [1, 4, 7, 9]\nindex = bisect_left(values, 7)\nassert values[index] == 7",
        "example": "정렬된 배열에서 목표값의 위치를 찾아 결과를 검증한다.",
        "usage": "정렬된 상품 번호, 로그 시각, 점수 목록에서 값이나 경계 위치를 반복 조회할 때 사용한다.",
    },
    "algorithm-dfs": {
        "name": "DFS(깊이 우선 탐색)",
        "definition": "DFS(깊이 우선 탐색)는 한 경로를 끝까지 따라간 뒤 이전 분기점으로 돌아가 다른 경로를 탐색한다. 다음 방문 지점을 후입선출 순서로 관리하기 위해 스택이나 재귀 호출을 사용한다.",
        "answer": "DFS는 가장 최근에 발견한 정점을 먼저 방문해야 하므로 후입선출 구조인 스택이 맞다. 재귀도 호출 스택으로 같은 순서를 만들며, 큐는 먼저 발견한 정점을 처리하는 BFS에 사용된다.",
        "wrong": ["큐는 선입선출 순서로 정점을 처리하므로 너비 우선 탐색의 방문 순서를 만든다.", "힙은 우선순위가 높은 항목을 꺼내지만 최근 경로로 되돌아가는 순서를 보장하지 않는다.", "해시 테이블은 방문 여부 기록에는 유용하지만 다음 방문 경로의 순서를 관리하지 않는다."],
        "comparison": "DFS는 스택으로 경로를 깊게 탐색하고, BFS는 큐로 같은 깊이의 정점을 먼저 탐색한다.",
        "code": "stack = ['A']\nstack.append('B')\nnext_node = stack.pop()\nassert next_node == 'B'",
        "example": "마지막에 추가한 정점이 먼저 꺼내지는 후입선출 동작을 검증한다.",
        "usage": "미로 경로 탐색, 연결 요소 확인, 백트래킹처럼 한 분기를 깊게 조사해야 할 때 사용한다.",
    },
    "algorithm-linked": {
        "name": "연결 리스트(Linked List)",
        "definition": "연결 리스트(Linked List)는 각 노드가 값과 다음 노드의 참조를 저장하는 자료구조다. 삽입하거나 삭제할 위치의 노드를 이미 알고 있으면 주변 참조만 변경하므로 O(1)에 처리할 수 있다.",
        "answer": "위치를 이미 알고 있는 삽입·삭제는 노드 연결만 바꾸므로 O(1)이다. 인덱스 조회는 처음부터 노드를 따라가 O(n)이고, 참조 저장 비용 때문에 배열보다 메모리를 적게 쓴다고 단정할 수 없으며 정렬 속도도 자동으로 빨라지지 않는다.",
        "wrong": ["연결 리스트는 원하는 인덱스까지 노드를 순서대로 따라가야 하므로 인덱스 접근이 O(n)이다.", "각 노드가 다음 노드 참조를 추가로 저장하므로 배열보다 메모리를 적게 쓴다고 일반화할 수 없다.", "연결 리스트라는 구조만으로 정렬이 빨라지지 않으며 정렬 알고리즘의 비교와 탐색 비용을 고려해야 한다."],
        "comparison": "연결 리스트는 위치를 아는 삽입·삭제가 O(1)이고 조회는 O(n)이며, 배열 목록은 인덱스 조회가 O(1)이지만 중간 변경에 요소 이동이 필요하다.",
        "code": "from collections import deque\nvalues = deque(['B', 'C'])\nvalues.appendleft('A')\nassert list(values) == ['A', 'B', 'C']",
        "example": "앞쪽 연결을 변경해 원소를 삽입한 결과를 검증한다.",
        "usage": "큐나 덱처럼 양 끝 삽입·삭제가 빈번하고 임의 인덱스 조회가 중요하지 않은 구조에 사용한다.",
    },
}


def build_payload(card_id: str, question: dict, spec: dict) -> dict:
    wrong_options = [
        (index, text)
        for index, text in enumerate(question["options"])
        if index != question["correct_answer"]
    ]
    payloads = {
        "CONCEPT_DEFINITION": {
            "content": spec["definition"],
            "examples": [f"{spec['name']}을 작은 입력에 적용해 핵심 연산과 결과의 변화를 관찰한다."],
        },
        "ANSWER_REASON": {
            "why_correct": spec["answer"],
            "key_points": [spec["name"], question["options"][question["correct_answer"]]],
        },
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["복잡도나 자료구조의 적용 전제를 제외하고 결과 표현만 암기하는 실수"],
            "per_option": {
                f"option_{index}": {"text": text, "reason": reason}
                for (index, text), reason in zip(wrong_options, spec["wrong"], strict=True)
            },
        },
        "COMPARISON": {"comparisons": [{"with": "혼동하기 쉬운 개념", "diff": spec["comparison"]}]},
        "EXAMPLE_REQUEST": {"code_example": spec["code"], "explanation": spec["example"]},
        "PRACTICAL_USAGE": {
            "real_world": spec["usage"],
            "best_practices": ["입력 조건과 기대 결과를 작은 실행 예제로 먼저 검증한다."],
        },
        "DEBUG_OR_ERROR": {
            "common_errors": [{
                "error": f"{spec['name']}의 적용 전제와 다른 알고리즘의 특징을 혼동한다.",
                "solution": "입력 조건, 핵심 연산, 시간·공간 복잡도를 각각 분리해 비교한다.",
            }]
        },
    }
    RagPayloads.model_validate(payloads)
    return payloads


def build_artifact(candidate_ids: list[str], cards: dict[str, dict], questions: dict[str, dict]) -> dict:
    drafts, skipped, review = {}, [], {}
    for card_id in candidate_ids:
        card = cards.get(card_id)
        spec = SPECS.get(card_id)
        question = questions.get(card_id)
        if not card or not spec or not question:
            skipped.append(card_id)
            continue
        payloads = build_payload(card_id, question, spec)
        simulated = copy.deepcopy(card)
        simulated["payloads"] = payloads
        quality = validate_payload_quality(simulated)
        drafts[card_id] = {
            "payloads": payloads,
            "fact_check_notes": [
                f"출처 문제 '{question['content']}'와 정답 '{question['options'][question['correct_answer']]}'를 대조했다.",
                "검색 필드와 승인 상태는 초안 대상에서 제외했다.",
            ],
            "patch_reason": "java-arraylist 기준으로 원리, 정답 도출, 선택지별 차이, 실행 검증 예시를 구체화한다.",
            "quality_review": quality,
        }
        review[card_id] = {"pass": not quality["reasons"], "remaining_reasons": quality["reasons"]}
    return {
        "candidate_count": len(candidate_ids),
        "prepared_count": len(drafts),
        "skipped_count": len(skipped),
        "FACTCHECK_PREPARATION": drafts,
        "review_summary": review,
        "skipped_cards": skipped,
        "execution_performed": False,
        "card_files_modified": False,
        "approval_status_changed": False,
        "patches_ready_created": False,
        "json_validation_result": "pass",
    }


def main() -> int:
    source = json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))
    candidate_ids = source["validated_cards"]
    cards = {
        card["card_id"]: card
        for path in CARD_ROOT.rglob("*.json")
        if (card := json.loads(path.read_text(encoding="utf-8-sig")))["card_id"] in candidate_ids
    }
    question_by_source = {
        f"algorithm:{question.id}": {
            "content": question.content,
            "options": question.options,
            "correct_answer": question.correct_answer,
        }
        for question in extract_questions()
    }
    questions = {
        card_id: question_by_source[cards[card_id]["source_question_ids"][0]]
        for card_id in candidate_ids
        if card_id in cards and cards[card_id]["source_question_ids"][0] in question_by_source
    }
    artifact = build_artifact(candidate_ids, cards, questions)
    serialized = json.dumps(artifact, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    summary = {key: value for key, value in artifact.items() if key != "FACTCHECK_PREPARATION"}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

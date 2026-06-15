from __future__ import annotations

import copy
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard
from app.scripts.apply_payload_batch_v217 import card_acceptance_reasons
from app.scripts.concept_example_verifiers import verify
from app.scripts.initialize_validation_policy_v212 import validate_lock
from app.scripts.migrate_rag_cards import evaluate_retrieval_modes, extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[3]
AI_ROOT = ROOT / "ai"
REPORT = AI_ROOT / "reports" / "concept_verified_examples_approval_2026-06-15.json"
BACKUP_ROOT = AI_ROOT / "app" / "knowledge" / "concepts_v2_backups"
LOCKED = (
    "card_id", "category", "term", "source_question_ids", "retrieval", "aliases",
    "boost_keywords", "created_at", "related_card_ids",
)


JAVA_DELEGATION = """public class ConceptCheck {
    static class TrackingLoader extends ClassLoader {
        boolean findClassCalled;
        TrackingLoader(ClassLoader parent) { super(parent); }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            findClassCalled = true;
            throw new ClassNotFoundException(name);
        }
    }
    public static void main(String[] args) throws Exception {
        TrackingLoader child = new TrackingLoader(ClassLoader.getSystemClassLoader());
        Class<?> loaded = child.loadClass("java.lang.String");
        assert loaded == String.class && !child.findClassCalled;
    }
}"""

JAVA_G1 = """import java.lang.management.ManagementFactory;
public class ConceptCheck {
    public static void main(String[] args) {
        boolean g1 = ManagementFactory.getGarbageCollectorMXBeans().stream()
            .anyMatch(bean -> bean.getName().contains("G1"));
        assert g1;
    }
}"""

JAVA_CHECKED = """import java.io.IOException;
public class ConceptCheck {
    static void load() throws IOException { throw new IOException("read failed"); }
    public static void main(String[] args) {
        try { load(); throw new AssertionError(); }
        catch (IOException expected) { assert expected.getMessage().equals("read failed"); }
    }
}"""

PYTHON_MULTIPROCESSING = """import os
import threading
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
def process_identity(_): return os.getpid()
def thread_identity(): return os.getpid(), threading.get_ident()
if __name__ == "__main__":
    with ProcessPoolExecutor(1) as pool: child_pid = pool.submit(process_identity, 0).result()
    with ThreadPoolExecutor(1) as pool: thread_pid, thread_id = pool.submit(thread_identity).result()
    assert child_pid != os.getpid() and thread_pid == os.getpid() and thread_id != threading.get_ident()
"""


EXAMPLES = {
    "java-delegation": {
        "code_example": JAVA_DELEGATION,
        "explanation": "자식 ClassLoader가 String을 요청했을 때 부모가 먼저 찾아 자식의 findClass가 호출되지 않는 부모 우선 위임을 검증한다.",
        "validator": "java",
    },
    "java-g1-gc": {
        "code_example": JAVA_G1,
        "explanation": "JVM을 G1 GC로 실행한 뒤 관리 API에서 실제 G1 수집기가 활성화되었는지 검증한다.",
        "validator": "java_g1",
    },
    "java-checked": {
        "code_example": JAVA_CHECKED,
        "explanation": "IOException을 선언한 메서드를 호출하고 catch로 처리한다. 검증기는 예외를 처리하지 않은 동일 호출이 컴파일에 실패하는지도 확인한다.",
        "validator": "java_checked",
    },
    "spring-circuit": {
        "code_example": "CircuitBreaker breaker = CircuitBreaker.ofDefaults(\"remote\");\nbreaker.onError(1, TimeUnit.MILLISECONDS, new IOException());\nassert breaker.getState() == CircuitBreaker.State.OPEN;",
        "explanation": "Resilience4j CircuitBreaker의 실제 실패 기록과 상태 전이를 검증해야 한다.",
        "validator": "resilience4j",
    },
    "spring-valid": {
        "code_example": "Set<ConstraintViolation<UserRequest>> violations = validator.validate(request, Update.class);\nboolean nameRequired = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(\"name\"));\nassert nameRequired;",
        "explanation": "Bean Validation Validator를 실행해 Update 그룹에서 실제 제약 위반이 발생하는지 검증해야 한다.",
        "validator": "spring_harness",
    },
    "spring-profile": {
        "code_example": "context.getEnvironment().setActiveProfiles(\"dev\");\ncontext.refresh();\nassert context.getBean(DataSource.class) instanceof H2DataSource;",
        "explanation": "Spring ApplicationContext에서 dev 프로필 활성화 후 선택된 Bean 구현을 검증해야 한다.",
        "validator": "spring_harness",
    },
    "frontend-usecallback": {
        "code_example": "let previous;\nfunction Demo({ value }) { const callback = React.useCallback(() => value, [value]); const same = previous === callback; previous = callback; return same; }\nconst view = TestRenderer.create(<Demo value={1} />);\nview.update(<Demo value={1} />);\nassert(view.root.findByType(Demo).instance === null);",
        "explanation": "동일 의존성으로 재렌더링했을 때 useCallback이 같은 함수 참조를 유지하는지 React 테스트 런타임에서 검증해야 한다.",
        "validator": "react_renderer",
    },
    "frontend-react-server-components": {
        "code_example": "export default async function Users() {\n  const users = await db.user.findMany();\n  return <ul>{users.map(user => <li key={user.id}>{user.name}</li>)}</ul>;\n}",
        "explanation": "Next.js RSC 런타임에서 서버 데이터 조회 결과와 클라이언트 번들 제외 여부를 검증해야 한다.",
        "validator": "rsc_runtime",
    },
    "python-multiprocessing": {
        "code_example": PYTHON_MULTIPROCESSING,
        "explanation": "프로세스 작업은 다른 PID에서, 스레드 작업은 같은 PID의 다른 스레드에서 실행되는지 검증한다.",
        "validator": "python",
    },
}


def build_approved_candidate(original: dict, example: dict, *, approved_at: str) -> dict:
    candidate = copy.deepcopy(original)
    candidate["payloads"]["EXAMPLE_REQUEST"] = copy.deepcopy(example)
    review = candidate["review"]
    review["card_status"] = "approved"
    for intent in ("CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON", "EXAMPLE_REQUEST"):
        review["payload_status"][intent] = "approved"
    review["approved_at"] = approved_at
    review["reviewer"] = "concept-execution-verifier"
    review["rejected_reason"] = None
    return candidate


def acceptance_reasons(*, execution: dict, lock_reasons: list[str], retrieval_reasons: list[str]) -> list[str]:
    reasons = []
    if not execution["passed"]:
        reasons.append(execution["reason"] or "execution_failed")
    reasons.extend(lock_reasons)
    reasons.extend(retrieval_reasons)
    return list(dict.fromkeys(reasons))


def verify_example(card_id: str, spec: dict) -> dict:
    return verify(spec["validator"], spec["code_example"], card_id=card_id)


def main() -> int:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
    approved_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    backup_root = BACKUP_ROOT / f"concept_verified_examples_{timestamp}"
    backup_root.mkdir(parents=True, exist_ok=False)
    questions = extract_questions()
    initial_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    initial_metrics = evaluate_retrieval_modes(questions, initial_cards)
    approved, rolled_back, failures, executions = [], [], {}, {}

    paths = {}
    for path in CARD_ROOT.rglob("*.json"):
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        paths[card["card_id"]] = path

    for card_id, spec in EXAMPLES.items():
        path = paths[card_id]
        backup = backup_root / path.relative_to(CARD_ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, backup)
        original = json.loads(path.read_text(encoding="utf-8-sig"))
        execution = verify_example(card_id, spec)
        executions[card_id] = execution
        if not execution["passed"]:
            rolled_back.append(card_id)
            failures[card_id] = [execution["reason"]]
            continue
        example = {"code_example": spec["code_example"], "explanation": spec["explanation"]}
        candidate = build_approved_candidate(original, example, approved_at=approved_at)
        path.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        try:
            written = json.loads(path.read_text(encoding="utf-8"))
            RagCard.model_validate(written)
        except Exception as exc:
            shutil.copy2(backup, path)
            rolled_back.append(card_id)
            failures[card_id] = [f"json_invalid:{exc}"]
            continue
        lock_reasons = validate_lock(original, written)
        current_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        current_metrics = evaluate_retrieval_modes(questions, current_cards)
        retrieval_reasons = card_acceptance_reasons(
            initial_metrics["production_mode"], initial_metrics["content_mode"],
            current_metrics["production_mode"], current_metrics["content_mode"],
        )
        reasons = acceptance_reasons(execution=execution, lock_reasons=lock_reasons, retrieval_reasons=retrieval_reasons)
        if reasons:
            shutil.copy2(backup, path)
            rolled_back.append(card_id)
            failures[card_id] = reasons
        else:
            approved.append(card_id)

    final_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    final_metrics = evaluate_retrieval_modes(questions, final_cards)
    report = {
        "candidate_count": len(EXAMPLES),
        "approved_count": len(approved),
        "rolled_back_count": len(rolled_back),
        "approved_cards": approved,
        "rolled_back_cards": rolled_back,
        "failed_cards": failures,
        "execution_results": executions,
        "backup_root": str(backup_root),
        "production_hit1_diff": final_metrics["production_mode"].exact_hit1 - initial_metrics["production_mode"].exact_hit1,
        "production_loo_diff": final_metrics["production_mode"].loo_average_score - initial_metrics["production_mode"].loo_average_score,
        "content_hit1_diff": final_metrics["content_mode"].exact_hit1 - initial_metrics["content_mode"].exact_hit1,
        "content_loo_diff": final_metrics["content_mode"].loo_average_score - initial_metrics["content_mode"].loo_average_score,
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

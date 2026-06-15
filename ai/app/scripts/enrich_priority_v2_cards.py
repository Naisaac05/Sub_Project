from __future__ import annotations

import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.scripts.improve_rag_cards_v2 import score_card_quality


CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
TARGETS = {
    "frontend-react-key": {
        "aliases": ["react key", "리액트 key", "list key", "reconciliation key", "stable react key", "react list identity"],
        "boosts": ["react-key", "reconciliation-key", "list-identity", "stable-key", "react-list"],
        "definition": "React key는 목록 렌더링에서 각 항목의 정체성을 안정적으로 식별하는 값입니다. React는 key를 사용해 이전 렌더와 다음 렌더의 항목을 대응시키므로, 형제 항목 사이에서 고유하고 렌더 간 변하지 않는 값을 사용해야 합니다.",
        "reason": "React 목록 항목에 안정적인 key를 지정하면 reconciliation 과정에서 항목의 추가, 삭제, 순서 변경을 정확히 추적하여 잘못된 DOM 재사용과 컴포넌트 상태 이동을 방지할 수 있습니다.",
        "mistakes": ["배열 index를 변경 가능한 목록의 key로 사용하면 순서 변경 시 다른 항목의 상태가 재사용될 수 있습니다."],
    },
    "java-equals": {
        "aliases": ["java equals", "자바 equals", "object equality", "logical equality", "equals method", "객체 동등성"],
        "boosts": ["java-equals", "logical-equality", "object-equality", "equals-method", "hashcode-contract"],
        "definition": "Java의 equals 메서드는 두 객체가 논리적으로 같은 값을 나타내는지 판단합니다. 기본 Object.equals는 참조 동일성을 비교하지만, 값 객체는 필요한 필드를 기준으로 equals를 재정의하며 hashCode도 같은 계약에 맞춰 재정의해야 합니다.",
        "reason": "문자열이나 값 객체의 내용 동등성을 비교하려면 equals를 사용해야 합니다. ==는 객체 참조가 같은지 확인하므로 서로 다른 인스턴스가 같은 값을 담은 경우 올바른 동등성 판단을 하지 못합니다.",
        "mistakes": ["equals만 재정의하고 hashCode를 재정의하지 않으면 HashMap과 HashSet에서 동등한 객체가 다르게 처리될 수 있습니다."],
    },
    "java-extends": {
        "aliases": ["java extends", "자바 extends", "bounded wildcard extends", "upper bounded wildcard", "? extends Number", "제네릭 상한 경계"],
        "boosts": ["java-extends", "upper-bounded-wildcard", "extends-number", "generic-covariance", "producer-extends"],
        "definition": "Java 제네릭의 ? extends T는 T 또는 T의 하위 타입을 원소로 갖는 타입을 허용하는 상한 경계 와일드카드입니다. 안전하게 값을 읽을 수 있지만 실제 하위 타입을 알 수 없으므로 null 이외의 값을 추가할 수 없습니다.",
        "reason": "? extends Number는 Number 자체와 Integer, Double 같은 Number의 하위 타입 컬렉션을 받을 수 있습니다. 생산자 역할의 컬렉션에서 Number로 안전하게 읽기 위한 제약입니다.",
        "mistakes": ["? extends Number를 Number만 허용한다고 해석하거나 모든 타입을 허용한다고 해석하면 상한 경계의 의미를 놓칩니다."],
    },
    "python-with": {
        "aliases": ["python with", "파이썬 with", "with statement", "context manager", "컨텍스트 매니저", "__enter__ __exit__"],
        "boosts": ["python-with", "context-manager", "with-statement", "__enter__", "__exit__", "resource-cleanup"],
        "definition": "Python의 with 문은 컨텍스트 매니저를 사용해 자원 획득과 정리를 하나의 블록으로 관리합니다. 블록 진입 시 __enter__가 호출되고 종료 시 예외 발생 여부와 관계없이 __exit__가 호출되어 파일이나 락 같은 자원을 안전하게 정리합니다.",
        "reason": "with 문은 __enter__와 __exit__ 프로토콜을 사용하므로 예외가 발생해도 정리 로직이 실행됩니다. 따라서 직접 close를 호출하는 방식보다 누락 가능성이 낮고 자원 수명 범위가 명확합니다.",
        "mistakes": ["__init__/__del__은 객체 수명 주기 메서드이며 with 문의 진입과 종료를 보장하는 컨텍스트 매니저 프로토콜이 아닙니다."],
    },
    "spring-spring-question-59": {
        "aliases": ["spring cache evict", "스프링 캐시 제거", "@CacheEvict", "cache eviction", "spring cache invalidation", "캐시 무효화"],
        "boosts": ["spring-cache-evict", "@CacheEvict", "cache-eviction", "cache-invalidation", "spring-cache"],
        "definition": "Spring의 @CacheEvict는 지정한 캐시에서 기존 항목을 제거하는 애너테이션입니다. 데이터 변경 이후 오래된 캐시 값이 반환되지 않도록 특정 key를 제거하거나 allEntries 옵션으로 캐시 전체를 비울 때 사용합니다.",
        "reason": "@CacheEvict의 책임은 캐시에 값을 저장하거나 조회하는 것이 아니라 기존 캐시 항목을 제거해 다음 요청이 최신 데이터를 다시 읽도록 만드는 것입니다.",
        "mistakes": ["@Cacheable은 결과 저장과 조회 재사용에 사용되고 @CacheEvict는 오래된 항목 제거에 사용됩니다."],
    },
}


def enrich(root: Path, backup_root: Path) -> dict[str, object]:
    report = {}
    for path in sorted(root.rglob("*.json")):
        card = json.loads(path.read_text(encoding="utf-8"))
        details = TARGETS.get(card.get("card_id"))
        if details is None:
            continue
        backup = backup_root / path.relative_to(root)
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, backup)
        before_review = json.loads(json.dumps(card["review"]))
        before = score_card_quality(card)
        card["aliases"] = details["aliases"]
        card["retrieval"]["boost_keywords"] = details["boosts"]
        card["retrieval"]["embedding_text"] = " ".join([
            card["term"], *card["aliases"][:3], card["category"], *card["retrieval"]["boost_keywords"]
        ])
        card["payloads"]["CONCEPT_DEFINITION"]["content"] = details["definition"]
        card["payloads"]["CONCEPT_DEFINITION"]["examples"] = [f"{details['aliases'][0]} 사용 조건을 설명하세요."]
        card["payloads"]["ANSWER_REASON"]["why_correct"] = details["reason"]
        card["payloads"]["ANSWER_REASON"]["key_points"] = details["boosts"][:3]
        wrong = card["payloads"]["WRONG_ANSWER_REASON"]
        wrong["common_mistakes"] = details["mistakes"]
        wrong["per_option"] = {
            f"option_{index}": {
                "text": value["text"],
                "reason": f"`{value['text']}`은(는) {details['definition'].split('.')[0]}의 책임과 일치하지 않습니다.",
            }
            for index, value in enumerate(wrong.get("per_option", {}).values())
        }
        card["updated_at"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        if card["review"] != before_review:
            raise RuntimeError(f"review status changed: {card['card_id']}")
        path.write_text(json.dumps(card, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report[card["card_id"]] = {"before": before, "after": score_card_quality(card)}
    return report


if __name__ == "__main__":
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    backup_root = ROOT / "app" / "knowledge" / "concepts_v2_backups" / f"priority-enrichment-{stamp}"
    output = enrich(CARD_ROOT, backup_root)
    report = ROOT / "reports" / "priority_v2_enrichment.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({"backup_root": str(backup_root), "cards": output}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(report)

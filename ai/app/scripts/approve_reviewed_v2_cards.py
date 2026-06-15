from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
GENERATED_PAYLOADS = ("CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON")
REVIEWED_CARD_IDS = frozenset({
    "algorithm", "algorithm-2", "algorithm-3", "algorithm-4", "algorithm-5",
    "algorithm-6", "algorithm-7", "algorithm-linked", "algorithm-queue", "algorithm-stack",
    "frontend", "frontend-2", "frontend-button", "frontend-conditional-rendering",
    "frontend-functional-component", "frontend-hook", "frontend-jsx-expression",
    "frontend-react-project-tool", "frontend-useeffect", "frontend-usestate",
    "java-access-modifier", "java-array-length", "java-arraylist", "java-extends-keyword",
    "java-final-keyword", "java-int-variable", "java-loop-control", "java-main", "java-primitive",
    "python", "python-decorator", "python-dictionary", "python-fstring", "python-function-definition",
    "python-immutable", "python-multiline-string", "python-negative-indexing", "python-none",
    "python-range", "python-with-statement",
    "spring-applicationyml", "spring-autowired", "spring-boot", "spring-controller",
    "spring-dependency-injection", "spring-embedded-tomcat", "spring-ioc",
    "spring-requestmapping", "spring-service", "spring-spring-bean-scope",
})
PROTECTED_APPROVED_IDS = frozenset({
    "frontend-react-key", "java-equals", "spring-spring-question-59", "java-extends", "python-with",
})


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def review_errors(card: dict) -> list[str]:
    errors = []
    payloads = card.get("payloads", {})
    retrieval = card.get("retrieval", {})
    if card.get("review", {}).get("card_status") != "draft":
        errors.append("not_draft")
    if not card.get("source_question_ids"):
        errors.append("missing_source_question_ids")
    if len(card.get("aliases", [])) < 3:
        errors.append("weak_aliases")
    if not 3 <= len(retrieval.get("boost_keywords", [])) <= 7:
        errors.append("weak_boost_keywords")
    if not 0 < len(retrieval.get("embedding_text", "")) <= 150:
        errors.append("invalid_embedding_text")
    for intent in GENERATED_PAYLOADS:
        if not payloads.get(intent):
            errors.append(f"missing_{intent}")
    wrong = (payloads.get("WRONG_ANSWER_REASON") or {}).get("per_option") or {}
    if not wrong or any(not value.get("reason", "").strip() for value in wrong.values()):
        errors.append("incomplete_wrong_answer_reasons")
    rendered = json.dumps(payloads, ensure_ascii=False)
    if not any("가" <= char <= "힣" for char in rendered):
        errors.append("missing_korean_payload")
    return errors


def approve(root: Path, backup_root: Path, *, write: bool) -> dict[str, object]:
    paths = {json.loads(path.read_text(encoding="utf-8-sig"))["card_id"]: path for path in root.rglob("*.json")}
    protected_before = {card_id: sha256(paths[card_id]) for card_id in PROTECTED_APPROVED_IDS}
    reviewed = {}
    approved = []
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    for card_id in sorted(REVIEWED_CARD_IDS):
        card = json.loads(paths[card_id].read_text(encoding="utf-8-sig"))
        if card.get("review", {}).get("card_status") == "approved":
            reviewed[card_id] = []
            continue
        errors = review_errors(card)
        reviewed[card_id] = errors
        if errors:
            continue
        approved.append(card_id)
        if write:
            backup = backup_root / paths[card_id].relative_to(root)
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(paths[card_id], backup)
            card["review"]["card_status"] = "approved"
            for intent in GENERATED_PAYLOADS:
                card["review"]["payload_status"][intent] = "approved"
            card["review"]["approved_at"] = now
            card["review"]["reviewer"] = "codex-assisted-quality-review"
            paths[card_id].write_text(json.dumps(card, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    protected_after = {card_id: sha256(paths[card_id]) for card_id in PROTECTED_APPROVED_IDS}
    if protected_before != protected_after:
        raise RuntimeError("protected approved card changed")
    manifest = {
        "created_at": now,
        "approved_card_ids": approved,
        "review_errors": reviewed,
        "protected_approved_sha256": protected_before,
        "write": write,
    }
    if write:
        backup_root.mkdir(parents=True, exist_ok=True)
        (backup_root / "approval-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--backup-root", type=Path, required=True)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    report = approve(args.root, args.backup_root, write=args.write)
    print(json.dumps({
        "approved_count": len(report["approved_card_ids"]),
        "rejected_count": sum(bool(value) for value in report["review_errors"].values()),
        "write": report["write"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.documents import KNOWLEDGE_ROOT, ConceptCard, load_concept_cards


REQUIRED_METADATA = ("id", "category", "difficulty", "version", "last_updated")
REQUIRED_SECTIONS = ("핵심 설명", "대표 해결", "흔한 오해", "평가 키워드")
DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def lint_cards(
    cards: list[ConceptCard] | None = None,
    approved_qa_root: Path | None = None,
) -> list[str]:
    concept_cards = cards if cards is not None else load_concept_cards()
    approved_root = approved_qa_root or KNOWLEDGE_ROOT / "approved_qa"
    errors: list[str] = []
    seen: set[str] = set()

    for card in concept_cards:
        location = str(card.path)
        for key in REQUIRED_METADATA:
            if not card.metadata.get(key):
                errors.append(f"{location}: missing metadata {key}")

        if card.concept_id in seen:
            errors.append(f"{location}: duplicate concept id {card.concept_id}")
        seen.add(card.concept_id)

        if card.metadata.get("last_updated") and not DATE_PATTERN.match(card.metadata["last_updated"]):
            errors.append(f"{location}: last_updated must be YYYY-MM-DD")

        for section in REQUIRED_SECTIONS:
            if not card.sections.get(section):
                errors.append(f"{location}: missing section {section}")

        keywords = _bullet_items(card.sections.get("평가 키워드", ""))
        if len(keywords) < 2:
            errors.append(f"{location}: 평가 키워드 must contain at least 2 items")

    known_ids = {card.concept_id for card in concept_cards}
    if approved_root.exists():
        for path in approved_root.rglob("*.md"):
            text = path.read_text(encoding="utf-8")
            for concept_id in re.findall(r"concept_id:\s*([A-Za-z0-9_.-]+)", text):
                if concept_id not in known_ids:
                    errors.append(f"{path}: unknown concept_id reference {concept_id}")

    return errors


def _bullet_items(text: str) -> list[str]:
    return [line for line in text.splitlines() if line.strip().startswith("-")]


def main() -> int:
    errors = lint_cards()
    if errors:
        for error in errors:
            print(error)
        return 1
    print("Knowledge card lint passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


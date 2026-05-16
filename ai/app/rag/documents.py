from dataclasses import dataclass
from pathlib import Path
import re


KNOWLEDGE_ROOT = Path(__file__).resolve().parents[1] / "knowledge"
CONCEPT_ROOT = KNOWLEDGE_ROOT / "concepts"


@dataclass(frozen=True)
class ConceptCard:
    path: Path
    concept_id: str
    metadata: dict[str, str]
    title: str
    sections: dict[str, str]

    @property
    def searchable_text(self) -> str:
        parts = [self.concept_id, self.title]
        parts.extend(self.metadata.values())
        parts.extend(self.sections.keys())
        parts.extend(self.sections.values())
        return "\n".join(parts)


def load_concept_cards(root: Path | None = None) -> list[ConceptCard]:
    concept_root = root or CONCEPT_ROOT
    if not concept_root.exists():
        return []

    cards = [
        parse_concept_card(path)
        for path in sorted(concept_root.rglob("*.md"))
        if path.is_file()
    ]
    return cards


def parse_concept_card(path: Path) -> ConceptCard:
    text = path.read_text(encoding="utf-8")
    metadata, body = _split_front_matter(text)
    title = _extract_title(body)
    sections = _extract_sections(body)
    concept_id = metadata.get("id", path.stem)
    return ConceptCard(
        path=path,
        concept_id=concept_id,
        metadata=metadata,
        title=title,
        sections=sections,
    )


def _split_front_matter(text: str) -> tuple[dict[str, str], str]:
    if not text.startswith("---"):
        return {}, text

    parts = text.split("---", 2)
    if len(parts) < 3:
        return {}, text

    metadata: dict[str, str] = {}
    for line in parts[1].splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        metadata[key.strip()] = value.strip().strip('"')
    return metadata, parts[2].lstrip()


def _extract_title(body: str) -> str:
    for line in body.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def _extract_sections(body: str) -> dict[str, str]:
    sections: dict[str, list[str]] = {}
    current: str | None = None

    for line in body.splitlines():
        heading = re.match(r"^##\s+(.+?)\s*$", line)
        if heading:
            current = heading.group(1).strip()
            sections[current] = []
            continue
        if current is not None:
            sections[current].append(line)

    return {name: "\n".join(lines).strip() for name, lines in sections.items()}


from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.extraction import (
    extract_candidate_concepts,
    parse_course_skill_questions,
    write_candidate_jsonl,
)


DEFAULT_SOURCE = REPO_ROOT / "backend" / "src" / "main" / "java" / "com" / "devmatch" / "config" / "CourseSkillTestInitializer.java"
DEFAULT_OUTPUT = ROOT / "app" / "knowledge" / "candidates" / "course_concepts.jsonl"


def extract_course_concepts(
    source_path: Path = DEFAULT_SOURCE,
    output_path: Path = DEFAULT_OUTPUT,
) -> dict[str, int | str]:
    text = source_path.read_text(encoding="utf-8")
    questions = parse_course_skill_questions(text, source_path=str(source_path.relative_to(REPO_ROOT)))
    candidates = extract_candidate_concepts(questions)
    write_candidate_jsonl(candidates, output_path)
    return {
        "source": str(source_path.relative_to(REPO_ROOT)),
        "output": str(output_path.relative_to(ROOT)),
        "questions": len(questions),
        "candidates": len(candidates),
    }


def main() -> int:
    report = extract_course_concepts()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["questions"] and report["candidates"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

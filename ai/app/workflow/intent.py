from dataclasses import dataclass
import re


@dataclass(frozen=True)
class FreeQuestionIntent:
    intent: str
    rag_policy: str
    topic: str


CONTEXT_DEPENDENT_CLARIFICATIONS = {
    "왜",
    "왜요",
    "이유",
    "무슨말이야",
    "뭔말이야",
    "다시설명",
    "다시설명해줘",
    "잘모르겠어",
    "이해안돼",
}

ORIGINAL_PROBLEM_MARKERS = (
    "이답",
    "정답",
    "오답",
    "내답",
    "선택지",
    "왜맞",
    "왜틀",
)

PRACTICAL_MARKERS = ("실무", "현업", "서비스", "운영", "대용량", "어떻게처리")
DEFINITION_MARKERS = ("뭐", "무엇", "의미", "개념", "뭔데", "설명")


def classify_free_question(text: str) -> FreeQuestionIntent:
    normalized = normalize_question(text)
    topic = extract_topic(text)

    if not normalized or normalized in CONTEXT_DEPENDENT_CLARIFICATIONS:
        return FreeQuestionIntent("clarification", "original_context_mixed", topic)

    if any(marker in normalized for marker in ORIGINAL_PROBLEM_MARKERS):
        return FreeQuestionIntent("original_problem_reason", "original_context_mixed", topic)

    if _is_comparison_question(text, normalized):
        return FreeQuestionIntent("comparison", "latest_question_only", topic)

    if any(marker in normalized for marker in PRACTICAL_MARKERS):
        return FreeQuestionIntent("practical_application", "latest_question_only", topic)

    if any(marker in normalized for marker in DEFINITION_MARKERS):
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic)

    if _has_technical_signal(text) or len(normalized) >= 8:
        return FreeQuestionIntent("related_concept", "latest_question_only", topic)

    return FreeQuestionIntent("clarification", "original_context_mixed", topic)


def normalize_question(text: str) -> str:
    return re.sub(r"[\s?!?.。,]+", "", text.lower())


def extract_topic(text: str) -> str:
    stripped = text.strip()
    if not stripped:
        return ""

    patterns = (
        r"(.+?)(?:이|가|은|는)\s*(?:뭐|무엇|어떤|무슨|의미|개념|뭔데)",
        r"(.+?)(?:이랑|랑|와|과)\s*.+?(?:차이|비교)",
    )
    for pattern in patterns:
        match = re.search(pattern, stripped)
        if match:
            return _clean_topic(match.group(1))

    tokens = re.findall(r"[A-Za-z0-9+#.-]+|[가-힣]{2,}", stripped)
    return _clean_topic(tokens[0]) if tokens else ""


def _clean_topic(value: str) -> str:
    return re.sub(r"[\s?!?.。,]+", "", value.strip())


def _has_technical_signal(text: str) -> bool:
    if re.search(r"[A-Za-z][A-Za-z0-9+#.-]{1,}", text):
        return True
    return bool(re.search(r"[가-힣]{4,}", text))


def _is_comparison_question(text: str, normalized: str) -> bool:
    if "차이" in normalized or "vs" in normalized:
        return True
    return bool(re.search(r"(.+?)(이랑|랑|와|과)\s*.+?(비교|차이)", text))

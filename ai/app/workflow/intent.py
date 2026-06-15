from dataclasses import dataclass
import re


@dataclass(frozen=True)
class FreeQuestionIntent:
    intent: str
    rag_policy: str
    topic: str
    confidence: float = 0.7
    context_dependent: bool = False
    sub_intent: str = "definition"


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
ENGLISH_DEFINITION_PATTERN = re.compile(r"^\s*(?:what\s+is|what's|explain)\s+(.+?)\s*\??\s*$", re.IGNORECASE)
# 목적격 의문사("무엇을"/"뭘")는 정의가 아니라 특정 내용·행위를 묻는다.
# 정의 템플릿으로 답하면 비응답이 되므로 별도 intent로 분류해 fast-path를 건너뛴다.
SPECIFIC_GUIDANCE_MARKERS = ("무엇을", "무엇과", "무엇이랑", "무엇부터", "뭘")
DISCOURSE_MARKERS = ("혹시", "만약", "근데", "그럼", "그러면", "저기", "음", "아", "그리고", "그래서")
FILLER_WORDS = {"좀", "요", "네", "음", "그", "이제"}
GENERIC_SINGLETON_TOPICS = {"ai", "db", "it"}


def classify_free_question_rule_based(text: str) -> FreeQuestionIntent:
    canonical_text = canonicalize_question(text)
    normalized = normalize_question(canonical_text)
    topic = extract_topic(canonical_text)
    confidence = topic_confidence(topic, canonical_text)

    if not normalized or normalized in CONTEXT_DEPENDENT_CLARIFICATIONS:
        return FreeQuestionIntent("follow_up", "original_context_mixed", topic, confidence, False, "follow_up")

    context_dependent = is_context_dependent_question(text)

    if context_dependent:
        return FreeQuestionIntent("wrong_answer_explanation", "original_context_mixed", topic, confidence, True, "explanation")

    if any(marker in normalized for marker in ORIGINAL_PROBLEM_MARKERS):
        return FreeQuestionIntent("wrong_answer_explanation", "original_context_mixed", topic, confidence, context_dependent, "explanation")

    if _is_comparison_question(canonical_text, normalized):
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic, confidence, context_dependent, "comparison")

    if any(marker in normalized for marker in PRACTICAL_MARKERS):
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic, confidence, context_dependent, "practical")

    if _is_specific_guidance_question(normalized):
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic, confidence, context_dependent, "definition")

    if any(marker in normalized for marker in DEFINITION_MARKERS) or _is_english_definition_question(canonical_text):
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic, confidence, context_dependent, "definition")

    if _has_technical_signal(canonical_text) or len(normalized) >= 8:
        return FreeQuestionIntent("concept_definition", "latest_question_only", topic, confidence, context_dependent, "related")

    return FreeQuestionIntent("general_question", "original_context_mixed", topic, confidence, context_dependent, "general")



def canonicalize_question(text: str) -> str:
    stripped = text.strip()
    previous = None
    while stripped and stripped != previous:
        previous = stripped
        for marker in DISCOURSE_MARKERS:
            pattern = rf"^{re.escape(marker)}(?:[\s,?!?.。]+|$)"
            stripped = re.sub(pattern, "", stripped, count=1).strip()

    parts = []
    for part in stripped.split():
        bare = re.sub(r"^[,?!?.。]+|[,?!?.。]+$", "", part)
        if bare in FILLER_WORDS:
            continue
        parts.append(part)

    return re.sub(r"\s+", " ", " ".join(parts)).strip()


def is_context_dependent_question(text: str) -> bool:
    canonical = canonicalize_question(text)
    normalized = normalize_question(canonical)
    original_normalized = normalize_question(text)

    if not normalized:
        return False

    context_references = (
        "이건",
        "이게",
        "이문제",
        "내답변",
        "방금",
        "다시쉽게",
        "왜틀",
        "왜답",
        "뭐가부족",
    )
    if any(marker in original_normalized for marker in context_references):
        return True

    return any(marker in normalized for marker in ("이건왜틀", "이게답", "다시설명", "쉽게말해"))


def topic_confidence(topic: str, text: str) -> float:
    cleaned_topic = _clean_topic(topic).lower()
    if not cleaned_topic:
        return 0.4
    if cleaned_topic in GENERIC_SINGLETON_TOPICS:
        return 0.85
    if " " in topic.strip() or len(cleaned_topic) >= 3:
        return 0.95
    if _has_technical_signal(text):
        return 0.95
    return 0.7


def normalize_question(text: str) -> str:
    return re.sub(r"[\s?!?.。,]+", "", text.lower())


def extract_topic(text: str) -> str:
    stripped = canonicalize_question(text)
    if not stripped:
        return ""

    patterns = (
        ENGLISH_DEFINITION_PATTERN,
        r"(.+?)(?:이|가|은|는)\s*(?:뭐|무엇|어떤|무슨|의미|개념|뭔데)",
        r"(.+?)(?:이랑|랑|와|과)\s*.+?(?:차이|비교)",
    )
    for pattern in patterns:
        match = pattern.search(stripped) if hasattr(pattern, "search") else re.search(pattern, stripped)
        if match:
            return _clean_topic(match.group(1))

    tokens = re.findall(r"[A-Za-z0-9+#.-]+|[가-힣]{2,}", stripped)
    return _clean_topic(tokens[0]) if tokens else ""


def _clean_topic(value: str) -> str:
    value = canonicalize_question(value)
    value = re.sub(r"[?!?.。,]+", "", value.strip())
    return re.sub(r"\s+", " ", value).strip()


def _has_technical_signal(text: str) -> bool:
    if re.search(r"[A-Za-z][A-Za-z0-9+#.-]{1,}", text):
        return True
    return bool(re.search(r"[가-힣]{4,}", text))


def _is_specific_guidance_question(normalized: str) -> bool:
    if "의미" in normalized or "개념" in normalized:
        return False
    return any(marker in normalized for marker in SPECIFIC_GUIDANCE_MARKERS)


def _is_english_definition_question(text: str) -> bool:
    return bool(ENGLISH_DEFINITION_PATTERN.search(text))


def _is_comparison_question(text: str, normalized: str) -> bool:
    if "차이" in normalized or "vs" in normalized:
        return True
    return bool(re.search(r"(.+?)(이랑|랑|와|과)\s*.+?(비교|차이)", text))

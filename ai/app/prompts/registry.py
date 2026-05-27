import hashlib
import re

# Central Registry storing prompt metadata, templates or strategies, aliases, and rollback history
PROMPT_REGISTRY = {
    "first_question_v1": {
        "name": "first_question",
        "version": "1.0.0",
        "alias": "latest",
        "description": "First diagnostic test review follow-up question template"
    },
    "follow_up_v1": {
        "name": "follow_up",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Follow-up question feedback and question template"
    },
    "free_question_v1": {
        "name": "free_question",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Generic free question template with full background context"
    },
    "concept_definition_v1": {
        "name": "concept_definition",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Pure concept/definition question template with minimized background context"
    },
    "wrong_answer_explanation_v1": {
        "name": "wrong_answer_explanation",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Wrong answer explanation template with active context detail"
    },
    "follow_up_intent_v1": {
        "name": "follow_up_intent",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Follow-up tail question intent template"
    },
    "retry_v1": {
        "name": "retry",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Retry generation template with background context completely removed"
    },
    "semantic_judge_v1": {
        "name": "semantic_judge",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Semantic quality and bias assessment LLM judge template"
    },
    "grounding_judge_v1": {
        "name": "grounding_judge",
        "version": "1.0.0",
        "alias": "latest",
        "description": "RAG evidence semantic grounding LLM judge template"
    }
}


def compute_prompt_hash(prompt_text: str) -> str:
    """
    동일 prompt에 대해 항상 동일한 해시값을 보장합니다.
    - 모든 연속된 공백문자(공백, 탭, 개행, 캐리지 리턴)를 단일 스페이스 ' '로 정규화합니다.
    - 양 끝의 공백을 제거합니다.
    - SHA-256 해시를 적용하여 16진수 문자열로 반환합니다.
    """
    if not prompt_text:
        return ""
    # Normalize newlines and any sequence of whitespace to a single space
    normalized = re.sub(r"\s+", " ", prompt_text).strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def lookup_prompt_version(alias: str, category: str) -> str:
    """
    주어진 alias와 category에 매칭되는 프롬프트 버전을 찾아 반환합니다.
    매칭되는 항목이 없으면 기본 {category}_v1 버전을 반환하여 하위 호환성을 보장합니다.
    """
    for version, meta in PROMPT_REGISTRY.items():
        if meta["name"] == category and meta["alias"] == alias:
            return version
    return f"{category}_v1"

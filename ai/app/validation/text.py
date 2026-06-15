import re

from app.schemas import AiGenerateRequest
from app.guardrails import mask_pii


def strip_thinking(answer: str) -> str:
    while "<think>" in answer and "</think>" in answer:
        start = answer.find("<think>")
        end = answer.find("</think>", start)
        if end < 0:
            break
        answer = answer[:start] + answer[end + len("</think>"):]
    return answer.strip()


def compact_answer(answer: str, mode: str) -> str:
    paragraphs = [paragraph.strip() for paragraph in answer.split("\n\n") if paragraph.strip()]
    unique_paragraphs: list[str] = []
    seen: set[str] = set()

    for paragraph in paragraphs:
        normalized = " ".join(paragraph.split())
        if normalized in seen:
            continue
        seen.add(normalized)
        unique_paragraphs.append(paragraph)

    compacted = "\n\n".join(unique_paragraphs).strip()
    if not compacted:
        return answer.strip()

    sentence_limit = 3 if mode == "free-question" else 2
    return limit_sentences(compacted, sentence_limit)


def limit_sentences(text: str, sentence_limit: int) -> str:
    sentence_count = 0
    result: list[str] = []

    for index, char in enumerate(text):
        result.append(char)
        previous = text[index - 1] if index > 0 else ""
        following = text[index + 1] if index + 1 < len(text) else ""
        identifier_dot = char == "." and previous.isalnum() and following.isalnum()
        if char in ".!?" and not identifier_dot:
            sentence_count += 1
            if sentence_count >= sentence_limit:
                break

    return "".join(result).strip()


def contains_korean(text: str) -> bool:
    return any("\uac00" <= char <= "\ud7a3" for char in text)


def korean_ratio(text: str) -> float:
    letters = [char for char in text if char.isalpha()]
    if not letters:
        return 0.0
    korean = [char for char in letters if "\uac00" <= char <= "\ud7a3"]
    return len(korean) / len(letters)


def korean_fallback(mode: str, request: AiGenerateRequest) -> str:
    correct = request.correct_answer or "정답 선택지"
    selected = request.selected_answer or "내 선택지"

    if mode == "first-question":
        return "왜 그 선택지를 골랐는지 한 문장으로 설명해볼까요? 정답과 내 선택의 차이를 떠올리면서 답해보세요."

    if mode == "follow-up":
        return (
            f"핵심은 `{correct}`와 `{selected}`가 가리키는 개념 차이를 구분하는 거예요. "
            "내 답변에서 어떤 기준이 빠졌는지 한 문장으로 다시 정리해볼까요?"
        )

    return (
        f"`{correct}`가 왜 맞는지와 `{selected}`가 어떤 개념을 놓쳤는지를 먼저 나눠보면 좋아요. "
        "질문한 내용은 이 차이를 기준으로 다시 보면 더 쉽게 정리됩니다."
    )


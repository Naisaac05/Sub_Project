import json
import os
import urllib.error
import urllib.request

from app.schemas import AiGenerateRequest, AiGenerateResponse

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_REQUEST_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_REQUEST_TIMEOUT_SECONDS", "150"))


def generate_review_answer(mode: str, request: AiGenerateRequest) -> AiGenerateResponse:
    prompt = build_prompt(mode, request)
    max_tokens = min(request.max_tokens, max_tokens_for_mode(mode))
    answer = call_ollama(
        model=request.model,
        prompt=prompt,
        temperature=request.temperature,
        max_tokens=max_tokens,
        num_ctx=request.num_ctx,
        num_thread=request.num_thread,
    )
    answer = compact_answer(answer, mode)
    if not contains_korean(answer):
        answer = korean_fallback(mode, request)
    return AiGenerateResponse(answer=answer)


def build_prompt(mode: str, request: AiGenerateRequest) -> str:
    options = format_options(request.options)

    if mode == "first-question":
        return f"""
You are a Korean programming mentor.
Ask one short follow-up question for a learner who got a diagnostic test question wrong.
Rules:
- Korean only.
- 2 sentences maximum.
- Do not write reasoning steps or <think> tags.
- Do not reveal the full answer yet.
- Ask why they chose their answer and what concept they used.
- Ask exactly one question.
- Stop after the question.

[Question]
{request.question}

[Selected Answer]
{request.selected_answer}

[Correct Answer]
{request.correct_answer}
""".strip()

    if mode == "follow-up":
        return f"""
You are a Korean programming mentor helping with a diagnostic test review.
Give feedback on the learner's answer and ask exactly one next follow-up question.
Rules:
- Korean only.
- 3 sentences maximum.
- Do not write reasoning steps or <think> tags.
- Be specific to the learner answer.
- If the learner says they do not know, briefly explain the missing concept first.
- Do not be too verbose.
- Do not use markdown tables.
- Ask exactly one next question.
- Stop after the next question.

[Question]
{request.question}

[Options]
{options}

[Selected Answer]
{request.selected_answer}

[Correct Answer]
{request.correct_answer}

[Learner Answer]
{request.user_answer}

[Rule Evaluation]
{request.evaluation}

[Follow-up Step]
{request.step}
""".strip()

    return f"""
You are a Korean programming mentor.
Answer the learner's free question using the diagnostic test context.
Rules:
- Korean only.
- 3 sentences maximum.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- If the question is unrelated, gently connect it back to the concept.
- Do not grade the learner.
- Do not ask more than one follow-up question.
- Stop after the answer.

[Original Question]
{request.question}

[Options]
{options}

[Selected Answer]
{request.selected_answer}

[Correct Answer]
{request.correct_answer}

[Learner Free Question]
{request.user_answer}
""".strip()


def call_ollama(model: str, prompt: str, temperature: float, max_tokens: int, num_ctx: int, num_thread: int) -> str:
    body = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "think": False,
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens,
            "num_ctx": num_ctx,
            "num_thread": num_thread,
            "repeat_penalty": 1.25,
            "repeat_last_n": 128,
            "stop": ["\n\n\n", "[Question]", "[Original Question]", "[Learner Free Question]"],
        },
    }
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=OLLAMA_REQUEST_TIMEOUT_SECONDS) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError("Ollama request failed") from exc

    answer = str(payload.get("response", "")).strip()
    if not answer:
        raise RuntimeError("Ollama returned an empty response")
    return strip_thinking(answer)


def max_tokens_for_mode(mode: str) -> int:
    if mode == "first-question":
        return 80
    if mode == "follow-up":
        return 140
    return 180


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

    for char in text:
        result.append(char)
        if char in ".!?\n":
            sentence_count += 1
            if sentence_count >= sentence_limit:
                break

    return "".join(result).strip()


def contains_korean(text: str) -> bool:
    return any("\uac00" <= char <= "\ud7a3" for char in text)


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


def format_options(options: list[str]) -> str:
    if not options:
        return ""
    return "\n".join(f"{index + 1}. {option}" for index, option in enumerate(options))

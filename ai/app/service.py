import json
import os
import urllib.error
import urllib.request

from app.schemas import AiGenerateRequest, AiGenerateResponse

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")


def generate_review_answer(mode: str, request: AiGenerateRequest) -> AiGenerateResponse:
    prompt = build_prompt(mode, request)
    answer = call_ollama(
        model=request.model,
        prompt=prompt,
        temperature=request.temperature,
        max_tokens=request.max_tokens,
    )
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
- Do not reveal the full answer yet.
- Ask why they chose their answer and what concept they used.

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
- 4 sentences maximum.
- Be specific to the learner answer.
- If the learner says they do not know, briefly explain the missing concept first.
- Do not be too verbose.
- Do not use markdown tables.

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
- 5 sentences maximum.
- Explain with a small concrete example if helpful.
- If the question is unrelated, gently connect it back to the concept.
- Do not grade the learner.

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


def call_ollama(model: str, prompt: str, temperature: float, max_tokens: int) -> str:
    body = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens,
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
        with urllib.request.urlopen(request, timeout=75) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError("Ollama request failed") from exc

    answer = str(payload.get("response", "")).strip()
    if not answer:
        raise RuntimeError("Ollama returned an empty response")
    return answer


def format_options(options: list[str]) -> str:
    if not options:
        return ""
    return "\n".join(f"{index + 1}. {option}" for index, option in enumerate(options))

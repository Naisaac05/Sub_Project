from app.schemas import AiGenerateRequest


PROMPT_VERSIONS = {
    "first-question": "first_question_v1",
    "follow-up": "follow_up_v1",
    "free-question": "free_question_v1",
}


def prompt_version_for_mode(mode: str) -> str:
    return PROMPT_VERSIONS.get(mode, "free_question_v1")


def build_prompt(mode: str, request: AiGenerateRequest, context: str = "") -> str:
    options = format_options(request.options)
    context_block = f"\n\n[Retrieved Context]\n{context}" if context else ""

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
{request.correct_answer}{context_block}
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
{request.step}{context_block}
""".strip()

    return f"""
You are a Korean programming mentor.
The learner's latest question is: {request.user_answer}
Answer that latest question first in Korean only.
Use the background test context only if it directly helps answer the latest question.
If the latest question asks about a separate concept, ignore the background context.
Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Do not grade the learner.
- Stop after the answer.

Background original test question: {request.question}
Background options:
{options}
Background selected answer: {request.selected_answer}
Background correct answer: {request.correct_answer}
{context_block}
""".strip()


def format_options(options: list[str]) -> str:
    if not options:
        return ""
    return "\n".join(f"{index + 1}. {option}" for index, option in enumerate(options))


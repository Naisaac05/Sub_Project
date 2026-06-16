from app.schemas import AiGenerateRequest
from app.workflow.intent import FreeQuestionIntent
from app.prompts.registry import compute_prompt_hash, lookup_prompt_version, PROMPT_REGISTRY

PROMPT_VERSIONS = {
    "first-question": "first_question_v1",
    "follow-up": "follow_up_v1",
    "free-question": "free_question_v1",
}


def prompt_version_for_mode(mode: str, intent: FreeQuestionIntent | None = None) -> str:
    if mode == "first-question":
        return "first_question_v1"
    if mode == "follow-up":
        return "follow_up_v1"
    if mode == "free-question":
        if intent:
            if intent.intent == "concept_definition":
                return "concept_definition_v1"
            elif intent.intent == "wrong_answer_explanation":
                return "wrong_answer_explanation_v1"
            elif intent.intent == "follow_up":
                return "follow_up_intent_v1"
        return "free_question_v1"
    return "free_question_v1"


def prompt_strategy_for_mode(mode: str, intent: FreeQuestionIntent | None = None) -> str:
    if mode == "first-question":
        return "first-question"
    if mode == "follow-up":
        return "follow-up"
    if mode == "free-question":
        if intent:
            return f"free-question:{intent.intent}:context_dependent={intent.context_dependent}"
        return "free-question:generic"
    return f"unknown:{mode}"


def build_prompt(
    mode: str,
    request: AiGenerateRequest,
    context: str = "",
    intent: FreeQuestionIntent | None = None,
) -> str:
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

[Previous AI Question]
{request.previous_ai_question}

[Active Concept]
{request.active_concept}

[Follow-up Type]
{request.follow_up_type}

[Rule Evaluation]
{request.evaluation}

[Follow-up Step]
{request.step}{context_block}
""".strip()

    # mode == "free-question"
    if intent:
        if intent.intent == "concept_definition":
            if not intent.context_dependent:
                return f"""
You are a Korean programming mentor.
The learner's latest question is: {request.user_answer}
Answer that latest question first in Korean only.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Do not grade the learner.
- Stop after the answer.

Important Specific Rules:
1. 사용자의 현재 질문 의도를 가장 우선한다.
2. 배경 문제 context는 참고자료일 뿐이며, 현재 질문이 개념 정의라면 배경 문제의 선지 비교로 답변하지 않는다.
3. 사용자가 "각각 무슨 뜻인지", "개념이 뭐야", "의미가 뭐야"라고 물으면 각 용어를 독립적으로 정의한다.
4. 기존 문제의 틀린 선지나 정답 선지를 먼저 설명하지 않는다.
{context_block}
""".strip()
            else:
                topic_hint = f"\n[Hint] 현재 학습/질문하고 있는 주제 맥락은 '{request.correct_answer or '백엔드 관련 개념'}'에 연관되어 있습니다."
                return f"""
You are a Korean programming mentor.
The learner's latest question is: {request.user_answer}
Answer that latest question first in Korean only.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Do not grade the learner.
- Stop after the answer.

Important Specific Rules:
1. 사용자의 현재 질문 의도를 가장 우선한다.
2. 배경 문제 context는 참고자료일 뿐이며, 현재 질문이 개념 정의라면 배경 문제의 선지 비교로 답변하지 않는다.
3. 사용자가 "각각 무슨 뜻인지", "개념이 뭐야", "의미가 뭐야"라고 물으면 각 용어를 독립적으로 정의한다.
4. 기존 문제의 틀린 선지나 정답 선지를 먼저 설명하지 않는다.
{topic_hint}
{context_block}
""".strip()

        elif intent.intent == "wrong_answer_explanation":
            if not intent.context_dependent:
                return f"""
You are a Korean programming mentor helping a learner understand programming concepts.
The learner's latest question is: {request.user_answer}
Explain the concepts in Korean only.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Stop after the answer.
{context_block}
""".strip()
            else:
                return f"""
You are a Korean programming mentor helping a learner understand their diagnostic test result.
The learner's latest question is: {request.user_answer}
Explain the concepts, background question, options, and why the selected answer was wrong or the correct answer is right in Korean only.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Stop after the answer.

Background test context:
- Question: {request.question}
- Options:
{options}
- Selected Answer (Learner's wrong choice): {request.selected_answer}
- Correct Answer: {request.correct_answer}
{context_block}
""".strip()

        elif intent.intent == "follow_up":
            previous_summary = f"\n- 직전 꼬리 질문/답변 흐름: {request.user_answer} (평가 결과: {request.evaluation or '진행 중'})" if request.user_answer else ""
            return f"""
You are a Korean programming mentor helping with a diagnostic test review.
The learner's latest question is a follow-up: {request.user_answer}
Provide active concept-centered feedback and answer their follow-up question.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Stop after the answer.
{previous_summary}
{context_block}
""".strip()

    if intent and not intent.context_dependent:
        return f"""
You are a Korean programming mentor.
The learner's latest question is: {request.user_answer}
Answer that latest question first in Korean only.

Rules:
- Korean only.
- 3 sentences maximum.
- Do not copy section labels.
- Do not write reasoning steps or <think> tags.
- Explain with a small concrete example if helpful.
- Do not grade the learner.
- Stop after the answer.

{context_block}
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

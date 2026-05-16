# AI review Ollama speed design

> Goal: keep `qwen3:4b-q4_K_M` for answer quality while reducing local CPU latency by shrinking the model workload.

## Problem

The AI review flow uses short tutoring answers, but the Ollama prompt includes more context than the model needs. On a 4-core CPU laptop this increases first-token and total response time. Lowering the model size would improve speed, but it also risks answer quality.

## Strategy

Use the existing backend as the source of truth and make Qwen only phrase the response:

- Keep Qwen 4B as the default model.
- Send only the question, selected answer, correct answer, learner answer, and rule evaluation.
- Do not send the full option list unless it is necessary.
- Tell the model the backend facts are authoritative.
- Limit output to 2-3 short Korean sentences.
- Cache identical Ollama prompts in memory so repeated free questions or retries do not call the model again.
- Tune defaults for a 4-core CPU: lower output tokens and context window while preserving the 4B model.

## Non-goals

- No OpenAI usage.
- No DB schema change.
- No Redis cache.
- No smaller model default.

## Acceptance

- Default provider remains Ollama.
- Default model remains `qwen3:4b-q4_K_M`.
- Backend compiles.
- Repeated identical prompts are served from cache.
- Prompt size is visibly shorter than the previous full-context prompt.

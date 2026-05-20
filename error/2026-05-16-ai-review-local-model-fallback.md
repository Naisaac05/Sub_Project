# AI review used fallback instead of local Ollama model

- Date: 2026-05-16
- Area: ai
- Severity: medium

## Symptoms

The Python server returned `200 OK`, but free-question answers looked like generic template responses instead of local AI answers. Direct Python API inspection showed `model_used: "template"` and `fallback_used: true`.

## Cause

Two issues combined:

- The configured Python AI model was `qwen3:1.7b`, but the only installed Ollama model was `qwen3:4b-q4_K_M`. Ollama returned `model 'qwen3:1.7b' not found`.
- The Ollama stop sequences included prompt section labels such as `[Learner Free Question]`. The local model sometimes started its answer with that label, so Ollama stopped immediately and returned an empty response. Python treated that as `Ollama returned an empty response` and used the template fallback.

The free-question prompt also encouraged the small model to copy section labels, which made answer quality worse.

## Fix

- Updated Python AI defaults to the installed model: `ai/app/schemas.py:5`, `ai/app/ollama/client.py:13`.
- Updated Spring Boot and local env defaults to the installed model: `backend/src/main/resources/application.yml:75`, `.env:45`.
- Removed prompt section labels from Ollama stop sequences: `ai/app/ollama/client.py:61`.
- Simplified the free-question prompt so the latest learner question is a plain sentence, not a copyable bracketed label: `ai/app/prompts.py:81`.
- Added regression tests for the installed model default and stop sequence behavior: `ai/tests/test_schemas.py:6`, `ai/tests/test_ollama_client.py:6`.

## Prevention / Notes

After changing `application.yml` or `.env`, restart the Spring Boot process so it stops sending stale model names. The Python server can reload code, but environment variables and the Java backend config are not guaranteed to refresh without a process restart.

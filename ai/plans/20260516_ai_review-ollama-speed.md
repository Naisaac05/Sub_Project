---
type: plan
category: ollama
status: active
updated: 2026-06-18
description: "AI 리뷰 Ollama 추론 속도 최적화 및 개선 계획"

---

# AI review Ollama speed implementation plan

> Spec: `docs/superpowers/specs/2026-05-16-ai-review-ollama-speed-design.md`

**Goal:** Keep Qwen 4B quality, but reduce local CPU latency with short grounded prompts, smaller generation settings, and in-memory response caching.

## Tasks

- [x] Document the speed strategy in a superpowers spec.
- [x] Replace verbose Ollama prompts with compact grounded prompts.
- [x] Add bounded in-memory cache to `OllamaAiReviewClient`.
- [x] Tune default Ollama settings for a 4-core CPU laptop.
- [x] Compile backend.
- [x] Record the root-cause/fix in `error/`.

## Verification

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat compileJava
```

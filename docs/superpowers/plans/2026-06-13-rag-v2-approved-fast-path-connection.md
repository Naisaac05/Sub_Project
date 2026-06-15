# RAG v2 Approved Payload Fast Path Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development while implementing this plan. Do not commit, push, approve additional cards, or activate the full v2 store.

**Goal:** Allow only the five explicitly approved v2 cards and their approved payloads to serve as an optional Fast Path while keeping v1 as the default operational store.

**Architecture:** Keep the existing v1 retrieval and generated-card flow unchanged. Add a separate, feature-flagged v2 approved-payload resolver at the beginning of answer generation; it loads only allowlisted approved v2 cards, selects only an approved payload matching the resolved intent, and returns no result on every miss or policy failure so the existing v1/Ollama path continues unchanged.

**Tech Stack:** Python, Pydantic, unittest/pytest, existing lexical retriever and workflow state

---

## Safety Invariants

- `ACTIVE_CARD_STORE` remains v1 or unset.
- `load_concept_cards()` with no explicit root continues to load v1.
- The full `concepts_v2` store is never added to default retrieval.
- Only this allowlist is eligible:
  - `frontend-react-key`
  - `java-equals`
  - `spring-spring-question-59`
  - `java-extends`
  - `python-with`
- A card is eligible only when `review.card_status == approved`.
- A payload is eligible only when its own `review.payload_status[intent] == approved`.
- Draft, rejected, missing, malformed, or non-allowlisted cards always return a v2 miss.
- A v2 miss never prevents existing v1 retrieval, existing Fast Paths, cache, or Ollama generation.
- No implementation step changes card JSON, v1 concepts, or `ACTIVE_CARD_STORE`.

## Proposed Feature Flags

| Flag | Default | Purpose |
| --- | --- | --- |
| `AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED` | `false` | Master kill switch. When false, no v2 files are read by the workflow. |
| `AI_REVIEW_V2_APPROVED_FAST_PATH_MODE` | `shadow` | `shadow` records the decision but never serves it; `serve` may return an approved payload. Invalid values behave as `off`. |
| `AI_REVIEW_V2_APPROVED_FAST_PATH_CARD_IDS` | Exact five-card allowlist | Additional deployment allowlist. Runtime configuration may narrow but may not expand beyond the code safety allowlist. |
| `AI_REVIEW_V2_APPROVED_FAST_PATH_MIN_SCORE` | Calibration-owned config value | Minimum retrieval confidence. It must remain configuration, not a question-specific exception. |

Activation must happen in two independent steps:

1. Enable `shadow` mode and compare decisions while v1/Ollama still serves every answer.
2. Separately approve `serve` mode only after shadow metrics and rollback readiness pass.

Setting `ACTIVE_CARD_STORE=v2` is never part of this plan.

## Request Flow

```text
Question
  → existing intent classification
  → v2 approved Fast Path resolver (feature-flagged, isolated root)
      → filter code allowlist
      → require approved card
      → retrieve Top1 from eligible v2 cards
      → require topic/card match and configured score gate
      → map intent to payload
      → require approved payload and non-empty content
      → shadow: record only, continue
      → serve: return v2 approved Fast Path response
  → on every miss: existing v1 static/generated Fast Path
  → existing v1 retrieval / cache / Ollama generation
```

## Intent-To-Payload Mapping

| Workflow intent | v2 payload |
| --- | --- |
| Concept definition request | `CONCEPT_DEFINITION.content` |
| Correct-answer reason request | `ANSWER_REASON.why_correct`, with `key_points` when useful |
| Wrong-answer explanation request | `WRONG_ANSWER_REASON`, selecting the matching option when available and otherwise using approved common mistakes |

Unsupported, ambiguous, follow-up, off-topic, comparison, practical, example, and debug intents return a v2 miss. They must not use a generated payload merely because the card is approved.

## File Structure

### Create

- `ai/app/workflow/v2_approved_fast_path.py`
  - Owns flags, immutable allowlist, approved-card loading, retrieval, intent-to-payload selection, and a structured hit/miss result.
- `ai/tests/test_v2_approved_fast_path.py`
  - Focused policy and resolver tests.

### Modify

- `ai/app/workflow/nodes.py`
  - Calls the resolver before the existing lightweight/v1/Ollama answer path.
  - In shadow mode records metadata only.
  - In serve mode returns only a valid approved-payload hit.
- `ai/app/workflow/state.py`
  - Adds optional shadow/serve decision metadata without changing existing fields.
- `ai/app/schemas/__init__.py`
  - Adds optional response observability fields only if needed; defaults preserve existing API responses.
- `ai/tests/test_workflow_runner.py`
  - Adds end-to-end fallback and serve-mode coverage.
- `ai/tests/test_workflow_degraded_modes.py`
  - Verifies v2 miss does not interfere with existing degraded and fallback behavior.

## Task 1: Add Resolver Policy Tests

**Files:**
- Create: `ai/tests/test_v2_approved_fast_path.py`

- [ ] Test master flag disabled: loader is not called and result is a miss.
- [ ] Test a non-allowlisted approved card is ignored.
- [ ] Test an allowlisted draft card is ignored.
- [ ] Test an approved card with a draft selected payload is ignored.
- [ ] Test an approved card with an approved selected payload returns a hit.
- [ ] Test a missing or empty selected payload returns a miss.
- [ ] Test unsupported and ambiguous intents return a miss.
- [ ] Test runtime allowlist can narrow but cannot expand the immutable five-card allowlist.
- [ ] Run focused tests and confirm RED because the resolver does not exist.

## Task 2: Implement Isolated Approved-Only Lookup

**Files:**
- Create: `ai/app/workflow/v2_approved_fast_path.py`

- [ ] Define immutable code allowlist containing exactly the five approved card IDs.
- [ ] Read `concepts_v2` only when the master flag is enabled.
- [ ] Filter cards before retrieval using the immutable and runtime allowlists.
- [ ] Require approved card status before retrieval.
- [ ] Retrieve only against the filtered eligible list.
- [ ] Require configured score gate and existing title/topic match semantics.
- [ ] Map the classified intent to exactly one generated payload.
- [ ] Require approved payload status and non-empty payload content.
- [ ] Return a structured decision containing hit/miss reason, card ID, payload intent, score, latency, and rendered answer.
- [ ] Never mutate cards or workflow state inside the resolver.
- [ ] Run focused tests until green.

## Task 3: Add Shadow-Only Workflow Integration

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/state.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] Add a failing test proving `shadow` mode records a valid v2 hit but still serves the existing v1/Ollama result.
- [ ] Add a failing test proving a v2 miss leaves the existing route, contexts, model, and answer unchanged.
- [ ] Call the resolver at the beginning of `generate_answer_node`, after intent classification is available.
- [ ] Store only shadow observability metadata in `shadow` mode.
- [ ] Do not short-circuit generation in shadow mode.
- [ ] Run workflow tests until green.

## Task 4: Add Separately Gated Serve Mode

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/schemas/__init__.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] Add a failing test proving `serve` mode returns an approved v2 payload without calling Ollama.
- [ ] Add a failing test proving draft payload, miss, low score, malformed JSON, and loader exception all fall through to existing v1/Ollama behavior.
- [ ] On valid serve hit, set an explicit route such as `v2_approved_fast_path`.
- [ ] Preserve existing response fields and add provenance: v2 card ID, payload intent, score, and feature mode.
- [ ] Never place all v2 cards into `state.contexts` or the default retriever.
- [ ] Run workflow and degraded-mode tests until green.

## Task 5: Observability And Shadow Evaluation

**Files:**
- Modify: `ai/app/observability.py`
- Modify: `ai/scripts/shadow_rag_cards_v2.py`
- Test: `ai/tests/test_shadow_rag_cards_v2.py`

- [ ] Record v2 decision reason: disabled, miss, non-allowlisted, card-not-approved, payload-not-approved, score-gate, hit, or exception.
- [ ] Record whether the existing path would call Ollama.
- [ ] Compare shadow-selected answer provenance with the actually served v1/Ollama route.
- [ ] Report hit rate, fallback rate, false-hit review list, latency, and per-card coverage.
- [ ] Keep answer content out of normal logs unless existing redaction policy explicitly permits it.

## Task 6: Verification Plan

Run with the feature disabled:

```powershell
$env:AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED='false'
.\ai\.venv\Scripts\python.exe -m pytest ai/tests/test_workflow_runner.py ai/tests/test_workflow_degraded_modes.py -q
```

Expected: existing behavior and failure baseline are unchanged; v2 loader is not called.

Run in shadow mode:

```powershell
$env:AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED='true'
$env:AI_REVIEW_V2_APPROVED_FAST_PATH_MODE='shadow'
.\ai\.venv\Scripts\python.exe ai\scripts\shadow_rag_cards_v2.py
```

Expected: v1/Ollama still serves responses; v2 decisions are recorded only.

Run focused policy and workflow tests:

```powershell
.\ai\.venv\Scripts\python.exe -m pytest ai/tests/test_v2_approved_fast_path.py ai/tests/test_workflow_runner.py ai/tests/test_workflow_degraded_modes.py ai/tests/test_shadow_rag_cards_v2.py -q
```

Expected:

- draft payload usage: 0
- non-allowlisted v2 card usage: 0
- valid five-card Shadow hits: expected coverage
- miss/error fallback: existing route preserved
- no new workflow failures

Final safety checks:

```powershell
.\ai\.venv\Scripts\python.exe ai\app\scripts\migrate_rag_cards.py --validate-only
git diff --check
```

Also verify:

- `ACTIVE_CARD_STORE` remains v1 or unset.
- v1 concepts tree hash remains unchanged.
- No card status or payload status changes occurred.
- No commit or push occurred.

## Rollback

Runtime rollback requires only disabling the dedicated flag:

```text
AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED=false
```

The disabled branch must avoid reading v2 and immediately use the existing path. No card rollback and no `ACTIVE_CARD_STORE` change are required.

If serve mode has been enabled:

1. Set mode to `shadow` or disable the master flag.
2. Restart/reload configuration using the existing deployment mechanism.
3. Verify route `v2_approved_fast_path` count drops to zero.
4. Verify existing v1/Ollama success and fallback rates return to baseline.
5. Investigate without changing card approvals unless a separate rollback approval is issued.

## Expected Risks

- **Intent mapping ambiguity:** Existing workflow intents do not always cleanly distinguish correct-answer from wrong-answer reason. Incorrect mapping could serve the wrong approved payload.
- **False-positive retrieval:** A Top1 card may be approved but irrelevant. The score gate and title/topic match reduce but do not eliminate this risk.
- **Content quality:** Approval status confirms review policy, but payload text may still be too generic for some paraphrases.
- **Mixed-format loader behavior:** v1 Markdown and JSON plus isolated v2 JSON must remain backward-compatible.
- **Latency regression:** Loading and parsing v2 on every request may exceed the Fast Path budget. Cache only the filtered approved allowlist and provide explicit invalidation.
- **Stale approval cache:** A cached approved card may remain usable after status rollback unless cache invalidation is tested.
- **Silent exception fallback:** Fail-open behavior protects availability but can hide repeated v2 loader failures. Observability must count miss reasons and exceptions.
- **Configuration drift:** Runtime allowlist or mode differences across instances can produce inconsistent responses.
- **Accidental full activation:** Reusing `ACTIVE_CARD_STORE` or the default retriever for this feature would violate isolation; tests must assert this never occurs.

## Activation Gates

Shadow connection implementation is ready for execution only after separate approval.

Serve mode must remain disabled until all are true:

- Shadow mode shows no draft/non-allowlisted payload use.
- The five approved cards meet agreed hit and false-positive targets.
- Existing workflow regression has no new failures.
- v2 lookup latency remains within budget.
- Kill-switch rollback is tested in the deployed environment.
- Monitoring and ownership for v2 miss/error reasons are defined.


# AI Review Typo Alias Resolver Design

## Goal

Phase 4.9 adds a lightweight typo and alias resolver for learner free questions so small spelling mistakes or alternate names still reach the right fast path answer and RAG concept card.

## Scope

The resolver runs only inside the Python AI server. It does not call an LLM, does not change the Spring/Next API contract, and does not auto-promote new knowledge. It focuses on high-confidence concept terms already known to the lightweight answer layer or approved concept cards.

## Architecture

Add a focused `app.workflow.query_resolver` module. It receives the learner question, compares it against a small registry of canonical concept terms, aliases, and known typo variants, and returns a `ResolvedQuery` object with the resolved query, concept id, matched term, correction type, and confidence.

The workflow uses the resolved query before intent classification, fast path answer lookup, and RAG retrieval. High-confidence typo/alias matches are rewritten internally. Low-confidence or unknown text is left unchanged.

## Matching Policy

1. Exact and normalized aliases win first.
2. Known typo variants are allowed when they map to one clear canonical term.
3. Generic fuzzy matching is conservative: only longer Latin tokens are compared, and only strong matches are accepted.
4. Ambiguous or weak matches keep the original question.

## Data Flow

1. User asks a free question.
2. `resolve_learner_query()` returns the original question or a corrected query.
3. `retrieve_context_node()` stores resolver metadata in workflow state.
4. Intent classification and RAG query use the corrected query.
5. `generate_answer_node()` passes the corrected query into `lightweight_answer_for()`.

## Testing

Tests cover exact aliases, known typo correction, weak-match rejection, RAG retrieval after correction, and fast path behavior after correction.

# BGE-M3 Runtime Intent Classifier Design

## Goal

Replace production rule-based free-question intent classification with an Ollama `bge-m3` embedding classifier. Tests must exercise the same production classifier contract. Rule-based classification remains available only as an isolated PoC comparison target.

## Runtime Flow

1. Resolve the learner query.
2. Embed the query with Ollama `bge-m3`.
3. Compare it with cached intent prototype vectors for the 10-class intent taxonomy.
4. Convert the selected 10-class label into the existing `FreeQuestionIntent` workflow contract.
5. Use the converted intent for RAG policy, prompt selection, answer style, and observability.

The supported labels are:

- `ANSWER_REASON`
- `WRONG_ANSWER_REASON`
- `CONCEPT_DEFINITION`
- `COMPARISON`
- `EXAMPLE_REQUEST`
- `PRACTICAL_USAGE`
- `DEBUG_OR_ERROR`
- `FOLLOW_UP`
- `OFF_TOPIC`
- `UNKNOWN`

## Failure And Low-Confidence Policy

The classifier must not call the legacy rule-based classifier as a fallback.

Ollama failure, timeout, malformed vectors, similarity below the configured threshold, or an insufficient score margin returns:

```text
UNKNOWN -> FreeQuestionIntent(
  intent="general_question",
  rag_policy="original_context_mixed",
  context_dependent=False,
  sub_intent="general"
)
```

## Performance

Intent prototype vectors are calculated lazily and cached on disk with the model name and prototype hash, then cached in memory. After warm-up, each classified question requires one embedding request. The classifier does not load evaluation datasets or depend on NumPy at runtime.

## Compatibility Mapping

- Answer-reason intents use `wrong_answer_explanation` and original problem context.
- Definition, comparison, example, practical, and debugging intents use `concept_definition` and the latest question.
- Follow-up uses `follow_up` and original context.
- Off-topic and unknown use `general_question`.

Topic extraction remains a separate query-processing responsibility. It must not decide the intent label.

## Verification

- Unit-test embedding ranking, cache reuse, low confidence, and embedding failure.
- Verify workflow calls the BGE classifier and never the rule classifier.
- Verify prompt/RAG policy receives the mapped intent.
- Run intent, workflow, prompt, embedding, and RAG focused suites, followed by the full AI suite.

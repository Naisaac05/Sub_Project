# Free Question Follow-Up Design

## Goal
Return a short learning follow-up in `nextQuestion` after `FREE_QUESTION` answers so the AI review flow does not stop after answering a learner's free-form question.

## Decision
Use a conditional, backend-generated follow-up question for free-question answers. The follow-up is not a command to move to the next wrong question; it is a short check or application prompt based on the learner's free question and the AI answer metadata.

## Data Flow
`RuleBasedAiReviewService.answerFreeQuestion()` continues to save the learner `FREE_QUESTION` message and AI `FREE_ANSWER` message. After generating or falling back to the free-question answer, it builds one follow-up string and returns it as `AiReviewSubmitResponse.nextQuestion`.

## Follow-Up Rules
- `answerStyle=practical`: ask how the learner would apply the concept to the current problem.
- `answerStyle=comparison`: ask the learner to identify the deciding difference.
- otherwise: ask the learner to explain the key point in one sentence.

## Constraints
Do not save the follow-up as a separate AI message yet, because `nextQuestion` is already the response field used by the client. Do not call another AI endpoint for this first version; avoid extra latency and keep behavior deterministic.

## Testing
Add a backend service test proving `FREE_QUESTION` returns a non-blank `nextQuestion` while still saving only the user free question and AI free answer messages.

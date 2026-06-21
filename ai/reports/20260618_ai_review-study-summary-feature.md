---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 학습 내용 자동 요약(Study Summary) 기능 구현 보고서"

---

# AI Review Study Summary Feature

Date: 2026-05-12

## Goal

Add a study-focused summary flow to the smart review page.

## Implemented

- Per-problem summary: summarizes the selected wrong question, user's wrong choice, correct answer, key follow-up question, latest user explanation, and retry checkpoints.
- Full review report: summarizes all wrong questions in the session, repeated weak areas, current understanding counts, and next review order.
- Summary persistence: generated summaries are saved as AI review messages with `QUESTION_SUMMARY` or `REVIEW_REPORT`, so they can be restored when the page is reopened.
- Token efficiency: summaries are generated from stored conversation messages first, without calling the local AI server.

## Files

- `backend/src/main/java/com/devmatch/controller/AiReviewController.java`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`
- `backend/src/main/java/com/devmatch/dto/aireview/AiReviewSummaryResponse.java`
- `backend/src/main/java/com/devmatch/entity/AiReviewMessageMode.java`
- `frontend/src/lib/ai-review.ts`
- `frontend/src/lib/types.ts`
- `frontend/src/app/tests/results/[id]/review/page.tsx`

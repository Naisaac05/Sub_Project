---
type: prompt
category: inference
status: active
updated: 2026-06-18
description: "코드 품질 평가 전문 리뷰어용 시스템 프롬프트 템플릿"

---

# Code Quality Reviewer Prompt Template

Use this template when running a code quality review step in single-flow mode.

**Purpose:** Verify implementation is well-built (clean, tested, maintainable)

**Only proceed after spec compliance review passes.**

```
task_boundary:
  Use template at requesting-code-review/code-reviewer.md

  WHAT_WAS_IMPLEMENTED: [from implementer's report]
  PLAN_OR_REQUIREMENTS: Task N from [plan-file]
  BASE_SHA: [commit before task]
  HEAD_SHA: [current commit]
  DESCRIPTION: [task summary]
```

**Code reviewer returns:** Strengths, Issues (Critical/Important/Minor), Assessment

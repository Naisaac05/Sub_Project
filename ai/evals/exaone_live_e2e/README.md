# EXAONE Live E2E Quality Evaluation

This evaluation compares the real AI review workflow in two modes for the same questions:

- `rag`: BGE-M3 semantic retrieval followed by the existing EXAONE workflow.
- `no_rag_forced`: the same workflow with retrieval forced to return no context.

Both modes disable the lightweight/static answer resolver inside the evaluator so that every evaluated answer reaches live EXAONE generation. Production code and default production routing are not changed.

Semantic Judge is temporarily disabled inside this evaluator. This prevents a second EXAONE judge request from timing out or doubling CPU evaluation time. Production workflow behavior is not changed.

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\evaluate_exaone_live_e2e.py
```

Quick smoke:

```powershell
.\.venv\Scripts\python.exe scripts\evaluate_exaone_live_e2e.py --limit 1
```

Outputs:

- `REPORT.json`: machine-readable results and summary.
- `REPORT.md`: complete answers for human review.

Interpretation limits:

- Required keywords and forbidden claims are only rule-based supporting signals.
- Semantic Judge fields are intentionally recorded as skipped during this evaluation.
- A successful run proves that the live workflow executed; it does not by itself prove factual quality.
- Latency represents generation-forced evaluation and not the default production fast-path mix.
- Human review is required before treating the result as an operational quality gate.

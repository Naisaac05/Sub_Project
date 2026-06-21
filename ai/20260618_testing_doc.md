---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 서비스 테스팅 절차 및 주요 가이드라인"

---

# AI service testing

Create a local virtual environment, install dev requirements, then run the streaming tests:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest tests/test_stream.py -q
```

`requirements-dev.txt` includes FastAPI runtime dependencies plus `pytest` and `httpx`, which are required by FastAPI's `TestClient`.

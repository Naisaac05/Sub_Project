# Production AI Candidate Card Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore approved RAG cards and durable Ollama-generated approval candidates in the production Docker deployment.

**Architecture:** Bind mount the EC2 checkout's `concepts_v2` directory into both AI and backend containers, and point the backend at its mounted path. Route AI candidate capture to the backend Docker service name and log sanitized capture failures.

**Tech Stack:** Docker Compose, Python 3.11/unittest, Spring Boot, Markdown operations documentation

---

### Task 1: Lock production wiring with a failing test

**Files:**
- Create: `ai/tests/test_prod_deployment_wiring.py`
- Modify: `docker-compose.prod.yml:6-35`

- [ ] **Step 1: Write the failing compose contract test**

```python
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ProductionDeploymentWiringTest(unittest.TestCase):
    def test_compose_shares_cards_and_routes_candidate_capture_to_backend(self):
        compose = (ROOT / "docker-compose.prod.yml").read_text(encoding="utf-8")

        self.assertIn("AI_REVIEW_CONCEPTS_V2_PATH: /app/ai/knowledge/concepts_v2", compose)
        self.assertIn("./ai/app/knowledge/concepts_v2:/app/ai/knowledge/concepts_v2", compose)
        self.assertIn("./ai/app/knowledge/concepts_v2:/app/app/knowledge/concepts_v2", compose)
        self.assertIn(
            "AI_REVIEW_CANDIDATE_CAPTURE_URL: http://backend:8080/api/internal/ai-review/candidates/capture",
            compose,
        )


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify RED**

Run: `ai/.venv/Scripts/python.exe -m pytest ai/tests/test_prod_deployment_wiring.py -q`

Expected: FAIL because the backend card mount and candidate capture URL are absent.

- [ ] **Step 3: Add the shared paths and internal URL**

Add to `backend.environment` and `backend.volumes`:

```yaml
      AI_REVIEW_CONCEPTS_V2_PATH: /app/ai/knowledge/concepts_v2
      - ./ai/app/knowledge/concepts_v2:/app/ai/knowledge/concepts_v2
```

Add to `ai.environment` and `ai.volumes`:

```yaml
      AI_REVIEW_CANDIDATE_CAPTURE_URL: http://backend:8080/api/internal/ai-review/candidates/capture
      - ./ai/app/knowledge/concepts_v2:/app/app/knowledge/concepts_v2
```

- [ ] **Step 4: Verify the test and rendered compose configuration**

Run: `ai/.venv/Scripts/python.exe -m pytest ai/tests/test_prod_deployment_wiring.py -q`

Expected: `1 passed`.

Run: `docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --quiet`

Expected: exit code 0.

### Task 2: Make candidate capture failures observable

**Files:**
- Create: `ai/tests/test_candidate_sink.py`
- Modify: `ai/app/knowledge/candidate_sink.py:1-48`

- [ ] **Step 1: Write failing sanitized logging tests**

```python
import unittest
from unittest.mock import patch
from urllib import error

from app.knowledge.candidate_sink import save_auto_candidate


class CandidateSinkTest(unittest.TestCase):
    @patch("app.knowledge.candidate_sink.request.urlopen")
    def test_http_error_logs_status_without_candidate_payload(self, urlopen):
        urlopen.side_effect = error.HTTPError("http://backend/capture", 403, "Forbidden", {}, None)

        with self.assertLogs("app.knowledge.candidate_sink", level="WARNING") as logs:
            result = save_auto_candidate({"candidate_id": "secret-candidate", "definition_draft": "secret-answer"})

        self.assertFalse(result)
        self.assertIn("status=403", " ".join(logs.output))
        self.assertNotIn("secret-answer", " ".join(logs.output))

    @patch("app.knowledge.candidate_sink.request.urlopen")
    def test_network_error_logs_exception_type(self, urlopen):
        urlopen.side_effect = error.URLError("connection refused")

        with self.assertLogs("app.knowledge.candidate_sink", level="WARNING") as logs:
            result = save_auto_candidate({"candidate_id": "candidate-1"})

        self.assertFalse(result)
        self.assertIn("URLError", " ".join(logs.output))
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ai/.venv/Scripts/python.exe -m pytest ai/tests/test_candidate_sink.py -q`

Expected: FAIL because no warning is logged.

- [ ] **Step 3: Add sanitized warning logs**

In `candidate_sink.py`, add a module logger and split HTTP from transport failures:

```python
import logging

logger = logging.getLogger(__name__)

    except error.HTTPError as exc:
        logger.warning("AI candidate capture failed url=%s status=%s", url, exc.code)
        return False
    except (OSError, error.URLError, TimeoutError) as exc:
        logger.warning("AI candidate capture failed url=%s error=%s", url, type(exc).__name__)
        return False
```

- [ ] **Step 4: Verify candidate sink and workflow tests**

Run: `ai/.venv/Scripts/python.exe -m pytest ai/tests/test_candidate_sink.py ai/tests/test_workflow_runner.py -q`

Expected: all selected tests pass.

### Task 3: Document deployment and resolved error

**Files:**
- Modify: `.env.prod.example:34-42`
- Modify: `docs/deploy-runbook.md`
- Create: `error/2026-07-01-prod-ai-card-path-and-candidate-capture-url.md`
- Modify: `error/README.md`

- [ ] **Step 1: Document the topology-owned candidate URL**

Add a comment to `.env.prod.example` stating that `AI_REVIEW_CANDIDATE_CAPTURE_URL` and `AI_REVIEW_CONCEPTS_V2_PATH` are set by compose and must not use localhost inside containers.

- [ ] **Step 2: Add post-deploy checks to the runbook**

Document these exact checks:

```bash
docker compose -f docker-compose.prod.yml exec -T backend test -d /app/ai/knowledge/concepts_v2
docker compose -f docker-compose.prod.yml exec -T ai test -d /app/app/knowledge/concepts_v2
docker compose -f docker-compose.prod.yml exec -T ai printenv AI_REVIEW_CANDIDATE_CAPTURE_URL
docker compose -f docker-compose.prod.yml logs --tail=100 ai | grep 'AI candidate capture failed'
```

- [ ] **Step 3: Record the resolved issue using the repository template**

Create the error entry with symptoms, the two root causes, changed files using `path:line`, verification results, and the note that bind-mounted published cards remain in the EC2 checkout. Add its link under `error/README.md`'s `## 인덱스` section.

- [ ] **Step 4: Run final verification**

Run: `ai/.venv/Scripts/python.exe -m pytest ai/tests/test_prod_deployment_wiring.py ai/tests/test_candidate_sink.py ai/tests/test_workflow_runner.py -q`

Run: `docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --quiet`

Run: `git diff --check`

Expected: tests pass, compose exits 0, and diff check reports no whitespace errors.

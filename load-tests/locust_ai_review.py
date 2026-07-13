from __future__ import annotations

import os

from locust import HttpUser, between, task


SESSION_IDS = [value.strip() for value in os.getenv("SESSION_IDS", os.getenv("SESSION_ID", "")).split(",") if value.strip()]
ACCESS_TOKEN = os.getenv("ACCESS_TOKEN", "")


class AiReviewUser(HttpUser):
    wait_time = between(0.5, 1.5)

    def on_start(self) -> None:
        if not ACCESS_TOKEN or not SESSION_IDS:
            raise RuntimeError("ACCESS_TOKEN and SESSION_IDS (comma-separated) are required")
        self.session_id = SESSION_IDS[(self.environment.runner.user_count - 1) % len(SESSION_IDS)]
        self.client.headers.update(
            {"Authorization": f"Bearer {ACCESS_TOKEN}", "Content-Type": "application/json"}
        )

    @task
    def submit_answer(self) -> None:
        payload = {
            "answer": f"load-test-{self.session_id}",
            "mode": os.getenv("MODE", "FREE_QUESTION"),
            "questionId": int(os.environ["QUESTION_ID"]) if os.getenv("QUESTION_ID") else None,
        }
        with self.client.post(
            f"/api/ai-review/sessions/{self.session_id}/messages",
            json=payload,
            timeout=float(os.getenv("REQUEST_TIMEOUT_SECONDS", "65")),
            catch_response=True,
        ) as response:
            if response.status_code == 429 and not response.headers.get("Retry-After"):
                response.failure("429 response is missing Retry-After")
            elif response.status_code not in (200, 429):
                response.failure(f"unexpected status {response.status_code}")

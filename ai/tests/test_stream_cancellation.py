import contextlib
import asyncio
import socket
import threading
import time
import unittest
from unittest.mock import patch

import httpx
import uvicorn

from app.main import app


def _unused_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class FastApiStreamingCancellationTest(unittest.TestCase):
    def test_client_disconnect_closes_workflow_stream(self):
        workflow_started = threading.Event()
        workflow_closed = threading.Event()
        keep_stream_open = threading.Event()

        async def blocking_workflow_stream(mode, request):
            try:
                workflow_started.set()
                yield {"type": "start"}
                while not keep_stream_open.is_set():
                    await asyncio.sleep(0.05)
            finally:
                workflow_closed.set()

        port = _unused_local_port()
        config = uvicorn.Config(
            app,
            host="127.0.0.1",
            port=port,
            log_level="warning",
            lifespan="off",
        )
        server = uvicorn.Server(config)
        thread = threading.Thread(target=server.run, daemon=True)

        with (
            patch("app.main.STREAMING_ENABLED", True),
            patch("app.main.verify_service_token", lambda req: None),
            patch("app.workflow.runner.run_review_workflow_stream", blocking_workflow_stream),
        ):
            thread.start()
            deadline = time.monotonic() + 5
            while not server.started and thread.is_alive() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(server.started)

            try:
                with httpx.Client(timeout=5) as client:
                    with client.stream(
                        "POST",
                        f"http://127.0.0.1:{port}/api/review/free-question",
                        json={"question": "cancel test", "stream": True},
                    ) as response:
                        self.assertEqual(response.status_code, 200)
                        self.assertTrue(workflow_started.wait(timeout=2))
                        first_line = next(response.iter_lines())
                        self.assertTrue(first_line.startswith("data: "))

                self.assertTrue(
                    workflow_closed.wait(timeout=2),
                    "FastAPI should close the workflow stream when the SSE client disconnects",
                )
            finally:
                keep_stream_open.set()
                server.should_exit = True
                with contextlib.suppress(Exception):
                    thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()

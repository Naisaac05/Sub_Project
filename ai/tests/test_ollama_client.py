import asyncio
import json
import unittest
from unittest.mock import patch

from app.ollama.client import (
    DEFAULT_MODEL,
    FALLBACK_MODEL,
    OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS,
    OLLAMA_MAX_CONCURRENT_GENERATIONS,
    OLLAMA_REQUEST_TIMEOUT_SECONDS,
    OLLAMA_WARMUP_MODEL,
    bounded_ollama_queue_wait_timeout_seconds,
    bounded_ollama_request_timeout_seconds,
    call_ollama_stream_async,
    keep_alive_for_model,
    stop_sequences,
)
from app.ollama.gateway import AcquireResult, OllamaEndpoint


class OllamaClientTest(unittest.TestCase):
    def test_stop_sequences_do_not_include_prompt_section_labels(self):
        stops = stop_sequences()

        self.assertIn("\n\n\n", stops)
        self.assertNotIn("[Learner Free Question]", stops)
        self.assertNotIn("[Original Question]", stops)
        self.assertNotIn("[Question]", stops)

    def test_default_model_policy_uses_small_model_and_4b_fallback(self):
        self.assertEqual(DEFAULT_MODEL, "qwen3:1.7b")
        self.assertEqual(FALLBACK_MODEL, "qwen3:4b-q4_K_M")
        self.assertEqual(OLLAMA_WARMUP_MODEL, "qwen3:1.7b")

    def test_keep_alive_policy_keeps_small_resident_and_fallback_bounded(self):
        # Ollama requires keep_alive as int seconds or a duration string with a unit.
        # "-1" (int) means "stay resident forever"; sending the bare string "-1" returns HTTP 400.
        self.assertEqual(keep_alive_for_model("qwen3:1.7b"), -1)
        self.assertEqual(keep_alive_for_model("qwen3:4b-q4_K_M"), "30m")

    def test_generation_concurrency_defaults_to_one(self):
        self.assertEqual(OLLAMA_MAX_CONCURRENT_GENERATIONS, 1)

    def test_request_timeout_defaults_to_bounded_value(self):
        self.assertEqual(OLLAMA_REQUEST_TIMEOUT_SECONDS, 30)

    def test_request_timeout_zero_is_bounded_instead_of_unbounded(self):
        self.assertEqual(bounded_ollama_request_timeout_seconds(0), 30)
        self.assertEqual(bounded_ollama_request_timeout_seconds(-1), 30)
        self.assertEqual(bounded_ollama_request_timeout_seconds(12), 12)

    def test_queue_wait_timeout_defaults_to_bounded_value(self):
        self.assertEqual(OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS, 3)
        self.assertEqual(bounded_ollama_queue_wait_timeout_seconds(0), 3)
        self.assertEqual(bounded_ollama_queue_wait_timeout_seconds(-1), 3)
        self.assertEqual(bounded_ollama_queue_wait_timeout_seconds(2), 2)


class OllamaStreamCancellationTest(unittest.IsolatedAsyncioTestCase):
    async def test_cancelling_pending_stream_read_closes_httpx_response_stream(self):
        read_waiting = asyncio.Event()
        stream_closed = False

        class FakeResponse:
            status_code = 200

            async def aiter_lines(self):
                yield '{"response": "partial", "done": false}'
                read_waiting.set()
                while True:
                    await asyncio.sleep(0.05)

        class FakeStream:
            async def __aenter__(self):
                return FakeResponse()

            async def __aexit__(self, exc_type, exc, tb):
                nonlocal stream_closed
                stream_closed = True

        class FakeAsyncClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                pass

            def stream(self, *args, **kwargs):
                self.last_stream_args = args
                self.last_stream_kwargs = kwargs
                return FakeStream()

        with patch("app.ollama.client.httpx.AsyncClient", FakeAsyncClient):
            generator = call_ollama_stream_async(
                model=DEFAULT_MODEL,
                prompt="cancel test",
                temperature=0,
                max_tokens=128,
                num_ctx=256,
                num_thread=1,
            )

            self.assertEqual(await anext(generator), "partial")
            pending_read = asyncio.create_task(anext(generator))
            await asyncio.wait_for(read_waiting.wait(), timeout=1)
            pending_read.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await pending_read

        self.assertTrue(stream_closed)

    async def test_cancelling_pending_stream_read_logs_semaphore_release_metric(self):
        read_waiting = asyncio.Event()

        class FakeResponse:
            status_code = 200

            async def aiter_lines(self):
                yield '{"response": "partial", "done": false}'
                read_waiting.set()
                while True:
                    await asyncio.sleep(0.05)

        class FakeStream:
            async def __aenter__(self):
                return FakeResponse()

            async def __aexit__(self, exc_type, exc, tb):
                pass

        class FakeAsyncClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                pass

            def stream(self, *args, **kwargs):
                return FakeStream()

        with patch("app.ollama.client.httpx.AsyncClient", FakeAsyncClient):
            generator = call_ollama_stream_async(
                model=DEFAULT_MODEL,
                prompt="cancel test",
                temperature=0,
                max_tokens=128,
                num_ctx=256,
                num_thread=1,
            )

            with self.assertLogs("ai_review.observability", level="INFO") as logs:
                self.assertEqual(await anext(generator), "partial")
                pending_read = asyncio.create_task(anext(generator))
                await asyncio.wait_for(read_waiting.wait(), timeout=1)
                pending_read.cancel()
                with self.assertRaises(asyncio.CancelledError):
                    await pending_read

        event = _last_json_log(logs.output)
        self.assertEqual(event["event"], "ai_review.ollama_stream_finished")
        self.assertEqual(event["status"], "cancelled")
        self.assertTrue(event["semaphore_released"])
        self.assertEqual(event["model"], DEFAULT_MODEL)
        self.assertIn("endpoint", event)
        self.assertIn("capacity", event)
        self.assertIn("in_flight", event)

    async def test_stream_timeout_logs_semaphore_release_metric(self):
        class FakeResponse:
            status_code = 200

            async def aiter_lines(self):
                raise TimeoutError("read timeout")
                yield "never reached"

        class FakeStream:
            async def __aenter__(self):
                return FakeResponse()

            async def __aexit__(self, exc_type, exc, tb):
                pass

        class FakeAsyncClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                pass

            def stream(self, *args, **kwargs):
                return FakeStream()

        with patch("app.ollama.client.httpx.AsyncClient", FakeAsyncClient):
            generator = call_ollama_stream_async(
                model=DEFAULT_MODEL,
                prompt="timeout test",
                temperature=0,
                max_tokens=128,
                num_ctx=256,
                num_thread=1,
            )

            with self.assertLogs("ai_review.observability", level="INFO") as logs:
                with self.assertRaises(RuntimeError):
                    await anext(generator)

        event = _last_json_log(logs.output)
        self.assertEqual(event["event"], "ai_review.ollama_stream_finished")
        self.assertEqual(event["status"], "timeout")
        self.assertTrue(event["semaphore_released"])
        self.assertEqual(event["model"], DEFAULT_MODEL)
        self.assertIn("endpoint", event)
        self.assertIn("capacity", event)
        self.assertIn("in_flight", event)

    async def test_stream_queue_wait_timeout_logs_metric_without_release(self):
        class FakeGateway:
            def __init__(self):
                self.release_called = False

            def route_for(self, model):
                endpoint = OllamaEndpoint(model=model, base_url="http://localhost:11434")
                return type("Route", (), {
                    "endpoint": endpoint,
                    "capacity": 1,
                    "in_flight": 0,
                    "all_draining": False,
                })()

            def acquire(self, model, timeout_seconds):
                return AcquireResult(
                    model=model,
                    endpoint=OllamaEndpoint(model=model, base_url="http://localhost:11434"),
                    capacity=1,
                    in_flight=0,
                    queue_wait_ms=0,
                    acquired=False,
                )

            def release(self, acquisition):
                self.release_called = True

            def in_flight_for(self, model):
                return 0

        gateway = FakeGateway()

        with patch("app.ollama.client._GATEWAY", gateway):
            generator = call_ollama_stream_async(
                model=DEFAULT_MODEL,
                prompt="queue timeout test",
                temperature=0,
                max_tokens=128,
                num_ctx=256,
                num_thread=1,
            )

            with self.assertLogs("ai_review.observability", level="INFO") as logs:
                with self.assertRaises(RuntimeError):
                    await anext(generator)

        event = _last_json_log(logs.output)
        self.assertEqual(event["event"], "ai_review.ollama_stream_finished")
        self.assertEqual(event["status"], "queue_timeout")
        self.assertFalse(event["semaphore_released"])
        self.assertFalse(gateway.release_called)

    async def test_stream_uses_gateway_endpoint_for_model(self):
        seen_urls = []

        class FakeResponse:
            status_code = 200

            async def aiter_lines(self):
                yield '{"response": "hello", "done": true}'

        class FakeStream:
            async def __aenter__(self):
                return FakeResponse()

            async def __aexit__(self, exc_type, exc, tb):
                pass

        class FakeAsyncClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                pass

            def stream(self, method, url, **kwargs):
                seen_urls.append(url)
                return FakeStream()

        with patch.dict(
            "os.environ",
            {"OLLAMA_MODEL_POOL": f"{DEFAULT_MODEL}=http://pool-endpoint:11434"},
        ):
            with patch("app.ollama.client.httpx.AsyncClient", FakeAsyncClient):
                from app.ollama.client import reset_ollama_gateway_for_tests

                reset_ollama_gateway_for_tests()
                generator = call_ollama_stream_async(
                    model=DEFAULT_MODEL,
                    prompt="routing test",
                    temperature=0,
                    max_tokens=128,
                    num_ctx=256,
                    num_thread=1,
                )

                self.assertEqual(await anext(generator), "hello")

        self.assertEqual(seen_urls[0], "http://pool-endpoint:11434/api/generate")


def _last_json_log(lines: list[str]) -> dict[str, object]:
    message = lines[-1].split(":", 2)[-1].strip()
    return json.loads(message)


if __name__ == "__main__":
    unittest.main()

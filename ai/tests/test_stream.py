import unittest
import asyncio
import json
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

from app.schemas import AiGenerateRequest
from app.workflow.runner import run_review_workflow_stream
from app.main import app


class StreamingWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_successful_streaming_generation(self):
        async def mock_stream_gen(*args, **kwargs):
            yield "동시성 제어는 "
            yield "여러 작업이 동시에 실행될 때 "
            yield "공유 자원 접근을 안전하게 조율하는 방식입니다."

        request = AiGenerateRequest(
            question="테스트 질문",
            user_answer="동시성 제어 설명해줘",
        )

        events = []
        async for event in run_review_workflow_stream(
            mode="free-question",
            request=request,
            generator=mock_stream_gen,
        ):
            events.append(event)

        # Expected event flow: start -> chunk(s) -> done
        self.assertGreaterEqual(len(events), 3)
        self.assertEqual(events[0]["type"], "start")
        
        # Verify chunks are yielded
        chunks = [e["chunk"] for e in events if e["type"] == "chunk"]
        expected_answer = "동시성 제어는 여러 작업이 동시에 실행될 때 공유 자원 접근을 안전하게 조율하는 방식입니다."
        self.assertEqual("".join(chunks), expected_answer)
        
        # Verify done event contains AiGenerateResponse
        done_event = events[-1]
        self.assertEqual(done_event["type"], "done")
        response = done_event["response"]
        self.assertEqual(response.answer, expected_answer)
        self.assertFalse(response.fallback_used)

    async def test_streaming_generation_exception_falls_back(self):
        async def failing_stream_gen(*args, **kwargs):
            raise RuntimeError("stream failure")
            yield "Never reached"

        request = AiGenerateRequest(
            question="테스트 질문",
            user_answer="테스트 답변",
        )

        events = []
        async for event in run_review_workflow_stream(
            mode="free-question",
            request=request,
            generator=failing_stream_gen,
        ):
            events.append(event)

        # Should fall back gracefully to template answer
        done_event = events[-1]
        self.assertEqual(done_event["type"], "done")
        response = done_event["response"]
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")


class StreamingEndpointTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    @patch("app.main.STREAMING_ENABLED", True)
    @patch("app.main.verify_service_token", lambda req: None)
    def test_streaming_endpoint_returns_sse_when_requested(self):
        async def mock_workflow_stream(mode, request):
            from app.schemas import AiGenerateResponse
            yield {"type": "start"}
            yield {"type": "chunk", "chunk": "테스트 응답"}
            yield {"type": "done", "response": AiGenerateResponse(answer="테스트 응답")}

        with patch("app.workflow.runner.run_review_workflow_stream", mock_workflow_stream):
            response = self.client.post(
                "/api/review/free-question",
                json={"question": "테스트", "stream": True},
            )
            self.assertEqual(response.status_code, 200)
            self.assertTrue(response.headers.get("content-type", "").startswith("text/event-stream"))
            
            # Read SSE chunks from the response body
            lines = response.text.split("\n\n")
            non_empty_lines = [line for line in lines if line.strip()]
            self.assertEqual(len(non_empty_lines), 3)
            self.assertTrue(non_empty_lines[0].startswith("data: "))
            self.assertIn("start", non_empty_lines[0])
            self.assertIn("chunk", non_empty_lines[1])
            self.assertIn("done", non_empty_lines[2])

    @patch("app.main.STREAMING_ENABLED", True)
    @patch("app.main.verify_service_token", lambda req: None)
    def test_streaming_endpoint_accept_header(self):
        async def mock_workflow_stream(mode, request):
            from app.schemas import AiGenerateResponse
            yield {"type": "start"}
            yield {"type": "chunk", "chunk": "Accept 헤더"}
            yield {"type": "done", "response": AiGenerateResponse(answer="Accept 헤더")}

        with patch("app.workflow.runner.run_review_workflow_stream", mock_workflow_stream):
            response = self.client.post(
                "/api/review/free-question",
                json={"question": "테스트"},
                headers={"Accept": "text/event-stream"},
            )
            self.assertEqual(response.status_code, 200)
            self.assertTrue(response.headers.get("content-type", "").startswith("text/event-stream"))

    @patch("app.main.STREAMING_ENABLED", False)
    @patch("app.main.verify_service_token", lambda req: None)
    def test_non_streaming_endpoint_returns_json_when_streaming_disabled(self):
        with patch("app.main.generate_review_answer") as mock_generate:
            from app.schemas import AiGenerateResponse
            mock_generate.return_value = AiGenerateResponse(answer="Non-streaming response")
            
            response = self.client.post(
                "/api/review/free-question",
                json={"question": "테스트", "stream": True},
            )
            self.assertEqual(response.status_code, 200)
            self.assertFalse(response.headers.get("content-type", "").startswith("text/event-stream"))
            data = response.json()
            self.assertEqual(data["answer"], "Non-streaming response")


if __name__ == "__main__":
    unittest.main()

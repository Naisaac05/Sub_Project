import json
import math
import os
import unittest
import urllib.error
from unittest.mock import patch

from app.ollama.embeddings import (
    EmbeddingError,
    OllamaEmbeddingClient,
    cosine_similarity,
    normalize_vector,
)


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


class RawResponse(FakeResponse):
    def read(self):
        return self.payload


class VectorHelpersTest(unittest.TestCase):
    def test_normalize_vector_returns_l2_normalized_values(self):
        self.assertEqual(normalize_vector([3, 4]), [0.6, 0.8])

    def test_normalize_vector_handles_large_finite_values(self):
        result = normalize_vector([1e200, 1e200])

        self.assertTrue(all(math.isfinite(value) for value in result))
        self.assertAlmostEqual(cosine_similarity(result, result), 1.0)

    def test_normalize_vector_rejects_zero_vector(self):
        with self.assertRaises(EmbeddingError):
            normalize_vector([0, 0])

    def test_normalize_vector_rejects_empty_non_finite_and_malformed_values(self):
        for values in ([], [float("nan")], [float("inf")], ["invalid"]):
            with self.subTest(values=values), self.assertRaises(EmbeddingError):
                normalize_vector(values)

    def test_cosine_similarity_uses_normalized_vectors(self):
        self.assertAlmostEqual(cosine_similarity([3, 4], [4, 3]), 0.96)

    def test_cosine_similarity_rejects_dimension_mismatch(self):
        with self.assertRaises(EmbeddingError):
            cosine_similarity([1, 0], [1, 0, 0])


class OllamaEmbeddingClientTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(
            os.environ,
            {
                "AI_REVIEW_EMBEDDING_MODEL": "",
                "AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS": "10",
            },
            clear=False,
        )
        self.env.start()
        self.addCleanup(self.env.stop)
        self.base_url = patch("app.ollama.embeddings.OLLAMA_BASE_URL", "http://default-ollama")
        self.base_url.start()
        self.addCleanup(self.base_url.stop)

    @patch("app.ollama.embeddings.urllib.request.urlopen")
    def test_embed_posts_prompt_and_returns_normalized_embedding(self, urlopen):
        urlopen.return_value = FakeResponse({"embedding": [3, 4]})
        client = OllamaEmbeddingClient(
            base_url="http://ollama.test/",
            model="bge-test",
            timeout_seconds=7,
        )

        result = client.embed("review this")

        self.assertEqual(result, [0.6, 0.8])
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "http://ollama.test/api/embeddings")
        self.assertEqual(
            json.loads(request.data.decode("utf-8")),
            {"model": "bge-test", "prompt": "review this"},
        )
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 7)

    def test_defaults_come_from_environment_and_timeout_has_minimum(self):
        with (
            patch.dict(
                os.environ,
                {
                    "AI_REVIEW_EMBEDDING_MODEL": "env-model",
                    "AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS": "0",
                },
            ),
            patch("app.ollama.embeddings.OLLAMA_BASE_URL", "http://env-ollama/"),
        ):
            client = OllamaEmbeddingClient()

        self.assertEqual(client.base_url, "http://env-ollama")
        self.assertEqual(client.model, "env-model")
        self.assertEqual(client.timeout_seconds, 1)

    def test_blank_environment_model_uses_default_model(self):
        with patch.dict(os.environ, {"AI_REVIEW_EMBEDDING_MODEL": "   "}, clear=False):
            client = OllamaEmbeddingClient()

        self.assertEqual(client.model, "bge-m3")

    def test_default_client_ignores_ambient_blank_base_url(self):
        with patch.dict(os.environ, {"OLLAMA_BASE_URL": "   "}, clear=False):
            client = OllamaEmbeddingClient()

        self.assertEqual(client.base_url, "http://default-ollama")

    def test_explicit_blank_model_is_rejected(self):
        with self.assertRaisesRegex(EmbeddingError, "model"):
            OllamaEmbeddingClient(model="   ")

    def test_explicit_non_string_model_is_wrapped(self):
        with self.assertRaisesRegex(EmbeddingError, "model"):
            OllamaEmbeddingClient(model=1)

    def test_explicit_blank_base_url_is_rejected(self):
        with self.assertRaisesRegex(EmbeddingError, "base URL"):
            OllamaEmbeddingClient(base_url="   ")

    def test_non_integer_timeout_environment_is_wrapped(self):
        with patch.dict(
            os.environ,
            {"AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS": "not-an-int"},
            clear=False,
        ):
            with self.assertRaisesRegex(EmbeddingError, "timeout"):
                OllamaEmbeddingClient()

    def test_explicit_non_integer_timeout_is_wrapped(self):
        with self.assertRaisesRegex(EmbeddingError, "timeout"):
            OllamaEmbeddingClient(timeout_seconds="5")

    @patch("app.ollama.embeddings.urllib.request.urlopen")
    def test_embed_rejects_blank_input(self, urlopen):
        with self.assertRaises(EmbeddingError):
            OllamaEmbeddingClient().embed("   ")

        urlopen.assert_not_called()

    @patch("app.ollama.embeddings.urllib.request.urlopen")
    def test_embed_rejects_missing_or_malformed_embedding(self, urlopen):
        for payload in ({}, {"embedding": "not-a-vector"}, {"embedding": ["bad"]}):
            with self.subTest(payload=payload):
                urlopen.return_value = FakeResponse(payload)
                with self.assertRaises(EmbeddingError):
                    OllamaEmbeddingClient().embed("review this")

    @patch("app.ollama.embeddings.urllib.request.urlopen")
    def test_embed_wraps_url_and_json_errors(self, urlopen):
        failures = [
            urllib.error.URLError("offline"),
            TimeoutError("slow"),
            RawResponse(b"not json"),
        ]
        for failure in failures:
            with self.subTest(failure=failure):
                if isinstance(failure, BaseException):
                    urlopen.side_effect = failure
                else:
                    urlopen.side_effect = None
                    urlopen.return_value = failure
                with self.assertRaises(EmbeddingError):
                    OllamaEmbeddingClient().embed("review this")

    @patch("app.ollama.embeddings.urllib.request.Request")
    def test_embed_wraps_request_construction_errors(self, request_class):
        for error_type in (OSError, ValueError):
            with self.subTest(error_type=error_type):
                request_class.side_effect = error_type("cannot build request")

                with self.assertRaises(EmbeddingError) as context:
                    OllamaEmbeddingClient().embed("review this")

                self.assertIsInstance(context.exception.__cause__, error_type)

    @patch("app.ollama.embeddings.urllib.request.urlopen")
    def test_embed_wraps_response_read_errors(self, urlopen):
        class OSErrorResponse(FakeResponse):
            def read(self):
                raise OSError("socket closed")

        urlopen.return_value = OSErrorResponse({})

        with self.assertRaises(EmbeddingError) as context:
            OllamaEmbeddingClient().embed("review this")

        self.assertIsInstance(context.exception.__cause__, OSError)


if __name__ == "__main__":
    unittest.main()

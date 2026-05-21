import os
import unittest

from app.congestion import (
    AiRequestBusyError,
    admission_snapshot,
    ai_request_admission,
    reset_admission_gate_for_tests,
)


class CongestionControlTest(unittest.TestCase):
    def setUp(self):
        self._previous_limit = os.environ.get("AI_REVIEW_MAX_IN_FLIGHT_REQUESTS")
        os.environ["AI_REVIEW_MAX_IN_FLIGHT_REQUESTS"] = "1"
        reset_admission_gate_for_tests()

    def tearDown(self):
        if self._previous_limit is None:
            os.environ.pop("AI_REVIEW_MAX_IN_FLIGHT_REQUESTS", None)
        else:
            os.environ["AI_REVIEW_MAX_IN_FLIGHT_REQUESTS"] = self._previous_limit
        reset_admission_gate_for_tests()

    def test_admission_gate_raises_429_when_capacity_is_full(self):
        with ai_request_admission():
            with self.assertRaises(AiRequestBusyError) as raised:
                with ai_request_admission():
                    pass

        self.assertIn("busy", str(raised.exception.detail).lower())
        self.assertEqual(raised.exception.status_code, 429)
        self.assertEqual(raised.exception.retry_after_seconds, 3)

    def test_admission_snapshot_reports_capacity_and_in_flight_count(self):
        with ai_request_admission():
            snapshot = admission_snapshot()

        self.assertEqual(snapshot["limit"], 1)
        self.assertEqual(snapshot["in_flight"], 1)
        self.assertEqual(snapshot["available"], 0)

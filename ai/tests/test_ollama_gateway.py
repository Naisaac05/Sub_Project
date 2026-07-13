import unittest

from app.ollama.gateway import ModelPoolGateway, parse_model_pool, parse_model_capacities


class OllamaGatewayTest(unittest.TestCase):
    def test_parse_model_pool_maps_models_to_endpoints(self):
        pool = parse_model_pool(
            "exaone3.5:2.4b=http://localhost:11434,exaone3.5:7.8b=http://localhost:11435",
            default_base_url="http://fallback:11434",
        )

        self.assertEqual(pool["exaone3.5:2.4b"][0].base_url, "http://localhost:11434")
        self.assertEqual(pool["exaone3.5:7.8b"][0].base_url, "http://localhost:11435")

    def test_parse_model_pool_falls_back_to_default_model_endpoint(self):
        pool = parse_model_pool("", default_base_url="http://localhost:11434", default_model="exaone3.5:2.4b")

        self.assertEqual(pool["exaone3.5:2.4b"][0].base_url, "http://localhost:11434")

    def test_gateway_skips_draining_endpoint_when_active_endpoint_exists(self):
        gateway = ModelPoolGateway(
            model_pool=parse_model_pool(
                "exaone3.5:2.4b=http://draining:11434,exaone3.5:2.4b=http://active:11434",
                default_base_url="http://fallback:11434",
            ),
            capacities={"exaone3.5:2.4b": 1},
            draining_endpoints={"http://draining:11434"},
        )

        route = gateway.route_for("exaone3.5:2.4b")

        self.assertEqual(route.endpoint.base_url, "http://active:11434")
        self.assertFalse(route.all_draining)

    def test_gateway_unknown_model_uses_default_base_url(self):
        gateway = ModelPoolGateway(
            model_pool={},
            capacities={},
            default_base_url="http://localhost:11434",
        )

        route = gateway.route_for("unknown-model")

        self.assertEqual(route.endpoint.base_url, "http://localhost:11434")
        self.assertEqual(route.capacity, 1)

    def test_model_capacity_gates_are_independent(self):
        gateway = ModelPoolGateway(
            model_pool=parse_model_pool(
                "small=http://localhost:11434,large=http://localhost:11435",
                default_base_url="http://fallback:11434",
            ),
            capacities=parse_model_capacities("small=1,large=1"),
        )

        small = gateway.acquire("small", timeout_seconds=0.001)
        large = gateway.acquire("large", timeout_seconds=0.001)
        blocked_small = gateway.acquire("small", timeout_seconds=0.001)

        try:
            self.assertTrue(small.acquired)
            self.assertTrue(large.acquired)
            self.assertFalse(blocked_small.acquired)
            self.assertEqual(small.capacity, 1)
            self.assertEqual(large.capacity, 1)
        finally:
            gateway.release(small)
            gateway.release(large)

    def test_replicas_receive_requests_by_least_in_flight(self):
        gateway = ModelPoolGateway(
            model_pool=parse_model_pool(
                "small=http://ollama-a:11434,small=http://ollama-b:11434",
                default_base_url="http://fallback:11434",
            ),
            capacities={"small": 1},
        )

        first = gateway.acquire("small", timeout_seconds=0.001)
        second = gateway.acquire("small", timeout_seconds=0.001)
        third = gateway.acquire("small", timeout_seconds=0.001)

        try:
            self.assertTrue(first.acquired)
            self.assertTrue(second.acquired)
            self.assertNotEqual(first.endpoint.base_url, second.endpoint.base_url)
            self.assertFalse(third.acquired)
        finally:
            gateway.release(first)
            gateway.release(second)

    def test_equal_load_routes_rotate_between_replicas(self):
        gateway = ModelPoolGateway(
            model_pool=parse_model_pool(
                "small=http://ollama-a:11434,small=http://ollama-b:11434",
                default_base_url="http://fallback:11434",
            )
        )

        routes = [gateway.route_for("small").endpoint.base_url for _ in range(4)]

        self.assertEqual(
            routes,
            [
                "http://ollama-a:11434",
                "http://ollama-b:11434",
                "http://ollama-a:11434",
                "http://ollama-b:11434",
            ],
        )


if __name__ == "__main__":
    unittest.main()

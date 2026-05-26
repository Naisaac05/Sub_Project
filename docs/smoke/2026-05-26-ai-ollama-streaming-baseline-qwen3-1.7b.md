# AI Ollama Streaming Baseline

- measured_at: 2026-05-26T15:29:24
- command: `python scripts/measure_ollama_stream_baseline.py`

## Environment

- os: Windows-11-10.0.26200-SP0
- cpu: Intel64 Family 6 Model 142 Stepping 12, GenuineIntel
- ram: 15.8 GB
- python: 3.12.13
- ollama_base_url: http://localhost:11434
- ollama_version: 0.24.0
- model: qwen3:1.7b
- prompt: Explain quorum read and quorum write in Korean, in exactly two short sentences.
- knowledge_version: version=1 entries=7 sha256=09648d5e5ab1

## Summary

- stream_completed: 3
- stream_disconnected: 0
- stream_partial_failed: 0
- fallback_to_sync_count: 0
- first_token_latency_ms: min=5158, p50=5419, p95=6587, max=6587
- stream_duration_ms: min=17760, p50=17798, p95=19917, max=19917

## Samples

| run | status | first_token_latency_ms | stream_duration_ms | chunks | chars | model | route | fallback | quality_flags | error |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| 1 | completed | 5419 | 17798 | 108 | 220 | qwen3:1.7b | fallback_template | true | missing_topic |  |
| 2 | completed | 5158 | 17760 | 103 | 192 | qwen3:1.7b | fallback_template | true | missing_topic |  |
| 3 | completed | 6587 | 19917 | 109 | 217 | qwen3:1.7b | fallback_template | true | missing_topic |  |

## Notes

- This baseline measures the Python streaming workflow with real Ollama generation.
- `fallback_to_sync_count` is fixed at 0 here because Spring synchronous fallback is outside this script.
- Default runtime cache and candidate outputs are isolated under the system temp directory for this run.

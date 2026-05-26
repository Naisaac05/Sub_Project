# AI Review Metrics Dashboard Draft

> 작성일: 2026-05-26
> 범위: P1 minimal metrics backend connection

## Data Source

- Spring Actuator Micrometer endpoint: `/actuator/metrics`
- Application tag: `application=devmatch`
- Initial registry: Spring Boot default Micrometer registry
- Prometheus/OpenTelemetry exporter: not wired yet

## Panels

### Stream Lifecycle

- Metric: `ai.review.stream.lifecycle`
- Type: counter
- Tags: `status`, `mode`, optional `reason`
- Suggested views:
  - completed vs disconnected vs partial_failed count
  - disconnected count by reason
  - partial_failed count by reason

### First Token Latency

- Metric: `ai.review.stream.first_token.latency`
- Type: timer / histogram
- Tags: `mode`
- Suggested views:
  - p50 / p95 / max first token latency
  - count by mode

### Stream Duration

- Metric: `ai.review.stream.duration`
- Type: timer / histogram
- Tags: `status`, `mode`, optional `reason`
- Suggested views:
  - p50 / p95 / max stream duration
  - duration by terminal status
  - timeout/disconnect duration by reason

### Fallback To Sync

- Metric: `ai.review.fallback.sync`
- Type: counter
- Tags: `reason`, `mode`
- Suggested views:
  - fallback count by reason
  - fallback count by mode
  - alert candidate when fallback count increases while streaming is enabled

## Alert Candidates

- `ai.review.stream.lifecycle{status="partial_failed"}` increases for 5 minutes.
- `ai.review.stream.lifecycle{status="disconnected",reason!="completion_callback"}` spikes above local baseline.
- `ai.review.fallback.sync` increases during normal streaming rollout.
- p95 `ai.review.stream.first_token.latency` exceeds the current dev-laptop baseline.
- p95 `ai.review.stream.duration` approaches Spring stream timeout.

## Follow-Up

- Add Prometheus or OpenTelemetry exporter after deciding the production metrics backend.
- Add degraded-mode specific counters when kill switches are implemented.
- Add Grafana/OTel dashboard JSON once the backend is selected.

# AI Review Observability Output Design

## Goal

Turn existing AI review structured response events into actual FastAPI and Spring Boot log output with correlation ids and lightweight operational metrics.

## Architecture

FastAPI accepts `X-Correlation-ID`, generates one when absent, attaches it to every `observability_events` item, emits one JSON log line per event, and echoes the header in the HTTP response. Spring Boot sends the same header when calling FastAPI and logs returned event summaries.

## Metrics In Logs

Log records include boolean/count fields that can be scraped by a log backend:

- `fallback_used`
- `retrieval_miss`
- `candidate_captured`
- `candidate_backlog_pending`

Phase 20 stays log-based; no metrics registry dependency is added.

## Scope

This phase does not add dashboards or external sinks. It creates stable structured log payloads and correlation id propagation so later infrastructure can consume them.

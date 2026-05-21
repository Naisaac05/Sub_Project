# Task Tracker

| Task | Status | Description |
| --- | --- | --- |
| Step 1: Feature Flag + 설정 추가 | completed | Spring Boot 및 Python FastAPI 환경에 스트리밍 활성화 Feature Flag 및 설정 프로퍼티 추가 |
| Step 2: Python streaming 최소 구현 | completed | call_ollama_stream_async 및 비동기 generator 기반 Python SSE 연동 구현 |
| Step 3: Spring SSE proxy 구현 | completed | WebClient 기반 Reactive SSE 스트리밍 프록시 및 SseEmitter 라이프사이클 관리 구현 |
| └ Task 1: WebFlux 라이브러리 추가 | completed | build.gradle에 WebFlux 의존성 추가 및 빌드 검증 |
| └ Task 2: PythonAiReviewClient 확장 | completed | PythonAiReviewClient에 streamReview(Flux<String> 반환) 구현 및 client test |
| └ Task 3: AiReviewStreamingService 구현 | completed | 세분화된 트랜잭션 DB 적재 및 SseEmitter 라이프사이클/상태 관리와 parser 구현 |
| └ Task 4: Controller 엔드포인트 연동 | completed | POST /sessions/{sessionId}/messages/stream 연동 및 최종 빌드/통합 테스트 |
| Step 4: React streaming consumer + fallback 구현 | completed | 프론트엔드 ReadableStream SSE 버퍼 파서 및 폴백 메커니즘 연동 |

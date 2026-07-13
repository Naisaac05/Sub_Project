# AI review capacity and scaling

## Request protection

- backend는 인증 사용자 ID와 원본 IP를 각각 제한한다. 카운터는 Redis에 저장되어 backend replica 사이에서 공유된다.
- 기본값은 사용자당 분당 12회, IP당 분당 60회다. 초과 응답은 `429`와 `Retry-After` 헤더를 반환한다.
- AI 서비스는 `AI_REVIEW_MAX_IN_FLIGHT_REQUESTS=8`로 전체 진입량을 제한하고 Ollama 모델별 endpoint는 기본 동시 생성 1개로 제한한다.
- 프론트는 `Retry-After`를 표시하며, 429를 받은 스트리밍 요청을 동기 요청으로 자동 재시도하지 않는다.

## Model pool

기본 운영은 Ollama 한 개다. 같은 서버에서 두 번째 인스턴스를 시험하려면 각 인스턴스에 모델을 준비한 뒤 다음처럼 실행한다.

```powershell
$env:OLLAMA_MODEL_POOL='exaone3.5:2.4b=http://ollama:11434,exaone3.5:2.4b=http://ollama-secondary:11434'
docker compose -f docker-compose.prod.yml --profile model-pool up -d
```

라우터는 같은 모델의 endpoint 중 현재 in-flight가 가장 적은 곳을 선택한다. `OLLAMA_MODEL_CAPACITY=exaone3.5:2.4b=1`은 endpoint 한 개당 허용 동시 생성 수다. 외부 GPU 서버나 관리형 Ollama endpoint도 `model=url` 항목을 쉼표로 추가하면 같은 풀에 들어간다.

Compose profile은 replica를 켜는 수단이지 자동 확장은 아니다. 트래픽 기반 자동 확장은 ECS/Kubernetes 같은 오케스트레이터와 외부 모델 endpoint가 필요하다.

## Operational gate

1. `load-tests`의 k6 또는 Locust 시나리오를 테스트 계정과 세션으로 실행한다.
2. 정상 부하에서 5xx가 없어야 하고 p95가 설정한 backend/AI timeout 안에 들어와야 한다.
3. 한도를 넘긴 부하는 서버 다운 대신 `429 + Retry-After`로 수렴해야 한다.
4. 컨테이너가 resource limit에 닿거나 healthcheck가 실패하면 동시성 값을 낮추거나 AI/Ollama endpoint를 늘린다.

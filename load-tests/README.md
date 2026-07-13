# AI review load tests

운영 데이터와 분리된 테스트 계정으로 실행한다. 여러 VU가 한 세션을 동시에 변경하면 세션 상태 충돌이 측정값에 섞이므로 `SESSION_IDS`에 테스트용 세션 ID를 여러 개 전달한다.

## k6

```powershell
$env:BASE_URL='https://devmatch.duckdns.org'
$env:ACCESS_TOKEN='<access-token>'
$env:SESSION_IDS='101,102,103,104'
k6 run -e BASE_URL=$env:BASE_URL -e ACCESS_TOKEN=$env:ACCESS_TOKEN -e SESSION_IDS=$env:SESSION_IDS -e RATE=10 -e DURATION=2m load-tests/k6-ai-review.js
```

`200`은 정상 처리, `429`는 의도된 과부하 제어로 집계한다. 그 외 상태가 5% 이상이거나 p95가 55초 이상이면 테스트가 실패한다.

## Locust

```powershell
$env:ACCESS_TOKEN='<access-token>'
$env:SESSION_IDS='101,102,103,104'
locust -f load-tests/locust_ai_review.py --host https://devmatch.duckdns.org
```

초기 기준은 10 RPS에서 시작해 25, 50 RPS로 단계적으로 올린다. 서버 CPU/메모리, Redis 연결 수, AI in-flight, Ollama queue wait, `429` 비율을 함께 본다.

# AI 서버 모든 generate 호출이 Ollama 400으로 실패 — keep_alive 문자열 `"-1"`

- 발생 일시: 2026-05-20
- 영역: ai (Python FastAPI / Ollama 연동)
- 심각도: high (실사용 시 모든 답변이 템플릿 폴백)

## 증상

- `uvicorn app.main:app` 자체는 정상 기동, `/health` 200 OK.
- `/api/review/first-question`, `/follow-up`, `/free-question` 호출은 모두 200으로 응답하지만 본문이 항상 다음 형태:
  - `model_used: "template"`, `fallback_used: true`, `route: "fallback_template"`
  - latency_ms 약 2000ms 안팎 (실모델 추론은 5초 이상 걸려야 정상)
- Ollama 자체는 `ollama list`, `ollama run` 으로 직접 호출하면 정상 응답.

## 원인

`call_ollama` 가 요청 본문에 `"keep_alive": keep_alive_for_model(model)` 을 넣는데, 작은 모델용 기본값이 문자열 `"-1"` 였음.

```
HTTPError 400: {"error":"time: missing unit in duration \"-1\""}
```

Ollama `/api/generate` 의 `keep_alive` 필드는 **정수(초)** 또는 **단위가 붙은 duration 문자열**(`"30m"`, `"24h"`) 만 받습니다. 정수 `-1` 은 "영구 상주" 의미로 정상 작동하지만, 단위 없는 문자열 `"-1"` 은 Go duration 파서에서 거부됩니다.

해당 호출이 `urllib.error.HTTPError` 를 던지면 [`ai/app/ollama/client.py:53-54`](../ai/app/ollama/client.py#L53) 의 `except (URLError, TimeoutError, JSONDecodeError)` 가 잡아서 `RuntimeError("Ollama request failed")` 로 감싸 올림. ([`HTTPError` 가 `URLError` 의 자식이라 함께 잡힘])

그러면 [`ai/app/workflow/nodes.py:94-118`](../ai/app/workflow/nodes.py#L94) 의 except 블록이 동작:
- `first-question`, `follow-up` 모드는 `_should_retry_fallback_model(...)` 조건([nodes.py:395-401](../ai/app/workflow/nodes.py#L395))이 free-question 전용이라 fallback 모델로의 재시도 없이 곧장 템플릿(`model_used="template"`, `route="fallback_template"`)으로 빠짐.

부차적으로, 처음 디버깅할 때는 기본 모델 `qwen3:1.7b` 가 로컬 Ollama에 설치돼 있지 않아 같은 fallback 경로로 빠지는 또 다른 원인이 있었음(`ollama pull qwen3:1.7b` 로 해소).

## 해결 방법

1. `qwen3:1.7b` 모델 설치: `ollama pull qwen3:1.7b` (≈1.4GB).
2. [`ai/app/ollama/client.py`](../ai/app/ollama/client.py) `keep_alive_for_model` 가 숫자 문자열(`"-1"`, `"0"`)이면 `int` 로, 단위 있는 문자열(`"30m"`)이면 문자열 그대로 반환하도록 수정. 환경변수 의미("-1 = 영구 상주") 유지하면서 Ollama가 받아들이는 형태로 보냄.
3. [`ai/tests/test_ollama_client.py`](../ai/tests/test_ollama_client.py) `test_keep_alive_policy_keeps_small_resident_and_fallback_bounded` 기대값을 문자열 `"-1"` → 정수 `-1` 로 수정. (이전 테스트가 버그 동작을 그대로 검증하고 있었음.)

검증:
- `python -m unittest tests.test_ollama_client -v` ✅ 4/4 통과.
- 서버 재기동 후 `/api/review/first-question` 호출 시 `model_used: "qwen3:1.7b"`, latency 5~7초로 정상 Ollama 추론 확인.

## 재발 방지 / 메모

- 남은 동작: RAG 컨텍스트가 비어 있을 때 confidence_gate가 작은 모델 답변을 신뢰하지 않고 템플릿으로 교체하는 것은 설계 의도임([`ai/app/workflow/nodes.py:176-200`](../ai/app/workflow/nodes.py#L176)). "fallback_used=true" 라고 무조건 Ollama 호출 실패는 아님 — `model_used` 가 `"template"` 이 아니라 `"qwen3:1.7b"` 이면 모델은 정상 동작한 것.
- `OLLAMA_FALLBACK_KEEP_ALIVE` 기본값 `"30m"` 은 단위가 붙어 있어 영향 없었음.
- 환경변수로 keep_alive를 덮어쓸 때는 항상 단위 붙은 duration(`"24h"`, `"30m"`) 또는 단순 정수 문자열(`"-1"`, `"0"`)로 줘야 함. 이제 둘 다 안전하게 처리됨.
- 부가 작업으로 모델 누락이 다시 일어나지 않으려면 README "1. Ollama 모델 준비" 단계가 작은 모델(`qwen3:1.7b`)과 fallback 모델(`qwen3:4b-q4_K_M`) 둘 다 pull 하도록 안내돼야 함 — 현재 README는 fallback만 pull. 다음 README 정비 시 같이 손볼 것.

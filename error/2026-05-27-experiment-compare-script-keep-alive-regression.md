# 프롬프트 v1/v2 비교 실험 스크립트가 Ollama 400 으로 전부 실패 — keep_alive 문자열 `"-1"` 재발

- 발생 일시: 2026-05-27
- 영역: ai (실험 스크립트, `ai/experiments/prompt_v2_comparison/`)
- 심각도: medium (운영 영향 없음, 실험 자체가 진행 불가)

## 증상

- `python ai/experiments/prompt_v2_comparison/compare.py` 실행 시 5개 샘플 × v1/v2 = 10회 호출이 **모두 동일한 에러**로 실패.
- `results/comparison.md` 의 모든 출력란이 `[오류] HTTPError: HTTP Error 400: Bad Request` 로만 채워짐.
- 응답 시간이 정확히 ~2초로 일관 → 모델 추론까지 가지 못하고 Ollama API 가 요청 자체를 즉시 거부.
- 디버그 스크립트(`debug_ollama.py`) 4단계(`keep_alive` 추가) 에서 처음 실패하며 다음 본문 확인:
  ```
  {"error":"time: missing unit in duration \"-1\""}
  ```

## 원인

[`ai/experiments/prompt_v2_comparison/compare.py`](../ai/experiments/prompt_v2_comparison/compare.py) 의 `call_ollama` 가 요청 본문에 `"keep_alive": "-1"` 을 **문자열** 그대로 넣었음.

Ollama `/api/generate` 의 `keep_alive` 필드는 정수(초) 또는 단위 붙은 duration 문자열(`"30m"`, `"24h"`) 만 받는다. 정수 `-1` 은 "영구 상주" 의미로 유효하지만, 단위 없는 문자열 `"-1"` 은 Go duration 파서에서 즉시 거부된다. → HTTP 400.

이건 [2026-05-20-ai-server-keep-alive-invalid-duration.md](2026-05-20-ai-server-keep-alive-invalid-duration.md) 에서 운영 코드(`ai/app/ollama/client.py`)에 이미 잡힌 동일한 버그였다. 운영 코드는 그 후 [`ai/app/ollama/client.py:103-108`](../ai/app/ollama/client.py#L103) 의 `keep_alive_for_model` 에서 숫자 문자열(`"-1"`)을 `int` 로 변환해서 보내도록 수정됐다.

재발 경위: 이번에 새로 추가한 실험 스크립트(`compare.py`)는 운영 코드의 변환 로직을 거치지 않고 **직접 Ollama API 를 호출**한다. 작성 당시 운영 코드의 `body` 구조만 시각적으로 참고했고 `keep_alive` 가 `keep_alive_for_model(model)` 의 변환 결과라는 점을 놓쳐, 환경변수 기본값 표기인 `"-1"` 문자열을 그대로 박아넣었다.

## 해결 방법

1. [`ai/experiments/prompt_v2_comparison/compare.py`](../ai/experiments/prompt_v2_comparison/compare.py): `"keep_alive": "-1"` → `"keep_alive": -1` (정수). 운영 `keep_alive_for_model` 의 변환 결과와 동일해진다.
2. [`ai/experiments/prompt_v2_comparison/debug_ollama.py`](../ai/experiments/prompt_v2_comparison/debug_ollama.py): 동일하게 정수로 수정. 향후 디버그 시 같은 함정에 빠지지 않게.
3. compare.py 의 `call_ollama` 에 `urllib.error.HTTPError` 전용 except 추가 — `.read()` 로 응답 본문을 가져와 실제 Ollama 에러 메시지를 노출. 이전엔 `"Bad Request"` 만 보여서 원인 추적이 어려웠음.

검증:
- `python ai/experiments/prompt_v2_comparison/debug_ollama.py` → 5단계 모두 ✅ 통과 예상 (사용자 측 재실행 필요)
- 그 후 compare.py 본 실행 시 정상 응답 받을 수 있어야 함.

## 재발 방지 / 메모

- **교훈**: Ollama API 를 직접 호출하는 신규 스크립트를 만들 때는 운영 코드의 `body` 구조만 보지 말고 **변환 함수(`keep_alive_for_model`, `bounded_*_timeout_seconds` 등) 도 함께 옮겨야 함**. 또는 `call_ollama` 같은 공용 헬퍼를 재사용하는 게 더 안전.
- 환경변수 `OLLAMA_SMALL_KEEP_ALIVE` 기본값이 표기상 `"-1"` 문자열인 점이 함정. 운영 코드가 이를 정수로 변환해서 보내는 의도를 알지 못하면 같은 실수를 반복하기 쉽다.
- 향후 실험 스크립트를 더 만들 일이 있으면 `ai/app/ollama/client.py` 의 `call_ollama` 를 직접 import 해서 쓰는 방안도 검토 가치 있음 (단, 게이트웨이/세마포어 초기화 비용 때문에 단순 스크립트에는 과할 수 있음).
- HTTPError 본문을 항상 노출하는 패턴(`except HTTPError: body = exc.read()`) 은 모든 신규 Ollama 클라이언트에 기본 장착할 것.

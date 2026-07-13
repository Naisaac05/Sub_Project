# Ollama model pool always selected the first endpoint

- 발생 일시: 2026-07-10
- 영역: ai / infra
- 심각도: high

## 증상

`OLLAMA_MODEL_POOL`에 같은 모델의 endpoint를 여러 개 등록해도 모든 요청이 목록의 첫 번째 endpoint로 전달되어 replica가 부하를 분담하지 못했다.

## 원인

모델풀 파서는 중복 모델 endpoint를 정상적으로 보관했지만 라우터가 활성 endpoint 목록의 첫 항목을 고정 선택했다. 동시 실행 수도 모델 단위로만 저장되어 endpoint별 여유 용량을 판단할 수 없었다.

## 해결 방법

`ai/app/ollama/gateway.py:78`에서 in-flight와 semaphore를 `(model, base_url)` 단위로 분리하고, 현재 in-flight가 가장 적은 endpoint를 선택하도록 변경했다. `ai/tests/test_ollama_gateway.py:71`에 두 replica 분산 및 전체 포화 테스트를 추가했다.

## 재발 방지 / 메모

모델풀 관련 변경 시 같은 모델의 endpoint를 두 개 이상 넣고 첫 두 요청이 서로 다른 endpoint를 획득하는지 검증한다. Compose의 replica 추가만으로 자동 확장이 되는 것은 아니며 각 endpoint에 모델이 준비되어 있어야 한다.

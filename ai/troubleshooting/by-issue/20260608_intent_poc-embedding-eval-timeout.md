---
type: troubleshooting
category: evaluation
status: active
updated: 2026-06-18
description: "Intent PoC embedding evaluation timed out 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# Intent PoC embedding evaluation timed out

- 발생 일시: 2026-06-08
- 영역: ai / eval
- 심각도: medium

## 증상

`ai/evals/intent_poc/evaluate.py --classifier embed --split dev` 실행이 180초 제한에서 한 번, 600초 제한에서 한 번 시간 초과됐다. `REPORT_embed_dev.md`도 생성되지 않아 3단 평가(dev -> holdout -> golden)를 완료할 수 없었다.

## 원인

Ollama와 `bge-m3` 모델은 정상 실행 중이었지만, 단일 `/api/embeddings` 호출이 약 2.9초 걸렸다. 기존 `embed` classifier는 centroid 생성 시 dev 120개를 임베딩하고, dev 평가에서도 다시 120개 질문을 순차 임베딩했다. 즉 dev 평가 1회만 약 240회 원격 호출이 필요해 10분 제한도 넘을 수 있는 구조였다.

## 해결 방법

`ai/evals/intent_poc/classifiers.py:229`에 PoC 전용 persistent embedding cache 경로를 추가하고, `ai/evals/intent_poc/classifiers.py:274`의 `_ollama_embed()`가 `model + text` 캐시 키를 먼저 확인하게 했다. 실제 Ollama 호출은 `ai/evals/intent_poc/classifiers.py:261`의 `_fetch_ollama_embedding()`로 분리했다.

추가로 평가 누수 확인을 명시하기 위해 `ai/evals/intent_poc/evaluate.py:24`에 split 필터 함수를 추가하고, `ai/evals/intent_poc/evaluate.py:41`에 `--split dev|holdout` 옵션을 추가했다. golden 최종 평가는 `ai/evals/intent_poc/evaluate_golden.py:36`에서 runtime intent/sub-intent를 PoC 10-class taxonomy로 매핑해 수행한다.

## 재발 방지 / 메모

임베딩 기반 PoC 평가는 첫 실행 때 캐시를 채우느라 여전히 수 분이 걸릴 수 있다. 이후 같은 model/text 조합은 `.embed_cache.json`에서 재사용된다. `.embed_cache.json`은 이미 `.gitignore`에 포함되어 있어 대용량 벡터 캐시가 저장소에 올라가지 않는다.

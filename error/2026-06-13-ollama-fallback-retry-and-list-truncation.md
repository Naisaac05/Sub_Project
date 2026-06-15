# Ollama fallback 재시도 생략 및 목록 답변 절단

- 발생 일시: 2026-06-13
- 영역: AI / Ollama / workflow
- 심각도: high

## 증상

Fast Path miss 후 Ollama 요청이 실패했을 때 기본 모델과 fallback 모델이 같으면 재시도 없이 즉시 template fallback이 표시됐다. 타임아웃도 일반 오류로 감싸져 사용자 안내에서 원인을 구분할 수 없었다.

또한 `equals()`와 `==` 비교처럼 목록 형태로 생성된 정상 답변이 첫 번째 목록 항목 뒤에서 잘려, `==` 설명이 사라지거나 의미상 모순 답변으로 판정됐다.

## 원인

재시도 조건이 현재 모델과 fallback 모델이 다른 경우만 허용해, 둘 다 `exaone3.5:2.4b`인 기본 설정에서는 실패 재시도가 생략됐다. Ollama 클라이언트도 타임아웃을 일반 `RuntimeError`로 변환해 workflow가 실패 원인을 알 수 없었다.

답변 압축 함수는 마침표뿐 아니라 줄바꿈도 문장 수로 계산했다. 제목 뒤 빈 줄과 첫 목록 항목이 문장 제한을 모두 소비해 두 번째 목록 항목이 잘렸다.

## 해결 방법

- Ollama 기본 요청 타임아웃을 50초로 늘리고 타임아웃 전용 예외를 보존했다.
  - `ai/app/ollama/client.py:17`
  - `ai/app/ollama/client.py:327`
- Fast Path miss 후 생성 실패 시 같은 모델이어도 1회 재시도하고, 최종 실패 원인을 `timeout`, `quality_validation`, `other_error`로 분류했다.
  - `ai/app/workflow/nodes.py:704`
  - `ai/app/workflow/nodes.py:708`
  - `ai/app/workflow/nodes.py:720`
- 의미상 모순이 감지된 답변도 품질 fallback으로 전환했다.
  - `ai/app/workflow/semantic_gate.py:11`
- 줄바꿈을 문장 종료로 세지 않도록 답변 압축 로직을 수정했다.
  - `ai/app/validation/text.py:37`
- 모든 완료 요청에 `route`, `ollama_duration`, `fallback_reason`, `v2_hit`를 기록하는 일자별 JSONL 로그를 추가했다.
  - `ai/app/observability.py:56`
  - `ai/app/workflow/runner.py:526`

## 재발 방지 / 메모

- 기본 모델과 fallback 모델이 같아도 네트워크·프로세스성 실패에는 1회 재시도가 필요하다.
- 목록, 코드 블록, 여러 문단을 포함하는 답변을 문자 단위 줄바꿈으로 문장 수 제한하면 안 된다.
- 실제 equals 재테스트에서 Ollama 생성 결과가 의미상 모순으로 판정됐으며, 최종 응답은 `quality_validation` fallback의 정확한 설명으로 대체됐다. 검색된 컨텍스트가 `java-equals`가 아닌 일반 Java 카드였던 점은 남은 품질 위험이다.

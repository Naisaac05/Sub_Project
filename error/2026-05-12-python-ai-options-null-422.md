# Python AI rejected null request fields with 422

- 발생 일시: 2026-05-12
- 영역: ai / backend
- 심각도: medium

## 증상
Python AI 서버 로그에 `POST /api/review/free-question HTTP/1.1" 422 Unprocessable Entity` 및 `POST /api/review/first-question HTTP/1.1" 422 Unprocessable Entity`가 반복 출력됐다. 같은 서버의 `/health`는 `200 OK`였고, 일부 `free-question` 요청은 정상적으로 `200 OK`를 반환했다. 프론트엔드 화면에는 "답변을 제출하지 못했습니다."가 표시됐다.

## 원인
FastAPI 요청 스키마가 `options`를 `list[str]`로만 받고, `question`, `correct_answer`, `selected_answer`, `user_answer`, `evaluation`, `model`도 `str`로만 받도록 되어 있었다. 추가로 `step`, `temperature`, `max_tokens` 같은 숫자 필드도 `null` 또는 빈 문자열이면 Pydantic 검증에서 거절됐다. 백엔드 또는 테스트 요청에서 복습 질문의 일부 값이 없는 경우 `null` 형태로 직렬화될 수 있고, `options` 내부에 `null`이나 숫자가 섞일 수도 있는데, Pydantic은 이런 값을 그대로 리스트/문자열/숫자로 인정하지 않아 요청 본문 검증 단계에서 422를 반환했다.

관련 파일:
- ai/app/schemas.py:5
- ai/app/service.py:19
- backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:108

## 해결 방법
Python AI 요청 스키마의 `options` 타입을 `list[str] | None`으로 완화하고, 문자열 필드는 `field_validator(..., mode="before")`로 문자열 변환 또는 빈 문자열 정규화를 수행했다. `options` 내부의 `null`은 제거하고 숫자 등은 문자열로 변환한다. 숫자 필드는 `None`, 빈 문자열, 파싱 불가능한 값이면 기본값으로 정규화한다. `model`은 비어 있으면 `qwen2.5:1.5b`로 되돌린다.

수정 파일:
- ai/app/schemas.py:5

확인:
- `options: null`을 포함한 `POST http://127.0.0.1:8001/api/review/free-question` 요청이 수정 전에는 422를 반환했다.
- `question: null`, `correct_answer: null`, `selected_answer: null`, `evaluation: null` 요청도 수정 전에는 422를 반환했다.
- `step: null`, `temperature: null`, `max_tokens: null` 요청도 수정 전에는 422를 반환했다.
- `question: 123`, `options: [null, 42, "선택지"]`, `step: ""`, `temperature: ""`, `max_tokens: ""`, `model: null` 요청도 수정 전에는 422 또는 500을 반환했다.
- 수정 후 동일 계열 요청이 `200 OK`와 `provider: python-ollama` 응답을 반환하는 것을 확인했다.

## 재발 방지 / 메모
AI gateway 성격의 Python 서버는 백엔드 데이터가 일부 비어 있어도 가능한 한 빈 값으로 정규화해야 한다. Python AI 서버가 `--reload` 없이 실행 중이면 스키마 수정 후 프로세스를 재시작해야 한다.

추가 확인: 파일 수정 후에도 422가 반복된 원인은 실행 중인 FastAPI 앱이 `main.py` 변경을 아직 반영하지 않은 상태였기 때문이다. `/openapi.json`에서 requestBody가 계속 `AiGenerateRequest`로 표시되어 있었고, 이는 엔드포인트가 여전히 Pydantic 모델을 직접 받고 있음을 의미했다. AI 서버 프로세스를 직접 재시작한 뒤 `/openapi.json`의 requestBody가 `Payload`로 바뀌었고, 배열 바디 및 타입이 섞인 객체 바디도 `200 OK`로 통과했다.

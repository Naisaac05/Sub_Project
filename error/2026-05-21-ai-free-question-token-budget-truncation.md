# AI free-question answers truncated by low token budget

- 발생 일시: 2026-05-21
- 영역: ai / workflow
- 심각도: medium

## 증상

자유 질문에서 `"push notification의 정의"`를 물었을 때 답변이 `"알림을 보내"`처럼 문장 중간에서 끊겨 표시됐다. UI에는 약 20초 이상 생성 시간이 표시됐지만 최종 답변은 종결 어미나 문장 부호 없이 잘린 상태였다.

## 원인

Spring과 Python request 기본값은 `max_tokens=256`이지만, Python workflow가 `free-question` 생성 요청을 다시 `70` token으로 제한했다: `ai/app/workflow/nodes.py:205`. `free_question_v1` prompt는 최대 3문장 답변을 요구하므로, 짧은 개념 정의와 예시를 생성하는 중에 Ollama `num_predict` 한도에 닿아 답변이 문장 중간에서 끊길 수 있었다.

## 해결 방법

`free-question` token cap을 70에서 128로 올려 짧은 2~3문장 정의가 완결될 여지를 확보했다: `ai/app/workflow/nodes.py:210`. 회귀 테스트도 기존 “70 이하” 강제에서 “120~140 범위의 완결 가능한 예산” 검증으로 바꿨다: `ai/tests/test_workflow_runner.py:669`.

## 재발 방지 / 메모

생성 token cap은 latency만 보지 말고 답변 완결성도 같이 봐야 한다. 이미 잘린 답변이 answer cache에 들어간 경우 같은 질문은 코드 수정 뒤에도 cache hit로 잘린 답변을 받을 수 있으므로, 배포 후 AI 프로세스 재시작 또는 `AI_REVIEW_ANSWER_CACHE_PATH` 파일 삭제/무효화가 필요하다.

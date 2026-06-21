---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI semantic judge lite prompt version drift 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI semantic judge lite prompt version drift

- 발생 일시: 2026-06-09
- 영역: ai
- 심각도: low

## 증상
`python -m unittest discover -s tests -v` 실행 중 prompt versioning 테스트 2개가 실패했다.

- `ai/tests/test_prompt_versioning.py:108`
- `ai/tests/test_prompt_versioning.py:145`

실제 workflow state와 observability event에는 `semantic_judge_lite_v1`이 기록됐지만, 테스트는 기존 `semantic_judge_v1`을 기대했다.

## 원인
Semantic Judge 구현이 lightweight prompt로 전환되어 `LITE_SEMANTIC_JUDGE_PROMPT_VERSION = "semantic_judge_lite_v1"`을 사용하고 있었다.

하지만 prompt artifact registry에는 lite judge 버전이 등록되지 않았고, prompt versioning 테스트도 이전 full judge 버전 문자열을 직접 기대하고 있었다. 즉, runtime prompt version source와 테스트/registry metadata가 서로 drift 난 상태였다.

관련 코드:

- `ai/app/workflow/judge.py:19`
- `ai/app/prompts/registry.py:48`
- `ai/tests/test_prompt_versioning.py:108`
- `ai/tests/test_prompt_versioning.py:145`

## 해결 방법
`semantic_judge_lite_v1`을 prompt registry에 등록하고, 테스트는 하드코딩 문자열 대신 `LITE_SEMANTIC_JUDGE_PROMPT_VERSION` 상수를 기대하도록 변경했다.

수정 파일:

- `ai/app/prompts/registry.py:55`
- `ai/tests/test_prompt_versioning.py:5`
- `ai/tests/test_prompt_versioning.py:109`
- `ai/tests/test_prompt_versioning.py:146`

검증:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_prompt_versioning.PromptVersioningTest.test_retry_metadata_isolation tests.test_prompt_versioning.PromptVersioningTest.test_observability_metadata_emission -v
```

결과: 2 tests OK.

## 재발 방지 / 메모
Prompt version 문자열은 테스트에 직접 하드코딩하지 말고, runtime에서 사용하는 상수 또는 registry 조회 결과와 연결해야 한다. 특히 judge prompt를 lite/full로 분리할 때는 registry metadata도 같이 갱신해야 observability event와 prompt artifact 추적이 어긋나지 않는다.

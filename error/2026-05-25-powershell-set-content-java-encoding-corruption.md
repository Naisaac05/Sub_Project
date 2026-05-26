# PowerShell Set-Content Java encoding corruption

- 발생 일시: 2026-05-25
- 영역: backend / Windows
- 심각도: medium

## 증상

`AiReviewStreamingService.java`, `RuleBasedAiReviewService.java` 일부를 PowerShell `Set-Content -Encoding UTF8`로 재저장한 뒤 `compileJava`가 `illegal character: '\ufeff'`, `unclosed string literal` 등 다수의 컴파일 오류로 실패했다.

## 원인

기존 Java 파일에 이미 인코딩이 어긋난 한글 문자열이 섞여 있었고, PowerShell 전체 파일 재저장이 BOM 추가 및 문자열 재해석을 일으키면서 파일 전반의 문자열 리터럴이 더 깨졌다. 특히 수동 라인 치환을 전체 파일 재쓰기 방식으로 처리한 것이 문제였다.

## 해결 방법

- `git show HEAD:<path>`로 두 Java 파일의 기준 버전을 복구한 뒤 필요한 변경을 `apply_patch`로 다시 적용했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:1`, `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:1`
- 공용 컨텍스트 support 추출은 새 파일과 좁은 patch로 반영했다: `backend/src/main/java/com/devmatch/service/ai/AiReviewContextSupport.java:1`
- 회귀 테스트로 컴파일 및 AI 서비스 동작을 확인했다: `backend/src/test/java/com/devmatch/service/ai/AiReviewContextSupportTest.java:1`

## 재발 방지 / 메모

Windows에서 Java 파일을 부분 수정할 때는 전체 파일을 `Set-Content`로 재저장하지 말고 `apply_patch`를 우선 사용한다. 인코딩이 이미 불안정한 파일은 특히 문자열 리터럴이 깨질 수 있으므로, 복구가 필요하면 기준 버전을 먼저 확보하고 작은 patch 단위로 재적용한다.

# PowerShell UTF-8 JSON Pipeline Mojibake

- 발생 일시: 2026-06-15
- 영역: ai / RAG card batch validation
- 심각도: medium

## 증상

`Get-Content -Raw ... | ConvertFrom-Json`으로 한국어 PATCHES_READY와 적용 보고서를 검사할 때 정상 UTF-8 문자열이 깨져 보였고, 일부 실행에서는 유효한 JSON을 `ConvertFrom-Json`이 파싱 오류로 판단했다.

## 원인

현재 PowerShell 콘솔과 파이프라인의 문자 인코딩 처리 과정에서 UTF-8 한국어 JSON 문자열이 재해석되었다. 원본 파일과 실제 적용된 카드 파일은 UTF-8로 정상이며, Node.js에서 `fs.readFile(..., "utf8")` 후 `JSON.parse`한 결과로 확인했다.

관련 파일:

- `ai/reports/course_balanced_next20_ready_2026-06-14.json`
- `ai/reports/course_balanced_next20_applied_2026-06-15.json`

## 해결 방법

한국어 JSON의 내용 검증과 구조 비교는 Node.js의 UTF-8 원문 읽기 및 `JSON.parse`로 수행했다. JSON 문법 검증은 프로젝트 가상환경의 `python -m json.tool`로 별도 수행했다.

## 재발 방지·메모

- 한국어 JSON을 `Get-Content | ConvertFrom-Json` 결과만으로 손상 판단하지 않는다.
- 배치 적용 전후 payload 및 잠금 필드 비교는 UTF-8 인코딩을 명시하는 Node.js 또는 Python 검증기를 사용한다.
- PowerShell 출력에서 mojibake가 보여도 원본 바이트와 독립 파서 결과를 먼저 확인한다.

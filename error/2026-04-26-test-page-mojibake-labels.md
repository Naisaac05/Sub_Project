# Test page labels rendered as mojibake

- 발생 날짜: 2026-04-26
- 영역: frontend
- 심각도: medium

## 증상

코스별 실력 테스트 화면과 테스트 응시 화면의 고정 UI 문구가 깨진 문자열로 저장되어 브라우저에서 `?` 또는 mojibake 형태로 보일 수 있었다. 테스트 문제 자체를 새로 추가해도 목록, 제출, 결과, 이전/다음 같은 안내 문구가 깨져 사용자가 테스트 흐름을 이해하기 어려운 상태였다.

## 원인

기존 `frontend/src/app/tests/page.tsx`와 `frontend/src/app/tests/[id]/page.tsx`에 한글 라벨이 이미 깨진 상태로 하드코딩되어 있었다. 일부 화면은 새 기능의 진입점이 되면서 깨진 문자열이 그대로 노출될 가능성이 있었다.

## 해결 방법

- `frontend/src/app/tests/page.tsx`: 코스별 진단 테스트 홈으로 재구성하고, 노출되는 한글 라벨을 유니코드 escape 기반 상수로 분리했다.
- `frontend/src/app/tests/[id]/page.tsx`: 테스트 응시, 제출 확인, 결과 화면의 깨진 문구를 제거하고 진단용 점수 안내 문구로 교체했다.
- 관련 백엔드 씨드는 `backend/src/main/java/com/devmatch/config/CourseSkillTestInitializer.java`에 분리해 기존 깨진 seed 파일을 직접 수정하지 않도록 했다.

## 재발 방지·메모

한글 UI 문구가 다시 `???` 또는 mojibake로 보이면 PowerShell 출력이 아니라 실제 소스 파일 문자열이 깨졌는지 먼저 확인한다. 새로 추가하는 TSX 라벨은 `LABELS` 상수로 모으고, 인코딩이 불안정한 파일에서는 유니코드 escape를 사용하는 편이 안전하다.

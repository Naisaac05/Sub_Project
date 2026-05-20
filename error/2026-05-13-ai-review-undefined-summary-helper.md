# AI review undefined summary helper

- 발생 일시: 2026-05-13
- 영역: frontend
- 심각도: medium

## 증상
AI 복습 리뷰 화면 진입 시 Next.js 개발 오버레이에서 `ReferenceError: summarizeQuestionMessages is not defined`가 표시됐다. 오류 위치는 `frontend/src/app/tests/results/[id]/review/page.tsx:259`의 이전 문제 요약 생성 로직과 `frontend/src/app/tests/results/[id]/review/page.tsx:485`의 문제 카드 요약 렌더링이었다.

## 원인
문제별 대화 요약 UI에서 `summarizeQuestionMessages`를 호출했지만 해당 helper 함수가 파일 안에 정의되어 있지 않았다. 같은 변경 흐름에서 `conversationSummaryLabel` 내부의 `evaluationText`, 메시지 평가 표시용 `evaluationLabel`, 이전 요약 라벨, `ChevronRight` 아이콘 import도 누락되어 있어 첫 ReferenceError를 해결해도 후속 런타임/컴파일 오류가 날 수 있는 상태였다.

## 해결 방법
`frontend/src/app/tests/results/[id]/review/page.tsx:16`에 `ChevronRight` import를 추가하고, `frontend/src/app/tests/results/[id]/review/page.tsx:50`에 `previousSummary` 라벨을 추가했다. `frontend/src/app/tests/results/[id]/review/page.tsx:90`에 `evaluationLabel`, `frontend/src/app/tests/results/[id]/review/page.tsx:120`에 `summarizeQuestionMessages`를 정의해서 저장된 문제 요약이 있으면 우선 표시하고, 없으면 현재 대화의 최신 평가와 자유 질문 수를 기반으로 간단한 요약을 표시하도록 했다.

## 재발 방지 / 메모
프론트엔드 helper를 추가하는 UI 변경에서는 호출부와 정의/import가 함께 커밋됐는지 확인해야 한다. `npm.cmd run lint`는 현재 ESLint 설정이 없어 Next.js 초기 설정 프롬프트에서 멈추며, `npx.cmd tsc --noEmit`은 이번 파일이 아닌 기존 admin/chart/Radix 타입 문제에서 실패한다.

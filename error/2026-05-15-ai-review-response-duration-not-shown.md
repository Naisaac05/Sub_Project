# AI review response duration not shown

- 발생 일시: 2026-05-15
- 영역: frontend
- 심각도: low

## 증상

스마트 개념 복습에서 AI 답변 위에 지연시간이 표시되지 않았다. 새 답변 직후에도 상황에 따라 보이지 않거나, 페이지를 새로고침하면 기존 AI 답변의 지연시간이 사라졌다.

## 원인

프론트엔드가 지연시간을 `messageDurations` 로컬 state에만 저장했다. 이 값은 새 요청이 성공한 직후 `res.data.messages`에 포함된 AI 메시지 id에만 채워지고, 서버에서 다시 불러온 기존 메시지에는 복원되지 않았다. 또한 초기 질문 프롬프트는 서버 저장 메시지가 아니라 화면에서 임시 생성한 안내 문구라 지연시간 계산 대상이 아니었다.

## 해결 방법

AI 메시지에 직접 측정한 시간이 있으면 그 값을 우선 사용하고, 없으면 같은 문제의 직전 사용자 메시지 `createdAt`과 AI 메시지 `createdAt` 차이로 지연시간을 추정하도록 수정했다.

- `frontend/src/app/tests/results/[id]/review/page.tsx:98`
- `frontend/src/app/tests/results/[id]/review/page.tsx:111`
- `frontend/src/app/tests/results/[id]/review/page.tsx:623`

## 재발 방지 / 메모

UI에 표시할 성능 정보가 새로고침 후에도 필요하면 서버 응답 필드로 저장하거나, 최소한 저장된 타임스탬프에서 복원하는 경로를 함께 둔다. 화면에서만 만든 임시 메시지는 실제 AI 처리 시간이 없으므로 지연시간 표시 대상에서 제외한다.

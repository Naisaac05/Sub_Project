# LMS 커리큘럼에서 주차 추가 시 "상태 변경에 실패" 문구 + 실제로 새 주차가 저장되지 않음

- 발생일: 2026-04-12
- 영역: backend / frontend
- 심각도: high

## 증상

- LMS 커리큘럼 페이지에 이미 8주차까지 등록된 상태에서, 멘토가 "주차 추가" 버튼을 눌러 9주차를 추가하려고 하면 모달에 "상태 변경에 실패했습니다" 라는 빨간 문구가 떠 있다.
- 그 상태에서 "추가" 버튼을 눌러도 (HTTP 200이 떨어지더라도) 새로고침하면 여전히 주차가 8개까지만 존재하고 9주차가 생기지 않는다.

## 원인

두 가지 버그가 겹쳐 있었다.

### 1. 백엔드가 `request.getWeeks()` 를 아예 처리하지 않음 (진짜 원인)

`backend/src/main/java/com/devmatch/service/CurriculumService.java`의 `update()` 메서드는 `curriculum.update(title, description, totalWeeks, startDate, endDate, discordUrl)` 만 호출하고 `request.getWeeks()` 리스트를 완전히 무시했다. 그래서 프론트에서 "기존 주차 + 새 주차"를 합쳐 PUT 으로 보내도, DB에 `CurriculumWeek` 레코드가 하나도 새로 저장되지 않았다.

→ `create()` 쪽에는 `for` 루프가 있지만 `update()` 쪽에는 처음부터 누락돼 있었다.

### 2. "상태 변경에 실패" 문구는 이전 동작의 잔상 (표면 증상)

"상태 변경에 실패했습니다" 라는 메시지는 오직 `handleToggle` 에서만 세팅된다 (`frontend/src/app/lms/curriculum/page.tsx:45`). 그런데:

- 주차 옆 체크 버튼(완료 토글)이 **멘토에게도 노출**되어 있었음 (`curriculum/page.tsx:195`).
- 백엔드 `CurriculumService.toggleWeekComplete()` 는 `validateMenteeAccess` 로 멘티만 허용 (`CurriculumService.java:71`).
- 즉, 멘토가 동그라미를 한 번이라도 클릭하면 403 → 프론트 catch → `setError('상태 변경에 실패했습니다')` 로 `error` 상태가 오염됨.
- 그 뒤 "주차 추가" 모달을 열 때 `error` 상태를 초기화하는 코드가 없어서 잔류한 오류 문구가 그대로 모달에 뜸 (`{error && <p ...>{error}</p>}`).

## 해결 방법

### 백엔드

- `backend/src/main/java/com/devmatch/entity/CurriculumWeek.java:54`: 완료 상태(`isCompleted`, `completedAt`) 를 건드리지 않고 내용만 갱신하는 `updateContent(...)` 메서드를 추가.
- `backend/src/main/java/com/devmatch/service/CurriculumService.java:57`: `update()` 에 `syncWeeks(curriculum, request.getWeeks())` 호출 추가. 동기화 규칙:
  - `weekNumber` 기준으로 기존 주차를 찾아 `updateContent()` 로 제목/설명/주제/자료만 덮어쓰기 (완료 상태 보존)
  - 요청에 있는데 DB에 없으면 `CurriculumWeek.builder()` 로 새로 만들어 `curriculum.addWeek()`
  - 요청에서 빠진 기존 주차는 `curriculum.getWeeks().removeIf(...)` 로 삭제 (orphanRemoval 로 cascade)

### 프론트엔드

- `frontend/src/app/lms/curriculum/page.tsx:41` — `handleToggle`에서 `isMentor` 면 조기 리턴. 또한 진입 시 `setError('')` 로 초기화.
- `frontend/src/app/lms/curriculum/page.tsx:195` — 주차 완료 동그라미를 `isMentor` 면 클릭 불가능한 `<div>` 로 렌더링하고 `title="멘티만 완료 처리할 수 있습니다"` 안내. 멘티일 때만 `<button onClick={handleToggle}>` 로 렌더.
- `frontend/src/app/lms/curriculum/page.tsx:103, 169, 203` — 커리큘럼 생성 / 주차 추가 / 주차 수정 버튼 핸들러마다 `setError('')` 를 추가해서 모달을 열 때 잔류 에러가 덮어지도록 함.

## 재발 방지 / 메모

- `CurriculumCreateRequest` 같이 `create` 와 `update` 가 동일한 DTO 를 공유하는 경우, `update` 서비스에서 자식 컬렉션(`weeks`) 처리를 빼먹기 쉽다. 앞으로 "같은 DTO 를 받는 update 메서드" 를 볼 때마다 자식 엔티티 동기화 로직이 있는지 반드시 확인할 것.
- 역할(role) 별 접근 제어는 백엔드에서만 하지 말고, UI 에서도 "멘토한테는 토글 버튼을 안 보이게" 처럼 한 겹 더 막아야 한다. 그렇지 않으면 백엔드 403 이 UI 에 의미 없는 오류 메시지로 떠서 유저를 혼란스럽게 한다.
- React 에서 `error` 같은 글로벌 상태를 여러 플로우(토글/생성/수정)가 공유하면, 한 플로우의 실패가 다른 플로우의 모달로 번진다. 모달 오픈 시점에 `setError('')` 로 명시적으로 초기화하거나, 아예 플로우별로 에러 상태를 분리하는 걸 기본 패턴으로 삼자.

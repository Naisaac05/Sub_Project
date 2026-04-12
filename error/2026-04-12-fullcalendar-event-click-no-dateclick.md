# FullCalendar 이벤트 클릭 시 모달이 뜨지 않음

- 발생일: 2026-04-12
- 영역: frontend
- 심각도: medium

## 증상

LMS 세션 페이지(`frontend/src/app/lms/sessions/page.tsx`)에서 멘티가 제안한 시간이 캘린더에 앰버 색 이벤트로 표시되는데, 멘토가 해당 이벤트를 클릭해도 아무 반응이 없어 승인/거절 UI가 뜨지 않음.

## 원인

FullCalendar는 날짜 셀 여백을 클릭하면 `dateClick`을, 이벤트 위를 클릭하면 `eventClick`을 발동한다. 두 핸들러는 동시에 발동하지 않는다. 기존 구현은 `dateClick`만 정의되어 있었고, 멘티 제안 슬롯이 있는 날짜 전체를 이벤트가 덮고 있어서 "제안 이벤트 위를 클릭 → `eventClick` 발동 → 핸들러 없음 → 무반응"이 되었다.

참고 메시지:
- `.fc .fc-event { cursor: default }` 스타일도 클릭 가능함을 감추는 데 기여.

## 해결 방법

- `frontend/src/app/lms/sessions/page.tsx`에 `eventClick` 핸들러 추가. 슬롯 이벤트 클릭 시 `openModalForDate(date)`를 호출해 `dateClick`과 동일한 모달을 연다.
- `handleDateClick`을 `openModalForDate`로 리팩터링해 두 핸들러가 동일 로직을 공유.
- `.fc .fc-event` 커서를 `pointer`로 변경해 클릭 가능함을 시각적으로 노출.
- FullCalendar prop에 `eventClick={handleEventClick}` 추가.

## 재발 방지 / 메모

- FullCalendar에서 이벤트와 날짜 셀 모두 클릭 대상으로 쓰려면 `dateClick` + `eventClick` 두 핸들러를 모두 연결해야 한다. 이벤트가 날짜 셀을 덮고 있으면 `dateClick`은 발동하지 않는다.
- 캘린더 UI에서 "클릭해도 반응 없음" 신고가 들어오면 `eventClick` 존재 여부와 `cursor` 스타일을 먼저 점검.

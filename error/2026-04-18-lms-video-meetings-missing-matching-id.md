# [LMS 화상회의 직접 진입 시 matchingId 유실로 기능 비활성]

- 발생 일시: 2026-04-18
- 영역: frontend
- 심각도: medium

## 증상
멘토가 LMS 화상회의 화면에 들어갔는데 세션 목록이 비어 있고, 화상회의 등록 버튼도 보이지 않아 아무 작업도 할 수 없었다.

## 원인
[frontend/src/app/lms/(dashboard)/video-meetings/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/lms/(dashboard)/video-meetings/page.tsx:24) 는 `matchingId` 쿼리스트링이 있을 때만 세션과 미팅 정보를 조회하도록 되어 있었다.  
하지만 사용자가 `/lms/video-meetings` 로 직접 진입하거나 `matchingId` 없는 주소로 새로고침하면 조회를 중단하고 빈 화면 상태로 빠졌고, 그 결과 멘토용 등록 버튼도 표시되지 않았다.

## 해결 방법
[frontend/src/app/lms/(dashboard)/video-meetings/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/lms/(dashboard)/video-meetings/page.tsx:49) 에서 `matchingId` 가 없을 때 현재 로그인 사용자의 활성 매칭을 조회해 자동으로 `?matchingId=...` 를 붙여 다시 진입하도록 수정했다.  
활성 매칭 자체가 없는 경우에도 [frontend/src/app/lms/(dashboard)/video-meetings/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/lms/(dashboard)/video-meetings/page.tsx:193) 에 명확한 안내 문구가 보이도록 처리했다.

## 재발 방지 / 메모
LMS 하위 페이지가 특정 `matchingId` 컨텍스트에 의존한다면, 쿼리스트링 누락 시 바로 빈 화면으로 끝내지 말고 자동 복구하거나 최소한 이유를 설명하는 안내 상태를 제공하는 편이 안전하다.

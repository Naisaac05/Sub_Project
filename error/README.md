# 에러 기록 가이드

프로젝트에서 원인까지 확인한 에러와 재발 가능성이 있는 이슈를 남기는 공간입니다. 기록은 짧아도 되지만, 다음 사람이 같은 문제를 만났을 때 바로 이해하고 대응할 수 있어야 합니다.

## 파일명 규칙

`YYYY-MM-DD-짧은-제목.md` 형식을 사용합니다. 제목은 원인을 빠르게 파악할 수 있게 구체적으로 적습니다.

## 작성 템플릿

아래 템플릿을 그대로 복사해서 사용합니다.

```markdown
# [제목]

- 발생 일시: YYYY-MM-DD
- 영역: backend / frontend / infra / docker / DB
- 심각도: low / medium / high

## 증상
(사용자가 겪은 현상, 화면/로그/버튼 동작 등을 짧게 설명)

## 원인
(실제로 확인한 원인과 왜 그런 문제가 생겼는지 정리)

## 해결 방법
(수정 내용과 반영 파일을 정리. 관련 파일은 경로:line 형식으로 정확히 링크)

## 재발 방지 / 메모
(남은 리스크, 환경 특이사항, 다음에 볼 때 주의할 점)
```

## 인덱스

- [2026-04-12 시드 멘토 계정 로그인 실패](2026-04-12-mentor-seed-password-mismatch.md)
- [2026-04-12 마이페이지 멘토 매칭 목록 누락](2026-04-12-mypage-mentor-matchings-missing.md)
- [2026-04-12 FullCalendar 이벤트 클릭 시 dateClick 미발생](2026-04-12-fullcalendar-event-click-no-dateclick.md)
- [2026-04-12 LMS 과제 유형 select 옵션 비가시 문제](2026-04-12-lms-assignments-select-option-invisible.md)
- [2026-04-12 LMS 커리큘럼 주차 추가 실패](2026-04-12-lms-curriculum-week-add-fails.md)
- [2026-04-12 LMS 커리큘럼 주차 데이터 재시작 후 유지 문제](2026-04-12-lms-curriculum-weeks-persist-restart.md)
- [2026-04-12 Refresh token 세션 구조 개편](2026-04-12-auth-refresh-token-session-redesign.md)
- [2026-04-18 커뮤니티 더미 게시글을 실제 작성 흐름으로 교체](2026-04-18-community-dummy-posts-replaced-with-real-posting.md)
- [2026-04-18 LMS 과제 경로 오연결 및 빈 화상회의 조회 예외](2026-04-18-lms-assignments-route-and-empty-video-meeting-lookup.md)
- [2026-04-18 화상회의 페이지 진입 시 보조 조회 실패로 알림이 두 번 뜨는 문제](2026-04-18-video-meetings-secondary-lookup-alert.md)
- [2026-04-18 멘토링 코스 루트 404 및 가짜 후기/정적 신청 배너 문제](2026-04-18-mentoring-course-page-cleanup.md)
- [2026-04-18 LMS 화상회의 직접 진입 시 matchingId 유실로 기능 비활성](2026-04-18-lms-video-meetings-missing-matching-id.md)
- [2026-04-18 화상회의 수정 후 제목/URL 상태 미반영](2026-04-18-video-meeting-edit-state-sync.md)
- [2026-04-18 회원가입 시 MENTOR를 선택해도 항상 MENTEE로 저장됨 (stale build로 실행된 백엔드)](2026-04-18-signup-role-always-mentee-stale-build.md)
- [2026-04-18 Course API 코드 리뷰 지적사항 4건 반영 (CourseNotFoundException, ResponseEntity, Swagger, 테스트)](2026-04-18-course-api-review-fixes.md)
- [2026-04-18 Phase B 멘토 리팩터 — JPA PersistentSet 갱신 버그, Test 카테고리 슬러그 불일치, 이력 무결성 drift](2026-04-18-mentor-refactor-jpa-fixes.md)
- [2026-04-20 ICON_MAP 타입 불일치 — ComponentType<{size?: number}> vs LucideIcon](2026-04-20-icon-map-lucide-type-mismatch.md)
- [2026-04-20 로그인 직후 MENTOR가 `/mentor/status`가 아닌 랜딩으로 가는 경쟁 조건](2026-04-20-login-redirect-race-mentor-to-landing.md)
- [2026-04-20 `/mentor/apply` 제공 가능한 코스 목록 비어 있음 (백엔드를 main 폴더에서 실행)](2026-04-20-mentor-apply-courses-empty-backend-wrong-folder.md)
- [2026-04-20 멘토 프로필 반려 사유(`rejectedReason`) 백엔드 갭 — 관리자 엔드포인트 부재](2026-04-20-mentor-profile-rejected-reason-backend-missing.md)
- [2026-04-21 Windows worktree 제거 시 `frontend/node_modules` 파일 잠금 — Gradle Daemon + Defender 복합 원인, rename-then-delete 우회](2026-04-21-windows-worktree-remove-file-lock.md)
- [2026-04-21 `/lms/assignments` Next.js 빌드 실패 — `useSearchParams()` Suspense 경계 부재](2026-04-21-lms-assignments-suspense-prerender.md)

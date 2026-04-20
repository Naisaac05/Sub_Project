# 에러 트러블슈팅 로그

프로젝트를 진행하면서 마주친 에러/버그와 원인, 해결 방안을 기록하는 공간입니다. 같은 문제를 다시 만났을 때 빠르게 대처하고, 팀원 간 공유하기 위한 용도입니다.

## 파일 네이밍

`YYYY-MM-DD-짧은-제목.md` 형식으로 작성합니다. 최신 항목이 자동으로 아래쪽에 오도록 날짜 정렬합니다.

## 템플릿

새 에러를 기록할 때는 아래 구조를 사용하세요.

```markdown
# [제목]

- 발생일: YYYY-MM-DD
- 영역: backend / frontend / infra / docker / DB
- 심각도: low / medium / high

## 증상
(어떤 화면/명령에서 어떻게 실패하는지, 에러 메시지 원문)

## 원인
(근본 원인 — 추측이 아닌 확인된 사실을 씁니다)

## 해결 방법
(실제로 적용한 조치. 관련 파일 경로:line 포함)

## 재발 방지 / 메모
(다음에 같은 실수를 안 하려면 무엇을 체크해야 하는지)
```

## 인덱스

- [2026-04-12 — 시드 멘토 계정 로그인 실패](2026-04-12-mentor-seed-password-mismatch.md)
- [2026-04-12 — 마이페이지에서 멘토가 멘티 매칭을 못 봄](2026-04-12-mypage-mentor-matchings-missing.md)
- [2026-04-12 — FullCalendar 이벤트 클릭 시 모달 안 뜸](2026-04-12-fullcalendar-event-click-no-dateclick.md)
- [2026-04-12 — LMS 과제 만들기 select 드롭다운이 흰 배경+흰 글자](2026-04-12-lms-assignments-select-option-invisible.md)
- [2026-04-12 — LMS 커리큘럼에서 주차 추가가 실제로 저장되지 않음 (+ 잔상 에러)](2026-04-12-lms-curriculum-week-add-fails.md)
- [2026-04-12 — LMS 커리큘럼 주차가 여전히 반영 안 됨 → 백엔드 재시작 필요](2026-04-12-lms-curriculum-weeks-persist-restart.md)
- [2026-04-12 — Refresh token 세션 재설계 (Redis 세션 + HttpOnly 쿠키 + rotation/reuse detection)](2026-04-12-auth-refresh-token-session-redesign.md)
- [2026-04-18 — 회원가입 시 MENTOR를 선택해도 항상 MENTEE로 저장됨 (stale build로 실행된 백엔드)](2026-04-18-signup-role-always-mentee-stale-build.md)
- [2026-04-18 — Course API 코드 리뷰 지적사항 4건 반영 (CourseNotFoundException, ResponseEntity, Swagger, 테스트)](2026-04-18-course-api-review-fixes.md)
- [2026-04-18 — Phase B 멘토 리팩터 — JPA PersistentSet 갱신 버그, Test 카테고리 슬러그 불일치, 이력 무결성 drift](2026-04-18-mentor-refactor-jpa-fixes.md)
- [2026-04-20 — ICON_MAP 타입 불일치 — ComponentType<{size?: number}> vs LucideIcon](2026-04-20-icon-map-lucide-type-mismatch.md)

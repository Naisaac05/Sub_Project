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

- [2026-05-15 AI review qwen3 timeout double provider wait](2026-05-15-ai-review-qwen3-timeout-double-provider-wait.md)
- [2026-05-15 AI review free question count included AI prompts](2026-05-15-ai-review-free-question-count-included-ai-prompts.md)
- [2026-05-15 AI review direct AI answer timeout config](2026-05-15-ai-review-direct-ai-answer-timeout-config.md)
- [2026-05-15 AI review Next proxy timeout regression](2026-05-15-ai-review-next-proxy-timeout-regression.md)
- [2026-05-15 AI review free question limit bypass](2026-05-15-ai-review-free-question-limit-bypass.md)
- [2026-05-15 AI review response duration not shown](2026-05-15-ai-review-response-duration-not-shown.md)
- [2026-05-15 AI review misleading backend 500 message](2026-05-15-ai-review-misleading-backend-500-message.md)
- [2026-05-13 AI review request failed while slow local AI continued](2026-05-13-ai-review-ai-call-timeout-socket-hang-up.md)
- [2026-05-13 AI review submit failed after summary message](2026-05-13-ai-review-summary-message-submit-500.md)
- [2026-05-13 AI review undefined summary helper](2026-05-13-ai-review-undefined-summary-helper.md)
- [2026-05-12 AI review AUTO provider selected stopped Python before Ollama fallback](2026-05-12-ai-review-auto-provider-python-blocked-ollama.md)
- [2026-05-12 Python AI rejected null request fields with 422](2026-05-12-python-ai-options-null-422.md)
- [2026-05-12 Ollama repeated AI review answer paragraphs](2026-05-12-ollama-repeated-ai-review-answer.md)
- [2026-04-30 AI review submit full message payload](2026-04-30-ai-review-submit-full-message-payload.md)
- [2026-04-30 AI review Ollama submit timed out before saved response was returned](2026-04-30-ai-review-ollama-submit-timeout.md)
- [2026-04-29 Diagnostic test result showed pass/fail wording](2026-04-29-diagnostic-test-result-showed-pass-fail.md)
- [2026-04-26 Course skill tests stayed in preparing state](2026-04-26-course-skill-tests-stuck-preparing.md)
- [2026-04-26 Test page labels rendered as mojibake](2026-04-26-test-page-mojibake-labels.md)
- [2026-04-26 Header conditional render blocked course navigation](2026-04-26-header-conditional-render-blocked-course-navigation.md)
- [2026-04-25 Admin dashboard audit action switch missed mentor change actions](2026-04-25-admin-dashboard-action-switch-missing.md)
- [2026-04-25 Course-driven application entry showed unavailable courses](2026-04-25-course-driven-application-entry.md)
- [2026-04-25 mentor_profile_courses 조인 테이블 데이터 누락으로 매칭 실패](2026-04-25-mentor-course-mapping-missing.md)
- [2026-04-25 Course-specific auto matching did not filter mentor courses](2026-04-25-course-specific-auto-matching.md)
- [2026-04-25 Mentor could not review matched mentee application and unavailable courses were still submittable](2026-04-25-mentor-application-detail-and-course-availability.md)
- [2026-04-25 Apply page course availability guide was shown in English and time entry was too free-form](2026-04-25-apply-course-guide-english-and-time-select.md)
- [2026-04-25 Payment success page did not confirm application matching](2026-04-25-payment-success-token-confirm-matching.md)
- [2026-04-25 Matching application detail text rendered as mojibake](2026-04-25-matching-application-mojibake-text.md)
- [2026-04-25 멘토 매칭 내역에서 멘티 신청서를 확인할 수 없던 문제](2026-04-25-mentor-matching-application-detail.md)
- [2026-04-25 멘티 신청서 자동 매칭 흐름에 멘토 승인/거절 API가 남아 있던 문제](2026-04-25-application-auto-matching-mentor-reject.md)
- [2026-04-25 AdminAuditLog.target_type 컬럼 길이 부족 — MENTOR_CHANGE_REQUEST 삽입 시 500](2026-04-25-admin-audit-log-target-type-too-short.md)
- [2026-04-24 Admin 게시물 상세 페이지 — 비정수 경로 진입 시 스켈레톤 무한 렌더](2026-04-24-admin-post-detail-nan-id-infinite-skeleton.md)
- [2026-04-24 커뮤니티 새 글 카테고리 필터 재발](2026-04-24-community-category-slug-normalization.md)
- [2026-04-23 Subagent 가 feature 브랜치 대신 main 브랜치에 커밋 + 빌드 결과 허위 보고](2026-04-23-subagent-committed-to-main.md)
- [2026-04-21 Windows worktree 제거 시 `frontend/node_modules` 파일 잠금 — Gradle Daemon + Defender 복합 원인, rename-then-delete 우회](2026-04-21-windows-worktree-remove-file-lock.md)
- [2026-04-21 `/lms/assignments` Next.js 빌드 실패 — `useSearchParams()` Suspense 경계 부재](2026-04-21-lms-assignments-suspense-prerender.md)
- [2026-04-21 Pencil MCP — 새 I() 삽입 프레임의 자식이 부모 composite 뷰에서 렌더 안 됨](2026-04-21-pencil-mcp-new-inserts-render-blank.md)
- [2026-04-20 ICON_MAP 타입 불일치 — ComponentType<{size?: number}> vs LucideIcon](2026-04-20-icon-map-lucide-type-mismatch.md)
- [2026-04-20 로그인 직후 MENTOR가 `/mentor/status`가 아닌 랜딩으로 가는 경쟁 조건](2026-04-20-login-redirect-race-mentor-to-landing.md)
- [2026-04-20 `/mentor/apply` 제공 가능한 코스 목록 비어 있음 (백엔드를 main 폴더에서 실행)](2026-04-20-mentor-apply-courses-empty-backend-wrong-folder.md)
- [2026-04-20 멘토 프로필 반려 사유(`rejectedReason`) 백엔드 갭 — 관리자 엔드포인트 부재](2026-04-20-mentor-profile-rejected-reason-backend-missing.md)
- [2026-04-18 회원가입 시 MENTOR를 선택해도 항상 MENTEE로 저장됨 (stale build로 실행된 백엔드)](2026-04-18-signup-role-always-mentee-stale-build.md)
- [2026-04-18 Course API 코드 리뷰 지적사항 4건 반영 (CourseNotFoundException, ResponseEntity, Swagger, 테스트)](2026-04-18-course-api-review-fixes.md)
- [2026-04-18 Phase B 멘토 리팩터 — JPA PersistentSet 갱신 버그, Test 카테고리 슬러그 불일치, 이력 무결성 drift](2026-04-18-mentor-refactor-jpa-fixes.md)
- [2026-04-18 커뮤니티 카테고리 필터 오작동, 게시글 삭제 실패, 이미지 업로드 부재](2026-04-18-community-category-filter-delete-and-image-upload.md)
- [2026-04-18 커뮤니티 더미 게시글을 실제 작성 흐름으로 교체](2026-04-18-community-dummy-posts-replaced-with-real-posting.md)
- [2026-04-18 커뮤니티 카테고리별 글쓰기 경험 부족 및 이미지 미지원](2026-04-18-community-category-form-and-image-preview.md)
- [2026-04-18 LMS 과제 경로 오연결 및 빈 화상회의 조회 예외](2026-04-18-lms-assignments-route-and-empty-video-meeting-lookup.md)
- [2026-04-18 화상회의 페이지 진입 시 보조 조회 실패로 알림이 두 번 뜨는 문제](2026-04-18-video-meetings-secondary-lookup-alert.md)
- [2026-04-18 멘토링 코스 루트 404 및 가짜 후기/정적 신청 배너 문제](2026-04-18-mentoring-course-page-cleanup.md)
- [2026-04-18 LMS 화상회의 직접 진입 시 matchingId 유실로 기능 비활성](2026-04-18-lms-video-meetings-missing-matching-id.md)
- [2026-04-18 화상회의 수정 후 제목/URL 상태 미반영](2026-04-18-video-meeting-edit-state-sync.md)
- [2026-04-18 회원가입 시 MENTOR를 선택해도 항상 MENTEE로 저장됨 (stale build로 실행된 백엔드)](2026-04-18-signup-role-always-mentee-stale-build.md)
- [2026-04-18 Course API 코드 리뷰 지적사항 4건 반영 (CourseNotFoundException, ResponseEntity, Swagger, 테스트)](2026-04-18-course-api-review-fixes.md)
- [2026-04-18 Phase B 멘토 리팩터 — JPA PersistentSet 갱신 버그, Test 카테고리 슬러그 불일치, 이력 무결성 drift](2026-04-18-mentor-refactor-jpa-fixes.md)
- [2026-04-23 Subagent 가 feature 브랜치 대신 main 브랜치에 커밋 + 빌드 결과 허위 보고](2026-04-23-subagent-committed-to-main.md)
- [2026-04-20 ICON_MAP 타입 불일치 — ComponentType<{size?: number}> vs LucideIcon](2026-04-20-icon-map-lucide-type-mismatch.md)
- [2026-04-20 로그인 직후 MENTOR가 `/mentor/status`가 아닌 랜딩으로 가는 경쟁 조건](2026-04-20-login-redirect-race-mentor-to-landing.md)
- [2026-04-20 `/mentor/apply` 제공 가능한 코스 목록 비어 있음 (백엔드를 main 폴더에서 실행)](2026-04-20-mentor-apply-courses-empty-backend-wrong-folder.md)
- [2026-04-20 멘토 프로필 반려 사유(`rejectedReason`) 백엔드 갭 — 관리자 엔드포인트 부재](2026-04-20-mentor-profile-rejected-reason-backend-missing.md)
- [2026-04-21 Windows worktree 제거 시 `frontend/node_modules` 파일 잠금 — Gradle Daemon + Defender 복합 원인, rename-then-delete 우회](2026-04-21-windows-worktree-remove-file-lock.md)
- [2026-04-21 `/lms/assignments` Next.js 빌드 실패 — `useSearchParams()` Suspense 경계 부재](2026-04-21-lms-assignments-suspense-prerender.md)
- [2026-04-21 Pencil MCP — 새 I() 삽입 프레임의 자식이 부모 composite 뷰에서 렌더 안 됨](2026-04-21-pencil-mcp-new-inserts-render-blank.md)
- [2026-04-24 Admin 게시물 상세 페이지 — 비정수 경로 진입 시 스켈레톤 무한 렌더](2026-04-24-admin-post-detail-nan-id-infinite-skeleton.md)
- [2026-04-25 Admin 대시보드 — apiClient baseURL 과 중복돼 `/api/api/admin/dashboard` 호출 (500)](2026-04-25-admin-dashboard-double-api-prefix.md)
- [2026-04-25 Admin 레이아웃 가드가 SUPER_ADMIN 을 거부 — 403 차단](2026-04-25-admin-layout-rejects-super-admin.md)
- [2026-04-25 `/faq` 공개 페이지 카테고리 순서가 어드민·의도와 다른 알파벳 순](2026-04-25-faq-public-page-category-order-mismatch.md)
- [2026-04-12 마이페이지 멘토 매칭 목록 누락](2026-04-12-mypage-mentor-matchings-missing.md)
- [2026-04-12 멘토 시드 비밀번호 불일치](2026-04-12-mentor-seed-password-mismatch.md)
- [2026-04-12 FullCalendar 이벤트 클릭 시 dateClick 미발생](2026-04-12-fullcalendar-event-click-no-dateclick.md)
- [2026-04-12 LMS 과제 유형 select 옵션 비가시 문제](2026-04-12-lms-assignments-select-option-invisible.md)
- [2026-04-12 LMS 커리큘럼 주차 추가 실패](2026-04-12-lms-curriculum-week-add-fails.md)
- [2026-04-12 LMS 커리큘럼 주차 데이터 재시작 후 유지 문제](2026-04-12-lms-curriculum-weeks-persist-restart.md)
- [2026-04-12 Refresh token 세션 구조 개편](2026-04-12-auth-refresh-token-session-redesign.md)

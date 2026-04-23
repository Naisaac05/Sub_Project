# PR #42 수동 통합 스모크 테스트 — Phase II Common + Feature 1 회원 관리

> 작성일: 2026-04-23
> 대상 PR: [#42](https://github.com/Naisaac05/Sub_Project/pull/42)
> 대상 스펙: [admin-console-common](../superpowers/specs/2026-04-22-admin-console-common-design.md), [admin-users](../superpowers/specs/2026-04-23-admin-users-design.md)

본 문서는 PR #42 머지 전 필수로 수행할 수동 통합 스모크 테스트 가이드다. 백엔드 단위 테스트·프런트 빌드는 CI 에서 녹색이지만, **통합 플로우(로그인 차단 연쇄, 강제 리다이렉트, 감사 로그 기록, DB 상태 전이)는 런타임에서만 검증 가능**하다.

---

## 0. 사전 조건

### 0.1 서버 기동

| 컴포넌트 | 명령 | 기대 |
|---|---|---|
| DB | (dev: H2 인메모리, Hibernate `ddl-auto: update` 로 자동 반영) | 별도 마이그레이션 불필요 |
| 백엔드 | `./gradlew bootRun` | 기동 로그에 `기본 SUPER_ADMIN 계정 시드 완료: admin@devmatch.com` 확인 |
| 프런트 | `npm run dev` | 기본 `localhost:3000` |

**⚠ dev 환경 한정**: prod 배포 전엔 `ROADMAP.md §10` 의 SQL 을 수동 적용해야 한다 (`ALTER TABLE users ADD status, job_title, must_change_password` + 기존 ADMIN 승급).

### 0.2 테스트 계정

DataInitializer 시드 기준:

| 계정 | 이메일 | 비번 | 비고 |
|---|---|---|---|
| SUPER_ADMIN | `admin@devmatch.com` | `Admin1234!` | 스모크 주체 |
| MENTEE | 시드 멘티 중 아무 1명 | (시드 스크립트 참조) | 비활성화·삭제·비번리셋·멘토교체 대상 |
| MENTOR | APPROVED 상태 멘토 2명 이상 | — | 멘토 교체 전/후 멘토 |

MENTEE 가 기존 매칭(ACCEPTED) 을 하나 보유해야 멘토 교체 시나리오 가능. 시드에 없으면 테스트용으로 수동 매칭 1건 생성 필요 (멘티 로그인 → 멘토 신청 → 관리자/멘토 승인 플로우).

### 0.3 DB 접근

감사 로그·매칭 검증은 DB 직접 조회가 가장 확실하다. dev H2 는 H2 Console 활성화 여부 확인 (`application-dev.yml` 의 `spring.h2.console.enabled: true`). 대체로 `http://localhost:8080/h2-console` 또는 JDBC 로 접속.

---

## 1. 시나리오 1 — SUPER_ADMIN 목록/검색/필터

**목표**: `/admin/users` 기본 표시 + 서버 사이드 검색·역할/상태 필터 동작.

### 조작

1. `admin@devmatch.com / Admin1234!` 로 로그인
2. Header → "관리자 콘솔" → 사이드바 "회원 관리" 클릭 (경로: `/admin/users`)
3. 검색창에 시드 멘티 이름 일부 입력 (300ms debounce 확인)
4. 역할 탭을 "멘토" 로 전환 → 멘토만 노출되는지 확인
5. 상태 탭을 "비활성" 으로 전환 → 현재 비활성 회원만 노출 (보통 0건)
6. 페이지네이션 (size=20) 하단 컨트롤 노출 확인

### 기대

- [ ] 테이블 컬럼: 이름 / 이메일 / 역할 배지 / 상태 배지 / 가입일 / 상세
- [ ] 역할 배지 텍스트 한글: **멘티 / 멘토 / 관리자 / 슈퍼관리자**
- [ ] 상태 배지 텍스트 한글: **활성 / 비활성 / 삭제**
- [ ] 검색·필터를 동시에 적용해도 결과가 교집합으로 좁혀짐 (코드리뷰 Critical #2 에서 수정된 `searchAdminUsers` JPQL)
- [ ] DELETED 행은 회색 톤 + "탈퇴한 회원" 으로 이름 마스킹

### 실패 시 확인

- 한글 배지가 아닌 영어 (ACTIVE 등) 로 뜸 → 프런트 매핑 유틸 (`displayRole`, `displayStatus`) 미적용
- 필터 중 하나만 동작 → 백엔드 `AdminUserService.list` 의 JPQL 파라미터 바인딩 확인

---

## 2. 시나리오 2 — 비활성화 → 로그인 차단

**목표**: `DEACTIVATED` 전이 후 당사자 로그인 시 401 + 감사 로그 기록.

### 조작

1. `/admin/users` 에서 테스트 멘티 행 → "상세" 클릭
2. 카드 C "액션" 영역에서 "비활성화" 버튼 → 모달 오픈
3. 모달 확인:
   - [ ] 제목: "회원 비활성화"
   - [ ] 사유 입력 textarea (10~500자 검증)
   - [ ] 경고 **amber alert** 박스 — 문장별 줄바꿈 (한 줄에 한 문장)
   - [ ] 취소 버튼: **outline 스타일** (회색 배경 + 테두리 + 진한 텍스트)
   - [ ] 확인 버튼: destructive (빨강)
4. 사유 입력 "스모크 테스트" → 확인
5. 상세 페이지로 돌아와 상태 배지가 **비활성** 으로 바뀌었는지 확인
6. **시크릿 창** 또는 로그아웃 후 그 멘티 이메일/비밀번호로 로그인 시도

### 기대

- [ ] 로그인 시 401 + 에러 메시지 "계정이 비활성 상태입니다" 또는 동등 (`AccountInactiveException` 매핑)
- [ ] DB `admin_audit_log` 에 row 1건 추가 (§7 참조)
- [ ] 다시 SUPER_ADMIN 으로 돌아가 해당 회원 "재활성화" → 상태 "활성" 복구 → 그 멘티 로그인 정상

### 실패 시 확인

- 비활성 멘티가 로그인됨 → `AuthService.login` 의 status 가드 누락
- 상태 배지는 바뀌지 않았는데 API 200 OK → 트랜잭션 롤백/엔티티 merge 문제

---

## 3. 시나리오 3 — 비밀번호 리셋 → 임시 비번 → 강제 변경

**목표**: 임시 비번 1회 응답 + 로그인 시 `/account/change-password` 강제 리다이렉트.

### 조작

1. `/admin/users/[menteeId]` 진입 → "비밀번호 리셋" 버튼
2. 확인 모달 → 확인 → **결과 모달** 표시:
   - [ ] 임시 비번 monospace 폰트로 표시
   - [ ] 복사 버튼 동작
   - [ ] 안내: "이 비밀번호는 닫으면 다시 볼 수 없습니다"
   - [ ] 닫기 버튼만 존재 (취소 없음)
3. 임시 비번을 클립보드에 복사 후 모달 닫기
4. **시크릿 창**으로 그 멘티 이메일 + 임시 비번으로 로그인
5. 로그인 직후 **자동으로 `/account/change-password` 로 이동** 되는지 확인
6. 어떤 다른 경로 진입 시도 (예: `/lms` 를 주소창에 입력) → 계속 `/account/change-password` 로 튕기는지 확인
7. 현재 비번(임시) + 새 비번 + 확인 입력 → 제출
8. 제출 성공 → 마이페이지로 이동 → 새 비번으로 재로그인 정상

### 기대

- [ ] 임시 비번은 네트워크 응답 body 에 평문 1회만 (응답 후 리로드 시 재획득 불가)
- [ ] `must_change_password=true` 동안 어떤 인증 라우트로 가도 change-password 로 리다이렉트 (무한루프 방지: change-password 자체는 예외)
- [ ] 비번 변경 성공 후 `must_change_password=false` (DB `users.must_change_password` 확인)
- [ ] `admin_audit_log` 에 `USER_PASSWORD_RESET` row 추가 (평문 비번 **미기록**)

### 실패 시 확인

- change-password 로 안 튕김 → `AuthContext` 의 `useEffect` 가드 또는 `/api/auth/me` 응답의 `mustChangePassword` 필드 누락
- 평문 비번이 DB 에 저장됨 → `resetPassword` 서비스에서 `passwordEncoder.encode()` 누락
- 감사로그 metadata 에 평문 비번이 들어감 → 보안 이슈, 즉시 수정

---

## 4. 시나리오 4 — SUPER_ADMIN 전용 `/admin/admins` + ADMIN 신규 생성

**목표**: SUPER_ADMIN 만 접근 가능 + 신규 ADMIN 생성 시 임시 비번 응답.

### 조작

1. 사이드바 맨 아래 "관리자 계정" (SUPER_ADMIN 에게만 보임) 클릭 → `/admin/admins`
2. 목록에 `admin@devmatch.com` (슈퍼관리자) 1건 표시 확인
3. 우상단 "관리자 추가" 버튼 → 모달:
   - 이메일 `test-admin@example.com`
   - 이름 `테스트관리자`
   - 회사직책 `QA팀`
4. 제출 → 결과 모달에 임시 비번 표시
5. **시크릿 창**으로 신규 ADMIN 로그인 → 역시 `/account/change-password` 강제 이동
6. 비번 변경 후 `/admin/users` 접근 가능 확인 (ADMIN 은 `/admin/admins` 사이드바 미노출 + 직접 URL 진입 시 403)

### 권한 경계 검증

1. 시나리오 4 에서 만든 ADMIN 으로 `/admin/admins` 직접 URL 진입 시도
   - [ ] 프런트: 사이드바에 항목 없음
   - [ ] 백엔드: `GET /api/admin/admins` 로 직접 호출 시 **403** (Spring Security `hasRole('SUPER_ADMIN')`)

### 기대

- [ ] `admin_audit_log` 에 `ADMIN_CREATE` row (metadata JSON 에 `email`, `jobTitle`)
- [ ] 신규 ADMIN 계정의 `role=ADMIN`, `job_title='QA팀'`, `must_change_password=true`
- [ ] SUPER_ADMIN 의 권한 매핑: ROLE_ADMIN 도 보유 → `/admin/users` 도 진입 가능

### 실패 시 확인

- 일반 ADMIN 이 `/admin/admins` 접근 가능 → `SecurityConfig` 룰 순서 오류 (`/api/admin/admins/**` 가 `/api/admin/**` 뒤에 있으면 덜 구체적인 룰에 먼저 잡힘 — 더 구체적인 것이 먼저 와야 함)
- 신규 ADMIN 이 mustChangePassword 없이 바로 로그인 가능 → `AdminAccountService.createAdmin` 플래그 누락

---

## 5. 시나리오 5 — 멘토 교체 (MENTEE 상세에서)

**목표**: 멘티의 활성 매칭을 다른 APPROVED 멘토로 교체 + DB 상태 전이 검증.

### 사전 조건

- 대상 멘티가 기존 매칭 **ACCEPTED** 상태 1건 보유
- 교체 후보 멘토가 APPROVED 상태 (기존 멘토와 다른 사람)

### 조작

1. 테스트 멘티 상세 → "멘토 교체" 버튼 (활성 매칭이 있을 때만 활성)
2. 모달:
   - [ ] 현재 멘토 표시
   - [ ] 새 멘토 선택 (APPROVED 만 드롭다운/검색, 현재 멘토 제외)
   - [ ] 사유 입력 (10~500자)
   - [ ] 경고: "기존 매칭은 취소되며 신규 매칭이 생성됩니다. 결제 환불은 별도 처리 필요."
3. 새 멘토 선택 + 사유 "스모크 테스트" → 확인

### 기대 (DB 검증)

```sql
-- 대상 멘티의 매칭 히스토리
SELECT id, mentee_id, mentor_id, status, change_count, created_at
FROM matchings
WHERE mentee_id = <menteeUserId>
ORDER BY id DESC;
```

- [ ] 기존 row: `status='SWAPPED'`, `change_count=1` (이전에 교체 이력이 없었다면 최초 1)
- [ ] 신규 row: `status='ACCEPTED'`, `mentor_id=<newMentorUserId>`
- [ ] `admin_audit_log` 에 `USER_MENTOR_SWAP` row (metadata JSON 에 `oldMatchingId`, `oldMentorUserId`, `newMentorUserId`)

### 보존 검증

- 기존 매칭에 묶인 `MentoringSession`, `LmsAssignment`, `Curriculum`, `Payment` 는 **그대로 존재** (cascade 삭제 금지)
- 신규 매칭은 빈 상태 (세션/과제 0건)

### 실패 시 확인

- 기존 매칭 status 가 `CANCELLED` 로 바뀜 → 구현이 스펙과 다름. PR 본문 기준은 `SWAPPED`
- 신규 매칭 status 가 `PENDING` 또는 `TRIAL` → `MentorSwapService.swap()` 에서 `ACCEPTED` 직접 설정 누락
- 멘토 교체 버튼이 활성인데 에러 → 새 멘토 선택 후 APPROVED 여부 재검증 필요

---

## 6. 시나리오 6 — 감사 로그 통합 검증

**목표**: 1~5 시나리오에서 발생한 모든 관리자 액션이 `admin_audit_log` 에 누락 없이 기록.

### 조작

시나리오 1~5 를 모두 완료한 후 DB 조회:

```sql
SELECT id, admin_id, action, target_type, target_id, reason, created_at, metadata
FROM admin_audit_log
ORDER BY id DESC
LIMIT 20;
```

### 기대 — 최소 다음 row 들이 있어야 함

| action | target_type | 추가 검증 |
|---|---|---|
| `USER_DEACTIVATE` | `USER` | reason 필수 |
| `USER_REACTIVATE` | `USER` | reason 옵셔널 |
| `USER_PASSWORD_RESET` | `USER` | reason null, metadata null (평문 비번 없음) |
| `ADMIN_CREATE` | `USER` | metadata JSON 에 `email`, `jobTitle` |
| `USER_MENTOR_SWAP` | `USER` | metadata JSON 에 `oldMatchingId`, `oldMentorUserId`, `newMentorUserId` |

### 검증 포인트

- [ ] 모든 row 의 `admin_id` 가 SUPER_ADMIN 계정 id (시나리오 주체)
- [ ] `created_at` 시간 순서가 시나리오 순서와 일치
- [ ] metadata 컬럼은 TEXT 타입에 JSON 문자열 (Common 결정 §4.1)
- [ ] Phase I `MentorService.approve/reject` 감사 로그 소급도 기존 로그에서 확인 (Common 의 dual-write 검증)

### 실패 시 확인

- 특정 action 이 누락 → 서비스 레이어의 `auditLogService.record()` 호출 누락
- 트랜잭션 실패 시 감사 로그도 같이 롤백되는지 확인 (일부러 실패 케이스 만들어 검증 권장: 예) MENTEE 에게 ADMIN 대상 엔드포인트 호출 → 403 → 감사 로그 row 없음)

---

## 7. 최종 체크리스트 (머지 직전)

- [ ] 시나리오 1~6 모두 위 기대대로 동작
- [ ] 콘솔/서버 로그에 ERROR 없음 (경고는 허용)
- [ ] 브라우저 네트워크 탭에서 임시 비번이 **응답 body 외 어디에도 노출되지 않음** (헤더·URL·다음 응답 등)
- [ ] DELETE 한 회원 이름이 다른 사용자 화면 (예: 게시글, 매칭 목록) 에서 "탈퇴한 회원" 으로 마스킹
- [ ] 프런트 빌드 재확인: `npm run build` 성공

**체크리스트 모두 통과 시** PR #42 를 "Ready for review" 로 전환 → 셀프 머지 또는 리뷰어 요청.

---

## 8. 알려진 잔여 follow-up (이 PR 범위 외)

PR #42 본문 참조. 이 스모크에서 발견 가능하지만 별도 PR 로 처리:

1. `MatchingResponse` TS 타입에 `SWAPPED` 누락 (시나리오 5 후 프런트가 `SWAPPED` 를 모를 수 있음)
2. `AdminUserService.reactivate` self-guard (자기 자신 재활성화 edge case)
3. `AdminUserDetailResponse` 에 `mustChangePassword` 필드 노출 (UI 에서 리셋 상태 가시화)
4. 비활성화/삭제 모달 backdrop 클릭 시 폼 state 리셋

스모크 중 위 4건이 드러나면 "문제 없음" 으로 간주하고 follow-up PR 에서 처리.

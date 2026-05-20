# DevMatch — Phase III 이후 다음 단계 로드맵

> 2026-04-25 작성 · Phase III Feature 1 (대시보드) + Feature 2 (FAQ) 머지 완료 시점 기준

---

## 0. 현재 상태

### 완료된 영역
- **Phase 0~5**: Java 기초부터 결제·커뮤니티까지 핵심 기능 구현
- **Phase III (어드민 콘솔)**: 메뉴 6개 모두 채움
  - 대시보드 / 멘토 심사 / 회원 관리 / 결제 관리 / 게시물 관리 / FAQ 관리
  - SUPER_ADMIN 전용 메뉴 1개 (관리자 계정 관리)
- **ROADMAP §10 admin API 표**: 모든 항목 ✅
- main 브랜치 안정 상태, 머지된 PR 50+ 개

### 남은 영역
1. 자잘한 polish / cosmetic 이슈
2. **Phase 6 — 배포** (가장 큰 마일스톤)
3. 포트폴리오 마감 작업 (README, 스크린샷 등)
4. 선택적 신규 기능

---

## 1. 단기 (1~3일) — 코드베이스 정리

### 1-1. Polish 묶음 PR (반나절)

지금까지 어드민 콘솔 작업하며 발견한 미해결 흠집들을 한 번에 정리.

**알려진 이슈**:
- `Header.tsx:62` — `roleLabel` 가 SUPER_ADMIN 을 "멘티" 로 표시
- `mypage/page.tsx:117` — 동일 패턴 누락
- `RevenueTrendChart.tsx` — Y축 `-0M` 표기 cosmetic
- 코드베이스 훑으면서 추가 발견되는 작은 이슈들

**작업 흐름**:
- 새 worktree (`claude/polish-pass-1`) 생성
- 한 PR 안에 작은 fix 들 묶기
- 각 fix 별 commit 분리

### 1-2. worktree / 브랜치 정리 (10분)

- 머지된 두 브랜치 삭제: `claude/heuristic-wilbur-f1ad1a`, `claude/admin-faq`
- 사용 안 하는 worktree 정리
- **주의**: Windows 환경에서 `frontend/node_modules` 잠금 이슈가 있음
  - 참고: `error/2026-04-21-windows-worktree-remove-file-lock.md` 의 rename-then-delete 우회

---

## 2. 중기 (1~2주) — Phase 6 배포

학생 포트폴리오 핵심 단계. **라이브 URL** 이 있어야 면접·이력서에 어필 가능.

### 2-1. 인프라 brainstorming (반나절)

학생 무료 도구 우선 (사용자 메모리 반영).

| 영역 | 후보 | 비고 |
|------|------|------|
| Frontend | **Vercel** (무료) | GitHub 연동 자동 배포 |
| Backend | **Railway** / Render / Fly.io | 학생 무료 티어 비교 후 결정 |
| DB | **Railway MySQL** / PlanetScale | 무료 |
| Redis | **Upstash** | 무료 티어 |
| 도메인 | *.vercel.app + *.railway.app | 추후 무료 도메인 (.kro.kr 등) |
| HTTPS | 호스팅 자동 제공 | Let's Encrypt |

**결제 흐름**: `toss-cancel-enabled=false` 유지
(사용자 메모리: `No Real Payment Calls` — 학생 포트폴리오라 실결제/환불 API 호출 금지)

### 2-2. Dockerfile + docker-compose.prod 정리 (1일)

- 백엔드 multi-stage build 최적화 (build cache 활용)
- 프론트는 Vercel 자체 빌드 → Docker 불필요
- 환경변수 분리 (`application-prod.yml` 검토)

### 2-3. GitHub Actions CI (1일)

- PR 시 자동 테스트: `./gradlew test` + `npm run build`
- main push 시 자동 배포 트리거 (호스팅 webhook 또는 GHA 직접 배포)
- 기존 워크플로우가 있으면 보강만

### 2-4. 실제 배포 + 도메인 연결 (1~2일)

**배포 시 환경변수 체크리스트** (사용자 메모리 `deploy_checklist` 반영):
- `REFRESH_COOKIE_SECURE=true` (HTTPS 필수)
- `JWT_SECRET` 강한 값으로 변경
- `DB_PASSWORD` prod 전용
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `TOSS_SECRET_KEY`, `TOSS_CLIENT_KEY` (live 키 X, test 키 OK)
- `TOSS_CANCEL_ENABLED=false`

**DDL 수동 실행** — ROADMAP §10 항목 7~12 모두:
- 7. `admin_audit_log` 테이블
- 8. `users` 컬럼 추가 + SUPER_ADMIN 1명 승급
- 9. `payments` 컬럼 추가 + cancelled_at backfill
- 10. `posts` / `comments` 소프트 삭제 컬럼
- 12. `faq` 테이블 + 시드 9건 자동

**시드 데이터 결정**:
- prod DB 에 `DataInitializer` 시드 돌릴지 (테스트 데이터 X, 멘토 코스 정의 / FAQ 만 시드)
- 또는 별도 `seed-prod.sql` 로 관리

### 2-5. 배포 후 verification (반나절)

라이브 URL 에서:
- 핵심 사용자 시나리오 5개:
  - 회원가입 → 실력 테스트 → 매칭 추천 → 결제 (test 키) → 멘토링 화면
- 어드민 콘솔 메뉴 6개 모두 한 번씩
- 권한 매트릭스 (MENTEE / ADMIN / SUPER_ADMIN)

---

## 3. 후기 (1주) — 포트폴리오 마감

### 3-1. README 전면 정비 (반나절)

- **라이브 URL** 최상단
- 스크린샷 5~6장 (랜딩 / 매칭 / 어드민 대시보드 / FAQ 등)
- 기술 스택 + 아키텍처 다이어그램
- **핵심 의사결정**: 왜 Spring Boot? 왜 Next.js? 왜 Toss?
- 로컬 실행 가이드 (`docker-compose up` 한 줄로)
- 시연 계정 정보 (admin / mentor / mentee)

### 3-2. 데모 시나리오 / 영상 (선택, 1일)

면접·이력서용 1~2분 데모 영상.
화면 녹화 + 짧은 자막.

### 3-3. 회고 / 트러블슈팅 정리 (선택, 1일)

`error/` 폴더에 30+개 트러블슈팅 기록 보유.
가장 의미 있는 5개를 골라:
- README 의 "Troubleshooting" 섹션 또는
- 블로그 포스트 (취업 어필용)

후보:
- `2026-04-12-mentor-seed-password-mismatch.md` — 시드 데이터 비밀번호 미스매치
- `2026-04-18-signup-role-always-mentee-stale-build.md` — stale build 함정
- `2026-04-23-subagent-committed-to-main.md` — Subagent 가 main 에 잘못 커밋한 사례
- `2026-04-25-admin-dashboard-double-api-prefix.md` — apiClient baseURL 중복
- `2026-04-21-windows-worktree-remove-file-lock.md` — Windows 환경 worktree 함정

---

## 4. 장기 / 선택 — 라이브 후 옵션

배포가 안정적으로 자리잡은 후 고려할 항목들. 사용자 판단 영역.

### 4-1. 알림 시스템
- 이메일 (회원가입, 결제 환불, 멘토 승인/거절)
- 푸시 알림은 모바일 앱 단계로 미룸

### 4-2. 검색 / 필터 강화
- 멘토 검색 (직무·연차·기술 스택)
- 게시물 검색 (제목 + 본문 LIKE)

### 4-3. 별점·리뷰 보강
- 매칭 종료 후 멘티 → 멘토 리뷰
- 멘토 프로필에 평균 별점 노출

### 4-4. Toss 실결제 활성화
- **현재 학생 단계에서는 X**
- 사업자 등록 / Toss 비즈 연동 / 약관 정비 등 필요
- 자율 사용자 전환 후 단계

### 4-5. 모바일 앱 (React Native)
- 차기 포트폴리오 단위 작업
- API 는 이미 잘 분리돼 있어 재사용 가능

---

## 5. 즉시 추천 시작점

**오늘 / 내일: 1-1. Polish 묶음 PR**

근거:
- 30분~반나절이면 끝나는 작은 PR
- 어드민 콘솔 작업 중 발견한 미해결 흠집을 한 번에 정리
- 깨끗한 상태로 배포 단계 진입

이후 자연스러운 흐름:
```
Polish PR → 브랜치 정리 → Phase 6 배포 brainstorming → 인프라 결정
→ Dockerfile + CI → 실제 배포 → README 정비 → 데모
```

---

## 6. 사용자 메모 / 제약 사항 (참고)

- **무료 도구 우선** — 학생 신분, 비용 부담 최소화
- **Toss 실결제 금지** — `toss-cancel-enabled=false` 유지, 환불 흐름은 모킹된 형태
- **HTTPS 배포 시 환경변수**: `REFRESH_COOKIE_SECURE=true` 등 변경 필수
- **세션 핸드오프 위생**: 핸드오프 문서를 PR 브랜치에 commit 하지 말 것 (다른 변경과 묶이지 않게)
- **에러 로깅 규칙**: 원인까지 밝힌 에러는 `error/` 폴더에 그 턴에 즉시 기록 (`error/README.md` 인덱스 갱신 포함)
- **프론트 구현 전**: Pencil 목업 + shadcn 컴포넌트 매핑을 사용자에게 확인받는 워크플로우

---

## 7. 변경 이력

| 일자 | 작업자 | 내용 |
|------|--------|------|
| 2026-04-25 | Claude | 초안 작성 (Phase III 머지 직후) |

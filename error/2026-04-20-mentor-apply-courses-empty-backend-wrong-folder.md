# `/mentor/apply` 페이지에서 제공 가능한 코스 목록이 비어 있음

- 발생일: 2026-04-20
- 영역: frontend + backend (운영 환경 문제)
- 심각도: medium

## 증상

워크트리 (`.claude/worktrees/naughty-bassi-92ccad`) 에서 프론트 dev 서버를 띄운 뒤 `/mentor/apply` 에 접속했을 때, 폼은 정상적으로 렌더되지만 **"제공 가능한 코스"** 섹션이 `courses.length === 0` 상태에 머물러 "코스 목록을 불러오는 중입니다…" 문구만 표시됨.

콘솔에는 명시적인 에러가 뜨지 않음 (`.catch(() => setCourses([]))` 가 에러를 조용히 삼키고 있었기 때문).

`curl http://localhost:8080/api/courses` 로 확인 시 **HTTP 403** 반환.

## 원인

사용자의 백엔드가 **main 폴더 (`C:\Users\aucu2\Sub_Project`) 의 빌드**로 실행되고 있었기 때문. 프론트는 워크트리 폴더에서 실행하고 있었지만 백엔드는 main 폴더에서 실행 중이었다.

main 브랜치에는 다음 두 가지가 **아직 없다**:

1. `backend/src/main/java/com/devmatch/controller/CourseController.java` — `/api/courses` 엔드포인트 자체가 없음
2. `SecurityConfig.java` 의 `.requestMatchers("/api/courses/**").permitAll()` 설정

두 변경은 모두 워크트리 브랜치(`claude/naughty-bassi-92ccad`) 의 커밋 `5420140 feat(backend): /api/courses 엔드포인트 추가 및 permitAll 설정` 에만 존재한다.

결과적으로 main 기반 백엔드에서 `/api/courses` 는 매핑되지 않는 경로가 되고, Spring Security 의 `.anyRequest().authenticated()` 가 적용되어 **인증 없이 접근 시 403** 을 반환. 프론트의 `fetchCourseSummaries` 는 `.catch(() => setCourses([]))` 로 에러를 삼키기 때문에 사용자 입장에서는 "조용히 빈 목록" 으로 보였다.

## 해결 방법

백엔드도 워크트리 폴더의 빌드로 바꾸어 실행.

1. main 폴더에서 돌던 백엔드 종료 (포트 8080):

```cmd
for /f "tokens=5" %a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do taskkill /F /PID %a
```

2. 워크트리 폴더 백엔드 실행 (PowerShell 은 `.\` 필요):

```powershell
cd C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad\backend
.\gradlew.bat bootRun
```

3. `curl http://localhost:8080/api/courses` 로 17개 코스 JSON 이 반환되는지 확인.

재실행 후 `/mentor/apply` 새로고침 → 체크박스 목록 정상 노출.

## 재발 방지 / 메모

- **"프론트만 워크트리, 백엔드는 main"** 같이 폴더가 엇갈려 실행되는 일이 자주 발생한다. 워크트리 작업 중에는 **프론트·백엔드 모두 워크트리 폴더 기준**으로 돌려야 한다. `docs/git-worktree-guide.md` 의 dev 서버 절차에 이 항목을 추후 보완.
- 프론트의 `fetchCourseSummaries()` 호출부(`frontend/src/app/mentor/apply/page.tsx:200-204` 와 `frontend/src/app/apply/page.tsx:27`)가 `.catch(() => setCourses([]))` 로 에러를 삼켜 문제를 늦게 발견했다. 개발 편의상 빈 목록 처리 자체는 유지해도, **최소한 `console.error` 로 원인을 남기는** 쪽이 유지보수에 유리하다.
- 403 재발 시 먼저 `git log main..HEAD -- backend` 로 "지금 실행 중인 백엔드에 반영이 안 되어 있을 수 있는 변경" 이 있는지 확인.
- 같은 패턴이 `CourseController` 외에도 `MentorController` / `matching` 등 워크트리에만 있는 엔드포인트 전부에 적용된다. 404 가 아니라 403 으로 뜨는 이유는 "매핑되지 않은 경로는 `anyRequest().authenticated()` 에 걸리기 때문" 임을 기억.

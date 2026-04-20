# Git Worktree 가이드 (입문자용)

이 문서는 Claude 가 이 프로젝트에서 자주 사용하는 **Git worktree** 기능이 왜 필요하고 어떻게 쓰는지 설명합니다. Git 경험이 많지 않아도 따라올 수 있게 작성했습니다.

---

## 1. Worktree 란?

**한 저장소(repo)를 여러 브랜치에서 동시에 체크아웃** 하는 기능입니다.

### 일반적인 Git 사용법

한 폴더 안에서 브랜치를 전환하면 그때그때 파일이 덮어씌워집니다.

```
[C:\Users\aucu2\Sub_Project]  ← 한 폴더, 브랜치 전환 시 내용이 덮어씌워짐
  git checkout main
  git checkout feature/foo
  git checkout main
  ...
```

### Worktree 사용법

같은 repo 를 **여러 폴더에 동시에 체크아웃** 해 두고 각자 다른 브랜치에서 작업합니다.

```
[C:\Users\aucu2\Sub_Project]                                          ← main 브랜치 (항상)
[C:\Users\aucu2\Sub_Project\.claude\worktrees\<feature-name>]         ← feature 브랜치 (항상)
```

두 폴더가 **같은 repo 의 서로 다른 스냅샷** 입니다. 하드디스크 용량은 거의 더 들지 않습니다 (Git 내부적으로 공유).

---

## 2. 왜 이렇게 쓰나요?

Claude 가 여러분과 작업할 때 **main 을 안전하게 유지** 하기 위해서입니다.

| 역할 | 폴더 | 특징 |
|------|------|------|
| 안정 버전 | `C:\Users\aucu2\Sub_Project` (main) | 항상 실행 가능, 다른 사람이 봐도 이상 없음 |
| 작업 버전 | `...\.claude\worktrees\<feature>` | 실험적 변경 자유, 깨져도 main 무영향 |

작업이 검증되면 → PR / merge 로 main 에 합칩니다.

---

## 3. 지금 상황 (예시: Phase E 멘토 페이지 작업)

### worktree 폴더

```
C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad
```

브랜치: `claude/naughty-bassi-92ccad`

여기에는 아래 커밋들이 존재합니다:
- `c0ab4a0` fix(frontend): 로그인 경쟁 조건 제거
- `7f8af3a` feat(frontend): Phase E1-E2 + Phase F
- `0559c90` chore(frontend): Phase E0 shadcn 목업
- ...

**이 커밋들은 worktree 폴더에만 있습니다.** main 에는 아직 없습니다.

### main 폴더

```
C:\Users\aucu2\Sub_Project
```

브랜치: `main`

작업 시작 이전의 커밋까지만 반영되어 있습니다. 즉:

- ✅ main 폴더 = 이전 안정 버전 (멘토 신청 페이지 **없음**)
- ✅ worktree 폴더 = Phase E/F 완료 버전 (멘토 신청 페이지 **있음**)

---

## 4. 자주 겪는 함정: "변경한 게 안 보여요"

```
http://localhost:3000/mentor/status → 404
```

이런 404 가 뜨는 대표 원인은 **dev 서버를 main 폴더에서 띄운 경우** 입니다.
main 브랜치에는 아직 만들어지지 않은 페이지니까 당연히 404 입니다.

### 해결: worktree 폴더에서 dev 서버 띄우기

```cmd
cd C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad\frontend
npm run dev
```

터미널이 올라올 때 출력되는 디렉토리 경로가 `worktrees\<이름>\frontend` 로 끝나는지 반드시 확인하세요.

| ✗ 잘못된 경로 | ✓ 올바른 경로 |
|---|---|
| `C:\Users\aucu2\Sub_Project\frontend` | `...\.claude\worktrees\naughty-bassi-92ccad\frontend` |
| (main — 변경 없음) | (feature — 변경 있음) |

---

## 5. 동시에 두 서버를 띄우고 싶다면

두 폴더 모두에서 `npm run dev` 를 돌리려면 포트가 겹치므로 한쪽은 다른 포트로 띄우세요.

```cmd
npm run dev -- -p 3001
```

평소에는 worktree 한쪽만 띄워 쓰는 게 간단합니다.

---

## 6. 브랜치에 뭐가 들어있는지 확인하는 법

```cmd
cd C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad
git log --oneline -10
```

최근 커밋 10개가 보입니다. main 폴더에서 같은 명령을 치면 main 의 커밋만 보입니다. 둘을 비교하면 "무엇이 추가됐는지" 감이 잡힙니다.

```cmd
git log main..HEAD --oneline
```

이 브랜치에만 있는(main 에는 없는) 커밋을 나열합니다.

---

## 7. 작업이 끝났을 때 main 에 합치는 방법

### 방법 1: PR 만들어서 merge (권장)

```cmd
cd C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad
git push origin claude/naughty-bassi-92ccad
gh pr create --title "<제목>" --body "<요약>"
```

GitHub 에서 PR 리뷰 후 merge 버튼으로 합칩니다.

### 방법 2: 로컬에서 바로 merge

```cmd
cd C:\Users\aucu2\Sub_Project
git pull                                   (원격 main 동기화)
git merge claude/naughty-bassi-92ccad      (worktree 브랜치를 main 으로 병합)
git push origin main
```

둘 중 무엇이든, main 폴더에서도 이후 `git pull` 하면 같은 코드가 보이게 됩니다.

### 합친 후 worktree 정리

더 이상 필요 없는 worktree 는 삭제할 수 있습니다.

```cmd
git worktree remove C:\Users\aucu2\Sub_Project\.claude\worktrees\naughty-bassi-92ccad
git branch -d claude/naughty-bassi-92ccad
```

병합되지 않은 변경이 남아 있으면 경고가 뜹니다. 강제 삭제는 확신이 설 때만 `--force` / `-D` 사용.

---

## 8. 요약 체크리스트

- [ ] dev 서버를 띄울 때 **반드시 worktree 폴더 경로**에서 실행
- [ ] 404 가 나면 먼저 "지금 내 터미널 경로가 worktree 인가?" 체크
- [ ] 작업이 끝나면 PR or merge 로 main 반영
- [ ] 한 번에 한 폴더에서만 dev 서버 띄우기 (포트 충돌 방지)
- [ ] git worktree 목록 확인: `git worktree list`

---

## 부록: 명령어 모음

| 목적 | 명령 |
|------|------|
| worktree 목록 보기 | `git worktree list` |
| 새 worktree 생성 | `git worktree add ../<경로> <브랜치>` |
| worktree 삭제 | `git worktree remove <경로>` |
| 현재 브랜치 확인 | `git branch --show-current` |
| 최근 커밋 보기 | `git log --oneline -10` |
| main 기준 차이 보기 | `git log main..HEAD --oneline` |

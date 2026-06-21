---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "Subagent 가 feature 브랜치 대신 main 브랜치에 커밋 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# Subagent 가 feature 브랜치 대신 main 브랜치에 커밋

- 발생 일시: 2026-04-23
- 영역: infra / workflow
- 심각도: medium

## 증상

Phase II Common Task 5 (프런트 `AdminSidebar` 확장)를 subagent-driven-development 플로우로 실행하던 중, 구현 subagent 가 `commit SHA 57e3311` 로 커밋 완료를 보고했음에도 spec reviewer subagent 는 working tree 의 파일 내용이 변경 전 상태라고 보고.

직접 확인 결과:
- 현재 브랜치: `claude/festive-williamson-e52b71`, HEAD=`6c8f162`
- `git log --all` 로 확인한 `57e3311`: `main` 브랜치 위에 존재 (우리 feature 브랜치엔 없음)
- `git branch -v` 출력에 `+ main 57e3311 [ahead 1]` — main 이 다른 worktree 에서 체크아웃 중이고, 거기에 오렌지 커밋이 올라감

즉 **구현 subagent 가 의도한 feature 브랜치가 아닌 main 브랜치에 커밋을 생성**.

또한 구현 subagent 는 "Compiled successfully" 라고 보고했으나, `frontend/node_modules` 가 설치되어 있지 않아 실제 `npm run build` 는 `'next' is not recognized` 로 실패함 → **false positive 보고**.

## 원인

1. **브랜치 오염**: subagent 에게 `Work from: <worktree 경로>` 를 명시적으로 지시했지만, Bash 툴의 shell cwd 가 어떤 이유로 main 이 체크아웃된 부모 저장소(`C:\Users\aucu2\Sub_Project`) 쪽으로 해석되어 `git commit` 이 거기서 실행됨. Windows + Git worktree + subagent Bash 조합에서 cwd 해석이 불안정할 수 있음.

2. **빌드 검증 허위 보고**: subagent 가 `npm run build` 의 실제 stdout 을 검증하지 않고 "성공" 으로 판단. 환경 상태(node_modules 미설치)를 확인하지 않은 채 진행.

## 해결 방법

1. 컨트롤러(상위 Claude 세션)에서 복구:
   - `git cherry-pick 57e3311` → feature 브랜치에 신규 커밋 `f00cb2c` 생성
   - Working tree 의 [frontend/src/components/admin/AdminSidebar.tsx](../frontend/src/components/admin/AdminSidebar.tsx) 가 정상 반영됨 (4개 메뉴, `UserCheck/Users/CreditCard/FileText` 아이콘 import)

2. main 브랜치의 orphan 커밋 정리 (사용자 작업 필요):
   - main 이 체크아웃된 worktree(주 저장소)로 이동 후 `git reset --hard origin/main`
   - 또는 `git branch -f main origin/main` 로 브랜치 포인터만 되돌림 (해당 worktree 에서 먼저 다른 브랜치로 switch 필요)

3. 빌드 검증은 Task 7 (최종 회귀) 에서 `npm install && npm run build` 통합 실행하여 재확인.

## 재발 방지 / 메모

- **규약 추가**: subagent dispatch 프롬프트에 "첫 작업 전 `pwd` + `git branch --show-current` 출력하여 확인하고 보고할 것" 지시 추가 권장.
- **빌드 보고 검증**: subagent 에게 "build 명령 실행 후 stdout 의 마지막 5~10줄을 그대로 보고에 포함할 것" 지시 추가.
- Windows 환경에서 git worktree + subagent 조합은 cwd 해석에 취약점이 있을 수 있음. 중요한 git 작업은 컨트롤러가 직접 수행하거나 subagent 에게 `git -C <abspath>` 명시적 사용을 지시하는 편이 안전.
- 이번 incident 의 직접 영향은 복구 완료 (cherry-pick `f00cb2c`). 잔존 리스크는 main 브랜치 포인터가 로컬에서 1커밋 앞서 있다는 것뿐 (origin 에 push 전이라 원격엔 영향 없음).

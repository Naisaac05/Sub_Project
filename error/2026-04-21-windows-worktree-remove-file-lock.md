# Windows에서 `git worktree remove --force` 실패 — `node_modules` / `frontend` 디렉터리 파일 잠금

- 발생일: 2026-04-21
- 영역: infra (개발 환경, Windows)
- 심각도: low

## 증상

PR #35 머지 직후, 해당 브랜치에 매여 있던 worktree (`C:/Users/aucu2/Sub_Project/.claude/worktrees/naughty-bassi-92ccad`) 를 정리하려 했으나 아래 두 명령 모두 실패.

```
$ git worktree remove --force "C:/Users/.../naughty-bassi-92ccad"
error: failed to delete '...': Invalid argument

PS> Remove-Item -Recurse -Force "C:\Users\...\naughty-bassi-92ccad"
Remove-Item : Cannot remove item C:\Users\...\naughty-bassi-92ccad\frontend\node_modules\@next\swc-win32-x64-msvc\next-swc.win32-x64-msvc.node:
  The process cannot access the file because it is being used by another process.

PS> cmd /c 'rmdir /S /Q "C:\Users\...\naughty-bassi-92ccad"'
cmd : C:\Users\...\naughty-bassi-92ccad\frontend - The process cannot access the file because it is being used by another process.
```

흥미로운 점은 `git worktree list` 에선 이미 해당 worktree 가 **사라진 상태** 였다는 것. git 관리 기록은 첫 `--force` 시도 단계에서 이미 제거되었지만 디스크의 디렉터리는 남았음. 즉 **git 관점에서는 클린하나 디스크에만 고아 디렉터리가 남는 부분 실패** 상태.

## 원인

두 가지가 겹친 결과.

1. **Gradle Daemon** 이 `backend/gradle/wrapper/gradle-wrapper.jar` 를 잡고 있었음. 이 worktree 에서 `./gradlew.bat test` 를 돌린 뒤 데몬이 계속 살아 있었음. → `./gradlew.bat --stop` 으로 해결.
2. **Windows Defender 실시간 보호** 가 `frontend/node_modules` 트리 (수천 개 소형 바이너리) 를 스캔하며 디렉터리/파일 핸들을 계속 쥐고 있었음. `Get-CimInstance Win32_Process` 로 CWD / CommandLine 에 `naughty-bassi` 를 가진 프로세스는 내 PowerShell 한 개뿐이었고, Explorer/VS Code/IntelliJ 도 종료 확인했는데도 `frontend` 디렉터리 삭제가 실패 → Defender 가 유일하게 남은 설명.
   - IntelliJ 는 별개로 같은 node_modules 를 물고 있을 수 있어서 우선 종료해야 했음 (fsnotifier + idea64 프로세스 확인).

## 해결 방법

1. `./gradlew.bat --stop` 으로 Gradle Daemon 정지.
2. IntelliJ IDEA 완전 종료 (Task Manager 에서 `idea64.exe`, `fsnotifier.exe` 잔존 여부 확인).
3. **핵심 우회: "rename-then-delete" 트릭**
   ```powershell
   Rename-Item -Path $src -NewName "_to_delete_$(Get-Random)"
   Start-Sleep -Seconds 2
   Remove-Item -Recurse -Force $renamed
   ```
   디렉터리 이름 변경은 메타데이터 연산이라 Defender 스캔 중에도 성공하고, rename 직후 스캔 컨텍스트가 무효화되어 핸들이 풀리면서 삭제가 통과함.

실제 적용 결과 `Removed successfully` 로 바로 완료. `git worktree list` 와 디스크 상태 모두 정상 확인.

## 재발 방지 / 메모

- **worktree 정리 전 체크리스트:**
  1. 해당 worktree 에서 실행된 **Gradle Daemon 중지** (`./gradlew.bat --stop`).
  2. 해당 경로를 연 **IDE 완전 종료** (IntelliJ: File → Exit, VS Code: 창 닫기 말고 프로세스 종료).
  3. 해당 경로에 `cd` 되어 있는 **터미널 전부 종료**.
  4. `git worktree remove --force <path>` 시도.
  5. 실패하면 **rename-then-delete** 로 우회.
- `git worktree remove --force` 가 실패해도 git 관리 기록은 이미 제거될 수 있음 → 이후 디스크 정리가 분리된 작업이 됨을 염두에 둘 것.
- 장기적으로는 `.gitignore` 범위 밖의 개발 산출물(`node_modules`, `build/`, `.gradle/`) 을 worktree 밖 공통 캐시로 빼는 구조가 이상적이지만 현재 구조에서는 수작업 정리가 불가피.
- 리눅스/맥에선 이 문제 없음. 이 문서는 **Windows 팀원 전용 가이드** 성격.

# Pencil MCP — 새 I() 삽입 프레임의 자식이 부모 composite 뷰에서 렌더 안 됨

- 발생 일시: 2026-04-21
- 영역: infra (Pencil MCP 툴링)
- 심각도: medium (목업 시각 검증 불가 — 데이터 자체는 정상 저장)

## 증상

`docs/mockups/admin-mentor.md` 의 Frame 3(반려 모달) · Frame 4(403 페이지) 를 Pencil 에 옮기는 작업 중:

1. `mcp__pencil__batch_design` 의 `I("document", {...})` 로 top-level frame 삽입은 됨 — outer fill(`#1E293B`, `#F8FAFC`) 은 정상 렌더.
2. 해당 frame 의 자식(`body3`, `modal`, 카드들, text) 을 같은 batch 에서 `I()` 로 추가하면, **부모 frame 을 `get_screenshot` 했을 때 자식이 전혀 안 보임** — outer fill 만 덩그러니.
3. 자식 텍스트 하나를 직접 `get_screenshot(<textId>)` 하면 **텍스트 자체는 정상 렌더** (예: `get_screenshot("EvUw3")` → "403 · 접근 권한 없음" 이 그대로 찍힘).
4. `C()` 로 **기존 frame 안에** 복사해 넣은 텍스트는 부모 composite 뷰에서도 보임 (예: 테스트 때 `C("PGlQT", "3UwV8", ...)` 가 yellow 박스 안에 "COPIED TEXT" 로 잘 나옴).
5. 하지만 **새로 `I()` 로 만든 frame 안에** `C()` 한 텍스트는 **다시 composite 뷰에서 보이지 않음** — 개별 screenshot 으로만 보임.

즉 레이어 데이터는 올바르게 쓰였고, 단일 노드 렌더도 되는데, "이번 세션에서 새로 만든 frame"을 부모로 잡고 composite 할 때만 자식이 누락됨.

## 원인

Pencil MCP (또는 그 백엔드의 screenshot 렌더러) 에서 **이번 세션에 `I()` 로 만든 프레임에 대한 layout/composition 캐시가 무효화되지 않는 버그** 로 추정. 근거:

- `U()` 로 fill/gap/width 등을 바꿔도 composite 뷰는 갱신되지 않음.
- 같은 타입의 노드라도 기존 frame (`9R9oB`, `0Os1c`) 은 정상 composite, 이번 세션 신규 frame (`913T9`, `FOZ2M`) 만 깨짐.
- 자식 개별 screenshot 은 정상 → 노드 트리·속성은 문제없음.

데이터는 .pen 파일에 제대로 써진 상태 — Pencil 데스크톱에서 파일을 열면 기대한 대로 보일 가능성이 매우 높음 (MCP 쪽 렌더 파이프라인의 한계).

## 해결 방법

이번 세션에서는 **데이터까지만 제작하고 MCP composite 검증은 포기** → **데스크톱 Pencil 앱에서 열어 4개 프레임 (admin-list, admin-detail, reject-modal, 403) 모두 정상 렌더 확인 완료 (2026-04-21 사용자 스크린샷).** 데이터 정합성 OK.

남긴 결과:

- [Frame 3 reject-modal-page (`913T9`)](../) — 노드 트리 완성, 데스크톱 composite 정상.
- [Frame 4 403-page (`FOZ2M`)](../) — 노드 트리 완성, 데스크톱 composite 정상.

검증 우회:

1. **권장**: Pencil 데스크톱 앱에서 `C:/Users/aucu2/OneDrive/문서/phase-e-mentor.pen` 을 직접 열어 확인 — 이번 세션에서 실증됨.
2. MCP 내에서는 자식 개별 노드 id 를 `get_screenshot` 해서 스팟 체크 (각 text/frame 은 자체적으론 정상 렌더).

근본 수정은 Pencil MCP 서버 (screenshot 렌더러) 쪽 이슈라서 사용자 측에서 고칠 여지 없음 — 업스트림 제보 거리. 데이터 제작·검증 자체에는 영향 없음.

## 재발 방지 / 메모

- 다음에 Pencil 로 작업할 때 같은 증상이 나오면, **처음부터 데스크톱 앱 검증 루트로 계획** 할 것 (MCP screenshot 에 의존하지 말 것).
- Handoff 문서 [(이전 세션 메모)](../docs/) 에서 예고된 증상 그대로 재현됨. "I() 가 깨지고 C() 는 일부만 살아난다" 는 경험칙은 유효.
- `I()` 로 top-level frame 만 만들고 그 안을 모두 `C()` 로 채우는 실험도 해 봤지만(`C("Mo3xu", mheadRow, ...)` 등), composite 는 여전히 안 나옴 → 원인은 "새 I() frame 자체" 로 추정.
- 데이터 정합성은 OK 이므로 **이 작업을 원점에서 다시 할 필요는 없음**. 다음 Pencil 세션이 열릴 때 파일을 열고 composite 뷰가 살아 있으면 그대로 진행.

# LMS 과제 만들기 모달의 select 드롭다운이 흰 배경+흰 글자로 안 보임

- 발생일: 2026-04-12
- 영역: frontend
- 심각도: medium

## 증상

`/lms/assignments` 페이지에서 "과제 만들기" 버튼으로 모달을 열고 "타입" 드롭다운(`<select>`)을 펼치면 옵션 목록이 보이지 않는다. 같은 페이지의 "피드백 작성" 모달의 "등급" 드롭다운도 동일 증상.

## 원인

`<select>` 자체에는 `text-white bg-white/5` (반투명 흰색) 클래스가 적용되어 다크 테마와 어울리지만, 펼쳐졌을 때 표시되는 `<option>` 항목은 브라우저(Chromium)가 OS 네이티브 위젯으로 그린다. 네이티브 옵션 리스트는 부모의 `bg-white/5`가 적용되지 않고 기본 흰 배경이 되는데, 글자색은 부모로부터 흰색을 상속받아 흰 배경 위에 흰 글자가 되어 보이지 않게 된다.

## 해결 방법

`<option>`에 명시적으로 다크 배경/흰 글자를 지정. Tailwind 임의 셀렉터(`[&>option]:bg-gray-900 [&>option]:text-white`)로 부모 select 클래스에 추가했다.

- frontend/src/app/lms/assignments/page.tsx:180 (타입 드롭다운)
- frontend/src/app/lms/assignments/page.tsx:258 (등급 드롭다운)

## 재발 방지 / 메모

- 다크 테마에서 `<select>`를 쓸 때는 `text-white`만 두지 말고 `<option>`에도 다크 배경을 명시해야 한다. Tailwind에서는 `[&>option]:bg-gray-900 [&>option]:text-white` 패턴이 간단.
- 네이티브 컨트롤(`<select>`, `<input type="date">`, `<input type="color">` 등)은 OS/브라우저 그리기에 의존하므로 새 select를 추가할 때마다 다크 모드에서 펼쳐 본 상태를 한 번 확인할 것.
- 더 강한 해결책이 필요하면 headless UI 기반 커스텀 Select 컴포넌트로 대체하는 방법도 있음.

# 마이페이지 "최근 매칭 내역"에 멘토 입장 매칭이 표시되지 않음

- 발생일: 2026-04-12
- 영역: frontend (Next.js App Router)
- 심각도: medium

## 증상

멘토 계정(`java.mentor@devmatch.com`, 김자바)으로 로그인한 뒤 `/mypage`에 들어가면
"최근 매칭 내역" 섹션이 **항상 비어있음**("매칭 내역이 없습니다").

DB에는 해당 멘토가 수락한 매칭이 존재:

```sql
SELECT id, mentee_id, mentor_id, status
FROM matchings
WHERE mentor_id = 1;
-- 4 | 8  | 1 | ACCEPTED   (qwer@naver.com)
-- 1 | 10 | 1 | ACCEPTED   (ganada@devmatch.com)
```

`/matching` 페이지의 "멘토" 탭에서는 정상적으로 보임. 마이페이지에서만 누락.

## 원인

`frontend/src/app/mypage/page.tsx`의 `fetchData` 이펙트가 로그인한 사용자의 role과 무관하게
**항상 `getMyMatchingsAsMentee()`만** 호출하고 있었음.

```ts
// before — mypage/page.tsx:37-40
const [resultsRes, matchingsRes] = await Promise.all([
  getMyResults(),
  getMyMatchingsAsMentee(),   // ← 멘토여도 멘티 엔드포인트를 호출
]);
```

이 엔드포인트는 서버에서 `matchings WHERE mentee_id = :userId`로 조회하므로,
멘토 계정(`user.id = 1`)으로 호출하면 **항상 0건** 반환 → 섹션이 빈 상태로 렌더링됨.

추가로 카드의 이름 표시도 `matching.mentorName` 고정이라, 멘토 입장에서 봤을 땐
"상대방(멘티) 이름"이 아닌 자기 이름이 나오는 문제도 겸비.

## 해결 방법

`mypage/page.tsx`를 role에 따라 분기하도록 수정.

1. import에 `getMyMatchingsAsMentor` 추가 (`mypage/page.tsx:11`).
2. `fetchData` 이펙트에서 role로 분기 (`mypage/page.tsx:37-48`):

   ```ts
   const matchingsPromise =
     user?.role === 'MENTOR' ? getMyMatchingsAsMentor() : getMyMatchingsAsMentee();
   const [resultsRes, matchingsRes] = await Promise.all([
     getMyResults(),
     matchingsPromise,
   ]);
   ```

   의존성 배열에 `user?.role` 추가 — role이 뒤늦게 들어올 때 재요청하게 함.

3. 카드의 상대방 이름 표시도 role에 따라 분기 (`mypage/page.tsx:437`):

   ```tsx
   {user?.role === 'MENTOR' ? matching.menteeName : matching.mentorName}
   ```

## 재발 방지 / 메모

- "내 매칭"류 API가 입장(mentee/mentor)에 따라 엔드포인트가 갈리는 구조라,
  role 분기를 잊기 쉽다. `/matching` 페이지처럼 탭/role 분기가 한 번 들어간 화면에서는
  **화면 단에서 래핑한 훅**(예: `useMyMatchings()`)을 두고 내부에서 role 분기하는 편이 안전.
- 비슷한 패턴: LMS 대시보드, 세션 목록, 학습 노트 등도 "본인이 멘토인지 멘티인지"에 따라
  동일한 데이터지만 다른 관점으로 보여줘야 함. 마이페이지 같은 "양쪽 다 공유하는" 화면을
  추가할 때는 항상 role 케이스를 체크리스트로 확인할 것.
- QA 체크리스트 제안:
  - [ ] 멘티 계정으로 마이페이지 → 최근 매칭에 내가 신청한 매칭이 보인다
  - [ ] 멘토 계정으로 마이페이지 → 최근 매칭에 나에게 들어온/수락한 매칭이 보인다
  - [ ] 카드 제목이 "상대방" 이름이다 (자기 이름이 아니어야 함)

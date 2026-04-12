# 시드 멘토 계정 로그인 실패 (`java.mentor@devmatch.com`)

- 발생일: 2026-04-12
- 영역: backend / DB / seed
- 심각도: medium

## 증상

`DataInitializer.java:554`에 적힌 비밀번호 `mentor1234!`로 시드 멘토 계정에 로그인하면
`InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다")`가 발생.

```sql
SELECT id, email FROM users WHERE id = 1;
-- 1 | java.mentor@devmatch.com
```

계정은 존재하지만 BCrypt 검증에서 실패.

## 원인

1. `DataInitializer.run()` 진입 조건이 `if (testRepository.count() > 0) return;`로 되어 있어,
   테스트 데이터가 한 번이라도 들어있으면 **멘토 재삽입 로직도 같이 건너뜀**.
2. 과거에 이미 다른 비밀번호(`Mentor1234!`, 대문자 M)로 멘토 행이 만들어져 있었는데,
   이후 코드에서 상수를 `mentor1234!`로 바꿨지만 가드 때문에 DB의 해시는 그대로 유지됨.
3. 결과적으로 **코드의 비밀번호 문자열**과 **DB의 BCrypt 해시**가 다르게 유지되어 로그인 실패.

Python `bcrypt.checkpw`로 확인한 결과:

```
mentor1234!  → False
Mentor1234!  → True  ← 실제로 유효
```

## 해결 방법

단기 (이미 있는 환경):

- `java.mentor@devmatch.com`(user 1, 김자바)의 실제 유효한 비밀번호는 `Mentor1234!` (M 대문자).
- **나머지 시드 멘토 4명(이스프링/박리액트/최파이썬/정풀스택)은 소문자 `mentor1234!`가 실제 비밀번호.**
  user 1만 과거 코드로 먼저 해시가 만들어져 혼자 다른 값이고, 2~5번은 이후 코드(`mentor1234!`)로 생성돼 서로 다른 해시 블록을 가짐. DB에서 직접 확인한 결과:

  ```
  user 1 (java.mentor)     → Mentor1234!
  user 2 (spring.mentor)   → mentor1234!
  user 3 (react.mentor)    → mentor1234!
  user 4 (python.mentor)   → mentor1234!
  user 5 (fullstack.mentor) → mentor1234!
  ```

- 시드 멘티(LMS 연동)도 함께 정리:

  ```
  qwer@naver.com     → qwer1234!    (user 8, seed-lms.sql의 매칭 대상)
  qwer@qwer.com      → qwer1234!
  ganada@devmatch.com → Ganada1234!
  newtest002@example.com → Test1234!
  ```

  > `darkni2005@naver.com`은 시드가 아닌 실제 가입 계정이라 본인 비밀번호 사용.

근본 해결 (코드 정합성):

- `backend/src/main/java/com/devmatch/config/DataInitializer.java:554`의 `passwordEncoder.encode("mentor1234!")`를
  실제 DB 값인 `"Mentor1234!"`로 맞추거나, 시드를 다시 넣을 때 원하는 값으로 통일.
- 또는 `initMentors()`를 별도 가드(`if (mentorProfileRepository.count() == 0)`)로 분리해서
  테스트 데이터 존재 여부와 독립적으로 재실행되도록 변경.

## 재발 방지 / 메모

- 시드 생성기의 가드는 **엔티티 단위로 좁게** 잡을 것. `testRepository.count()` 하나로 여러 도메인의 시드를 한 번에 막지 말 것.
- 비밀번호 같이 "코드의 상수 = DB의 값"이 전제되는 값은, 코드 변경 시 **기존 DB를 drop하거나 비밀번호를 재해싱**해야 실제로 반영됨을 항상 기억.
- 팀 공유용 DB 덤프(`backend/data/devmatch-full-dump.sql`)는 이 "실제 유효한" 상태를 스냅샷한 것이므로, 여기 들어있는 해시를 기준으로 비밀번호 문서를 작성해야 혼선이 없음.

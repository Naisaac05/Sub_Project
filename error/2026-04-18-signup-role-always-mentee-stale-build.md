# 회원가입 시 role을 MENTOR로 선택해도 DB에는 항상 MENTEE로 저장됨

- 발생일: 2026-04-18
- 영역: backend / infra (stale build)
- 심각도: high (신규 멘토 가입 자체가 불가능)

## 증상

- 회원가입 페이지([frontend/src/app/auth/signup/page.tsx:126](../frontend/src/app/auth/signup/page.tsx))에서 "멘토" 버튼을 선택하고 가입해도 DB에 저장된 `users.role` 값은 `MENTEE`.
- 예) `python@naver.com` (id=13, 김파이썬) — 유저는 멘토로 가입했다고 하나, 실제 DB에는 `MENTEE` 저장.
- 결과적으로 Header의 역할 라벨이 "멘티"로 표시되고, 멘토 전용 메뉴(LMS `배정 목록` 등)가 보이지 않음.
- 추가 확인: DB의 **모든 수동 가입 유저(id 6~13)** 가 예외 없이 `MENTEE` — 일관된 버그.

## 원인

**IntelliJ에서 실행 중인 Spring Boot 백엔드가 최신 수정 전에 컴파일된 stale `.class` 파일을 사용하고 있었음.**

1. 원본 코드(`73c607a` ~ `9c2fad3`)에서 `AuthService.signup()`은 User 빌더에 `.role(...)`을 호출하지 않았음:
   ```java
   User user = User.builder()
           .email(request.getEmail())
           .password(passwordEncoder.encode(request.getPassword()))
           .name(request.getName())
           .build();   // ← role 세팅 없음
   ```
   [User.java:33-36](../backend/src/main/java/com/devmatch/entity/User.java)에 `@Builder.Default private Role role = Role.MENTEE;`가 있어 **항상 MENTEE로 저장**.

2. 2026-04-18 12:15:35 커밋 [`757afb6`](https://github.com/)에서 아래와 같이 수정됨:
   ```java
   .role(Role.valueOf(request.getRole()))
   ```
   동시에 [SignupRequest.java:27-28](../backend/src/main/java/com/devmatch/dto/auth/SignupRequest.java)에 `role` 필드가 추가됨.

3. 그러나 현재 실행 중인 backend JVM(PID 27444, 시작 시각 14:42:03)이 로딩한 `.class`는 수정 이전에 빌드된 것. `javap`로 검증:
   - `backend/build/classes/java/main/com/devmatch/dto/auth/SignupRequest.class` — 필드가 `email, password, name`만 존재 (role 필드 없음)
   - `backend/build/classes/java/main/com/devmatch/service/AuthService.class`의 `signup()` 바이트코드에 `Role.valueOf` 호출이나 `User$UserBuilder.role(...)` 호출이 없음
   - 즉, 최신 소스는 올바르게 고쳐졌지만 컴파일/재시작이 반영되지 않은 상태로 서버가 돌고 있었음.

4. Jackson은 JSON `{"role":"MENTOR"}`의 `role` 필드를 `@JsonIgnoreProperties(ignoreUnknown=true)` 기본 동작으로 조용히 무시 → DTO는 role 없이 생성 → User 빌더에도 전달 안 됨 → `@Builder.Default` MENTEE로 저장.

## 해결 방법

### 1) 백엔드 재빌드 + 재시작

IntelliJ에서:
- `Build > Rebuild Project` (또는 터미널에서 `./gradlew classes`)
- Run 구성 재시작 (기존 JVM 종료 후 재실행)

재시작 후 `javap -c backend/build/classes/java/main/com/devmatch/service/AuthService.class | grep valueOf` 로 `Role.valueOf` 호출이 포함되었는지 확인 가능.

### 2) 기존 잘못 저장된 유저 교정

해당 유저가 실제로 멘토로 가입하려 한 경우 role 수정 필요:

```sql
-- 김파이썬 계정
UPDATE users SET role = 'MENTOR' WHERE id = 13;
-- 그 외 멘토 의도로 가입한 계정도 동일하게 처리
```

(다른 id 6~12 유저는 원래 멘티 의도였는지 확인 필요)

## 재발 방지 / 메모

- **IntelliJ에서 Spring Boot를 실행할 때는 "Build project automatically"가 켜져 있어도 run 중인 JVM은 hot-swap이 제한적.** DTO 필드 추가/엔티티 변경 같은 구조적 수정 후엔 반드시 **Stop → Rebuild → Run** 순으로 재시작해야 함.
- `application.yml`의 `spring.jpa.hibernate.ddl-auto=update`는 컬럼 추가는 반영하지만, Java 코드 변경은 당연히 재시작 없이 반영되지 않음.
- DTO에 새 필드를 추가할 때는 `@JsonIgnoreProperties(ignoreUnknown = false)` 같은 strict 옵션을 고려하거나, 필수 필드를 `@NotBlank`로 막아서 stale 서버에 새 필드가 전송되었을 때 400으로 실패하게 하면 이런 silent drop을 조기 발견할 수 있음.
- 디버깅 시 `.class` 파일을 `javap -c`로 분해해서 실제 실행되는 바이트코드를 확인하는 것이 소스 코드만 보는 것보다 빠를 수 있음 (특히 stale build 의심 시).
- 부가 관찰: 회원가입 직후 `/auth/login?signup=success`로 리다이렉트되므로 로그인 전 `/apply`에 들어가면 "로그인이 필요한 페이지입니다" alert 후 다시 로그인 페이지로 튕김. "신청서 페이지가 안 나온다"는 증상은 실제로는 (a) 역할 라벨이 멘티로 찍혀서 본인 계정이 아닌 줄 착각, (b) 로그인 전 접근 리다이렉트 때문일 가능성이 높음 — role 버그를 고치면 자연스럽게 해결될 가능성이 큼.

# 멘토-코스 매핑 누락으로 인한 매칭 실패

## 증상
- 결제 완료 후 "Matching failed" 화면이 표시됨
- 프론트엔드 신청서에서 멘토링 코스를 선택해도 "멘토가 없다"는 경고가 모든 코스에 대해 발생
- `countAvailableMentors()` API가 항상 0을 반환

## 원인
`mentor_profile_courses` JPA 조인 테이블(ManyToMany)의 데이터가 비어있음.

**근본 원인 체인:**
1. `DataInitializer.initMentors()`는 `mentorProfileRepository.count() > 0`이면 **전체 스킵**하는 구조
2. SQL 덤프(`devmatch-data-only.sql`)에는 `mentor_profiles` 테이블 데이터만 있고, `mentor_profile_courses` 조인 테이블 데이터가 **누락**
3. 따라서 DB 복원 후 서버 재시작 시: 멘토 유저/프로필은 존재 → `count() > 0` → 멘토 초기화 스킵 → 코스 매핑은 빈 상태 유지
4. `ApplicationService.createAutoMatching()`에서 `eligibleMentors`가 비어있어 `application.markMatchingFailed()` 호출

## 해결 방법
`DataInitializer.initMentors()`를 리팩토링하여 **방어적 복구 로직** 추가:
- 기존: `count() > 0`이면 전체 스킵
- 변경: 개별 멘토별로 신규 생성 또는 코스 매핑 복구를 수행
- 기존 멘토의 `profile.getCourses()`가 비어있으면 자동으로 코스를 다시 매핑

```java
// 기존 멘토의 코스 매핑이 비어있으면 복구
userRepository.findByEmail(seed.email()).ifPresent(user -> {
    mentorProfileRepository.findByUser(user).ifPresent(profile -> {
        if (profile.getCourses() == null || profile.getCourses().isEmpty()) {
            List<MentoringCourse> foundCourses =
                    mentoringCourseRepository.findAllByCourseKeyInAndActiveTrue(seed.courseKeys());
            if (!foundCourses.isEmpty()) {
                profile.getCourses().addAll(foundCourses);
                mentorProfileRepository.save(profile);
            }
        }
    });
});
```

## 관련 파일
- `backend/src/main/java/com/devmatch/config/DataInitializer.java:555` — initMentors() 복구 로직 추가
- `backend/src/main/java/com/devmatch/service/CourseService.java:43` — countAvailableMentors()
- `backend/src/main/java/com/devmatch/service/ApplicationService.java` — createAutoMatching()
- `frontend/src/app/apply/page.tsx:59-69` — 멘토 유무 확인 useEffect

## 재발 방지 / 메모
- SQL 덤프 시 `mentor_profile_courses` 조인 테이블도 반드시 포함할 것
- DataInitializer가 "이미 존재" 체크 시 조인 테이블까지 검증하도록 방어 로직 적용 완료
- 신규 멘토 코스 추가 시에도 동일한 패턴으로 매핑 동기화 필요

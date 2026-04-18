# Course API 코드 리뷰 지적사항 4건 반영

- 발생일: 2026-04-18
- 영역: backend
- 심각도: high (Critical 1 + Important 3)

## 증상

코드 리뷰에서 A4-A6 태스크 관련 4가지 문제 지적:
1. `CourseService.findByKey`가 `IllegalArgumentException`을 던져 GlobalExceptionHandler에서 잡히지 않고 500으로 떨어짐
2. `CourseController` 메서드가 `ResponseEntity` 없이 `ApiResponse`를 직접 반환 (프로젝트 관례 위반)
3. `CourseController`에 Swagger 어노테이션(`@Tag`, `@Operation`) 누락
4. `CourseServiceTest`가 orderring/size/타입 검증 부족, `findActiveByKeys` 테스트 없음

## 원인

1. **Critical**: `findByKey`에서 `IllegalArgumentException` 사용 — `@ExceptionHandler`에 등록된 타입이 아니므로 `handleGeneral`로 빠져 500 응답 반환
2. **Important**: 신규 컨트롤러 작성 시 `ResponseEntity` 래퍼 패턴을 누락
3. **Important**: Swagger 어노테이션 미작성
4. **Important**: 테스트가 기본 동작만 검증하고 순서, 정확한 예외 타입, 신규 메서드 커버리지 없음

## 해결 방법

- `backend/src/main/java/com/devmatch/exception/CourseNotFoundException.java` — 신규 생성
- `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java:107` — `handleCourseNotFound` 핸들러 추가 (CurriculumNotFoundException 핸들러 바로 아래)
- `backend/src/main/java/com/devmatch/service/CourseService.java:29` — `IllegalArgumentException` → `CourseNotFoundException("코스를 찾을 수 없습니다: " + courseKey)`로 변경
- `backend/src/main/java/com/devmatch/service/CourseService.java:33` — `findActiveByKeys` 에서 size 불일치 throw 제거 (조용히 활성 목록만 반환)
- `backend/src/main/java/com/devmatch/controller/CourseController.java` — `ResponseEntity<ApiResponse<T>>` 반환, `@Tag` + `@Operation` 추가
- `backend/src/test/java/com/devmatch/service/CourseServiceTest.java` — 순서·사이즈·title 검증 강화, `CourseNotFoundException` 타입+메시지 검증, `findActiveByKeys` 신규 테스트 추가

## 재발 방지 / 메모

- 새 도메인 예외를 추가하면 반드시 `GlobalExceptionHandler`에 `@ExceptionHandler` 핸들러도 같이 등록해야 함. 두 파일을 묶어 PR에 포함시키는 것이 좋음
- 컨트롤러는 `MentorController`를 레퍼런스로 삼아 `ResponseEntity<ApiResponse<T>>` 패턴 유지
- `findActiveByKeys`에서 입력 수 vs 반환 수 불일치 시 throw 하던 로직을 제거했으므로, 호출부에서 빠진 키를 허용해야 하는지 여부를 설계 시 명확히 결정해야 함

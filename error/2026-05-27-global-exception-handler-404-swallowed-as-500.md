# GlobalExceptionHandler가 NoResourceFoundException을 잡아 404 대신 500 반환

- 발생 일시: 2026-05-27
- 영역: backend
- 심각도: medium

## 증상

`TestResetAiReviewControllerDisabledTest`에서 존재하지 않는 라우트에 POST 요청 시
`status().isNotFound()` (404) 를 기대했으나 실제로는 500이 반환되어 테스트 실패.

```
java.lang.AssertionError: Status expected:<404> but was:<500>
```

## 원인

Spring Boot 3.x (Spring MVC 6.x) 에서는 매핑이 없는 라우트에 요청이 들어오면
`NoResourceFoundException` 을 발생시키고 이것이 HTTP 404 로 매핑돼야 한다.
그러나 `GlobalExceptionHandler` 에 `@ExceptionHandler(Exception.class)` 핸들러가 있어
`NoResourceFoundException` 을 먼저 잡아버렸고, 그 결과 무조건 500을 반환했다.

`NoResourceFoundException` 전용 핸들러가 없었기 때문에 발생.

관련 파일: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java:270`

## 해결 방법

`GlobalExceptionHandler.java` 에 `NoResourceFoundException` 전용 핸들러 추가.

```java
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("요청한 리소스를 찾을 수 없습니다"));
}
```

`Exception.class` 핸들러보다 위에 선언해서 먼저 매핑되게 함.
관련 파일: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java:271`

## 재발 방지 / 메모

- Spring Boot 3.x 로 업그레이드하면서 `NoHandlerFoundException` → `NoResourceFoundException` 으로
  예외 클래스가 바뀐 것을 인지하지 못한 채 `Exception.class` catch-all 이 남아 있던 것이 근본 원인.
- 프로덕션에서도 실제로 잘못된 URL 에 접근하면 500이 반환되고 있었으므로 실제 버그이기도 함.
- 향후 `Exception.class` catch-all 핸들러를 추가할 때는 Spring 예외 계층이 먼저 처리돼야 하는
  표준 예외들이 있는지 확인할 것.

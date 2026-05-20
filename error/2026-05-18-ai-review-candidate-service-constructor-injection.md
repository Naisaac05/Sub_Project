# AI review candidate service constructor injection failure

- 발생 날짜: 2026-05-18
- 영역: backend
- 심각도: medium

## 증상

`.\gradlew.bat bootRun` 실행 중 Spring Boot 애플리케이션 컨텍스트 초기화가 실패했다.

핵심 로그:

```text
Error creating bean with name 'adminAiReviewCandidateController'
Error creating bean with name 'aiReviewCandidateAdminService'
Failed to instantiate [com.devmatch.service.AiReviewCandidateAdminService]: No default constructor found
```

## 원인

`AiReviewCandidateAdminService`에 생성자가 2개 있었다.

- Spring 주입용 생성자: `ObjectMapper`, `@Value String candidatePath`
- 테스트 편의용 생성자: `ObjectMapper`, `Path candidatePath`

생성자가 하나만 있으면 Spring이 자동으로 선택할 수 있지만, 생성자가 여러 개일 때는 주입 생성자를 명시해야 한다. 기존 코드에는 `@Autowired`가 없어서 Spring이 어떤 생성자를 사용해야 하는지 결정하지 못했고, 결과적으로 기본 생성자를 찾다가 실패했다.

관련 파일:

- `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java:30`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java:38`

## 해결 방법

Spring 주입 생성자에 `@Autowired`를 명시하고, 테스트 편의용 `Path` 생성자는 package-private로 낮췄다.

수정 위치:

- `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java:7`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java:30`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java:38`

검증:

```powershell
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest
.\gradlew.bat test --tests com.devmatch.controller.AdminFaqControllerTest
```

두 명령 모두 통과했다. `AdminFaqControllerTest`는 `@SpringBootTest` 기반이라 애플리케이션 컨텍스트 생성자 주입 문제를 다시 확인할 수 있다.

## 재발 방지·메모

Spring Bean 클래스에 테스트 편의용 생성자를 추가할 때는 다음 중 하나를 지킨다.

- 실제 주입 생성자에 `@Autowired`를 명시한다.
- 테스트용 생성자는 package-private 또는 static factory로 분리한다.
- 생성자가 2개 이상인 `@Service`, `@Component`, `@Controller`는 컨텍스트 테스트를 반드시 한 번 돌린다.

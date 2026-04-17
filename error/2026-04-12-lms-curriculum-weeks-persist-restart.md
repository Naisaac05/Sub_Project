# LMS 커리큘럼 주차 추가 후에도 목록에 반영되지 않음 (백엔드 핫리로드 누락)

- 발생일: 2026-04-12
- 영역: backend / 운영
- 심각도: medium

## 증상

동일 날짜 `2026-04-12-lms-curriculum-week-add-fails.md` 에서 `CurriculumService.update()` 에 `syncWeeks(...)` 를 추가하고 `./gradlew compileJava --rerun-tasks` 까지 성공했는데도, 브라우저에서 "주차 추가" 를 눌러도 여전히 새 주차가 목록에 나타나지 않았다.

## 원인

JVM 은 이미 수정 전 버전의 `CurriculumService` 클래스를 로드한 상태였다. `./gradlew compileJava` 는 `build/classes/...` 의 .class 파일만 새로 만들 뿐, 이미 실행 중인 `java -jar ...` / `bootRun` 프로세스에는 코드가 주입되지 않는다.

즉, 파일상으로는 고쳐졌지만 **런타임은 old bytecode 로 계속 돌고 있어서** 백엔드는 여전히 `request.getWeeks()` 를 무시하고 있었다.

## 해결 방법

실행 중이던 Spring Boot 프로세스를 종료하고 다시 띄우면 된다. 예:

- IDE 에서 돌리는 경우: Run 탭에서 `DevmatchApplication` 을 Stop → 다시 Run.
- 터미널에서 `./gradlew bootRun` 으로 돌리는 경우: 해당 터미널에서 Ctrl+C 후 다시 `./gradlew bootRun`.
- 배포된 jar 로 돌리는 경우: 프로세스 kill 후 `java -jar build/libs/devmatch-0.0.1-SNAPSHOT.jar`.

서버가 재시작되면서 새로 빌드된 `CurriculumService` 가 로드되고, 이후 "주차 추가" 요청이 `syncWeeks(...)` 를 거쳐 정상적으로 DB 에 `CurriculumWeek` 를 insert 한다.

## 재발 방지 / 메모

- 백엔드 Java 코드를 고친 뒤에는 반드시 **running process 재시작** 이 필요하다는 걸 잊지 말자. 파일 수정만으로는 아무 일도 일어나지 않는다.
- 개발 중 자주 건드리는 경우 Spring DevTools (`spring-boot-devtools`) 의존성을 추가하면 classpath 변경을 감지해 자동 리로드된다. 다만 JPA 매핑/DTO 변경 등은 여전히 풀 재시작이 필요할 수 있다.
- "수정했는데 동작이 그대로" 일 때 체크 순서:
  1. 파일 저장됐는지 (`git diff`)
  2. `compileJava` 나 IDE 빌드가 실제로 실행됐는지 (`build/classes/.../*.class` mtime)
  3. 실행 중인 JVM 이 그 빌드 결과를 로드했는지 (= 재시작했는지)
  4. 브라우저 캐시/네트워크 응답 (DevTools Network 탭에서 실제 응답 확인)

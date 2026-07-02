# Spring YAML 사용자 정의 설정 경고

- 발생 일시: 2026-07-02
- 영역: backend
- 심각도: low

## 증상

VS Code Spring Boot 확장이 `application.yml`의 `jwt`, `app`, `toss`, `google`, `file`을 알 수 없는 설정으로 표시하고, Hibernate의 `format_sql`, `default_batch_fetch_size` map key에 인용 부호 사용을 권고했다.

## 원인

애플리케이션은 일부 설정을 `@Value`로 읽고 있어 Spring 설정 메타데이터가 자동 생성되지 않았다. `@ConfigurationProperties` 클래스도 configuration processor가 없어 IDE가 `app` 하위 설정 구조를 발견하지 못했다. Hibernate 설정 두 개는 임의 map key라 실행에는 문제가 없지만 YAML 언어 서버의 안전한 key 표기 권고 대상이었다.

## 해결 방법

Spring Boot configuration processor를 annotation processor로 추가했다 (`backend/build.gradle:60`). `@Value` 기반 사용자 정의 설정은 추가 메타데이터에 명시했다 (`backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json:1`). Hibernate map key는 문자열로 인용했다 (`backend/src/main/resources/application.yml:19`, `backend/src/main/resources/application.yml:20`).

## 재발 방지 / 메모

- 새 사용자 정의 설정을 추가할 때는 가능하면 `@ConfigurationProperties`로 묶어 자동 메타데이터 생성 대상에 포함한다.
- `@Value`를 유지해야 하는 설정은 additional metadata에도 함께 등록한다.
- Spring Boot 3.5 OSS 지원 종료 경고는 이 YAML 메타데이터 문제와 별개이며, Boot 4 전환은 별도 호환성 작업으로 진행한다.
- 삭제된 `.worktrees` 프로젝트가 계속 Problems에 보이면 Java 언어 서버의 프로젝트 캐시가 남은 상태다. 로컬 `.vscode/settings.json`에서 `.worktrees` import/watcher를 제외하고 `Java: Clean Java Language Server Workspace`를 한 번 실행한다.

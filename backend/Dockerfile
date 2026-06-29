# ===== Build Stage =====
# jammy(Ubuntu) = 멀티아키텍처(amd64+arm64). alpine 변형은 arm64 미지원이라 사용 불가.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# 1. 의존성 파일만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Windows CRLF 문제 방지를 위한 줄바꿈 문자 변경 및 실행 권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x ./gradlew

# 2. 의존성 먼저 다운로드 (build.gradle/settings.gradle 변경 시에만 다시 다운로드됨)
# return 0은 애플리케이션 코드가 없어 빌드가 실패하더라도 캐시를 유지하기 위한 팁입니다.
RUN ./gradlew dependencies --no-daemon || true

# 3. 소스 코드 복사
COPY src src

# 4. 실제 프로젝트 빌드 (의존성이 모두 캐시되어 매우 빨라짐)
RUN ./gradlew bootJar --no-daemon

# ===== Run Stage =====
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]

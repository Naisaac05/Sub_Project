# Backend bootRun failed because stale 8080 process was still running

- 발생 일시: 2026-05-27
- 영역: backend / local environment
- 심각도: medium

## 증상

`./gradlew.bat bootRun` 실행 시 Spring Boot가 시작 막바지에 종료되며 `Web server failed to start. Port 8080 was already in use.`가 출력됐다.

## 원인

이전 확인 과정에서 띄워둔 백엔드 Java 프로세스가 `8080` 포트를 계속 점유하고 있었다. 확인 결과 `PID 30316`의 `C:\Program Files\Java\jdk-17\bin\java.exe`가 `2026-05-27 13:50:45`부터 `8080`에 listen 중이었다.

## 해결 방법

`Stop-Process -Id 30316 -Force`로 기존 백엔드 프로세스를 종료했다. 이후 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 조회에서 listener가 없음을 확인했다.

관련 파일:

- backend/src/main/resources/application.yml:43

## 재발 방지 / 메모

백엔드가 이미 떠 있는 상태에서 다시 `bootRun`을 실행하면 같은 오류가 난다. 새로 실행하려면 기존 Java 프로세스를 종료하거나, 임시로 `./gradlew.bat bootRun --args=--server.port=18080`처럼 다른 포트를 사용한다.

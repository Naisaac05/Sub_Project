# Gradle 개념 검증기가 샌드박스에서 캐시를 사용하지 못함

- 발생 일시: 2026-06-15
- 영역: backend / AI validation
- 심각도: low

## 증상
Python 개념 예시 검증기에서 Spring Gradle 테스트를 실행하면 wrapper가 Gradle 배포본을 다시 내려받으려다 `SocketException: Permission denied`로 실패했다. 같은 테스트를 실제 실행 환경에서 수행하면 통과했다.

## 원인
Codex 샌드박스의 하위 프로세스는 사용자 Gradle 캐시의 네이티브 라이브러리와 네트워크에 제한을 받는다. 이 때문에 Gradle wrapper가 이미 존재하는 캐시를 정상 실행 환경과 동일하게 사용하지 못했다.

## 해결 방법
실제 실행 환경에서 검증 레지스트리를 다시 실행해 `spring-valid`, `spring-profile`이 모두 `verified`임을 확인했다. 검증기는 실패를 성공으로 오인하지 않고 `spring_execution_failed`로 반환한다.

관련 파일: `ai/app/scripts/concept_example_verifiers.py:54`

## 재발 방지·메모
Spring 실행 검증 결과를 판정할 때 샌드박스 내부 실패와 실제 프로젝트 실행 실패를 구분한다. 배치 승인 전 최종 실행 검증은 Gradle 실행 권한이 있는 환경에서 수행한다.

# Coverage gates test config and timing

- 발생 일시: 2026-07-10
- 영역: backend / ai / frontend
- 심각도: medium

## 증상

커버리지 게이트를 추가한 뒤 검증 명령에서 다음 문제가 드러났다.

- `backend`의 `jacocoTestReport`가 테스트를 실제로 재실행하면서 로컬 MySQL(`localhost:3307`)에 붙으려다 Hibernate dialect 초기화에 실패했다.
- 임시로 테스트 리소스 `application.yml`을 추가했을 때 메인 `application.yml`이 클래스패스에서 가려져 `jwt.secret` placeholder가 사라졌다.
- AI pytest에 coverage 계측을 켜자 비동기 grounding latency 테스트가 `0.5s` 임계값을 `0.520s`로 살짝 넘었다.
- `frontend`의 `next lint`는 ESLint 설정 파일이 없어 대화형 설정 프롬프트로 멈췄고, 설정 추가 후 FAQ 삭제 문구의 JSX 따옴표 lint 오류가 드러났다.

## 원인

백엔드 테스트는 `testRuntimeOnly com.h2database:h2`만 있고 테스트 프로파일 datasource 설정이 없어 기본 메인 MySQL 설정을 사용했다. 또한 `src/test/resources/application.yml`은 메인 `application.yml`을 덮어쓰므로, datasource만 보강하려던 변경이 JWT 등 메인 기본 설정을 제거하는 부작용을 냈다.

AI 테스트는 실제 비동기 여부를 확인하려는 테스트였지만, coverage instrumentation 오버헤드를 전혀 고려하지 않은 `0.5s` 고정 임계값을 사용했다. 프론트는 Next.js lint 설정 파일이 없어 CI/CLI 검증 명령으로 쓰기 어려운 상태였다.

## 해결 방법

- JaCoCo 플러그인과 리포트 태스크를 추가하고 테스트 실행 시 `spring.profiles.active=test`를 지정했다: `backend/build.gradle:3`, `backend/build.gradle:76`, `backend/build.gradle:80`
- 테스트 전용 datasource를 `application-test.yml`로 분리해 메인 설정을 덮지 않게 했다: `backend/src/test/resources/application-test.yml:3`, `backend/src/test/resources/application-test.yml:13`
- AI 개발 의존성에 `pytest-cov`를 추가하고 pytest 기본 옵션에서 term/XML coverage 리포트를 생성하게 했다: `ai/requirements-dev.txt:3`, `ai/pytest.ini:2`
- coverage 환경에서도 비동기 grounding 테스트의 핵심 의도, 즉 2초 grounding judge를 기다리지 않는다는 점이 유지되도록 임계값을 `1.0s`로 완화했다: `ai/tests/test_adaptive_judge.py:187`, `ai/tests/test_adaptive_judge.py:188`
- Next ESLint 설정을 추가하고 FAQ 삭제 문구의 JSX 따옴표를 escape했다: `frontend/.eslintrc.json:2`, `frontend/src/app/admin/faqs/page.tsx:248`

## 재발 방지 / 메모

Spring Boot 테스트용 설정은 `src/test/resources/application.yml`보다 `application-test.yml` + `spring.profiles.active=test` 조합을 우선 사용한다. 그래야 메인 기본 설정을 유지하면서 테스트에서 필요한 값만 덮을 수 있다.

타이밍 기반 테스트는 coverage, 느린 로컬 장비, 백그라운드 부하에 영향을 받으므로 절대 시간보다 테스트 의도를 보존하는 완충 임계값을 둔다. 이번 경우에는 2초짜리 background judge를 기다리지 않는지를 보는 테스트이므로 `1.0s`도 충분히 비동기 동작을 검증한다.

# AWS(EC2 ARM) 첫 배포 중 만난 빌드/부팅 함정 7건 — 로컬 amd64에선 안 보이던 것들

- 발생 일시: 2026-06-29
- 영역: infra / docker / backend
- 심각도: medium (전부 배포 차단성, 원인 규명 후 해결)

## 증상

DevMatch를 AWS 단일 EC2(t4g.xlarge, **ARM64/Graviton**) + RDS 에 처음 배포하는 과정에서,
로컬(Windows, amd64)에선 멀쩡하던 것이 EC2에서 연달아 실패함. 빌드 단계와 백엔드 부팅 단계에서
각각 막혔고, `docker compose up` / `terraform plan` 이 비치명적으로 보이는 메시지(`|| true`로 가려진 실패 포함)로 끝나 원인 파악이 필요했음.

## 원인

크게 두 부류 — **(A) CPU 아키텍처(amd64→arm64) 차이**, **(B) prod 프로필 검증기**:

1. **frontend `public/` 없음** — Dockerfile이 `COPY --from=build /app/public` 했으나 이 프로젝트엔 `public/` 디렉터리가 없어 빌드 실패. (로컬에서도 났을 문제지만 빌드 테스트로 발견)
2. **ai reindex 무효** — `RUN python scripts/reindex_knowledge.py`가 `scripts/`를 `ai/.dockerignore`로 제외해 파일 부재. `|| true`가 실패를 가려 "성공"처럼 보였음. (Chroma 인덱스는 `ai/app/vectorstore/`에 git 추적되어 `COPY . .`로 이미 포함됨 → reindex 불필요)
3. **SG description 한글 거부** — AWS 보안그룹 description은 `^[0-9A-Za-z_ .:/()#,@\[\]+=&;{}!$*-]*$`만 허용. 한글 description으로 `terraform plan` 실패.
4. **temurin alpine arm64 미지원** — `eclipse-temurin:17-jdk-alpine`/`jre-alpine`은 arm64 manifest가 없음("no matching manifest for linux/arm64/v8"). amd64에선 pull되어 안 보였음.
5. **buildx 구버전** — Amazon Linux 2023 기본 docker의 buildx가 <0.17 이라 `docker compose build` 가 "requires buildx 0.17.0 or later"로 거부.
6. **kiwipiepy arm64 휠 없음** — `kiwipiepy==0.20.4`가 arm64용 prebuilt wheel이 없어 소스 빌드로 폴백 → 그 `setup.py`가 `import numpy`를 먼저 수행하는데 빌드격리 환경에 numpy가 없어 실패.
7. **backend prod 검증기 — candidates-path JSONL 거부** — prod에서 [`AiReviewProductionConfigValidator.rejectJsonl`](../backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java)이 `app.ai-review.candidates-path`/`auto-candidates-path`가 `.jsonl`로 끝나면 부팅 거부. 기본값이 `.jsonl` 경로라 prod 부팅 크래시.

## 해결 방법

| # | 수정 | 반영 |
|---|---|---|
| 1 | `COPY .../public` 줄 제거 | [frontend/Dockerfile](../frontend/Dockerfile) (commit dbfd057) |
| 2 | reindex 줄 제거(인덱스는 COPY로 포함) | [ai/Dockerfile](../ai/Dockerfile) (commit 1e1742f) |
| 3 | SG description 영문화 | [infra/terraform/aws/security.tf](../infra/terraform/aws/security.tf) (commit ecef3b0) |
| 4 | 베이스 이미지를 `*-jammy`(멀티아키)로 + `\|\| return 0`→`\|\| true` | [backend/Dockerfile](../backend/Dockerfile) (commit 244f3b4) |
| 5 | 클래식 빌더로 우회: `DOCKER_BUILDKIT=0 docker build -t <name> <ctx>` per service → `docker compose up -d` (--build 없이) | 런북 §6 |
| 6 | `apt-get ... cmake` 추가 + `pip install numpy` 선설치 + `pip install --no-build-isolation kiwipiepy==0.20.4` | [ai/Dockerfile](../ai/Dockerfile) (commit 2b99a21) |
| 7 | `.env.prod`에 `AI_REVIEW_CANDIDATES_PATH=` / `AI_REVIEW_AUTO_CANDIDATES_PATH=` (빈 값) | [.env.prod.example](../.env.prod.example) (commit 8237674) |

검증 결과: 컨테이너 6개 running, `https://devmatch.duckdns.org` 200, 로그인 API 401(정상), AI 리뷰 `/api/review/first-question` → exaone3.5:2.4b 생성 + RAG 검색 동작(fallback 없음).

## 재발 방지 / 메모

- **로컬 빌드 성공 ≠ 배포 성공.** 로컬은 amd64, EC2(t4g)는 arm64라 베이스 이미지 manifest·prebuilt wheel 가용성이 다르다. 멀티아키 베이스(`*-jammy`, `node:*-alpine`, `python:*-slim`은 arm64 OK)를 쓰고, 가능하면 **배포 대상 아키텍처로 빌드 테스트**할 것.
- **`|| true` / `|| return 0` 는 실패를 숨긴다.** 빌드 로그를 끝까지 확인. reindex처럼 불필요하면 아예 제거.
- **prod 검증기 2개가 게이트**: 백엔드 `AiReviewProductionConfigValidator`, AI `app/production_config.py`. 둘 다 prod에서 필수 env(타임아웃 양수, 서비스 토큰)·금지 설정(candidates-path `.jsonl`, candidate-sink `jsonl`)을 강제한다. `.env.prod.example`이 그 기준 충족하도록 유지.
- AL2023 docker는 compose/buildx가 구버전일 수 있음 — buildx 업그레이드가 막히면 클래식 빌더로 우회 가능.
- 전체 재현 절차: [docs/deploy-runbook.md](../docs/deploy-runbook.md).

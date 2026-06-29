# DevMatch AWS 배포 구현 계획

**Goal:** DevMatch(프론트·백엔드·AI·Ollama·MySQL·Redis)를 AWS 무료 크레딧으로 단일 EC2 + RDS 구성에 HTTPS로 실서비스 배포한다.

**Architecture:** 단일 t4g.xlarge EC2에 `docker compose`로 프론트·백엔드·AI·Ollama·Redis·Caddy를 띄우고, MySQL은 RDS(매니지드)로 분리한다. 네트워크/방화벽은 Terraform([infra/terraform/aws/](../infra/terraform/aws/README.md))으로 만들고, HTTPS는 DuckDNS 도메인 + Caddy 자동 TLS로 처리한다.

**Tech Stack:** AWS(EC2/RDS/VPC), Terraform, Docker Compose, Caddy, DuckDNS, Spring Boot, Next.js, FastAPI, Ollama.

**담당 범례:** 🧑 = 사용자만 가능(계정·결제·외부 콘솔) · 🤖 = Claude가 코드/파일로 수행 · 🤝 = 함께

> ✅ **진행 상태(2026-06-29):** `main`에서 `chore/deploy-aws` 브랜치 생성 → **Phase 1 완료·커밋**(`3e8a0e0`, 백엔드 컴파일 통과). 다음은 Phase 2.
> ⚠️ **구글 로그인/캘린더 제외 결정:** 로그인은 자체 이메일/비번(JWT, `/api/auth/login`), 캘린더는 스텁 모드로 동작. → Phase 0.4(OAuth 설정)·`GOOGLE_*` env 불필요. 단 HTTPS는 자체 로그인 쿠키(`Secure`) 때문에 여전히 필요.
> ⚠️ 비용: 안 쓸 땐 EC2 **stop**, **AWS Budgets 알람** 필수. 데모할 때만 켜면 $200 크레딧으로 6개월 운영 가능.

---

## Phase 0 — 사전 준비 🧑 (Claude가 못 하는 부분)

### Task 0.1: AWS 계정 + 보안 + 비용 알람
- [ ] AWS 계정 생성 (카드 등록 필요, $200 크레딧 활성화 확인)
- [ ] **IAM 사용자** 생성(루트 키 사용 금지) → `AdministratorAccess` 부여 → **액세스 키** 발급
- [ ] **AWS Budgets** 알람: 예산 $50, 80%·100% 도달 시 이메일
- [ ] **EC2 Key Pair** 생성(서울 리전), `.pem` 파일 안전 보관 — 이름 메모(예: `devmatch-key`)

### Task 0.2: 로컬 도구 설치 (Windows)
- [ ] Terraform: `winget install HashiCorp.Terraform`
- [ ] AWS CLI: `winget install Amazon.AWSCLI` → `aws configure` (Task 0.1 액세스 키 입력, region=`ap-northeast-2`)
- [ ] Docker Desktop 설치(이미지 로컬 빌드·테스트용)
- [ ] 확인: `terraform version`, `aws sts get-caller-identity`, `docker version` 모두 정상 출력

### Task 0.3: DuckDNS 도메인
- [ ] [duckdns.org](https://www.duckdns.org) 로그인 → 서브도메인 생성(예: `devmatch` → `devmatch.duckdns.org`)
- [ ] IP 칸은 Phase 3에서 EC2 IP 나오면 입력(지금은 비워둠)
- [ ] 본인 토큰 메모(자동 갱신용)

### ~~Task 0.4: Google OAuth 운영 설정~~ — **제외(구글 로그인 미사용)**
- 자체 로그인(`/api/auth/login`)을 사용하므로 구글 OAuth redirect·운영 키 불필요.
- 캘린더는 credentials 없이 **스텁 모드**로 자동 동작([GoogleCalendarConfig](../backend/src/main/java/com/devmatch/config/GoogleCalendarConfig.java)).

### Task 0.5: 운영 비밀값 생성
- [ ] `JWT_SECRET`: 256bit 이상 랜덤 (예: `openssl rand -base64 48`)
- [ ] `DB_PASSWORD`: 강한 비밀번호
- [ ] `AI_REVIEW_SERVICE_TOKEN`: 랜덤 토큰 (백엔드·AI 공유)
- [ ] Toss는 **`test_` 키 유지**(실결제 금지 정책)

---

## Phase 1 — 앱 코드 수정 🤖 (브랜치 `chore/deploy-aws`)

### Task 1.1: CORS env화
**Files:** Modify `backend/src/main/java/com/devmatch/config/CorsConfig.java`, `backend/src/main/resources/application.yml`

- [ ] **Step 1:** `CorsConfig.java`에 필드 추가 + origin을 변수로 교체
```java
import org.springframework.beans.factory.annotation.Value;
// ...
@Value("#{'${app.cors.allowed-origins:http://localhost:3000}'.split(',')}")
private java.util.List<String> allowedOrigins;
// corsConfigurationSource() 안:
config.setAllowedOrigins(allowedOrigins);   // 기존 List.of("http://localhost:3000") 대체
```
- [ ] **Step 2:** `application.yml`에 프로퍼티 추가
```yaml
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}
```
- [ ] **Step 3:** 로컬 빌드 확인 — `cd backend && ./gradlew compileJava`  → BUILD SUCCESSFUL
- [ ] **Step 4:** 검증(배포 후) — `curl -H "Origin: https://devmatch.duckdns.org" -I https://devmatch.duckdns.org/api/health` → 응답에 `Access-Control-Allow-Origin: https://devmatch.duckdns.org`

### Task 1.2: 프론트 API 프록시 env화
**Files:** Modify `frontend/next.config.js`
- [ ] **Step 1:** 전체 교체
```js
/** @type {import('next').NextConfig} */
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      { source: '/api/:path*', destination: `${BACKEND_URL}/api/:path*` },
      { source: '/uploads/:path*', destination: `${BACKEND_URL}/uploads/:path*` },
    ];
  },
};
module.exports = nextConfig;
```
- [ ] **Step 2:** 로컬 확인 — `cd frontend && npm run build` → 성공(`localhost` 기본값으로 동작)

### Task 1.3: prod 프로필 — 프록시 헤더 + Toss 가드 + AI 토큰
**Files:** Modify `backend/src/main/resources/application-prod.yml`, `application.yml`
- [ ] **Step 1:** `application-prod.yml`의 `server:` 블록에 추가
```yaml
server:
  port: 8080
  forward-headers-strategy: framework   # Caddy 뒤 HTTPS 인식 → OAuth redirect 정상
```
- [ ] **Step 2:** 같은 파일의 Toss 플래그를 env 가변으로
```yaml
app:
  payment:
    toss-cancel-enabled: ${TOSS_CANCEL_ENABLED:false}   # 기본 안전(false)
```
- [x] **Step 3:** ~~AI 서비스 토큰 바인딩 추가~~ — **이미 존재**([application.yml:96](../backend/src/main/resources/application.yml) `service-token: ${AI_REVIEW_SERVICE_TOKEN:}`). 추가 작업 없음.
- [x] **Step 4:** 빌드 확인 — `./gradlew compileJava` → BUILD SUCCESSFUL (Phase 1 검증 완료)

### Task 1.4: 업로드 경로 env화
**Files:** Modify `backend/src/main/resources/application.yml`
- [ ] **Step 1:** 추가(이미 코드의 `@Value("${file.upload-dir:uploads}")`가 이 프로퍼티를 읽음)
```yaml
file:
  upload-dir: ${FILE_UPLOAD_DIR:uploads}
```
- [ ] **Step 2:** 커밋
```bash
git add backend/ frontend/next.config.js
git commit -m "chore(deploy): externalize CORS/proxy/upload config + prod proxy headers & toss guard"
```

---

## Phase 2 — 배포 산출물 작성 🤖

### Task 2.1: `frontend/Dockerfile`
- [ ] 생성
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

### Task 2.2: `ai/Dockerfile`
- [ ] 생성
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt requirements-rag.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-rag.txt
COPY . .
RUN python scripts/reindex_knowledge.py || true   # Chroma 인덱스 baking
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

### Task 2.3: DB 스키마 초기화 — 최초 부팅 update (Flyway는 선택)
**Files:** Modify `backend/src/main/resources/application-prod.yml`
- [x] `ddl-auto`를 env 가변으로 변경: `ddl-auto: ${JPA_DDL_AUTO:validate}` (적용 완료)
- [ ] **최초 배포 1회:** `.env.prod`에 `JPA_DDL_AUTO=update` → 기동(스키마 생성 + DataInitializer 시드) → 정상 확인 후 `validate`(기본)로 되돌림.
- (선택) 마이그레이션 이력관리가 필요하면 이후 Flyway 도입: `flyway-core`/`flyway-mysql` 의존성 + `db/migration/V1__init.sql`(빈 DB에 `ddl-auto=create` 후 `mysqldump --no-data` 덤프).

### Task 2.4: `docker-compose.prod.yml`
- [ ] 생성 (요지: 프론트·백엔드·AI·ollama·redis·caddy, 볼륨 영속화. MySQL은 RDS이므로 컨테이너에서 제외)
```yaml
services:
  backend:
    build: ./backend
    env_file: [.env.prod]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      FILE_UPLOAD_DIR: /app/uploads
    volumes: [ uploads_data:/app/uploads ]
    restart: unless-stopped
  frontend:
    build: ./frontend
    environment:
      BACKEND_URL: http://backend:8080
    restart: unless-stopped
  ai:
    build: ./ai
    env_file: [.env.prod]
    environment:
      ENVIRONMENT: prod
      OLLAMA_BASE_URL: http://ollama:11434
    volumes: [ chroma_index:/app/app/vectorstore ]
    restart: unless-stopped
  ollama:
    image: ollama/ollama:latest
    volumes: [ ollama_models:/root/.ollama ]
    restart: unless-stopped
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --appendfsync everysec
    volumes: [ redis_data:/data ]
    restart: unless-stopped
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    restart: unless-stopped
volumes:
  uploads_data: {}
  chroma_index: {}
  ollama_models: {}
  redis_data: {}
  caddy_data: {}
```

### Task 2.5: `Caddyfile`
- [ ] 생성
```
devmatch.duckdns.org {
  handle /api/*     { reverse_proxy backend:8080 }
  handle /uploads/* { reverse_proxy backend:8080 }
  reverse_proxy frontend:3000
}
```

### Task 2.6: `.env.prod.example`
- [ ] 생성 (실제 `.env.prod`는 서버에서 작성, git 제외)
```bash
JWT_SECRET=
DB_HOST=<RDS endpoint host>
DB_USERNAME=devmatch_admin
DB_PASSWORD=
REDIS_HOST=redis
APP_CORS_ALLOWED_ORIGINS=https://devmatch.duckdns.org
# (구글 로그인/캘린더 제외 — GOOGLE_* 불필요. 캘린더는 자동 스텁 모드)
TOSS_CANCEL_ENABLED=false
TOSS_SECRET_KEY=test_sk_...
TOSS_CLIENT_KEY=test_gck_...
AI_REVIEW_SERVICE_TOKEN=
AI_REVIEW_CANDIDATE_SINK=http
OLLAMA_REQUEST_TIMEOUT_SECONDS=60
OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=30
AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS=10
PYTHON_AI_MAX_TOKENS=256
PYTHON_AI_NUM_CTX=1024
AI_REVIEW_MAX_USER_ANSWER_LENGTH=700
```
- [ ] 커밋: `git add . && git commit -m "chore(deploy): add prod Dockerfiles, compose, Caddyfile, flyway, env example"`

---

## Phase 3 — 인프라 생성 🤝 (Claude 작성 / 사용자 실행)

### Task 3.1: 인스턴스 타입 상향 🤖
**Files:** Modify `infra/terraform/aws/variables.tf`
- [ ] `ec2_instance_type` 기본값 `"t4g.large"` → `"t4g.xlarge"` (AI 추론 4 vCPU/16GB)

### Task 3.2: Terraform 적용 🧑
- [ ] `cd infra/terraform/aws && cp terraform.tfvars.example terraform.tfvars`
- [ ] `terraform.tfvars` 작성: `my_ip_cidr`(내 공인 IP/32), `ec2_ssh_key_name`(Task 0.1), `db_password`(Task 0.5)
- [ ] `terraform init` → `terraform plan`(생성 개수 확인) → `terraform apply`
- [ ] 출력 기록: `terraform output ec2_public_ip`, `rds_endpoint`, `backend_datasource_url`

### Task 3.3: DuckDNS 연결 🧑
- [ ] DuckDNS의 `devmatch` IP 칸에 `ec2_public_ip` 입력 → 저장
- [ ] 확인: `nslookup devmatch.duckdns.org` → EC2 IP 반환

---

## Phase 4 — 배포 실행 🧑 (Claude 안내)

### Task 4.1: 코드 올리기
- [ ] EC2 접속: `ssh -i devmatch-key.pem ec2-user@<ec2_public_ip>`
- [ ] `git clone <레포>` (또는 `git pull`로 `chore/deploy-aws` 브랜치)

### Task 4.2: `.env.prod` 작성
- [ ] `.env.prod.example`를 복사해 실제 값 입력
- [ ] `DB_HOST`는 `rds_endpoint`의 host 부분(`:3306` 제외), `google-credentials.json`도 서버에 업로드

### Task 4.3: 모델·인덱스 준비
- [ ] `docker compose -f docker-compose.prod.yml up -d ollama`
- [ ] `docker exec -it <ollama> ollama pull qwen3:4b-q4_K_M`

### Task 4.4: 전체 기동
- [ ] `docker compose -f docker-compose.prod.yml up -d --build`
- [ ] `docker compose logs -f backend`로 Flyway 마이그레이션·기동 확인

### Task 4.5: HTTPS 확인
- [ ] `curl -I https://devmatch.duckdns.org` → 200, 인증서 유효(Caddy 자동 발급)

---

## Phase 5 — E2E 검증 🤝

- [ ] **Task 5.1:** `https://devmatch.duckdns.org` 브라우저 접속(자물쇠 표시)
- [ ] **Task 5.2:** Google 로그인 → refresh 쿠키 `Secure` 확인(개발자도구)
- [ ] **Task 5.3:** 수강신청 → 결제(test 키, 실제 돈 X) → 매칭 추천
- [ ] **Task 5.4:** AI 리뷰 세션 생성 → 꼬리질문 → 답변 수신(지연 시 폴링 동작 확인)
- [ ] **Task 5.5:** AWS Budgets 알람·비용 탐색기에서 예상 청구 확인

---

## 운영 / 정리

- **데모 후 비용 절약:** `aws ec2 stop-instances --instance-ids <id>` (컴퓨팅 0원, 디스크만)
- **완전 철거:** `cd infra/terraform/aws && terraform destroy`
- **재배포 루프:** 서버에서 `git pull` → `docker compose -f docker-compose.prod.yml up -d --build` (`.env.prod`는 그대로)

---

## Self-Review (스펙 대비 점검)

- 🔴 필수 6항목 매핑: CORS(1.1)·next.config(1.2)·Dockerfile(2.1/2.2)·Flyway(2.3)·AI env+토큰(2.6/1.3)·HTTPS·OAuth(0.3/0.4/2.5/1.3) ✅
- 🟡 권장 3항목: 업로드 볼륨(2.4 `uploads_data`)·모델/인덱스 영속(2.4 볼륨)·Toss 가드(1.3) ✅
- 담당 구분 명확(🧑 계정·도메인·콘솔 / 🤖 코드·파일) ✅
- 미해결 가정: DuckDNS 사용(도메인 구매 시 Caddyfile·OAuth redirect의 도메인만 교체)

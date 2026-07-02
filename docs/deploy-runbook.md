# DevMatch AWS 배포 런북 (동료용)

> **목표:** 단일 EC2(t4g.xlarge, ARM) + RDS(MySQL) + DuckDNS + Caddy 로 DevMatch 6개 서비스를
> AWS 무료 크레딧에 **HTTPS 실서비스 배포**한다. 이 문서만 따라가면 처음부터 재현 가능.
>
> - 라이브 예시: `https://devmatch.duckdns.org` (2026-06-29 최초 배포)
> - 단계별 계획(원본): [deploy-guide.md](deploy-guide.md) · 인프라 코드: [infra/terraform/aws/](../infra/terraform/aws/README.md)
> - 배포 중 만난 버그 7건: [error/2026-06-29-aws-arm-prod-deploy-gotchas.md](../error/2026-06-29-aws-arm-prod-deploy-gotchas.md)
> - ⚠️ 이 문서의 `<...>` 는 본인 값으로 치환. **비밀값은 git에 올리지 말 것.**

---

## 0. 아키텍처 한눈에

```
                 인터넷
                   │ HTTPS(443) — DuckDNS 도메인
                   ▼
            ┌──────────────┐  EC2 t4g.xlarge (ARM, 1대)
            │   Caddy       │  ── /api,/uploads → backend
            │ (자동 TLS)    │  ── 그 외        → frontend
            └──────────────┘
            frontend  backend ── HTTP ──▶ ai(FastAPI) ── HTTP ──▶ ollama
              :3000    :8080                :8001                  :11434
                          │                                  (exaone3.5:2.4b
                          │ JDBC                              + bge-m3 임베딩)
                          ▼
                   RDS MySQL (매니지드, EC2와 분리)
```

| 구성요소 | 컨테이너 | 비고 |
|---|---|---|
| 프론트 Next.js | frontend | standalone 빌드 |
| 백엔드 Spring | backend | prod 프로필, RDS 연결 |
| AI FastAPI | ai | RAG + Ollama 클라이언트 |
| LLM 런타임 | ollama | 모델 2개 pull 필요 |
| 캐시 | redis | |
| 리버스프록시·HTTPS | caddy | Let's Encrypt 자동 |
| DB | **RDS(외부)** | compose에 없음 |

> ⚠️ **비용:** 생성 즉시 크레딧 차감. 안 쓰면 §9 의 stop/destroy 로 정지.

---

## 1. 사전 준비물 (본인 AWS 계정 / 본인 PC)

### AWS (콘솔, 리전 = 서울 `ap-northeast-2` 로 통일)
- [ ] AWS 계정 + 카드 등록 + $200 크레딧 활성화
- [ ] **IAM 사용자**(루트 금지) + `AdministratorAccess` + **액세스 키**(CLI용)
- [ ] **Budgets 비용 알람** (예: $50, 80%/100% 이메일) — 필수
- [ ] **EC2 Key Pair** 생성(`devmatch-key`, `.pem` 보관)

### 로컬 도구
- [ ] Terraform · AWS CLI · Docker · Git · ssh
- [ ] `aws configure` (액세스 키 + region `ap-northeast-2` + json)
- [ ] 확인: `aws sts get-caller-identity` 성공

### 도메인 / 비밀값
- [ ] **DuckDNS** 서브도메인 생성 (예: `devmatch.duckdns.org`, IP는 §3에서 입력)
- [ ] 비밀값 3개 생성·기록:
  ```bash
  openssl rand -base64 48   # JWT_SECRET
  openssl rand -hex 32      # AI_REVIEW_SERVICE_TOKEN
  openssl rand -hex 16      # DB_PASSWORD (RDS 규칙: 8~41자, / @ " 공백 금지 → hex 안전)
  ```

### 스코프 메모
- **구글 로그인/캘린더 제외**: 로그인은 자체 이메일/비번(JWT, `/api/auth/login`), 캘린더는 credentials 없으면 스텁 모드. → `GOOGLE_*` 불필요.
- **Toss**: 반드시 `test_` 키 사용(실결제 금지 정책). 없으면 결제 플로우만 비활성.

---

## 2. 코드 받기

```bash
git clone <repo-url> Sub_Project
cd Sub_Project
git checkout <deployment-branch>   # 배포할 커밋이 체크아웃된 브랜치
```

---

## 3. 인프라 생성 (Terraform)

```bash
cd infra/terraform/aws
cp terraform.tfvars.example terraform.tfvars
```
`terraform.tfvars` 편집:
```hcl
region           = "ap-northeast-2"
my_ip_cidr       = "<내 공인 IP>/32"   # https://ifconfig.me 확인
ec2_ssh_key_name = "devmatch-key"
db_password      = "<위에서 생성한 DB_PASSWORD>"
db_multi_az      = false
# ec2_instance_type 기본 t4g.xlarge(AI 추론용 4vCPU/16GB)
```
실행:
```bash
terraform init
terraform plan        # 13개 생성 예정 확인
terraform apply       # yes → 10~15분(RDS 느림)
terraform output      # ec2_public_ip, rds_endpoint 기록
```
**DuckDNS 연결:** duckdns.org 에서 `devmatch` 의 **IPv4(current ip)** 칸에 `ec2_public_ip` 입력, **ipv6 칸은 비움** → update.
(확인: `nslookup devmatch.duckdns.org 8.8.8.8` → EC2 IP)

---

## ⚡ 빠른 길 (권장): `deploy.ps1` 한 줄 배포

§1 사전준비 + §3 Terraform 으로 인프라가 있고, 루트에 `.env.prod` 만 만들면
이후 코드 전송·빌드·기동(§4~7)을 **한 줄**로 자동 실행할 수 있다. (스크립트엔 비밀값 없음 — `.env.prod`에서 읽어 전송)

**1) `.env.prod` 준비** (루트, gitignored — 본인 값으로 채움)
```bash
cp .env.prod.example .env.prod
# 편집기로 열어 §11 표대로 본인 값 입력 (DB_HOST, DB_PASSWORD, JWT_SECRET, AI_REVIEW_SERVICE_TOKEN 등)
```
> ⚠️ 실제 비밀값은 `.env.prod` 에만. `.gitignore` 로 커밋 안 됨.

**2) 실행** (PowerShell, 프로젝트 루트)
```powershell
.\deploy.ps1                  # 코드 재배포(빌드+기동) — 가장 흔함
.\deploy.ps1 -Infra           # terraform apply 먼저(인프라 생성/갱신) 후 배포
.\deploy.ps1 -Models          # 배포 + Ollama 모델 pull
.\deploy.ps1 -Infra -Models   # 최초 전체 배포

# 기본값은 현재 체크아웃된 커밋(HEAD)을 아카이브합니다.
# 다른 브랜치/태그/커밋을 배포할 때만 명시적으로 ref를 지정합니다.
.\deploy.ps1 -Branch <ref>

# terraform 이 PATH에 없으면:  .\deploy.ps1 -Terraform "C:\...\terraform.exe"
# SSH 키 경로가 다르면:        .\deploy.ps1 -Key "C:\path\to\key.pem"
```
스크립트(`deploy.ps1` + EC2측 `remote-build.sh`)가 하는 일: EC2 IP를 `terraform output`에서 읽고 → 코드 `git archive`→`scp` → `.env.prod` 전송 → 원격 빌드(클래식 빌더)→`docker compose up -d`. 모델 pull은 `-Models`.

> 아래 §4~7 은 이 스크립트가 자동으로 하는 일을 **수동으로 풀어쓴 것**(이해·디버깅용).

---

## 4. 코드를 EC2로 전송 (방법 A — scp)

> 방법 B(GitHub push→clone)는 [deploy-guide.md](deploy-guide.md) 부록 참고. 첫 배포는 A가 간단.

```bash
KEY=~/.ssh/devmatch-key.pem
EC2=<ec2_public_ip>
# 추적 파일만 묶어 전송 (node_modules 제외, Chroma 인덱스 포함)
git archive --format=tar.gz -o /tmp/devmatch.tar.gz HEAD
scp -i $KEY /tmp/devmatch.tar.gz ec2-user@$EC2:~/devmatch.tar.gz
ssh -i $KEY ec2-user@$EC2 "mkdir -p ~/devmatch && tar -xzf ~/devmatch.tar.gz -C ~/devmatch"
```

---

## 5. `.env.prod` 작성 (EC2 의 `~/devmatch/.env.prod`)

로컬에서 파일을 만들어 scp(따옴표 문제 회피). **값 출처는 §11 표 참고.**
```bash
# 최소 필수값 (나머지는 §11)
DB_HOST=<rds_endpoint 의 host (:3306 제외)>
DB_USERNAME=devmatch_admin
DB_PASSWORD=<terraform.tfvars 와 동일>
JWT_SECRET=<생성값>
AI_REVIEW_SERVICE_TOKEN=<생성값>
APP_CORS_ALLOWED_ORIGINS=https://devmatch.duckdns.org
JPA_DDL_AUTO=update          # ★최초 1회만 update(스키마 생성+시드), 이후 validate 권장
AI_REVIEW_CANDIDATE_SINK=http
AI_REVIEW_CANDIDATES_PATH=          # ★빈 값 필수 (.jsonl 이면 prod 부팅 거부)
AI_REVIEW_AUTO_CANDIDATES_PATH=     # ★빈 값 필수
TOSS_CANCEL_ENABLED=false
TOSS_SECRET_KEY=test_sk_xxx
TOSS_CLIENT_KEY=test_gck_xxx
# 타임아웃·토큰 한도 (prod 검증기 필수, §11)
OLLAMA_REQUEST_TIMEOUT_SECONDS=60
OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=30
AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS=10
PYTHON_AI_MAX_TOKENS=256
PYTHON_AI_NUM_CTX=1024
AI_REVIEW_MAX_USER_ANSWER_LENGTH=700
```
```bash
scp -i $KEY ./env.prod ec2-user@$EC2:~/devmatch/.env.prod
```

---

## 6. 빌드 & 기동

> ⚠️ **Amazon Linux 2023 기본 docker 의 buildx 가 구버전(<0.17)** 이면 `docker compose build` 가
> "requires buildx 0.17.0 or later" 로 실패한다. **클래식 빌더로 우회**한다(아래).
> buildx ≥0.17 이면 그냥 `docker compose -f docker-compose.prod.yml up -d --build` 로 충분.

EC2에서 (`~/devmatch`):
```bash
export DOCKER_BUILDKIT=0
docker build -t devmatch-backend ./backend
docker build -t devmatch-frontend --build-arg BACKEND_URL=http://backend:8080 ./frontend
docker build -t devmatch-ai ./ai
docker compose -f docker-compose.prod.yml up -d   # 이미지 사용 + ollama/redis/caddy pull
docker compose -f docker-compose.prod.yml ps
```

---

## 7. Ollama 모델 pull

```bash
docker compose -f docker-compose.prod.yml exec -T ollama ollama pull exaone3.5:2.4b
docker compose -f docker-compose.prod.yml exec -T ollama ollama pull bge-m3
docker compose -f docker-compose.prod.yml exec -T ollama ollama list
```
- `exaone3.5:2.4b` = 리뷰 LLM, `bge-m3` = RAG 임베딩. 둘 다 없으면 AI 리뷰 실패.
- `.env.prod`의 `PYTHON_AI_MODEL`, `PYTHON_AI_FALLBACK_MODEL`, `OLLAMA_MODEL`은 모두 `exaone3.5:2.4b`로 맞춘다.

---

## 8. 검증 (스모크 테스트)

```bash
# 프론트(HTTPS)
curl -s -o /dev/null -w "%{http_code}\n" https://devmatch.duckdns.org/        # 200
# 백엔드 프록시(로그인, 잘못된 비번 → 401 = 정상, 502면 프록시/백엔드 문제)
curl -s -X POST https://devmatch.duckdns.org/api/auth/login \
  -H "Content-Type: application/json" -d '{"email":"x","password":"y"}' -w "\n%{http_code}\n"
# AI 파이프라인(컨테이너 내부에서 직접) — exaone + RAG 동작 확인
ssh -i $KEY ec2-user@$EC2 "cd ~/devmatch && docker compose -f docker-compose.prod.yml exec -T ai python -" <<'PY'
import urllib.request, json, os
t=os.environ.get("AI_REVIEW_SERVICE_TOKEN","")
p={"question":"String과 StringBuilder 차이?","correct_answer":"StringBuilder는 가변","selected_answer":"차이 없다","user_answer":"차이 없다"}
r=urllib.request.Request("http://localhost:8001/api/review/first-question",data=json.dumps(p).encode(),
  headers={"Content-Type":"application/json","X-AI-Service-Token":t})
print(urllib.request.urlopen(r,timeout=240).read().decode()[:600])
PY
```
기대: AI 응답 JSON 에 `"model_used":"exaone3.5:2.4b"`, `"fallback_used":false`, `retrieved_concept_ids`(RAG) 포함.

자유 질문은 승인 카드 hit와 카드 없는 Ollama 생성을 따로 검사한다.

```bash
docker compose -f docker-compose.prod.yml exec -T ai python - <<'PY'
import json, os, urllib.request

token = os.environ["AI_REVIEW_SERVICE_TOKEN"]
for question in ("hashCode가 뭐지?", "Java CopyOnWriteArrayList가 뭐야?"):
    payload = json.dumps({"user_answer": question, "model": "exaone3.5:2.4b"}).encode()
    request = urllib.request.Request(
        "http://localhost:8001/api/review/free-question",
        data=payload,
        headers={"Content-Type": "application/json", "X-AI-Service-Token": token},
    )
    response = json.loads(urllib.request.urlopen(request, timeout=240).read())
    print({key: response.get(key) for key in ("route", "model_used", "fallback_used", "matched_concept_id")})
PY
```

기대:

- `hashCode가 뭐지?` → `route=v2_approved_fast_path`, `matched_concept_id=java-hashcode`, `fallback_used=false`
- 카드 없는 질문 → Ollama 호출 후 `model_used=exaone3.5:2.4b`; 정상 답변이면 `route=generation`, `fallback_used=false`

### 배포 후 AI 카드·후보 연결 확인

EC2의 `~/devmatch`에서 다음을 실행한다. 앞의 두 명령은 백엔드와 AI 컨테이너에서 카드 디렉터리를 읽을 수 있는지만 확인한다. 세 번째 명령은 AI가 localhost가 아닌 백엔드 서비스로 후보를 전송하는지 확인하고, 네 번째 명령은 최근 수집 실패를 확인한다.

```bash
docker compose -f docker-compose.prod.yml exec -T backend test -d /app/ai/knowledge/concepts_v2
docker compose -f docker-compose.prod.yml exec -T ai test -d /app/app/knowledge/concepts_v2
docker compose -f docker-compose.prod.yml exec -T ai printenv AI_REVIEW_CANDIDATE_CAPTURE_URL
docker compose -f docker-compose.prod.yml logs --tail=100 ai | grep 'candidate capture failed'
```

앞의 두 명령은 종료 코드 0, URL은 `http://backend:8080/api/internal/ai-review/candidates/capture`여야 한다. 마지막 명령은 후보 생성 후에도 출력이 없어야 한다. 디렉터리가 이미지에 포함된 경우도 있으므로, 실제로 같은 호스트 디렉터리를 bind했는지는 이어서 mount source를 확인한다.

```bash
docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/ai/knowledge/concepts_v2"}}{{println .Source}}{{end}}{{end}}' "$(docker compose -f docker-compose.prod.yml ps -q backend)"
docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/app/knowledge/concepts_v2"}}{{println .Source}}{{end}}{{end}}' "$(docker compose -f docker-compose.prod.yml ps -q ai)"
```

두 출력은 서로 같은 호스트 source 경로여야 하며, 경로 끝은 `/ai/app/knowledge/concepts_v2`여야 한다.

---

## 9. 운영 (Ops)

| 작업 | 명령 |
|---|---|
| 잠깐 멈춤(EC2만) | `aws ec2 stop-instances --instance-ids <id> --region ap-northeast-2` |
| 다시 켜기(앱 자동복구, IP 유지) | `aws ec2 start-instances --instance-ids <id> --region ap-northeast-2` |
| 전부 삭제(~$0) | `terraform -chdir=infra/terraform/aws destroy` |
| 로그 | `docker compose -f docker-compose.prod.yml logs --tail=50 <service>` |
| 재배포 | **`.\deploy.ps1`** (한 줄) — 또는 수동 §4~6 |
| **스키마 굳히기** | 첫 부팅 성공 후 `.env.prod` 의 `JPA_DDL_AUTO=validate` 로 변경 → backend 재생성 |

- EIP 덕분에 stop/start 해도 IP·DuckDNS 유지. EC2 stop 중에도 RDS는 소액 과금(며칠↑ 미사용이면 destroy).

---

## 10. 트러블슈팅 — 실제로 만난 7개 (전부 로컬 amd64에선 안 보이던 ARM/prod 차이)

| 증상 | 원인 | 해결 |
|---|---|---|
| frontend 빌드 `/app/public not found` | 프로젝트에 `public/` 없음 | Dockerfile에서 해당 COPY 제거 |
| ai 빌드 reindex `No such file` | `scripts/`가 `ai/.dockerignore` 제외 | reindex 줄 삭제(인덱스는 `app/vectorstore/` git 추적분 사용) |
| `terraform plan` SG description 거부 | SG description은 ASCII만 허용(한글 X) | description 영문화 |
| backend 빌드 `no matching manifest for arm64` | `eclipse-temurin:*-alpine`은 arm64 미지원 | 베이스를 `*-jammy`(멀티아키)로 |
| `docker compose build requires buildx 0.17+` | AL2023 기본 buildx 구버전 | 클래식 빌더(`DOCKER_BUILDKIT=0 docker build`)로 우회 |
| ai 빌드 kiwipiepy `No module named numpy` | arm64 휠 없어 소스빌드 → setup.py가 numpy 선요구 | cmake 추가 + numpy 선설치 + `--no-build-isolation` |
| backend 부팅 크래시 `must not point to JSONL in prod` | prod 검증기가 candidates-path `.jsonl` 거부 | `AI_REVIEW_CANDIDATES_PATH`/`AUTO` 를 빈 값으로 |

상세: [error/2026-06-29-aws-arm-prod-deploy-gotchas.md](../error/2026-06-29-aws-arm-prod-deploy-gotchas.md)

---

## 11. `.env.prod` 전체 레퍼런스

| 변수 | 값 출처 | 비고 |
|---|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` | prod 기본값 없음(필수) |
| `AI_REVIEW_SERVICE_TOKEN` | `openssl rand -hex 32` | 백엔드↔AI 공유(같은 값) |
| `DB_HOST` | `terraform output rds_endpoint` 의 host | `:3306` 제외 |
| `DB_USERNAME` | `devmatch_admin` | terraform 기본 |
| `DB_PASSWORD` | terraform.tfvars 와 **동일** | RDS 마스터 |
| `JPA_DDL_AUTO` | `update`(최초)→`validate` | 스키마 생성/검증 |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<도메인>` | 콤마로 다중 가능 |
| `AI_REVIEW_CANDIDATE_SINK` | `http` | jsonl 금지 |
| `AI_REVIEW_CANDIDATES_PATH` / `_AUTO_` | **빈 값** | .jsonl이면 부팅 거부 |
| `TOSS_CANCEL_ENABLED` | `false` | 실호출 차단 |
| `TOSS_SECRET_KEY`/`CLIENT_KEY` | `test_` 키 | 결제 플로우용 |
| `OLLAMA_REQUEST_TIMEOUT_SECONDS` 등 6종 | 양수 | prod 검증기 필수 |

> 토폴로지성 주소(`REDIS_HOST=redis`, `PYTHON_AI_BASE_URL=http://ai:8001`, `OLLAMA_BASE_URL=http://ollama:11434`, 프론트 `BACKEND_URL=http://backend:8080`)는 `docker-compose.prod.yml`에 이미 박혀 있어 `.env.prod`에 넣지 않는다.

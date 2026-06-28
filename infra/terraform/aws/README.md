# DevMatch — AWS 부분 매니지드 배포 (Terraform 학습용)

이 폴더는 DevMatch를 AWS에 **부분 매니지드** 구성으로 올리는 Terraform 코드다.
"보면서 익히는" 목적이라 모든 `.tf` 파일에 한국어 주석이 촘촘히 달려 있다.

> ⚠️ **비용 주의**: `apply` 하면 EC2·RDS·EIP 등 **과금되는 리소스**가 실제로 생성된다.
> 실습이 끝나면 반드시 `terraform destroy` 로 내려 과금을 막을 것. (AWS $200 크레딧으로 며칠 실습 권장)

---

## 1. 이 구성이 무엇인가 (사다리 그림 복습)

| DevMatch 조각 | 여기서 다루는 방식 | 관리 주체 |
|---|---|---|
| 백엔드 Spring + AI(FastAPI) + Ollama | **EC2** 한 대에 docker compose (자체관리) | 내가 |
| MySQL | **RDS** (매니지드) — 백업·장애조치 자동 | AWS가 |
| 네트워크/방화벽 | VPC + 퍼블릭/프라이빗 서브넷 + 보안그룹 | 코드로 |
| 프론트 Next.js | **Amplify** (옵션, `amplify.tf` 주석 처리됨) | AWS가 |

핵심 보안 설계: **RDS는 프라이빗 서브넷**에 있고, **EC2 보안그룹에서 오는 3306 트래픽만** 허용한다.
즉 DB는 인터넷에서 직접 못 닿는다 → 면접에서 말하는 "DB를 프라이빗에 격리했다"가 이것.

```
인터넷
  │ 80/443 (누구나) · 22 (내 IP만)
  ▼
[ 퍼블릭 서브넷 ]  EC2 (backend + AI + Ollama, docker)
  │ 3306 (EC2 SG 에서만)
  ▼
[ 프라이빗 서브넷 x2 ]  RDS MySQL (외부 차단)
```

## 2. 파일 안내 (읽는 순서)

| 파일 | 역할 |
|---|---|
| `versions.tf` | Terraform/AWS Provider 버전 고정 |
| `providers.tf` | 리전·공통 태그. (자격증명은 코드에 안 넣음) |
| `variables.tf` | 입력 값 정의(빈칸) |
| `network.tf` | VPC·서브넷·인터넷 게이트웨이 |
| `security.tf` | 보안그룹(방화벽) — SG가 SG를 참조하는 핵심 패턴 |
| `ec2.tf` | 앱 호스트 + 고정 IP(EIP) + 도커 설치 스크립트 |
| `rds.tf` | 매니지드 MySQL |
| `amplify.tf` | 프론트 호스팅(옵션, 주석) |
| `outputs.tf` | 완료 후 IP·DB주소 출력 |
| `terraform.tfvars.example` | 변수 값 채우는 예시 |

## 3. 사전 준비

1. **Terraform 설치** — Windows: `winget install HashiCorp.Terraform` (또는 [공식 다운로드](https://developer.hashicorp.com/terraform/install))
2. **AWS 자격증명** — `aws configure` 로 Access Key 등록 (또는 환경변수). 키는 코드에 넣지 않는다.
3. **변수 채우기**
   ```bash
   cd infra/terraform/aws
   cp terraform.tfvars.example terraform.tfvars
   # terraform.tfvars 를 열어 my_ip_cidr, db_password 등 입력
   ```

## 4. 실행 — 명령 4개

```bash
terraform init      # ① AWS Provider 플러그인 다운로드 (최초 1회)
terraform plan      # ② 무엇이 생길지 미리보기 (아무것도 안 만듦)
terraform apply     # ③ 실제 생성 (yes 입력). 끝나면 outputs 출력
# ... 실습 ...
terraform destroy   # ④ 만든 것 전부 삭제 (과금 차단)
```

- `plan` 을 먼저 보는 습관이 중요하다 — "+ 생성 / ~ 변경 / - 삭제" 를 적용 전에 확인한다.
- `apply` 후 `terraform output` 으로 EC2 IP, RDS 주소를 다시 볼 수 있다.

## 5. 만든 다음 — DevMatch와 연결

1. `terraform output ssh_command` 로 EC2 접속.
2. EC2에서 레포 clone → 앞서 만들 `docker-compose.prod.yml` 로 backend·AI·Ollama 기동.
3. 백엔드 DB 설정을 RDS로:
   - `terraform output backend_datasource_url` 값을 `SPRING_DATASOURCE_URL` 환경변수에 넣는다.
   - (compose의 mysql 컨테이너는 제거 — DB는 RDS가 담당)
4. HTTPS·도메인은 EC2 안의 Caddy가 처리(앞 대화의 배포 산출물).

## 6. 학습 체크포인트 (스스로 확인)

- `terraform plan` 출력에서 리소스가 몇 개 "+ create" 되는지 세어보기.
- `security.tf` 의 RDS 인그레스가 `cidr_blocks` 가 아니라 `security_groups` 인 이유 설명해보기.
- `db_multi_az` 를 `true`로 바꾼 뒤 `plan` 을 다시 돌려 무엇이 바뀌는지 보기.
- 실습 후 `destroy` 가 모든 리소스를 지우는지 확인 (남으면 과금).

## 7. 다음 단계 (원하면 확장)

- **상태 파일 원격 저장**: 지금은 state가 로컬에 저장된다. 팀/실무에선 S3 + DynamoDB 백엔드로 옮긴다.
- **Amplify 활성화**: `amplify.tf` 주석 해제 + GitHub 토큰.
- **모듈화**: network/compute/db 를 재사용 가능한 module 로 분리.

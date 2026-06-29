<#
  deploy.ps1 — DevMatch AWS 재배포 자동화 (Windows PowerShell)

  설계: 비밀값은 이 스크립트에 없다. 루트의 .env.prod(gitignored)에서 읽어 EC2로 전송한다.
        EC2 IP 는 'terraform output' 에서 동적으로 읽는다(하드코딩 X).

  사전 준비:
    - .env.prod (루트, gitignored)  ← 실제 비밀값. .env.prod.example 참고
    - infra/terraform/aws/terraform.tfvars  ← terraform 변수값
    - SSH 키 (.pem), aws configure 완료

  사용 예:
    .\deploy.ps1                  # 코드만 재배포(빌드+기동) — 가장 흔함
    .\deploy.ps1 -Infra           # terraform apply 먼저(인프라 생성/갱신) 후 배포
    .\deploy.ps1 -Models          # 배포 + Ollama 모델 pull
    .\deploy.ps1 -Infra -Models   # 최초 전체 배포
#>
param(
  [string]$Key       = "$env:USERPROFILE\.ssh\devmatch-key.pem",
  [string]$Branch    = "chore/deploy-aws",
  [string]$Terraform = "terraform",   # PATH에 없으면 terraform.exe 풀경로로 교체
  [switch]$Infra,
  [switch]$Models
)
$ErrorActionPreference = "Stop"
$TfDir = "infra/terraform/aws"

Write-Host "==> 사전 점검"
if (-not (Test-Path ".env.prod")) { throw ".env.prod 가 없습니다. .env.prod.example 보고 만드세요." }
if (-not (Test-Path $Key))        { throw "SSH 키가 없습니다: $Key" }

if ($Infra) {
  Write-Host "==> [Infra] terraform init + apply"
  & $Terraform -chdir=$TfDir init
  & $Terraform -chdir=$TfDir apply -auto-approve
}

Write-Host "==> EC2 공인 IP 조회 (terraform output)"
$EC2 = (& $Terraform -chdir=$TfDir output -raw ec2_public_ip).Trim()
if (-not $EC2) { throw "ec2_public_ip 를 읽지 못했습니다. 먼저 -Infra 로 apply 했나요?" }
Write-Host "    EC2 = $EC2"
$Target = "ec2-user@$EC2"
$SshOpt = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")

Write-Host "==> 코드 아카이브 + 전송 (추적 파일만, 인덱스 포함)"
$Arc = "$env:TEMP\devmatch.tar.gz"
git archive --format=tar.gz -o $Arc $Branch
scp @SshOpt $Arc "${Target}:~/devmatch.tar.gz"
ssh @SshOpt $Target "mkdir -p ~/devmatch && tar -xzf ~/devmatch.tar.gz -C ~/devmatch"

Write-Host "==> .env.prod 전송 (비밀값은 이 파일에만)"
scp @SshOpt ".env.prod" "${Target}:~/devmatch/.env.prod"

Write-Host "==> 원격 빌드 + 기동 (remote-build.sh 를 stdin 으로 전달; CRLF 제거)"
((Get-Content "remote-build.sh" -Raw) -replace "`r","") | ssh @SshOpt $Target "bash -s"

if ($Models) {
  Write-Host "==> Ollama 모델 pull (exaone3.5:2.4b, bge-m3)"
  ssh @SshOpt $Target "cd ~/devmatch && docker compose -f docker-compose.prod.yml exec -T ollama ollama pull exaone3.5:2.4b && docker compose -f docker-compose.prod.yml exec -T ollama ollama pull bge-m3"
}

Write-Host "==> 완료: https://devmatch.duckdns.org"

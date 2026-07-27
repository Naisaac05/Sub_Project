# deploy.ps1 의 `terraform -chdir=$TfDir` 가 Windows PowerShell 5.1에서 변수 미확장으로 실패

- 발생 일시: 2026-06-29
- 영역: infra (배포 스크립트 / PowerShell)
- 심각도: high

## 증상

Windows PowerShell 5.1에서 `.\deploy.ps1 -Models` 실행 시:

```
==> EC2 공인 IP 조회 (terraform output)
Error handling -chdir option: chdir $TfDir: The system cannot find the file specified.
null 값 식에서 메서드를 호출할 수 없습니다.
위치 ...deploy.ps1:39
+ $EC2 = (& $Terraform -chdir=$TfDir output -raw ec2_public_ip).Trim()
```

terraform이 `$TfDir`를 **리터럴 문자열 그대로** 받아 디렉토리를 못 찾고, 그 결과 `$EC2`가 null → `.Trim()` 호출에서 PowerShell 예외.

## 원인

Windows PowerShell **5.1**의 네이티브 인자 파싱 버그. `& terraform -chdir=$TfDir ...` 처럼 인자가 `-`로 시작하고 `=` 뒤에 변수가 오면, 5.1은 `$TfDir`를 확장하지 않고 `-chdir=$TfDir` 리터럴을 그대로 exe에 전달함. (PowerShell 7+에서는 정상 확장되어, 스크립트가 pwsh 7 기준으로 작성·테스트된 것으로 보임.)

재현 결과:
- `& terraform -chdir=$TfDir output ...` → `Error handling -chdir option: chdir $TfDir` (실패)
- `& terraform "-chdir=$TfDir" output ...` → `43.202.250.254` (정상)
- `& terraform "-chdir=$abs" output ...` → 정상

terraform(v1.15.7), `infra/terraform/aws` 디렉토리, `terraform.tfstate` 모두 정상 존재했으므로 인프라/상태 문제 아님 — 순수 인자 전달 문제.

## 해결 방법

`deploy.ps1`의 terraform 호출 3곳에서 `-chdir=$TfDir`를 따옴표로 묶어 변수 확장을 강제:

- [deploy.ps1:34-35](deploy.ps1:34) `& $Terraform "-chdir=$TfDir" init` / `apply -auto-approve`
- [deploy.ps1:39](deploy.ps1:39) `$EC2 = (& $Terraform "-chdir=$TfDir" output -raw ec2_public_ip).Trim()`

`"-chdir=$TfDir"`는 PowerShell 5.1·7 양쪽에서 동일하게 동작.

## 재발 방지 / 메모

- PowerShell에서 네이티브 exe에 `--flag=$var` / `-flag=$var` 형태로 변수를 넘길 땐 **항상 인자 전체를 따옴표로 묶을 것** (`"-flag=$var"`). 5.1/7 호환을 위해 필수.
- 현재 EC2 공인 IP = `43.202.250.254` (DuckDNS `devmatch.duckdns.org`). terraform output이 막혀도 이 IP로 수동 배포 가능.
- 이 수정은 `deploy.ps1`(git 추적 파일) 변경이므로, 서버 사본까지 반영하려면 커밋 후 배포하면 됨. 단 로컬에서 `.\deploy.ps1` 재실행에는 즉시 적용됨.
- 후속: 이 버그로 IP 조회가 막혀 있던 동안 모델 일원화 수정([2026-06-29-prod-python-ai-model-default-qwen-not-pulled.md](error/2026-06-29-prod-python-ai-model-default-qwen-not-pulled.md))이 서버에 반영되지 못했음 → 이 수정 후 재배포해야 모델 변경도 함께 적용됨.

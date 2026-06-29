# =====================================================================
# variables.tf — "입력 값"의 정의 (코드의 빈칸들)
# ---------------------------------------------------------------------
# variable 은 "이 코드가 외부에서 받는 값"이다. 실제 값은 terraform.tfvars
# 파일이나 -var 옵션으로 채운다. 이렇게 분리하면 비밀번호/IP 같은 값을
# 코드 본문에 박지 않고, 같은 코드를 dev/prod 양쪽에 재사용할 수 있다.
# =====================================================================

variable "region" {
  description = "리소스를 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2" # 서울
}

variable "project_name" {
  description = "리소스 이름·태그에 붙일 프로젝트명"
  type        = string
  default     = "devmatch"
}

# ----- 네트워크 -----
variable "vpc_cidr" {
  description = "VPC 전체 IP 대역"
  type        = string
  default     = "10.0.0.0/16"
}

# ----- EC2 (백엔드 + AI + Ollama 도커 호스트 = 자체관리 영역) -----
variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입. t4g=ARM(Graviton, 저렴). AI(Ollama) CPU 추론 위해 4vCPU/16GB 권장"
  type        = string
  default     = "t4g.xlarge" # 4 vCPU / 16GB — AI 라이브 데모 권장. 비용 절약 시 t4g.large(8GB)도 가능하나 빡빡
}

variable "ec2_ssh_key_name" {
  description = "SSH 접속에 쓸 기존 EC2 Key Pair 이름. 콘솔/CLI에서 미리 만들어 둘 것 (빈 문자열이면 SSH 키 없이 생성)"
  type        = string
  default     = ""
}

variable "my_ip_cidr" {
  description = "SSH(22번)를 허용할 내 공인 IP. 예: 1.2.3.4/32  (0.0.0.0/0 = 전체 개방은 위험)"
  type        = string
}

# ----- RDS (MySQL = 매니지드 영역, 이 구성의 핵심) -----
variable "db_instance_class" {
  description = "RDS 인스턴스 타입. db.t4g.micro = 가장 저렴한 ARM"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS 스토리지(GB)"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "초기 데이터베이스 이름 (백엔드 application-prod.yml 의 devmatch 와 일치)"
  type        = string
  default     = "devmatch"
}

variable "db_username" {
  description = "RDS 마스터 사용자명"
  type        = string
  default     = "devmatch_admin"
}

variable "db_password" {
  description = "RDS 마스터 비밀번호. tfvars 에 넣되 그 파일은 git에 올리지 말 것"
  type        = string
  sensitive   = true # 로그/출력에 ***로 가려짐
}

variable "db_multi_az" {
  description = "true면 다른 AZ에 대기 DB를 두고 장애 시 자동 전환(고가용성). 단 비용 약 2배. 학습 땐 false"
  type        = bool
  default     = false
}

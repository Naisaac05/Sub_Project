# =====================================================================
# providers.tf — AWS Provider 설정 (어느 리전에, 무슨 태그로 만들지)
# ---------------------------------------------------------------------
# AWS 자격증명(키)은 여기에 적지 않는다. Terraform은 aws CLI와 똑같이
#   - 환경변수 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY, 또는
#   - `aws configure` 로 저장한 ~/.aws/credentials
# 를 자동으로 읽는다. (키를 코드에 박으면 git에 유출되므로 절대 금지)
# =====================================================================

provider "aws" {
  region = var.region

  # default_tags: 이 Provider로 만드는 "모든" 리소스에 자동으로 붙는 태그.
  # 나중에 콘솔/비용탐색기에서 "이건 DevMatch가 Terraform으로 만든 것"이라고
  # 한눈에 구분하고, destroy 때 빠뜨리지 않게 해준다.
  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "Terraform"
    }
  }
}

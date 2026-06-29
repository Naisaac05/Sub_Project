# =====================================================================
# versions.tf — Terraform 본체와 Provider(=AWS 플러그인) 버전 고정
# ---------------------------------------------------------------------
# Terraform은 "코어"와 "Provider"가 분리돼 있다.
#   - 코어        : plan/apply/destroy 같은 엔진
#   - AWS Provider: 코어가 AWS API를 호출할 수 있게 해주는 플러그인
# 버전을 고정해두면 6개월 뒤에 받아도 똑같이 동작한다(재현성).
# =====================================================================

terraform {
  # 이 코드를 돌릴 수 있는 Terraform 최소 버전
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws" # 공식 AWS Provider
      version = "~> 5.0"        # 5.x 안에서만 업데이트 허용 (6.0 같은 큰 변경은 막음)
    }
  }
}

# =====================================================================
# amplify.tf — 프론트(Next.js)용 매니지드 호스팅 (선택 / 옵션)
# ---------------------------------------------------------------------
# Amplify Hosting 은 git 저장소를 연결하면 push 할 때마다 자동으로 빌드·
# 배포하고 CDN·HTTPS까지 붙여주는 매니지드 서비스다(사다리 그림의 초록).
#
# ★ 이 파일은 기본적으로 "주석 처리"되어 있다. 이유:
#   - GitHub 저장소 연결에 개인 액세스 토큰(PAT)이 필요해 진입장벽이 있고,
#   - 토큰 없이도 EC2+RDS 핵심은 완전히 동작하기 때문.
# Amplify까지 해보고 싶으면 아래 블록의 주석(#)을 풀고,
# variables.tf 에 github_repository / github_access_token 변수를 추가하면 된다.
# =====================================================================

# variable "github_repository" {
#   description = "Next.js 프론트가 있는 GitHub 저장소 URL"
#   type        = string
# }
#
# variable "github_access_token" {
#   description = "Amplify가 저장소를 읽을 GitHub Personal Access Token"
#   type        = string
#   sensitive   = true
# }
#
# resource "aws_amplify_app" "frontend" {
#   name         = "${var.project_name}-frontend"
#   repository   = var.github_repository
#   access_token = var.github_access_token
#
#   # 모노레포라 프론트가 frontend/ 하위에 있으면 빌드 스펙에서 그쪽으로 이동
#   build_spec = <<-YAML
#     version: 1
#     applications:
#       - appRoot: frontend
#         frontend:
#           phases:
#             preBuild:
#               commands: [npm ci]
#             build:
#               commands: [npm run build]
#           artifacts:
#             baseDirectory: .next
#             files: ['**/*']
#   YAML
#
#   # 백엔드 API 주소 같은 빌드타임 환경변수
#   environment_variables = {
#     NEXT_PUBLIC_API_BASE_URL = "https://api.example.com"
#   }
# }
#
# resource "aws_amplify_branch" "main" {
#   app_id      = aws_amplify_app.frontend.id
#   branch_name = "main"
# }

# =====================================================================
# ec2.tf — 백엔드 + AI 서버 + Ollama 를 docker compose 로 돌릴 호스트
# ---------------------------------------------------------------------
# 이게 "자체관리(self-managed)" 영역이다. 앞 사다리 그림에서 OS/런타임/
# 백업을 내가 책임지는 칸. AI(Ollama)는 매니지드로 못 넘기므로 여기 남는다.
# =====================================================================

# 최신 Amazon Linux 2023 ARM64 AMI(OS 이미지) ID를 AWS가 관리하는
# 파라미터에서 자동 조회. AMI ID를 직접 박으면 리전/시점마다 바뀌어 깨진다.
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# 인스턴스 부팅 시 1회 실행되는 스크립트: 도커 + compose 플러그인 설치.
# 실제 배포는 여기에 docker-compose.prod.yml 을 올려 `docker compose up -d` 한다.
locals {
  user_data = <<-EOF
    #!/bin/bash
    set -e
    dnf update -y
    dnf install -y docker git
    systemctl enable --now docker
    usermod -aG docker ec2-user
    # docker compose v2 플러그인 (ARM64)
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    # 여기서부터는 배포 단계: 레포를 clone 하고 docker compose up -d 하면 됨
  EOF
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  user_data              = local.user_data

  # key_name 이 빈 문자열이면 null 로 → SSH 키 없이 생성 (SSM 세션매니저로 접속 가능)
  key_name = var.ec2_ssh_key_name != "" ? var.ec2_ssh_key_name : null

  root_block_device {
    volume_size = 40    # GB. Ollama 모델 + 도커 이미지 + Chroma 인덱스 여유
    volume_type = "gp3"
  }

  tags = { Name = "${var.project_name}-app" }
}

# 고정 공인 IP: 인스턴스를 재부팅해도 IP가 안 바뀌게(도메인 연결·OAuth redirect 안정).
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
  tags     = { Name = "${var.project_name}-eip" }
}

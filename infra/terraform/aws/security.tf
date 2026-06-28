# =====================================================================
# security.tf — 보안 그룹(Security Group) = 인스턴스 단위 방화벽
# ---------------------------------------------------------------------
# 보안 그룹은 "어떤 포트로, 어디서 오는 트래픽을 허용할지"를 정하는 규칙.
# 핵심 포인트: RDS의 3306 포트는 "EC2 보안그룹에서 오는 것만" 허용한다.
# 즉 DB는 인터넷 어디서도 직접 못 들어오고, 오직 우리 EC2를 거쳐야 한다.
# 이 "SG가 SG를 참조"하는 패턴이 매니지드 아키텍처 면접 단골 포인트다.
# =====================================================================

# ----- EC2용 방화벽: 웹(80/443)은 누구나, SSH(22)는 내 IP만 -----
resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "DevMatch backend/AI host"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "SSH (내 IP만)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  # egress: 밖으로 나가는 트래픽은 전부 허용(도커 이미지 pull, ollama 모델 다운로드 등)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" # 모든 프로토콜
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-ec2-sg" }
}

# ----- RDS용 방화벽: 3306을 "EC2 보안그룹"에서 오는 것만 허용 -----
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "DevMatch MySQL - EC2 에서만 접근"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from EC2 only"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id] # ← cidr 가 아니라 SG 참조가 핵심
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-rds-sg" }
}

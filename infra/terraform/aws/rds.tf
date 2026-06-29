# =====================================================================
# rds.tf — 매니지드 MySQL (이 구성의 핵심 "매니지드" 영역)
# ---------------------------------------------------------------------
# docker-compose 의 mysql 컨테이너를 직접 운영하는 대신 RDS에 맡긴다.
# 그러면 백업·패치·장애조치를 AWS가 자동으로 해준다(사다리 그림의 초록 칸).
# =====================================================================

# DB 서브넷 그룹: "RDS를 어느 서브넷들에 배치할지" 목록.
# 프라이빗 서브넷 2개(서로 다른 AZ)를 줘야 Multi-AZ 장애조치가 가능하다.
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  subnet_ids = aws_subnet.private[*].id # private 서브넷 2개 전부
  tags       = { Name = "${var.project_name}-db-subnet" }
}

resource "aws_db_instance" "mysql" {
  identifier     = "${var.project_name}-mysql"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true # 저장 데이터 암호화 (운영 권장)

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false # 인터넷에서 직접 접근 불가 (EC2 경유만)

  multi_az = var.db_multi_az # true면 자동 장애조치(고가용성), 비용 약 2배

  # ----- 학습 환경용 설정 (운영에선 바꿔야 함) -----
  skip_final_snapshot = true # destroy 때 마지막 스냅샷 안 만듦 → 깔끔히 삭제
  # 운영에서는 아래처럼 보호를 켠다:
  # skip_final_snapshot      = false
  # final_snapshot_identifier = "${var.project_name}-final"
  # deletion_protection      = true   # 실수로 destroy 되는 것 방지
  backup_retention_period = 1 # 자동 백업 보관일(운영은 7~30 권장)

  tags = { Name = "${var.project_name}-mysql" }
}

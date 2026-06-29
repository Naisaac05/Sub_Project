# =====================================================================
# outputs.tf — apply 가 끝난 뒤 화면에 보여줄 "결과 값"
# ---------------------------------------------------------------------
# 만들어진 리소스의 IP/주소 등을 출력해, 다음 작업(SSH 접속, 백엔드 DB
# 연결 설정)에 바로 쓸 수 있게 한다. `terraform output` 으로 다시 볼 수 있다.
# =====================================================================

output "ec2_public_ip" {
  description = "EC2 고정 공인 IP (SSH·도메인 연결용)"
  value       = aws_eip.app.public_ip
}

output "ssh_command" {
  description = "EC2 접속 명령 예시"
  value       = "ssh ec2-user@${aws_eip.app.public_ip}"
}

output "rds_endpoint" {
  description = "RDS 접속 주소:포트 (백엔드 DB_HOST 에 host 부분을 넣는다)"
  value       = aws_db_instance.mysql.endpoint
}

output "backend_datasource_url" {
  description = "application-prod.yml 의 SPRING_DATASOURCE_URL 로 바로 쓸 수 있는 형태"
  # RDS endpoint 는 'host:3306' 형태라 :3306 을 떼고 jdbc URL 을 조립
  value = "jdbc:mysql://${replace(aws_db_instance.mysql.endpoint, ":3306", "")}:3306/${var.db_name}?useSSL=true&serverTimezone=Asia/Seoul"
}

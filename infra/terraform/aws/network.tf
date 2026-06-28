# =====================================================================
# network.tf — VPC(가상 네트워크) + 서브넷 + 인터넷 연결
# ---------------------------------------------------------------------
# 이 파일이 "DB는 외부에서 직접 못 닿는 사설망에, 서버만 공개망에" 라는
# 보안 설계의 뼈대다. 면접에서 말하는 "DB를 프라이빗 서브넷에 뒀다"가 여기.
#
#   VPC 10.0.0.0/16
#   ├─ public  서브넷 10.0.1.0/24   → EC2 (인터넷에서 접근 가능)
#   ├─ private 서브넷 10.0.11.0/24  ┐
#   └─ private 서브넷 10.0.12.0/24  ┘→ RDS (외부 차단, EC2만 접근)
#
# RDS를 private 2개에 두는 이유: RDS는 "서로 다른 AZ의 서브넷 2개"를
# 요구한다(Multi-AZ 장애조치 대비). 그래서 private 서브넷이 2개다.
# =====================================================================

# 현재 리전에서 사용 가능한 가용영역(AZ) 목록을 조회 (예: ap-northeast-2a, 2c)
data "aws_availability_zones" "available" {
  state = "available"
}

# ----- VPC: 우리만의 격리된 가상 네트워크 -----
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = { Name = "${var.project_name}-vpc" }
}

# ----- 인터넷 게이트웨이: VPC를 인터넷과 연결하는 문 -----
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project_name}-igw" }
}

# ----- 퍼블릭 서브넷: EC2가 들어갈, 인터넷에 노출되는 칸 -----
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true # 이 서브넷에 뜨는 인스턴스는 공인 IP를 받음
  tags                    = { Name = "${var.project_name}-public" }
}

# ----- 프라이빗 서브넷 2개: RDS가 들어갈, 인터넷에서 못 닿는 칸 -----
# count = 2 → 같은 블록으로 리소스 2개를 만든다(서로 다른 AZ에 하나씩).
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1${count.index + 1}.0/24" # 10.0.11.0/24, 10.0.12.0/24
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags              = { Name = "${var.project_name}-private-${count.index}" }
}

# ----- 라우팅 테이블: "인터넷으로 나가는 길은 IGW로" 라는 이정표 -----
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0" # 모든 외부 목적지
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = { Name = "${var.project_name}-public-rt" }
}

# 퍼블릭 서브넷에 위 이정표를 연결 (프라이빗 서브넷은 연결 안 함 = 외부로 못 나감)
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# DevMatch Seed Data

팀원과 공유하기 위한 DB 시드/덤프 모음입니다. (생성일: 2026-04-12)

소스: 로컬 `devmatch-mysql` 컨테이너 (MySQL 8.0, DB `devmatch`)

## 파일 목록

| 파일 | 용도 |
| --- | --- |
| `devmatch-full-dump.sql` | 스키마 + 데이터 풀 덤프. 비어있는 DB에 한 번에 복원할 때 사용. |
| `devmatch-schema.sql` | 스키마(DDL)만. 구조 비교/리뷰용. |
| `devmatch-data-only.sql` | INSERT 만. 이미 Hibernate가 만든 스키마 위에 데이터만 얹을 때 사용. |
| `seed-lms.sql` | LMS(매칭/커리큘럼/세션/과제/노트/캘린더) 전용 시드. `backend/src/main/resources/seed-lms.sql`의 사본. |

> 비고: 일반 테스트/문제/멘토 더미데이터는 백엔드 부팅 시 `DataInitializer`(CommandLineRunner) 가 자동으로 채웁니다. LMS 시드는 자동 생성되지 않으므로 `seed-lms.sql`을 별도로 적용해야 합니다.

## 적용 방법

### A. 깨끗한 DB에 풀 덤프 복원

```bash
# 컨테이너 기동
docker compose up -d mysql

# (선택) 기존 devmatch DB 비우고 다시 만들기
docker exec -i devmatch-mysql mysql -uroot -padminuser -e "DROP DATABASE IF EXISTS devmatch; CREATE DATABASE devmatch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 풀 덤프 복원
docker exec -i devmatch-mysql mysql -uroot -padminuser devmatch < backend/data/devmatch-full-dump.sql
```

PowerShell:

```powershell
Get-Content backend/data/devmatch-full-dump.sql | docker exec -i devmatch-mysql mysql -uroot -padminuser devmatch
```

### B. 백엔드를 한 번 띄운 뒤 LMS 시드만 추가

`DataInitializer`가 기본 데이터를 만든 다음, LMS 데이터만 따로 넣고 싶을 때.

```bash
docker exec -i devmatch-mysql mysql -uroot -padminuser devmatch < backend/data/seed-lms.sql
```

## 재생성 방법 (덤프 갱신)

DB 상태가 바뀌어 새 덤프를 만들고 싶을 때:

```bash
# 풀 덤프
docker exec devmatch-mysql sh -c 'mysqldump -uroot -padminuser --default-character-set=utf8mb4 --single-transaction --no-tablespaces --routines --triggers --events devmatch' > backend/data/devmatch-full-dump.sql

# 스키마만
docker exec devmatch-mysql sh -c 'mysqldump -uroot -padminuser --default-character-set=utf8mb4 --no-data --no-tablespaces --routines --triggers devmatch' > backend/data/devmatch-schema.sql

# 데이터만
docker exec devmatch-mysql sh -c 'mysqldump -uroot -padminuser --default-character-set=utf8mb4 --single-transaction --no-tablespaces --no-create-info --skip-extended-insert --complete-insert devmatch' > backend/data/devmatch-data-only.sql
```

## 주의

- 덤프에는 로컬 개발용 더미 사용자 비밀번호 해시가 들어 있습니다. 운영 DB에 그대로 넣지 마세요.
- 날짜 컬럼 다수가 2026-03 ~ 2026-04 기준입니다. 캘린더/대시보드 화면을 그대로 보려면 시스템 시계와 무관하게 이 시기 데이터를 그대로 사용하세요.
-- LMS Seed Data
-- Run AFTER backend starts with new entities (Hibernate will create tables)
-- Execute (CMD): docker exec -i devmatch-mysql mysql -u root -padminuser devmatch < seed-lms.sql
-- Execute (PowerShell): Get-Content seed-lms.sql | docker exec -i devmatch-mysql mysql -u root -padminuser devmatch

SET NAMES utf8mb4;

-- ============================================================
-- Matching for user 8 (qwer@naver.com, MENTEE) ↔ mentor 1 (김자바)
-- ============================================================

-- 0. Clean up existing data for user 8's matching
SET @m8_id = (SELECT id FROM matchings WHERE mentee_id = 8 LIMIT 1);

DELETE FROM session_change_requests WHERE session_id IN (SELECT id FROM mentoring_sessions WHERE matching_id = @m8_id);
DELETE FROM mentor_time_slots WHERE matching_id = @m8_id;
DELETE FROM note_comments WHERE note_id IN (SELECT id FROM learning_notes WHERE matching_id = @m8_id);
DELETE FROM learning_notes WHERE matching_id = @m8_id;
DELETE FROM assignment_submissions WHERE assignment_id IN (SELECT id FROM assignments WHERE matching_id = @m8_id);
DELETE FROM assignments WHERE matching_id = @m8_id;
DELETE FROM mentoring_sessions WHERE matching_id = @m8_id;
DELETE FROM curriculum_weeks WHERE curriculum_id IN (SELECT id FROM curriculums WHERE matching_id = @m8_id);
DELETE FROM curriculums WHERE matching_id = @m8_id;
DELETE FROM payments WHERE matching_id = @m8_id;
DELETE FROM matchings WHERE mentee_id = 8;

-- 0-1. Create matching (ACCEPTED) and payment (CONFIRMED)
INSERT INTO matchings (mentee_id, mentor_id, status, category, message, created_at, updated_at)
VALUES (8, 1, 'ACCEPTED', 'Backend', 'Java 백엔드 개발을 배우고 싶습니다', NOW(), NOW());

SET @matching_id = LAST_INSERT_ID();

INSERT INTO payments (matching_id, user_id, application_id, amount, order_id, payment_key, status, created_at, updated_at)
VALUES (@matching_id, 8, 0, 300000, CONCAT('ORDER-', @matching_id, '-', UNIX_TIMESTAMP()), CONCAT('PAY-', @matching_id), 'CONFIRMED', NOW(), NOW());

-- 1. Curriculum
INSERT INTO curriculums (matching_id, title, description, total_weeks, start_date, end_date, discord_url, created_at, updated_at)
VALUES (@matching_id, 'Java Backend 마스터 과정', 'Spring Boot와 JPA를 활용한 백엔드 개발 심화 과정', 8, '2026-03-24', '2026-05-18', NULL, NOW(), NOW());

SET @curriculum_id = LAST_INSERT_ID();

-- 2. Curriculum Weeks (topics, resources must be JSON arrays for StringListConverter)
INSERT INTO curriculum_weeks (curriculum_id, week_number, title, description, topics, resources, is_completed, completed_at) VALUES
(@curriculum_id, 1, 'Java 기초 복습', '객체지향 핵심 개념과 Java 17 기능', '["OOP 4대 원칙","Java 17 Records","Sealed Classes"]', '["https://docs.oracle.com/en/java/"]', true, '2026-03-28 18:00:00'),
(@curriculum_id, 2, 'Spring Boot 기초', 'Spring Boot 프로젝트 구성과 DI/IoC', '["Spring IoC","의존성 주입","Bean 생명주기"]', '["https://spring.io/guides"]', true, '2026-04-04 18:00:00'),
(@curriculum_id, 3, 'Spring MVC & REST API', 'RESTful API 설계와 구현', '["REST 설계 원칙","Controller 패턴","예외 처리"]', '["https://spring.io/guides/gs/rest-service/"]', true, '2026-04-11 18:00:00'),
(@curriculum_id, 4, 'JPA & 데이터 접근', 'JPA 엔티티 매핑과 쿼리 작성', '["Entity 매핑","연관관계","JPQL","QueryDSL"]', '["https://docs.spring.io/spring-data/jpa/"]', false, NULL),
(@curriculum_id, 5, '인증 & 보안', 'Spring Security와 JWT 인증', '["Spring Security","JWT","OAuth2"]', '[]', false, NULL),
(@curriculum_id, 6, '테스트 & CI/CD', '단위 테스트와 통합 테스트', '["JUnit 5","Mockito","Testcontainers"]', '[]', false, NULL),
(@curriculum_id, 7, '성능 최적화', '캐싱, 인덱싱, 비동기 처리', '["Redis 캐싱","DB 인덱스","@Async"]', '[]', false, NULL),
(@curriculum_id, 8, '배포 & 운영', 'Docker, AWS 배포', '["Docker Compose","AWS EC2","모니터링"]', '[]', false, NULL);

-- 3. Mentoring Sessions (remove unique index on matching_id if exists)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mentoring_sessions' AND COLUMN_NAME = 'matching_id' AND NON_UNIQUE = 0);
SET @drop_sql = IF(@idx_exists > 0, (SELECT CONCAT('ALTER TABLE mentoring_sessions DROP INDEX ', INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mentoring_sessions' AND COLUMN_NAME = 'matching_id' AND NON_UNIQUE = 0 LIMIT 1), 'SELECT 1');
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO mentoring_sessions (matching_id, mentee_id, mentor_id, category, session_date, start_time, end_time, status, meet_link, memo, created_at, updated_at) VALUES
(@matching_id, 8, 1, 'Backend', '2026-03-25', '19:00:00', '20:00:00', 'COMPLETED', NULL, '첫 세션 - OOP 개념 리뷰', NOW(), NOW()),
(@matching_id, 8, 1, 'Backend', '2026-04-01', '19:00:00', '20:00:00', 'COMPLETED', NULL, 'Spring Boot 프로젝트 셋업 실습', NOW(), NOW()),
(@matching_id, 8, 1, 'Backend', '2026-04-08', '19:00:00', '20:00:00', 'COMPLETED', NULL, 'REST API 설계 리뷰', NOW(), NOW()),
(@matching_id, 8, 1, 'Backend', '2026-04-14', '19:00:00', '20:00:00', 'SCHEDULED', NULL, 'JPA 엔티티 매핑 실습', NOW(), NOW()),
(@matching_id, 8, 1, 'Backend', '2026-04-21', '19:00:00', '20:00:00', 'SCHEDULED', NULL, 'QueryDSL 심화', NOW(), NOW());

-- 4. Assignments
INSERT INTO assignments (matching_id, mentor_id, type, title, description, due_date, reference_urls, status, created_at, updated_at) VALUES
(@matching_id, 1, 'TASK', 'REST API 설계 과제', 'Todo 앱의 RESTful API를 설계하고 Swagger 문서를 작성하세요', '2026-04-01', '["https://swagger.io/docs/"]', 'REVIEWED', NOW(), NOW()),
(@matching_id, 1, 'CODE_REVIEW', 'Spring Boot CRUD 구현', 'Todo 앱의 CRUD API를 Spring Boot로 구현하세요', '2026-04-08', '["https://github.com/example/spring-todo"]', 'SUBMITTED', NOW(), NOW()),
(@matching_id, 1, 'TASK', 'JPA 연관관계 매핑', '1:N, N:M 연관관계를 매핑하고 테스트를 작성하세요', '2026-04-15', '[]', 'ASSIGNED', NOW(), NOW()),
(@matching_id, 1, 'TASK', 'Spring Security JWT 인증', 'JWT 기반 인증/인가를 구현하세요', '2026-04-22', '[]', 'ASSIGNED', NOW(), NOW());

-- 5. Assignment Submissions
SET @assign1_id = (SELECT id FROM assignments WHERE title = 'REST API 설계 과제' AND matching_id = @matching_id LIMIT 1);
SET @assign2_id = (SELECT id FROM assignments WHERE title = 'Spring Boot CRUD 구현' AND matching_id = @matching_id LIMIT 1);

INSERT INTO assignment_submissions (assignment_id, mentee_id, submission_url, submission_note, submitted_at, feedback_content, grade, feedback_at) VALUES
(@assign1_id, 8, 'https://github.com/qwer/todo-api-design', 'Swagger UI 포함하여 작성했습니다', '2026-03-31 23:00:00', 'API 설계가 깔끔합니다. HATEOAS도 고려해보세요.', 'A', '2026-04-02 10:00:00'),
(@assign2_id, 8, 'https://github.com/qwer/spring-todo-crud', '기본 CRUD 완성, 예외 처리 추가 예정', '2026-04-07 22:00:00', NULL, NULL, NULL);

-- 6. Learning Notes
INSERT INTO learning_notes (matching_id, author_id, type, week_number, title, content, self_rating, created_at, updated_at) VALUES
(@matching_id, 8, 'SESSION_REVIEW', 1, '1주차 세션 정리 - OOP 핵심', '오늘 세션에서 SOLID 원칙에 대해 깊이 있게 배웠다. 특히 LSP와 ISP의 실제 적용 사례가 인상적이었다. 다음에는 실제 코드에서 이 원칙들이 어떻게 적용되는지 더 연습해봐야겠다.', 4, NOW(), NOW()),
(@matching_id, 8, 'WEEKLY_JOURNAL', 2, '2주차 학습일지 - Spring Boot', 'Spring Boot의 자동 설정 메커니즘을 이해하게 되었다. @SpringBootApplication 어노테이션이 @Configuration, @EnableAutoConfiguration, @ComponentScan을 포함하고 있다는 것을 알게 됐다.', 3, NOW(), NOW()),
(@matching_id, 8, 'SESSION_REVIEW', 3, '3주차 세션 정리 - REST API', 'REST API 설계 원칙과 HTTP 메서드 사용법을 정리했다. Richardson Maturity Model Level 2까지는 확실히 이해했고, Level 3 HATEOAS는 추가 학습이 필요하다.', 5, NOW(), NOW());

-- 7. Note Comments
SET @note1_id = (SELECT id FROM learning_notes WHERE title = '1주차 세션 정리 - OOP 핵심' AND matching_id = @matching_id LIMIT 1);
SET @note3_id = (SELECT id FROM learning_notes WHERE title = '3주차 세션 정리 - REST API' AND matching_id = @matching_id LIMIT 1);

INSERT INTO note_comments (note_id, author_id, content, created_at) VALUES
(@note1_id, 1, 'SOLID 원칙 정리를 잘 하셨네요. DIP(의존성 역전)도 Spring에서 어떻게 적용되는지 다음 세션에서 함께 보겠습니다.', NOW()),
(@note3_id, 1, 'Richardson Maturity Model 정리가 좋습니다. Level 3는 실무에서는 잘 안 쓰이니 Level 2까지 확실히 익히는 게 좋습니다.', NOW());

-- 8. Mentor Time Slots (for calendar feature)
INSERT INTO mentor_time_slots (mentor_id, matching_id, slot_date, start_time, end_time, is_booked, created_at) VALUES
(1, @matching_id, '2026-04-14', '19:00:00', '20:00:00', true, NOW()),
(1, @matching_id, '2026-04-16', '20:00:00', '21:00:00', false, NOW()),
(1, @matching_id, '2026-04-18', '19:00:00', '20:00:00', false, NOW()),
(1, @matching_id, '2026-04-21', '19:00:00', '20:00:00', true, NOW()),
(1, @matching_id, '2026-04-23', '19:00:00', '20:30:00', false, NOW()),
(1, @matching_id, '2026-04-25', '20:00:00', '21:00:00', false, NOW());

-- 9. Session Change Request (PENDING for 04-21 session)
SET @session_0421 = (SELECT id FROM mentoring_sessions WHERE session_date = '2026-04-21' AND matching_id = @matching_id LIMIT 1);

INSERT INTO session_change_requests (session_id, requester_id, new_date, new_start_time, new_end_time, reason, status, created_at)
VALUES (@session_0421, 8, '2026-04-22', '20:00:00', '21:00:00', '개인 일정이 생겨서 하루 뒤로 변경 부탁드립니다', 'PENDING', NOW());

-- Flyway baseline: ddl-auto 로 생성돼 있던 기존 스키마의 스냅샷 (2026-07-27)
-- 기존 DB 에는 baseline-on-migrate 로 "이미 적용됨" 처리되어 실행되지 않고,
-- 빈 DB(신규 환경)에서는 이 파일이 스키마를 만든다.
--
-- FOREIGN_KEY_CHECKS: mysqldump 는 테이블을 알파벳순으로 출력하므로 FK 가 아직 생성되지 않은
-- 테이블을 참조할 수 있다. 생성 중에만 검사를 끄고 끝나면 되돌린다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `admin_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` enum('ADMIN_CREATE','COMMENT_DELETE','MENTOR_APPROVE','MENTOR_CHANGE_APPROVE','MENTOR_CHANGE_REJECT','MENTOR_REJECT','PAYMENT_REFUND','POST_DELETE','USER_DEACTIVATE','USER_DELETE','USER_MENTOR_SWAP','USER_PASSWORD_RESET','USER_REACTIVATE','USER_ROLE_CHANGE') NOT NULL,
  `admin_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `metadata` text,
  `reason` varchar(500) DEFAULT NULL,
  `target_id` bigint NOT NULL,
  `target_type` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_admin_created_at` (`admin_id`,`created_at`),
  KEY `idx_audit_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `ai_review_candidate_audits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('APPROVE','CAPTURE','EDIT_AND_APPROVE','MERGE','REJECT','START_REVIEW') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `next_status` enum('APPROVED','MERGED','PENDING','REJECTED') NOT NULL,
  `previous_status` enum('APPROVED','MERGED','PENDING','REJECTED') NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `reviewer` varchar(80) DEFAULT NULL,
  `reviewer_edited_answer` text,
  `candidate_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_review_candidate_audit_candidate` (`candidate_id`,`created_at`),
  CONSTRAINT `FKpc272ugl8hxyxlt496fd1xqrp` FOREIGN KEY (`candidate_id`) REFERENCES `ai_review_candidates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `ai_review_candidates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(120) NOT NULL,
  `confidence_score` double DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `definition` text,
  `definition_draft` text,
  `external_candidate_id` varchar(120) DEFAULT NULL,
  `merged_into_id` bigint DEFAULT NULL,
  `needs_review_reason` varchar(120) DEFAULT NULL,
  `rejected_reason` varchar(500) DEFAULT NULL,
  `resolved_query` varchar(500) DEFAULT NULL,
  `retention_until` datetime(6) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewer` varchar(80) DEFAULT NULL,
  `reviewer_edited_answer` text,
  `route` varchar(80) DEFAULT NULL,
  `source` enum('AUTO','COURSE','MANUAL') NOT NULL,
  `source_question` varchar(1000) DEFAULT NULL,
  `status` enum('APPROVED','MERGED','PENDING','REJECTED') NOT NULL,
  `term` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `workflow_phase` enum('APPROVED','CAPTURED','DRAFTED','HUMAN_REVIEW','MERGED','PUBLISH_FAILED','REJECTED') DEFAULT NULL,
  `publish_error` varchar(500) DEFAULT NULL,
  `published_card_id` varchar(160) DEFAULT NULL,
  `published_card_path` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_review_candidate_status` (`status`),
  KEY `idx_ai_review_candidate_external_id` (`external_candidate_id`),
  KEY `idx_ai_review_candidate_term_category` (`term`,`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `ai_review_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(2000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `evaluation` enum('NEEDS_REVIEW','PARTIAL','UNDERSTOOD') DEFAULT NULL,
  `mode` enum('CHECK_ANSWER','CHECK_QUESTION','EXPLANATION','FREE_ANSWER','FREE_QUESTION','NEXT_QUESTION','QUESTION_SUMMARY','REVIEW_REPORT','SYSTEM_SUMMARY') DEFAULT NULL,
  `role` enum('AI','USER') NOT NULL,
  `question_id` bigint DEFAULT NULL,
  `session_id` bigint NOT NULL,
  `ai_answer_style` varchar(40) DEFAULT NULL,
  `ai_candidate_id` varchar(80) DEFAULT NULL,
  `ai_correction_type` varchar(40) DEFAULT NULL,
  `ai_latency_ms` int DEFAULT NULL,
  `ai_matched_concept_id` varchar(120) DEFAULT NULL,
  `ai_quality_flags` varchar(500) DEFAULT NULL,
  `ai_resolved_query` varchar(500) DEFAULT NULL,
  `ai_route` varchar(80) DEFAULT NULL,
  `stream_request_id` varchar(80) DEFAULT NULL,
  `stream_terminal_status` enum('COMPLETED','DISCONNECTED','ERROR') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_review_stream_request_terminal` (`stream_request_id`),
  KEY `FKh36ragl03vu4517gf21wckxbs` (`question_id`),
  KEY `FKmexe48m5vt4ivsh2q26yfxer6` (`session_id`),
  CONSTRAINT `FKh36ragl03vu4517gf21wckxbs` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`),
  CONSTRAINT `FKmexe48m5vt4ivsh2q26yfxer6` FOREIGN KEY (`session_id`) REFERENCES `ai_review_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `ai_review_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `course_key` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `status` enum('COMPLETED','IN_PROGRESS') NOT NULL,
  `summary` varchar(2000) DEFAULT NULL,
  `weakness_tags` text,
  `test_result_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK694c3kopn45vi0447cemsyv3b` (`test_result_id`),
  KEY `FK6x7nt7w30btvv212xt93hxsbm` (`user_id`),
  CONSTRAINT `FK694c3kopn45vi0447cemsyv3b` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`id`),
  CONSTRAINT `FK6x7nt7w30btvv212xt93hxsbm` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `application_rejected_mentors` (
  `application_id` bigint NOT NULL,
  `mentor_id` bigint DEFAULT NULL,
  KEY `FK3okxa82bmb4hovgyr4l5rehqs` (`application_id`),
  CONSTRAINT `FK3okxa82bmb4hovgyr4l5rehqs` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `career_goal` varchar(200) NOT NULL,
  `category` varchar(50) NOT NULL,
  `course_type` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_level` varchar(50) NOT NULL,
  `desired_months` int NOT NULL,
  `status` varchar(20) NOT NULL,
  `target_tech_stack` varchar(500) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `mentee_id` bigint NOT NULL,
  `auto_matched` bit(1) NOT NULL,
  `career_years` varchar(255) DEFAULT NULL,
  `github_url` varchar(255) DEFAULT NULL,
  `goal` varchar(255) DEFAULT NULL,
  `is_cs_major` bit(1) DEFAULT NULL,
  `languages` text,
  `learning_paths` text,
  `personality` varchar(255) DEFAULT NULL,
  `platforms` text,
  `project_count` varchar(255) DEFAULT NULL,
  `project_description` text,
  `referral_code` varchar(255) DEFAULT NULL,
  `referral_sources` text,
  `rejected_reason` text,
  `reviewed_by` bigint DEFAULT NULL,
  `self_introduction` text,
  `submitted_at` datetime(6) DEFAULT NULL,
  `terms_agreed` bit(1) DEFAULT NULL,
  `weekday_study_hours` varchar(255) DEFAULT NULL,
  `weekend_study_hours` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `assigned_mentor_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb1402y14c9llj80igp3aj5lwn` (`mentee_id`),
  KEY `FKhk8ow3eieg24ylcu4pokjljti` (`assigned_mentor_id`),
  CONSTRAINT `FKb1402y14c9llj80igp3aj5lwn` FOREIGN KEY (`mentee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhk8ow3eieg24ylcu4pokjljti` FOREIGN KEY (`assigned_mentor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `assignment_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `feedback_at` datetime(6) DEFAULT NULL,
  `feedback_content` text,
  `grade` varchar(10) DEFAULT NULL,
  `mentee_id` bigint NOT NULL,
  `submission_note` text,
  `submission_url` varchar(500) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `assignment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpw60qm3gotl9ggxmflvtuur9s` (`assignment_id`),
  CONSTRAINT `FKm7i7ubgh7y2n6mvg8muw62oax` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `due_date` date DEFAULT NULL,
  `matching_id` bigint NOT NULL,
  `mentor_id` bigint NOT NULL,
  `reference_urls` text,
  `status` enum('ASSIGNED','REVIEWED','SUBMITTED') NOT NULL,
  `title` varchar(200) NOT NULL,
  `type` enum('CODE_REVIEW','TASK') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `deletion_reason` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8omq0tc18jd43bu5tjh6jvraq` (`user_id`),
  KEY `FKh4c7lvsc298whoyd4w9ta25cr` (`post_id`),
  KEY `idx_comments_deleted_post` (`deleted`,`post_id`),
  CONSTRAINT `FK8omq0tc18jd43bu5tjh6jvraq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKh4c7lvsc298whoyd4w9ta25cr` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `curriculum_weeks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `description` text,
  `is_completed` bit(1) NOT NULL,
  `resources` text,
  `title` varchar(200) NOT NULL,
  `topics` text,
  `week_number` int NOT NULL,
  `curriculum_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg8jbbpxtwkwx9ydmi2n3ky2fu` (`curriculum_id`),
  CONSTRAINT `FKg8jbbpxtwkwx9ydmi2n3ky2fu` FOREIGN KEY (`curriculum_id`) REFERENCES `curriculums` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `curriculums` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `discord_url` varchar(500) DEFAULT NULL,
  `end_date` date NOT NULL,
  `matching_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `title` varchar(200) NOT NULL,
  `total_weeks` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5trjgww9hakkuwikq12wobk42` (`matching_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `faq` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answer` text NOT NULL,
  `category` enum('MENTORING','MENTOR_APPLY','PAYMENT','SERVICE_INTRO','TEST') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `order_index` int NOT NULL,
  `published` bit(1) NOT NULL,
  `question` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_faq_published_category_order` (`published`,`category`,`order_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `learning_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `matching_id` bigint NOT NULL,
  `self_rating` int DEFAULT NULL,
  `session_id` bigint DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `type` enum('SESSION_REVIEW','WEEKLY_JOURNAL') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `week_number` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `matchings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `message` varchar(500) DEFAULT NULL,
  `rejected_reason` varchar(500) DEFAULT NULL,
  `status` enum('ACCEPTED','CANCELLED','PENDING','REJECTED','SWAPPED','TRIAL') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `mentee_id` bigint NOT NULL,
  `mentor_id` bigint NOT NULL,
  `test_result_id` bigint DEFAULT NULL,
  `application_id` bigint DEFAULT NULL,
  `swap_count` int DEFAULT NULL,
  `trial_end_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5a0bgqkn7111m4pdu9ln1j5bd` (`mentee_id`),
  KEY `FK6crgxtxpn4k0aqdeqdthrtl5m` (`mentor_id`),
  KEY `FKf0j1h5ns02m8cn5eki18bk1jh` (`test_result_id`),
  CONSTRAINT `FK5a0bgqkn7111m4pdu9ln1j5bd` FOREIGN KEY (`mentee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6crgxtxpn4k0aqdeqdthrtl5m` FOREIGN KEY (`mentor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKf0j1h5ns02m8cn5eki18bk1jh` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_availabilities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day_of_week` varchar(10) NOT NULL,
  `end_time` time(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `mentor_id` bigint NOT NULL,
  `start_time` time(6) NOT NULL,
  `is_waiting` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_change_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `current_matching_id` bigint NOT NULL,
  `current_mentor_id` bigint NOT NULL,
  `decided_by_admin_id` bigint DEFAULT NULL,
  `mentee_id` bigint NOT NULL,
  `new_mentor_id` bigint DEFAULT NULL,
  `reason` varchar(500) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `status` enum('APPROVED','CANCELLED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mentor_change_status_created` (`status`,`created_at`),
  KEY `idx_mentor_change_mentee_status` (`mentee_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_profile_courses` (
  `mentor_profile_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  PRIMARY KEY (`mentor_profile_id`,`course_id`),
  KEY `FKhv3bbeldyxb0vird6t5t8xqf0` (`course_id`),
  CONSTRAINT `FK23skr0526lfmu6wfgh4wrtlx2` FOREIGN KEY (`mentor_profile_id`) REFERENCES `mentor_profiles` (`id`),
  CONSTRAINT `FKhv3bbeldyxb0vird6t5t8xqf0` FOREIGN KEY (`course_id`) REFERENCES `mentoring_courses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_profile_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bio` varchar(1000) DEFAULT NULL,
  `career_years` int NOT NULL,
  `certifications` text,
  `company` varchar(100) DEFAULT NULL,
  `course_keys` text NOT NULL,
  `education` varchar(200) DEFAULT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `portfolio_url` varchar(500) DEFAULT NULL,
  `preferred_mentee_level` varchar(20) DEFAULT NULL,
  `rejected_reason` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `tech_stack` text,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bio` varchar(1000) DEFAULT NULL,
  `career_years` int NOT NULL,
  `company` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `specialty` text,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `certifications` text,
  `education` varchar(200) DEFAULT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `portfolio_url` varchar(500) DEFAULT NULL,
  `preferred_mentee_level` varchar(20) DEFAULT NULL,
  `tech_stack` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpai8h0mpu1jotdijdxmve22qa` (`user_id`),
  CONSTRAINT `FK335r9en8na3y8bk9ltm01m83f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentor_time_slots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `is_booked` bit(1) NOT NULL,
  `matching_id` bigint NOT NULL,
  `mentor_id` bigint NOT NULL,
  `slot_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  `proposed_by_mentee` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentoring_courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `boxes_json` text,
  `course_key` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description_text` text,
  `description_title` text,
  `display_order` int NOT NULL,
  `icon_string` varchar(10) DEFAULT NULL,
  `subtitle` varchar(500) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhs1v0j492ptyh7dv01exs46u9` (`course_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mentoring_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `calendar_event_id` varchar(200) DEFAULT NULL,
  `category` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `matching_id` bigint NOT NULL,
  `meet_link` varchar(500) DEFAULT NULL,
  `memo` varchar(1000) DEFAULT NULL,
  `mentee_id` bigint NOT NULL,
  `mentor_id` bigint NOT NULL,
  `session_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  `status` enum('CANCELLED','COMPLETED','PENDING','SCHEDULED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mock_interviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `interview_date` date NOT NULL,
  `matching_id` bigint NOT NULL,
  `mentor_feedback` text,
  `questions_and_answers` text,
  `rating` int DEFAULT NULL,
  `topic` varchar(200) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `note_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `note_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoqekbioxfj7wm3oxm3xw6mq0a` (`note_id`),
  CONSTRAINT `FKoqekbioxfj7wm3oxm3xw6mq0a` FOREIGN KEY (`note_id`) REFERENCES `learning_notes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `cancel_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `matching_id` bigint NOT NULL,
  `order_id` varchar(100) NOT NULL,
  `payment_key` varchar(200) DEFAULT NULL,
  `status` enum('CANCELLED','CONFIRMED','FAILED','PENDING') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `application_id` bigint NOT NULL,
  `course_type` varchar(20) DEFAULT NULL,
  `discount_applied` int DEFAULT NULL,
  `installment_months` int DEFAULT NULL,
  `months_bundled` int DEFAULT NULL,
  `renewal_count` int DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `processed_by_admin_id` bigint DEFAULT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK51m1gkdcevrqj4pof90j6sure` (`matching_id`),
  UNIQUE KEY `UK8vo36cen604as7etdfwmyjsxt` (`order_id`),
  UNIQUE KEY `UK35yqdahtiysne6iij9ske72bj` (`payment_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5l2rj28vw5oj6f7ox746grokg` (`post_id`,`user_id`),
  KEY `FKkgau5n0nlewg6o9lr4yibqgxj` (`user_id`),
  CONSTRAINT `FKa5wxsgl4doibhbed9gm7ikie2` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `FKkgau5n0nlewg6o9lr4yibqgxj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_count` int NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `like_count` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `category` varchar(50) NOT NULL,
  `view_count` int NOT NULL,
  `deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `deletion_reason` varchar(500) DEFAULT NULL,
  `image_url` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5lidm6cqbc7u4xhqpxm898qme` (`user_id`),
  KEY `idx_posts_deleted_created` (`deleted`,`created_at`),
  CONSTRAINT `FK5lidm6cqbc7u4xhqpxm898qme` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(2000) NOT NULL,
  `correct_answer` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `options` text,
  `order_index` int NOT NULL,
  `score` int NOT NULL,
  `test_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoc6xkgj16nhyyes4ath9dyxxw` (`test_id`),
  CONSTRAINT `FKoc6xkgj16nhyyes4ath9dyxxw` FOREIGN KEY (`test_id`) REFERENCES `tests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `recommended_mentors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `is_selected` bit(1) NOT NULL,
  `match_score` int NOT NULL,
  `recommend_reason` varchar(500) DEFAULT NULL,
  `mentor_id` bigint NOT NULL,
  `survey_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKi5717br7pd4a45b05b0u09gui` (`mentor_id`),
  KEY `FKj9goer7rwjd2bom24dwximsh2` (`survey_id`),
  CONSTRAINT `FKi5717br7pd4a45b05b0u09gui` FOREIGN KEY (`mentor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKj9goer7rwjd2bom24dwximsh2` FOREIGN KEY (`survey_id`) REFERENCES `survey_responses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `resumes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `feedback_at` datetime(6) DEFAULT NULL,
  `file_name` varchar(200) NOT NULL,
  `file_url` varchar(500) NOT NULL,
  `matching_id` bigint NOT NULL,
  `mentee_id` bigint NOT NULL,
  `mentor_feedback` text,
  `uploaded_at` datetime(6) NOT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `session_change_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `new_date` date NOT NULL,
  `new_end_time` time(6) NOT NULL,
  `new_start_time` time(6) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `session_id` bigint NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `survey_responses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `career_goal` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_level` varchar(50) DEFAULT NULL,
  `feedback_preference` varchar(500) DEFAULT NULL,
  `learning_style` varchar(500) DEFAULT NULL,
  `mentoring_method` varchar(500) DEFAULT NULL,
  `preferred_schedule` varchar(500) DEFAULT NULL,
  `tech_stack` varchar(500) DEFAULT NULL,
  `mentee_id` bigint NOT NULL,
  `payment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKke7osswgsg2qe3tpca7avj7wv` (`payment_id`),
  KEY `FKkid1qar61i8vt5sav5od4u9n` (`mentee_id`),
  CONSTRAINT `FKkid1qar61i8vt5sav5od4u9n` FOREIGN KEY (`mentee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKoyi8mbyplh4pwb38nu4w64wtb` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `test_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_correct` bit(1) NOT NULL,
  `selected_answer` int NOT NULL,
  `question_id` bigint NOT NULL,
  `test_result_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfxnpjlreos87c1dqcqmt820lu` (`question_id`),
  KEY `FKiu9lgbmictlrx8cl57xtki0sf` (`test_result_id`),
  CONSTRAINT `FKfxnpjlreos87c1dqcqmt820lu` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`),
  CONSTRAINT `FKiu9lgbmictlrx8cl57xtki0sf` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `test_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `correct_count` int NOT NULL,
  `passed` bit(1) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `total_score` int NOT NULL,
  `test_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeb5e15t9e5hn11gbkuub0xeln` (`test_id`),
  KEY `FK3pgkl7t3gw3f6eu20n4db4i20` (`user_id`),
  CONSTRAINT `FK3pgkl7t3gw3f6eu20n4db4i20` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKeb5e15t9e5hn11gbkuub0xeln` FOREIGN KEY (`test_id`) REFERENCES `tests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `tests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `difficulty` enum('ADVANCED','BEGINNER','INTERMEDIATE') NOT NULL,
  `is_active` bit(1) NOT NULL,
  `passing_score` int NOT NULL,
  `question_count` int NOT NULL,
  `time_limit` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(100) NOT NULL,
  `name` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `provider` varchar(20) DEFAULT NULL,
  `provider_id` varchar(100) DEFAULT NULL,
  `role` enum('ADMIN','MENTEE','MENTOR','SUPER_ADMIN') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `must_change_password` bit(1) NOT NULL,
  `status` enum('ACTIVE','DEACTIVATED','DELETED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `video_meetings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `platform` varchar(255) NOT NULL,
  `session_id` bigint NOT NULL,
  `url` varchar(255) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;

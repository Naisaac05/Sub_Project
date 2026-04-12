-- MySQL dump 10.13  Distrib 8.0.45, for Linux (x86_64)
--
-- Host: localhost    Database: devmatch
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `applications`
--

DROP TABLE IF EXISTS `applications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  PRIMARY KEY (`id`),
  KEY `FKb1402y14c9llj80igp3aj5lwn` (`mentee_id`),
  CONSTRAINT `FKb1402y14c9llj80igp3aj5lwn` FOREIGN KEY (`mentee_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assignment_submissions`
--

DROP TABLE IF EXISTS `assignment_submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assignments`
--

DROP TABLE IF EXISTS `assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `comments`
--

DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8omq0tc18jd43bu5tjh6jvraq` (`user_id`),
  KEY `FKh4c7lvsc298whoyd4w9ta25cr` (`post_id`),
  CONSTRAINT `FK8omq0tc18jd43bu5tjh6jvraq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKh4c7lvsc298whoyd4w9ta25cr` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `curriculum_weeks`
--

DROP TABLE IF EXISTS `curriculum_weeks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `curriculums`
--

DROP TABLE IF EXISTS `curriculums`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_notes`
--

DROP TABLE IF EXISTS `learning_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `matchings`
--

DROP TABLE IF EXISTS `matchings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mentor_availabilities`
--

DROP TABLE IF EXISTS `mentor_availabilities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mentor_availabilities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day_of_week` varchar(10) NOT NULL,
  `end_time` time(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `mentor_id` bigint NOT NULL,
  `start_time` time(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mentor_profiles`
--

DROP TABLE IF EXISTS `mentor_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpai8h0mpu1jotdijdxmve22qa` (`user_id`),
  CONSTRAINT `FK335r9en8na3y8bk9ltm01m83f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mentor_time_slots`
--

DROP TABLE IF EXISTS `mentor_time_slots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mentor_time_slots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `is_booked` bit(1) NOT NULL,
  `matching_id` bigint NOT NULL,
  `mentor_id` bigint NOT NULL,
  `slot_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mentoring_sessions`
--

DROP TABLE IF EXISTS `mentoring_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `status` enum('CANCELLED','COMPLETED','SCHEDULED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mock_interviews`
--

DROP TABLE IF EXISTS `mock_interviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `note_comments`
--

DROP TABLE IF EXISTS `note_comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `note_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `note_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoqekbioxfj7wm3oxm3xw6mq0a` (`note_id`),
  CONSTRAINT `FKoqekbioxfj7wm3oxm3xw6mq0a` FOREIGN KEY (`note_id`) REFERENCES `learning_notes` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK51m1gkdcevrqj4pof90j6sure` (`matching_id`),
  UNIQUE KEY `UK8vo36cen604as7etdfwmyjsxt` (`order_id`),
  UNIQUE KEY `UK35yqdahtiysne6iij9ske72bj` (`payment_key`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `post_likes`
--

DROP TABLE IF EXISTS `post_likes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `posts`
--

DROP TABLE IF EXISTS `posts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_count` int NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `like_count` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5lidm6cqbc7u4xhqpxm898qme` (`user_id`),
  CONSTRAINT `FK5lidm6cqbc7u4xhqpxm898qme` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `questions`
--

DROP TABLE IF EXISTS `questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=151 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recommended_mentors`
--

DROP TABLE IF EXISTS `recommended_mentors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `resumes`
--

DROP TABLE IF EXISTS `resumes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `session_change_requests`
--

DROP TABLE IF EXISTS `session_change_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `survey_responses`
--

DROP TABLE IF EXISTS `survey_responses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_answers`
--

DROP TABLE IF EXISTS `test_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_results`
--

DROP TABLE IF EXISTS `test_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tests`
--

DROP TABLE IF EXISTS `tests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(100) NOT NULL,
  `name` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `provider` varchar(20) DEFAULT NULL,
  `provider_id` varchar(100) DEFAULT NULL,
  `role` enum('ADMIN','MENTEE','MENTOR') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'devmatch'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-12  6:33:30

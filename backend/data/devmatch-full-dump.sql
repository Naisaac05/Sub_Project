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
-- Dumping data for table `applications`
--

/*!40000 ALTER TABLE `applications` DISABLE KEYS */;
INSERT INTO `applications` VALUES (1,'백엔드 개발자','Java Backend','IMMEDIATE','2026-04-10 01:32:57.053817','BEGINNER',3,'PAID','Java, Spring Boot, JPA','2026-04-10 01:32:57.053817',10);
/*!40000 ALTER TABLE `applications` ENABLE KEYS */;

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
-- Dumping data for table `assignment_submissions`
--

/*!40000 ALTER TABLE `assignment_submissions` DISABLE KEYS */;
INSERT INTO `assignment_submissions` VALUES (1,'2026-04-02 10:00:00.000000','API ì„¤ê³„ê°€ ê¹”ë”í•©ë‹ˆë‹¤. HATEOASë„ ê³ ë ¤í•´ë³´ì„¸ìš”.','A',10,'Swagger UI í¬í•¨í•˜ì—¬ ìž‘ì„±í–ˆìŠµë‹ˆë‹¤','https://github.com/ganada/todo-api-design','2026-03-31 23:00:00.000000',1),(2,NULL,NULL,NULL,10,'ê¸°ë³¸ CRUD ì™„ì„±, ì˜ˆì™¸ ì²˜ë¦¬ ì¶”ê°€ ì˜ˆì •','https://github.com/ganada/spring-todo-crud','2026-04-07 22:00:00.000000',2),(7,'2026-04-02 10:00:00.000000','API 설계가 깔끔합니다. HATEOAS도 고려해보세요.','A',8,'Swagger UI 포함하여 작성했습니다','https://github.com/qwer/todo-api-design','2026-03-31 23:00:00.000000',13),(8,NULL,NULL,NULL,8,'기본 CRUD 완성, 예외 처리 추가 예정','https://github.com/qwer/spring-todo-crud','2026-04-07 22:00:00.000000',14);
/*!40000 ALTER TABLE `assignment_submissions` ENABLE KEYS */;

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
-- Dumping data for table `assignments`
--

/*!40000 ALTER TABLE `assignments` DISABLE KEYS */;
INSERT INTO `assignments` VALUES (1,'2026-04-11 18:56:43.000000','Todo ì•±ì˜ RESTful APIë¥¼ ì„¤ê³„í•˜ê³  Swagger ë¬¸ì„œë¥¼ ìž‘ì„±í•˜ì„¸ìš”','2026-04-01',1,1,'https://swagger.io/docs/','REVIEWED','REST API ì„¤ê³„ ê³¼ì œ','TASK','2026-04-11 18:56:43.000000'),(2,'2026-04-11 18:56:43.000000','Todo ì•±ì˜ CRUD APIë¥¼ Spring Bootë¡œ êµ¬í˜„í•˜ì„¸ìš”','2026-04-08',1,1,'https://github.com/example/spring-todo','SUBMITTED','Spring Boot CRUD êµ¬í˜„','CODE_REVIEW','2026-04-11 18:56:43.000000'),(3,'2026-04-11 18:56:43.000000','1:N, N:M ì—°ê´€ê´€ê³„ë¥¼ ë§¤í•‘í•˜ê³  í…ŒìŠ¤íŠ¸ë¥¼ ìž‘ì„±í•˜ì„¸ìš”','2026-04-15',1,1,'','ASSIGNED','JPA ì—°ê´€ê´€ê³„ ë§¤í•‘','TASK','2026-04-11 18:56:43.000000'),(4,'2026-04-11 18:56:43.000000','JWT ê¸°ë°˜ ì¸ì¦/ì¸ê°€ë¥¼ êµ¬í˜„í•˜ì„¸ìš”','2026-04-22',1,1,'','ASSIGNED','Spring Security JWT ì¸ì¦','TASK','2026-04-11 18:56:43.000000'),(13,'2026-04-11 19:26:27.000000','Todo 앱의 RESTful API를 설계하고 Swagger 문서를 작성하세요','2026-04-01',4,1,'[\"https://swagger.io/docs/\"]','REVIEWED','REST API 설계 과제','TASK','2026-04-11 19:26:27.000000'),(14,'2026-04-11 19:26:27.000000','Todo 앱의 CRUD API를 Spring Boot로 구현하세요','2026-04-08',4,1,'[\"https://github.com/example/spring-todo\"]','SUBMITTED','Spring Boot CRUD 구현','CODE_REVIEW','2026-04-11 19:26:27.000000'),(15,'2026-04-11 19:26:27.000000','1:N, N:M 연관관계를 매핑하고 테스트를 작성하세요','2026-04-15',4,1,'[]','ASSIGNED','JPA 연관관계 매핑','TASK','2026-04-11 19:26:27.000000'),(16,'2026-04-11 19:26:27.000000','JWT 기반 인증/인가를 구현하세요','2026-04-22',4,1,'[]','ASSIGNED','Spring Security JWT 인증','TASK','2026-04-11 19:26:27.000000');
/*!40000 ALTER TABLE `assignments` ENABLE KEYS */;

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
-- Dumping data for table `comments`
--

/*!40000 ALTER TABLE `comments` DISABLE KEYS */;
/*!40000 ALTER TABLE `comments` ENABLE KEYS */;

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
-- Dumping data for table `curriculum_weeks`
--

/*!40000 ALTER TABLE `curriculum_weeks` DISABLE KEYS */;
INSERT INTO `curriculum_weeks` VALUES (9,'2026-03-28 18:00:00.000000','ê°ì²´ì§€í–¥ í•µì‹¬ ê°œë…ê³¼ Java 17 ê¸°ëŠ¥',_binary '','https://docs.oracle.com/en/java/','Java ê¸°ì´ˆ ë³µìŠµ','OOP 4ëŒ€ ì›ì¹™,Java 17 Records,Sealed Classes',1,3),(10,'2026-04-04 18:00:00.000000','Spring Boot í”„ë¡œì íŠ¸ êµ¬ì„±ê³¼ DI/IoC',_binary '','https://spring.io/guides','Spring Boot ê¸°ì´ˆ','Spring IoC,ì˜ì¡´ì„± ì£¼ìž…,Bean ìƒëª…ì£¼ê¸°',2,3),(11,'2026-04-11 18:00:00.000000','RESTful API ì„¤ê³„ì™€ êµ¬í˜„',_binary '','https://spring.io/guides/gs/rest-service/','Spring MVC & REST API','REST ì„¤ê³„ ì›ì¹™,Controller íŒ¨í„´,ì˜ˆì™¸ ì²˜ë¦¬',3,3),(12,NULL,'JPA ì—”í‹°í‹° ë§¤í•‘ê³¼ ì¿¼ë¦¬ ìž‘ì„±',_binary '\0','https://docs.spring.io/spring-data/jpa/','JPA & ë°ì´í„° ì ‘ê·¼','Entity ë§¤í•‘,ì—°ê´€ê´€ê³„,JPQL,QueryDSL',4,3),(13,NULL,'Spring Securityì™€ JWT ì¸ì¦',_binary '\0','','ì¸ì¦ & ë³´ì•ˆ','Spring Security,JWT,OAuth2',5,3),(14,NULL,'ë‹¨ìœ„ í…ŒìŠ¤íŠ¸ì™€ í†µí•© í…ŒìŠ¤íŠ¸',_binary '\0','','í…ŒìŠ¤íŠ¸ & CI/CD','JUnit 5,Mockito,Testcontainers',6,3),(15,NULL,'ìºì‹±, ì¸ë±ì‹±, ë¹„ë™ê¸° ì²˜ë¦¬',_binary '\0','','ì„±ëŠ¥ ìµœì í™”','Redis ìºì‹±,DB ì¸ë±ìŠ¤,@Async',7,3),(16,NULL,'Docker, AWS ë°°í¬',_binary '\0','','ë°°í¬ & ìš´ì˜','Docker Compose,AWS EC2,ëª¨ë‹ˆí„°ë§',8,3),(33,'2026-03-28 18:00:00.000000','객체지향 핵심 개념과 Java 17 기능',_binary '','[\"https://docs.oracle.com/en/java/\"]','Java 기초 복습','[\"OOP 4대 원칙\",\"Java 17 Records\",\"Sealed Classes\"]',1,6),(34,'2026-04-04 18:00:00.000000','Spring Boot 프로젝트 구성과 DI/IoC',_binary '','[\"https://spring.io/guides\"]','Spring Boot 기초','[\"Spring IoC\",\"의존성 주입\",\"Bean 생명주기\"]',2,6),(35,'2026-04-11 18:00:00.000000','RESTful API 설계와 구현',_binary '','[\"https://spring.io/guides/gs/rest-service/\"]','Spring MVC & REST API','[\"REST 설계 원칙\",\"Controller 패턴\",\"예외 처리\"]',3,6),(36,NULL,'JPA 엔티티 매핑과 쿼리 작성',_binary '\0','[\"https://docs.spring.io/spring-data/jpa/\"]','JPA & 데이터 접근','[\"Entity 매핑\",\"연관관계\",\"JPQL\",\"QueryDSL\"]',4,6),(37,NULL,'Spring Security와 JWT 인증',_binary '\0','[]','인증 & 보안','[\"Spring Security\",\"JWT\",\"OAuth2\"]',5,6),(38,NULL,'단위 테스트와 통합 테스트',_binary '\0','[]','테스트 & CI/CD','[\"JUnit 5\",\"Mockito\",\"Testcontainers\"]',6,6),(39,NULL,'캐싱, 인덱싱, 비동기 처리',_binary '\0','[]','성능 최적화','[\"Redis 캐싱\",\"DB 인덱스\",\"@Async\"]',7,6),(40,NULL,'Docker, AWS 배포',_binary '\0','[]','배포 & 운영','[\"Docker Compose\",\"AWS EC2\",\"모니터링\"]',8,6);
/*!40000 ALTER TABLE `curriculum_weeks` ENABLE KEYS */;

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
-- Dumping data for table `curriculums`
--

/*!40000 ALTER TABLE `curriculums` DISABLE KEYS */;
INSERT INTO `curriculums` VALUES (3,'2026-04-11 18:56:43.000000','Spring Bootì™€ JPAë¥¼ í™œìš©í•œ ë°±ì—”ë“œ ê°œë°œ ì‹¬í™” ê³¼ì •',NULL,'2026-05-18',1,'2026-03-24','Java Backend ë§ˆìŠ¤í„° ê³¼ì •',8,'2026-04-11 18:56:43.000000'),(6,'2026-04-11 19:26:27.000000','Spring Boot와 JPA를 활용한 백엔드 개발 심화 과정',NULL,'2026-05-18',4,'2026-03-24','Java Backend 마스터 과정',8,'2026-04-11 19:26:27.000000');
/*!40000 ALTER TABLE `curriculums` ENABLE KEYS */;

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
-- Dumping data for table `learning_notes`
--

/*!40000 ALTER TABLE `learning_notes` DISABLE KEYS */;
INSERT INTO `learning_notes` VALUES (1,10,'ì˜¤ëŠ˜ ì„¸ì…˜ì—ì„œ SOLID ì›ì¹™ì— ëŒ€í•´ ê¹Šì´ ìžˆê²Œ ë°°ì› ë‹¤. íŠ¹ížˆ LSPì™€ ISPì˜ ì‹¤ì œ ì ìš© ì‚¬ë¡€ê°€ ì¸ìƒì ì´ì—ˆë‹¤. ë‹¤ìŒì—ëŠ” ì‹¤ì œ ì½”ë“œì—ì„œ ì´ ì›ì¹™ë“¤ì´ ì–´ë–»ê²Œ ì ìš©ë˜ëŠ”ì§€ ë” ì—°ìŠµí•´ë´ì•¼ê² ë‹¤.','2026-04-11 18:56:43.000000',1,4,NULL,'1ì£¼ì°¨ ì„¸ì…˜ ì •ë¦¬ - OOP í•µì‹¬','SESSION_REVIEW','2026-04-11 18:56:43.000000',1),(2,10,'Spring Bootì˜ ìžë™ ì„¤ì • ë©”ì»¤ë‹ˆì¦˜ì„ ì´í•´í•˜ê²Œ ë˜ì—ˆë‹¤. @SpringBootApplication ì–´ë…¸í…Œì´ì…˜ì´ @Configuration, @EnableAutoConfiguration, @ComponentScanì„ í¬í•¨í•˜ê³  ìžˆë‹¤ëŠ” ê²ƒì„ ì•Œê²Œ ëë‹¤.','2026-04-11 18:56:43.000000',1,3,NULL,'2ì£¼ì°¨ í•™ìŠµì¼ì§€ - Spring Boot','WEEKLY_JOURNAL','2026-04-11 18:56:43.000000',2),(3,10,'REST API ì„¤ê³„ ì›ì¹™ê³¼ HTTP ë©”ì„œë“œ ì‚¬ìš©ë²•ì„ ì •ë¦¬í–ˆë‹¤. Richardson Maturity Model Level 2ê¹Œì§€ëŠ” í™•ì‹¤ížˆ ì´í•´í–ˆê³ , Level 3 HATEOASëŠ” ì¶”ê°€ í•™ìŠµì´ í•„ìš”í•˜ë‹¤.','2026-04-11 18:56:43.000000',1,5,NULL,'3ì£¼ì°¨ ì„¸ì…˜ ì •ë¦¬ - REST API','SESSION_REVIEW','2026-04-11 18:56:43.000000',3),(10,8,'오늘 세션에서 SOLID 원칙에 대해 깊이 있게 배웠다. 특히 LSP와 ISP의 실제 적용 사례가 인상적이었다. 다음에는 실제 코드에서 이 원칙들이 어떻게 적용되는지 더 연습해봐야겠다.','2026-04-11 19:26:27.000000',4,4,NULL,'1주차 세션 정리 - OOP 핵심','SESSION_REVIEW','2026-04-11 19:26:27.000000',1),(11,8,'Spring Boot의 자동 설정 메커니즘을 이해하게 되었다. @SpringBootApplication 어노테이션이 @Configuration, @EnableAutoConfiguration, @ComponentScan을 포함하고 있다는 것을 알게 됐다.','2026-04-11 19:26:27.000000',4,3,NULL,'2주차 학습일지 - Spring Boot','WEEKLY_JOURNAL','2026-04-11 19:26:27.000000',2),(12,8,'REST API 설계 원칙과 HTTP 메서드 사용법을 정리했다. Richardson Maturity Model Level 2까지는 확실히 이해했고, Level 3 HATEOAS는 추가 학습이 필요하다.','2026-04-11 19:26:27.000000',4,5,NULL,'3주차 세션 정리 - REST API','SESSION_REVIEW','2026-04-11 19:26:27.000000',3);
/*!40000 ALTER TABLE `learning_notes` ENABLE KEYS */;

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
-- Dumping data for table `matchings`
--

/*!40000 ALTER TABLE `matchings` DISABLE KEYS */;
INSERT INTO `matchings` VALUES (1,'Java Backend','2026-04-10 01:32:57.066222',NULL,NULL,'ACCEPTED','2026-04-10 01:32:57.066222',10,1,NULL,1,0,NULL),(4,'Backend','2026-04-11 19:26:27.000000','Java 백엔드 개발을 배우고 싶습니다',NULL,'ACCEPTED','2026-04-11 19:26:27.000000',8,1,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `matchings` ENABLE KEYS */;

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
-- Dumping data for table `mentor_availabilities`
--

/*!40000 ALTER TABLE `mentor_availabilities` DISABLE KEYS */;
/*!40000 ALTER TABLE `mentor_availabilities` ENABLE KEYS */;

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
-- Dumping data for table `mentor_profiles`
--

/*!40000 ALTER TABLE `mentor_profiles` DISABLE KEYS */;
INSERT INTO `mentor_profiles` VALUES (1,'Java/Spring 전문 멘토입니다. 대규모 서비스 설계 경험이 풍부합니다.',8,'네이버','2026-04-04 11:19:06.999777','[\"Java\",\"Spring\"]','APPROVED','2026-04-04 11:19:06.999777',1),(2,'Spring 기반 MSA 설계와 DevOps 파이프라인 구축 경험이 있습니다.',5,'카카오','2026-04-04 11:19:07.019661','[\"Spring\",\"DevOps\"]','APPROVED','2026-04-04 11:19:07.019661',2),(3,'React와 Node.js 풀스택 개발 멘토입니다.',6,'라인','2026-04-04 11:19:07.035652','[\"React\",\"Node.js\"]','APPROVED','2026-04-04 11:19:07.035652',3),(4,'Python 백엔드와 알고리즘 전문 멘토입니다.',7,'쿠팡','2026-04-04 11:19:07.050927','[\"Python\",\"Algorithm\"]','APPROVED','2026-04-04 11:19:07.050927',4),(5,'10년차 풀스택 개발자입니다. 서비스 전체 아키텍처 설계를 도와드립니다.',10,'토스','2026-04-04 11:19:07.066550','[\"Java\",\"React\",\"Spring\"]','APPROVED','2026-04-04 11:19:07.066550',5);
/*!40000 ALTER TABLE `mentor_profiles` ENABLE KEYS */;

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
-- Dumping data for table `mentor_time_slots`
--

/*!40000 ALTER TABLE `mentor_time_slots` DISABLE KEYS */;
INSERT INTO `mentor_time_slots` VALUES (1,'2026-04-11 18:56:43.000000','20:00:00.000000',_binary '',1,1,'2026-04-14','19:00:00.000000'),(2,'2026-04-11 18:56:43.000000','21:00:00.000000',_binary '\0',1,1,'2026-04-16','20:00:00.000000'),(3,'2026-04-11 18:56:43.000000','20:00:00.000000',_binary '\0',1,1,'2026-04-18','19:00:00.000000'),(4,'2026-04-11 18:56:43.000000','20:00:00.000000',_binary '',1,1,'2026-04-21','19:00:00.000000'),(5,'2026-04-11 18:56:43.000000','20:30:00.000000',_binary '\0',1,1,'2026-04-23','19:00:00.000000'),(6,'2026-04-11 18:56:43.000000','21:00:00.000000',_binary '\0',1,1,'2026-04-25','20:00:00.000000'),(19,'2026-04-11 19:26:27.000000','20:00:00.000000',_binary '',4,1,'2026-04-14','19:00:00.000000'),(20,'2026-04-11 19:26:27.000000','21:00:00.000000',_binary '\0',4,1,'2026-04-16','20:00:00.000000'),(21,'2026-04-11 19:26:27.000000','20:00:00.000000',_binary '\0',4,1,'2026-04-18','19:00:00.000000'),(22,'2026-04-11 19:26:27.000000','20:00:00.000000',_binary '',4,1,'2026-04-21','19:00:00.000000'),(23,'2026-04-11 19:26:27.000000','20:30:00.000000',_binary '\0',4,1,'2026-04-23','19:00:00.000000'),(24,'2026-04-11 19:26:27.000000','21:00:00.000000',_binary '\0',4,1,'2026-04-25','20:00:00.000000');
/*!40000 ALTER TABLE `mentor_time_slots` ENABLE KEYS */;

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
-- Dumping data for table `mentoring_sessions`
--

/*!40000 ALTER TABLE `mentoring_sessions` DISABLE KEYS */;
INSERT INTO `mentoring_sessions` VALUES (1,NULL,'Backend','2026-04-11 18:56:43.000000','20:00:00.000000',1,NULL,'ì²« ì„¸ì…˜ - OOP ê°œë… ë¦¬ë·°',10,1,'2026-03-25','19:00:00.000000','COMPLETED','2026-04-11 18:56:43.000000'),(2,NULL,'Backend','2026-04-11 18:56:43.000000','20:00:00.000000',1,NULL,'Spring Boot í”„ë¡œì íŠ¸ ì…‹ì—… ì‹¤ìŠµ',10,1,'2026-04-01','19:00:00.000000','COMPLETED','2026-04-11 18:56:43.000000'),(3,NULL,'Backend','2026-04-11 18:56:43.000000','20:00:00.000000',1,NULL,'REST API ì„¤ê³„ ë¦¬ë·°',10,1,'2026-04-08','19:00:00.000000','COMPLETED','2026-04-11 18:56:43.000000'),(4,NULL,'Backend','2026-04-11 18:56:43.000000','20:00:00.000000',1,NULL,'JPA ì—”í‹°í‹° ë§¤í•‘ ì‹¤ìŠµ',10,1,'2026-04-14','19:00:00.000000','SCHEDULED','2026-04-11 18:56:43.000000'),(5,NULL,'Backend','2026-04-11 18:56:43.000000','20:00:00.000000',1,NULL,'QueryDSL ì‹¬í™”',10,1,'2026-04-21','19:00:00.000000','SCHEDULED','2026-04-11 18:56:43.000000'),(16,NULL,'Backend','2026-04-11 19:26:27.000000','20:00:00.000000',4,NULL,'첫 세션 - OOP 개념 리뷰',8,1,'2026-03-25','19:00:00.000000','COMPLETED','2026-04-11 19:26:27.000000'),(17,NULL,'Backend','2026-04-11 19:26:27.000000','20:00:00.000000',4,NULL,'Spring Boot 프로젝트 셋업 실습',8,1,'2026-04-01','19:00:00.000000','COMPLETED','2026-04-11 19:26:27.000000'),(18,NULL,'Backend','2026-04-11 19:26:27.000000','20:00:00.000000',4,NULL,'REST API 설계 리뷰',8,1,'2026-04-08','19:00:00.000000','COMPLETED','2026-04-11 19:26:27.000000'),(19,NULL,'Backend','2026-04-11 19:26:27.000000','20:00:00.000000',4,NULL,'JPA 엔티티 매핑 실습',8,1,'2026-04-14','19:00:00.000000','SCHEDULED','2026-04-11 19:26:27.000000'),(20,NULL,'Backend','2026-04-11 19:26:27.000000','20:00:00.000000',4,NULL,'QueryDSL 심화',8,1,'2026-04-21','19:00:00.000000','SCHEDULED','2026-04-11 19:26:27.000000');
/*!40000 ALTER TABLE `mentoring_sessions` ENABLE KEYS */;

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
-- Dumping data for table `mock_interviews`
--

/*!40000 ALTER TABLE `mock_interviews` DISABLE KEYS */;
/*!40000 ALTER TABLE `mock_interviews` ENABLE KEYS */;

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
-- Dumping data for table `note_comments`
--

/*!40000 ALTER TABLE `note_comments` DISABLE KEYS */;
INSERT INTO `note_comments` VALUES (1,1,'SOLID ì›ì¹™ ì •ë¦¬ë¥¼ ìž˜ í•˜ì…¨ë„¤ìš”. DIP(ì˜ì¡´ì„± ì—­ì „)ë„ Springì—ì„œ ì–´ë–»ê²Œ ì ìš©ë˜ëŠ”ì§€ ë‹¤ìŒ ì„¸ì…˜ì—ì„œ í•¨ê»˜ ë³´ê² ìŠµë‹ˆë‹¤.','2026-04-11 18:56:43.000000',1),(2,1,'Richardson Maturity Model ì •ë¦¬ê°€ ì¢‹ìŠµë‹ˆë‹¤. Level 3ëŠ” ì‹¤ë¬´ì—ì„œëŠ” ìž˜ ì•ˆ ì“°ì´ë‹ˆ Level 2ê¹Œì§€ í™•ì‹¤ížˆ ìµížˆëŠ” ê²Œ ì¢‹ìŠµë‹ˆë‹¤.','2026-04-11 18:56:43.000000',3),(7,1,'SOLID 원칙 정리를 잘 하셨네요. DIP(의존성 역전)도 Spring에서 어떻게 적용되는지 다음 세션에서 함께 보겠습니다.','2026-04-11 19:26:27.000000',10),(8,1,'Richardson Maturity Model 정리가 좋습니다. Level 3는 실무에서는 잘 안 쓰이니 Level 2까지 확실히 익히는 게 좋습니다.','2026-04-11 19:26:27.000000',12);
/*!40000 ALTER TABLE `note_comments` ENABLE KEYS */;

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
-- Dumping data for table `payments`
--

/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
INSERT INTO `payments` VALUES (1,990000,NULL,'2026-04-10 01:32:57.077053',1,'SEED-1775784777-1','SEEDKEY-1775784777-1','CONFIRMED','2026-04-10 01:32:57.077053',10,1,'IMMEDIATE',0,0,3,0),(4,300000,NULL,'2026-04-11 19:26:27.000000',4,'ORDER-4-1775935587','PAY-4','CONFIRMED','2026-04-11 19:26:27.000000',8,0,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;

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
-- Dumping data for table `post_likes`
--

/*!40000 ALTER TABLE `post_likes` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_likes` ENABLE KEYS */;

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
-- Dumping data for table `posts`
--

/*!40000 ALTER TABLE `posts` DISABLE KEYS */;
/*!40000 ALTER TABLE `posts` ENABLE KEYS */;

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
-- Dumping data for table `questions`
--

/*!40000 ALTER TABLE `questions` DISABLE KEYS */;
INSERT INTO `questions` VALUES (1,'Java에서 정수형 변수를 선언하는 올바른 방법은?',0,'2026-04-04 11:19:05.914183','[\"int number = 10;\",\"number int = 10;\",\"integer number = 10;\",\"var: int number = 10;\"]',1,10,1),(2,'Java에서 문자열을 비교할 때 올바른 방법은?',1,'2026-04-04 11:19:05.983905','[\"str1 == str2\",\"str1.equals(str2)\",\"str1.compare(str2)\",\"str1.match(str2)\"]',2,10,1),(3,'다음 중 Java의 기본 자료형(primitive type)이 아닌 것은?',2,'2026-04-04 11:19:05.989761','[\"int\",\"boolean\",\"String\",\"double\"]',3,10,1),(4,'Java에서 배열의 길이를 구하는 방법은?',1,'2026-04-04 11:19:05.994588','[\"arr.size()\",\"arr.length\",\"arr.count()\",\"len(arr)\"]',4,10,1),(5,'다음 코드의 출력 결과는?\nint x = 5;\nSystem.out.println(x++);',1,'2026-04-04 11:19:05.999145','[\"4\",\"5\",\"6\",\"컴파일 에러\"]',5,10,1),(6,'Java에서 상수를 선언할 때 사용하는 키워드는?',1,'2026-04-04 11:19:06.002638','[\"const\",\"final\",\"static\",\"immutable\"]',6,10,1),(7,'다음 중 반복문이 아닌 것은?',3,'2026-04-04 11:19:06.007830','[\"for\",\"while\",\"do-while\",\"switch\"]',7,10,1),(8,'Java에서 클래스를 상속할 때 사용하는 키워드는?',2,'2026-04-04 11:19:06.012082','[\"implements\",\"inherits\",\"extends\",\"super\"]',8,10,1),(9,'접근 제어자 중 같은 패키지 내에서만 접근 가능한 것은?',1,'2026-04-04 11:19:06.018228','[\"private\",\"default (접근 제어자 없음)\",\"protected\",\"public\"]',9,10,1),(10,'main 메서드의 올바른 선언은?',1,'2026-04-04 11:19:06.024150','[\"public void main(String args)\",\"public static void main(String[] args)\",\"static void main(String args[])\",\"public static main(String[] args)\"]',10,10,1),(11,'ArrayList와 LinkedList의 차이에 대한 설명으로 올바른 것은?',2,'2026-04-04 11:19:06.035507','[\"ArrayList는 삽입/삭제가 빠르다\",\"LinkedList는 인덱스 접근이 빠르다\",\"ArrayList는 인덱스 접근이 O(1)이다\",\"둘 다 동기화가 보장된다\"]',1,10,2),(12,'Java 제네릭에서 와일드카드 \'? extends Number\'의 의미는?',0,'2026-04-04 11:19:06.040486','[\"Number를 상속한 모든 타입\",\"Number만 허용\",\"Number의 부모 타입만 허용\",\"모든 타입 허용\"]',2,10,2),(13,'다음 중 함수형 인터페이스가 아닌 것은?',2,'2026-04-04 11:19:06.047037','[\"Runnable\",\"Comparator\",\"List\",\"Predicate\"]',3,10,2),(14,'Stream API에서 중간 연산(intermediate operation)이 아닌 것은?',2,'2026-04-04 11:19:06.052786','[\"filter()\",\"map()\",\"collect()\",\"sorted()\"]',4,10,2),(15,'Java에서 checked exception과 unchecked exception의 차이는?',0,'2026-04-04 11:19:06.057316','[\"checked는 컴파일 시점에 처리가 강제된다\",\"unchecked는 컴파일 시점에 처리가 강제된다\",\"둘 다 반드시 try-catch가 필요하다\",\"차이가 없다\"]',5,10,2),(16,'SOLID 원칙 중 \'O\'에 해당하는 원칙은?',1,'2026-04-04 11:19:06.061074','[\"단일 책임 원칙\",\"개방-폐쇄 원칙\",\"리스코프 치환 원칙\",\"의존 역전 원칙\"]',6,10,2),(17,'HashMap의 시간 복잡도로 올바른 것은? (평균)',1,'2026-04-04 11:19:06.067609','[\"검색 O(n), 삽입 O(n)\",\"검색 O(1), 삽입 O(1)\",\"검색 O(log n), 삽입 O(log n)\",\"검색 O(1), 삽입 O(n)\"]',7,10,2),(18,'Optional 클래스의 주요 목적은?',1,'2026-04-04 11:19:06.072468','[\"성능 향상\",\"NullPointerException 방지\",\"멀티스레드 지원\",\"직렬화 지원\"]',8,10,2),(19,'다음 코드의 결과는?\nList.of(1,2,3,4,5).stream().filter(n -> n > 2).count();',1,'2026-04-04 11:19:06.076835','[\"2\",\"3\",\"5\",\"컴파일 에러\"]',9,10,2),(20,'인터페이스에서 default 메서드의 특징으로 올바른 것은?',2,'2026-04-04 11:19:06.082333','[\"추상 메서드이다\",\"구현 클래스에서 반드시 오버라이드해야 한다\",\"메서드 본문(body)을 가질 수 있다\",\"static이어야 한다\"]',10,10,2),(21,'JVM 메모리 영역 중 객체 인스턴스가 저장되는 곳은?',1,'2026-04-04 11:19:06.090978','[\"Stack\",\"Heap\",\"Method Area\",\"PC Register\"]',1,10,3),(22,'G1 GC의 특징으로 올바르지 않은 것은?',2,'2026-04-04 11:19:06.096474','[\"Region 기반으로 힙을 관리한다\",\"STW(Stop-The-World) 시간을 예측 가능하게 한다\",\"Young/Old 영역이 물리적으로 고정되어 있다\",\"Java 9부터 기본 GC이다\"]',2,10,3),(23,'volatile 키워드의 역할은?',1,'2026-04-04 11:19:06.100889','[\"변수를 상수로 만든다\",\"변수의 가시성(visibility)을 보장한다\",\"원자성(atomicity)을 보장한다\",\"변수를 직렬화 가능하게 한다\"]',3,10,3),(24,'synchronized와 ReentrantLock의 차이로 올바른 것은?',1,'2026-04-04 11:19:06.105785','[\"synchronized가 더 유연하다\",\"ReentrantLock은 tryLock으로 타임아웃이 가능하다\",\"둘 다 인터럽트 처리가 불가능하다\",\"ReentrantLock은 암묵적 잠금이다\"]',4,10,3),(25,'싱글턴 패턴을 thread-safe하게 구현하는 가장 권장되는 방법은?',2,'2026-04-04 11:19:06.109731','[\"public static 필드\",\"synchronized 메서드\",\"LazyHolder (정적 내부 클래스)\",\"일반 private static 변수\"]',5,10,3),(26,'ConcurrentHashMap이 HashTable보다 성능이 좋은 이유는?',1,'2026-04-04 11:19:06.117230','[\"해시 함수가 더 빠르다\",\"세그먼트/버킷 단위로 락을 건다\",\"동기화를 하지 않는다\",\"LinkedList 대신 배열을 사용한다\"]',6,10,3),(27,'Java 리플렉션(Reflection)의 단점이 아닌 것은?',2,'2026-04-04 11:19:06.121525','[\"성능 오버헤드\",\"컴파일 타임 타입 체크 불가\",\"런타임에 클래스 정보 접근 가능\",\"캡슐화 위반 가능성\"]',7,10,3),(28,'CompletableFuture에서 thenApply와 thenCompose의 차이는?',1,'2026-04-04 11:19:06.126418','[\"차이 없다\",\"thenApply는 값 변환, thenCompose는 비동기 체이닝\",\"thenCompose는 동기, thenApply는 비동기\",\"thenApply만 예외 처리 가능\"]',8,10,3),(29,'클래스 로더의 동작 원칙 중 \'위임 모델(Delegation Model)\'이란?',1,'2026-04-04 11:19:06.132966','[\"자식 클래스 로더가 먼저 로딩을 시도한다\",\"부모 클래스 로더에게 먼저 위임하고, 없으면 자식이 로딩한다\",\"모든 클래스 로더가 동시에 로딩을 시도한다\",\"Application 클래스 로더만 사용한다\"]',9,10,3),(30,'Java의 String이 불변(immutable)인 주요 이유가 아닌 것은?',3,'2026-04-04 11:19:06.137836','[\"String Pool을 통한 메모리 최적화\",\"해시코드 캐싱으로 HashMap 성능 향상\",\"스레드 안전성 보장\",\"가비지 컬렉션 성능 향상\"]',10,10,3),(31,'Spring의 핵심 개념인 IoC(Inversion of Control)란?',1,'2026-04-04 11:19:06.146189','[\"개발자가 직접 객체를 생성하고 관리한다\",\"프레임워크가 객체의 생성과 생명주기를 관리한다\",\"모든 객체를 싱글턴으로 만든다\",\"인터페이스를 반드시 사용해야 한다\"]',1,10,4),(32,'Spring Boot 프로젝트에서 설정 파일의 기본 이름은?',2,'2026-04-04 11:19:06.152675','[\"config.xml\",\"settings.properties\",\"application.properties (또는 application.yml)\",\"spring.conf\"]',2,10,4),(33,'@Controller와 @RestController의 차이는?',1,'2026-04-04 11:19:06.157771','[\"차이 없다\",\"@RestController는 @ResponseBody가 포함되어 있다\",\"@Controller는 REST API 전용이다\",\"@RestController는 View를 반환한다\"]',3,10,4),(34,'Spring에서 의존성 주입(DI) 방법이 아닌 것은?',3,'2026-04-04 11:19:06.163110','[\"생성자 주입\",\"Setter 주입\",\"필드 주입\",\"Static 주입\"]',4,10,4),(35,'@Autowired 어노테이션의 역할은?',1,'2026-04-04 11:19:06.169192','[\"빈을 등록한다\",\"의존성을 자동으로 주입한다\",\"트랜잭션을 관리한다\",\"URL 매핑을 한다\"]',5,10,4),(36,'Spring Boot에서 내장 서버로 사용되는 것은?',2,'2026-04-04 11:19:06.172960','[\"Apache HTTP Server\",\"Nginx\",\"Tomcat\",\"IIS\"]',6,10,4),(37,'@RequestMapping의 HTTP 메서드별 축약 어노테이션이 아닌 것은?',2,'2026-04-04 11:19:06.178287','[\"@GetMapping\",\"@PostMapping\",\"@SendMapping\",\"@DeleteMapping\"]',7,10,4),(38,'Spring Bean의 기본 스코프는?',1,'2026-04-04 11:19:06.182651','[\"prototype\",\"singleton\",\"request\",\"session\"]',8,10,4),(39,'@Service 어노테이션의 역할은?',1,'2026-04-04 11:19:06.186502','[\"데이터베이스 접근 계층 표시\",\"비즈니스 로직 계층의 빈 등록\",\"컨트롤러 계층 표시\",\"설정 클래스 표시\"]',9,10,4),(40,'application.yml에서 서버 포트를 변경하는 설정은?',1,'2026-04-04 11:19:06.191481','[\"app.port: 9090\",\"server.port: 9090\",\"spring.port: 9090\",\"web.port: 9090\"]',10,10,4),(41,'Spring MVC에서 클라이언트 요청의 처리 순서로 올바른 것은?',1,'2026-04-04 11:19:06.201596','[\"Filter → Controller → Service → DispatcherServlet\",\"DispatcherServlet → HandlerMapping → Controller → ViewResolver\",\"Controller → DispatcherServlet → View\",\"HandlerMapping → Filter → Controller → Service\"]',1,10,5),(42,'JPA에서 @ManyToOne 관계의 기본 fetch 전략은?',1,'2026-04-04 11:19:06.204840','[\"LAZY\",\"EAGER\",\"NONE\",\"SELECT\"]',2,10,5),(43,'@Transactional(readOnly = true)의 효과는?',1,'2026-04-04 11:19:06.208094','[\"읽기도 불가능하다\",\"JPA 변경 감지(Dirty Checking)를 수행하지 않아 성능이 향상된다\",\"트랜잭션이 적용되지 않는다\",\"캐시가 비활성화된다\"]',3,10,5),(44,'Spring Security에서 비밀번호 암호화에 사용되는 인터페이스는?',1,'2026-04-04 11:19:06.211878','[\"Encoder\",\"PasswordEncoder\",\"CryptoService\",\"HashProvider\"]',4,10,5),(45,'JPA N+1 문제의 해결 방법이 아닌 것은?',3,'2026-04-04 11:19:06.216329','[\"Fetch Join\",\"@EntityGraph\",\"Batch Size 설정\",\"@Lazy 어노테이션\"]',5,10,5),(46,'AOP에서 @Around 어드바이스의 특징은?',2,'2026-04-04 11:19:06.220049','[\"메서드 실행 전에만 동작한다\",\"메서드 실행 후에만 동작한다\",\"메서드 실행 전후 모두 동작하며 실행 자체를 제어할 수 있다\",\"예외 발생 시에만 동작한다\"]',6,10,5),(47,'Spring에서 프로파일(Profile)의 용도는?',1,'2026-04-04 11:19:06.223267','[\"코드 성능 분석\",\"환경별(dev/prod) 설정 분리\",\"사용자 프로필 관리\",\"로깅 레벨 설정\"]',7,10,5),(48,'JPA에서 영속성 컨텍스트의 1차 캐시 역할은?',1,'2026-04-04 11:19:06.226969','[\"데이터베이스 쿼리 결과를 캐싱한다\",\"같은 트랜잭션 내 동일 엔티티 조회 시 DB 접근 없이 반환한다\",\"Redis와 연동하여 캐시한다\",\"모든 엔티티를 메모리에 유지한다\"]',8,10,5),(49,'@Valid와 @Validated의 차이는?',1,'2026-04-04 11:19:06.231473','[\"차이 없다\",\"@Valid는 JSR-303 표준, @Validated는 Spring 전용으로 그룹 검증을 지원한다\",\"@Validated는 Java 표준이다\",\"@Valid만 Controller에서 사용 가능하다\"]',9,10,5),(50,'ResponseEntity를 사용하는 이유는?',1,'2026-04-04 11:19:06.236398','[\"성능 향상\",\"HTTP 상태 코드와 헤더를 직접 제어할 수 있다\",\"자동으로 JSON 변환이 된다\",\"보안이 강화된다\"]',10,10,5),(51,'트랜잭션 전파 속성 REQUIRES_NEW의 동작은?',1,'2026-04-04 11:19:06.243126','[\"기존 트랜잭션에 참여한다\",\"항상 새로운 트랜잭션을 시작하고 기존 트랜잭션을 보류한다\",\"트랜잭션 없이 실행한다\",\"기존 트랜잭션이 없으면 예외를 발생시킨다\"]',1,10,6),(52,'트랜잭션 격리 수준 중 Phantom Read를 방지하는 최소 수준은?',3,'2026-04-04 11:19:06.247138','[\"READ_UNCOMMITTED\",\"READ_COMMITTED\",\"REPEATABLE_READ\",\"SERIALIZABLE\"]',2,10,6),(53,'JPA에서 OSIV(Open Session In View)를 false로 설정하는 이유는?',1,'2026-04-04 11:19:06.253294','[\"성능 향상을 위해\",\"영속성 컨텍스트를 트랜잭션 범위로 제한하여 지연로딩 문제를 명확히 하기 위해\",\"보안을 위해\",\"메모리 절약을 위해\"]',3,10,6),(54,'Spring에서 @Async의 주의사항으로 올바른 것은?',2,'2026-04-04 11:19:06.256512','[\"같은 클래스 내부 호출에서도 동작한다\",\"별도의 설정 없이 사용 가능하다\",\"프록시 기반이므로 같은 클래스 내부 호출에서는 동작하지 않는다\",\"반드시 void 반환 타입이어야 한다\"]',4,10,6),(55,'MSA에서 서비스 간 데이터 일관성을 위한 패턴은?',1,'2026-04-04 11:19:06.259320','[\"2PC만 사용\",\"SAGA 패턴\",\"모든 서비스가 같은 DB 사용\",\"캐시로 해결\"]',5,10,6),(56,'Spring Batch의 핵심 구성요소가 아닌 것은?',3,'2026-04-04 11:19:06.263095','[\"Job\",\"Step\",\"ItemReader/ItemWriter\",\"DispatcherServlet\"]',6,10,6),(57,'JPA에서 벌크 연산(Bulk Operation) 후 주의해야 할 점은?',1,'2026-04-04 11:19:06.267384','[\"커넥션을 닫아야 한다\",\"영속성 컨텍스트를 초기화해야 한다\",\"인덱스를 재구성해야 한다\",\"트랜잭션을 수동 커밋해야 한다\"]',7,10,6),(58,'Circuit Breaker 패턴의 목적은?',1,'2026-04-04 11:19:06.271091','[\"네트워크 암호화\",\"장애가 전파되는 것을 방지하고 빠르게 실패를 반환한다\",\"로드 밸런싱\",\"서비스 디스커버리\"]',8,10,6),(59,'Spring Cache에서 @CacheEvict의 역할은?',1,'2026-04-04 11:19:06.276905','[\"캐시에 데이터를 저장한다\",\"캐시에서 데이터를 삭제한다\",\"캐시를 생성한다\",\"캐시 상태를 조회한다\"]',9,10,6),(60,'QueryDSL을 JPA와 함께 사용하는 이유로 올바르지 않은 것은?',3,'2026-04-04 11:19:06.282857','[\"컴파일 시점에 쿼리 오류를 검출할 수 있다\",\"동적 쿼리 작성이 용이하다\",\"타입 안전한 쿼리를 작성할 수 있다\",\"SQL 성능이 자동으로 최적화된다\"]',10,10,6),(61,'React에서 컴포넌트의 상태를 관리하기 위해 사용하는 Hook은?',2,'2026-04-04 11:19:06.296063','[\"useEffect\",\"useContext\",\"useState\",\"useRef\"]',1,10,7),(62,'JSX에서 JavaScript 표현식을 사용할 때 감싸는 기호는?',1,'2026-04-04 11:19:06.302020','[\"( )\",\"{ }\",\"[ ]\",\"< >\"]',2,10,7),(63,'React 컴포넌트에서 부모로부터 데이터를 전달받는 방법은?',1,'2026-04-04 11:19:06.306473','[\"state\",\"props\",\"context\",\"ref\"]',3,10,7),(64,'다음 중 올바른 이벤트 핸들링 방법은?',1,'2026-04-04 11:19:06.310173','[\"<button onclick=\'handleClick()\'>\",\"<button onClick={handleClick}>\",\"<button on-click={handleClick}>\",\"<button click={handleClick}>\"]',4,10,7),(65,'React에서 리스트를 렌더링할 때 각 요소에 필요한 속성은?',2,'2026-04-04 11:19:06.316322','[\"id\",\"name\",\"key\",\"index\"]',5,10,7),(66,'함수형 컴포넌트의 올바른 선언 방법은?',1,'2026-04-04 11:19:06.322050','[\"function App { return <div/> }\",\"function App() { return <div/> }\",\"class App() { return <div/> }\",\"def App(): return <div/>\"]',6,10,7),(67,'React에서 조건부 렌더링에 사용할 수 없는 방법은?',2,'2026-04-04 11:19:06.334057','[\"삼항 연산자 (? :)\",\"&& 연산자\",\"if-else (JSX 내부에서 직접)\",\"조건 함수 호출\"]',7,10,7),(68,'useState의 반환값은?',2,'2026-04-04 11:19:06.339528','[\"현재 상태값만\",\"상태 변경 함수만\",\"[현재 상태값, 상태 변경 함수]\",\"{state, setState}\"]',8,10,7),(69,'React 프로젝트를 생성할 때 사용하는 도구가 아닌 것은?',3,'2026-04-04 11:19:06.343340','[\"create-react-app\",\"Vite\",\"Next.js\",\"Maven\"]',9,10,7),(70,'컴포넌트가 화면에서 사라질 때를 뜻하는 용어는?',2,'2026-04-04 11:19:06.346606','[\"Mounting\",\"Updating\",\"Unmounting\",\"Rendering\"]',10,10,7),(71,'useEffect의 의존성 배열(dependency array)이 빈 배열 []일 때 동작은?',1,'2026-04-04 11:19:06.353717','[\"매 렌더링마다 실행\",\"컴포넌트 마운트 시 1회만 실행\",\"상태 변경 시마다 실행\",\"실행되지 않는다\"]',1,10,8),(72,'React.memo의 역할은?',1,'2026-04-04 11:19:06.357088','[\"메모리를 절약한다\",\"props가 변경되지 않으면 리렌더링을 방지한다\",\"state를 캐싱한다\",\"이벤트 핸들러를 최적화한다\"]',2,10,8),(73,'Custom Hook의 이름 규칙은?',1,'2026-04-04 11:19:06.360867','[\"get으로 시작\",\"use로 시작\",\"hook으로 시작\",\"규칙 없음\"]',3,10,8),(74,'useCallback과 useMemo의 차이는?',1,'2026-04-04 11:19:06.364188','[\"차이 없다\",\"useCallback은 함수를 메모이제이션, useMemo는 값을 메모이제이션\",\"useMemo는 함수를 메모이제이션, useCallback은 값을 메모이제이션\",\"useCallback은 클래스 컴포넌트 전용\"]',4,10,8),(75,'React Router에서 동적 경로 파라미터를 가져오는 Hook은?',1,'2026-04-04 11:19:06.367443','[\"useRouter()\",\"useParams()\",\"useQuery()\",\"useLocation()\"]',5,10,8),(76,'Context API의 단점은?',1,'2026-04-04 11:19:06.372535','[\"사용법이 어렵다\",\"Provider 값이 변경되면 모든 Consumer가 리렌더링된다\",\"함수형 컴포넌트에서 사용 불가\",\"전역 상태 관리가 불가능하다\"]',6,10,8),(77,'useRef의 용도가 아닌 것은?',2,'2026-04-04 11:19:06.377477','[\"DOM 요소에 직접 접근\",\"렌더링 간 값을 유지 (리렌더링 없이)\",\"상태를 변경하고 리렌더링을 트리거\",\"이전 값을 저장\"]',7,10,8),(78,'비동기 데이터 페칭 시 useEffect에서 async를 직접 사용할 수 없는 이유는?',1,'2026-04-04 11:19:06.383003','[\"문법 오류이다\",\"useEffect의 콜백은 cleanup 함수만 반환해야 하는데, async는 Promise를 반환하기 때문\",\"React가 async를 지원하지 않는다\",\"성능 문제 때문\"]',8,10,8),(79,'React에서 폼 입력을 관리하는 두 가지 방식은?',1,'2026-04-04 11:19:06.388374','[\"Client/Server\",\"Controlled/Uncontrolled\",\"Sync/Async\",\"Static/Dynamic\"]',9,10,8),(80,'Error Boundary의 특징으로 올바른 것은?',2,'2026-04-04 11:19:06.392753','[\"함수형 컴포넌트에서만 사용 가능하다\",\"이벤트 핸들러의 에러도 잡는다\",\"클래스 컴포넌트로만 구현 가능하다 (componentDidCatch)\",\"비동기 에러도 자동으로 잡는다\"]',10,10,8),(81,'React의 가상 DOM(Virtual DOM)이 성능에 기여하는 방식은?',1,'2026-04-04 11:19:06.400484','[\"DOM을 사용하지 않는다\",\"변경된 부분만 계산(diff)하여 실제 DOM에 최소한의 업데이트를 적용한다\",\"모든 DOM을 매번 다시 생성한다\",\"WebWorker에서 DOM을 처리한다\"]',1,10,9),(82,'React Fiber 아키텍처의 핵심 개선점은?',1,'2026-04-04 11:19:06.404785','[\"번들 사이즈 감소\",\"렌더링 작업을 분할(time-slicing)하여 우선순위 기반으로 처리할 수 있다\",\"TypeScript 지원\",\"CSS-in-JS 지원\"]',2,10,9),(83,'React Server Components(RSC)의 특징이 아닌 것은?',2,'2026-04-04 11:19:06.409294','[\"서버에서만 실행된다\",\"클라이언트 번들에 포함되지 않는다\",\"useState를 사용할 수 있다\",\"데이터베이스에 직접 접근 가능하다\"]',3,10,9),(84,'Suspense의 역할은?',1,'2026-04-04 11:19:06.441499','[\"에러를 처리한다\",\"비동기 작업이 완료될 때까지 fallback UI를 보여준다\",\"컴포넌트를 지연 로딩한다\",\"상태를 초기화한다\"]',4,10,9),(85,'Next.js에서 SSR, SSG, ISR의 공통점은?',1,'2026-04-04 11:19:06.457596','[\"모두 클라이언트에서 렌더링된다\",\"모두 서버 측에서 HTML을 생성한다\",\"모두 실시간으로 데이터를 반영한다\",\"모두 CDN 캐싱이 불가능하다\"]',5,10,9),(86,'Concurrent Mode에서 useTransition의 용도는?',1,'2026-04-04 11:19:06.461882','[\"페이지 전환 애니메이션\",\"우선순위가 낮은 상태 업데이트를 표시하여 UI 블로킹을 방지한다\",\"데이터베이스 트랜잭션 관리\",\"CSS 트랜지션 제어\"]',6,10,9),(87,'React의 재조정(Reconciliation) 알고리즘에서 key가 중요한 이유는?',1,'2026-04-04 11:19:06.466133','[\"스타일링을 위해\",\"리스트 요소의 동일성을 판별하여 불필요한 DOM 조작을 최소화하기 위해\",\"이벤트 바인딩을 위해\",\"접근성(a11y)을 위해\"]',7,10,9),(88,'Hydration이란?',1,'2026-04-04 11:19:06.470087','[\"CSS 적용 과정\",\"서버에서 렌더링된 HTML에 클라이언트 JavaScript 이벤트와 상태를 연결하는 과정\",\"데이터 직렬화\",\"메모리 해제\"]',8,10,9),(89,'React에서 코드 스플리팅(Code Splitting)을 구현하는 방법은?',1,'2026-04-04 11:19:06.474906','[\"React.memo\",\"React.lazy + Suspense\",\"useReducer\",\"React.Fragment\"]',9,10,9),(90,'Streaming SSR의 장점은?',1,'2026-04-04 11:19:06.478738','[\"번들 사이즈가 줄어든다\",\"전체 페이지를 기다리지 않고 준비된 부분부터 점진적으로 전송한다\",\"SEO가 불필요해진다\",\"CDN 캐싱이 가능해진다\"]',10,10,9),(91,'Python에서 리스트의 마지막 요소에 접근하는 방법은?',1,'2026-04-04 11:19:06.487585','[\"list[0]\",\"list[-1]\",\"list.last()\",\"list.end()\"]',1,10,10),(92,'Python에서 딕셔너리를 생성하는 올바른 방법은?',1,'2026-04-04 11:19:06.492480','[\"d = [key: value]\",\"d = {key: value}\",\"d = (key, value)\",\"d = <key, value>\"]',2,10,10),(93,'다음 중 Python의 불변(immutable) 자료형은?',2,'2026-04-04 11:19:06.496781','[\"list\",\"dict\",\"tuple\",\"set\"]',3,10,10),(94,'Python에서 여러 줄 문자열을 표현하는 방법은?',2,'2026-04-04 11:19:06.503164','[\"\\\"\\\"\\\"텍스트\\\"\\\"\\\"\",\"\'\'\'텍스트\'\'\'\",\"\\\"\\\"\\\"텍스트\\\"\\\"\\\" 또는 \'\'\'텍스트\'\'\'\",\"<<텍스트>>\"]',4,10,10),(95,'range(1, 10, 2)의 결과에 포함되지 않는 값은?',3,'2026-04-04 11:19:06.508537','[\"1\",\"3\",\"9\",\"10\"]',5,10,10),(96,'Python 함수 정의에 사용하는 키워드는?',2,'2026-04-04 11:19:06.513698','[\"function\",\"func\",\"def\",\"define\"]',6,10,10),(97,'리스트에 요소를 추가하는 메서드는?',1,'2026-04-04 11:19:06.517803','[\"add()\",\"append()\",\"push()\",\"insert_last()\"]',7,10,10),(98,'Python에서 None을 확인하는 올바른 방법은?',1,'2026-04-04 11:19:06.528483','[\"x == None\",\"x is None\",\"x.isNone()\",\"None(x)\"]',8,10,10),(99,'f-string 포매팅의 올바른 예시는?',0,'2026-04-04 11:19:06.533437','[\"f\'이름은 {name}입니다\'\",\"\'이름은 ${name}입니다\'\",\"f\'이름은 (name)입니다\'\",\"\'이름은 #{name}입니다\'\"]',9,10,10),(100,'Python에서 파일을 안전하게 읽는 방법은?',1,'2026-04-04 11:19:06.538828','[\"f = open(\'file.txt\')\",\"with open(\'file.txt\') as f:\",\"file.read(\'file.txt\')\",\"read(\'file.txt\')\"]',10,10,10),(101,'Python에서 리스트 컴프리헨션의 올바른 문법은?',0,'2026-04-04 11:19:06.547143','[\"[x for x in range(10) if x > 5]\",\"[for x in range(10): x if x > 5]\",\"[x if x > 5 for x in range(10)]\",\"[x in range(10) for if x > 5]\"]',1,10,11),(102,'데코레이터(decorator)의 역할은?',1,'2026-04-04 11:19:06.550981','[\"클래스를 생성한다\",\"함수를 수정하지 않고 기능을 추가한다\",\"변수를 상수로 만든다\",\"메모리를 해제한다\"]',2,10,11),(103,'제너레이터(generator)에서 값을 반환하는 키워드는?',1,'2026-04-04 11:19:06.556861','[\"return\",\"yield\",\"emit\",\"send\"]',3,10,11),(104,'Python의 다중 상속에서 MRO(Method Resolution Order)를 확인하는 방법은?',1,'2026-04-04 11:19:06.560726','[\"Class.order()\",\"Class.__mro__\",\"Class.inheritance()\",\"Class.__bases_order__\"]',4,10,11),(105,'다음 중 @staticmethod와 @classmethod의 차이로 올바른 것은?',1,'2026-04-04 11:19:06.565035','[\"차이 없다\",\"@classmethod는 cls를 첫 번째 인자로 받고, @staticmethod는 받지 않는다\",\"@staticmethod는 self를 받는다\",\"@classmethod는 인스턴스 메서드이다\"]',5,10,11),(106,'Python에서 \'with\' 문이 내부적으로 호출하는 매직 메서드는?',1,'2026-04-04 11:19:06.568765','[\"__init__과 __del__\",\"__enter__과 __exit__\",\"__open__과 __close__\",\"__start__과 __end__\"]',6,10,11),(107,'*args와 **kwargs의 차이는?',1,'2026-04-04 11:19:06.573866','[\"차이 없다\",\"*args는 위치 인자를 튜플로, **kwargs는 키워드 인자를 딕셔너리로 받는다\",\"*args는 딕셔너리, **kwargs는 리스트\",\"*args만 가변 인자이다\"]',7,10,11),(108,'Python에서 private 속성을 나타내는 관례는?',1,'2026-04-04 11:19:06.578243','[\"@private 어노테이션\",\"속성 이름 앞에 언더스코어 (_) 또는 더블 언더스코어 (__)\",\"private 키워드 사용\",\"# private 주석 추가\"]',8,10,11),(109,'다음 코드의 출력은?\ndef f(a, b=[]):\n    b.append(a)\n    return b\nprint(f(1))\nprint(f(2))',1,'2026-04-04 11:19:06.583096','[\"[1] [2]\",\"[1] [1, 2]\",\"[1] [2, 1]\",\"에러 발생\"]',9,10,11),(110,'try-except-else-finally에서 else 블록이 실행되는 조건은?',1,'2026-04-04 11:19:06.588240','[\"예외가 발생했을 때\",\"예외가 발생하지 않았을 때\",\"항상 실행된다\",\"finally 후에 실행된다\"]',10,10,11),(111,'Python GIL(Global Interpreter Lock)의 영향은?',1,'2026-04-04 11:19:06.597595','[\"멀티프로세싱이 불가능하다\",\"CPU-bound 작업에서 멀티스레딩의 성능 이점이 제한된다\",\"I/O-bound 작업에서도 병렬 처리가 불가능하다\",\"메모리 사용량이 증가한다\"]',1,10,12),(112,'메타클래스(Metaclass)란?',1,'2026-04-04 11:19:06.601641','[\"추상 클래스의 다른 이름\",\"클래스를 생성하는 클래스\",\"인스턴스를 생성하는 함수\",\"데코레이터의 일종\"]',2,10,12),(113,'Python 디스크립터 프로토콜에 포함되는 메서드가 아닌 것은?',3,'2026-04-04 11:19:06.607661','[\"__get__\",\"__set__\",\"__delete__\",\"__describe__\"]',3,10,12),(114,'asyncio에서 await 키워드의 역할은?',1,'2026-04-04 11:19:06.613056','[\"스레드를 생성한다\",\"코루틴의 실행을 일시 중단하고 완료를 기다린다\",\"함수를 동기 함수로 변환한다\",\"예외를 발생시킨다\"]',4,10,12),(115,'Python의 가비지 컬렉션에서 순환 참조를 처리하는 방식은?',1,'2026-04-04 11:19:06.617994','[\"참조 카운팅만으로 처리\",\"세대별(generational) 가비지 컬렉터를 사용\",\"개발자가 수동으로 해제\",\"순환 참조를 허용하지 않는다\"]',5,10,12),(116,'__slots__의 용도는?',1,'2026-04-04 11:19:06.623205','[\"메서드를 제한한다\",\"인스턴스 속성을 제한하고 __dict__를 생성하지 않아 메모리를 절약한다\",\"상속을 방지한다\",\"직렬화를 지원한다\"]',6,10,12),(117,'CPython에서 작은 정수(-5~256)가 캐싱되는 이유는?',1,'2026-04-04 11:19:06.628031','[\"문법 규칙이다\",\"자주 사용되는 정수의 객체 생성 비용을 줄이기 위해\",\"가비지 컬렉션을 위해\",\"멀티스레드 안전성을 위해\"]',7,10,12),(118,'typing 모듈의 Protocol이 기존 ABC와 다른 점은?',1,'2026-04-04 11:19:06.631253','[\"차이 없다\",\"구조적 서브타이핑(structural subtyping)을 지원한다 (상속 없이 타입 호환)\",\"더 빠르다\",\"런타임에 타입 체크를 한다\"]',8,10,12),(119,'multiprocessing과 threading의 사용 시나리오로 올바른 것은?',1,'2026-04-04 11:19:06.634672','[\"CPU-bound → threading, I/O-bound → multiprocessing\",\"CPU-bound → multiprocessing, I/O-bound → threading\",\"둘 다 CPU-bound에 적합\",\"둘 다 I/O-bound에 적합\"]',9,10,12),(120,'Python에서 weakref의 용도는?',1,'2026-04-04 11:19:06.638994','[\"참조 카운트를 증가시킨다\",\"참조 카운트를 증가시키지 않는 약한 참조를 만들어 순환 참조를 방지한다\",\"메모리를 즉시 해제한다\",\"가비지 컬렉션을 비활성화한다\"]',10,10,12),(121,'시간 복잡도 O(n)의 의미는?',1,'2026-04-04 11:19:06.646755','[\"항상 일정한 시간이 걸린다\",\"입력 크기에 비례하여 시간이 증가한다\",\"입력 크기의 제곱에 비례한다\",\"로그 시간이 걸린다\"]',1,10,13),(122,'스택(Stack)의 특성은?',1,'2026-04-04 11:19:06.649528','[\"FIFO (First In First Out)\",\"LIFO (Last In First Out)\",\"임의 접근 가능\",\"정렬된 상태 유지\"]',2,10,13),(123,'큐(Queue)의 특성은?',1,'2026-04-04 11:19:06.654935','[\"LIFO\",\"FIFO\",\"LILO\",\"임의 접근\"]',3,10,13),(124,'버블 정렬의 평균 시간 복잡도는?',2,'2026-04-04 11:19:06.659378','[\"O(n)\",\"O(n log n)\",\"O(n^2)\",\"O(log n)\"]',4,10,13),(125,'배열에서 특정 값을 선형 탐색할 때 최악의 시간 복잡도는?',2,'2026-04-04 11:19:06.663711','[\"O(1)\",\"O(log n)\",\"O(n)\",\"O(n^2)\"]',5,10,13),(126,'재귀 함수에 반드시 필요한 것은?',1,'2026-04-04 11:19:06.668259','[\"반복문\",\"기저 조건(Base Case)\",\"전역 변수\",\"배열\"]',6,10,13),(127,'연결 리스트(Linked List)의 장점은?',1,'2026-04-04 11:19:06.673650','[\"인덱스 접근이 O(1)이다\",\"삽입/삭제가 O(1)이다 (위치를 알 때)\",\"메모리를 적게 사용한다\",\"정렬이 빠르다\"]',7,10,13),(128,'삽입 정렬이 효율적인 경우는?',1,'2026-04-04 11:19:06.677992','[\"완전히 역순으로 정렬된 경우\",\"데이터가 거의 정렬되어 있는 경우\",\"데이터가 매우 큰 경우\",\"모든 경우에 동일\"]',8,10,13),(129,'공간 복잡도가 O(1)이라는 의미는?',1,'2026-04-04 11:19:06.683751','[\"메모리를 사용하지 않는다\",\"입력 크기와 관계없이 일정한 추가 메모리만 사용한다\",\"입력 크기에 비례하여 메모리가 증가한다\",\"메모리가 무한하다\"]',9,10,13),(130,'선택 정렬의 동작 방식은?',1,'2026-04-04 11:19:06.689750','[\"인접한 두 요소를 비교하여 교환\",\"최솟값을 찾아 맨 앞과 교환\",\"이미 정렬된 부분에 삽입\",\"배열을 반으로 나눠 정렬\"]',10,10,13),(131,'이진 탐색(Binary Search)의 전제 조건은?',1,'2026-04-04 11:19:06.697068','[\"데이터가 연결 리스트에 저장\",\"데이터가 정렬되어 있어야 한다\",\"데이터가 해시 테이블에 저장\",\"데이터 크기가 2의 거듭제곱\"]',1,10,14),(132,'해시 테이블에서 충돌(Collision) 해결 방법이 아닌 것은?',3,'2026-04-04 11:19:06.702544','[\"체이닝(Chaining)\",\"개방 주소법(Open Addressing)\",\"이중 해싱(Double Hashing)\",\"버블 해싱(Bubble Hashing)\"]',2,10,14),(133,'이진 탐색 트리(BST)에서 검색의 평균 시간 복잡도는?',1,'2026-04-04 11:19:06.707479','[\"O(1)\",\"O(log n)\",\"O(n)\",\"O(n log n)\"]',3,10,14),(134,'BFS(너비 우선 탐색)에서 사용하는 자료구조는?',1,'2026-04-04 11:19:06.711410','[\"스택\",\"큐\",\"힙\",\"트리\"]',4,10,14),(135,'DFS(깊이 우선 탐색)에서 사용하는 자료구조는?',1,'2026-04-04 11:19:06.714134','[\"큐\",\"스택 (또는 재귀)\",\"힙\",\"해시 테이블\"]',5,10,14),(136,'다이나믹 프로그래밍(DP)의 핵심 조건 2가지는?',1,'2026-04-04 11:19:06.717907','[\"정렬과 탐색\",\"최적 부분 구조와 중복 부분 문제\",\"분할과 병합\",\"그리디와 백트래킹\"]',6,10,14),(137,'피보나치 수열을 DP로 풀었을 때 시간 복잡도는?',1,'2026-04-04 11:19:06.722785','[\"O(2^n)\",\"O(n)\",\"O(n^2)\",\"O(n log n)\"]',7,10,14),(138,'힙(Heap)의 특성으로 올바른 것은?',1,'2026-04-04 11:19:06.727588','[\"완전 정렬된 트리\",\"완전 이진 트리이며 부모가 자식보다 크거나(최대힙) 작다(최소힙)\",\"이진 탐색 트리의 일종\",\"선형 자료구조\"]',8,10,14),(139,'그래프에서 사이클을 감지하는 방법은?',1,'2026-04-04 11:19:06.731322','[\"BFS만 가능\",\"DFS에서 방문 중인 노드를 다시 만나면 사이클\",\"힙 정렬 사용\",\"사이클 감지는 불가능\"]',9,10,14),(140,'분할 정복(Divide and Conquer)을 사용하는 정렬은?',2,'2026-04-04 11:19:06.735175','[\"버블 정렬\",\"삽입 정렬\",\"병합 정렬(Merge Sort)\",\"선택 정렬\"]',10,10,14),(141,'다익스트라 알고리즘의 시간 복잡도는? (우선순위 큐 사용 시)',2,'2026-04-04 11:19:06.743391','[\"O(V^2)\",\"O(V + E)\",\"O((V + E) log V)\",\"O(V * E)\"]',1,10,15),(142,'벨만-포드 알고리즘이 다익스트라보다 유리한 경우는?',1,'2026-04-04 11:19:06.747091','[\"가중치가 모두 같을 때\",\"음의 가중치가 있는 그래프\",\"그래프가 완전 그래프일 때\",\"정점이 매우 적을 때\"]',2,10,15),(143,'위상 정렬(Topological Sort)이 가능한 그래프 조건은?',1,'2026-04-04 11:19:06.751438','[\"무방향 그래프\",\"DAG (방향 비순환 그래프)\",\"완전 그래프\",\"이분 그래프\"]',3,10,15),(144,'세그먼트 트리의 구간 합 쿼리 시간 복잡도는?',1,'2026-04-04 11:19:06.756750','[\"O(1)\",\"O(log n)\",\"O(n)\",\"O(n log n)\"]',4,10,15),(145,'Knapsack 문제에서 0/1 Knapsack과 Fractional Knapsack의 차이는?',1,'2026-04-04 11:19:06.762126','[\"차이 없다\",\"0/1은 DP, Fractional은 그리디로 풀 수 있다\",\"0/1은 그리디, Fractional은 DP\",\"둘 다 그리디로 풀 수 있다\"]',5,10,15),(146,'최소 신장 트리(MST)를 구하는 알고리즘이 아닌 것은?',2,'2026-04-04 11:19:06.768030','[\"크루스칼(Kruskal)\",\"프림(Prim)\",\"플로이드-워셜(Floyd-Warshall)\",\"보루프카(Borůvka)\"]',6,10,15),(147,'LCS(Longest Common Subsequence)의 시간 복잡도는?',2,'2026-04-04 11:19:06.772444','[\"O(n)\",\"O(n log n)\",\"O(n * m)\",\"O(2^n)\"]',7,10,15),(148,'Union-Find에서 경로 압축(Path Compression)의 효과는?',1,'2026-04-04 11:19:06.777851','[\"공간 복잡도를 줄인다\",\"Find 연산의 시간 복잡도를 거의 O(1)에 근접하게 한다\",\"Union 연산을 빠르게 한다\",\"사이클을 자동으로 감지한다\"]',8,10,15),(149,'A* 알고리즘에서 휴리스틱 함수의 역할은?',1,'2026-04-04 11:19:06.782178','[\"정확한 최단 거리를 계산\",\"목표까지의 예상 비용을 추정하여 탐색 방향을 안내한다\",\"그래프를 정렬한다\",\"음의 가중치를 처리한다\"]',9,10,15),(150,'NP-완전(NP-Complete) 문제의 특성은?',1,'2026-04-04 11:19:06.787360','[\"다항 시간 알고리즘이 알려져 있다\",\"해를 검증하는 것은 다항 시간이지만, 찾는 것은 다항 시간 알고리즘이 알려지지 않았다\",\"해가 존재하지 않는다\",\"근사 알고리즘이 불가능하다\"]',10,10,15);
/*!40000 ALTER TABLE `questions` ENABLE KEYS */;

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
-- Dumping data for table `recommended_mentors`
--

/*!40000 ALTER TABLE `recommended_mentors` DISABLE KEYS */;
/*!40000 ALTER TABLE `recommended_mentors` ENABLE KEYS */;

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
-- Dumping data for table `resumes`
--

/*!40000 ALTER TABLE `resumes` DISABLE KEYS */;
/*!40000 ALTER TABLE `resumes` ENABLE KEYS */;

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
-- Dumping data for table `session_change_requests`
--

/*!40000 ALTER TABLE `session_change_requests` DISABLE KEYS */;
INSERT INTO `session_change_requests` VALUES (1,'2026-04-11 18:56:43.000000','2026-04-22','21:00:00.000000','20:00:00.000000','ê°œì¸ ì¼ì •ì´ ìƒê²¨ì„œ í•˜ë£¨ ë’¤ë¡œ ë³€ê²½ ë¶€íƒë“œë¦½ë‹ˆë‹¤',10,NULL,5,'PENDING'),(4,'2026-04-11 19:26:27.000000','2026-04-22','21:00:00.000000','20:00:00.000000','개인 일정이 생겨서 하루 뒤로 변경 부탁드립니다',8,NULL,20,'PENDING');
/*!40000 ALTER TABLE `session_change_requests` ENABLE KEYS */;

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
-- Dumping data for table `survey_responses`
--

/*!40000 ALTER TABLE `survey_responses` DISABLE KEYS */;
/*!40000 ALTER TABLE `survey_responses` ENABLE KEYS */;

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
-- Dumping data for table `test_answers`
--

/*!40000 ALTER TABLE `test_answers` DISABLE KEYS */;
INSERT INTO `test_answers` VALUES (1,_binary '',0,1,1),(2,_binary '',1,2,1),(3,_binary '',2,3,1),(4,_binary '',1,4,1),(5,_binary '',1,5,1),(6,_binary '',1,6,1),(7,_binary '',3,7,1),(8,_binary '',2,8,1),(9,_binary '',1,9,1),(10,_binary '',1,10,1),(11,_binary '\0',1,1,2),(12,_binary '\0',2,2,2),(13,_binary '',2,3,2),(14,_binary '',1,4,2),(15,_binary '\0',0,5,2),(16,_binary '\0',2,6,2),(17,_binary '\0',2,7,2),(18,_binary '\0',1,8,2),(19,_binary '\0',0,9,2),(20,_binary '\0',0,10,2),(21,_binary '\0',0,11,3),(22,_binary '\0',2,12,3),(23,_binary '\0',3,13,3),(24,_binary '',2,14,3),(25,_binary '\0',2,15,3),(26,_binary '\0',2,16,3),(27,_binary '',1,17,3),(28,_binary '\0',2,18,3),(29,_binary '\0',2,19,3),(30,_binary '\0',3,20,3),(31,_binary '\0',0,131,4),(32,_binary '\0',0,132,4),(33,_binary '\0',0,133,4),(34,_binary '\0',0,134,4),(35,_binary '\0',0,135,4),(36,_binary '\0',0,136,4),(37,_binary '\0',0,137,4),(38,_binary '\0',0,138,4),(39,_binary '\0',0,139,4),(40,_binary '\0',0,140,4),(41,_binary '',1,21,5),(42,_binary '\0',1,22,5),(43,_binary '\0',3,23,5),(44,_binary '',1,24,5),(45,_binary '\0',0,25,5),(46,_binary '',1,26,5),(47,_binary '',2,27,5),(48,_binary '\0',3,28,5),(49,_binary '\0',0,29,5),(50,_binary '\0',1,30,5);
/*!40000 ALTER TABLE `test_answers` ENABLE KEYS */;

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
-- Dumping data for table `test_results`
--

/*!40000 ALTER TABLE `test_results` DISABLE KEYS */;
INSERT INTO `test_results` VALUES (1,10,_binary '','2026-04-04 11:43:13.872946',100,1,1),(2,2,_binary '\0','2026-04-04 14:55:41.272410',20,1,6),(3,2,_binary '\0','2026-04-04 14:56:36.486077',20,2,6),(4,0,_binary '\0','2026-04-04 14:58:10.314187',0,14,6),(5,4,_binary '\0','2026-04-04 14:59:05.804966',40,3,6);
/*!40000 ALTER TABLE `test_results` ENABLE KEYS */;

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
-- Dumping data for table `tests`
--

/*!40000 ALTER TABLE `tests` DISABLE KEYS */;
INSERT INTO `tests` VALUES (1,'Java','2026-04-04 11:19:05.837071','Java 언어의 기본 문법과 핵심 개념을 평가합니다. 변수, 타입, 조건문, 반복문, 배열, 클래스 기초 등을 다룹니다.','BEGINNER',_binary '',60,10,15,'Java 기초 문법 테스트','2026-04-04 11:19:05.837071'),(2,'Java','2026-04-04 11:19:06.029933','컬렉션, 제네릭, 스트림, 람다, OOP 심화 원칙 등 Java 중급 개념을 평가합니다.','INTERMEDIATE',_binary '',65,10,20,'Java 중급 심화 테스트','2026-04-04 11:19:06.029933'),(3,'Java','2026-04-04 11:19:06.087243','JVM 내부 구조, GC 알고리즘, 동시성, 디자인 패턴 등 Java 고급 지식을 평가합니다.','ADVANCED',_binary '',70,10,25,'Java 고급 아키텍처 테스트','2026-04-04 11:19:06.087243'),(4,'Spring','2026-04-04 11:19:06.142303','Spring Boot의 기본 개념, IoC/DI, 어노테이션, REST API 기초를 평가합니다.','BEGINNER',_binary '',60,10,15,'Spring 입문 테스트','2026-04-04 11:19:06.142303'),(5,'Spring','2026-04-04 11:19:06.195795','Spring MVC 동작 흐름, JPA 매핑, 트랜잭션, Spring Security 기초 등을 평가합니다.','INTERMEDIATE',_binary '',65,10,20,'Spring 중급 실무 테스트','2026-04-04 11:19:06.195795'),(6,'Spring','2026-04-04 11:19:06.239864','트랜잭션 전파/격리, 영속성 컨텍스트, 캐시, MSA 패턴 등 고급 지식을 평가합니다.','ADVANCED',_binary '',70,10,25,'Spring 고급 아키텍처 테스트','2026-04-04 11:19:06.239864'),(7,'React','2026-04-04 11:19:06.290144','JSX 문법, 컴포넌트, Props, useState, 이벤트 핸들링 등 React 기초를 평가합니다.','BEGINNER',_binary '',60,10,15,'React 기초 테스트','2026-04-04 11:19:06.290144'),(8,'React','2026-04-04 11:19:06.349386','useEffect, Custom Hook, 상태 관리, React Router, 성능 최적화 등을 평가합니다.','INTERMEDIATE',_binary '',65,10,20,'React 중급 실무 테스트','2026-04-04 11:19:06.349386'),(9,'React','2026-04-04 11:19:06.397149','가상 DOM, Fiber, 서버 컴포넌트, Concurrent 기능, 렌더링 전략 등 고급 주제를 평가합니다.','ADVANCED',_binary '',70,10,25,'React 고급 심화 테스트','2026-04-04 11:19:06.397149'),(10,'Python','2026-04-04 11:19:06.483120','변수, 타입, 리스트, 딕셔너리, 조건문, 반복문, 함수 등 Python 기초를 평가합니다.','BEGINNER',_binary '',60,10,15,'Python 기초 문법 테스트','2026-04-04 11:19:06.483120'),(11,'Python','2026-04-04 11:19:06.542593','클래스/OOP, 데코레이터, 제너레이터, 컴프리헨션, 예외 처리 등을 평가합니다.','INTERMEDIATE',_binary '',65,10,20,'Python 중급 심화 테스트','2026-04-04 11:19:06.542593'),(12,'Python','2026-04-04 11:19:06.593201','GIL, 메타클래스, 디스크립터, asyncio, 메모리 관리 등 Python 고급 주제를 평가합니다.','ADVANCED',_binary '',70,10,25,'Python 고급 아키텍처 테스트','2026-04-04 11:19:06.593201'),(13,'Algorithm','2026-04-04 11:19:06.643500','시간/공간 복잡도, 기본 자료구조, 정렬, 탐색 등 알고리즘 기초를 평가합니다.','BEGINNER',_binary '',60,10,15,'알고리즘 기초 테스트','2026-04-04 11:19:06.643500'),(14,'Algorithm','2026-04-04 11:19:06.693639','이진 탐색, 해시, 트리, 그래프 탐색, DP 기초 등 중급 알고리즘을 평가합니다.','INTERMEDIATE',_binary '',65,10,20,'알고리즘 중급 테스트','2026-04-04 11:19:06.693639'),(15,'Algorithm','2026-04-04 11:19:06.739641','고급 DP, 최단 경로, 세그먼트 트리, 그래프 고급 알고리즘 등을 평가합니다.','ADVANCED',_binary '',70,10,25,'알고리즘 고급 테스트','2026-04-04 11:19:06.739641');
/*!40000 ALTER TABLE `tests` ENABLE KEYS */;

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
-- Dumping data for table `users`
--

/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'2026-04-04 11:19:06.989893','java.mentor@devmatch.com','김자바','$2a$10$O3Xh3NmO..ln4/0ahGf0L.39EyeORSIasoxTJqs58MjV.iP7n4hO6',NULL,NULL,'MENTOR','2026-04-04 11:19:06.989893'),(2,'2026-04-04 11:19:07.015351','spring.mentor@devmatch.com','이스프링','$2a$10$YpOM9ywtv8ftxWG3OnzewuqFqg5OFg9KMofgT6rfBRUQj1EhfJPAm',NULL,NULL,'MENTOR','2026-04-04 11:19:07.015351'),(3,'2026-04-04 11:19:07.031318','react.mentor@devmatch.com','박리액트','$2a$10$YpOM9ywtv8ftxWG3OnzewuqFqg5OFg9KMofgT6rfBRUQj1EhfJPAm',NULL,NULL,'MENTOR','2026-04-04 11:19:07.031318'),(4,'2026-04-04 11:19:07.047368','python.mentor@devmatch.com','최파이썬','$2a$10$YpOM9ywtv8ftxWG3OnzewuqFqg5OFg9KMofgT6rfBRUQj1EhfJPAm',NULL,NULL,'MENTOR','2026-04-04 11:19:07.047368'),(5,'2026-04-04 11:19:07.060539','fullstack.mentor@devmatch.com','정풀스택','$2a$10$YpOM9ywtv8ftxWG3OnzewuqFqg5OFg9KMofgT6rfBRUQj1EhfJPAm',NULL,NULL,'MENTOR','2026-04-04 11:19:07.060539'),(6,'2026-04-04 14:54:33.480763','darkni2005@naver.com','김동국','$2a$10$aV9f/VDWZQGmFve0zmTuTeUFkX4R43DTkIDDJQ4GTsgAO4leSKsEO',NULL,NULL,'MENTEE','2026-04-04 14:54:33.480763'),(7,'2026-04-04 17:06:44.964693','qwer@qwer.com','마바사','$2a$10$tV.uoOoGX6NxnnqbVEy7w.MXj9QyjWqfZC7DBjMT2fBQcUZ2acw.G',NULL,NULL,'MENTEE','2026-04-04 17:06:44.964693'),(8,'2026-04-10 00:08:03.105621','qwer@naver.com','가나다','$2a$10$R9NPpezSUyDGEfHMWgZyBujBGhZ7Kh4xrD9FdRf9q/logXQlNcCC.',NULL,NULL,'MENTEE','2026-04-10 00:08:03.105621'),(9,'2026-04-10 10:18:57.829725','newtest002@example.com','tester','$2a$10$rm4xP7wNl6UfEJbRsCN.Guk.uh./jRStsQwU5h8ODD1bGvLfPMGNq',NULL,NULL,'MENTEE','2026-04-10 10:18:57.829725'),(10,'2026-04-10 10:31:57.890126','ganada@devmatch.com','가나다','$2a$10$/6F.7RIvJ9BX6W3Wins/U.7v0I4KANtYLkkE0gWTcpONA4YfNNH6y',NULL,NULL,'MENTEE','2026-04-10 10:31:57.890126');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;

--
-- Dumping events for database 'devmatch'
--

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

-- Dump completed on 2026-04-12  6:33:17

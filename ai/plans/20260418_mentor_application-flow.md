---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "멘토 등록 신청서 + 멘토링 코스 DB 승격 구현 플랜 도입 계획 및 마이그레이션 작업 방향"

---

# 멘토 등록 신청서 + 멘토링 코스 DB 승격 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MENTOR 가입자를 위한 `/mentor/apply` 신청서 흐름, PENDING/REJECTED/APPROVED 상태별 UX, 그리고 12개 멘토링 코스를 프론트 하드코딩에서 DB 엔티티로 승격.

**Architecture:**
- 백엔드: 신규 `MentoringCourse` / `MentorProfileHistory` 엔티티, `MentorProfile` 확장, `MentorService.apply()` 재신청 분기 로직, `DataInitializer` 12개 코스 시드.
- 프론트: `/mentor/apply`, `/mentor/status` 신규 페이지, `AuthContext` 상태 조회, 헤더 뱃지/가드, 기존 하드코딩 `COURSE_DATA` 제거 및 `GET /api/courses` 연동.
- 스펙: [`docs/superpowers/specs/2026-04-18-mentor-application-flow-design.md`](../specs/2026-04-18-mentor-application-flow-design.md)

**Tech Stack:** Spring Boot 3.x + Java 17 + JPA/Hibernate + JUnit 5 + Mockito (백엔드), Next.js 14 + TypeScript + Tailwind CSS + axios (프론트, 테스트 인프라 없음 — 수동 검증).

**Frontend Preview Rule:** Phase D/E/F 시작 직전, Pencil MCP 로 해당 페이지의 UI 목업을 먼저 만들고 사용자 확인을 받은 뒤 구현을 진행한다. (메모리 `feedback_frontend_preview.md`)

---

## Phase A: 백엔드 — 멘토링 코스 카탈로그

### Task A1: `MentoringCourse` 엔티티 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/MentoringCourse.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentoring_courses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentoringCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_key", nullable = false, unique = true, length = 50)
    private String courseKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "icon_string", length = 10)
    private String iconString;

    @Column(name = "description_title", columnDefinition = "TEXT")
    private String descriptionTitle;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "boxes_json", columnDefinition = "TEXT")
    private String boxesJson;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/MentoringCourse.java
git commit -m "feat(backend): MentoringCourse 엔티티 추가"
```

---

### Task A2: `MentoringCourseRepository` 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/repository/MentoringCourseRepository.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.repository;

import com.devmatch.entity.MentoringCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentoringCourseRepository extends JpaRepository<MentoringCourse, Long> {

    Optional<MentoringCourse> findByCourseKey(String courseKey);

    List<MentoringCourse> findAllByActiveTrueOrderByDisplayOrderAsc();

    List<MentoringCourse> findAllByCourseKeyInAndActiveTrue(List<String> courseKeys);

    boolean existsByCourseKey(String courseKey);
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/MentoringCourseRepository.java
git commit -m "feat(backend): MentoringCourseRepository 추가"
```

---

### Task A3: 코스 응답 DTO 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/course/CourseResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/course/CourseSummary.java`

- [ ] **Step 1: `CourseSummary.java` 생성 (요약용 — MentorProfileResponse 등에서 사용)**

```java
package com.devmatch.dto.course;

import com.devmatch.entity.MentoringCourse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CourseSummary {

    private String courseKey;
    private String title;
    private String iconString;

    public static CourseSummary from(MentoringCourse course) {
        return CourseSummary.builder()
                .courseKey(course.getCourseKey())
                .title(course.getTitle())
                .iconString(course.getIconString())
                .build();
    }
}
```

- [ ] **Step 2: `CourseResponse.java` 생성 (상세용 — /api/courses 응답)**

```java
package com.devmatch.dto.course;

import com.devmatch.entity.MentoringCourse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Builder
@AllArgsConstructor
public class CourseResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private String courseKey;
    private String title;
    private String subtitle;
    private String iconString;
    private String descriptionTitle;
    private String descriptionText;
    private List<Map<String, Object>> boxes;
    private Integer displayOrder;
    private Boolean active;

    public static CourseResponse from(MentoringCourse c) {
        return CourseResponse.builder()
                .id(c.getId())
                .courseKey(c.getCourseKey())
                .title(c.getTitle())
                .subtitle(c.getSubtitle())
                .iconString(c.getIconString())
                .descriptionTitle(c.getDescriptionTitle())
                .descriptionText(c.getDescriptionText())
                .boxes(parseBoxes(c.getBoxesJson()))
                .displayOrder(c.getDisplayOrder())
                .active(c.getActive())
                .build();
    }

    private static List<Map<String, Object>> parseBoxes(String boxesJson) {
        if (boxesJson == null || boxesJson.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(boxesJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("boxesJson 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/devmatch/dto/course/
git commit -m "feat(backend): 코스 응답 DTO (CourseResponse, CourseSummary) 추가"
```

---

### Task A4: `CourseService` 생성 — TDD

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/CourseServiceTest.java`
- Create: `backend/src/main/java/com/devmatch/service/CourseService.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.repository.MentoringCourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private MentoringCourseRepository courseRepository;
    @InjectMocks private CourseService courseService;

    @Test
    void findAllActive_은_활성코스만_display_order로_반환한다() {
        MentoringCourse c1 = MentoringCourse.builder().courseKey("java-backend").title("Java").displayOrder(1).active(true).build();
        MentoringCourse c2 = MentoringCourse.builder().courseKey("kafka").title("Kafka").displayOrder(2).active(true).build();
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(c1, c2));

        List<CourseResponse> result = courseService.findAllActive();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCourseKey()).isEqualTo("java-backend");
    }

    @Test
    void findByKey_존재하지_않으면_예외() {
        when(courseRepository.findByCourseKey("missing")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> courseService.findByKey("missing"))
                .isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew -p backend test --tests CourseServiceTest`
Expected: 컴파일 에러 (`CourseService` 없음)

- [ ] **Step 3: `CourseService` 구현**

```java
package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.repository.MentoringCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final MentoringCourseRepository courseRepository;

    public List<CourseResponse> findAllActive() {
        return courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CourseResponse::from)
                .toList();
    }

    public CourseResponse findByKey(String courseKey) {
        MentoringCourse course = courseRepository.findByCourseKey(courseKey)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코스: " + courseKey));
        return CourseResponse.from(course);
    }

    public List<MentoringCourse> findActiveByKeys(List<String> courseKeys) {
        List<MentoringCourse> courses = courseRepository.findAllByCourseKeyInAndActiveTrue(courseKeys);
        if (courses.size() != courseKeys.size()) {
            throw new IllegalArgumentException("존재하지 않거나 비활성화된 코스 키가 포함되어 있습니다");
        }
        return courses;
    }
}
```

- [ ] **Step 4: 테스트 재실행 — PASS 확인**

Run: `./gradlew -p backend test --tests CourseServiceTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/CourseService.java backend/src/test/java/com/devmatch/service/CourseServiceTest.java
git commit -m "feat(backend): CourseService + 단위 테스트 추가"
```

---

### Task A5: `CourseController` 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/CourseController.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.controller;

import com.devmatch.dto.ApiResponse;
import com.devmatch.dto.course.CourseResponse;
import com.devmatch.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ApiResponse<List<CourseResponse>> list() {
        return ApiResponse.success("코스 목록 조회 성공", courseService.findAllActive());
    }

    @GetMapping("/{courseKey}")
    public ApiResponse<CourseResponse> detail(@PathVariable String courseKey) {
        return ApiResponse.success("코스 조회 성공", courseService.findByKey(courseKey));
    }
}
```

- [ ] **Step 2: `ApiResponse` 임포트 경로 확인**

Run: `grep -rn "class ApiResponse" backend/src/main/java/`
Expected: 실제 `ApiResponse` 클래스 위치 확인 후, Step 1 의 import 를 실제 경로로 조정 (예: `com.devmatch.dto.common.ApiResponse`)

- [ ] **Step 3: Security 설정 확인**

`backend/src/main/java/com/devmatch/config/SecurityConfig.java` 에서 `/api/courses/**` 를 `permitAll()` 로 허용하도록 확인/추가 (공개 엔드포인트).

예시 변경:
```java
.requestMatchers("/api/auth/**", "/api/courses/**").permitAll()
```

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/controller/CourseController.java backend/src/main/java/com/devmatch/config/SecurityConfig.java
git commit -m "feat(backend): /api/courses 엔드포인트 추가 및 permitAll 설정"
```

---

### Task A6: `DataInitializer` 에 12개 코스 시드

**Files:**
- Modify: `backend/src/main/java/com/devmatch/config/DataInitializer.java`

- [ ] **Step 1: 필드 & 시드 메서드 추가**

`DataInitializer` 상단 필드 추가:
```java
private final MentoringCourseRepository mentoringCourseRepository;
```

`run()` 끝에 호출 추가:
```java
initMentoringCourses();
```

클래스 하단에 메서드 추가:

```java
// ──────────────────────────────────────────────
//  멘토링 코스 (12개)
// ──────────────────────────────────────────────
private void initMentoringCourses() {
    if (mentoringCourseRepository.count() > 0) {
        log.info("멘토링 코스가 이미 존재합니다. 시드 건너뜀.");
        return;
    }

    record Seed(String key, String title, String subtitle, String icon,
                String descTitle, String descText, String boxesJson, int order) {}

    List<Seed> seeds = List.of(
        new Seed("java-backend", "AI+ Java 백엔드",
            "깊이 있는 학습과 고퀄리티 프로젝트 수행을 통해 채용 경쟁력을 높이는 1:1 심화형 멘토링 코스",
            "☕",
            "실전 백엔드 역량,\n기본기부터 AI 서빙까지.",
            "Java/Spring 생태계를 깊게 다루며 실무에서 바로 쓰이는 아키텍처를 학습합니다.",
            "[]", 1),
        new Seed("node-backend", "Node.js Backend + AI",
            "실시간 통신과 고성능 비동기 서버 아키텍처를 마스터하는 심화형 멘토링",
            "JS",
            "단순한 CRUD를 넘어\n고성능 비동기 아키텍처를 다룹니다.",
            "JavaScript/TypeScript 백엔드 환경에서 Event Loop의 이해부터 Redis, Socket.io 활용까지.",
            "[]", 2),
        new Seed("python-backend", "Python Backend + AI",
            "백엔드 생태계와 AI 서빙을 결합한 최적의 실무 밀착 멘토링",
            "🐍",
            "데이터와 백엔드의 브릿지,\nPython 서버 서빙 최적화.",
            "동기/비동기 프레임워크의 장단점을 파악하고 최신 FastAPI 생태계를 익힙니다.",
            "[]", 3),
        new Seed("frontend", "Frontend + AI",
            "프론트엔드 성능 최적화와 트러블슈팅, 최신 기술 스택을 다루는 심화 코스",
            "⚛️",
            "보이는 것 그 이상,\n사용자 경험(UX)과 성능의 극대화를 이룹니다.",
            "Next.js의 SSR/SSG/ISR 혼합 렌더링, Web Vitals 최적화, 상태관리 패턴 등.",
            "[]", 4),
        new Seed("android", "Android + AI",
            "모던 안드로이드 앱 아키텍처와 Compose, 성능 최적화 마스터 과정",
            "🤖",
            "안드로이드 네이티브의 끝판왕,\n안정적이고 유려한 앱을 만듭니다.",
            "Jetpack Compose와 MVVM/MVI 아키텍처, Memory Leak 방지 기술 등.",
            "[]", 5),
        new Seed("ios", "iOS + AI",
            "모던 iOS 앱 아키텍처와 SwiftUI 마스터 과정",
            "🍎",
            "부드러운 경험을 만드는\n최상급 iOS 애플리케이션",
            "SwiftUI, Combine 기반의 선언형 패러다임과 TCA 등 최신 iOS 생태계.",
            "[]", 6),
        new Seed("flutter", "Flutter + AI",
            "크로스 플랫폼의 한계를 뛰어넘는 최적화 및 네이티브 연동",
            "🦋",
            "하나의 코드로 두 배의 가치를,\n크로스 플랫폼의 완성.",
            "단순 UI 클론을 넘어 렌더링 최적화, 상태관리 패턴, 네이티브 연동까지.",
            "[]", 7),
        new Seed("react-native", "React Native + AI",
            "웹 개발 경험으로 시작하는 최고 수준의 앱 배포",
            "📱",
            "웹과 모바일의 브릿지,\n빠른 속도로 시장을 선점합니다.",
            "React 생태계를 그대로 활용하며 최신 JSI 아키텍처와 애니메이션 최적화를 배웁니다.",
            "[]", 8),
        new Seed("devops", "DevOps 엔지니어 육성",
            "CI/CD 빌드 파이프라인부터 클라우드 네이티브 아키텍처까지",
            "⚙️",
            "인프라를 코드로 구성하고,\n자동화로 생산성을 극대화합니다.",
            "AWS IaC(Terraform), 클러스터 오케스트레이션(K8s) 등 DevOps 툴체인.",
            "[]", 9),
        new Seed("firststep", "First Step: Java Backend",
            "비전공자/입문자도 따라할 수 있는 탄탄한 웹 백엔드 첫걸음",
            "🌱",
            "처음이라고 두려워 마세요,\n기본부터 든든하게 다집니다.",
            "Java 언어 기초부터 Spring Boot 서버 배포까지 끝맺음하는 과정.",
            "[]", 10),
        new Seed("distributed-lock", "분산 락 Deep Dive",
            "수만 명의 선착순 트래픽을 놓치지 않고 완벽히 제어하는 특강",
            "🔒",
            "단 한 건의 동시성 오류도 용납하지 않는\n초정밀 트래픽 제어.",
            "티켓팅, 수강신청 등 극단적인 동시성 상황 시스템 설계.",
            "[]", 11),
        new Seed("kafka", "Kafka Deep Dive",
            "대규모 메시지 큐와 이벤트 드리븐 설계 패턴 완전 정복",
            "📨",
            "시스템 간의 완벽한 징검다리,\n이벤트 기반 아키텍처.",
            "Kafka의 내부 동작 원리와 MSA 환경에서의 활용을 심층 실습.",
            "[]", 12)
    );

    seeds.forEach(s -> mentoringCourseRepository.save(
        MentoringCourse.builder()
            .courseKey(s.key())
            .title(s.title())
            .subtitle(s.subtitle())
            .iconString(s.icon())
            .descriptionTitle(s.descTitle())
            .descriptionText(s.descText())
            .boxesJson(s.boxesJson())
            .displayOrder(s.order())
            .active(true)
            .build()
    ));

    log.info("멘토링 코스 {}개 시드 완료", seeds.size());
}
```

- [ ] **Step 2: 최상단 import 추가**

```java
import com.devmatch.entity.MentoringCourse;
import com.devmatch.repository.MentoringCourseRepository;
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/devmatch/config/DataInitializer.java
git commit -m "feat(backend): DataInitializer 에 12개 멘토링 코스 시드 추가"
```

---

### Task A7: 백엔드 수동 검증 — 코스 API

- [ ] **Step 1: 기존 mentoring_courses 테이블 정리 (있다면)**

```bash
mysql -u <user> -p devmatch_db -e "DROP TABLE IF EXISTS mentoring_courses;"
```

- [ ] **Step 2: 백엔드 재빌드 + 재시작**

IntelliJ: `Build > Rebuild Project` → Run 재시작  
(또는 CLI: `./gradlew -p backend bootRun`)

- [ ] **Step 3: 로그에 시드 메시지 확인**

Expected 로그: `멘토링 코스 12개 시드 완료`

- [ ] **Step 4: API 호출 테스트**

```bash
curl -s http://localhost:8080/api/courses | head -c 500
curl -s http://localhost:8080/api/courses/java-backend
```

Expected: `success: true`, 12개 코스 목록 / 단일 코스 상세

- [ ] **Step 5: 체크포인트 — 이 지점까지 성공하면 Phase A 완료로 커밋 체크**

Phase A 작업 모두 `git log --oneline` 으로 5~6 커밋이 쌓였는지 확인.

---

## Phase B: 백엔드 — MentorProfile 확장 & History

### Task B1: `MentorProfile` 엔티티 변경

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/MentorProfile.java`

- [ ] **Step 1: 전체 파일 교체**

```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "mentor_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "mentor_profile_courses",
        joinColumns = @JoinColumn(name = "mentor_profile_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<MentoringCourse> courses = new HashSet<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private List<String> techStack;

    @Column(nullable = false)
    private Integer careerYears;

    @Column(length = 100)
    private String company;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(length = 200)
    private String education;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> certifications;

    @Column(name = "preferred_mentee_level", length = 20)
    private String preferredMenteeLevel;

    @Column(length = 1000)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorStatus status = MentorStatus.PENDING;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void updateFromRequest(Set<MentoringCourse> newCourses,
                                  List<String> techStack,
                                  Integer careerYears,
                                  String company, String jobTitle,
                                  String portfolioUrl, String education,
                                  List<String> certifications,
                                  String preferredMenteeLevel,
                                  String bio) {
        this.courses = newCourses;
        this.techStack = techStack;
        this.careerYears = careerYears;
        this.company = company;
        this.jobTitle = jobTitle;
        this.portfolioUrl = portfolioUrl;
        this.education = education;
        this.certifications = certifications;
        this.preferredMenteeLevel = preferredMenteeLevel;
        this.bio = bio;
        this.status = MentorStatus.PENDING;
    }

    public void markApproved() { this.status = MentorStatus.APPROVED; }
    public void markRejected() { this.status = MentorStatus.REJECTED; }
}
```

- [ ] **Step 2: 기존 테이블 DROP (스키마 대폭 변경으로 깔끔한 재생성 필요)**

```bash
mysql -u <user> -p devmatch_db -e "DROP TABLE IF EXISTS mentor_profiles; DROP TABLE IF EXISTS mentor_profile_courses;"
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL` — (repository 등에서 `specialty` 참조로 컴파일 실패할 수 있음 → Task B4 까지 기다렸다가 함께 커밋)

※ 컴파일 오류 나는 파일 목록을 기록해두고 다음 Task 들에서 순차적으로 고침.

- [ ] **Step 4: 일단 변경만 스테이지 (커밋은 B4 완료 후)**

```bash
git add backend/src/main/java/com/devmatch/entity/MentorProfile.java
```

---

### Task B2: `MentorProfileHistory` 엔티티 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/MentorProfileHistory.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "mentor_profile_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorProfileHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = StringListConverter.class)
    @Column(name = "course_keys", columnDefinition = "TEXT", nullable = false)
    private List<String> courseKeys;

    @Convert(converter = StringListConverter.class)
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private List<String> techStack;

    @Column(name = "career_years", nullable = false)
    private Integer careerYears;

    @Column(length = 100)
    private String company;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(length = 200)
    private String education;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> certifications;

    @Column(name = "preferred_mentee_level", length = 20)
    private String preferredMenteeLevel;

    @Column(length = 1000)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorStatus status = MentorStatus.PENDING;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    public void markApproved(Long adminUserId) {
        this.status = MentorStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = adminUserId;
    }

    public void markRejected(Long adminUserId, String reason) {
        this.status = MentorStatus.REJECTED;
        this.rejectedReason = reason;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = adminUserId;
    }
}
```

- [ ] **Step 2: 스테이지**

```bash
git add backend/src/main/java/com/devmatch/entity/MentorProfileHistory.java
```

---

### Task B3: `MentorProfileHistoryRepository` 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/repository/MentorProfileHistoryRepository.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.repository;

import com.devmatch.entity.MentorProfileHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorProfileHistoryRepository extends JpaRepository<MentorProfileHistory, Long> {

    Optional<MentorProfileHistory> findTopByUserIdOrderBySubmittedAtDesc(Long userId);
}
```

- [ ] **Step 2: 스테이지**

```bash
git add backend/src/main/java/com/devmatch/repository/MentorProfileHistoryRepository.java
```

---

### Task B4: `MentorApplyRequest` DTO 업데이트

**Files:**
- Modify: `backend/src/main/java/com/devmatch/dto/mentor/MentorApplyRequest.java`

- [ ] **Step 1: 전체 파일 교체**

```java
package com.devmatch.dto.mentor;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MentorApplyRequest {

    @NotEmpty(message = "멘토링 코스를 최소 1개 이상 선택해야 합니다")
    private List<String> courseKeys;

    private List<String> techStack;

    @NotNull(message = "경력 연수는 필수입니다")
    @Min(value = 1, message = "경력은 1년 이상이어야 합니다")
    private Integer careerYears;

    @Size(max = 100)
    private String company;

    @Size(max = 100)
    private String jobTitle;

    @Size(max = 500)
    private String portfolioUrl;

    @Size(max = 200)
    private String education;

    private List<String> certifications;

    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|ANY", message = "선호 멘티 수준 값이 유효하지 않습니다")
    private String preferredMenteeLevel;

    @Size(max = 1000, message = "자기 소개는 1000자 이하여야 합니다")
    private String bio;
}
```

- [ ] **Step 2: 스테이지 + Phase B entity 그룹 커밋**

```bash
git add backend/src/main/java/com/devmatch/dto/mentor/MentorApplyRequest.java
git commit -m "feat(backend): MentorProfile 확장 + History 엔티티 + Request DTO 갱신"
```

---

### Task B5: `MentorProfileResponse` DTO 업데이트

**Files:**
- Modify: `backend/src/main/java/com/devmatch/dto/mentor/MentorProfileResponse.java`

- [ ] **Step 1: 기존 파일 내용 확인**

Run: `cat backend/src/main/java/com/devmatch/dto/mentor/MentorProfileResponse.java`

- [ ] **Step 2: 전체 파일 교체**

```java
package com.devmatch.dto.mentor;

import com.devmatch.dto.course.CourseSummary;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MentorProfileResponse {

    private Long id;
    private Long userId;
    private String name;
    private String email;
    private List<CourseSummary> courses;
    private List<String> techStack;
    private Integer careerYears;
    private String company;
    private String jobTitle;
    private String portfolioUrl;
    private String education;
    private List<String> certifications;
    private String preferredMenteeLevel;
    private String bio;
    private MentorStatus status;
    private String rejectedReason;

    public static MentorProfileResponse from(MentorProfile p, String rejectedReason) {
        return MentorProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .name(p.getUser().getName())
                .email(p.getUser().getEmail())
                .courses(p.getCourses().stream().map(CourseSummary::from).toList())
                .techStack(p.getTechStack())
                .careerYears(p.getCareerYears())
                .company(p.getCompany())
                .jobTitle(p.getJobTitle())
                .portfolioUrl(p.getPortfolioUrl())
                .education(p.getEducation())
                .certifications(p.getCertifications())
                .preferredMenteeLevel(p.getPreferredMenteeLevel())
                .bio(p.getBio())
                .status(p.getStatus())
                .rejectedReason(rejectedReason)
                .build();
    }
}
```

- [ ] **Step 3: 컴파일 확인 — 다른 곳에서 이 DTO 의 기존 메서드 시그니처를 사용하는지 검증**

Run: `./gradlew -p backend compileJava 2>&1 | head -40`
Expected: 컴파일 에러가 있다면 `MentorService`/`MentorController`/`MatchingService` 등에서 기존 `MentorProfileResponse.from(profile)` 호출을 Task B6/B7 에서 수정할 예정이므로 다음 Task 진행.

- [ ] **Step 4: 스테이지**

```bash
git add backend/src/main/java/com/devmatch/dto/mentor/MentorProfileResponse.java
```

---

### Task B6: `MentorService` 리팩터 — 재신청 분기 + 이력 저장 (TDD)

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/MentorServiceTest.java`
- Modify: `backend/src/main/java/com/devmatch/service/MentorService.java`

- [ ] **Step 1: 실패 테스트 작성 — 4가지 분기 검증**

```java
package com.devmatch.service;

import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.AlreadyAppliedException;
import com.devmatch.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseService courseService;
    @Mock private MentorProfileHistoryRepository historyRepository;
    @InjectMocks private MentorService mentorService;

    private MentorApplyRequest validRequest() {
        MentorApplyRequest req = new MentorApplyRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "courseKeys", List.of("java-backend"));
        org.springframework.test.util.ReflectionTestUtils.setField(req, "careerYears", 5);
        return req;
    }

    @Test
    void apply_신규_신청시_프로필과_이력이_생성된다() {
        User user = User.builder().id(1L).email("m@test").name("멘토").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(courseService.findActiveByKeys(List.of("java-backend")))
                .thenReturn(List.of(MentoringCourse.builder().courseKey("java-backend").title("Java").build()));
        when(mentorProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mentorService.apply(1L, validRequest());

        verify(mentorProfileRepository).save(any(MentorProfile.class));
        verify(historyRepository).save(any(MentorProfileHistory.class));
    }

    @Test
    void apply_REJECTED_상태에서는_프로필_업데이트_후_새_이력_insert() {
        User user = User.builder().id(1L).email("m@test").name("멘토").build();
        MentorProfile existing = MentorProfile.builder()
                .user(user).careerYears(3).status(MentorStatus.REJECTED).courses(new HashSet<>()).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(courseService.findActiveByKeys(any()))
                .thenReturn(List.of(MentoringCourse.builder().courseKey("java-backend").build()));

        mentorService.apply(1L, validRequest());

        assertThat(existing.getStatus()).isEqualTo(MentorStatus.PENDING);
        verify(historyRepository).save(any(MentorProfileHistory.class));
        verify(mentorProfileRepository, never()).save(existing);  // 덮어쓰기는 dirty checking
    }

    @Test
    void apply_PENDING_중복_신청시_AlreadyAppliedException() {
        User user = User.builder().id(1L).build();
        MentorProfile existing = MentorProfile.builder().user(user).status(MentorStatus.PENDING).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mentorService.apply(1L, validRequest()))
                .isInstanceOf(AlreadyAppliedException.class);
    }

    @Test
    void apply_APPROVED_상태에서_재신청_불가() {
        User user = User.builder().id(1L).build();
        MentorProfile existing = MentorProfile.builder().user(user).status(MentorStatus.APPROVED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mentorService.apply(1L, validRequest()))
                .isInstanceOf(AlreadyAppliedException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew -p backend test --tests MentorServiceTest`
Expected: 테스트 4개 모두 실패 (또는 컴파일 오류)

- [ ] **Step 3: `MentorService` 재구현**

```java
package com.devmatch.service;

import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.AlreadyAppliedException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorProfileHistoryRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorService {

    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;
    private final MentorProfileHistoryRepository historyRepository;

    @Transactional
    public MentorProfileResponse apply(Long userId, MentorApplyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        List<MentoringCourse> courses = courseService.findActiveByKeys(request.getCourseKeys());

        Optional<MentorProfile> existingOpt = mentorProfileRepository.findByUserId(userId);
        MentorProfile profile;

        if (existingOpt.isPresent()) {
            MentorProfile existing = existingOpt.get();
            if (existing.getStatus() == MentorStatus.PENDING) {
                throw new AlreadyAppliedException("이미 심사 중인 신청이 있습니다");
            }
            if (existing.getStatus() == MentorStatus.APPROVED) {
                throw new AlreadyAppliedException("이미 승인된 멘토입니다");
            }
            // REJECTED → 덮어쓰기 + PENDING 복귀
            existing.updateFromRequest(
                    new HashSet<>(courses),
                    request.getTechStack(),
                    request.getCareerYears(),
                    request.getCompany(),
                    request.getJobTitle(),
                    request.getPortfolioUrl(),
                    request.getEducation(),
                    request.getCertifications(),
                    request.getPreferredMenteeLevel(),
                    request.getBio()
            );
            profile = existing;
        } else {
            profile = MentorProfile.builder()
                    .user(user)
                    .courses(new HashSet<>(courses))
                    .techStack(request.getTechStack())
                    .careerYears(request.getCareerYears())
                    .company(request.getCompany())
                    .jobTitle(request.getJobTitle())
                    .portfolioUrl(request.getPortfolioUrl())
                    .education(request.getEducation())
                    .certifications(request.getCertifications())
                    .preferredMenteeLevel(request.getPreferredMenteeLevel())
                    .bio(request.getBio())
                    .status(MentorStatus.PENDING)
                    .build();
            profile = mentorProfileRepository.save(profile);
        }

        historyRepository.save(MentorProfileHistory.builder()
                .userId(userId)
                .courseKeys(request.getCourseKeys())
                .techStack(request.getTechStack())
                .careerYears(request.getCareerYears())
                .company(request.getCompany())
                .jobTitle(request.getJobTitle())
                .portfolioUrl(request.getPortfolioUrl())
                .education(request.getEducation())
                .certifications(request.getCertifications())
                .preferredMenteeLevel(request.getPreferredMenteeLevel())
                .bio(request.getBio())
                .status(MentorStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build());

        return MentorProfileResponse.from(profile, null);
    }

    public MentorProfileResponse getMyMentorProfile(Long userId) {
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("멘토 프로필을 찾을 수 없습니다"));

        String rejectedReason = null;
        if (profile.getStatus() == MentorStatus.REJECTED) {
            rejectedReason = historyRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                    .map(MentorProfileHistory::getRejectedReason)
                    .orElse(null);
        }
        return MentorProfileResponse.from(profile, rejectedReason);
    }
}
```

- [ ] **Step 4: 테스트 재실행**

Run: `./gradlew -p backend test --tests MentorServiceTest`
Expected: 4개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/MentorService.java backend/src/test/java/com/devmatch/service/MentorServiceTest.java
git commit -m "feat(backend): MentorService 재신청 분기 + 이력 저장 로직 구현 + 테스트"
```

---

### Task B7: 기존 `MatchingService`/기타 참조 보정

**Files:**
- Modify: 컴파일 실패 파일들 (주로 `MatchingService.java`, `MentorController.java` 등)

- [ ] **Step 1: 컴파일 실패 지점 전수 확인**

Run: `./gradlew -p backend compileJava 2>&1 | grep "error:" | head -30`

주 예상 수정점:
- `MentorProfile.getSpecialty()` 호출 → `.getCourses()` + `.stream().map(MentoringCourse::getCourseKey).toList()` 로 치환
- `MentorProfileResponse.from(profile)` 1인자 호출 → `.from(profile, null)` 2인자로 치환

- [ ] **Step 2: 각 파일 수정**

각 컴파일 에러 케이스마다 위 치환 규칙으로 수정. 매칭 로직이 `specialty` 문자열 필터를 쓰고 있었다면 `courses` 의 `courseKey` 로 필터하도록 변경.

예시 — `MatchingService` 내 매칭 필터:
```java
// 변경 전
mentor.getSpecialty().contains(category)
// 변경 후
mentor.getCourses().stream().anyMatch(c -> c.getCourseKey().equals(category))
```

- [ ] **Step 3: 컴파일 성공 확인**

Run: `./gradlew -p backend compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 전체 테스트 실행**

Run: `./gradlew -p backend test`
Expected: 모든 테스트 PASS (기존 테스트가 깨지면 해당 파일 보정)

- [ ] **Step 5: 커밋**

```bash
git add -A backend/src/main/java/
git commit -m "refactor(backend): specialty 제거에 따른 MatchingService 등 호출부 보정"
```

---

### Task B8: 백엔드 수동 검증 — 신청 플로우

- [ ] **Step 1: DB 깨끗이 리셋 (mentor_profiles 관련)**

```bash
mysql -u <user> -p devmatch_db -e "DROP TABLE IF EXISTS mentor_profile_courses; DROP TABLE IF EXISTS mentor_profile_history; DROP TABLE IF EXISTS mentor_profiles;"
```

- [ ] **Step 2: 백엔드 재빌드 + 재시작**

- [ ] **Step 3: curl 로 신청서 제출 테스트**

```bash
# 1) 멘토 계정으로 로그인해서 accessToken 취득
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"python@naver.com","password":"비밀번호"}' | jq -r '.data.accessToken')

# 2) 멘토 신청
curl -X POST http://localhost:8080/api/mentor/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "courseKeys":["java-backend","kafka"],
    "techStack":["Spring Boot"],
    "careerYears":5,
    "company":"OO테크",
    "jobTitle":"백엔드 개발자",
    "portfolioUrl":"https://github.com/me",
    "education":"OO대 컴공",
    "certifications":["정보처리기사"],
    "preferredMenteeLevel":"INTERMEDIATE",
    "bio":"안녕하세요"
  }'

# 3) 중복 제출 → 409
curl -X POST http://localhost:8080/api/mentor/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"courseKeys":["java-backend"],"careerYears":5}'

# 4) 내 프로필 조회
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/mentor/me
```

Expected:
- (2) 신청 성공, `status: "PENDING"` 리턴
- (3) 409 + "이미 심사 중인 신청이 있습니다"
- (4) `courses` 배열에 `[{courseKey: "java-backend", title: "...", iconString: "☕"}, ...]`

- [ ] **Step 4: DB 확인**

```bash
mysql -u <user> -p devmatch_db -e "SELECT * FROM mentor_profile_history;"
```
Expected: 1 row, `status=PENDING`, `course_keys` 에 `java-backend,kafka` 저장

- [ ] **Step 5: 체크포인트 — Phase B 완료**

---

## Phase C: 프론트엔드 — 타입 & API 클라이언트

### Task C1: `frontend/src/lib/types.ts` 업데이트

**Files:**
- Modify: `frontend/src/lib/types.ts`

- [ ] **Step 1: `MentorApplyRequest`/`MentorProfileResponse` 교체 + 코스 타입 추가**

`MentorApplyRequest` (기존) 교체:
```ts
export interface MentorApplyRequest {
  courseKeys: string[];
  techStack?: string[];
  careerYears: number;
  company?: string;
  jobTitle?: string;
  portfolioUrl?: string;
  education?: string;
  certifications?: string[];
  preferredMenteeLevel?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ANY';
  bio?: string;
}
```

`MentorProfileResponse` 교체:
```ts
export interface CourseSummary {
  courseKey: string;
  title: string;
  iconString: string;
}

export interface MentorProfileResponse {
  id: number;
  userId: number;
  name: string;
  email: string;
  courses: CourseSummary[];
  techStack: string[];
  careerYears: number;
  company: string;
  jobTitle: string;
  portfolioUrl: string;
  education: string;
  certifications: string[];
  preferredMenteeLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ANY' | null;
  bio: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectedReason: string | null;
}
```

신규 `MentoringCourseBox`, `MentoringCourseDetail` 타입 추가:
```ts
export interface MentoringCourseBox {
  icon?: string;
  title: string;
  color?: string;
  tags: string[];
  desc: string;
  isWide?: boolean;
}

export interface MentoringCourseDetail {
  id: number;
  courseKey: string;
  title: string;
  subtitle: string;
  iconString: string;
  descriptionTitle: string;
  descriptionText: string;
  boxes: MentoringCourseBox[];
  displayOrder: number;
  active: boolean;
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: 다른 파일에서 기존 타입을 참조해 에러가 날 수 있음 (Task C2/C3/D1/D2 에서 정리) — 에러 목록 기록만.

- [ ] **Step 3: 스테이지**

```bash
git add frontend/src/lib/types.ts
```

---

### Task C2: `frontend/src/lib/courses.ts` 생성

**Files:**
- Create: `frontend/src/lib/courses.ts`

- [ ] **Step 1: 파일 생성**

```ts
import apiClient from './api';
import type { ApiResponse, CourseSummary, MentoringCourseDetail } from './types';

export async function fetchCourses(): Promise<MentoringCourseDetail[]> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail[]>>('/courses');
  return res.data.data;
}

export async function fetchCourse(courseKey: string): Promise<MentoringCourseDetail> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail>>(`/courses/${courseKey}`);
  return res.data.data;
}

export async function fetchCourseSummaries(): Promise<CourseSummary[]> {
  const courses = await fetchCourses();
  return courses.map(c => ({
    courseKey: c.courseKey,
    title: c.title,
    iconString: c.iconString,
  }));
}
```

- [ ] **Step 2: 스테이지**

```bash
git add frontend/src/lib/courses.ts
```

---

### Task C3: `frontend/src/lib/mentor.ts` 생성

**Files:**
- Create: `frontend/src/lib/mentor.ts`

- [ ] **Step 1: 파일 생성**

```ts
import apiClient from './api';
import type { ApiResponse, MentorApplyRequest, MentorProfileResponse } from './types';

export async function applyAsMentor(data: MentorApplyRequest): Promise<MentorProfileResponse> {
  const res = await apiClient.post<ApiResponse<MentorProfileResponse>>('/mentor/apply', data);
  return res.data.data;
}

export async function getMyMentorProfile(): Promise<MentorProfileResponse | null> {
  try {
    const res = await apiClient.get<ApiResponse<MentorProfileResponse>>('/mentor/me');
    return res.data.data;
  } catch (err: any) {
    if (err.response?.status === 404) return null;
    throw err;
  }
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend && npx tsc --noEmit`

- [ ] **Step 3: 스테이지 + C 그룹 커밋**

```bash
git add frontend/src/lib/mentor.ts
git commit -m "feat(frontend): 코스/멘토 API 타입 및 클라이언트 추가"
```

---

## Phase D: 프론트엔드 — 기존 페이지 리팩터

**Pencil 목업 체크 (이 Phase 시작 전):** `mentors/[id]` 페이지의 코스 데이터 API 전환은 UI 변경이 아니라 데이터 소스 전환이므로 목업 불필요. `apply/page.tsx` 도 기존 드롭다운 값만 교체하는 수준이면 생략 가능.

### Task D1: `mentors/[id]/page.tsx` — COURSE_DATA 제거

**Files:**
- Modify: `frontend/src/app/mentors/[id]/page.tsx`

- [ ] **Step 1: `COURSE_DATA` 객체 전체 삭제 (라인 11~422)**

파일 상단의 `const COURSE_DATA: Record<string, any> = { ... };` 블록을 모두 삭제.

- [ ] **Step 2: `useEffect`로 `fetchCourse` 호출하도록 변경**

컴포넌트 상단 useState/useEffect 추가 (위치: 컴포넌트 함수 본문 초입):

```tsx
import { fetchCourse } from '@/lib/courses';
import type { MentoringCourseDetail } from '@/lib/types';

// ...
const params = useParams();
const courseKey = params.id as string;
const [course, setCourse] = useState<MentoringCourseDetail | null>(null);
const [loading, setLoading] = useState(true);

useEffect(() => {
  fetchCourse(courseKey)
    .then(setCourse)
    .catch(() => setCourse(null))
    .finally(() => setLoading(false));
}, [courseKey]);

if (loading) return <div className="min-h-screen flex items-center justify-center">로딩 중…</div>;
if (!course) return <div className="min-h-screen flex items-center justify-center">코스를 찾을 수 없습니다.</div>;
```

기존에 `COURSE_DATA[courseKey]` 를 참조하던 부분을 `course` 변수로 치환. `FALLBACK_DATA` 블록은 제거 (또는 `course` 가 null 인 경우로 통합).

- [ ] **Step 3: dev 서버 기동 + 수동 확인**

```bash
cd frontend && npm run dev
```

브라우저 `http://localhost:3000/mentors/java-backend` 접속 → 기존과 동일한 콘텐츠가 렌더링되는지 확인. `http://localhost:3000/mentors/missing-key` 는 "코스를 찾을 수 없습니다" 표시.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/mentors/[id]/page.tsx
git commit -m "refactor(frontend): mentors/[id] 페이지 COURSE_DATA 하드코딩 제거, API 연동"
```

---

### Task D2: `apply/page.tsx` — category 드롭다운 API 연동

**Files:**
- Modify: `frontend/src/app/apply/page.tsx`

- [ ] **Step 1: 상단에 코스 목록 fetch 훅 추가**

```tsx
import { fetchCourseSummaries } from '@/lib/courses';
import type { CourseSummary } from '@/lib/types';

// 컴포넌트 안:
const [courseOptions, setCourseOptions] = useState<CourseSummary[]>([]);
useEffect(() => {
  fetchCourseSummaries().then(setCourseOptions).catch(() => setCourseOptions([]));
}, []);
```

- [ ] **Step 2: 기존 `category: 'backend'` 하드코딩 부분에 select 드롭다운 도입**

페이지 내 "카테고리" 관련 UI 를 `<select>` 로 교체:
```tsx
<select
  className="..."
  value={form.category}
  onChange={e => setForm({ ...form, category: e.target.value })}
>
  <option value="">선택…</option>
  {courseOptions.map(c => (
    <option key={c.courseKey} value={c.courseKey}>{c.title}</option>
  ))}
</select>
```

기본값 `'backend'` 는 `''` 로 변경하고, 유효성 검사에 `form.category !== ''` 추가.

- [ ] **Step 3: dev 서버에서 수동 확인**

`/apply` 접속 → 카테고리 드롭다운에 12개 옵션 표시 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/apply/page.tsx
git commit -m "refactor(frontend): apply 페이지 카테고리를 코스 API 기반 드롭다운으로 전환"
```

---

## Phase E: 프론트엔드 — 멘토 신규 페이지

**Pencil 목업 체크 (필수):** 이 Phase 시작 전, `/mentor/apply` 와 `/mentor/status` 의 UI 목업을 Pencil MCP 로 만들고 사용자 확인을 받은 뒤 구현 진행. 섹션 4 / 섹션 5 스펙 참조.

### Task E1: `/mentor/apply` 페이지

**Files:**
- Create: `frontend/src/app/mentor/apply/page.tsx`

- [ ] **Step 1: Pencil 목업 준비**

Pencil 로 섹션 A~D 레이아웃 목업 생성 → 사용자 승인. (통과 시 다음 Step 진행)

- [ ] **Step 2: 폼 페이지 구현**

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { fetchCourseSummaries } from '@/lib/courses';
import { applyAsMentor, getMyMentorProfile } from '@/lib/mentor';
import type { CourseSummary, MentorApplyRequest } from '@/lib/types';

export default function MentorApplyPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading } = useAuth();
  const [courses, setCourses] = useState<CourseSummary[]>([]);
  const [form, setForm] = useState<MentorApplyRequest>({
    courseKeys: [],
    techStack: [],
    careerYears: 1,
    company: '',
    jobTitle: '',
    portfolioUrl: '',
    education: '',
    certifications: [],
    preferredMenteeLevel: 'ANY',
    bio: '',
  });
  const [rejectedBanner, setRejectedBanner] = useState<string | null>(null);
  const [techStackInput, setTechStackInput] = useState('');
  const [certInput, setCertInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 가드 + prefill
  useEffect(() => {
    if (isLoading) return;
    if (!isLoggedIn) {
      router.replace('/auth/login?redirect=/mentor/apply');
      return;
    }
    if (user?.role !== 'MENTOR') {
      alert('멘토 계정만 접근 가능합니다');
      router.replace('/');
      return;
    }
    getMyMentorProfile().then(profile => {
      if (!profile) return;
      if (profile.status === 'PENDING' || profile.status === 'APPROVED') {
        router.replace('/mentor/status');
        return;
      }
      // REJECTED → prefill
      setForm({
        courseKeys: profile.courses.map(c => c.courseKey),
        techStack: profile.techStack || [],
        careerYears: profile.careerYears,
        company: profile.company || '',
        jobTitle: profile.jobTitle || '',
        portfolioUrl: profile.portfolioUrl || '',
        education: profile.education || '',
        certifications: profile.certifications || [],
        preferredMenteeLevel: profile.preferredMenteeLevel || 'ANY',
        bio: profile.bio || '',
      });
      setRejectedBanner(profile.rejectedReason || '이전 신청이 반려되었습니다.');
    });
  }, [isLoading, isLoggedIn, user, router]);

  useEffect(() => {
    fetchCourseSummaries().then(setCourses).catch(() => setCourses([]));
  }, []);

  const toggleCourse = (key: string) => {
    setForm(f => ({
      ...f,
      courseKeys: f.courseKeys.includes(key)
        ? f.courseKeys.filter(k => k !== key)
        : [...f.courseKeys, key],
    }));
  };

  const addTag = (field: 'techStack' | 'certifications', value: string, reset: () => void) => {
    const v = value.trim();
    if (!v) return;
    setForm(f => ({ ...f, [field]: [...(f[field] || []), v] }));
    reset();
  };

  const removeTag = (field: 'techStack' | 'certifications', v: string) => {
    setForm(f => ({ ...f, [field]: (f[field] || []).filter(x => x !== v) }));
  };

  const submit = async () => {
    setError(null);
    if (form.courseKeys.length === 0) return setError('멘토링 코스를 최소 1개 선택해주세요.');
    if (!form.careerYears || form.careerYears < 1) return setError('경력은 1년 이상이어야 합니다.');
    setSubmitting(true);
    try {
      await applyAsMentor(form);
      router.replace('/mentor/status');
    } catch (e: any) {
      setError(e.response?.data?.message || '신청 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Header />
      <main className="max-w-3xl mx-auto px-4 py-8 space-y-6">
        <h1 className="text-2xl font-bold">멘토 등록 신청서</h1>
        <p className="text-sm text-gray-600">검토 후 승인까지 영업일 기준 2-3일 소요됩니다.</p>

        {rejectedBanner && (
          <div className="border border-red-300 bg-red-50 text-red-800 rounded p-4">
            이전 신청이 반려되었습니다: <strong>{rejectedBanner}</strong> — 내용을 수정하여 재신청해주세요.
          </div>
        )}

        {/* 섹션 A — 기본 정보 */}
        <section className="border rounded-lg p-5 space-y-4">
          <h2 className="font-semibold">기본 정보</h2>
          <input className="w-full border rounded px-3 py-2" placeholder="현재 직무 (예: 백엔드 개발자)"
                 value={form.jobTitle} onChange={e => setForm({ ...form, jobTitle: e.target.value })} />
          <input className="w-full border rounded px-3 py-2" placeholder="소속 회사"
                 value={form.company} onChange={e => setForm({ ...form, company: e.target.value })} />
          <input className="w-full border rounded px-3 py-2" placeholder="최종 학력"
                 value={form.education} onChange={e => setForm({ ...form, education: e.target.value })} />
        </section>

        {/* 섹션 B — 멘토링 코스 & 경력 */}
        <section className="border rounded-lg p-5 space-y-4">
          <h2 className="font-semibold">멘토링 코스 & 경력</h2>
          <div>
            <label className="text-sm font-medium">가르칠 수 있는 코스 * <span className="text-xs text-gray-500">(최소 1개)</span></label>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2 mt-2">
              {courses.map(c => (
                <label key={c.courseKey} className={`flex items-center gap-2 border rounded px-3 py-2 cursor-pointer ${form.courseKeys.includes(c.courseKey) ? 'bg-blue-50 border-blue-400' : ''}`}>
                  <input type="checkbox" checked={form.courseKeys.includes(c.courseKey)}
                         onChange={() => toggleCourse(c.courseKey)} />
                  <span>{c.iconString} {c.title}</span>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium">세부 기술 스택 (Enter 로 추가)</label>
            <div className="flex flex-wrap gap-2 mt-2">
              {(form.techStack || []).map(t => (
                <span key={t} className="bg-gray-100 rounded px-2 py-1 text-sm">
                  {t} <button onClick={() => removeTag('techStack', t)}>×</button>
                </span>
              ))}
              <input className="border rounded px-2 py-1"
                     value={techStackInput}
                     onChange={e => setTechStackInput(e.target.value)}
                     onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addTag('techStack', techStackInput, () => setTechStackInput('')))} />
            </div>
          </div>

          <input className="w-full border rounded px-3 py-2" type="number" min={1} placeholder="경력 연수 *"
                 value={form.careerYears} onChange={e => setForm({ ...form, careerYears: Number(e.target.value) })} />

          <div>
            <label className="text-sm font-medium">선호 멘티 수준</label>
            <div className="flex gap-4 mt-2">
              {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ANY'] as const).map(lvl => (
                <label key={lvl}><input type="radio" checked={form.preferredMenteeLevel === lvl}
                                          onChange={() => setForm({ ...form, preferredMenteeLevel: lvl })} /> {lvl}</label>
              ))}
            </div>
          </div>
        </section>

        {/* 섹션 C — 포트폴리오 & 자격증 */}
        <section className="border rounded-lg p-5 space-y-4">
          <h2 className="font-semibold">포트폴리오 & 자격증</h2>
          <input className="w-full border rounded px-3 py-2" placeholder="포트폴리오/GitHub URL"
                 value={form.portfolioUrl} onChange={e => setForm({ ...form, portfolioUrl: e.target.value })} />
          <div>
            <label className="text-sm font-medium">자격증 (Enter 로 추가)</label>
            <div className="flex flex-wrap gap-2 mt-2">
              {(form.certifications || []).map(t => (
                <span key={t} className="bg-gray-100 rounded px-2 py-1 text-sm">
                  {t} <button onClick={() => removeTag('certifications', t)}>×</button>
                </span>
              ))}
              <input className="border rounded px-2 py-1"
                     value={certInput}
                     onChange={e => setCertInput(e.target.value)}
                     onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addTag('certifications', certInput, () => setCertInput('')))} />
            </div>
          </div>
        </section>

        {/* 섹션 D — 자기소개 */}
        <section className="border rounded-lg p-5 space-y-4">
          <h2 className="font-semibold">자기소개</h2>
          <textarea className="w-full border rounded px-3 py-2 h-32" maxLength={1000}
                    placeholder="멘토링 경험, 관심 분야, 강점을 자유롭게 작성해주세요"
                    value={form.bio} onChange={e => setForm({ ...form, bio: e.target.value })} />
          <p className="text-xs text-gray-500 text-right">{(form.bio || '').length}/1000</p>
        </section>

        {error && <div className="text-red-600">{error}</div>}

        <div className="flex justify-end gap-3">
          <button className="border rounded px-6 py-2" onClick={() => router.back()}>취소</button>
          <button className="bg-blue-600 text-white rounded px-6 py-2 disabled:opacity-50"
                  disabled={submitting} onClick={submit}>
            {submitting ? '제출 중…' : '신청서 제출'}
          </button>
        </div>
      </main>
      <Footer />
    </>
  );
}
```

- [ ] **Step 3: dev 서버 확인**

`http://localhost:3000/mentor/apply` 접속 → 로그인 후 MENTOR 계정이면 폼 렌더링, MENTEE 계정이면 alert 후 `/` 리다이렉트 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/mentor/apply/page.tsx
git commit -m "feat(frontend): /mentor/apply 신청서 페이지 추가"
```

---

### Task E2: `/mentor/status` 페이지

**Files:**
- Create: `frontend/src/app/mentor/status/page.tsx`

- [ ] **Step 1: Pencil 목업 승인 (3가지 상태)**

PENDING / REJECTED / APPROVED 각 레이아웃 목업 → 사용자 승인.

- [ ] **Step 2: 페이지 구현**

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyMentorProfile } from '@/lib/mentor';
import type { MentorProfileResponse } from '@/lib/types';

export default function MentorStatusPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading } = useAuth();
  const [profile, setProfile] = useState<MentorProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (isLoading) return;
    if (!isLoggedIn) {
      router.replace('/auth/login?redirect=/mentor/status');
      return;
    }
    if (user?.role !== 'MENTOR') {
      router.replace('/');
      return;
    }
    getMyMentorProfile()
      .then(p => {
        if (!p) {
          router.replace('/mentor/apply');
          return;
        }
        setProfile(p);
      })
      .finally(() => setLoading(false));
  }, [isLoading, isLoggedIn, user, router]);

  if (loading || !profile) {
    return <><Header /><main className="min-h-screen flex items-center justify-center">로딩 중…</main><Footer /></>;
  }

  return (
    <>
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-8">
        {profile.status === 'PENDING' && (
          <div className="border rounded-lg p-8 bg-amber-50 border-amber-300">
            <h1 className="text-2xl font-bold text-amber-800">⏳ 멘토 승인 대기 중</h1>
            <p className="mt-2 text-amber-700">검토까지 영업일 기준 2-3일 소요됩니다.</p>
            <ApplicationSummary profile={profile} />
          </div>
        )}

        {profile.status === 'REJECTED' && (
          <div className="border rounded-lg p-8 bg-red-50 border-red-300">
            <h1 className="text-2xl font-bold text-red-800">⚠ 신청이 반려되었습니다</h1>
            <p className="mt-3 text-red-900"><strong>반려 사유:</strong> {profile.rejectedReason || '(사유 미기재)'}</p>
            <ApplicationSummary profile={profile} />
            <button className="mt-4 bg-blue-600 text-white rounded px-6 py-2"
                    onClick={() => router.push('/mentor/apply')}>수정 후 재신청</button>
          </div>
        )}

        {profile.status === 'APPROVED' && (
          <div className="border rounded-lg p-8 bg-green-50 border-green-300">
            <h1 className="text-2xl font-bold text-green-800">✅ 멘토 승인 완료</h1>
            <p className="mt-2 text-green-700">이제 멘토링을 시작할 수 있습니다.</p>
            <div className="mt-4 flex gap-3">
              <button className="border rounded px-6 py-2" onClick={() => router.push('/')}>홈으로</button>
              <button className="bg-blue-600 text-white rounded px-6 py-2"
                      onClick={() => router.push('/lms/assignments')}>멘토 대시보드</button>
            </div>
          </div>
        )}
      </main>
      <Footer />
    </>
  );
}

function ApplicationSummary({ profile }: { profile: MentorProfileResponse }) {
  return (
    <div className="mt-5 border-t pt-4 space-y-1 text-sm">
      <p><strong>멘토링 코스:</strong> {profile.courses.map(c => c.title).join(', ')}</p>
      <p><strong>경력:</strong> {profile.careerYears}년</p>
      {profile.company && <p><strong>소속:</strong> {profile.company} / {profile.jobTitle}</p>}
      {profile.education && <p><strong>학력:</strong> {profile.education}</p>}
    </div>
  );
}
```

- [ ] **Step 3: dev 서버 확인**

MENTOR 계정 로그인 후 `/mentor/status` → PENDING 뷰 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/mentor/status/page.tsx
git commit -m "feat(frontend): /mentor/status 상태 페이지 추가 (PENDING/REJECTED/APPROVED)"
```

---

## Phase F: 프론트엔드 — 흐름 통합

### Task F1: `AuthContext` — 멘토 상태 조회

**Files:**
- Modify: `frontend/src/contexts/AuthContext.tsx`

- [ ] **Step 1: 기존 `AuthContext` 파일 확인**

Run: `cat frontend/src/contexts/AuthContext.tsx`

- [ ] **Step 2: `mentorStatus` 필드 추가**

컨텍스트 타입 및 `useState` 에 `mentorStatus: 'PENDING'|'APPROVED'|'REJECTED'|null` 필드를 추가하고, 로그인/초기 로드 시 `user.role === 'MENTOR'` 이면 `getMyMentorProfile()` 을 호출해 상태를 채움. 404 / null 이면 `mentorStatus = null`.

구체 수정 — 기존 구조에 맞춰 다음 요소를 삽입:
```tsx
import { getMyMentorProfile } from '@/lib/mentor';

// 컨텍스트 타입에 추가:
mentorStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
refreshMentorStatus: () => Promise<void>;

// state:
const [mentorStatus, setMentorStatus] = useState<'PENDING'|'APPROVED'|'REJECTED'|null>(null);

const refreshMentorStatus = async () => {
  if (!user || user.role !== 'MENTOR') { setMentorStatus(null); return; }
  const p = await getMyMentorProfile();
  setMentorStatus(p?.status ?? null);
};

// 초기 로드 및 로그인 성공 후 useEffect 에서 호출
useEffect(() => { refreshMentorStatus(); }, [user?.id, user?.role]);

// context value 에 추가:
{ user, isLoggedIn, isLoading, signup, login, logout, mentorStatus, refreshMentorStatus }
```

- [ ] **Step 3: 타입체크**

Run: `cd frontend && npx tsc --noEmit`

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/contexts/AuthContext.tsx
git commit -m "feat(frontend): AuthContext 에 mentorStatus 상태 추가"
```

---

### Task F2: `Header.tsx` — 상태 뱃지 + 조건부 메뉴

**Files:**
- Modify: `frontend/src/components/layout/Header.tsx`

- [ ] **Step 1: 기존 파일 확인**

Run: `cat frontend/src/components/layout/Header.tsx`

- [ ] **Step 2: 상태 뱃지 렌더링 + 멘토 전용 메뉴 조건**

```tsx
const { user, mentorStatus } = useAuth();

// 기존 roleLabel 계산 대신 상태 포함 표기:
const mentorBadge = user?.role === 'MENTOR' && mentorStatus && mentorStatus !== 'APPROVED' ? (
  <span className={`ml-2 text-xs px-2 py-0.5 rounded ${
    mentorStatus === 'PENDING' ? 'bg-amber-100 text-amber-800' :
    mentorStatus === 'REJECTED' ? 'bg-red-100 text-red-800' : ''
  }`}>
    {mentorStatus === 'PENDING' ? '승인 대기중' : '반려됨'}
  </span>
) : null;

// 멘토 전용 메뉴는 APPROVED 일 때만 렌더:
{user?.role === 'MENTOR' && mentorStatus === 'APPROVED' && (
  <Link href="/lms/assignments">LMS 배정 목록</Link>
)}
```

- [ ] **Step 3: dev 서버 수동 확인**

PENDING 계정으로 로그인 → 이름 옆 "승인 대기중" 뱃지 표시, `LMS 배정 목록` 메뉴 미노출 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/layout/Header.tsx
git commit -m "feat(frontend): Header 에 멘토 상태 뱃지 + 승인 시에만 전용 메뉴 노출"
```

---

### Task F3: 로그인 후 자동 리다이렉트 (`auth/login/page.tsx`)

**Files:**
- Modify: `frontend/src/app/auth/login/page.tsx`

- [ ] **Step 1: 기존 login 성공 처리 위치 확인**

Run: `cat frontend/src/app/auth/login/page.tsx | head -80`

- [ ] **Step 2: 로그인 성공 후 분기 로직 추가**

기존 login 함수 호출 직후 (`router.push` 직전)에 분기:
```tsx
import { getMyMentorProfile } from '@/lib/mentor';

// onLogin 내부, login() 성공 후:
const me = await apiClient.get('/users/me').then(r => r.data.data);
if (me.role === 'MENTOR') {
  const profile = await getMyMentorProfile();
  if (!profile || profile.status === 'REJECTED') {
    router.replace('/mentor/apply');
  } else if (profile.status === 'PENDING') {
    router.replace('/mentor/status');
  } else {
    router.replace('/');
  }
} else {
  router.replace(redirect || '/');
}
```

기존 `signup=success` 쿼리 파라미터 기반 토스트 메시지는 유지.

- [ ] **Step 3: dev 서버 수동 확인**

신규 MENTOR 가입 → 로그인 → `/mentor/apply` 자동 이동 확인.  
PENDING MENTOR 로그인 → `/mentor/status` 이동 확인.  
MENTEE 로그인 → `/` 이동 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/auth/login/page.tsx
git commit -m "feat(frontend): 로그인 성공 후 role/mentorStatus 기반 자동 리다이렉트"
```

---

## Phase G: E2E 수동 검증 & 최종 점검

### Task G1: 골든 패스 E2E

- [ ] **Step 1: DB 초기화**

```bash
mysql -u <user> -p devmatch_db -e "
  DROP TABLE IF EXISTS mentor_profile_courses;
  DROP TABLE IF EXISTS mentor_profile_history;
  DROP TABLE IF EXISTS mentor_profiles;
  DELETE FROM users WHERE email LIKE 'e2e-%';
"
```

- [ ] **Step 2: 백엔드·프론트 재기동**

```bash
# 터미널 1: 백엔드 IntelliJ Rebuild + Run
# 터미널 2
cd frontend && npm run dev
```

- [ ] **Step 3: 시나리오 — 신규 멘토 가입**

1. `http://localhost:3000/auth/signup` 접속
2. email: `e2e-mentor@test.com`, role: MENTOR 선택, 가입
3. 가입 후 로그인 페이지에서 로그인
4. **검증**: 자동으로 `/mentor/apply` 이동 확인
5. 헤더에 "MENTOR · 승인 대기중" 뱃지가 아직 없음 확인 (프로필 생성 전)

- [ ] **Step 4: 시나리오 — 신청 제출**

1. 폼에 유효 데이터 입력 (코스 2개 선택, 경력 3년 등) → 제출
2. **검증**: `/mentor/status` 로 리다이렉트, PENDING 카드 렌더링
3. 헤더 뱃지 "승인 대기중" 표시 확인
4. `/lms/assignments` URL 직접 접근 시 차단 또는 리다이렉트 확인

- [ ] **Step 5: 시나리오 — 관리자 반려 (DB 수동)**

```bash
mysql -u <user> -p devmatch_db -e "
  UPDATE mentor_profiles SET status='REJECTED' WHERE user_id=(SELECT id FROM users WHERE email='e2e-mentor@test.com');
  UPDATE mentor_profile_history SET status='REJECTED', rejected_reason='포트폴리오 URL 실패' WHERE user_id=(SELECT id FROM users WHERE email='e2e-mentor@test.com');
"
```

- [ ] **Step 6: 시나리오 — 재신청**

1. 페이지 새로고침 → `/mentor/status` 의 REJECTED 뷰 렌더링, 반려 사유 "포트폴리오 URL 실패" 표시 확인
2. 헤더 뱃지 "반려됨" 표시 확인
3. "수정 후 재신청" 클릭 → `/mentor/apply` 이동, 이전 값 prefill 확인, 상단 반려 배너 확인
4. 수정 후 제출 → `/mentor/status` PENDING 으로 다시 전환 확인
5. DB 확인: `mentor_profile_history` 에 2번째 행이 PENDING 으로 insert 되었는지

- [ ] **Step 7: 시나리오 — 관리자 승인 (DB 수동)**

```bash
mysql -u <user> -p devmatch_db -e "
  UPDATE mentor_profiles SET status='APPROVED' WHERE user_id=(SELECT id FROM users WHERE email='e2e-mentor@test.com');
"
```

새로고침 → `/mentor/status` 의 APPROVED 뷰 + 헤더 뱃지 사라지고 "멘토" 기본 라벨 + `LMS 배정 목록` 메뉴 노출 확인.

- [ ] **Step 8: 최종 백엔드 테스트 전체 실행**

Run: `./gradlew -p backend test`
Expected: `BUILD SUCCESSFUL`, 신규 추가 테스트들(CourseServiceTest + MentorServiceTest) 모두 PASS.

- [ ] **Step 9: 최종 타입체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: 타입 에러 없음, `BUILD SUCCESSFUL`

- [ ] **Step 10: 최종 체크 커밋 (필요 시)**

발견된 마이너 이슈 수정 후 커밋.

---

## 참고 문서

- 스펙: [`docs/superpowers/specs/2026-04-18-mentor-application-flow-design.md`](../specs/2026-04-18-mentor-application-flow-design.md)
- 전제조건 에러 로그: [`error/2026-04-18-signup-role-always-mentee-stale-build.md`](../../../error/2026-04-18-signup-role-always-mentee-stale-build.md)
- 기존 코스 데이터 원본: `frontend/src/app/mentors/[id]/page.tsx` (Task D1 에서 제거)

package com.devmatch.config;

import com.devmatch.entity.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final PasswordEncoder passwordEncoder;
    private final MentoringCourseRepository mentoringCourseRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchingRepository matchingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        initMentoringCourses();
        initDefaultAdmin();

        if (testRepository.count() == 0) {
            log.info("===== 테스트 초기 데이터 삽입 시작 =====");
            initJavaTests();
            initSpringTests();
            initReactTests();
            initPythonTests();
            initAlgorithmTests();
            initMentors();
            log.info("===== 초기 데이터 삽입 완료: 테스트 {}개, 문제 {}개 =====",
                    testRepository.count(), questionRepository.count());
        } else {
            log.info("테스트 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
        }

        initSamplePayments();
    }

    // ──────────────────────────────────────────────
    //  Java
    // ──────────────────────────────────────────────

    private void initJavaTests() {
        // BEGINNER
        Test t = createTest("Java 기초 문법 테스트",
                "Java 언어의 기본 문법과 핵심 개념을 평가합니다. 변수, 타입, 조건문, 반복문, 배열, 클래스 기초 등을 다룹니다.",
                "java-backend", Difficulty.BEGINNER, 15, 60, 10);
        q(t, 1, "Java에서 정수형 변수를 선언하는 올바른 방법은?",
                List.of("int number = 10;", "number int = 10;", "integer number = 10;", "var: int number = 10;"), 0);
        q(t, 2, "Java에서 문자열을 비교할 때 올바른 방법은?",
                List.of("str1 == str2", "str1.equals(str2)", "str1.compare(str2)", "str1.match(str2)"), 1);
        q(t, 3, "다음 중 Java의 기본 자료형(primitive type)이 아닌 것은?",
                List.of("int", "boolean", "String", "double"), 2);
        q(t, 4, "Java에서 배열의 길이를 구하는 방법은?",
                List.of("arr.size()", "arr.length", "arr.count()", "len(arr)"), 1);
        q(t, 5, "다음 코드의 출력 결과는?\nint x = 5;\nSystem.out.println(x++);",
                List.of("4", "5", "6", "컴파일 에러"), 1);
        q(t, 6, "Java에서 상수를 선언할 때 사용하는 키워드는?",
                List.of("const", "final", "static", "immutable"), 1);
        q(t, 7, "다음 중 반복문이 아닌 것은?",
                List.of("for", "while", "do-while", "switch"), 3);
        q(t, 8, "Java에서 클래스를 상속할 때 사용하는 키워드는?",
                List.of("implements", "inherits", "extends", "super"), 2);
        q(t, 9, "접근 제어자 중 같은 패키지 내에서만 접근 가능한 것은?",
                List.of("private", "default (접근 제어자 없음)", "protected", "public"), 1);
        q(t, 10, "main 메서드의 올바른 선언은?",
                List.of("public void main(String args)", "public static void main(String[] args)",
                        "static void main(String args[])", "public static main(String[] args)"), 1);

        // INTERMEDIATE
        t = createTest("Java 중급 심화 테스트",
                "컬렉션, 제네릭, 스트림, 람다, OOP 심화 원칙 등 Java 중급 개념을 평가합니다.",
                "java-backend", Difficulty.INTERMEDIATE, 20, 65, 10);
        q(t, 1, "ArrayList와 LinkedList의 차이에 대한 설명으로 올바른 것은?",
                List.of("ArrayList는 삽입/삭제가 빠르다", "LinkedList는 인덱스 접근이 빠르다",
                        "ArrayList는 인덱스 접근이 O(1)이다", "둘 다 동기화가 보장된다"), 2);
        q(t, 2, "Java 제네릭에서 와일드카드 '? extends Number'의 의미는?",
                List.of("Number를 상속한 모든 타입", "Number만 허용", "Number의 부모 타입만 허용", "모든 타입 허용"), 0);
        q(t, 3, "다음 중 함수형 인터페이스가 아닌 것은?",
                List.of("Runnable", "Comparator", "List", "Predicate"), 2);
        q(t, 4, "Stream API에서 중간 연산(intermediate operation)이 아닌 것은?",
                List.of("filter()", "map()", "collect()", "sorted()"), 2);
        q(t, 5, "Java에서 checked exception과 unchecked exception의 차이는?",
                List.of("checked는 컴파일 시점에 처리가 강제된다", "unchecked는 컴파일 시점에 처리가 강제된다",
                        "둘 다 반드시 try-catch가 필요하다", "차이가 없다"), 0);
        q(t, 6, "SOLID 원칙 중 'O'에 해당하는 원칙은?",
                List.of("단일 책임 원칙", "개방-폐쇄 원칙", "리스코프 치환 원칙", "의존 역전 원칙"), 1);
        q(t, 7, "HashMap의 시간 복잡도로 올바른 것은? (평균)",
                List.of("검색 O(n), 삽입 O(n)", "검색 O(1), 삽입 O(1)",
                        "검색 O(log n), 삽입 O(log n)", "검색 O(1), 삽입 O(n)"), 1);
        q(t, 8, "Optional 클래스의 주요 목적은?",
                List.of("성능 향상", "NullPointerException 방지", "멀티스레드 지원", "직렬화 지원"), 1);
        q(t, 9, "다음 코드의 결과는?\nList.of(1,2,3,4,5).stream().filter(n -> n > 2).count();",
                List.of("2", "3", "5", "컴파일 에러"), 1);
        q(t, 10, "인터페이스에서 default 메서드의 특징으로 올바른 것은?",
                List.of("추상 메서드이다", "구현 클래스에서 반드시 오버라이드해야 한다",
                        "메서드 본문(body)을 가질 수 있다", "static이어야 한다"), 2);

        // ADVANCED
        t = createTest("Java 고급 아키텍처 테스트",
                "JVM 내부 구조, GC 알고리즘, 동시성, 디자인 패턴 등 Java 고급 지식을 평가합니다.",
                "java-backend", Difficulty.ADVANCED, 25, 70, 10);
        q(t, 1, "JVM 메모리 영역 중 객체 인스턴스가 저장되는 곳은?",
                List.of("Stack", "Heap", "Method Area", "PC Register"), 1);
        q(t, 2, "G1 GC의 특징으로 올바르지 않은 것은?",
                List.of("Region 기반으로 힙을 관리한다", "STW(Stop-The-World) 시간을 예측 가능하게 한다",
                        "Young/Old 영역이 물리적으로 고정되어 있다", "Java 9부터 기본 GC이다"), 2);
        q(t, 3, "volatile 키워드의 역할은?",
                List.of("변수를 상수로 만든다", "변수의 가시성(visibility)을 보장한다",
                        "원자성(atomicity)을 보장한다", "변수를 직렬화 가능하게 한다"), 1);
        q(t, 4, "synchronized와 ReentrantLock의 차이로 올바른 것은?",
                List.of("synchronized가 더 유연하다", "ReentrantLock은 tryLock으로 타임아웃이 가능하다",
                        "둘 다 인터럽트 처리가 불가능하다", "ReentrantLock은 암묵적 잠금이다"), 1);
        q(t, 5, "싱글턴 패턴을 thread-safe하게 구현하는 가장 권장되는 방법은?",
                List.of("public static 필드", "synchronized 메서드", "LazyHolder (정적 내부 클래스)",
                        "일반 private static 변수"), 2);
        q(t, 6, "ConcurrentHashMap이 HashTable보다 성능이 좋은 이유는?",
                List.of("해시 함수가 더 빠르다", "세그먼트/버킷 단위로 락을 건다",
                        "동기화를 하지 않는다", "LinkedList 대신 배열을 사용한다"), 1);
        q(t, 7, "Java 리플렉션(Reflection)의 단점이 아닌 것은?",
                List.of("성능 오버헤드", "컴파일 타임 타입 체크 불가", "런타임에 클래스 정보 접근 가능",
                        "캡슐화 위반 가능성"), 2);
        q(t, 8, "CompletableFuture에서 thenApply와 thenCompose의 차이는?",
                List.of("차이 없다", "thenApply는 값 변환, thenCompose는 비동기 체이닝",
                        "thenCompose는 동기, thenApply는 비동기", "thenApply만 예외 처리 가능"), 1);
        q(t, 9, "클래스 로더의 동작 원칙 중 '위임 모델(Delegation Model)'이란?",
                List.of("자식 클래스 로더가 먼저 로딩을 시도한다",
                        "부모 클래스 로더에게 먼저 위임하고, 없으면 자식이 로딩한다",
                        "모든 클래스 로더가 동시에 로딩을 시도한다",
                        "Application 클래스 로더만 사용한다"), 1);
        q(t, 10, "Java의 String이 불변(immutable)인 주요 이유가 아닌 것은?",
                List.of("String Pool을 통한 메모리 최적화", "해시코드 캐싱으로 HashMap 성능 향상",
                        "스레드 안전성 보장", "가비지 컬렉션 성능 향상"), 3);
    }

    // ──────────────────────────────────────────────
    //  Spring
    // ──────────────────────────────────────────────

    private void initSpringTests() {
        // BEGINNER
        Test t = createTest("Spring 입문 테스트",
                "Spring Boot의 기본 개념, IoC/DI, 어노테이션, REST API 기초를 평가합니다.",
                "java-backend", Difficulty.BEGINNER, 15, 60, 10);
        q(t, 1, "Spring의 핵심 개념인 IoC(Inversion of Control)란?",
                List.of("개발자가 직접 객체를 생성하고 관리한다", "프레임워크가 객체의 생성과 생명주기를 관리한다",
                        "모든 객체를 싱글턴으로 만든다", "인터페이스를 반드시 사용해야 한다"), 1);
        q(t, 2, "Spring Boot 프로젝트에서 설정 파일의 기본 이름은?",
                List.of("config.xml", "settings.properties", "application.properties (또는 application.yml)", "spring.conf"), 2);
        q(t, 3, "@Controller와 @RestController의 차이는?",
                List.of("차이 없다", "@RestController는 @ResponseBody가 포함되어 있다",
                        "@Controller는 REST API 전용이다", "@RestController는 View를 반환한다"), 1);
        q(t, 4, "Spring에서 의존성 주입(DI) 방법이 아닌 것은?",
                List.of("생성자 주입", "Setter 주입", "필드 주입", "Static 주입"), 3);
        q(t, 5, "@Autowired 어노테이션의 역할은?",
                List.of("빈을 등록한다", "의존성을 자동으로 주입한다", "트랜잭션을 관리한다", "URL 매핑을 한다"), 1);
        q(t, 6, "Spring Boot에서 내장 서버로 사용되는 것은?",
                List.of("Apache HTTP Server", "Nginx", "Tomcat", "IIS"), 2);
        q(t, 7, "@RequestMapping의 HTTP 메서드별 축약 어노테이션이 아닌 것은?",
                List.of("@GetMapping", "@PostMapping", "@SendMapping", "@DeleteMapping"), 2);
        q(t, 8, "Spring Bean의 기본 스코프는?",
                List.of("prototype", "singleton", "request", "session"), 1);
        q(t, 9, "@Service 어노테이션의 역할은?",
                List.of("데이터베이스 접근 계층 표시", "비즈니스 로직 계층의 빈 등록",
                        "컨트롤러 계층 표시", "설정 클래스 표시"), 1);
        q(t, 10, "application.yml에서 서버 포트를 변경하는 설정은?",
                List.of("app.port: 9090", "server.port: 9090", "spring.port: 9090", "web.port: 9090"), 1);

        // INTERMEDIATE
        t = createTest("Spring 중급 실무 테스트",
                "Spring MVC 동작 흐름, JPA 매핑, 트랜잭션, Spring Security 기초 등을 평가합니다.",
                "java-backend", Difficulty.INTERMEDIATE, 20, 65, 10);
        q(t, 1, "Spring MVC에서 클라이언트 요청의 처리 순서로 올바른 것은?",
                List.of("Filter → Controller → Service → DispatcherServlet",
                        "DispatcherServlet → HandlerMapping → Controller → ViewResolver",
                        "Controller → DispatcherServlet → View",
                        "HandlerMapping → Filter → Controller → Service"), 1);
        q(t, 2, "JPA에서 @ManyToOne 관계의 기본 fetch 전략은?",
                List.of("LAZY", "EAGER", "NONE", "SELECT"), 1);
        q(t, 3, "@Transactional(readOnly = true)의 효과는?",
                List.of("읽기도 불가능하다", "JPA 변경 감지(Dirty Checking)를 수행하지 않아 성능이 향상된다",
                        "트랜잭션이 적용되지 않는다", "캐시가 비활성화된다"), 1);
        q(t, 4, "Spring Security에서 비밀번호 암호화에 사용되는 인터페이스는?",
                List.of("Encoder", "PasswordEncoder", "CryptoService", "HashProvider"), 1);
        q(t, 5, "JPA N+1 문제의 해결 방법이 아닌 것은?",
                List.of("Fetch Join", "@EntityGraph", "Batch Size 설정", "@Lazy 어노테이션"), 3);
        q(t, 6, "AOP에서 @Around 어드바이스의 특징은?",
                List.of("메서드 실행 전에만 동작한다", "메서드 실행 후에만 동작한다",
                        "메서드 실행 전후 모두 동작하며 실행 자체를 제어할 수 있다",
                        "예외 발생 시에만 동작한다"), 2);
        q(t, 7, "Spring에서 프로파일(Profile)의 용도는?",
                List.of("코드 성능 분석", "환경별(dev/prod) 설정 분리",
                        "사용자 프로필 관리", "로깅 레벨 설정"), 1);
        q(t, 8, "JPA에서 영속성 컨텍스트의 1차 캐시 역할은?",
                List.of("데이터베이스 쿼리 결과를 캐싱한다", "같은 트랜잭션 내 동일 엔티티 조회 시 DB 접근 없이 반환한다",
                        "Redis와 연동하여 캐시한다", "모든 엔티티를 메모리에 유지한다"), 1);
        q(t, 9, "@Valid와 @Validated의 차이는?",
                List.of("차이 없다", "@Valid는 JSR-303 표준, @Validated는 Spring 전용으로 그룹 검증을 지원한다",
                        "@Validated는 Java 표준이다", "@Valid만 Controller에서 사용 가능하다"), 1);
        q(t, 10, "ResponseEntity를 사용하는 이유는?",
                List.of("성능 향상", "HTTP 상태 코드와 헤더를 직접 제어할 수 있다",
                        "자동으로 JSON 변환이 된다", "보안이 강화된다"), 1);

        // ADVANCED
        t = createTest("Spring 고급 아키텍처 테스트",
                "트랜잭션 전파/격리, 영속성 컨텍스트, 캐시, MSA 패턴 등 고급 지식을 평가합니다.",
                "java-backend", Difficulty.ADVANCED, 25, 70, 10);
        q(t, 1, "트랜잭션 전파 속성 REQUIRES_NEW의 동작은?",
                List.of("기존 트랜잭션에 참여한다", "항상 새로운 트랜잭션을 시작하고 기존 트랜잭션을 보류한다",
                        "트랜잭션 없이 실행한다", "기존 트랜잭션이 없으면 예외를 발생시킨다"), 1);
        q(t, 2, "트랜잭션 격리 수준 중 Phantom Read를 방지하는 최소 수준은?",
                List.of("READ_UNCOMMITTED", "READ_COMMITTED", "REPEATABLE_READ", "SERIALIZABLE"), 3);
        q(t, 3, "JPA에서 OSIV(Open Session In View)를 false로 설정하는 이유는?",
                List.of("성능 향상을 위해", "영속성 컨텍스트를 트랜잭션 범위로 제한하여 지연로딩 문제를 명확히 하기 위해",
                        "보안을 위해", "메모리 절약을 위해"), 1);
        q(t, 4, "Spring에서 @Async의 주의사항으로 올바른 것은?",
                List.of("같은 클래스 내부 호출에서도 동작한다", "별도의 설정 없이 사용 가능하다",
                        "프록시 기반이므로 같은 클래스 내부 호출에서는 동작하지 않는다",
                        "반드시 void 반환 타입이어야 한다"), 2);
        q(t, 5, "MSA에서 서비스 간 데이터 일관성을 위한 패턴은?",
                List.of("2PC만 사용", "SAGA 패턴", "모든 서비스가 같은 DB 사용", "캐시로 해결"), 1);
        q(t, 6, "Spring Batch의 핵심 구성요소가 아닌 것은?",
                List.of("Job", "Step", "ItemReader/ItemWriter", "DispatcherServlet"), 3);
        q(t, 7, "JPA에서 벌크 연산(Bulk Operation) 후 주의해야 할 점은?",
                List.of("커넥션을 닫아야 한다", "영속성 컨텍스트를 초기화해야 한다",
                        "인덱스를 재구성해야 한다", "트랜잭션을 수동 커밋해야 한다"), 1);
        q(t, 8, "Circuit Breaker 패턴의 목적은?",
                List.of("네트워크 암호화", "장애가 전파되는 것을 방지하고 빠르게 실패를 반환한다",
                        "로드 밸런싱", "서비스 디스커버리"), 1);
        q(t, 9, "Spring Cache에서 @CacheEvict의 역할은?",
                List.of("캐시에 데이터를 저장한다", "캐시에서 데이터를 삭제한다",
                        "캐시를 생성한다", "캐시 상태를 조회한다"), 1);
        q(t, 10, "QueryDSL을 JPA와 함께 사용하는 이유로 올바르지 않은 것은?",
                List.of("컴파일 시점에 쿼리 오류를 검출할 수 있다", "동적 쿼리 작성이 용이하다",
                        "타입 안전한 쿼리를 작성할 수 있다", "SQL 성능이 자동으로 최적화된다"), 3);
    }

    // ──────────────────────────────────────────────
    //  React
    // ──────────────────────────────────────────────

    private void initReactTests() {
        // BEGINNER
        Test t = createTest("React 기초 테스트",
                "JSX 문법, 컴포넌트, Props, useState, 이벤트 핸들링 등 React 기초를 평가합니다.",
                "frontend", Difficulty.BEGINNER, 15, 60, 10);
        q(t, 1, "React에서 컴포넌트의 상태를 관리하기 위해 사용하는 Hook은?",
                List.of("useEffect", "useContext", "useState", "useRef"), 2);
        q(t, 2, "JSX에서 JavaScript 표현식을 사용할 때 감싸는 기호는?",
                List.of("( )", "{ }", "[ ]", "< >"), 1);
        q(t, 3, "React 컴포넌트에서 부모로부터 데이터를 전달받는 방법은?",
                List.of("state", "props", "context", "ref"), 1);
        q(t, 4, "다음 중 올바른 이벤트 핸들링 방법은?",
                List.of("<button onclick='handleClick()'>", "<button onClick={handleClick}>",
                        "<button on-click={handleClick}>", "<button click={handleClick}>"), 1);
        q(t, 5, "React에서 리스트를 렌더링할 때 각 요소에 필요한 속성은?",
                List.of("id", "name", "key", "index"), 2);
        q(t, 6, "함수형 컴포넌트의 올바른 선언 방법은?",
                List.of("function App { return <div/> }", "function App() { return <div/> }",
                        "class App() { return <div/> }", "def App(): return <div/>"), 1);
        q(t, 7, "React에서 조건부 렌더링에 사용할 수 없는 방법은?",
                List.of("삼항 연산자 (? :)", "&& 연산자", "if-else (JSX 내부에서 직접)", "조건 함수 호출"), 2);
        q(t, 8, "useState의 반환값은?",
                List.of("현재 상태값만", "상태 변경 함수만", "[현재 상태값, 상태 변경 함수]", "{state, setState}"), 2);
        q(t, 9, "React 프로젝트를 생성할 때 사용하는 도구가 아닌 것은?",
                List.of("create-react-app", "Vite", "Next.js", "Maven"), 3);
        q(t, 10, "컴포넌트가 화면에서 사라질 때를 뜻하는 용어는?",
                List.of("Mounting", "Updating", "Unmounting", "Rendering"), 2);

        // INTERMEDIATE
        t = createTest("React 중급 실무 테스트",
                "useEffect, Custom Hook, 상태 관리, React Router, 성능 최적화 등을 평가합니다.",
                "frontend", Difficulty.INTERMEDIATE, 20, 65, 10);
        q(t, 1, "useEffect의 의존성 배열(dependency array)이 빈 배열 []일 때 동작은?",
                List.of("매 렌더링마다 실행", "컴포넌트 마운트 시 1회만 실행",
                        "상태 변경 시마다 실행", "실행되지 않는다"), 1);
        q(t, 2, "React.memo의 역할은?",
                List.of("메모리를 절약한다", "props가 변경되지 않으면 리렌더링을 방지한다",
                        "state를 캐싱한다", "이벤트 핸들러를 최적화한다"), 1);
        q(t, 3, "Custom Hook의 이름 규칙은?",
                List.of("get으로 시작", "use로 시작", "hook으로 시작", "규칙 없음"), 1);
        q(t, 4, "useCallback과 useMemo의 차이는?",
                List.of("차이 없다", "useCallback은 함수를 메모이제이션, useMemo는 값을 메모이제이션",
                        "useMemo는 함수를 메모이제이션, useCallback은 값을 메모이제이션",
                        "useCallback은 클래스 컴포넌트 전용"), 1);
        q(t, 5, "React Router에서 동적 경로 파라미터를 가져오는 Hook은?",
                List.of("useRouter()", "useParams()", "useQuery()", "useLocation()"), 1);
        q(t, 6, "Context API의 단점은?",
                List.of("사용법이 어렵다", "Provider 값이 변경되면 모든 Consumer가 리렌더링된다",
                        "함수형 컴포넌트에서 사용 불가", "전역 상태 관리가 불가능하다"), 1);
        q(t, 7, "useRef의 용도가 아닌 것은?",
                List.of("DOM 요소에 직접 접근", "렌더링 간 값을 유지 (리렌더링 없이)",
                        "상태를 변경하고 리렌더링을 트리거", "이전 값을 저장"), 2);
        q(t, 8, "비동기 데이터 페칭 시 useEffect에서 async를 직접 사용할 수 없는 이유는?",
                List.of("문법 오류이다", "useEffect의 콜백은 cleanup 함수만 반환해야 하는데, async는 Promise를 반환하기 때문",
                        "React가 async를 지원하지 않는다", "성능 문제 때문"), 1);
        q(t, 9, "React에서 폼 입력을 관리하는 두 가지 방식은?",
                List.of("Client/Server", "Controlled/Uncontrolled", "Sync/Async", "Static/Dynamic"), 1);
        q(t, 10, "Error Boundary의 특징으로 올바른 것은?",
                List.of("함수형 컴포넌트에서만 사용 가능하다", "이벤트 핸들러의 에러도 잡는다",
                        "클래스 컴포넌트로만 구현 가능하다 (componentDidCatch)",
                        "비동기 에러도 자동으로 잡는다"), 2);

        // ADVANCED
        t = createTest("React 고급 심화 테스트",
                "가상 DOM, Fiber, 서버 컴포넌트, Concurrent 기능, 렌더링 전략 등 고급 주제를 평가합니다.",
                "frontend", Difficulty.ADVANCED, 25, 70, 10);
        q(t, 1, "React의 가상 DOM(Virtual DOM)이 성능에 기여하는 방식은?",
                List.of("DOM을 사용하지 않는다", "변경된 부분만 계산(diff)하여 실제 DOM에 최소한의 업데이트를 적용한다",
                        "모든 DOM을 매번 다시 생성한다", "WebWorker에서 DOM을 처리한다"), 1);
        q(t, 2, "React Fiber 아키텍처의 핵심 개선점은?",
                List.of("번들 사이즈 감소", "렌더링 작업을 분할(time-slicing)하여 우선순위 기반으로 처리할 수 있다",
                        "TypeScript 지원", "CSS-in-JS 지원"), 1);
        q(t, 3, "React Server Components(RSC)의 특징이 아닌 것은?",
                List.of("서버에서만 실행된다", "클라이언트 번들에 포함되지 않는다",
                        "useState를 사용할 수 있다", "데이터베이스에 직접 접근 가능하다"), 2);
        q(t, 4, "Suspense의 역할은?",
                List.of("에러를 처리한다", "비동기 작업이 완료될 때까지 fallback UI를 보여준다",
                        "컴포넌트를 지연 로딩한다", "상태를 초기화한다"), 1);
        q(t, 5, "Next.js에서 SSR, SSG, ISR의 공통점은?",
                List.of("모두 클라이언트에서 렌더링된다", "모두 서버 측에서 HTML을 생성한다",
                        "모두 실시간으로 데이터를 반영한다", "모두 CDN 캐싱이 불가능하다"), 1);
        q(t, 6, "Concurrent Mode에서 useTransition의 용도는?",
                List.of("페이지 전환 애니메이션", "우선순위가 낮은 상태 업데이트를 표시하여 UI 블로킹을 방지한다",
                        "데이터베이스 트랜잭션 관리", "CSS 트랜지션 제어"), 1);
        q(t, 7, "React의 재조정(Reconciliation) 알고리즘에서 key가 중요한 이유는?",
                List.of("스타일링을 위해", "리스트 요소의 동일성을 판별하여 불필요한 DOM 조작을 최소화하기 위해",
                        "이벤트 바인딩을 위해", "접근성(a11y)을 위해"), 1);
        q(t, 8, "Hydration이란?",
                List.of("CSS 적용 과정", "서버에서 렌더링된 HTML에 클라이언트 JavaScript 이벤트와 상태를 연결하는 과정",
                        "데이터 직렬화", "메모리 해제"), 1);
        q(t, 9, "React에서 코드 스플리팅(Code Splitting)을 구현하는 방법은?",
                List.of("React.memo", "React.lazy + Suspense", "useReducer", "React.Fragment"), 1);
        q(t, 10, "Streaming SSR의 장점은?",
                List.of("번들 사이즈가 줄어든다", "전체 페이지를 기다리지 않고 준비된 부분부터 점진적으로 전송한다",
                        "SEO가 불필요해진다", "CDN 캐싱이 가능해진다"), 1);
    }

    // ──────────────────────────────────────────────
    //  Python
    // ──────────────────────────────────────────────

    private void initPythonTests() {
        // BEGINNER
        Test t = createTest("Python 기초 문법 테스트",
                "변수, 타입, 리스트, 딕셔너리, 조건문, 반복문, 함수 등 Python 기초를 평가합니다.",
                "python-backend", Difficulty.BEGINNER, 15, 60, 10);
        q(t, 1, "Python에서 리스트의 마지막 요소에 접근하는 방법은?",
                List.of("list[0]", "list[-1]", "list.last()", "list.end()"), 1);
        q(t, 2, "Python에서 딕셔너리를 생성하는 올바른 방법은?",
                List.of("d = [key: value]", "d = {key: value}", "d = (key, value)", "d = <key, value>"), 1);
        q(t, 3, "다음 중 Python의 불변(immutable) 자료형은?",
                List.of("list", "dict", "tuple", "set"), 2);
        q(t, 4, "Python에서 여러 줄 문자열을 표현하는 방법은?",
                List.of("\"\"\"텍스트\"\"\"", "'''텍스트'''", "\"\"\"텍스트\"\"\" 또는 '''텍스트'''", "<<텍스트>>"), 2);
        q(t, 5, "range(1, 10, 2)의 결과에 포함되지 않는 값은?",
                List.of("1", "3", "9", "10"), 3);
        q(t, 6, "Python 함수 정의에 사용하는 키워드는?",
                List.of("function", "func", "def", "define"), 2);
        q(t, 7, "리스트에 요소를 추가하는 메서드는?",
                List.of("add()", "append()", "push()", "insert_last()"), 1);
        q(t, 8, "Python에서 None을 확인하는 올바른 방법은?",
                List.of("x == None", "x is None", "x.isNone()", "None(x)"), 1);
        q(t, 9, "f-string 포매팅의 올바른 예시는?",
                List.of("f'이름은 {name}입니다'", "'이름은 ${name}입니다'",
                        "f'이름은 (name)입니다'", "'이름은 #{name}입니다'"), 0);
        q(t, 10, "Python에서 파일을 안전하게 읽는 방법은?",
                List.of("f = open('file.txt')", "with open('file.txt') as f:",
                        "file.read('file.txt')", "read('file.txt')"), 1);

        // INTERMEDIATE
        t = createTest("Python 중급 심화 테스트",
                "클래스/OOP, 데코레이터, 제너레이터, 컴프리헨션, 예외 처리 등을 평가합니다.",
                "python-backend", Difficulty.INTERMEDIATE, 20, 65, 10);
        q(t, 1, "Python에서 리스트 컴프리헨션의 올바른 문법은?",
                List.of("[x for x in range(10) if x > 5]", "[for x in range(10): x if x > 5]",
                        "[x if x > 5 for x in range(10)]", "[x in range(10) for if x > 5]"), 0);
        q(t, 2, "데코레이터(decorator)의 역할은?",
                List.of("클래스를 생성한다", "함수를 수정하지 않고 기능을 추가한다",
                        "변수를 상수로 만든다", "메모리를 해제한다"), 1);
        q(t, 3, "제너레이터(generator)에서 값을 반환하는 키워드는?",
                List.of("return", "yield", "emit", "send"), 1);
        q(t, 4, "Python의 다중 상속에서 MRO(Method Resolution Order)를 확인하는 방법은?",
                List.of("Class.order()", "Class.__mro__", "Class.inheritance()", "Class.__bases_order__"), 1);
        q(t, 5, "다음 중 @staticmethod와 @classmethod의 차이로 올바른 것은?",
                List.of("차이 없다", "@classmethod는 cls를 첫 번째 인자로 받고, @staticmethod는 받지 않는다",
                        "@staticmethod는 self를 받는다", "@classmethod는 인스턴스 메서드이다"), 1);
        q(t, 6, "Python에서 'with' 문이 내부적으로 호출하는 매직 메서드는?",
                List.of("__init__과 __del__", "__enter__과 __exit__",
                        "__open__과 __close__", "__start__과 __end__"), 1);
        q(t, 7, "*args와 **kwargs의 차이는?",
                List.of("차이 없다", "*args는 위치 인자를 튜플로, **kwargs는 키워드 인자를 딕셔너리로 받는다",
                        "*args는 딕셔너리, **kwargs는 리스트", "*args만 가변 인자이다"), 1);
        q(t, 8, "Python에서 private 속성을 나타내는 관례는?",
                List.of("@private 어노테이션", "속성 이름 앞에 언더스코어 (_) 또는 더블 언더스코어 (__)",
                        "private 키워드 사용", "# private 주석 추가"), 1);
        q(t, 9, "다음 코드의 출력은?\ndef f(a, b=[]):\n    b.append(a)\n    return b\nprint(f(1))\nprint(f(2))",
                List.of("[1] [2]", "[1] [1, 2]", "[1] [2, 1]", "에러 발생"), 1);
        q(t, 10, "try-except-else-finally에서 else 블록이 실행되는 조건은?",
                List.of("예외가 발생했을 때", "예외가 발생하지 않았을 때",
                        "항상 실행된다", "finally 후에 실행된다"), 1);

        // ADVANCED
        t = createTest("Python 고급 아키텍처 테스트",
                "GIL, 메타클래스, 디스크립터, asyncio, 메모리 관리 등 Python 고급 주제를 평가합니다.",
                "python-backend", Difficulty.ADVANCED, 25, 70, 10);
        q(t, 1, "Python GIL(Global Interpreter Lock)의 영향은?",
                List.of("멀티프로세싱이 불가능하다", "CPU-bound 작업에서 멀티스레딩의 성능 이점이 제한된다",
                        "I/O-bound 작업에서도 병렬 처리가 불가능하다", "메모리 사용량이 증가한다"), 1);
        q(t, 2, "메타클래스(Metaclass)란?",
                List.of("추상 클래스의 다른 이름", "클래스를 생성하는 클래스",
                        "인스턴스를 생성하는 함수", "데코레이터의 일종"), 1);
        q(t, 3, "Python 디스크립터 프로토콜에 포함되는 메서드가 아닌 것은?",
                List.of("__get__", "__set__", "__delete__", "__describe__"), 3);
        q(t, 4, "asyncio에서 await 키워드의 역할은?",
                List.of("스레드를 생성한다", "코루틴의 실행을 일시 중단하고 완료를 기다린다",
                        "함수를 동기 함수로 변환한다", "예외를 발생시킨다"), 1);
        q(t, 5, "Python의 가비지 컬렉션에서 순환 참조를 처리하는 방식은?",
                List.of("참조 카운팅만으로 처리", "세대별(generational) 가비지 컬렉터를 사용",
                        "개발자가 수동으로 해제", "순환 참조를 허용하지 않는다"), 1);
        q(t, 6, "__slots__의 용도는?",
                List.of("메서드를 제한한다", "인스턴스 속성을 제한하고 __dict__를 생성하지 않아 메모리를 절약한다",
                        "상속을 방지한다", "직렬화를 지원한다"), 1);
        q(t, 7, "CPython에서 작은 정수(-5~256)가 캐싱되는 이유는?",
                List.of("문법 규칙이다", "자주 사용되는 정수의 객체 생성 비용을 줄이기 위해",
                        "가비지 컬렉션을 위해", "멀티스레드 안전성을 위해"), 1);
        q(t, 8, "typing 모듈의 Protocol이 기존 ABC와 다른 점은?",
                List.of("차이 없다", "구조적 서브타이핑(structural subtyping)을 지원한다 (상속 없이 타입 호환)",
                        "더 빠르다", "런타임에 타입 체크를 한다"), 1);
        q(t, 9, "multiprocessing과 threading의 사용 시나리오로 올바른 것은?",
                List.of("CPU-bound → threading, I/O-bound → multiprocessing",
                        "CPU-bound → multiprocessing, I/O-bound → threading",
                        "둘 다 CPU-bound에 적합", "둘 다 I/O-bound에 적합"), 1);
        q(t, 10, "Python에서 weakref의 용도는?",
                List.of("참조 카운트를 증가시킨다", "참조 카운트를 증가시키지 않는 약한 참조를 만들어 순환 참조를 방지한다",
                        "메모리를 즉시 해제한다", "가비지 컬렉션을 비활성화한다"), 1);
    }

    // ──────────────────────────────────────────────
    //  Algorithm
    // ──────────────────────────────────────────────

    private void initAlgorithmTests() {
        // BEGINNER
        Test t = createTest("알고리즘 기초 테스트",
                "시간/공간 복잡도, 기본 자료구조, 정렬, 탐색 등 알고리즘 기초를 평가합니다.",
                "firststep", Difficulty.BEGINNER, 15, 60, 10);
        q(t, 1, "시간 복잡도 O(n)의 의미는?",
                List.of("항상 일정한 시간이 걸린다", "입력 크기에 비례하여 시간이 증가한다",
                        "입력 크기의 제곱에 비례한다", "로그 시간이 걸린다"), 1);
        q(t, 2, "스택(Stack)의 특성은?",
                List.of("FIFO (First In First Out)", "LIFO (Last In First Out)",
                        "임의 접근 가능", "정렬된 상태 유지"), 1);
        q(t, 3, "큐(Queue)의 특성은?",
                List.of("LIFO", "FIFO", "LILO", "임의 접근"), 1);
        q(t, 4, "버블 정렬의 평균 시간 복잡도는?",
                List.of("O(n)", "O(n log n)", "O(n^2)", "O(log n)"), 2);
        q(t, 5, "배열에서 특정 값을 선형 탐색할 때 최악의 시간 복잡도는?",
                List.of("O(1)", "O(log n)", "O(n)", "O(n^2)"), 2);
        q(t, 6, "재귀 함수에 반드시 필요한 것은?",
                List.of("반복문", "기저 조건(Base Case)", "전역 변수", "배열"), 1);
        q(t, 7, "연결 리스트(Linked List)의 장점은?",
                List.of("인덱스 접근이 O(1)이다", "삽입/삭제가 O(1)이다 (위치를 알 때)",
                        "메모리를 적게 사용한다", "정렬이 빠르다"), 1);
        q(t, 8, "삽입 정렬이 효율적인 경우는?",
                List.of("완전히 역순으로 정렬된 경우", "데이터가 거의 정렬되어 있는 경우",
                        "데이터가 매우 큰 경우", "모든 경우에 동일"), 1);
        q(t, 9, "공간 복잡도가 O(1)이라는 의미는?",
                List.of("메모리를 사용하지 않는다", "입력 크기와 관계없이 일정한 추가 메모리만 사용한다",
                        "입력 크기에 비례하여 메모리가 증가한다", "메모리가 무한하다"), 1);
        q(t, 10, "선택 정렬의 동작 방식은?",
                List.of("인접한 두 요소를 비교하여 교환", "최솟값을 찾아 맨 앞과 교환",
                        "이미 정렬된 부분에 삽입", "배열을 반으로 나눠 정렬"), 1);

        // INTERMEDIATE
        t = createTest("알고리즘 중급 테스트",
                "이진 탐색, 해시, 트리, 그래프 탐색, DP 기초 등 중급 알고리즘을 평가합니다.",
                "firststep", Difficulty.INTERMEDIATE, 20, 65, 10);
        q(t, 1, "이진 탐색(Binary Search)의 전제 조건은?",
                List.of("데이터가 연결 리스트에 저장", "데이터가 정렬되어 있어야 한다",
                        "데이터가 해시 테이블에 저장", "데이터 크기가 2의 거듭제곱"), 1);
        q(t, 2, "해시 테이블에서 충돌(Collision) 해결 방법이 아닌 것은?",
                List.of("체이닝(Chaining)", "개방 주소법(Open Addressing)",
                        "이중 해싱(Double Hashing)", "버블 해싱(Bubble Hashing)"), 3);
        q(t, 3, "이진 탐색 트리(BST)에서 검색의 평균 시간 복잡도는?",
                List.of("O(1)", "O(log n)", "O(n)", "O(n log n)"), 1);
        q(t, 4, "BFS(너비 우선 탐색)에서 사용하는 자료구조는?",
                List.of("스택", "큐", "힙", "트리"), 1);
        q(t, 5, "DFS(깊이 우선 탐색)에서 사용하는 자료구조는?",
                List.of("큐", "스택 (또는 재귀)", "힙", "해시 테이블"), 1);
        q(t, 6, "다이나믹 프로그래밍(DP)의 핵심 조건 2가지는?",
                List.of("정렬과 탐색", "최적 부분 구조와 중복 부분 문제",
                        "분할과 병합", "그리디와 백트래킹"), 1);
        q(t, 7, "피보나치 수열을 DP로 풀었을 때 시간 복잡도는?",
                List.of("O(2^n)", "O(n)", "O(n^2)", "O(n log n)"), 1);
        q(t, 8, "힙(Heap)의 특성으로 올바른 것은?",
                List.of("완전 정렬된 트리", "완전 이진 트리이며 부모가 자식보다 크거나(최대힙) 작다(최소힙)",
                        "이진 탐색 트리의 일종", "선형 자료구조"), 1);
        q(t, 9, "그래프에서 사이클을 감지하는 방법은?",
                List.of("BFS만 가능", "DFS에서 방문 중인 노드를 다시 만나면 사이클",
                        "힙 정렬 사용", "사이클 감지는 불가능"), 1);
        q(t, 10, "분할 정복(Divide and Conquer)을 사용하는 정렬은?",
                List.of("버블 정렬", "삽입 정렬", "병합 정렬(Merge Sort)", "선택 정렬"), 2);

        // ADVANCED
        t = createTest("알고리즘 고급 테스트",
                "고급 DP, 최단 경로, 세그먼트 트리, 그래프 고급 알고리즘 등을 평가합니다.",
                "firststep", Difficulty.ADVANCED, 25, 70, 10);
        q(t, 1, "다익스트라 알고리즘의 시간 복잡도는? (우선순위 큐 사용 시)",
                List.of("O(V^2)", "O(V + E)", "O((V + E) log V)", "O(V * E)"), 2);
        q(t, 2, "벨만-포드 알고리즘이 다익스트라보다 유리한 경우는?",
                List.of("가중치가 모두 같을 때", "음의 가중치가 있는 그래프",
                        "그래프가 완전 그래프일 때", "정점이 매우 적을 때"), 1);
        q(t, 3, "위상 정렬(Topological Sort)이 가능한 그래프 조건은?",
                List.of("무방향 그래프", "DAG (방향 비순환 그래프)",
                        "완전 그래프", "이분 그래프"), 1);
        q(t, 4, "세그먼트 트리의 구간 합 쿼리 시간 복잡도는?",
                List.of("O(1)", "O(log n)", "O(n)", "O(n log n)"), 1);
        q(t, 5, "Knapsack 문제에서 0/1 Knapsack과 Fractional Knapsack의 차이는?",
                List.of("차이 없다", "0/1은 DP, Fractional은 그리디로 풀 수 있다",
                        "0/1은 그리디, Fractional은 DP", "둘 다 그리디로 풀 수 있다"), 1);
        q(t, 6, "최소 신장 트리(MST)를 구하는 알고리즘이 아닌 것은?",
                List.of("크루스칼(Kruskal)", "프림(Prim)", "플로이드-워셜(Floyd-Warshall)", "보루프카(Borůvka)"), 2);
        q(t, 7, "LCS(Longest Common Subsequence)의 시간 복잡도는?",
                List.of("O(n)", "O(n log n)", "O(n * m)", "O(2^n)"), 2);
        q(t, 8, "Union-Find에서 경로 압축(Path Compression)의 효과는?",
                List.of("공간 복잡도를 줄인다", "Find 연산의 시간 복잡도를 거의 O(1)에 근접하게 한다",
                        "Union 연산을 빠르게 한다", "사이클을 자동으로 감지한다"), 1);
        q(t, 9, "A* 알고리즘에서 휴리스틱 함수의 역할은?",
                List.of("정확한 최단 거리를 계산", "목표까지의 예상 비용을 추정하여 탐색 방향을 안내한다",
                        "그래프를 정렬한다", "음의 가중치를 처리한다"), 1);
        q(t, 10, "NP-완전(NP-Complete) 문제의 특성은?",
                List.of("다항 시간 알고리즘이 알려져 있다", "해를 검증하는 것은 다항 시간이지만, 찾는 것은 다항 시간 알고리즘이 알려지지 않았다",
                        "해가 존재하지 않는다", "근사 알고리즘이 불가능하다"), 1);
    }

    // ──────────────────────────────────────────────
    //  멘토 초기 데이터
    // ──────────────────────────────────────────────

    private void initMentors() {
        if (mentorProfileRepository.count() > 0) {
            log.info("멘토 데이터가 이미 존재합니다. 멘토 초기화를 건너뜁니다.");
            return;
        }

        String encoded = passwordEncoder.encode("mentor1234!");

        createMentor("김자바", "java.mentor@devmatch.com", encoded,
                List.of("java-backend", "firststep"), 8, "네이버", "Java/Spring 전문 멘토입니다. 대규모 서비스 설계 경험이 풍부합니다.");
        createMentor("이스프링", "spring.mentor@devmatch.com", encoded,
                List.of("java-backend", "devops"), 5, "카카오", "Spring 기반 MSA 설계와 DevOps 파이프라인 구축 경험이 있습니다.");
        createMentor("박리액트", "react.mentor@devmatch.com", encoded,
                List.of("frontend", "node-backend"), 6, "라인", "React와 Node.js 풀스택 개발 멘토입니다.");
        createMentor("최파이썬", "python.mentor@devmatch.com", encoded,
                List.of("python-backend"), 7, "쿠팡", "Python 백엔드와 알고리즘 전문 멘토입니다.");
        createMentor("정풀스택", "fullstack.mentor@devmatch.com", encoded,
                List.of("java-backend", "frontend", "kafka"), 10, "토스", "10년차 풀스택 개발자입니다. 서비스 전체 아키텍처 설계를 도와드립니다.");

        log.info("멘토 초기 데이터 {}명 삽입 완료", mentorProfileRepository.count());
    }

    // ──────────────────────────────────────────────
    //  헬퍼 메서드
    // ──────────────────────────────────────────────

    private Test createTest(String title, String description, String category,
                            Difficulty difficulty, int timeLimit, int passingScore, int questionCount) {
        return testRepository.save(Test.builder()
                .title(title)
                .description(description)
                .category(category)
                .difficulty(difficulty)
                .timeLimit(timeLimit)
                .passingScore(passingScore)
                .questionCount(questionCount)
                .build());
    }

    private void q(Test test, int order, String content, List<String> options, int correctAnswer) {
        questionRepository.save(Question.builder()
                .test(test)
                .orderIndex(order)
                .content(content)
                .options(options)
                .correctAnswer(correctAnswer)
                .score(10)
                .build());
    }

    // ──────────────────────────────────────────────
    //  멘토링 코스 (17개)
    // ──────────────────────────────────────────────
    private void initMentoringCourses() {
        record Seed(String key, String title, String subtitle, String icon,
                    String descTitle, String descText, String boxesJson, int order) {}

        List<Seed> seeds = List.of(
            new Seed("java-backend", "AI+ Java 백엔드",
                "깊이 있는 학습과 고퀄리티 프로젝트 수행을 통해 채용 경쟁력을 높이는 1:1 심화형 멘토링 코스",
                "☕",
                "단순히 \"써봤다\"를 넘어\n제대로 알고 대답할 수 있도록 교육합니다.",
                "MSA, Kafka까지 써봤다 하더라도 이 기술들은 국비/부트캠프에서도 다루는 흔한 스펙이고 이제 누구나 쉽게 쓸 수 있는 것들이기에,\n\"왜 썼는지\"를 깊게 설명하지 못하고 \"써봤다\"만으로는 채용 시장에서 경쟁력을 가지기 어렵습니다.",
                "[{\"icon\":\"Layout\",\"title\":\"기본기\",\"color\":\"cyan\",\"tags\":[\"컴퓨터 사이언스\",\"Java\",\"Effective Java\"],\"desc\":\"무작정 프레임워크를 쓰는 것을 넘어 CS 지식과 Java 언어 자체의 기본기를 확립합니다.\"},{\"icon\":\"Code2\",\"title\":\"응용\",\"color\":\"blue\",\"tags\":[\"Kotlin\",\"Spring Boot\",\"JPA/QueryDSL\",\"Spring Security\"],\"desc\":\"Spring의 내부 구조를 파고들고, JPA 연관관계 매핑과 최적화, Kotlin 기반 설계 등을 배웁니다.\"},{\"icon\":\"Database\",\"title\":\"심화 / 프로젝트\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"대규모 트래픽 아키텍처\",\"Redis 분산락\",\"Kafka\",\"Docker & K8s\",\"MSA\"],\"desc\":\"동시성 제어(Redis), MQ(Kafka)를 이용한 비동기 통신 설계 등 실제 트래픽 이슈들을 실무 관점에서 다루어 포트폴리오를 고도화합니다.\"}]",
                1),
            new Seed("node-backend", "Node.js Backend + AI",
                "실시간 통신과 고성능 비동기 서버 아키텍처를 마스터하는 심화형 멘토링",
                "JS",
                "단순한 CRUD를 넘어\n고성능 비동기 아키텍처를 다룹니다.",
                "JavaScript/TypeScript 백엔드 환경에서 Event Loop의 이해부터 Redis, Socket.io를 활용한 대규모 트래픽 처리를 경험해보세요.",
                "[{\"icon\":\"Layout\",\"title\":\"코어\",\"color\":\"cyan\",\"tags\":[\"TypeScript\",\"Node.js Core\",\"Event Loop\"],\"desc\":\"JS/TS의 타입 시스템과 Node.js 런타임의 핵심 아키텍처를 이해합니다.\"},{\"icon\":\"Code2\",\"title\":\"프레임워크\",\"color\":\"blue\",\"tags\":[\"NestJS\",\"Express\",\"TypeORM\",\"Prisma\"],\"desc\":\"가장 많이 쓰이는 NestJS 생태계와 ORM을 활용해 클린 아키텍처 기반 서버를 구축합니다.\"},{\"icon\":\"Database\",\"title\":\"심화 / 실시간 통신\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Socket.io\",\"Redis Pub/Sub\",\"WebRTC\",\"AWS / K8s\"],\"desc\":\"채팅, 알림, 실시간 서비스 등 Node.js가 가장 잘하는 분야의 아키텍처를 설계하고 배포합니다.\"}]",
                2),
            new Seed("python-backend", "Python Backend + AI",
                "백엔드 생태계와 AI 서빙을 결합한 최적의 실무 밀착 멘토링",
                "🐍",
                "데이터와 백엔드의 브릿지,\nPython 서버 서빙 최적화.",
                "Django, FastAPI의 깊은 이해와 더불어 AI 모델(PyTorch/LLM)을 어떻게 빠르고 안정적으로 서빙할 수 있는지 학습합니다.",
                "[{\"icon\":\"Layout\",\"title\":\"기본기\",\"color\":\"cyan\",\"tags\":[\"Python\",\"Django\",\"FastAPI\"],\"desc\":\"동기/비동기 프레임워크의 장단점을 파악하고 최신 FastAPI 생태계를 익힙니다.\"},{\"icon\":\"Cpu\",\"title\":\"AI 서빙\",\"color\":\"blue\",\"tags\":[\"LLM 연동\",\"ONNX\",\"Triton Server\"],\"desc\":\"인공지능 모델을 마이크로서비스 형태로 서빙하기 위한 아키텍처를 구축해봅니다.\"},{\"icon\":\"Database\",\"title\":\"인프라/스케일링\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Celery\",\"Redis\",\"Docker Compose\",\"Gunicorn\"],\"desc\":\"병렬 처리와 비동기 큐 워커를 활용하여 Python 서버의 한계를 깨는 확장성 설계를 다룹니다.\"}]",
                3),
            new Seed("frontend", "Frontend + AI",
                "프론트엔드 성능 최적화와 트러블슈팅, 최신 기술 스택을 다루는 심화 코스",
                "⚛️",
                "보이는 것 그 이상,\n사용자 경험(UX)과 성능의 극대화를 이룹니다.",
                "Next.js의 SSR/SSG/ISR 혼합 렌더링, Web Vitals 최적화, 상태관리 패턴 등 프론트엔드 엔진의 동작 원리를 뜯어봅니다.",
                "[{\"icon\":\"Layout\",\"title\":\"코어 UI\",\"color\":\"cyan\",\"tags\":[\"React\",\"TypeScript\",\"브라우저 렌더링\"],\"desc\":\"Virtual DOM의 이해와 브라우저 렌더링 파이프라인 최적화를 실습합니다.\"},{\"icon\":\"Code2\",\"title\":\"메타 프레임워크\",\"color\":\"blue\",\"tags\":[\"Next.js\",\"App Router\",\"State Management\"],\"desc\":\"모던 웹 개발의 핵심인 Next.js App Router 생태계와 서버 단 데이터 페칭을 고도화합니다.\"},{\"icon\":\"Layers\",\"title\":\"UX & 성능 고도화\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"CI/CD\",\"Web Vitals\",\"Framer Motion\",\"Micro-Frontend\"],\"desc\":\"웹 성능 최적화, 마이크로 프론트엔드 아키텍처 및 화려하고 자연스러운 인터랙션을 구현하여 압도적인 포트폴리오를 만듭니다.\"}]",
                4),
            new Seed("android", "Android + AI",
                "모던 안드로이드 앱 아키텍처와 Compose, 성능 최적화 마스터 과정",
                "🤖",
                "안드로이드 네이티브의 끝판왕,\n안정적이고 유려한 앱을 만듭니다.",
                "Jetpack Compose와 MVVM/MVI 아키텍처, Memory Leak 방지 기술 등 현업 안드로이드 팀이 선호하는 필수 역량을 다집니다.",
                "[{\"icon\":\"Smartphone\",\"title\":\"기본 & 패러다임\",\"color\":\"cyan\",\"tags\":[\"Kotlin\",\"Coroutines\",\"Flow\"],\"desc\":\"Kotlin의 강력한 비동기 처리와 선언형 프로그래밍 방식을 완벽하게 이해합니다.\"},{\"icon\":\"Layout\",\"title\":\"UI & 아키텍처\",\"color\":\"blue\",\"tags\":[\"Jetpack Compose\",\"MVVM/MVI\",\"Hilt\"],\"desc\":\"기존 XML 뷰에서 탈피하여 100% Compose 기반으로 앱 UI 레이어를 설계하고 의존성 주입을 다룹니다.\"},{\"icon\":\"Server\",\"title\":\"심화 / 오프라인 퍼스트\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Room\",\"WorkManager\",\"Modularization\",\"ExoPlayer\"],\"desc\":\"대규모 앱 스케일링을 위한 멀티 모듈 아키텍처 설계와 로컬 캐싱 전략을 통한 오프라인 최적화를 실습합니다.\"}]",
                5),
            new Seed("devops", "DevOps 엔지니어 육성",
                "CI/CD 빌드 파이프라인부터 클라우드 네이티브 아키텍처까지",
                "⚙️",
                "인프라를 코드로 구성하고,\n자동화로 생산성을 극대화합니다.",
                "AWS 환경에서의 IaC(Terraform), 클러스터 오케스트레이션(K8s) 등 실무에서 환영받는 DevOps 툴체인을 경험합니다.",
                "[{\"icon\":\"Cloud\",\"title\":\"클라우드 & IaC\",\"color\":\"cyan\",\"tags\":[\"AWS\",\"Terraform\",\"Linux\"],\"desc\":\"AWS의 심화 네트워킹과 컴퓨팅 리소스를 코드로 정의하고 프로비저닝 합니다.\"},{\"icon\":\"Server\",\"title\":\"CI/CD\",\"color\":\"blue\",\"tags\":[\"GitHub Actions\",\"Jenkins\",\"ArgoCD\"],\"desc\":\"개발부터 배포까지 무중단 스무스 파이프라인을 구축하여 빌드 시간을 획기적으로 줄여봅니다.\"},{\"icon\":\"Layers\",\"title\":\"컨테이너 오케스트레이션\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Docker\",\"Kubernetes\",\"Helm\",\"Prometheus\"],\"desc\":\"K8s 클러스터 운영 및 로깅/모니터링 체계를 바탕으로 장애 복원력(Resiliency)을 갖춘 인프라를 설계합니다.\"}]",
                6),
            new Seed("ios", "iOS + AI",
                "모던 iOS 앱 아키텍처와 SwiftUI 마스터 과정",
                "🍎",
                "부드러운 경험을 만드는\n최상급 iOS 애플리케이션",
                "SwiftUI, Combine 기반의 선언형 패러다임과 TCA(The Composable Architecture) 등 최신 iOS 생태계를 학습합니다.",
                "[{\"icon\":\"Smartphone\",\"title\":\"기본 & 패러다임\",\"color\":\"cyan\",\"tags\":[\"Swift\",\"SwiftUI\",\"Combine\"],\"desc\":\"Swift 언어의 핵심과 SwiftUI의 선언적 UI 구성 방식을 완벽하게 이해합니다.\"},{\"icon\":\"Layout\",\"title\":\"모던 아키텍처\",\"color\":\"blue\",\"tags\":[\"TCA\",\"MVVM\",\"Clean Architecture\"],\"desc\":\"대규모 애플리케이션에서 상태를 예측 가능하게 관리하기 위한 현대적 아키텍처를 도입해봅니다.\"},{\"icon\":\"Server\",\"title\":\"퍼포먼스/최적화\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"CoreData\",\"Memory Management\",\"Instruments\"],\"desc\":\"Memory Leak 방지와 앱 크래시 분석, 렌더링 최적화를 통한 프리미엄 사용자 경험을 제공합니다.\"}]",
                7),
            new Seed("flutter", "Flutter + AI",
                "크로스 플랫폼의 한계를 뛰어넘는 최적화 및 네이티브 연동",
                "🦋",
                "하나의 코드로 두 배의 가치를,\n크로스 플랫폼의 완성.",
                "단순 UI 클론을 넘어 렌더링 최적화, 상태관리 패턴, 그리고 네이티브(채널) 연동까지 깊게 파고드는 전문가 과정입니다.",
                "[{\"icon\":\"Smartphone\",\"title\":\"Dart & 코어\",\"color\":\"cyan\",\"tags\":[\"Dart\",\"Widget Lifecycle\",\"Element Tree\"],\"desc\":\"Flutter 엔진이 화면을 그리는 3가지 트리(Widget, Element, RenderObject)의 동작 원리를 이해합니다.\"},{\"icon\":\"Layout\",\"title\":\"상태 관리\",\"color\":\"blue\",\"tags\":[\"Provider\",\"Riverpod\",\"Bloc\"],\"desc\":\"현업에서 가장 많이 쓰이는 상태관리 라이브러리들을 비교하고 상황에 맞게 최적화합니다.\"},{\"icon\":\"Layers\",\"title\":\"네이티브 & 심화\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Method Channel\",\"Isolates\",\"CI/CD\"],\"desc\":\"스레드(Isolate) 분리를 통한 성능 최적화와 결제, 푸시 등 네이티브 연동을 마스터합니다.\"}]",
                8),
            new Seed("react-native", "React Native + AI",
                "웹 개발 경험으로 시작하는 최고 수준의 앱 배포",
                "📱",
                "웹과 모바일의 브릿지,\n빠른 속도로 시장을 선점합니다.",
                "React 생태계를 그대로 활용하며, 브릿지의 한계를 넘기 위한 최신 JSI 아키텍처와 애니메이션 최적화를 배웁니다.",
                "[{\"icon\":\"Smartphone\",\"title\":\"코어 개념\",\"color\":\"cyan\",\"tags\":[\"React\",\"Metro\",\"Native Bridge\"],\"desc\":\"React Native의 브릿지 통신 원리와 동작 메커니즘을 뜯어봅니다.\"},{\"icon\":\"Layout\",\"title\":\"UI & 애니메이션\",\"color\":\"blue\",\"tags\":[\"Reanimated\",\"Gesture Handler\",\"Skia\"],\"desc\":\"선언형 애니메이션을 작성하여 60fps를 방어하는 네이티브 수준의 UI를 렌더링합니다.\"},{\"icon\":\"Layers\",\"title\":\"인프라 & 배포\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Expo EAS\",\"CodePush\",\"App Store Connect\"],\"desc\":\"OTA 업데이트를 구상하고 원클릭 배포 파이프라인을 구축하여 유지보수 비용을 최소화합니다.\"}]",
                9),
            new Seed("data-engineer", "Data Engineer + AI",
                "대용량 데이터 파이프라인 구축 및 실시간 스트리밍 처리 기술",
                "📊",
                "데이터의 강이 흐르는\n견고한 파이프라인 설계.",
                "빅데이터 에코시스템(Hadoop, Spark)부터 최신 모던 데이터 스택(Airflow, dbt)까지 대용량 데이터를 안전하고 빠르게 처리하는 아키텍처를 학습합니다.",
                "[{\"icon\":\"Database\",\"title\":\"데이터 수집 & 저장\",\"color\":\"cyan\",\"tags\":[\"Hadoop\",\"S3\",\"Data Lake\"],\"desc\":\"다양한 소스에서 발생한 데이터를 분산 저장소에 안정적으로 적재하는 기초를 다룹니다.\"},{\"icon\":\"Cloud\",\"title\":\"스트리밍 & 배치\",\"color\":\"blue\",\"tags\":[\"Spark\",\"Kafka\",\"Flink\"],\"desc\":\"실시간 데이터 처리와 대규모 배치 트랜잭션을 구현하여 데이터의 정합성을 보장합니다.\"},{\"icon\":\"Layers\",\"title\":\"파이프라인 자동화\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Airflow\",\"dbt\",\"Snowflake\"],\"desc\":\"복잡한 데이터 파이프라인을 시각적으로 오케스트레이션하고 데이터 웨어하우스(DW)에 최적화합니다.\"}]",
                10),
            new Seed("ml-engineer", "ML Engineer",
                "머신러닝 모델의 학습, 평가 및 프로덕션 환경 배포의 모든 것",
                "🧠",
                "연구를 넘어 실전으로,\n살아 숨쉬는 AI 시스템 구축.",
                "모델 아키텍처 설계와 하이퍼파라미터 튜닝을 넘어, MLOps 기반으로 모델을 지속 가능하게 서비스하는 기술을 배웁니다.",
                "[{\"icon\":\"Cpu\",\"title\":\"모델링 코어\",\"color\":\"cyan\",\"tags\":[\"PyTorch\",\"TensorFlow\",\"Scikit-Learn\"],\"desc\":\"심층 신경망(DNN)의 기초 구조부터 시계열, 비전, 자연어 등 분야별 실무형 모델 아키텍처를 실습합니다.\"},{\"icon\":\"Layout\",\"title\":\"서빙 아키텍처\",\"color\":\"blue\",\"tags\":[\"FastAPI\",\"ONNX\",\"Triton Server\"],\"desc\":\"무거운 AI 모델을 압축, 최적화하여 짧은 레이턴시를 보장하는 API 서버 형태를 구축해봅니다.\"},{\"icon\":\"Server\",\"title\":\"MLOps 파이프라인\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Kubeflow\",\"MLflow\",\"Docker/K8s\"],\"desc\":\"모델 개발, 배포, 모니터링 라이프사이클 전체를 자동화해 안정성 높은 AI 서비스를 유지합니다.\"}]",
                11),
            new Seed("game-server", "Game Server",
                "초당 수천 번의 인터랙션을 처리하는 실시간 멀티플레이 서버 아키텍처",
                "🎮",
                "0.01초의 딜레이도 허용하지 않는\n극한의 게임 서버 튜닝.",
                "C++/C#을 기반으로 한 소켓 프로그래밍부터 동시성 제어, 매치메이킹 시스템 등 실제 게임 서버의 코어 로직을 작성합니다.",
                "[{\"icon\":\"Code2\",\"title\":\"네트워크 & 코어\",\"color\":\"cyan\",\"tags\":[\"C++/C#\",\"TCP/UDP\",\"IOCP\"],\"desc\":\"운영체제의 네트워크 I/O 구조를 뜯어보고 효율적인 소켓 입출력 통신망을 구현합니다.\"},{\"icon\":\"Server\",\"title\":\"멀티스레딩 & 동기화\",\"color\":\"blue\",\"tags\":[\"Lock-free\",\"Deadlock 방지\",\"Memory Pool\"],\"desc\":\"멀티스레드 환경의 크리티컬 섹션 제어부터 메모리 풀링 최적화 기술을 파고듭니다.\"},{\"icon\":\"Layers\",\"title\":\"분산 & 매치메이킹\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Redis\",\"gRPC\",\"AWS GameLift\"],\"desc\":\"글로벌 서비스 상황의 데이터 동기화와 원활한 유저 매치메이킹 레이어를 구축합니다.\"}]",
                12),
            new Seed("short-term", "단기 취업/이직",
                "빠른 속도로 실무 역량을 증명하고 목표하는 기업에 합격하는 1개월 밀착 코스",
                "⚡",
                "군더더기 없이 핵심만,\n합격을 위한 가장 빠른 지름길.",
                "알고리즘 코딩테스트부터 과제형 전형, 모의 면접, 이력서 첨삭 등 채용의 A to Z를 함께하는 극강의 단기 매니지먼트 멘토링입니다.",
                "[{\"icon\":\"Code2\",\"title\":\"코딩테스트/과제\",\"color\":\"cyan\",\"tags\":[\"알고리즘\",\"자료구조\",\"리팩토링\"],\"desc\":\"자주 출제되는 유형을 족집게처럼 짚어주며 깔끔하게 과제를 완성하는 전략을 배웁니다.\"},{\"icon\":\"Layout\",\"title\":\"서류/포트폴리오\",\"color\":\"blue\",\"tags\":[\"이력서\",\"Readme\",\"트러블슈팅\"],\"desc\":\"내가 한 경험을 가장 돋보이게 작성하는 이력서 레이아웃과 서술 방식을 1:1로 피드백합니다.\"},{\"icon\":\"Users\",\"title\":\"모의 면접\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"CS 질문\",\"인성 면접\",\"기술 심층 면접\"],\"desc\":\"실제 면접관 출신 멘토와 함께 예상 질문 리스트를 도출하고 꼬리물기 압박 면접을 대비합니다.\"}]",
                13),
            new Seed("firststep", "First Step: Java Backend",
                "비전공자/입문자도 따라할 수 있는 탄탄한 웹 백엔드 첫걸음",
                "🌱",
                "처음이라고 두려워 마세요,\n기본부터 든든하게 다집니다.",
                "Java 언어의 기초부터 시작해 웹의 동작 원리와 Spring Boot를 활용한 첫 서버 배포까지 끝맺음하는 과정입니다.",
                "[{\"icon\":\"Code2\",\"title\":\"언어의 기초\",\"color\":\"cyan\",\"tags\":[\"Java 17\",\"객체지향\",\"컬렉션\"],\"desc\":\"변수, 반복문부터 시작해 객체지향 4대 특징과 SOLID 프로그래밍 관점을 쉽게 이해합니다.\"},{\"icon\":\"Layout\",\"title\":\"웹 프레임워크\",\"color\":\"blue\",\"tags\":[\"Spring Boot\",\"REST API\",\"MySQL\"],\"desc\":\"간단한 게시판 형식의 API를 만들고, 데이터베이스에 정보를 지속적으로 저장하는 법을 실습합니다.\"},{\"icon\":\"Cloud\",\"title\":\"내 생의 첫 배포\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"AWS EC2\",\"Linux\",\"GitHub\"],\"desc\":\"클라우드 환경에 내 서버를 올려보고 전 세계 누구나 접속할 수 있도록 포트폴리오 첫 줄을 장식합니다.\"}]",
                14),
            new Seed("distributed-lock", "분산 락 (Distributed Lock) Deep Dive",
                "수만 명의 선착순 트래픽을 놓치지 않고 완벽히 제어하는 특강",
                "🔒",
                "단 한 건의 동시성 오류도 용납하지 않는\n초정밀 트래픽 제어.",
                "티켓팅, 수강신청, 타임세일 이벤트와 같은 극단적인 동시성 상황에서 정합성을 지키기 위한 시스템을 집중 설계합니다.",
                "[{\"icon\":\"Database\",\"title\":\"RDB Lock 전략\",\"color\":\"cyan\",\"tags\":[\"Pessimistic Lock\",\"Optimistic Lock\",\"JPA\"],\"desc\":\"데이터베이스의 배타 락, 공유 락 개념을 파고들며 가장 기초적인 동시성 제어를 구현합니다.\"},{\"icon\":\"Server\",\"title\":\"분산 환경의 락\",\"color\":\"blue\",\"tags\":[\"Redis\",\"Lettuce\",\"Redisson\"],\"desc\":\"여러 대의 서버 인스턴스에서도 정합성이 깨지지 않도록 Redis 기반 분산 락 메커니즘을 적용합니다.\"},{\"icon\":\"Cpu\",\"title\":\"도메인 적용\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"쿠폰 발급\",\"결제 트랜잭션\",\"재고 차감\"],\"desc\":\"Spring의 AOP나 Facade 패턴을 활용해 비즈니스 로직과 락 기능을 우아하게 분리하고 테스트 코드로 검증합니다.\"}]",
                15),
            new Seed("kafka", "Kafka Deep Dive",
                "대규모 메시지 큐와 이벤트 드리븐 설계 패턴 완전 정복",
                "📨",
                "시스템 간의 완벽한 징검다리,\n이벤트 기반 아키텍처.",
                "결합도를 낮추고 처리량을 높여주는 Kafka의 내부 동작 원리를 파악하고, 실제 MSA 환경에서 어떻게 활용하는지 심층 실습합니다.",
                "[{\"icon\":\"Server\",\"title\":\"Kafka 코어\",\"color\":\"cyan\",\"tags\":[\"Broker\",\"Topic & Partition\",\"Offset\"],\"desc\":\"Kafka의 튼튼한 분산 저장 원리와 Replication, Zookeeper(또는 KRaft) 체제에 대해 학습합니다.\"},{\"icon\":\"Code2\",\"title\":\"프로듀싱 & 컨슈밍\",\"color\":\"blue\",\"tags\":[\"Spring Kafka\",\"Ack 튜닝\",\"Idempotence\"],\"desc\":\"메시지를 유실하지 않기 위한 설정과 중복 처리를 막기 위한 At-Least/Exactly-Once 전략을 실습합니다.\"},{\"icon\":\"Layers\",\"title\":\"EDA 기반 서비스 분리\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Event-Driven\",\"Outbox Pattern\",\"Saga\"],\"desc\":\"데이터베이스 트랜잭션과 메시지 발행을 일치시키는 Transactional Outbox 패턴 등 고급 MSA 주제를 다룹니다.\"}]",
                16),
            new Seed("expert-msa", "Kotlin/MSA 최고급 과정",
                "현업 시니어 및 테크리드를 위한 엔터프라이즈 아키텍처 설계",
                "🏛️",
                "수백 개의 마이크로 서비스,\n복잡성 속에서 질서를 찾습니다.",
                "모놀리식 분리를 고민하거나 트래픽 임계점에 도달한 시스템을 책임지는 시니어 개발자들을 위한 1:1 맞춤형 컨설팅 및 교육입니다.",
                "[{\"icon\":\"Code2\",\"title\":\"도메인 주도 설계\",\"color\":\"cyan\",\"tags\":[\"DDD\",\"Clean Architecture\",\"Hexagonal\"],\"desc\":\"비즈니스 도메인을 명확하게 분리하여 마이크로서비스 간의 경계(Bounded Context)를 정의하는 법을 체득합니다.\"},{\"icon\":\"Layers\",\"title\":\"마이크로서비스 핵심\",\"color\":\"blue\",\"tags\":[\"Spring Cloud\",\"API Gateway\",\"Circuit Breaker\"],\"desc\":\"서비스 디스커버리와 장애 전파 차단을 위한 기술 스택을 도입해 안정적인 백엔드망을 오케스트레이션합니다.\"},{\"icon\":\"Database\",\"title\":\"분산 모니터링 & 트랜잭션\",\"color\":\"indigo\",\"isWide\":true,\"tags\":[\"Spring Cloud Data Flow\",\"Zipkin\",\"2PC/Saga\"],\"desc\":\"흩어진 서비스의 로그를 추적하고, 시스템 전체에 걸친 분산 트랜잭션을 최종적 정합성(Eventual Consistency)으로 해결합니다.\"}]",
                17)
        );

        boolean anyChange = false;
        for (Seed s : seeds) {
            var existingOpt = mentoringCourseRepository.findByCourseKey(s.key());
            if (existingOpt.isPresent()) {
                var existing = existingOpt.get();
                if (existing.getBoxesJson() == null || "[]".equals(existing.getBoxesJson())) {
                    existing.updateContent(s.title(), s.subtitle(), s.icon(),
                        s.descTitle(), s.descText(), s.boxesJson(), s.order(), true);
                    mentoringCourseRepository.save(existing);
                    anyChange = true;
                }
            } else {
                mentoringCourseRepository.save(MentoringCourse.builder()
                    .courseKey(s.key())
                    .title(s.title())
                    .subtitle(s.subtitle())
                    .iconString(s.icon())
                    .descriptionTitle(s.descTitle())
                    .descriptionText(s.descText())
                    .boxesJson(s.boxesJson())
                    .displayOrder(s.order())
                    .active(true)
                    .build());
                anyChange = true;
            }
        }
        if (anyChange) log.info("멘토링 코스 시드 업데이트 완료 (17개)");
        else log.info("멘토링 코스 시드 변경 없음.");
    }

    private void initDefaultAdmin() {
        String email = "admin@devmatch.com";
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("Admin1234!"))
                .name("DevMatch Admin")
                .role(Role.SUPER_ADMIN)
                .jobTitle("운영팀")
                .build());
        log.info("기본 SUPER_ADMIN 계정 시드 완료: {}", email);
    }

    private void createMentor(String name, String email, String encodedPassword,
                              List<String> courseKeys, int careerYears, String company, String bio) {
        java.util.List<MentoringCourse> foundCourses =
                mentoringCourseRepository.findAllByCourseKeyInAndActiveTrue(courseKeys);

        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            // 이미 존재하는 멘토 — 누락된 코스 링크/가용성만 보정 (기존 데이터 유지)
            User user = existingUser.get();
            mentorProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
                java.util.Set<Long> existingCourseIds = profile.getCourses().stream()
                        .map(MentoringCourse::getId)
                        .collect(java.util.stream.Collectors.toSet());
                boolean added = false;
                for (MentoringCourse c : foundCourses) {
                    if (!existingCourseIds.contains(c.getId())) {
                        profile.getCourses().add(c);
                        added = true;
                    }
                }
                if (added) {
                    mentorProfileRepository.save(profile);
                    log.info("멘토 [{}] 누락된 코스 링크 보정", name);
                }
            });
            if (mentorAvailabilityRepository.findByMentorId(user.getId()).isEmpty()) {
                mentorAvailabilityRepository.save(MentorAvailability.builder()
                        .mentorId(user.getId())
                        .isWaiting(true)
                        .isActive(true)
                        .dayOfWeek("MONDAY")
                        .startTime(java.time.LocalTime.of(9, 0))
                        .endTime(java.time.LocalTime.of(18, 0))
                        .build());
            }
            return;
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .role(Role.MENTOR)
                .build());

        mentorProfileRepository.save(MentorProfile.builder()
                .user(user)
                .courses(new java.util.HashSet<>(foundCourses))
                .careerYears(careerYears)
                .company(company)
                .bio(bio)
                .status(MentorStatus.APPROVED)
                .build());

        // 가용성 데이터 추가 (자동 매칭을 위해 필수)
        mentorAvailabilityRepository.save(MentorAvailability.builder()
                .mentorId(user.getId())
                .isWaiting(true)
                .isActive(true)
                .dayOfWeek("MONDAY")
                .startTime(java.time.LocalTime.of(9, 0))
                .endTime(java.time.LocalTime.of(18, 0))
                .build());
    }

    // ──────────────────────────────────────────────
    //  관리자 결제 관리 스모크용 seed — 7건
    //  Mentee + Application 이 없으면 graceful skip.
    // ──────────────────────────────────────────────
    private void initSamplePayments() {
        if (paymentRepository.count() > 0) {
            log.info("Payment seed skip — 이미 데이터 존재");
            return;
        }

        User mentee = userRepository.findByRole(Role.MENTEE).stream().findFirst().orElse(null);
        if (mentee == null) {
            log.info("Payment seed skip — MENTEE 가 없음. 먼저 멘티 + 신청서를 생성한 뒤 서버 재기동.");
            return;
        }

        List<Application> apps = applicationRepository.findByMenteeIdOrderByCreatedAtDesc(mentee.getId());
        if (apps.isEmpty()) {
            log.info("Payment seed skip — Application 이 없음.");
            return;
        }
        Long applicationId = apps.get(0).getId();

        List<Matching> matchings = matchingRepository.findByMenteeIdOrderByCreatedAtDesc(mentee.getId());
        Long matchingId1 = matchings.size() >= 1 ? matchings.get(0).getId() : null;
        Long matchingId2 = matchings.size() >= 2 ? matchings.get(1).getId() : null;

        User admin = userRepository.findByRole(Role.SUPER_ADMIN).stream().findFirst().orElse(null);
        Long adminId = admin != null ? admin.getId() : null;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // 1) CONFIRMED + matching
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(matchingId1)
                .orderId("SEED-ORD-001").paymentKey("FAKE_PK_SEED-ORD-001").amount(990_000)
                .status(PaymentStatus.CONFIRMED)
                .courseType("IMMEDIATE").monthsBundled(1).renewalCount(0)
                .build());

        // 2) CONFIRMED + matching (최근)
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(matchingId2)
                .orderId("SEED-ORD-002").paymentKey("FAKE_PK_SEED-ORD-002").amount(1_800_000)
                .status(PaymentStatus.CONFIRMED)
                .courseType("EARLY_BIRD").monthsBundled(2).renewalCount(0)
                .build());

        // 3) CONFIRMED without matching
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(null)
                .orderId("SEED-ORD-003").paymentKey("FAKE_PK_SEED-ORD-003").amount(990_000)
                .status(PaymentStatus.CONFIRMED)
                .courseType("IMMEDIATE").monthsBundled(1).renewalCount(0)
                .build());

        // 4) PENDING
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(null)
                .orderId("SEED-ORD-004").paymentKey(null).amount(990_000)
                .status(PaymentStatus.PENDING)
                .courseType("IMMEDIATE").monthsBundled(1).renewalCount(0)
                .build());

        // 5) CANCELLED by user
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(null)
                .orderId("SEED-ORD-005").paymentKey("FAKE_PK_SEED-ORD-005").amount(990_000)
                .status(PaymentStatus.CANCELLED).cancelReason("고객 요청 취소")
                .courseType("IMMEDIATE").monthsBundled(1).renewalCount(0)
                .build());

        // 6) CANCELLED by admin (환불 처리자 + 처리일시)
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(null)
                .orderId("SEED-ORD-006").paymentKey("FAKE_PK_SEED-ORD-006").amount(1_800_000)
                .status(PaymentStatus.CANCELLED).cancelReason("관리자 환불 — 서비스 오류 보상")
                .processedByAdminId(adminId).cancelledAt(now.minusDays(3))
                .courseType("EARLY_BIRD").monthsBundled(2).renewalCount(0)
                .build());

        // 7) FAILED
        paymentRepository.save(Payment.builder()
                .userId(mentee.getId()).applicationId(applicationId).matchingId(null)
                .orderId("SEED-ORD-007").paymentKey(null).amount(990_000)
                .status(PaymentStatus.FAILED)
                .courseType("IMMEDIATE").monthsBundled(1).renewalCount(0)
                .build());

        log.info("Payment seed 완료 — 7건 생성 (mentee={}, application={})", mentee.getId(), applicationId);
    }
}
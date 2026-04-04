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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (testRepository.count() > 0) {
            log.info("테스트 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("===== 테스트 초기 데이터 삽입 시작 =====");

        initJavaTests();
        initSpringTests();
        initReactTests();
        initPythonTests();
        initAlgorithmTests();
        initMentors();

        log.info("===== 초기 데이터 삽입 완료: 테스트 {}개, 문제 {}개 =====",
                testRepository.count(), questionRepository.count());
    }

    // ──────────────────────────────────────────────
    //  Java
    // ──────────────────────────────────────────────

    private void initJavaTests() {
        // BEGINNER
        Test t = createTest("Java 기초 문법 테스트",
                "Java 언어의 기본 문법과 핵심 개념을 평가합니다. 변수, 타입, 조건문, 반복문, 배열, 클래스 기초 등을 다룹니다.",
                "Java", Difficulty.BEGINNER, 15, 60, 10);
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
                "Java", Difficulty.INTERMEDIATE, 20, 65, 10);
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
                "Java", Difficulty.ADVANCED, 25, 70, 10);
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
                "Spring", Difficulty.BEGINNER, 15, 60, 10);
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
                "Spring", Difficulty.INTERMEDIATE, 20, 65, 10);
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
                "Spring", Difficulty.ADVANCED, 25, 70, 10);
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
                "React", Difficulty.BEGINNER, 15, 60, 10);
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
                "React", Difficulty.INTERMEDIATE, 20, 65, 10);
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
                "React", Difficulty.ADVANCED, 25, 70, 10);
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
                "Python", Difficulty.BEGINNER, 15, 60, 10);
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
                "Python", Difficulty.INTERMEDIATE, 20, 65, 10);
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
                "Python", Difficulty.ADVANCED, 25, 70, 10);
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
                "Algorithm", Difficulty.BEGINNER, 15, 60, 10);
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
                "Algorithm", Difficulty.INTERMEDIATE, 20, 65, 10);
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
                "Algorithm", Difficulty.ADVANCED, 25, 70, 10);
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
                List.of("Java", "Spring"), 8, "네이버", "Java/Spring 전문 멘토입니다. 대규모 서비스 설계 경험이 풍부합니다.");
        createMentor("이스프링", "spring.mentor@devmatch.com", encoded,
                List.of("Spring", "DevOps"), 5, "카카오", "Spring 기반 MSA 설계와 DevOps 파이프라인 구축 경험이 있습니다.");
        createMentor("박리액트", "react.mentor@devmatch.com", encoded,
                List.of("React", "Node.js"), 6, "라인", "React와 Node.js 풀스택 개발 멘토입니다.");
        createMentor("최파이썬", "python.mentor@devmatch.com", encoded,
                List.of("Python", "Algorithm"), 7, "쿠팡", "Python 백엔드와 알고리즘 전문 멘토입니다.");
        createMentor("정풀스택", "fullstack.mentor@devmatch.com", encoded,
                List.of("Java", "React", "Spring"), 10, "토스", "10년차 풀스택 개발자입니다. 서비스 전체 아키텍처 설계를 도와드립니다.");

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

    private void createMentor(String name, String email, String encodedPassword,
                              List<String> specialty, int careerYears, String company, String bio) {
        if (userRepository.existsByEmail(email)) {
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
                .specialty(specialty)
                .careerYears(careerYears)
                .company(company)
                .bio(bio)
                .status(MentorStatus.APPROVED)
                .build());
    }
}
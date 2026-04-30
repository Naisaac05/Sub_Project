package com.devmatch.config;

import com.devmatch.entity.Difficulty;
import com.devmatch.entity.Question;
import com.devmatch.entity.Test;
import com.devmatch.repository.QuestionRepository;
import com.devmatch.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseSkillTestInitializer {

    private static final int DIAGNOSTIC_TIME_LIMIT = 30;
    private static final int DIAGNOSTIC_PASSING_SCORE = 60;
    private static final int DIAGNOSTIC_QUESTION_COUNT = 10;

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initCourseSkillTests() {
        seedJavaBackend();
        seedFrontend();
        seedPythonBackend();
        seedAdditionalDiagnostics();
    }

    private void seedJavaBackend() {
        Test test = upsertDiagnosticTest(
                "java-backend",
                "Java Backend + AI 실력 진단",
                "Java, Spring, JPA, REST API, 트랜잭션 흐름을 기준으로 현재 백엔드 실무 준비도를 진단합니다.");

        upsertQuestions(test, List.of(
                sq(1, "Java에서 equals와 hashCode를 함께 재정의해야 하는 가장 중요한 이유는 무엇인가요?",
                        List.of("GC 성능을 높이기 위해", "HashMap, HashSet 같은 컬렉션에서 객체 동등성을 올바르게 처리하기 위해", "컴파일 속도를 높이기 위해", "상속을 금지하기 위해"), 1),
                sq(2, "Spring에서 @Transactional이 주로 적용되는 계층으로 가장 적절한 곳은 어디인가요?",
                        List.of("Controller", "Service", "Repository interface 선언부만", "DTO"), 1),
                sq(3, "JPA의 N+1 문제를 줄이기 위한 방법으로 가장 적절한 것은 무엇인가요?",
                        List.of("fetch join 또는 EntityGraph 사용", "모든 컬럼을 String으로 저장", "트랜잭션 제거", "테이블명을 짧게 변경"), 0),
                sq(4, "REST API에서 리소스를 새로 생성했을 때 일반적으로 가장 적절한 HTTP 상태 코드는 무엇인가요?",
                        List.of("200 OK", "201 Created", "204 No Content", "404 Not Found"), 1),
                sq(5, "여러 사용자가 동시에 같은 쿠폰을 발급받는 상황에서 트랜잭션 격리가 필요한 이유는 무엇인가요?",
                        List.of("HTML 렌더링 속도를 높이기 위해", "중복 발급이나 데이터 불일치를 막기 위해", "로그인을 생략하기 위해", "응답 JSON을 압축하기 위해"), 1),
                sq(6, "Spring Security에서 인증된 사용자 정보를 컨트롤러에서 가져올 때 자주 사용하는 방식은 무엇인가요?",
                        List.of("@AuthenticationPrincipal", "@PathVariable", "@RequestBody", "@Scheduled"), 0),
                sq(7, "JPA 엔티티를 API 응답으로 그대로 반환하는 방식의 위험으로 가장 적절한 것은 무엇인가요?",
                        List.of("컴파일이 불가능하다", "지연 로딩, 순환 참조, 불필요한 필드 노출 문제가 생길 수 있다", "HTTP 메서드를 사용할 수 없다", "DB 인덱스가 자동 삭제된다"), 1),
                sq(8, "대용량 목록 조회 API에서 우선 고려해야 할 설계는 무엇인가요?",
                        List.of("전체 데이터를 한 번에 반환", "페이지네이션과 정렬 기준 제공", "모든 요청을 POST로 변경", "프론트에서만 필터링"), 1),
                sq(9, "분산 환경에서 같은 작업이 중복 실행되지 않도록 제어해야 할 때 고려할 수 있는 것은 무엇인가요?",
                        List.of("분산 락 또는 멱등성 키", "CSS 변수", "브라우저 쿠키 삭제", "이미지 lazy loading"), 0),
                sq(10, "API 예외 처리에서 @ControllerAdvice를 사용하는 주된 목적은 무엇인가요?",
                        List.of("DB 테이블 생성", "공통 예외 응답 형식을 중앙에서 관리", "패키지명을 변경", "빌드 시간을 단축"), 1)
        ));
    }

    private void seedFrontend() {
        Test test = upsertDiagnosticTest(
                "frontend",
                "Frontend + AI 실력 진단",
                "React, Next.js, 상태 관리, 렌더링, CSS/UI 구현 흐름을 기준으로 프론트엔드 준비도를 진단합니다.");

        upsertQuestions(test, List.of(
                sq(1, "React에서 state를 직접 변경하지 않고 setter를 사용해야 하는 이유는 무엇인가요?",
                        List.of("파일 크기를 줄이기 위해", "React가 변경을 감지하고 다시 렌더링할 수 있게 하기 위해", "CSS를 자동 생성하기 위해", "라우팅을 막기 위해"), 1),
                sq(2, "Next.js App Router에서 클라이언트 훅을 쓰는 컴포넌트 상단에 필요한 선언은 무엇인가요?",
                        List.of("'use client';", "'use server';", "'use router';", "'use effect';"), 0),
                sq(3, "목록을 렌더링할 때 key가 필요한 가장 중요한 이유는 무엇인가요?",
                        List.of("텍스트 색상을 바꾸기 위해", "React가 각 항목의 변경을 안정적으로 추적하기 위해", "API 요청을 막기 위해", "이미지를 압축하기 위해"), 1),
                sq(4, "useEffect의 의존성 배열을 잘못 비워두었을 때 생길 수 있는 문제는 무엇인가요?",
                        List.of("필요한 값 변경에 반응하지 않는 오래된 상태 문제가 생길 수 있다", "브라우저가 즉시 종료된다", "TypeScript가 삭제된다", "CSS가 서버에서만 적용된다"), 0),
                sq(5, "서버에서 받아온 데이터가 아직 없을 때 UI가 가져야 할 상태로 가장 적절한 것은 무엇인가요?",
                        List.of("로딩, 에러, 빈 상태를 분리해서 보여준다", "무조건 빈 배열로만 처리한다", "페이지를 새로고침한다", "콘솔에만 출력한다"), 0),
                sq(6, "CSS에서 반응형 레이아웃을 만들 때 자주 사용하는 접근은 무엇인가요?",
                        List.of("고정 px만 사용", "flex/grid와 breakpoint 조합", "이미지만 확대", "모든 텍스트 숨김"), 1),
                sq(7, "전역 상태 관리가 필요한 상황으로 가장 적절한 것은 무엇인가요?",
                        List.of("한 버튼 내부 hover 상태", "로그인 사용자 정보처럼 여러 화면에서 공유되는 값", "한 input의 focus 여부", "단일 모달 내부 임시 값"), 1),
                sq(8, "사용자가 버튼을 여러 번 빠르게 클릭해 중복 요청을 보내는 문제를 줄이는 방법은 무엇인가요?",
                        List.of("요청 중 버튼 비활성화와 로딩 상태 처리", "버튼 색상 변경만 적용", "이미지 삭제", "라우터 제거"), 0),
                sq(9, "Next.js에서 동적 라우트 /mentors/[id]의 id를 읽을 때 사용하는 값은 무엇인가요?",
                        List.of("params", "headers only", "cookies only", "metadata"), 0),
                sq(10, "접근성 측면에서 아이콘 버튼에 필요한 처리로 가장 적절한 것은 무엇인가요?",
                        List.of("aria-label 또는 스크린리더가 읽을 수 있는 이름 제공", "아이콘을 더 작게 만들기", "색상을 한 가지로 고정", "hover 효과 제거"), 0)
        ));
    }

    private void seedPythonBackend() {
        Test test = upsertDiagnosticTest(
                "python-backend",
                "Python Backend + AI 실력 진단",
                "Python, FastAPI/Django, ORM, 비동기 처리, 데이터 처리 흐름을 기준으로 백엔드 준비도를 진단합니다.");

        upsertQuestions(test, List.of(
                sq(1, "Python에서 리스트 컴프리헨션을 사용하는 주된 장점은 무엇인가요?",
                        List.of("항상 더 느리다", "반복 변환 로직을 간결하게 표현할 수 있다", "네트워크를 자동 연결한다", "DB 트랜잭션을 생성한다"), 1),
                sq(2, "FastAPI에서 요청 본문을 검증하고 문서화하는 데 자주 함께 쓰이는 것은 무엇인가요?",
                        List.of("Pydantic 모델", "CSS module", "Docker volume", "Git tag"), 0),
                sq(3, "Django ORM에서 select_related를 고려하는 대표적인 이유는 무엇인가요?",
                        List.of("관련 객체 조회 쿼리 수를 줄이기 위해", "HTML을 압축하기 위해", "비밀번호를 평문 저장하기 위해", "서버 포트를 변경하기 위해"), 0),
                sq(4, "async/await를 사용할 때 가장 주의해야 할 점은 무엇인가요?",
                        List.of("블로킹 I/O를 섞으면 비동기 장점이 줄어들 수 있다", "모든 함수가 자동으로 병렬 실행된다", "DB가 필요 없어지는 것이다", "타입 힌트를 쓸 수 없다"), 0),
                sq(5, "API에서 입력값 검증을 서버에서도 해야 하는 이유로 가장 적절한 것은 무엇인가요?",
                        List.of("프론트 검증은 우회될 수 있기 때문에", "CSS가 깨지기 때문에", "라우팅이 없어지기 때문에", "로그 파일이 커지기 때문에"), 0),
                sq(6, "데이터 처리 배치 작업에서 idempotent 설계가 중요한 이유는 무엇인가요?",
                        List.of("재실행 시 중복 처리나 데이터 오염을 줄이기 위해", "모든 로그를 숨기기 위해", "메모리를 무조건 많이 쓰기 위해", "테스트를 생략하기 위해"), 0),
                sq(7, "Python 백엔드에서 환경변수로 관리하는 것이 적절한 값은 무엇인가요?",
                        List.of("DB 비밀번호와 API 키", "함수 이름", "반복문 횟수", "HTML 태그명"), 0),
                sq(8, "ORM을 사용할 때 raw SQL이 필요한 경우로 가장 적절한 것은 무엇인가요?",
                        List.of("ORM으로 표현하기 어렵거나 성능상 튜닝이 필요한 복잡한 쿼리", "모든 단순 조회", "모든 insert", "모든 테스트 코드"), 0),
                sq(9, "대용량 CSV 업로드 API에서 우선 고려해야 할 것은 무엇인가요?",
                        List.of("파일 크기 제한, 스트리밍/배치 처리, 실패 복구 전략", "텍스트 색상", "버튼 둥글기", "로그인 화면 배경"), 0),
                sq(10, "AI 기능을 백엔드에 붙일 때 응답 지연을 줄이기 위한 설계로 적절한 것은 무엇인가요?",
                        List.of("캐싱, 비동기 작업 큐, 타임아웃 처리", "모든 요청을 무한 대기", "DB 인덱스 삭제", "프론트 라우팅 제거"), 0)
        ));
    }

    private void seedAdditionalDiagnostics() {
        seedCourse("node-backend", "Node Backend + AI 실력 진단",
                "TypeScript, Node.js, Express/Nest, 비동기 처리, API 설계를 기준으로 진단합니다.",
                List.of(
                        sq(1, "Node.js 이벤트 루프에서 오래 걸리는 CPU 작업을 메인 스레드에서 직접 처리하면 생기는 문제는 무엇인가요?", List.of("다른 요청 처리까지 지연될 수 있다", "타입 검사가 강화된다", "DB 인덱스가 자동 생성된다", "HTTP 상태 코드가 바뀐다"), 0),
                        sq(2, "TypeScript에서 DTO 타입을 명확히 정의하는 주된 이유는 무엇인가요?", List.of("요청/응답 구조를 컴파일 단계에서 더 안전하게 다루기 위해", "CSS를 줄이기 위해", "포트를 자동 변경하기 위해", "로그인을 생략하기 위해"), 0),
                        sq(3, "NestJS에서 관심사를 분리하기 위해 Controller가 주로 담당해야 하는 것은 무엇인가요?", List.of("요청을 받고 Service에 작업을 위임", "모든 DB 쿼리 직접 작성", "비밀번호 암호화 알고리즘 구현", "프론트 UI 렌더링"), 0),
                        sq(4, "Express 미들웨어의 대표적인 역할은 무엇인가요?", List.of("요청 흐름 중 인증, 로깅, 파싱 같은 공통 처리를 수행", "테이블을 자동 정규화", "브라우저 캐시 삭제", "컴포넌트 상태 관리"), 0),
                        sq(5, "Promise.all을 사용할 때 주의해야 할 점은 무엇인가요?", List.of("하나라도 실패하면 전체가 reject될 수 있다", "항상 순차 실행된다", "메모리를 전혀 쓰지 않는다", "트랜잭션이 자동 보장된다"), 0),
                        sq(6, "API rate limit을 두는 이유로 가장 적절한 것은 무엇인가요?", List.of("남용 요청으로부터 서비스와 리소스를 보호하기 위해", "색상을 통일하기 위해", "DTO를 제거하기 위해", "라우팅을 막기 위해"), 0),
                        sq(7, "JWT 인증에서 access token 만료 시간을 너무 길게 잡으면 생기는 위험은 무엇인가요?", List.of("탈취 시 피해 시간이 길어진다", "응답 속도가 반드시 빨라진다", "DB 스키마가 단순해진다", "CORS가 사라진다"), 0),
                        sq(8, "ORM에서 트랜잭션이 필요한 대표 상황은 무엇인가요?", List.of("여러 테이블 변경이 하나의 업무 단위로 묶여야 할 때", "GET 요청만 있을 때", "CSS 파일을 빌드할 때", "정적 이미지를 내려줄 때"), 0),
                        sq(9, "WebSocket을 사용할 때 HTTP API보다 추가로 고려해야 할 것은 무엇인가요?", List.of("연결 상태, 재연결, 세션 정리", "이미지 alt 값", "HTML heading 순서", "폰트 크기"), 0),
                        sq(10, "Node 백엔드에서 환경변수로 관리해야 하는 값은 무엇인가요?", List.of("DB 비밀번호와 외부 API 키", "함수 이름", "반복문 인덱스", "컴포넌트 props"), 0)
                ));

        seedCourse("android", "Android + AI 실력 진단",
                "Android 생명주기, Kotlin, 상태 관리, 네이티브 기능, 배포 흐름을 기준으로 진단합니다.",
                List.of(
                        sq(1, "Activity 생명주기를 이해해야 하는 이유는 무엇인가요?", List.of("화면 전환과 백그라운드 복귀 시 상태를 안전하게 관리하기 위해", "앱 아이콘을 자동 생성하기 위해", "서버 포트를 바꾸기 위해", "SQL을 제거하기 위해"), 0),
                        sq(2, "ViewModel을 사용하는 주된 이유는 무엇인가요?", List.of("화면 회전 같은 구성 변경에도 UI 상태를 유지하기 위해", "APK 크기를 무조건 줄이기 위해", "네트워크를 자동 암호화하기 위해", "XML을 금지하기 위해"), 0),
                        sq(3, "Coroutine에서 Dispatchers.IO를 고려하는 대표 상황은 무엇인가요?", List.of("네트워크나 파일 I/O 작업", "버튼 색상 변경", "텍스트 크기 계산만", "화면 제목 표시"), 0),
                        sq(4, "Room을 사용할 때 DAO의 역할은 무엇인가요?", List.of("DB 접근 쿼리를 정의하는 계층", "이미지 압축 계층", "푸시 알림 서버", "권한 요청 UI"), 0),
                        sq(5, "Android 권한 요청에서 중요한 점은 무엇인가요?", List.of("필요한 시점에 이유를 설명하고 사용자 선택을 처리한다", "모든 권한을 시작 시 강제로 요청한다", "권한 거부를 무시한다", "권한은 서버에서만 처리한다"), 0),
                        sq(6, "RecyclerView에서 ViewHolder를 쓰는 이유는 무엇인가요?", List.of("목록 아이템 뷰를 재사용해 성능을 높이기 위해", "DB를 삭제하기 위해", "HTTP 캐시를 제어하기 위해", "앱 서명을 생략하기 위해"), 0),
                        sq(7, "Compose에서 state hoisting의 목적은 무엇인가요?", List.of("상태 소유권을 상위로 올려 재사용성과 테스트성을 높이기 위해", "상태를 숨기기 위해", "네트워크 요청을 막기 위해", "빌드 도구를 바꾸기 위해"), 0),
                        sq(8, "앱 배포 전 release build에서 확인해야 할 것은 무엇인가요?", List.of("서명, 난독화, 환경 설정, 권한", "버튼 둥글기만", "임시 로그만", "테스트 계정 이름"), 0),
                        sq(9, "푸시 알림을 구현할 때 고려해야 할 것은 무엇인가요?", List.of("토큰 갱신, 권한, 알림 채널", "CSS media query", "SQL join 종류", "HTML meta tag"), 0),
                        sq(10, "오프라인 대응이 필요한 앱에서 우선 고려할 것은 무엇인가요?", List.of("로컬 캐시와 동기화 전략", "서버 렌더링", "브라우저 쿠키", "마우스 hover"), 0)
                ));

        seedCourse("ios", "iOS + AI 실력 진단",
                "Swift, iOS 생명주기, 상태 관리, 네이티브 기능, 배포 흐름을 기준으로 진단합니다.",
                List.of(
                        sq(1, "iOS 앱 생명주기를 이해해야 하는 이유는 무엇인가요?", List.of("앱 foreground/background 전환에 맞춰 상태와 리소스를 관리하기 위해", "DB 인덱스를 자동 생성하기 위해", "HTML을 렌더링하기 위해", "서버 포트를 열기 위해"), 0),
                        sq(2, "Swift에서 Optional을 사용하는 이유는 무엇인가요?", List.of("값이 없을 수 있음을 타입으로 표현하기 위해", "모든 값을 문자열로 만들기 위해", "메모리를 쓰지 않기 위해", "네트워크 요청을 자동 실행하기 위해"), 0),
                        sq(3, "UIKit에서 ViewController가 지나치게 커질 때 생기는 문제는 무엇인가요?", List.of("테스트와 유지보수가 어려워진다", "앱이 자동 배포된다", "이미지가 항상 깨진다", "컴파일러가 사라진다"), 0),
                        sq(4, "SwiftUI에서 @State를 사용하는 대표 상황은 무엇인가요?", List.of("뷰 내부의 작은 UI 상태를 관리할 때", "서버 비밀번호 저장", "앱 서명 관리", "DB 마이그레이션"), 0),
                        sq(5, "URLSession 사용 시 고려해야 할 것은 무엇인가요?", List.of("에러, 타임아웃, 응답 코드, 디코딩 실패 처리", "폰트 이름만", "앱 아이콘만", "Git 브랜치 이름"), 0),
                        sq(6, "iOS 권한 요청에서 중요한 점은 무엇인가요?", List.of("사용자에게 필요한 이유를 설명하고 거부 상황을 처리한다", "모든 권한을 강제한다", "권한 상태를 저장하지 않는다", "서버에서만 승인한다"), 0),
                        sq(7, "CoreData나 로컬 저장소를 쓸 때 주의할 점은 무엇인가요?", List.of("스키마 변경과 동기화, 마이그레이션을 고려한다", "색상 팔레트만 고려한다", "HTTP method를 바꾼다", "버튼을 제거한다"), 0),
                        sq(8, "App Store 배포 전 확인해야 할 것은 무엇인가요?", List.of("인증서, 프로비저닝, 권한 문구, 심사 정책", "CSS 파일", "Node 버전만", "브라우저 캐시"), 0),
                        sq(9, "Combine이나 async/await 흐름에서 중요한 것은 무엇인가요?", List.of("비동기 결과와 취소, 에러 처리를 명확히 관리한다", "모든 작업을 main thread에서만 실행한다", "옵셔널을 금지한다", "뷰 이름을 줄인다"), 0),
                        sq(10, "메모리 누수를 줄이기 위해 클로저에서 고려할 수 있는 것은 무엇인가요?", List.of("필요할 때 weak self 사용", "모든 변수를 static으로 변경", "권한 요청 생략", "앱 이름 변경"), 0)
                ));

        seedCourse("flutter", "Flutter + AI 실력 진단",
                "Dart, Flutter 위젯, 상태 관리, 네이티브 연동, 배포 흐름을 기준으로 진단합니다.",
                List.of(
                        sq(1, "Flutter에서 Widget tree를 이해해야 하는 이유는 무엇인가요?", List.of("UI 구조와 rebuild 범위를 예측하기 위해", "DB 트랜잭션을 만들기 위해", "서버 라우팅을 설정하기 위해", "CSS를 컴파일하기 위해"), 0),
                        sq(2, "StatefulWidget이 필요한 대표 상황은 무엇인가요?", List.of("사용자 입력처럼 화면 내부 상태가 바뀔 때", "항상 정적인 텍스트만 보여줄 때", "서버 스키마를 만들 때", "이미지 파일명을 바꿀 때"), 0),
                        sq(3, "Provider, Riverpod, Bloc 같은 상태 관리 도구가 필요한 이유는 무엇인가요?", List.of("화면 간 상태 공유와 변경 흐름을 관리하기 위해", "앱 아이콘을 만들기 위해", "HTTP를 금지하기 위해", "Dart 문법을 제거하기 위해"), 0),
                        sq(4, "FutureBuilder를 사용할 때 주의할 점은 무엇인가요?", List.of("Future 생성 위치를 잘못 잡으면 불필요한 재요청이 생길 수 있다", "항상 동기 실행된다", "빌드가 불가능하다", "상태가 자동 저장된다"), 0),
                        sq(5, "Flutter에서 platform channel을 사용하는 이유는 무엇인가요?", List.of("Dart에서 네이티브 Android/iOS 기능을 호출하기 위해", "색상을 자동 생성하기 위해", "라우터를 삭제하기 위해", "테스트를 막기 위해"), 0),
                        sq(6, "ListView.builder를 사용하는 대표 이유는 무엇인가요?", List.of("긴 목록을 필요한 만큼 효율적으로 렌더링하기 위해", "모든 항목을 즉시 이미지로 저장하기 위해", "DB를 초기화하기 위해", "권한을 요청하기 위해"), 0),
                        sq(7, "Flutter 앱 배포 전 확인해야 할 것은 무엇인가요?", List.of("서명, 빌드 flavor, 권한, 스토어 정책", "컴포넌트 이름만", "테이블 join만", "브라우저 viewport"), 0),
                        sq(8, "Dart의 null safety가 주는 장점은 무엇인가요?", List.of("null 가능성을 타입으로 관리해 런타임 오류를 줄인다", "모든 값을 null로 만든다", "비동기를 제거한다", "이미지를 압축한다"), 0),
                        sq(9, "애니메이션이 많은 Flutter 화면에서 고려할 것은 무엇인가요?", List.of("불필요한 rebuild와 프레임 드랍을 줄인다", "모든 위젯을 const 금지한다", "네트워크를 차단한다", "DB를 삭제한다"), 0),
                        sq(10, "오프라인 캐시가 필요한 Flutter 앱에서 고려할 것은 무엇인가요?", List.of("로컬 저장소와 서버 동기화 정책", "CSS media query", "Spring Bean", "HTML form"), 0)
                ));

        seedCourse("react-native", "React Native + AI 실력 진단",
                "React Native, 모바일 생명주기, 상태 관리, 네이티브 모듈, 배포 흐름을 기준으로 진단합니다.",
                List.of(
                        sq(1, "React Native에서 bridge 또는 native module을 고려하는 상황은 무엇인가요?", List.of("JS만으로 접근하기 어려운 네이티브 기능이 필요할 때", "텍스트 색상만 바꿀 때", "배열을 정렬할 때", "CSS class를 만들 때"), 0),
                        sq(2, "FlatList를 사용하는 주된 이유는 무엇인가요?", List.of("긴 목록을 효율적으로 렌더링하기 위해", "앱 서명을 자동화하기 위해", "DB 인덱스를 만들기 위해", "권한을 생략하기 위해"), 0),
                        sq(3, "React Native 앱에서 navigation 상태를 잘 관리해야 하는 이유는 무엇인가요?", List.of("화면 전환, deep link, back action 흐름이 사용자 경험에 직접 영향을 주기 때문", "SQL이 빨라지기 때문", "서버가 자동 배포되기 때문", "이미지가 자동 생성되기 때문"), 0),
                        sq(4, "모바일 권한 요청에서 중요한 점은 무엇인가요?", List.of("권한 필요 이유와 거부 시 대체 흐름을 제공한다", "모든 권한을 강제한다", "권한 상태를 무시한다", "웹에서만 처리한다"), 0),
                        sq(5, "React Native 성능 문제에서 먼저 확인할 수 있는 것은 무엇인가요?", List.of("불필요한 re-render, 큰 리스트, 무거운 JS 작업", "HTML heading", "DB 정규화", "서버 JVM 옵션만"), 0),
                        sq(6, "AsyncStorage나 로컬 저장소에 저장하면 안 되는 값은 무엇인가요?", List.of("민감한 토큰 원문이나 비밀번호", "테마 이름", "온보딩 완료 여부", "임시 필터 값"), 0),
                        sq(7, "앱 배포 전 Android/iOS 공통으로 확인할 것은 무엇인가요?", List.of("서명, 권한 문구, 환경별 API URL, 스토어 정책", "CSS hover", "SQL join", "서버 포트만"), 0),
                        sq(8, "React Native에서 push notification 구현 시 고려할 것은 무엇인가요?", List.of("토큰 갱신, 권한, 플랫폼별 설정", "브라우저 favicon", "HTML form action", "JPA fetch join"), 0),
                        sq(9, "전역 상태 관리가 필요한 모바일 상황은 무엇인가요?", List.of("로그인 사용자와 앱 전체 설정처럼 여러 화면이 공유하는 값", "한 버튼의 눌림 상태", "한 TextInput의 placeholder", "일회성 애니메이션 flag"), 0),
                        sq(10, "네트워크가 불안정한 모바일 환경에서 필요한 처리는 무엇인가요?", List.of("로딩, 재시도, timeout, 오프라인 안내", "무한 대기", "모든 요청 동시 실행", "에러 숨김"), 0)
                ));

        seedCourse("devops", "DevOps 실력 진단",
                "Docker, CI/CD, Linux, AWS, 네트워크 운영 기초를 기준으로 진단합니다.",
                List.of(
                        sq(1, "Docker 이미지를 작게 유지해야 하는 이유는 무엇인가요?", List.of("배포 속도와 보안 표면, 저장 공간을 줄이기 위해", "코드 줄 수를 늘리기 위해", "DB를 제거하기 위해", "HTTP를 금지하기 위해"), 0),
                        sq(2, "Dockerfile에서 layer 캐시를 고려하는 이유는 무엇인가요?", List.of("빌드 시간을 줄이고 변경 범위를 최소화하기 위해", "포트를 자동 선택하기 위해", "로그인을 생략하기 위해", "권한을 상승시키기 위해"), 0),
                        sq(3, "CI/CD 파이프라인에서 테스트 단계를 두는 이유는 무엇인가요?", List.of("배포 전 회귀 버그를 줄이기 위해", "커밋 메시지를 숨기기 위해", "서버 비용을 늘리기 위해", "브랜치를 삭제하기 위해"), 0),
                        sq(4, "Linux에서 프로세스가 포트를 점유했는지 확인해야 하는 상황은 무엇인가요?", List.of("서버가 해당 포트로 시작하지 못할 때", "CSS가 깨질 때", "이미지가 흐릴 때", "SQL 컬럼명이 길 때"), 0),
                        sq(5, "로드밸런서의 주된 역할은 무엇인가요?", List.of("요청을 여러 서버로 분산하고 가용성을 높인다", "소스 코드를 압축한다", "DB 스키마를 변경한다", "브라우저 캐시를 삭제한다"), 0),
                        sq(6, "AWS 보안 그룹에서 인바운드 규칙을 최소화해야 하는 이유는 무엇인가요?", List.of("불필요한 외부 접근을 줄이기 위해", "CPU를 높이기 위해", "배포를 느리게 하기 위해", "로그를 없애기 위해"), 0),
                        sq(7, "블루/그린 배포의 장점은 무엇인가요?", List.of("새 버전 문제 발생 시 빠르게 이전 환경으로 되돌릴 수 있다", "DB 백업이 필요 없어진다", "테스트가 자동 삭제된다", "모든 비용이 0원이 된다"), 0),
                        sq(8, "모니터링에서 alert threshold를 정할 때 고려할 것은 무엇인가요?", List.of("정상 변동과 실제 장애 신호를 구분한다", "항상 100%로 둔다", "모든 로그를 버린다", "UI 색상만 맞춘다"), 0),
                        sq(9, "HTTPS 인증서 갱신을 자동화해야 하는 이유는 무엇인가요?", List.of("만료로 인한 서비스 중단을 막기 위해", "DB 성능을 높이기 위해", "빌드 파일을 줄이기 위해", "이미지 크기를 줄이기 위해"), 0),
                        sq(10, "배포 롤백 전략이 필요한 이유는 무엇인가요?", List.of("장애 발생 시 빠르게 안정 버전으로 복구하기 위해", "브랜치 이름을 바꾸기 위해", "코드를 난독화하기 위해", "권한 요청을 줄이기 위해"), 0)
                ));

        seedCourse("data-engineer", "Data Engineer + AI 실력 진단",
                "SQL, ETL, 파이프라인, 배치/스트리밍, 데이터 품질을 기준으로 진단합니다.",
                List.of(
                        sq(1, "SQL에서 인덱스가 도움이 되는 대표 상황은 무엇인가요?", List.of("조건 검색과 정렬이 자주 발생하는 컬럼 조회", "모든 insert만 있는 테이블", "컬럼 수를 줄일 때", "HTML 렌더링"), 0),
                        sq(2, "ETL에서 Transform 단계의 역할은 무엇인가요?", List.of("원천 데이터를 목적에 맞게 정제하고 구조화한다", "서버 포트를 연다", "CSS를 빌드한다", "권한을 요청한다"), 0),
                        sq(3, "데이터 파이프라인에서 idempotency가 중요한 이유는 무엇인가요?", List.of("재실행 시 중복 적재나 오염을 줄이기 위해", "메모리를 항상 많이 쓰기 위해", "로그를 제거하기 위해", "쿼리를 금지하기 위해"), 0),
                        sq(4, "배치 처리와 스트리밍 처리의 큰 차이는 무엇인가요?", List.of("일정 단위 묶음 처리와 실시간/준실시간 연속 처리", "프론트와 백엔드 차이", "HTTP와 CSS 차이", "정규화와 난독화 차이"), 0),
                        sq(5, "데이터 품질 검증에서 확인할 수 있는 것은 무엇인가요?", List.of("null, 중복, 범위 오류, 스키마 불일치", "버튼 색상", "이미지 비율", "브라우저 너비"), 0),
                        sq(6, "파티셔닝을 사용하는 이유로 적절한 것은 무엇인가요?", List.of("큰 데이터를 기준별로 나누어 조회와 관리를 효율화하기 위해", "모든 데이터를 하나의 파일로 합치기 위해", "권한을 없애기 위해", "UI를 빠르게 하기 위해"), 0),
                        sq(7, "데이터 lineage가 중요한 이유는 무엇인가요?", List.of("데이터가 어디서 와서 어떻게 변했는지 추적하기 위해", "HTML 구조를 만들기 위해", "서버를 재부팅하기 위해", "이미지를 압축하기 위해"), 0),
                        sq(8, "증분 적재를 설계할 때 필요한 기준은 무엇인가요?", List.of("변경 시각, 증가 ID, watermark 같은 변경 추적 기준", "버튼 radius", "폰트 이름", "도메인 색상"), 0),
                        sq(9, "메시지 큐 기반 파이프라인에서 고려해야 할 것은 무엇인가요?", List.of("중복 소비, 순서, 재처리, 실패 처리", "CSS selector", "JPA EntityGraph", "앱 아이콘"), 0),
                        sq(10, "분석용 테이블 설계에서 자주 고려하는 것은 무엇인가요?", List.of("조회 패턴에 맞춘 denormalization과 집계 테이블", "모든 테이블 완전 삭제", "모든 컬럼 암호화만", "HTML 태그 순서"), 0)
                ));

        seedCourse("ml-engineer", "ML Engineer 실력 진단",
                "모델 기본, 데이터 전처리, 평가 지표, MLOps, 실험 관리를 기준으로 진단합니다.",
                List.of(
                        sq(1, "train/validation/test 데이터를 나누는 이유는 무엇인가요?", List.of("모델 일반화 성능을 공정하게 확인하기 위해", "데이터를 숨기기 위해", "모든 오류를 제거하기 위해", "GPU를 사용하지 않기 위해"), 0),
                        sq(2, "과적합(overfitting)의 특징은 무엇인가요?", List.of("훈련 성능은 높지만 새로운 데이터 성능이 낮다", "모든 데이터에서 항상 0점이다", "모델이 학습하지 않는다", "데이터가 자동 정규화된다"), 0),
                        sq(3, "분류 모델에서 precision이 중요한 상황은 무엇인가요?", List.of("양성 예측의 정확도가 특히 중요할 때", "모든 음성을 놓쳐도 될 때", "회귀 문제만 있을 때", "데이터가 없을 때"), 0),
                        sq(4, "데이터 누수(data leakage)가 위험한 이유는 무엇인가요?", List.of("실제보다 성능이 과대평가될 수 있기 때문에", "모델 파일이 커지기 때문에", "학습이 무조건 느려지기 때문에", "정답이 사라지기 때문에"), 0),
                        sq(5, "feature scaling이 필요한 대표 모델은 무엇인가요?", List.of("거리 기반 모델이나 gradient 기반 모델", "모든 트리 모델만", "SQL join", "HTML parser"), 0),
                        sq(6, "MLOps에서 모델 버전 관리가 필요한 이유는 무엇인가요?", List.of("어떤 데이터와 코드로 만든 모델인지 추적하고 롤백하기 위해", "UI 색상을 맞추기 위해", "서버 포트를 줄이기 위해", "테스트를 생략하기 위해"), 0),
                        sq(7, "모델 배포 후 모니터링해야 할 것은 무엇인가요?", List.of("성능 저하, 데이터 drift, 지연 시간, 오류율", "버튼 hover", "CSS 크기", "브랜치 이름"), 0),
                        sq(8, "불균형 데이터에서 accuracy만 보면 위험한 이유는 무엇인가요?", List.of("소수 클래스 성능이 가려질 수 있기 때문에", "학습이 불가능하기 때문에", "정답이 모두 같기 때문에", "모델 저장이 안 되기 때문에"), 0),
                        sq(9, "하이퍼파라미터 튜닝에서 중요한 태도는 무엇인가요?", List.of("검증 기준을 고정하고 실험을 기록한다", "매번 기준을 바꾼다", "결과를 저장하지 않는다", "테스트 데이터를 학습에 넣는다"), 0),
                        sq(10, "LLM 기능을 서비스에 붙일 때 고려해야 할 것은 무엇인가요?", List.of("비용, latency, hallucination, 안전한 fallback", "버튼 색상만", "DB 컬럼 순서만", "CSS reset만"), 0)
                ));

        seedCourse("game-server", "Game Server 실력 진단",
                "실시간 통신, 동시성, 세션, 매칭, 성능 병목을 기준으로 진단합니다.",
                List.of(
                        sq(1, "게임 서버에서 tick rate를 고려하는 이유는 무엇인가요?", List.of("게임 상태 갱신 주기와 네트워크 부하에 영향을 주기 때문", "이미지를 압축하기 위해", "DB 이름을 줄이기 위해", "HTML을 렌더링하기 위해"), 0),
                        sq(2, "실시간 게임에서 UDP를 고려하는 이유는 무엇인가요?", List.of("낮은 지연이 중요하고 일부 패킷 손실을 허용할 수 있기 때문", "항상 신뢰성이 필요하기 때문", "DB 트랜잭션이 필요 없기 때문", "UI가 단순하기 때문"), 0),
                        sq(3, "서버 권위(authoritative server)가 중요한 이유는 무엇인가요?", List.of("클라이언트 조작을 줄이고 일관된 게임 상태를 유지하기 위해", "그래픽 품질을 높이기 위해", "로그를 제거하기 위해", "프론트 라우팅을 줄이기 위해"), 0),
                        sq(4, "매치메이킹에서 고려할 수 있는 요소는 무엇인가요?", List.of("실력, 대기 시간, 지역, 파티 구성", "폰트, 색상, 이미지 크기", "HTML 태그", "DB 컬럼 길이만"), 0),
                        sq(5, "게임 세션 종료 시 정리해야 할 것은 무엇인가요?", List.of("연결, 방 상태, 타이머, 임시 리소스", "CSS 파일", "브라우저 쿠키만", "마케팅 문구"), 0),
                        sq(6, "동시성 버그가 게임 서버에서 위험한 이유는 무엇인가요?", List.of("아이템 중복, 점수 불일치, 세션 꼬임이 생길 수 있다", "텍스트가 작아진다", "이미지가 흐려진다", "배포가 자동 중단된다"), 0),
                        sq(7, "leaderboard 설계에서 고려해야 할 것은 무엇인가요?", List.of("정렬 성능, 동점 처리, 갱신 빈도, 부정 데이터 방지", "버튼 hover", "CSS variable", "앱 권한"), 0),
                        sq(8, "게임 서버 로그에서 중요한 정보는 무엇인가요?", List.of("세션, 유저 행동, 에러, latency, 매치 ID", "폰트 크기", "이미지 ratio", "색상 팔레트"), 0),
                        sq(9, "서버 부하 테스트가 필요한 이유는 무엇인가요?", List.of("동접 증가 시 병목과 장애 지점을 미리 찾기 위해", "코드 줄 수를 늘리기 위해", "DB를 삭제하기 위해", "권한을 제거하기 위해"), 0),
                        sq(10, "실시간 게임에서 보상 지급은 어떻게 설계하는 것이 안전한가요?", List.of("멱등성과 서버 검증을 포함한다", "클라이언트 요청을 모두 신뢰한다", "로그를 남기지 않는다", "재시도 처리를 금지한다"), 0)
                ));

        seedCourse("kafka", "Kafka Deep Dive 실력 진단",
                "Kafka producer/consumer, partition, offset, consumer group, 장애 처리를 기준으로 진단합니다.",
                List.of(
                        sq(1, "Kafka에서 partition을 사용하는 이유는 무엇인가요?", List.of("병렬 처리와 확장성을 높이기 위해", "메시지를 암호화하기 위해", "토픽을 삭제하기 위해", "HTTP 요청을 만들기 위해"), 0),
                        sq(2, "consumer group의 역할은 무엇인가요?", List.of("같은 그룹 내 consumer들이 partition을 나눠 처리하게 한다", "모든 consumer가 모든 메시지를 항상 중복 처리한다", "토픽 생성을 금지한다", "브라우저 캐시를 지운다"), 0),
                        sq(3, "offset commit이 중요한 이유는 무엇인가요?", List.of("어디까지 처리했는지 기록해 재처리 범위를 결정하기 위해", "메시지 크기를 줄이기 위해", "토픽 이름을 바꾸기 위해", "네트워크를 끊기 위해"), 0),
                        sq(4, "at-least-once 처리에서 발생할 수 있는 것은 무엇인가요?", List.of("중복 처리가 생길 수 있어 멱등성이 필요하다", "절대 중복이 없다", "메시지가 항상 사라진다", "DB가 필요 없다"), 0),
                        sq(5, "key를 지정해 메시지를 보내는 이유는 무엇인가요?", List.of("같은 key의 메시지를 같은 partition으로 보내 순서를 유지하기 위해", "메시지를 숨기기 위해", "consumer를 삭제하기 위해", "offset을 0으로 만들기 위해"), 0),
                        sq(6, "consumer lag이 의미하는 것은 무엇인가요?", List.of("consumer가 아직 처리하지 못한 메시지 양", "producer가 없는 상태", "토픽이 삭제된 상태", "브로커 포트 번호"), 0),
                        sq(7, "DLQ를 사용하는 이유는 무엇인가요?", List.of("계속 실패하는 메시지를 분리해 분석하고 재처리하기 위해", "성공 메시지를 삭제하기 위해", "partition 수를 줄이기 위해", "consumer group을 숨기기 위해"), 0),
                        sq(8, "Kafka producer에서 acks 설정이 영향을 주는 것은 무엇인가요?", List.of("메시지 저장 확인 수준과 신뢰성/지연", "토픽 이름 길이", "consumer 언어", "브라우저 렌더링"), 0),
                        sq(9, "rebalancing이 자주 발생하면 생기는 문제는 무엇인가요?", List.of("처리 중단과 지연이 늘 수 있다", "메시지가 자동 압축된다", "DB가 빨라진다", "HTTP 캐시가 줄어든다"), 0),
                        sq(10, "Kafka 메시지 처리에서 멱등성이 필요한 이유는 무엇인가요?", List.of("재시도나 중복 소비에도 결과가 중복 반영되지 않게 하기 위해", "토픽을 자동 생성하기 위해", "파티션을 없애기 위해", "로그를 숨기기 위해"), 0)
                ));

        seedCourse("distributed-lock", "Distributed Lock Deep Dive 실력 진단",
                "분산 락 필요성, Redis 락, DB 락, TTL, 장애 상황 판단을 기준으로 진단합니다.",
                List.of(
                        sq(1, "분산 락이 필요한 대표 상황은 무엇인가요?", List.of("여러 서버가 같은 자원을 동시에 변경할 수 있을 때", "단일 스레드 로컬 변수만 바꿀 때", "정적 파일을 읽을 때", "CSS를 빌드할 때"), 0),
                        sq(2, "락에 TTL이 필요한 이유는 무엇인가요?", List.of("락을 잡은 프로세스가 죽어도 영원히 잠기지 않게 하기 위해", "락을 더 오래 잡기 위해", "DB를 삭제하기 위해", "네트워크를 막기 위해"), 0),
                        sq(3, "Redis SET NX PX 패턴의 목적은 무엇인가요?", List.of("키가 없을 때만 TTL과 함께 락을 획득하기 위해", "모든 키를 삭제하기 위해", "값을 정렬하기 위해", "메시지를 브로드캐스트하기 위해"), 0),
                        sq(4, "분산 락 해제 시 owner token을 확인해야 하는 이유는 무엇인가요?", List.of("다른 요청이 획득한 락을 잘못 해제하지 않기 위해", "TTL을 없애기 위해", "DB connection을 늘리기 위해", "로그를 숨기기 위해"), 0),
                        sq(5, "락 대기 시간이 길어질 때 고려할 것은 무엇인가요?", List.of("timeout, retry 정책, 사용자 응답 전략", "버튼 색상", "HTML 제목", "이미지 alt"), 0),
                        sq(6, "DB pessimistic lock의 단점으로 적절한 것은 무엇인가요?", List.of("동시성이 낮아지고 deadlock 가능성이 있다", "정합성을 항상 망친다", "트랜잭션이 필요 없다", "쿼리가 불가능하다"), 0),
                        sq(7, "optimistic lock이 적절한 상황은 무엇인가요?", List.of("충돌이 드물고 version 충돌 시 재시도할 수 있을 때", "항상 충돌이 매우 많을 때", "정합성이 필요 없을 때", "DB가 없을 때"), 0),
                        sq(8, "분산 락을 적용해도 함께 고려해야 하는 것은 무엇인가요?", List.of("멱등성, 트랜잭션 경계, 실패 보상 처리", "폰트 크기", "이미지 ratio", "브라우저 tab"), 0),
                        sq(9, "쿠폰 발급에서 락 범위를 너무 넓게 잡으면 생기는 문제는 무엇인가요?", List.of("처리량이 떨어지고 대기 시간이 늘어난다", "중복이 반드시 늘어난다", "코드가 컴파일되지 않는다", "UI가 사라진다"), 0),
                        sq(10, "락 없이도 해결 가능한 경우를 먼저 검토해야 하는 이유는 무엇인가요?", List.of("DB 제약조건, unique key, 원자적 update가 더 단순하고 안전할 수 있기 때문", "락이 항상 불가능하기 때문", "트랜잭션을 쓰면 안 되기 때문", "API가 필요 없기 때문"), 0)
                ));
    }

    private void seedCourse(String category, String title, String description, List<SeedQuestion> questions) {
        Test test = upsertDiagnosticTest(category, title, description);
        upsertQuestions(test, questions);
    }

    private Test upsertDiagnosticTest(String category, String title, String description) {
        Test test = testRepository.findByCategoryAndIsActiveTrue(category).stream()
                .filter(this::isDiagnosticShape)
                .findFirst()
                .orElseGet(() -> testRepository.save(Test.builder()
                        .title(title)
                        .description(description)
                        .category(category)
                        .difficulty(Difficulty.INTERMEDIATE)
                        .timeLimit(DIAGNOSTIC_TIME_LIMIT)
                        .passingScore(DIAGNOSTIC_PASSING_SCORE)
                        .questionCount(DIAGNOSTIC_QUESTION_COUNT)
                        .isActive(true)
                        .build()));

        test.updateDiagnostic(title, description, Difficulty.INTERMEDIATE,
                DIAGNOSTIC_TIME_LIMIT, DIAGNOSTIC_PASSING_SCORE, DIAGNOSTIC_QUESTION_COUNT);
        log.info("Upserted course skill diagnostic test: {}", title);
        return test;
    }

    private void upsertQuestions(Test test, List<SeedQuestion> seeds) {
        List<Question> existing = questionRepository.findByTestIdOrderByOrderIndexAsc(test.getId()).stream()
                .sorted(Comparator.comparing(Question::getOrderIndex))
                .toList();

        for (int index = 0; index < seeds.size(); index++) {
            SeedQuestion seed = seeds.get(index);
            if (index < existing.size()) {
                existing.get(index).updateQuestion(seed.content(), seed.options(), seed.correctAnswer(), 10, seed.order());
            } else {
                questionRepository.save(Question.builder()
                        .test(test)
                        .orderIndex(seed.order())
                        .content(seed.content())
                        .options(seed.options())
                        .correctAnswer(seed.correctAnswer())
                        .score(10)
                        .build());
            }
        }
    }

    private boolean isDiagnosticShape(Test test) {
        return test.getDifficulty() == Difficulty.INTERMEDIATE
                && DIAGNOSTIC_TIME_LIMIT == test.getTimeLimit()
                && DIAGNOSTIC_PASSING_SCORE == test.getPassingScore()
                && DIAGNOSTIC_QUESTION_COUNT == test.getQuestionCount();
    }

    private SeedQuestion sq(int order, String content, List<String> options, int correctAnswer) {
        return new SeedQuestion(order, content, options, correctAnswer);
    }

    private record SeedQuestion(int order, String content, List<String> options, int correctAnswer) {
    }
}

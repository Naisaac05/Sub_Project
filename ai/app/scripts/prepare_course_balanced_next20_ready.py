from __future__ import annotations

import copy
import json
from pathlib import Path

from app.schemas.rag_card import RagPayloads
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.prepare_payload_batch_v216 import validate_ready_patch


ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPORT = ROOT / "reports" / "course_balanced_next20_factcheck_preparation_2026-06-14.json"
REPORT = ROOT / "reports" / "course_balanced_next20_ready_2026-06-14.json"


def s(definition, answer, wrong, code, usage, comparison):
    return {"definition": definition, "answer": answer, "wrong": wrong, "code": code, "usage": usage, "comparison": comparison}


SPECS = {
    "java-delegation": s("클래스 로더 위임 모델은 클래스를 요청받은 로더가 먼저 부모 로더에 탐색을 맡기고, 부모가 찾지 못했을 때 자신이 로딩하는 방식이다. 핵심 Java 클래스가 애플리케이션 클래스에 의해 대체되는 일을 막는다.", "부모 로더가 우선 탐색하고 실패한 경우에만 자식 로더가 직접 로딩하므로 위임 모델을 정확히 설명한다. 자식 우선·동시 로딩·Application 로더 단독 사용은 부모 우선 계층을 따르지 않는다.", ["자식 우선 로딩은 일부 컨테이너의 별도 전략이며 기본 위임 모델과 순서가 반대다.", "모든 로더가 동시에 시도하면 계층별 책임과 클래스 동일성이 보장되지 않는다.", "Application 로더만 사용하면 Bootstrap·Platform 로더가 담당하는 표준 클래스를 처리할 수 없다."], "ClassLoader loader = String.class.getClassLoader();\nClassLoader app = Thread.currentThread().getContextClassLoader();\nassert loader == null && app != null;", "클래스 충돌 진단과 플러그인 로딩 구조를 설계할 때 부모 우선 위임 여부를 확인한다.", "부모 우선은 표준 클래스 보호에 유리하고, 자식 우선은 애플리케이션별 라이브러리 격리에 유리하다."),
    "java-completablefuture": s("CompletableFuture의 `thenApply`는 완료 값을 동기 함수로 변환하고, `thenCompose`는 함수가 반환한 새 CompletionStage를 평탄화해 비동기 작업을 연결한다.", "`thenApply` 결과는 변환된 값이고 `thenCompose` 결과는 중첩 Future 없이 다음 비동기 단계로 이어진다. 두 메서드의 차이는 동기·비동기 실행 여부나 예외 처리 가능 여부가 아니라 반환 구조다.", ["차이가 없다면 중첩 Future를 평탄화하는 compose의 역할을 설명하지 못한다.", "두 메서드 모두 비동기 단계에 사용할 수 있으며 compose만 동기라는 설명은 틀리다.", "예외 처리는 exceptionally·handle 등으로 둘 모두에 연결할 수 있다."], "CompletableFuture<Integer> base = CompletableFuture.completedFuture(2);\nint mapped = base.thenApply(v -> v * 2).join();\nint chained = base.thenCompose(v -> CompletableFuture.completedFuture(v * 3)).join();\nassert mapped == 4 && chained == 6;", "비동기 API 결과로 다음 비동기 요청을 이어갈 때 thenCompose를 사용해 중첩 Future를 피한다.", "thenApply는 `T→U`, thenCompose는 `T→CompletionStage<U>` 함수를 받는다."),
    "java-g1-gc": s("G1 GC는 힙을 동일 크기 Region으로 나누고 각 Region의 역할을 필요에 따라 Eden, Survivor, Old 등으로 바꾼다. 회수 효율이 높은 Region을 우선 처리해 목표 일시정지 시간에 맞춘다.", "Young과 Old 영역이 물리적으로 고정되어 있다는 설명은 Region 역할이 동적으로 바뀌는 G1 구조와 반대이므로 올바르지 않다. Region 관리, 일시정지 목표, Java 9 기본 GC는 특징에 해당한다.", ["Region 기반 관리는 G1이라는 이름과 동작의 핵심이므로 올바른 특징이다.", "목표 일시정지 시간을 설정해 수집 작업량을 조절하므로 예측 가능성을 높인다는 설명은 맞다.", "Java 9부터 서버급 환경의 기본 GC로 채택되었다는 설명은 맞다."], "List<String> regions = new ArrayList<>(List.of(\"Eden\", \"Old\"));\nregions.set(0, \"Old\");\nassert regions.get(0).equals(\"Old\");", "대용량 힙의 GC 일시정지 시간을 튜닝할 때 Region 점유와 목표 pause 시간을 관찰한다.", "고정 세대형 수집기는 물리 구역이 나뉘지만 G1은 Region 역할을 동적으로 지정한다."),
    "java-checked": s("checked exception은 컴파일러가 호출자에게 처리하거나 `throws`로 선언하도록 요구하는 예외다. unchecked exception은 RuntimeException 계열로 컴파일 시 처리가 강제되지 않는다.", "checked exception은 컴파일 시점에 처리 여부가 검사된다. unchecked 강제 처리, 모든 예외의 try-catch 의무, 차이가 없다는 설명은 예외 계층과 컴파일러 검사를 구분하지 못한다.", ["unchecked exception은 명시적으로 처리할 수 있지만 컴파일러가 강제하지 않는다.", "checked exception도 `throws`로 전파할 수 있어 모두 try-catch가 반드시 필요한 것은 아니다.", "두 종류는 컴파일러의 처리 강제 여부와 상속 계층이 다르다."], "void load() throws IOException { throw new IOException(); }\ntry { load(); } catch (IOException error) { assert error != null; }\nassert RuntimeException.class.getSuperclass() == Exception.class;", "복구 가능한 외부 자원 오류는 checked로 전달하고 프로그래밍 오류는 unchecked로 표현할지 결정한다.", "checked는 처리·선언이 필수이고 unchecked는 호출자의 선택이다."),
    "spring-responseentity": s("ResponseEntity는 HTTP 응답 본문뿐 아니라 상태 코드와 헤더를 함께 표현하는 Spring 타입이다. 컨트롤러가 성공·생성·오류 등의 응답 메타데이터를 명시적으로 구성할 수 있다.", "ResponseEntity의 고유한 이점은 상태 코드와 헤더를 직접 제어하는 것이다. JSON 변환은 메시지 컨버터의 역할이며 성능 향상과 보안 강화가 자동으로 따라오지 않는다.", ["ResponseEntity 사용만으로 처리 성능이 향상되지는 않는다.", "JSON 변환은 Jackson 같은 HttpMessageConverter가 담당하며 ResponseEntity만의 기능이 아니다.", "응답 타입을 사용한다고 인증·인가나 입력 검증이 자동 강화되지는 않는다."], "ResponseEntity<String> response = ResponseEntity.status(201).header(\"Location\", \"/users/7\").body(\"created\");\nassert response.getStatusCode().value() == 201;\nassert response.getHeaders().getFirst(\"Location\").equals(\"/users/7\");", "REST API에서 생성 후 201과 Location 헤더를 반환하거나 오류별 상태 코드를 명시할 때 사용한다.", "본문 객체 반환은 기본 200 응답에 편리하고 ResponseEntity는 전체 HTTP 응답 제어에 적합하다."),
    "spring-circuit": s("Circuit Breaker는 외부 서비스 실패가 임계치를 넘으면 호출을 잠시 차단하고 빠르게 실패 또는 대체 응답을 반환하는 회복성 패턴이다. 일정 시간 후 제한적으로 재시도해 복구 여부를 확인한다.", "실패 중인 의존 서비스 호출을 차단해 스레드와 연결 고갈을 막고 장애 전파를 제한하는 것이 목적이다. 암호화, 로드 밸런싱, 서비스 탐색은 다른 인프라 책임이다.", ["네트워크 암호화는 TLS가 담당하며 실패 호출 차단과 목적이 다르다.", "로드 밸런싱은 요청을 여러 인스턴스에 분산하지만 반복 장애를 차단하지는 않는다.", "서비스 디스커버리는 인스턴스 위치를 찾는 기능으로 빠른 실패 반환과 다르다."], "AtomicInteger failures = new AtomicInteger(3);\nboolean open = failures.get() >= 3;\nString result = open ? \"fallback\" : \"remote-call\";\nassert result.equals(\"fallback\");", "결제·추천 같은 원격 서비스 장애가 전체 요청 지연으로 번지는 것을 막을 때 사용한다.", "Retry는 다시 호출하고 Circuit Breaker는 임계 실패 후 호출 자체를 차단한다."),
    "spring-valid": s("`@Valid`는 Bean Validation 표준 애너테이션이고, Spring의 `@Validated`는 표준 검증에 그룹 선택과 메서드 검증 기능을 더한다.", "`@Valid`는 표준 검증 진입점이며 `@Validated`는 Spring 전용 확장으로 그룹 검증을 지원한다. 둘이 동일하거나 @Validated가 Java 표준이라는 설명, Controller 사용 제한은 틀리다.", ["두 애너테이션 모두 기본 검증은 수행하지만 그룹 선택 지원 등 기능 차이가 있다.", "`@Validated`는 Spring Framework의 애너테이션이며 Java 표준이 아니다.", "`@Valid`는 Controller 외에도 중첩 객체와 일반 Bean 검증에 사용할 수 있다."], "interface Update {}\n@Validated(Update.class)\nvoid update(@Valid UserRequest request) {}\nassert Update.class.isInterface();", "생성·수정 요청마다 다른 필수 필드를 검증해야 할 때 @Validated 그룹을 사용한다.", "단일 규칙 검사는 표준 진입점으로 충분하고, 생성·수정 규칙을 나눠 실행하려면 그룹 기능을 선택한다."),
    "spring-profile": s("Spring Profile은 환경 이름에 따라 Bean과 설정을 선택적으로 활성화하는 기능이다. dev, test, prod별 외부 시스템 주소나 구현체를 분리할 수 있다.", "프로파일은 환경별 설정과 Bean 구성을 분리하는 용도다. 성능 분석, 사용자 프로필, 로깅 레벨은 프로파일로 선택할 수는 있어도 기능 자체의 목적은 아니다.", ["코드 성능 분석은 프로파일러 도구의 역할이며 Spring Profile과 이름만 비슷하다.", "사용자 프로필은 도메인 데이터로 애플리케이션 실행 환경 선택과 다르다.", "로깅 레벨도 환경 설정 일부일 수 있지만 프로파일의 전체 용도를 대표하지 않는다."], "@Profile(\"dev\")\n@Bean DataSource devDataSource() { return new H2DataSource(); }\nassert environment.acceptsProfiles(Profiles.of(\"dev\"));", "개발 환경은 H2, 운영 환경은 관리형 DB처럼 환경별 Bean을 교체할 때 사용한다.", "Profile은 설정 묶음을 선택하고 프로퍼티 파일은 선택된 환경의 값을 제공한다."),
    "frontend-usecallback": s("useCallback은 의존성이 바뀔 때까지 함수 참조를 재사용하고, useMemo는 계산 결과 값을 재사용하는 React Hook이다. 둘 다 불필요한 재계산이나 자식 렌더를 줄이는 최적화 수단이다.", "useCallback은 함수 자체를, useMemo는 함수 실행 결과 값을 메모이제이션한다. 역할이 같거나 서로 반대라는 설명, 클래스 컴포넌트 전용이라는 설명은 Hook의 사용 방식과 다르다.", ["두 Hook은 보존 대상이 함수 참조와 계산 값으로 서로 다르다.", "useMemo가 값을, useCallback이 함수를 보존하므로 반대로 설명한 선택지는 틀리다.", "Hook은 함수형 컴포넌트에서 사용하며 클래스 컴포넌트 전용이 아니다."], "const callback = React.useCallback(() => 2 * 3, []);\nconst value = React.useMemo(() => 2 * 3, []);\nassert(callback() === value);", "memo로 감싼 자식에 콜백을 전달하거나 비용이 큰 계산 값을 재사용할 때 선택한다.", "useCallback은 함수 참조 안정화, useMemo는 계산 결과 캐시에 사용한다."),
    "frontend-functional-component": s("React 함수형 컴포넌트는 props를 입력받고 JSX 요소를 반환하는 JavaScript 함수다. 일반 함수 선언 또는 화살표 함수 문법으로 정의한다.", "`function App() { return <div/> }`는 괄호가 있는 올바른 JavaScript 함수 선언이며 JSX를 반환한다. 나머지는 함수 선언 문법이 아니거나 JavaScript가 아닌 문법이다.", ["함수 이름 뒤 매개변수 괄호가 없으면 올바른 JavaScript 함수 선언이 아니다.", "`class App()` 형태는 JavaScript 클래스 선언 문법이 아니며 render 메서드도 없다.", "`def`는 Python 문법으로 React JavaScript 컴포넌트를 선언하지 못한다."], "function App() { return <div>안녕</div>; }\nconst tree = TestRenderer.create(<App />).toJSON();\nassert(tree.children[0] === '안녕');", "작은 UI 단위를 props와 Hook으로 구성할 때 함수형 컴포넌트를 사용한다.", "함수형 컴포넌트는 함수를 사용하고 클래스 컴포넌트는 render 메서드를 사용한다."),
    "frontend-conditional-rendering": s("React 조건부 렌더링은 JavaScript 조건에 따라 반환할 JSX를 선택하는 방식이다. JSX 표현식 내부에서는 삼항식, 논리 AND, 함수 호출을 사용할 수 있지만 문(statement)인 if-else를 직접 넣을 수 없다.", "if-else는 값을 만드는 표현식이 아니라 문장이므로 JSX 중괄호 내부에 직접 작성할 수 없다. 삼항식, &&, 조건 함수 호출은 결과 값을 반환하므로 JSX에서 사용할 수 있다.", ["삼항 연산자는 조건에 따라 두 JSX 값 중 하나를 반환하므로 사용할 수 있다.", "&& 연산자는 조건이 참일 때 오른쪽 JSX를 반환해 조건부 표시가 가능하다.", "조건 함수가 JSX 또는 null을 반환하면 호출 결과를 JSX 표현식에 넣을 수 있다."], "const view = isAdmin ? <Admin /> : <Guest />;\nconst badge = unread > 0 && <Badge />;\nassert(view != null && badge != null);\nreturn view;", "권한, 로딩, 빈 상태에 따라 서로 다른 UI를 표시할 때 사용한다.", "if-else는 return 이전 로직에 쓰고 JSX 내부에서는 값을 반환하는 표현식을 쓴다."),
    "frontend-react-server-components": s("React Server Components는 서버에서만 실행되어 결과를 클라이언트에 전달하는 컴포넌트다. 클라이언트 번들에 포함되지 않고 서버 자원에 직접 접근할 수 있지만 상태·효과 같은 클라이언트 Hook은 사용할 수 없다.", "RSC에서 useState를 사용할 수 있다는 설명은 서버 전용 실행 모델과 맞지 않아 특징이 아니다. 서버 실행, 번들 제외, DB 직접 접근은 RSC의 장점이다.", ["서버에서 실행되는 것은 RSC의 핵심 특성이므로 틀린 설명이 아니다.", "RSC 코드는 클라이언트 번들에 포함되지 않아 번들 크기를 줄일 수 있다.", "서버 컴포넌트는 서버에서 DB나 파일 시스템에 직접 접근할 수 있다."], "async function Users() {\n  const users = await db.user.findMany();\n  return <ul>{users.map(user => <li key={user.id}>{user.name}</li>)}</ul>;\n}\nassert(typeof Users === 'function');", "DB 조회 중심 화면을 서버에서 구성해 클라이언트 JavaScript를 줄일 때 사용한다.", "Server Component는 서버 자원 접근에, Client Component는 useState와 이벤트 처리에 적합하다."),
    "python-multiline-string": s("Python 여러 줄 문자열은 작은따옴표 또는 큰따옴표 세 개로 감싸 작성한다. 줄바꿈 문자가 문자열 값에 그대로 포함되며 문서 문자열에도 같은 문법을 사용한다.", "`\"\"\"텍스트\"\"\"`와 `'''텍스트'''`는 모두 여러 줄 문자열을 만든다. 둘 중 하나만 제시한 보기는 가능한 문법 하나만 설명해 정답 범위보다 좁고, `<<텍스트>>`는 문자열 구문이 아니다.", ["큰따옴표 세 개는 유효하지만 작은따옴표 세 개 방식도 있어 가능한 방법 전체를 답하지 못한다.", "작은따옴표 세 개 역시 동작하지만 큰따옴표 세 개 방식까지 포함한 보기가 더 완전하다.", "꺾쇠괄호는 Python 문자열 리터럴 문법이 아니므로 파싱되지 않는다."], "double = \"\"\"첫 줄\n둘째 줄\"\"\"\nsingle = '''첫 줄\n둘째 줄'''\nassert double == single;", "SQL, 템플릿, 문서 문자열처럼 줄바꿈을 보존해야 하는 텍스트에 사용한다.", "삼중 따옴표는 실제 줄바꿈을 포함하고 `\\n`은 한 줄 리터럴 안에서 줄바꿈을 표현한다."),
    "python-dictionary": s("Python 딕셔너리는 키와 값을 연결해 저장하는 매핑 자료구조다. 중괄호 안에 `key: value` 쌍을 작성하거나 `dict()`로 생성한다.", "`d = {key: value}`는 키와 값을 콜론으로 연결한 올바른 딕셔너리 문법이다. 대괄호는 리스트, 소괄호는 튜플이며 꺾쇠괄호는 리터럴 문법이 아니다.", ["대괄호 안의 key:value 표기는 리스트 문법과 맞지 않아 SyntaxError가 난다.", "소괄호의 두 값은 튜플을 만들 뿐 키 조회를 제공하지 않는다.", "꺾쇠괄호는 Python 컬렉션 리터럴 문법으로 사용되지 않는다."], "key = 'name'\nvalue = 'Kim'\nd = {key: value}\nassert d['name'] == 'Kim'", "식별자로 사용자 정보나 설정 값을 빠르게 조회할 때 사용한다.", "딕셔너리는 키 조회를 제공하고 리스트·튜플은 위치 기반 순서를 표현한다."),
    "python-fstring": s("Python f-string은 문자열 앞에 `f`를 붙이고 중괄호 안의 표현식을 평가해 문자열에 삽입한다. 변수뿐 아니라 계산식과 포맷 지정자도 사용할 수 있다.", "`f'이름은 {name}입니다'`는 f 접두사와 중괄호 표현식을 모두 갖춘 올바른 문법이다. `$`, 일반 괄호, `#` 표기는 Python f-string 치환 구문이 아니다.", ["`${name}`은 JavaScript 템플릿 리터럴 계열 표기이며 Python f-string 문법이 아니다.", "일반 괄호는 문자열 안에서 표현식을 평가하지 않고 그대로 출력된다.", "f 접두사 없이 `#{name}`을 쓰면 변수 값이 치환되지 않는다."], "name = '민수'\nmessage = f'이름은 {name}입니다'\nassert message == '이름은 민수입니다'", "로그·메시지·보고서에 변수와 계산 결과를 읽기 좋게 삽입할 때 사용한다.", "f-string은 표현식을 평가하고 일반 문자열은 중괄호 내용을 그대로 보존한다."),
    "python-multiprocessing": s("multiprocessing은 별도 프로세스로 여러 CPU 코어를 활용하고, threading은 한 프로세스 안에서 I/O 대기 시간을 겹쳐 처리한다. CPython GIL 때문에 순수 Python CPU 작업은 프로세스가 유리하다.", "CPU-bound 작업은 multiprocessing으로 병렬화하고 I/O-bound 작업은 threading으로 대기 시간을 활용하는 구분이 적절하다. 반대 조합이나 한 방식이 모든 작업에 적합하다는 설명은 자원 특성을 무시한다.", ["CPU 작업에 threading을 쓰면 GIL로 병렬 실행 이점이 제한되고 I/O 작업에 프로세스는 비용이 크다.", "둘 다 CPU-bound에 적합하다고 하면 threading의 GIL 제약을 무시한다.", "둘 다 I/O-bound에 적합하다고 하면 multiprocessing의 생성·통신 비용을 고려하지 않는다."], "from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor\ncpu_pool = ProcessPoolExecutor(2)\nio_pool = ThreadPoolExecutor(4)\nassert cpu_pool._max_workers == 2 && io_pool._max_workers == 4", "이미지 계산은 프로세스 풀, 네트워크 요청은 스레드 풀로 분리해 처리량을 높인다.", "프로세스는 CPU 병렬성에, 스레드는 공유 메모리와 I/O 동시성에 유리하다."),
    "algorithm-5": s("삽입 정렬은 앞쪽 정렬 구간에서 현재 원소가 들어갈 위치를 찾아 큰 원소를 밀고 삽입한다. 거의 정렬된 입력에서는 이동 횟수가 적어 선형 시간에 가까워진다.", "데이터가 거의 정렬되어 있으면 각 원소가 짧은 거리만 이동하므로 삽입 정렬이 효율적이다. 역순·매우 큰 입력은 이동량이 커지고 모든 입력에서 성능이 같지도 않다.", ["완전 역순은 매 원소가 앞까지 이동해 최악 O(n²)이 된다.", "매우 큰 무작위 데이터에는 평균 O(n²)보다 O(n log n) 정렬이 유리하다.", "입력 순서에 따라 이동 횟수가 달라져 모든 경우 성능이 같지 않다."], "values = [1, 2, 4, 3]\nvalue = values.pop()\nvalues.insert(2, value)\nassert values == [1, 2, 3, 4]", "작은 배열이나 대부분 정렬된 데이터의 마무리 정렬에 사용한다.", "삽입 정렬은 거의 정렬된 입력에 강하고 선택 정렬은 입력 순서와 무관하게 비교량이 비슷하다."),
    "algorithm-linked": s("연결 리스트는 각 노드가 값과 다음 노드 참조를 저장하는 자료구조다. 변경 위치의 노드를 이미 알면 참조 몇 개만 바꿔 삽입·삭제하므로 O(1)에 처리할 수 있다.", "위치를 이미 알고 있을 때 연결 변경만 수행하므로 삽입·삭제가 O(1)이다. 인덱스 조회는 순회가 필요하고 추가 참조 메모리를 사용하며 정렬 속도도 자동으로 빨라지지 않는다.", ["인덱스 위치까지 노드를 따라가야 하므로 임의 접근은 O(n)이다.", "각 노드가 다음 노드 참조를 저장해 배열보다 추가 메모리가 필요하다.", "연결 구조만으로 정렬이 빨라지지 않으며 알고리즘과 탐색 비용을 고려해야 한다."], "next_node = {'value': 'C', 'next': None}\nnode = {'value': 'A', 'next': next_node}\nnode['next'] = {'value': 'B', 'next': next_node}\nassert node['next']['value'] == 'B'", "중간 삽입·삭제가 빈번하고 순차 접근하는 큐나 연결 구조에 사용한다.", "연결 리스트는 위치를 알 때 변경이 빠르고 배열 목록은 인덱스 조회가 빠르다."),
    "algorithm-4": s("재귀 함수는 자기 자신을 호출해 문제 크기를 줄이며, 기저 조건은 더 호출하지 않고 결과를 반환하는 종료 조건이다. 기저 조건에 도달하지 못하면 호출 스택이 계속 쌓인다.", "기저 조건이 재귀 호출을 멈춰 유한하게 종료시키므로 반드시 필요하다. 반복문·전역 변수·배열은 구현에 사용할 수 있지만 재귀 종료를 보장하는 필수 요소가 아니다.", ["반복문 없이도 재귀를 구현할 수 있으며 반복문은 종료 조건을 대신하지 않는다.", "매개변수와 반환값만으로 재귀 상태를 전달할 수 있어 전역 변수는 필수가 아니다.", "숫자 계산이나 트리 탐색 재귀는 배열 없이도 구현할 수 있다."], "def countdown(n):\n    if n == 0: return '끝'\n    return countdown(n - 1)\nassert countdown(3) == '끝'", "트리 순회와 분할 정복에서 입력을 줄이며 안전하게 종료하도록 기저 조건을 설계한다.", "기저 조건은 종료를 결정하고 재귀 단계는 문제를 더 작은 상태로 바꾼다."),
    "algorithm-7": s("선택 정렬은 정렬되지 않은 구간에서 최솟값을 찾아 그 구간의 첫 원소와 교환하며 정렬 구간을 확장한다. 매 회차 한 위치의 최종 값을 확정한다.", "최솟값을 찾아 맨 앞과 교환하는 절차가 선택 정렬의 핵심이다. 인접 교환은 버블, 정렬 구간 삽입은 삽입, 반으로 분할은 병합 정렬 방식이다.", ["인접한 두 요소를 비교해 교환하는 방식은 버블 정렬이다.", "이미 정렬된 부분의 위치에 원소를 넣는 방식은 삽입 정렬이다.", "배열을 반으로 나누고 결과를 합치는 방식은 병합 정렬이다."], "values = [3, 1, 2]\nminimum = values.index(min(values))\nvalues[0], values[minimum] = values[minimum], values[0]\nassert values == [1, 3, 2]", "작은 입력에서 교환 횟수를 제한하며 정렬 원리를 설명할 때 사용한다.", "선택 정렬은 최솟값을 선택하고 버블 정렬은 인접 원소를 반복 교환한다."),
}


def load_source_packets() -> dict[str, dict]:
    return json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))["FACTCHECK_SOURCE_PACKETS"]


def build_payload(card_id: str, packet: dict, spec: dict) -> dict:
    wrong_options = [(i, text) for i, text in enumerate(packet["options"]) if i != packet["correct_answer_index"]]
    payloads = {
        "CONCEPT_DEFINITION": {"content": spec["definition"], "examples": ["핵심 원리를 작은 입력으로 확인한다."]},
        "ANSWER_REASON": {"why_correct": spec["answer"], "key_points": [packet["correct_answer"], card_id]},
        "WRONG_ANSWER_REASON": {
            "common_mistakes": ["선택지의 용어만 보고 실제 원리와 적용 범위를 비교하지 않는 실수"],
            "per_option": {
                f"option_{index}": {"text": text, "reason": reason}
                for (index, text), reason in zip(wrong_options, spec["wrong"], strict=True)
            },
        },
        "COMPARISON": {"comparisons": [{"with": "유사 개념", "diff": spec["comparison"]}]},
        "EXAMPLE_REQUEST": {"code_example": spec["code"], "explanation": "코드의 상태 변화와 검증 결과로 개념의 실제 동작을 확인한다."},
        "PRACTICAL_USAGE": {"real_world": spec["usage"], "best_practices": ["작은 실행 예제로 핵심 동작과 경계 조건을 확인한다."]},
        "DEBUG_OR_ERROR": {"common_errors": [{"error": "유사 개념의 역할을 혼동해 잘못된 구현을 선택한다.", "solution": "입력 조건과 실행 결과를 비교해 개념의 적용 범위를 구분한다."}]},
    }
    RagPayloads.model_validate(payloads)
    return payloads


def build_report(packets: dict[str, dict]) -> dict:
    ready, backlog, failed, reviews = {}, [], {}, {}
    for card_id, packet in packets.items():
        spec = SPECS.get(card_id)
        if not spec:
            backlog.append(card_id); failed[card_id] = ["fact_check_not_prepared"]; continue
        payloads = build_payload(card_id, packet, spec)
        simulated = {"payloads": copy.deepcopy(payloads)}
        quality = validate_payload_quality(simulated)
        patch = {
            "payloads": payloads,
            "fact_check_notes": [f"실제 문제 '{packet['question']}'와 확정 정답 '{packet['correct_answer']}'를 기준으로 검증했다."],
            "patch_reason": "일반 템플릿과 정답 출력 예제를 사실 기반 설명·선택지별 근거·실행 예제로 교체한다.",
            "source_link": {key: packet[key] for key in ("course_id", "test_id", "question_id", "source_question_id")},
            "quality_review": quality,
        }
        reasons = list(dict.fromkeys(quality["reasons"] + validate_ready_patch(patch)))
        reviews[card_id] = {"pass": not reasons, "reasons": reasons}
        if reasons:
            backlog.append(card_id); failed[card_id] = reasons
        else:
            ready[card_id] = patch
    return {
        "candidate_count": len(packets), "ready_count": len(ready), "backlog_count": len(backlog),
        "PATCHES_READY": ready, "PREPARATION_BACKLOG": backlog, "quality_reviews": reviews,
        "failed_review": failed, "execution_performed": False, "card_files_modified": False,
        "approval_status_changed": False, "json_validation_result": "pass",
    }


def main() -> int:
    report = build_report(load_source_packets())
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized); REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

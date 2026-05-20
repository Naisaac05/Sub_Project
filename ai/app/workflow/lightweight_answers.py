from dataclasses import dataclass

from app.rag.documents import load_concept_cards
from app.workflow.intent import FreeQuestionIntent, normalize_question


LIGHTWEIGHT_MODEL_NAME = "lightweight-template"


@dataclass(frozen=True)
class LightweightAnswer:
    answer: str
    route: str
    style: str


_ANSWERS = {
    "list-comprehension": (
        "리스트 컴프리헨션은 반복문과 조건문을 한 줄로 써서 새 리스트를 만드는 Python 문법입니다. "
        "예를 들어 `[x * 2 for x in nums]`처럼 쓰면 `nums`의 값을 하나씩 바꿔 새 리스트로 만들 수 있습니다."
    ),
    "rest-api": (
        "REST API는 URL과 HTTP 메서드로 자원을 조회하거나 변경하는 API 설계 방식입니다. "
        "예를 들어 `GET /users`는 사용자 목록 조회, `POST /users`는 사용자 생성처럼 의미를 나눕니다."
    ),
    "json": (
        "JSON은 데이터를 key-value 형태의 텍스트로 표현하는 형식입니다. "
        "웹 API에서 서버와 클라이언트가 객체나 배열 데이터를 주고받을 때 자주 사용합니다."
    ),
    "optional": (
        "Optional은 값이 있을 수도 있고 없을 수도 있음을 명시적으로 표현하는 Java 타입입니다. "
        "null을 바로 다루다 생기는 실수를 줄이고, 값이 없을 때의 처리를 코드에서 드러내기 위해 씁니다."
    ),
    "stream-map-filter": (
        "Stream은 컬렉션 데이터를 흐름처럼 처리하는 Java API이고, `map`은 값을 변환하며 `filter`는 조건에 맞는 값만 남깁니다. "
        "예를 들어 이름 목록에서 길이가 긴 이름만 고른 뒤 대문자로 바꾸는 식의 처리를 간결하게 쓸 수 있습니다."
    ),
    "orm": (
        "ORM은 객체와 데이터베이스 테이블을 매핑해서 SQL을 직접 많이 쓰지 않고도 데이터를 다루게 해주는 방식입니다. "
        "JPA나 Hibernate가 대표적이고, 편리하지만 실제 쿼리와 성능 문제를 함께 이해해야 합니다."
    ),
    "jpa-entity": (
        "JPA 엔티티는 데이터베이스 테이블과 매핑되는 Java 객체입니다. "
        "비즈니스 상태를 표현하고 JPA가 변경을 추적하지만, API 응답에는 보통 DTO로 변환해서 내보냅니다."
    ),
    "promise": (
        "Promise는 JavaScript에서 비동기 작업의 결과를 나중에 받기 위한 객체입니다. "
        "성공하면 `then`, 실패하면 `catch`로 이어서 처리할 수 있고, 보통 `async/await`과 함께 사용합니다."
    ),
    "async-await": (
        "async/await은 비동기 코드를 동기 코드처럼 읽기 쉽게 쓰게 해주는 문법입니다. "
        "`await`은 Promise가 끝날 때까지 기다린 뒤 결과를 받아 다음 줄을 실행하게 합니다."
    ),
    "api": (
        "API는 클라이언트와 서버가 정해진 방식으로 데이터를 주고받기 위한 약속입니다. "
        "예를 들어 화면이 사용자 정보를 요청하면 서버는 API를 통해 JSON 같은 형태로 응답합니다."
    ),
    "aria-label": (
        "aria-label은 화면에는 보이지 않지만 스크린리더가 읽을 수 있는 이름을 제공하는 HTML 접근성 속성입니다. "
        "아이콘 버튼처럼 텍스트가 없는 요소에 붙이면 보조기술 사용자가 버튼의 목적을 이해할 수 있습니다."
    ),
    "pagination": (
        "페이지네이션은 데이터를 여러 페이지로 나누어 일정 개수씩 보여주는 방식입니다. "
        "전체 개수와 현재 위치를 파악하기 쉽고, 서버와 화면이 한 번에 처리하는 데이터 양을 줄일 수 있습니다."
    ),
    "infinite-scroll": (
        "무한스크롤은 사용자가 아래로 내릴 때 다음 데이터를 이어서 불러오는 방식입니다. "
        "탐색은 자연스럽지만 특정 위치로 돌아가거나 전체 개수를 파악하기는 페이지네이션보다 어려울 수 있습니다."
    ),
    "distributed-system": (
        "분산환경은 하나의 서버가 아니라 여러 서버나 시스템이 네트워크로 연결되어 함께 작업하는 구조입니다. "
        "트래픽을 나누고 장애에 더 잘 버티기 위해 쓰지만, 데이터 일관성이나 통신 실패를 함께 고려해야 합니다."
    ),
    "dto": (
        "DTO는 계층 사이에서 필요한 데이터만 담아 전달하는 객체입니다. "
        "엔티티를 API 응답으로 그대로 노출하지 않고 화면에 필요한 필드만 보내기 위해 자주 사용합니다."
    ),
}

_ANSWERS.update(
    {
        "transactional": (
            "@Transactional은 Spring에서 한 업무 단위를 하나의 transaction으로 묶는 선언입니다. "
            "메서드 실행 중 여러 DB 작업이 모두 성공하면 commit되고, 중간에 예외가 나면 rollback되어 데이터가 어중간하게 저장되는 일을 막습니다. "
            "보통 Controller나 Repository보다 비즈니스 흐름을 조합하는 Service 계층에 붙이는 것이 판단 기준입니다."
        ),
        "layered-architecture": (
            "계층 구조는 역할을 나눠서 코드를 유지보수하기 쉽게 만드는 방식입니다. "
            "Controller는 요청/응답, Service는 비즈니스 규칙과 transaction boundary, Repository는 DB 접근, Entity는 저장되는 도메인 상태를 맡습니다. "
            "문제에서 계층을 묻는다면 '어느 코드가 어떤 책임을 가져야 하는가'를 기준으로 보면 됩니다."
        ),
        "n-plus-one": (
            "N+1 문제는 목록을 한 번 조회한 뒤 각 항목의 연관 데이터를 지연 로딩하면서 추가 쿼리가 반복되는 성능 문제입니다. "
            "예를 들어 게시글 10개를 조회한 뒤 작성자를 하나씩 다시 조회하면 1번의 목록 쿼리 + N번의 추가 쿼리가 생깁니다. "
            "보통 fetch join, EntityGraph, batch size 같은 방식으로 필요한 연관 데이터를 한 번에 가져와 줄입니다."
        ),
        "fetch-join": (
            "fetch join은 JPQL에서 연관 엔티티를 한 번의 쿼리로 함께 로딩하도록 지시하는 방법입니다. "
            "N+1 문제처럼 지연 로딩 때문에 추가 쿼리가 반복될 때 자주 사용합니다. "
            "다만 컬렉션 fetch join은 페이징과 중복 row 문제를 함께 고려해야 합니다."
        ),
        "environment-variable": (
            "환경변수는 실행 환경마다 달라지는 설정값을 코드 밖에서 주입하는 방법입니다. "
            "DB 비밀번호, 외부 API 키, 서비스 토큰처럼 코드에 직접 넣으면 위험한 값을 관리할 때 사용합니다. "
            "로컬, 테스트, 운영 환경에서 같은 코드로 다른 설정을 쓰게 해주는 것이 핵심입니다."
        ),
        "cache": (
            "캐시는 자주 쓰는 데이터나 계산 결과를 잠시 저장해 두고 재사용해서 응답 속도를 높이는 방식입니다. "
            "DB나 외부 API를 매번 호출하지 않아도 되므로 latency와 부하를 줄일 수 있습니다. "
            "대신 오래된 데이터가 남지 않도록 만료 시간, 무효화, 일관성 정책을 함께 설계해야 합니다."
        ),
        "http-200-ok": (
            "200 OK는 요청이 정상적으로 처리되었고, 보통 응답 본문에 조회 결과나 처리 결과를 담아 돌려줄 때 쓰는 HTTP 성공 상태 코드입니다. "
            "예를 들어 `GET /users/1`처럼 리소스를 조회해 JSON 데이터를 돌려주는 경우에 자주 사용합니다."
        ),
        "http-201-created": (
            "201 Created는 요청이 성공했고 그 결과로 서버에 새 리소스가 생성되었음을 뜻하는 HTTP 상태 코드입니다. "
            "REST API에서는 보통 `POST /users`처럼 새 데이터를 만들었을 때 사용하며, 생성된 리소스의 위치를 `Location` 헤더로 함께 알려줄 수 있습니다. "
            "그래서 리소스를 새로 생성했다는 문제에서는 204 No Content보다 201 Created가 더 적절합니다."
        ),
        "http-204-no-content": (
            "204 No Content는 요청이 성공했지만 응답 본문을 보내지 않는다는 뜻의 HTTP 상태 코드입니다. "
            "주로 삭제 요청(`DELETE`)이나 본문을 돌려줄 필요가 없는 수정 요청에서 사용합니다. "
            "새 리소스를 만들었다는 의미는 아니므로, 생성 성공을 표현해야 하면 보통 201 Created가 더 맞습니다."
        ),
        "http-400-bad-request": (
            "400 Bad Request는 클라이언트가 보낸 요청 형식이나 값이 잘못되어 서버가 처리할 수 없다는 뜻의 HTTP 상태 코드입니다. "
            "예를 들어 필수 값 누락, 잘못된 JSON, 검증 실패 같은 입력 오류에 사용합니다."
        ),
        "http-401-unauthorized": (
            "401 Unauthorized는 인증이 필요하거나 인증 정보가 유효하지 않다는 뜻의 HTTP 상태 코드입니다. "
            "로그인 토큰이 없거나 만료된 경우처럼 누구인지 확인되지 않은 상태에 사용합니다."
        ),
        "http-403-forbidden": (
            "403 Forbidden은 인증은 되었지만 해당 작업을 할 권한이 없다는 뜻의 HTTP 상태 코드입니다. "
            "예를 들어 일반 사용자가 관리자 API를 호출하는 경우처럼 누구인지는 알지만 허용되지 않는 요청에 사용합니다."
        ),
        "http-404-not-found": (
            "404 Not Found는 요청한 리소스를 서버에서 찾을 수 없다는 뜻의 HTTP 상태 코드입니다. "
            "존재하지 않는 게시글 ID를 조회하거나 잘못된 URL로 요청했을 때 사용합니다."
        ),
        "http-500-internal-server-error": (
            "500 Internal Server Error는 서버 내부 오류로 요청을 정상 처리하지 못했다는 뜻의 HTTP 상태 코드입니다. "
            "클라이언트 입력 문제가 아니라 예외, 장애, 설정 오류처럼 서버 쪽 문제가 있을 때 사용합니다."
        ),
    }
)

_ANSWERS.update(
    {
        "recyclerview": (
            "RecyclerView는 Android에서 목록 형태의 데이터를 효율적으로 표시하는 UI 위젯입니다. "
            "예를 들어 게시글 목록이나 채팅 목록처럼 아이템이 많은 화면에서 ViewHolder로 아이템 뷰를 재사용해 성능을 높입니다. "
            "ListView보다 유연해서 레이아웃, 애니메이션, 아이템 갱신을 더 세밀하게 다룰 수 있습니다."
        ),
        "android": (
            "Android는 Google이 주도하는 모바일 운영체제이자 앱 실행 플랫폼입니다. "
            "Android 앱은 보통 Kotlin이나 Java로 만들고, Activity, Fragment, View 같은 구성요소로 화면과 동작을 구성합니다. "
            "스마트폰, 태블릿, TV, 웨어러블 같은 여러 기기에서 실행될 수 있습니다."
        ),
        "flutter": (
            "Flutter 앱은 Google의 Flutter 프레임워크로 만든 크로스 플랫폼 앱입니다. "
            "하나의 Dart 코드베이스로 Android와 iOS 같은 여러 플랫폼의 화면을 만들 수 있고, UI는 Widget 조합으로 구성합니다. "
            "빠른 개발과 일관된 화면 표현이 장점입니다."
        ),
        "dao": (
            "DAO는 Data Access Object의 줄임말로, 데이터베이스 접근 로직을 분리해 담는 객체입니다. "
            "Service가 비즈니스 규칙을 처리한다면 DAO는 조회, 저장, 수정, 삭제 같은 DB 작업을 맡습니다. "
            "DB 접근 코드를 한곳에 모아두면 테스트와 유지보수가 쉬워집니다."
        ),
    }
)


_ALIASES = {
    "list-comprehension": (
        "리스트컴프리헨션",
        "리스트 컴프리헨션",
        "listcomprehension",
        "list comprehension",
        "리스트축약",
        "리스트 축약",
        "파이썬for문한줄",
        "파이썬 for문 한줄",
    ),
    "rest-api": ("restapi", "rest api", "rest"),
    "json": ("json",),
    "optional": ("optional", "옵셔널"),
    "stream-map-filter": ("streammapfilter", "stream map filter", "mapfilter", "map filter", "스트림", "맵필터"),
    "orm": ("orm",),
    "jpa-entity": ("jpa엔티티", "jpa 엔티티", "엔티티", "entity"),
    "promise": ("promise", "프라미스", "프로미스"),
    "async-await": ("asyncawait", "async await", "비동기await", "어싱크", "에이싱크"),
    "api": ("api",),
    "aria-label": ("aria-label", "arialabel"),
    "pagination": ("페이지네이션", "pagination"),
    "infinite-scroll": ("무한스크롤", "infinite scroll", "infinitescroll"),
    "distributed-system": ("분산환경", "분산시스템", "distributed"),
    "dto": ("dto",),
    "transactional": ("@transactional", "transactional", "transaction", "트랜잭션"),
    "layered-architecture": ("계층", "layeredarchitecture", "layered architecture"),
    "n-plus-one": ("n+1", "n plus one", "nplusone"),
    "fetch-join": ("fetchjoin", "fetch join"),
    "environment-variable": ("환경변수", "environmentvariable", "environment variable", "env"),
    "cache": ("캐시", "캐싱", "cache", "caching"),
    "http-200-ok": ("200", "200ok", "200 ok"),
    "http-201-created": ("201", "201created", "201 created", "created"),
    "http-204-no-content": ("204", "204nocontent", "204 no content", "no content"),
    "http-400-bad-request": ("400", "400badrequest", "400 bad request", "bad request"),
    "http-401-unauthorized": ("401", "401unauthorized", "401 unauthorized", "unauthorized"),
    "http-403-forbidden": ("403", "403forbidden", "403 forbidden", "forbidden"),
    "http-404-not-found": ("404", "404notfound", "404 not found", "not found"),
    "http-500-internal-server-error": ("500", "500internalservererror", "500 internal server error", "internal server error"),
}

_ALIASES.update(
    {
        "recyclerview": ("recyclerview", "recycler view"),
        "android": ("android",),
        "flutter": ("flutter", "flutter app", "flutter앱"),
        "dao": ("dao", "data access object"),
    }
)


def lightweight_answer_for(
    question: str,
    intent: FreeQuestionIntent | None,
    matched_concept_id: str | None = None,
) -> str | None:
    resolved = resolve_lightweight_answer(question, intent, matched_concept_id)
    return resolved.answer if resolved else None


def resolve_lightweight_answer(
    question: str,
    intent: FreeQuestionIntent | None,
    matched_concept_id: str | None = None,
) -> LightweightAnswer | None:
    if not intent or intent.rag_policy != "latest_question_only":
        return None
    if intent.intent not in {"concept_definition", "comparison", "related_concept", "practical_application"}:
        return None

    haystack = normalize_question(" ".join((question, intent.topic)))
    for key, aliases in _ALIASES.items():
        if any(normalize_question(alias) in haystack for alias in aliases):
            return LightweightAnswer(_ANSWERS[key], "static_fast_path", _style_for_intent(intent))
    concept_answer = _concept_card_answer_for(matched_concept_id)
    if concept_answer:
        return LightweightAnswer(concept_answer, "generated_card_fast_path", _style_for_intent(intent))
    return None


def _style_for_intent(intent: FreeQuestionIntent) -> str:
    return {
        "comparison": "comparison",
        "practical_application": "practical",
        "related_concept": "related",
    }.get(intent.intent, "definition")


def _concept_card_answer_for(concept_id: str | None) -> str | None:
    if not concept_id:
        return None

    card = _concept_cards_by_id().get(concept_id)
    if not card:
        return None
    return card.sections.get("핵심 설명") or None


def _concept_cards_by_id():
    return {card.concept_id: card for card in load_concept_cards()}

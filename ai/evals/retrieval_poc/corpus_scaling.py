# -*- coding: utf-8 -*-
"""
코퍼스 규모/구성 실험 — "카드를 30장으로 늘리면 hybrid가 살아나는가?"

두 코퍼스를 spec에서 결정론적으로 생성해 대조한다:
  - DENSE   : 근접개념 덩어리(스프링 어노테이션/자바 컬렉션/JPA/HTTP/동시성). 의미가 비슷해 bge가 헷갈릴 여지 ↑ → hybrid 최선의 조건.
  - DIVERSE : 서로 무관한 30개 주제. 의미가 또렷해 bge가 잘 가름 → hybrid 값 적을 것.

각 개념마다 4유형 질의 생성: exact_token / mixed / semantic / typo.
리트리버: bm25(앱 tokenize + BM25) / bge(Ollama bge-m3 최근접) / hybrid(RRF).
지표: recall@1 / recall@3 / MRR (+ 질의 유형별 recall@1).

실행(임베딩 많아 느림 → 백그라운드 권장):
  PYTHONUTF8=1 python evals/retrieval_poc/corpus_scaling.py
"""
import atexit
import hashlib
import json
import math
import os
import pathlib
import sys
import urllib.request

AI_ROOT = pathlib.Path(__file__).resolve().parents[2]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))
from app.rag.retriever import tokenize  # noqa: E402  (앱과 동일한 토크나이저로 BM25 충실도 유지)

HERE = pathlib.Path(__file__).parent
REPORT = HERE / "REPORT_corpus_scaling.md"
KS = (1, 3)
QTYPES = ("exact_token", "mixed", "semantic", "typo")

# spec = (id, name, terms[list], desc, semantic_query, typo_query)
DENSE = [
    ("spring-transactional", "@Transactional", ["@Transactional", "트랜잭션 경계", "롤백", "선언적 트랜잭션"], "메서드에 트랜잭션 경계를 선언적으로 지정하는 스프링 어노테이션", "메서드 단위 작업을 묶어 실패하면 되돌리도록 표시하는 것", "트랜잭셔널 어노테이션 롤백 안돼요"),
    ("spring-service", "@Service", ["@Service", "비즈니스 로직", "서비스 빈", "스테레오타입"], "비즈니스 로직을 담는 빈임을 표시하는 스프링 스테레오타입 어노테이션", "핵심 업무 로직 담당 객체를 스프링 빈으로 등록하는 표시", "서비스 어노테이션 빈등록"),
    ("spring-component", "@Component", ["@Component", "빈 등록", "컴포넌트 스캔", "스테레오타입"], "일반 스프링 빈으로 등록되게 하는 기본 스테레오타입 어노테이션", "클래스를 스프링이 관리하는 객체로 자동 등록하게 하는 기본 표시", "컴포넌트 스캔 안잡혀요"),
    ("spring-autowired", "@Autowired", ["@Autowired", "의존성 주입", "자동 주입", "DI"], "필요한 의존 객체를 스프링이 자동 주입하도록 지정하는 어노테이션", "필요한 협력 객체를 컨테이너가 알아서 넣어주게 하는 표시", "오토와이어드 주입 실패"),
    ("spring-repository", "@Repository", ["@Repository", "데이터 접근", "예외 변환", "DAO"], "데이터 접근 계층 빈을 표시하고 예외를 변환하는 어노테이션", "DB 접근 객체를 빈으로 등록하고 예외를 표준화하는 표시", "리포지토리 예외변환"),
    ("spring-restcontroller", "@RestController", ["@RestController", "REST 컨트롤러", "@ResponseBody", "JSON 응답"], "응답 본문을 JSON으로 직렬화하는 REST 컨트롤러 빈을 표시하는 어노테이션", "HTTP 요청을 받아 JSON으로 응답하는 컨트롤러 표시", "레스트컨트롤러 제이슨 응답"),
    ("spring-configuration", "@Configuration", ["@Configuration", "설정 클래스", "@Bean 정의", "자바 설정"], "빈 정의를 담는 자바 설정 클래스를 표시하는 어노테이션", "빈 생성 규칙을 코드로 모아두는 설정 클래스 표시", "컨피규레이션 설정클래스"),
    ("spring-bean", "@Bean", ["@Bean", "메서드 빈 등록", "팩토리 메서드", "설정"], "설정 메서드의 반환 객체를 스프링 빈으로 등록하는 어노테이션", "메서드가 만든 객체를 컨테이너에 등록하게 하는 표시", "빈 어노테이션 메서드등록"),
    ("coll-arraylist", "ArrayList", ["ArrayList", "동적 배열", "임의 접근", "자동 확장"], "내부 배열에 저장해 임의 접근이 빠른 List 구현체", "인덱스로 빠르게 꺼내지만 중간 삽입이 느린 배열 기반 목록", "어레이리스트 중간삽입 느림"),
    ("coll-linkedlist", "LinkedList", ["LinkedList", "이중 연결 리스트", "노드 포인터", "삽입 삭제"], "노드를 포인터로 연결해 중간 삽입·삭제가 빠른 List 구현체", "앞뒤 노드를 가리키며 연결해 끼워넣기가 빠른 목록", "링크드리스트 임의접근"),
    ("coll-hashmap", "HashMap", ["HashMap", "키-값", "해시 버킷", "상수 시간 조회"], "키를 해시해 버킷에 저장하는 키-값 매핑 컬렉션", "키로 값을 평균 상수 시간에 찾는 해시 기반 사전", "해쉬맵 충돌 처리"),
    ("coll-hashset", "HashSet", ["HashSet", "중복 제거", "해시", "집합"], "중복을 허용하지 않고 해시로 원소를 저장하는 집합 컬렉션", "같은 값을 한 번만 담는 해시 기반 모음", "해쉬셋 중복제거"),
    ("coll-treemap", "TreeMap", ["TreeMap", "정렬된 키", "레드블랙 트리", "범위 검색"], "키를 정렬 상태로 유지하는 트리 기반 키-값 컬렉션", "키가 자동 정렬되어 범위 조회가 되는 사전", "트리맵 정렬순서"),
    ("coll-linkedhashmap", "LinkedHashMap", ["LinkedHashMap", "입력 순서 유지", "해시+연결", "LRU"], "입력 순서를 유지하는 해시 기반 키-값 컬렉션", "넣은 순서대로 순회되는 해시 사전", "링크드해쉬맵 순서유지"),
    ("coll-arraydeque", "ArrayDeque", ["ArrayDeque", "양방향 큐", "스택 큐", "배열 기반"], "양쪽 끝에서 넣고 빼는 배열 기반 덱 컬렉션", "앞뒤로 넣고 빼는 스택이자 큐로 쓰는 자료구조", "어레이덱 스택 큐"),
    ("jpa-nplus1", "N+1 문제", ["N+1", "지연 로딩", "추가 쿼리", "반복 쿼리"], "목록 조회 후 연관 데이터를 지연 로딩하며 쿼리가 반복되는 성능 문제", "리스트를 읽은 뒤 항목마다 쿼리가 또 나가 느려지는 현상", "엔플러스원 쿼리 반복"),
    ("jpa-fetchjoin", "fetch join", ["fetch join", "JPQL", "연관 엔티티", "즉시 로딩"], "JPQL에서 연관 엔티티를 한 쿼리로 함께 로딩하는 방식", "연관된 데이터까지 한 번의 쿼리로 같이 가져오는 방법", "패치조인 페이징"),
    ("jpa-entitygraph", "EntityGraph", ["@EntityGraph", "로딩 그래프", "attributePaths", "즉시 로딩"], "조회 시점의 연관 로딩 범위를 선언적으로 지정하는 JPA 기능", "어떤 연관을 함께 읽을지 조회마다 지정하는 방법", "엔티티그래프 즉시로딩"),
    ("jpa-persistence-context", "영속성 컨텍스트", ["영속성 컨텍스트", "1차 캐시", "엔티티 관리", "flush"], "엔티티를 관리하고 1차 캐시·변경 감지를 제공하는 JPA 작업 공간", "엔티티를 담아두고 변경을 추적하는 메모리 관리 공간", "영속성컨텍스트 1차캐시"),
    ("jpa-lazy", "지연 로딩", ["지연 로딩", "LAZY", "프록시", "필요 시 조회"], "연관 엔티티를 실제 사용할 때 쿼리로 가져오는 로딩 전략", "연관 데이터를 실제로 쓸 때 비로소 읽는 방식", "레이지 로딩 프록시"),
    ("jpa-dirtychecking", "변경 감지", ["변경 감지", "dirty checking", "스냅샷", "자동 update"], "영속 엔티티의 변경을 감지해 자동으로 update 쿼리를 내는 기능", "객체 값만 바꿔도 알아서 수정 쿼리가 나가는 동작", "더티체킹 업데이트"),
    ("http-get", "GET", ["GET", "조회", "멱등", "캐시 가능"], "자원을 조회하는 데 쓰는 멱등한 HTTP 메서드", "서버 자원을 읽어오기만 하는 안전한 요청 방식", "겟 메서드 멱등"),
    ("http-post", "POST", ["POST", "생성", "요청 본문", "비멱등"], "자원을 생성하거나 처리를 요청하는 HTTP 메서드", "새 데이터를 만들거나 작업을 일으키는 요청 방식", "포스트 생성 요청"),
    ("http-put", "PUT", ["PUT", "전체 교체", "멱등", "자원 갱신"], "자원을 통째로 교체하는 멱등한 HTTP 메서드", "대상을 통째로 새 값으로 바꾸는 요청 방식", "풋 전체교체"),
    ("http-delete", "DELETE", ["DELETE", "삭제", "멱등", "자원 제거"], "자원을 삭제하는 멱등한 HTTP 메서드", "대상 자원을 지우는 요청 방식", "딜리트 삭제 멱등"),
    ("http-patch", "PATCH", ["PATCH", "부분 수정", "비멱등", "일부 갱신"], "자원의 일부만 수정하는 HTTP 메서드", "대상의 일부 필드만 고치는 요청 방식", "패치 부분수정"),
    ("conc-synchronized", "synchronized", ["synchronized", "모니터 락", "상호 배제", "임계 영역"], "모니터 락으로 임계 영역을 한 스레드만 접근하게 하는 자바 키워드", "한 번에 한 스레드만 들어가게 잠그는 방식", "싱크로나이즈드 락"),
    ("conc-volatile", "volatile", ["volatile", "메모리 가시성", "캐시 일관성", "재정렬 방지"], "변수 변경을 모든 스레드에 즉시 보이게 하는 자바 키워드", "값 변경을 다른 스레드가 바로 보게 하는 표시", "볼라타일 가시성"),
    ("conc-reentrantlock", "ReentrantLock", ["ReentrantLock", "명시적 락", "tryLock", "공정성"], "락을 명시적으로 잠그고 푸는 유연한 동시성 제어 클래스", "직접 잠그고 풀며 조건을 거는 락 객체", "리엔트런트락 트라이락"),
    ("conc-atomic", "AtomicInteger", ["AtomicInteger", "CAS", "원자적 연산", "논블로킹"], "락 없이 CAS로 원자적 증감을 보장하는 동시성 클래스", "잠금 없이 안전하게 숫자를 더하는 방식", "어토믹인티저 캐스"),
]

DIVERSE = [
    ("tcp-handshake", "TCP 3-way handshake", ["SYN", "SYN-ACK", "ACK", "연결 수립"], "SYN/SYN-ACK/ACK 3단계로 TCP 연결을 수립하는 절차", "연결 전 양쪽이 신호를 주고받아 통신을 여는 과정", "티씨피 핸드셰이크 과정"),
    ("gc", "가비지 컬렉션", ["GC", "힙", "도달 불가", "Stop-the-world"], "참조되지 않는 객체 메모리를 자동 회수하는 기법", "안 쓰는 객체 메모리를 알아서 비워주는 동작", "가비지컬렉션 스탑더월드"),
    ("sql-index", "SQL 인덱스", ["인덱스", "B-tree", "조회 성능", "탐색"], "테이블 조회 속도를 높이려 칼럼을 정렬해 두는 자료구조", "검색을 빠르게 하려 미리 정렬해 둔 자료구조", "에스큐엘 인덱스 느려요"),
    ("oauth", "OAuth 2.0", ["OAuth", "액세스 토큰", "인가", "위임"], "제3자 앱에 비밀번호 없이 권한을 위임하는 인가 프로토콜", "비밀번호 안 주고 다른 앱에 접근 권한만 주는 방식", "오어스 토큰 인가"),
    ("docker", "Docker 컨테이너", ["컨테이너", "이미지", "격리", "경량 가상화"], "앱을 이미지로 패키징해 격리 실행하는 컨테이너 기술", "앱을 통째로 싸서 어디서나 똑같이 실행하는 방식", "도커 컨테이너 이미지"),
    ("bigo", "빅오 표기법", ["Big-O", "시간 복잡도", "점근", "증가율"], "입력 크기에 따른 알고리즘 비용 증가율 표기법", "데이터가 커질 때 얼마나 느려지는지 나타내는 척도", "빅오 시간복잡도"),
    ("recursion", "재귀", ["재귀", "호출 스택", "기저 조건", "자기 호출"], "함수가 자기 자신을 호출해 문제를 분할하는 기법", "스스로를 불러 작은 문제로 쪼개 푸는 방식", "리커전 기저조건"),
    ("polymorphism", "다형성", ["다형성", "오버라이딩", "동적 디스패치", "상속"], "같은 메시지에 객체 타입별로 다르게 동작하는 OOP 성질", "같은 호출이 객체에 따라 다르게 작동하는 성질", "폴리모피즘 오버라이딩"),
    ("di-concept", "의존성 주입", ["DI", "제어의 역전", "주입", "느슨한 결합"], "객체가 필요한 의존을 외부에서 받게 해 결합도를 낮추는 패턴", "필요한 부품을 밖에서 넣어줘 갈아끼우기 쉽게 하는 설계", "디아이 제어의역전"),
    ("cors", "CORS", ["CORS", "교차 출처", "preflight", "Access-Control"], "다른 출처 자원 요청을 브라우저가 제어하는 보안 정책", "다른 도메인 API 호출을 브라우저가 막거나 허용하는 규칙", "코어스 에러"),
    ("jwt-token", "JWT", ["JWT", "토큰", "서명", "stateless"], "서명된 토큰으로 인증 정보를 담아 주고받는 방식", "로그인 정보를 서명된 문자열에 담아 검증하는 토큰", "제이더블유티 토큰"),
    ("websocket", "WebSocket", ["WebSocket", "양방향", "실시간", "지속 연결"], "하나의 연결로 서버와 양방향 실시간 통신을 하는 프로토콜", "연결을 유지하며 서버와 실시간으로 주고받는 통신", "웹소켓 실시간"),
    ("cache", "캐시", ["캐시", "히트", "무효화", "TTL"], "자주 쓰는 데이터를 빠른 저장소에 두어 응답을 높이는 기법", "자주 쓰는 걸 가까이 저장해 빨리 꺼내쓰는 방식", "캐시 무효화"),
    ("loadbalancer", "로드 밸런서", ["로드 밸런서", "분산", "라운드로빈", "헬스 체크"], "트래픽을 여러 서버에 분산해 부하를 나누는 장치", "요청을 여러 서버로 나눠 보내 몰림을 막는 것", "로드밸런서 라운드로빈"),
    ("dns", "DNS", ["DNS", "도메인", "IP 변환", "네임 서버"], "도메인 이름을 IP 주소로 변환하는 인터넷 이름 체계", "사이트 이름을 실제 주소로 바꿔주는 전화번호부", "디엔에스 도메인"),
    ("consistent-hashing", "컨시스턴트 해싱", ["consistent hashing", "링", "노드 추가", "재분배 최소화"], "노드 증감 시 키 재분배를 최소화하는 해싱 기법", "서버가 늘거나 줄어도 데이터 이동을 줄이는 분배법", "컨시스턴트해싱 재분배"),
    ("btree", "B-tree", ["B-tree", "균형 트리", "디스크", "범위 검색"], "디스크 기반 검색에 적합한 다진 균형 트리 자료구조", "한 노드에 여러 키를 담아 디스크 검색을 줄이는 트리", "비트리 인덱스 구조"),
    ("deadlock", "교착 상태", ["데드락", "상호 대기", "순환", "자원 점유"], "여러 스레드가 서로의 자원을 기다리며 멈추는 상태", "서로 가진 걸 기다리다 둘 다 못 가는 상황", "데드락 발생 조건"),
    ("mvc", "MVC 패턴", ["MVC", "모델", "뷰", "컨트롤러"], "관심사를 모델·뷰·컨트롤러로 분리하는 아키텍처 패턴", "데이터·화면·제어를 셋으로 나눠 관리하는 구조", "엠브이씨 패턴"),
    ("orm", "ORM", ["ORM", "객체-테이블 매핑", "SQL 추상화", "JPA"], "객체와 DB 테이블을 매핑해 SQL을 추상화하는 기술", "객체로 DB를 다루게 해주는 매핑 방식", "오알엠 매핑"),
    ("tx-isolation", "트랜잭션 격리 수준", ["격리 수준", "팬텀 리드", "Read Committed", "일관성"], "동시 트랜잭션이 서로 미치는 영향을 정하는 단계", "동시 작업이 서로 얼마나 간섭하게 둘지 정하는 단계", "격리수준 팬텀리드"),
    ("cdn", "CDN", ["CDN", "엣지", "캐싱", "지연 감소"], "콘텐츠를 사용자 가까운 엣지 서버에서 제공하는 분산망", "파일을 가까운 서버에서 줘 빠르게 받게 하는 망", "씨디엔 엣지서버"),
    ("message-queue", "메시지 큐", ["메시지 큐", "비동기", "생산자-소비자", "버퍼"], "생산자와 소비자를 비동기로 잇는 메시지 버퍼 시스템", "작업을 줄 세워 천천히 처리하게 잇는 비동기 통로", "메시지큐 비동기"),
    ("regex", "정규 표현식", ["정규식", "패턴 매칭", "메타문자", "그룹"], "문자열 패턴을 기호로 표현해 검색·치환하는 표기법", "글자 패턴을 규칙으로 적어 찾고 바꾸는 방법", "정규표현식 패턴매칭"),
    ("unicode", "유니코드", ["유니코드", "코드 포인트", "UTF-8", "인코딩"], "전 세계 문자를 하나의 코드 체계로 표현하는 표준", "모든 나라 글자를 번호로 통일해 표현하는 표준", "유니코드 유티에프8"),
    ("base64", "Base64", ["Base64", "이진 인코딩", "ASCII", "전송"], "이진 데이터를 ASCII 문자로 바꿔 전송하는 인코딩 방식", "바이너리를 문자로 바꿔 안전히 실어 보내는 인코딩", "베이스64 인코딩"),
    ("pagination", "페이지네이션", ["페이지네이션", "offset", "limit", "목록 분할"], "많은 결과를 페이지 단위로 나눠 제공하는 방식", "목록을 잘라서 페이지로 나눠 보여주는 것", "페이지네이션 오프셋"),
    ("rate-limiting", "레이트 리미팅", ["rate limiting", "요청 제한", "토큰 버킷", "throttling"], "단위 시간당 요청 수를 제한해 과부하를 막는 기법", "일정 시간에 요청 횟수를 제한해 막는 것", "레이트리미팅 토큰버킷"),
    ("idempotency", "멱등성", ["멱등성", "같은 결과", "재시도 안전", "idempotent"], "같은 요청을 여러 번 보내도 결과가 같은 성질", "여러 번 해도 한 번 한 것과 같은 안전한 성질", "멱등성 재시도"),
    ("graphql", "GraphQL", ["GraphQL", "쿼리 언어", "필요한 필드", "단일 엔드포인트"], "클라이언트가 필요한 필드만 요청하는 API 쿼리 언어", "원하는 데이터만 골라 한 번에 받는 API 방식", "그래프큐엘 필드선택"),
]


def card_text(spec):
    _id, name, terms, desc, _sem, _typo = spec
    return f"{name}\n{desc}\n키워드: {', '.join(terms)}"


def queries_for(spec):
    _id, name, terms, _desc, sem, typo = spec
    return [
        (" ".join(terms), "exact_token"),
        (f"{name}가 뭐야?", "mixed"),
        (sem, "semantic"),
        (typo, "typo"),
    ]


# --- BM25 (앱 tokenize 사용) ------------------------------------------------
class BM25:
    def __init__(self, docs, k1=1.5, b=0.75):
        self.ids = list(docs)
        self.k1, self.b = k1, b
        self.toks = {i: tokenize(docs[i]) for i in self.ids}
        self.len = {i: len(self.toks[i]) for i in self.ids}
        self.avg = (sum(self.len.values()) / len(self.ids)) if self.ids else 0.0
        df = {}
        self.tf = {}
        for i in self.ids:
            counts = {}
            for t in self.toks[i]:
                counts[t] = counts.get(t, 0) + 1
            self.tf[i] = counts
            for t in counts:
                df[t] = df.get(t, 0) + 1
        n = len(self.ids)
        self.idf = {t: math.log(1 + (n - df_t + 0.5) / (df_t + 0.5)) for t, df_t in df.items()}

    def rank(self, query):
        qt = set(tokenize(query))
        scored = []
        for i in self.ids:
            s = 0.0
            for t in qt:
                f = self.tf[i].get(t, 0)
                if f == 0:
                    continue
                idf = self.idf.get(t, 0.0)
                denom = f + self.k1 * (1 - self.b + self.b * self.len[i] / max(self.avg, 1.0))
                s += idf * (f * (self.k1 + 1)) / denom
            scored.append((i, s))
        scored.sort(key=lambda x: x[1], reverse=True)  # 점수 0 포함 전체를 순위로 반환(안정 정렬)
        return [i for i, _ in scored]


# --- bge (Ollama bge-m3), 임베딩 디스크 캐시로 재측정 가속 ---------------------
_CACHE_FILE = HERE / ".embed_cache.json"
_EMB = json.loads(_CACHE_FILE.read_text(encoding="utf-8")) if _CACHE_FILE.exists() else {}
_EMB_DIRTY = False


def embed(text):
    global _EMB_DIRTY
    key = hashlib.md5(text.encode("utf-8")).hexdigest()
    if key in _EMB:
        return _EMB[key]
    base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    body = json.dumps({"model": os.getenv("POC_EMBED_MODEL", "bge-m3"), "prompt": text}).encode("utf-8")
    req = urllib.request.Request(f"{base}/api/embeddings", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        v = json.loads(r.read().decode("utf-8"))["embedding"]
    norm = math.sqrt(sum(x * x for x in v)) or 1.0
    v = [x / norm for x in v]
    _EMB[key] = v
    _EMB_DIRTY = True
    return v


@atexit.register
def _flush_embed_cache():
    if _EMB_DIRTY:
        _CACHE_FILE.write_text(json.dumps(_EMB), encoding="utf-8")


def bge_ranker(docs):
    vecs = {i: embed(docs[i]) for i in docs}

    def rank(query):
        q = embed(query)
        return sorted(docs, key=lambda i: sum(a * b for a, b in zip(q, vecs[i])), reverse=True)
    return rank


def rrf(lists, k=60):
    scores = {}
    for ids in lists:
        for rank, cid in enumerate(ids):
            scores[cid] = scores.get(cid, 0.0) + 1.0 / (k + rank + 1)
    return sorted(scores, key=lambda c: scores[c], reverse=True)


def run_corpus(specs):
    docs = {s[0]: card_text(s) for s in specs}
    bm = BM25(docs)
    bg = bge_ranker(docs)
    rankers = {"bm25": bm.rank, "bge": bg, "hybrid": lambda q: rrf([bm.rank(q), bg(q)])}

    qs = []
    for s in specs:
        for q, qt in queries_for(s):
            qs.append((q, s[0], qt))

    out = {}
    for name, fn in rankers.items():
        recs = []
        for q, gold, qt in qs:
            ranked = fn(q)
            rank = ranked.index(gold) + 1 if gold in ranked else None
            recs.append((rank, qt))
        n = len(recs)
        rec = {k: sum(1 for r, _ in recs if r and r <= k) / n for k in KS}
        mrr = sum((1.0 / r) if r else 0.0 for r, _ in recs) / n
        bytype = {}
        for qt in QTYPES:
            sub = [r for r, t in recs if t == qt]
            bytype[qt] = sum(1 for r in sub if r and r <= 1) / len(sub) if sub else 0.0
        out[name] = {"recall": rec, "mrr": mrr, "bytype": bytype, "n": n}
    return out


def main():
    corpora = {"DENSE (근접개념 집중)": DENSE, "DIVERSE (다양 분포)": DIVERSE}
    results = {name: run_corpus(specs) for name, specs in corpora.items()}

    lines = []
    lines.append("# 코퍼스 규모/구성 실험 — 30장 DENSE vs DIVERSE\n")
    lines.append("> 자동 생성. `python evals/retrieval_poc/corpus_scaling.py`\n")
    lines.append("질문: 카드를 30장으로 늘리면 hybrid가 살아나는가? → 코퍼스 '구성'이 가른다.\n")
    for cname, res in results.items():
        lines.append(f"## {cname} — 카드 30장, 질의 {res['bm25']['n']}개\n")
        lines.append("| 리트리버 | recall@1 | recall@3 | MRR | exact | mixed | semantic | typo |")
        lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
        for nm in ("bm25", "bge", "hybrid"):
            r = res[nm]
            bt = r["bytype"]
            lines.append(f"| {nm} | {r['recall'][1]:.0%} | {r['recall'][3]:.0%} | {r['mrr']:.3f} | "
                         f"{bt['exact_token']:.0%} | {bt['mixed']:.0%} | {bt['semantic']:.0%} | {bt['typo']:.0%} |")
        gap = res["hybrid"]["recall"][1] - res["bge"]["recall"][1]
        verdict = "hybrid가 bge보다 높음 → 하이브리드가 값을 함!" if gap > 0.001 else (
            "hybrid ≈ bge" if abs(gap) <= 0.001 else "hybrid가 bge보다 낮음 → 하이브리드가 깎아먹음")
        lines.append(f"\n→ hybrid recall@1 − bge recall@1 = {gap:+.1%} ({verdict})\n")

    REPORT.write_text("\n".join(lines), encoding="utf-8")

    for cname, res in results.items():
        print(f"== {cname} ==")
        for nm in ("bm25", "bge", "hybrid"):
            r = res[nm]
            print(f"  {nm:7s} r@1={r['recall'][1]:.3f} r@3={r['recall'][3]:.3f} mrr={r['mrr']:.3f} "
                  f"typo={r['bytype']['typo']:.2f} exact={r['bytype']['exact_token']:.2f}")
        gap = res["hybrid"]["recall"][1] - res["bge"]["recall"][1]
        print(f"  hybrid-bge r@1 = {gap:+.3f}")
    print(f"report -> {REPORT}")


if __name__ == "__main__":
    main()

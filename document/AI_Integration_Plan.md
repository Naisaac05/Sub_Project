# DevMatch AI 기능 통합 계획서

> LangChain / LangGraph 기반 AI 마이크로서비스 도입

---

## 1. 현재 시스템 분석

### 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 3.x + Java 17 |
| Frontend | Next.js 14 + TypeScript + Tailwind CSS |
| DB | MySQL 8.0 + Redis 7 |
| 인프라 | Docker Compose |

### 현재 매칭/추천 방식의 한계

현재 `RecommendationService`는 **Rule-based 점수 계산**으로 동작합니다:

- 기술 스택 일치도: 최대 40점 (단순 키워드 매칭)
- 피드백/학습 스타일 적합도: 최대 30점 (경력 연수 기반 분기)
- 스케줄 가용 시간 중첩: 최대 30점 (요일 겹침 수 계산)

**한계점:**
- 멘티의 자유 텍스트(목표, 고민 등)를 분석하지 못함
- 추천 사유가 템플릿 문자열로 고정되어 있음
- 테스트 결과에 대한 상세 피드백이 없음 (합격/불합격만 표시)
- 커뮤니티 질문에 대한 즉시 응답 불가

---

## 2. 아키텍처 선택

### 비교

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **Python AI 마이크로서비스** | FastAPI + LangChain/LangGraph 별도 서비스 | LangChain 생태계 100% 활용, MSA 경험 | 서비스 간 통신 오버헤드, 배포 복잡도 |
| **LangChain4j** | Java 네이티브 LangChain 포팅 | Spring Boot 직접 통합 | 생태계 규모가 작음 |
| **Spring AI** | Spring 공식 AI 프레임워크 | Spring과 자연스러운 통합 | LangGraph급 워크플로우 미지원 |

### 선택: Python AI 마이크로서비스

**선택 근거:**
- LangChain/LangGraph의 풍부한 Python 생태계 활용
- MSA(마이크로서비스 아키텍처) 경험을 포트폴리오에 추가
- AI 서비스를 독립적으로 스케일링 가능
- 기존 Spring Boot 백엔드 수정 최소화

---

## 3. 전체 시스템 아키텍처

```
┌─────────────────┐     ┌────────────────────────┐     ┌───────────────────┐
│                 │     │                        │     │                   │
│   Next.js       │────▶│   Spring Boot          │────▶│   Python AI       │
│   Frontend      │     │   Backend (:8080)      │REST │   Service         │
│   (:3000)       │     │                        │────▶│   (FastAPI :8000) │
│                 │     │  - 기존 API 유지         │     │                   │
│                 │     │  - AI API 프록시/연동     │     │  - LangChain      │
│                 │     │                        │     │  - LangGraph      │
└─────────────────┘     └────────────────────────┘     │  - ChromaDB       │
                                 │                      │                   │
                                 ▼                      └───────────────────┘
                        ┌────────────────────────┐              │
                        │  MySQL 8.0 + Redis 7   │              ▼
                        └────────────────────────┘     ┌───────────────────┐
                                                       │  LLM Provider     │
                                                       │  (OpenAI/Claude)  │
                                                       └───────────────────┘
```

### 통신 흐름

1. **Frontend → Spring Boot**: 기존 API 호출 (JWT 인증 유지)
2. **Spring Boot → AI Service**: 내부 REST 호출 (Docker 네트워크)
3. **AI Service → LLM Provider**: LangChain을 통한 외부 API 호출
4. **AI Service → MySQL**: DB에서 멘토/테스트 데이터 직접 조회 (읽기 전용)

---

## 4. AI 기능 상세

### 4-1. AI 테스트 피드백 (난이도: 하 / 우선순위: 1)

**현재:** 객관식 자동 채점 → 점수/합격 여부만 표시
**개선:** 틀린 문제별 상세 피드백 + 학습 방향 제안

#### 흐름

```
멘티가 테스트 제출
       │
       ▼
Spring Boot: TestService.submitTest() — 채점 완료
       │
       ▼
Spring Boot → AI Service: POST /api/ai/feedback
       │  (틀린 문제 목록, 카테고리, 점수)
       ▼
LangChain: LLM이 각 문제에 대해 분석
       │  - 왜 틀렸는지 설명
       │  - 올바른 개념 해설
       │  - 관련 학습 자료 추천
       ▼
응답 → Spring Boot → Frontend: 테스트 결과 + AI 피드백 표시
```

#### API 설계

```
POST /api/ai/feedback
Request:
{
  "category": "JAVA",
  "totalScore": 60,
  "passingScore": 70,
  "passed": false,
  "wrongAnswers": [
    {
      "questionText": "Java에서 String이 불변인 이유는?",
      "selectedAnswer": 2,
      "correctAnswer": 1,
      "options": ["보안과 성능", "메모리 절약만", "컴파일러 제한", "문법 규칙"]
    }
  ]
}

Response:
{
  "overallAnalysis": "Java 기초 문법은 이해하고 계시지만, 핵심 동작 원리에 대한...",
  "feedbacks": [
    {
      "questionText": "Java에서 String이 불변인 이유는?",
      "explanation": "String이 불변인 이유는 보안(비밀번호 등), String Pool을 통한 성능 최적화...",
      "concept": "String Immutability",
      "studyTip": "String vs StringBuilder vs StringBuffer 비교 학습을 추천합니다."
    }
  ],
  "recommendedTopics": ["String 내부 동작", "JVM 메모리 구조", "String Pool"],
  "studyPlan": "1주차: String 클래스 소스코드 분석..."
}
```

#### LangChain 구현 (Python)

```python
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

feedback_prompt = ChatPromptTemplate.from_messages([
    ("system", """당신은 Java/Spring 전문 멘토입니다.
    멘티의 테스트 결과를 분석하여 상세한 피드백을 제공하세요.
    각 틀린 문제에 대해:
    1. 왜 선택한 답이 틀렸는지
    2. 올바른 개념 설명
    3. 관련 학습 팁
    을 포함해 주세요."""),
    ("human", "카테고리: {category}\n점수: {score}/{passing}\n틀린 문제:\n{wrong_answers}")
])

chain = feedback_prompt | ChatOpenAI(model="gpt-4o") | output_parser
```

---

### 4-2. AI 멘토 매칭 강화 (난이도: 중 / 우선순위: 2)

**현재:** 키워드 매칭 + 경력 점수 + 스케줄 겹침
**개선:** LLM 기반 의미적 분석 + 자연어 매칭 사유 생성

#### 흐름

```
멘티 설문(SurveyResponse) 작성 완료
       │
       ▼
Spring Boot: RecommendationService — 기존 Rule-based 스코어링 (1차 필터)
       │  상위 10명 후보 추출
       ▼
Spring Boot → AI Service: POST /api/ai/matching/enhance
       │  (멘티 설문 + 후보 멘토 프로필 목록)
       ▼
LangChain:
       │  1. 멘티 목표/고민 텍스트 분석
       │  2. 각 멘토 프로필과 의미적 적합도 점수 보정
       │  3. 추천 사유 자연어 생성
       ▼
응답 → Spring Boot: 보정된 점수 + 자연어 추천 사유로 최종 6명 선정
```

#### API 설계

```
POST /api/ai/matching/enhance
Request:
{
  "menteeProfile": {
    "currentLevel": "BEGINNER",
    "techStack": "Java, Spring Boot",
    "goal": "백엔드 개발자로 취업하고 싶습니다. 특히 대규모 트래픽 처리에 관심이 있어요.",
    "concern": "독학으로 공부하다 보니 코드 리뷰를 받아본 적이 없습니다.",
    "preferredStyle": "꼼꼼한 코드 리뷰 위주"
  },
  "candidateMentors": [
    {
      "mentorId": 5,
      "name": "김시니어",
      "specialty": ["Java", "Spring Boot", "Kubernetes"],
      "careerYears": 8,
      "company": "네이버",
      "bio": "대규모 서비스 백엔드를 담당하고 있습니다.",
      "ruleBasedScore": 75
    }
  ]
}

Response:
{
  "enhancedRecommendations": [
    {
      "mentorId": 5,
      "adjustedScore": 92,
      "recommendation": "네이버에서 대규모 트래픽을 직접 다루고 계시는 멘토님으로,
        대규모 트래픽 처리에 대한 관심사와 정확히 일치합니다.
        Kubernetes 경험이 있어 인프라 관점의 조언도 기대할 수 있습니다.",
      "matchHighlights": ["대규모 트래픽 경험", "코드 리뷰 문화", "취업 멘토링 가능"]
    }
  ]
}
```

---

### 4-3. AI 학습 가이드 챗봇 (난이도: 상 / 우선순위: 3)

**LangGraph의 상태 기반 워크플로우를 활용한 다단계 대화형 챗봇**

#### LangGraph 워크플로우 설계

```
                    ┌──────────────┐
                    │  사용자 질문   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  의도 분류     │
                    │  (Router)    │
                    └──────┬───────┘
                    ┌──────┼───────────────┐
                    │      │               │
             ┌──────▼──┐ ┌─▼────────┐ ┌───▼──────────┐
             │ 학습 질문 │ │ 플랫폼    │ │ 멘토링 관련   │
             │ 분석     │ │ FAQ 검색  │ │ 상담         │
             └──────┬──┘ └─┬────────┘ └───┬──────────┘
                    │      │               │
                    ▼      ▼               ▼
             ┌─────────────────────────────────┐
             │  RAG: 커뮤니티 게시글 + FAQ 검색   │
             │  (ChromaDB Vector Store)        │
             └──────────────┬──────────────────┘
                            │
                     ┌──────▼───────┐
                     │  응답 생성     │
                     │  + 후속 질문   │
                     └──────────────┘
```

#### LangGraph 구현 (Python)

```python
from langgraph.graph import StateGraph, START, END
from typing import TypedDict, Literal

class TutorState(TypedDict):
    messages: list
    user_id: int
    test_results: list
    intent: str
    retrieved_docs: list

def classify_intent(state: TutorState) -> TutorState:
    """사용자 질문의 의도를 분류"""
    # LLM으로 의도 분류: learning / platform / mentoring
    ...

def analyze_learning(state: TutorState) -> TutorState:
    """테스트 결과 기반 학습 분석"""
    # DB에서 사용자의 테스트 결과 조회
    # 약점 영역 파악 + 맞춤 설명 생성
    ...

def search_faq(state: TutorState) -> TutorState:
    """RAG로 FAQ/커뮤니티 검색"""
    # ChromaDB에서 유사 질문/답변 검색
    ...

def generate_response(state: TutorState) -> TutorState:
    """최종 응답 생성"""
    ...

# 그래프 구성
graph = StateGraph(TutorState)
graph.add_node("classify", classify_intent)
graph.add_node("learning", analyze_learning)
graph.add_node("faq", search_faq)
graph.add_node("mentoring", mentoring_counsel)
graph.add_node("respond", generate_response)

graph.add_edge(START, "classify")
graph.add_conditional_edges("classify", route_by_intent, {
    "learning": "learning",
    "platform": "faq",
    "mentoring": "mentoring"
})
graph.add_edge("learning", "respond")
graph.add_edge("faq", "respond")
graph.add_edge("mentoring", "respond")
graph.add_edge("respond", END)

tutor_agent = graph.compile()
```

#### API 설계

```
POST /api/ai/chat
Request:
{
  "userId": 1,
  "sessionId": "chat-session-uuid",
  "message": "Java에서 인터페이스와 추상 클래스의 차이가 뭔가요?"
}

Response:
{
  "reply": "인터페이스와 추상 클래스의 핵심 차이점을 설명드리겠습니다...",
  "relatedPosts": [
    { "postId": 42, "title": "인터페이스 vs 추상클래스 정리" }
  ],
  "suggestedQuestions": [
    "다형성은 어떤 상황에서 쓰나요?",
    "디자인 패턴에서 인터페이스는 어떻게 활용되나요?"
  ]
}
```

---

### 4-4. 커뮤니티 AI 기능 (난이도: 하 / 우선순위: 4)

| 기능 | 설명 | API |
|------|------|-----|
| 게시글 자동 태깅 | 게시글 내용 분석 → 카테고리/태그 자동 부여 | `POST /api/ai/posts/tag` |
| AI 답변 초안 | 질문 게시글에 AI가 초안 답변 생성 | `POST /api/ai/posts/answer` |
| 게시글 요약 | 긴 게시글을 3줄로 요약 | `POST /api/ai/posts/summarize` |

---

## 5. 프로젝트 구조

### AI 서비스 디렉토리 구조

```
ai-service/
├── Dockerfile
├── requirements.txt
├── .env.example
├── app/
│   ├── main.py                  # FastAPI 앱 진입점
│   ├── config.py                # 환경변수, LLM 설정
│   ├── routers/
│   │   ├── feedback.py          # 테스트 피드백 API
│   │   ├── matching.py          # 매칭 강화 API
│   │   ├── chatbot.py           # 학습 가이드 챗봇 API
│   │   └── community.py         # 커뮤니티 AI API
│   ├── chains/
│   │   ├── feedback_chain.py    # LangChain: 테스트 피드백
│   │   ├── matching_chain.py    # LangChain: 매칭 분석
│   │   └── community_chain.py   # LangChain: 게시글 분석
│   ├── graphs/
│   │   └── tutor_graph.py       # LangGraph: 학습 가이드 워크플로우
│   ├── vectorstore/
│   │   └── chroma_store.py      # ChromaDB 벡터 스토어 관리
│   └── utils/
│       ├── db.py                # MySQL 읽기 전용 연결
│       └── models.py            # Pydantic 스키마
```

### Docker Compose 추가 설정

```yaml
# 기존 docker-compose.yml에 추가

  # ===== AI Service =====
  ai-service:
    build: ./ai-service
    container_name: devmatch-ai
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      # 또는 ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      DATABASE_URL: mysql+pymysql://root:adminuser@mysql:3306/devmatch
      REDIS_URL: redis://redis:6379
    depends_on:
      - mysql
      - redis

  # ===== ChromaDB (벡터 DB) =====
  chromadb:
    image: chromadb/chroma:latest
    container_name: devmatch-chromadb
    restart: unless-stopped
    ports:
      - "8001:8000"
    volumes:
      - chroma_data:/chroma/chroma

# volumes에 추가
volumes:
  mysql_data:
  redis_data:
  chroma_data:
```

### requirements.txt

```
# Core
fastapi==0.115.x
uvicorn==0.34.x
pydantic==2.x

# LangChain
langchain==0.3.x
langchain-openai==0.3.x
# langchain-anthropic==0.3.x  # Claude 사용 시
langgraph==0.3.x

# Vector Store
chromadb==0.6.x
langchain-chroma==0.2.x

# Database
sqlalchemy==2.x
pymysql==1.x

# Utils
python-dotenv==1.x
httpx==0.28.x
redis==5.x
```

---

## 6. Spring Boot 연동

### AI 서비스 호출 클라이언트

```java
// backend/src/main/java/com/devmatch/config/AiServiceConfig.java

@Configuration
public class AiServiceConfig {

    @Value("${ai-service.base-url:http://ai-service:8000}")
    private String aiServiceBaseUrl;

    @Bean
    public RestClient aiServiceClient() {
        return RestClient.builder()
                .baseUrl(aiServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
```

```java
// backend/src/main/java/com/devmatch/service/AiService.java

@Service
@RequiredArgsConstructor
public class AiService {

    private final RestClient aiServiceClient;

    public AiFeedbackResponse getTestFeedback(AiFeedbackRequest request) {
        return aiServiceClient.post()
                .uri("/api/ai/feedback")
                .body(request)
                .retrieve()
                .body(AiFeedbackResponse.class);
    }

    public AiMatchingResponse enhanceMatching(AiMatchingRequest request) {
        return aiServiceClient.post()
                .uri("/api/ai/matching/enhance")
                .body(request)
                .retrieve()
                .body(AiMatchingResponse.class);
    }
}
```

### application.yml 추가 설정

```yaml
# AI Service 설정
ai-service:
  base-url: ${AI_SERVICE_URL:http://localhost:8000}
  timeout: 30000  # LLM 응답 대기 최대 30초
```

---

## 7. 단계별 구현 로드맵

| 단계 | 기능 | 핵심 기술 | 예상 기간 |
|------|------|-----------|-----------|
| **Step 1** | AI 서비스 기본 세팅 | FastAPI + Docker + LangChain 연결 | 1주 |
| **Step 2** | 테스트 AI 피드백 | LangChain Chain + Prompt Engineering | 1~2주 |
| **Step 3** | AI 멘토 매칭 강화 | LangChain + Embedding 유사도 | 2주 |
| **Step 4** | 학습 가이드 챗봇 | LangGraph + RAG (ChromaDB) | 3주 |
| **Step 5** | 커뮤니티 AI 기능 | LangChain + 배치 처리 | 1주 |
| **Step 6** | 프론트엔드 AI UI | Next.js + 스트리밍 응답 | 2주 |

### Step 1 상세: AI 서비스 기본 세팅

1. `ai-service/` 디렉토리 생성
2. FastAPI 앱 + Dockerfile 작성
3. docker-compose.yml에 ai-service 추가
4. LangChain으로 LLM 연결 테스트
5. Spring Boot에서 AI 서비스 호출 테스트 (`/health` 엔드포인트)
6. `.env`에 `OPENAI_API_KEY` 또는 `ANTHROPIC_API_KEY` 추가

---

## 8. 비용 및 고려사항

### LLM API 비용 (예상)

| 기능 | 호출 빈도 | 토큰/호출 | 월간 예상 비용 (GPT-4o) |
|------|-----------|-----------|------------------------|
| 테스트 피드백 | ~100회/월 | ~2,000 | ~$3 |
| 매칭 강화 | ~50회/월 | ~3,000 | ~$2 |
| 챗봇 | ~500회/월 | ~1,500 | ~$10 |
| 커뮤니티 | ~200회/월 | ~500 | ~$1 |
| **합계** | | | **~$16/월** |

> GPT-4o-mini 사용 시 약 1/10 수준으로 절감 가능

### 보안 고려사항

- AI 서비스는 Docker 내부 네트워크에서만 접근 (외부 포트 노출 안 함)
- API Key는 환경변수로 관리 (`.env` → `.gitignore` 등록)
- 사용자 개인정보는 LLM에 전달하지 않도록 필터링
- AI 응답에 대한 레이트 리밋 적용 (Redis 활용)

### 성능 고려사항

- LLM 응답 지연 (2~10초) → **비동기 처리** 또는 **스트리밍 응답** 적용
- Redis 캐싱: 동일 입력에 대한 반복 호출 방지
- 테스트 피드백은 결과 저장 후 백그라운드 생성 가능
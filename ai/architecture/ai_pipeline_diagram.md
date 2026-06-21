---
type: architecture
category: rag
status: active
updated: 2026-06-18
description: "AI 전용(Python FastAPI) RAG 데이터 파이프라인 Mermaid 다이어그램"
---

# DevMatch AI 전용 아키텍처 다이어그램 (Python AI Service 집중)

이 다이어그램은 프론트엔드/백엔드(Spring)를 제외하고, 순수하게 **FastAPI 및 LangGraph 기반의 AI 파이프라인 내부 동작 구조**만 집중적으로 보여줍니다.

## 🤖 AI 내부 파이프라인 (Mermaid)

```mermaid
graph TD
    %% 외부 입력
    Input["Spring Boot로부터의 질문 요청"]
    
    %% FastAPI AI 서비스 메인
    subgraph FastAPI_Service ["FastAPI AI Service"]
        Router{"의도 분류기<br/>BGE-M3 (99.6% 정확도)"}
        
        %% LangGraph 파이프라인
        subgraph LangGraph_Workflow ["LangGraph RAG Workflow"]
            direction TB
            Node_RAG["1. RAG 지식 검색<br/>BM25 + Chroma Vector DB"]
            Node_Rerank["2. 컨텍스트 리랭킹<br/>FlashRank (Cross-Encoder)"]
            Node_Gen["3. 답변 생성<br/>ExaOne 3.5 2.4B"]
            Node_Valid["4. 답변 품질 검증<br/>Semantic Judge / Rule-based"]
            
            Node_RAG --> Node_Rerank --> Node_Gen --> Node_Valid
        end
        
        %% 일반 대화 처리
        Node_GeneralGen["단순 생성 (잡담 등)<br/>ExaOne 3.5 2.4B"]
        
        %% Fallback 처리
        Node_Fallback["Fallback 매크로 응답<br/>'지식 부족' 안내 템플릿"]
        
        %% 후보 등록
        Node_CandidateQueue["신규 지식 후보(Candidate) 큐"]
        
        %% 라우팅 로직
        Router -- "도메인 질문 (RAG 필요)" --> Node_RAG
        Router -- "인사/단순 대화" --> Node_GeneralGen
        
        %% 검증 루프 로직
        Node_Valid -- "검증 실패 (재시도)" --> Node_Gen
        Node_Valid -- "최종 실패 (재시도 초과)" --> Node_Fallback
        
        %% 최종 출력 라우팅
        Node_Valid -- "검증 통과" --> Output_Stream(["SSE 실시간 스트리밍 반환"])
        Node_GeneralGen --> Output_Stream
        Node_Fallback --> Output_Stream
        
        %% 분석 파이프라인 연결
        Node_Fallback -. "답변 실패 기록" .-> Node_CandidateQueue
    end
    
    %% 지식 저장소 
    subgraph Local_Knowledge ["로컬 모델 & 지식 저장소"]
        VectorDB[("Chroma DB")]
        MD_Cards["Markdown 지식 카드<br/>Concepts / QA"]
        Model(("Ollama<br/>ExaOne 3.5"))
    end
    
    %% 데이터 연결
    Input --> Router
    Node_RAG <--> |"유사도 기반 검색"| VectorDB
    Node_RAG <--> |"본문 데이터 로드"| MD_Cards
    Node_Gen <--> |"Prompt + Context 전송"| Model
    Node_GeneralGen <--> |"Prompt 전송"| Model
    
    %% 평가용 백그라운드 파이프라인 (Evaluation)
    subgraph Evaluation_Pipeline ["AI 평가 파이프라인"]
        GoldenSet[("Golden Dataset<br/>평가용 벤치마크")]
        Evaluator["답변 품질 평가 자동화"]
    end
    
    Evaluator -. "주기적 정확도 평가" .-> LangGraph_Workflow
    GoldenSet -. "정답 제공" .-> Evaluator
```

&nbsp;

## 📌 주요 모듈별 역할 (AI 전용)

1. **의도 분류기 (Router)**
   * `BGE-M3` 다국어 임베딩을 사용해 질문의 의도를 파악합니다.
   * "스프링 N+1 문제" 같은 기술 질문은 RAG 파이프라인으로, "안녕?" 같은 인사말은 단순 생성 파이프라인으로 분기합니다.

2. **LangGraph RAG Workflow**
   * **검색**: `BM25`(키워드)와 `Chroma`(의미 유사도) 하이브리드 검색으로 관련 Markdown 카드를 찾습니다.
   * **리랭킹**: `FlashRank`를 통해 검색된 컨텍스트 중 질문에 가장 관련성 높은 상위 카드만 추려냅니다.
   * **생성 & 검증**: `ExaOne 3.5` 모델이 답변을 생성하고, Rule-based(필수 키워드, 금지어) 및 Semantic 검증기를 통과하는지 확인합니다.

3. **Fallback & Candidate 로직**
   * 검증기를 깐깐하게 설정하여, 할루시네이션(환각) 우려가 있는 경우 무리해서 대답하지 않고 **템플릿 매크로 (Fallback)**로 전환합니다.
   * 실패한 질문은 **지식 후보 큐 (Candidate Queue)**에 쌓이며, 이후 관리자가 새 Markdown 카드를 만들어 넣을 수 있는 기반 데이터가 됩니다.

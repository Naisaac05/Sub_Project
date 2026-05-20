# AI review auto candidate answer draft missing

- 발생 날짜: 2026-05-20
- 범위: ai / backend
- 심각도: medium

## 증상

학습자 화면에는 "RecyclerView가 뭔가여?"에 대한 AI 답변이 표시됐지만, 관리자 지식 후보 상세의 "AI 답변 내용" 입력란은 비어 있었다. 같은 후보의 승인 대기 사유에는 `no_match`가 그대로 표시되어 실제 생성 실패처럼 보였다. 응답 시간도 30초 이상 걸려 짧은 일반 개념 질문에 비해 과하게 느려졌다.

## 원인

자동 후보 저장 시 AI가 생성한 답변 본문을 JSONL 후보의 `definition_draft`로 전달하지 않았다. 그래서 `ai/app/knowledge/candidates/auto_candidates.jsonl`에는 `definition`과 draft가 빈 값으로 남았고, 백엔드 import 후 관리자 화면도 빈 입력란을 보여줬다. 또한 이미 import된 후보는 external candidate id 중복으로 스킵되어 JSONL에 draft가 나중에 생겨도 DB 후보가 보강되지 않았다. RecyclerView, Android, Flutter, DAO 같은 짧은 개념 질문도 static fast-path가 없어 Ollama 생성 경로를 타면서 30초 이상 걸릴 수 있었다.

## 해결 방법

자동 후보 생성에 `generated_answer`를 추가해 `definition_draft`로 저장하도록 바꿨다. 동일 candidate id가 이미 JSONL에 있더라도 기존 draft가 비어 있으면 새 답변으로 보강한다. 백엔드 import도 이미 존재하는 external candidate의 DB draft가 비어 있으면 JSONL draft를 채우도록 했다. RecyclerView, Android, Flutter, DAO는 lightweight static fast-path로 즉시 답변하고, 승인되지 않은 static 답변은 `static_answer_unapproved` 사유로 후보에 남긴다.

- `ai/app/knowledge/auto_candidates.py:17`
- `ai/app/knowledge/auto_candidates.py:86`
- `ai/app/knowledge/auto_candidates.py:122`
- `ai/app/workflow/graph.py:137`
- `ai/app/workflow/lightweight_answers.py:152`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:95`
- `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java:133`
- `backend/src/main/java/com/devmatch/repository/AiReviewCandidateRepository.java:16`

## 재발 방지·메모

학습자에게 보여준 AI 답변과 관리자 승인 후보의 draft는 같은 생성 결과를 공유해야 한다. 후보가 중복이라도 "이미 있음"으로 끝내지 말고, 비어 있는 관리용 필드는 나중에 들어온 더 완성도 높은 후보 데이터로 보강해야 한다. 짧은 일반 개념 질문은 fast-path를 우선 검토하고, LLM 생성이 필요한 경우에만 Ollama를 호출한다.

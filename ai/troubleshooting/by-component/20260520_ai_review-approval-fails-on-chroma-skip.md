---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI 지식 후보 승인이 chroma reindex skip(optional chromadb 미설치) 때문에 실패 발생 원인 분석 및 트러블..."

---

# AI 지식 후보 승인이 chroma reindex skip(optional chromadb 미설치) 때문에 실패

- 발생 일시: 2026-05-20
- 영역: backend
- 심각도: high

## 증상

관리자 > AI 지식베이스 승인 관리(`/admin/ai-review-candidates`)에서 후보(예: hashCode)의
AI 답변을 수정하고 "수정 및 승인"을 누르면 화면에 "승인 처리에 실패했습니다." 가 뜬다.
DB 상에서 후보는 여전히 PENDING(대기중)으로 남지만, 생성된 지식 카드 파일
(`ai/app/knowledge/concepts/generated/auto-review-hashcode.md`)과
`ai/app/vectorstore/index_manifest.json` 변경분은 남아 있어 git에 untracked/modified 로 보인다.

## 원인

승인(EDIT_AND_APPROVE) 처리 순서:
1. DB에서 후보를 APPROVED 로 변경
2. 지식 카드 파일 + manifest 작성 (여기까지 성공, 비트랜잭션 파일 쓰기)
3. `runChromaReindex` 가 `python scripts/reindex_knowledge.py ... --fail-on-chroma-skip` 실행

이 환경에는 `chromadb`(선택적 RAG 의존성, `requirements-rag.txt` 에 optional 로 명시)가 설치돼 있지 않다.
스크립트는 chroma 단계를 정상적으로 **skip** 처리하지만, `--fail-on-chroma-skip` 플래그가
skip 을 **exit code 1** 로 격상시킨다 ([reindex_knowledge.py:259](../ai/scripts/reindex_knowledge.py:259)).
exit≠0 이면 Java 러너가 `IllegalStateException` 을 던지고
([LoggingAiReviewKnowledgeReindexer.java:319](../backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java:319)),
이 예외가 `@Transactional reviewCandidate` 밖으로 전파되면서 승인 트랜잭션이 롤백된다 → HTTP 500 → 프론트 "승인 처리에 실패했습니다."

즉, **설치되지 않은 선택적(optional) 벡터 인덱스 의존성**이 관리자 승인 자체를 막고 있었다.
카드 파일은 reindex 이전에 이미 쓰였기 때문에(비트랜잭션) DB는 롤백돼도 파일은 남는 불일치가 생긴다.

## 해결 방법

승인 경로의 reindex 호출에서 `--fail-on-chroma-skip` 플래그를 제거했다.
- [LoggingAiReviewKnowledgeReindexer.java:174](../backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java:174) `runChromaReindex` — `command.add("--fail-on-chroma-skip")` 삭제
- [LoggingAiReviewKnowledgeReindexerTest.java:136](../backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java:136) 명령어 기대값에서 해당 인자 제거

스크립트 종료 코드 규칙([reindex_knowledge.py:261](../ai/scripts/reindex_knowledge.py:261)):
- chroma **skipped**(의존성 부재 등) → exit 0 → 승인 정상 진행
- chroma **failed**(설치돼 있는데 실제 오류) → exit 1 → 기존대로 throw → 승인 실패로 노출

`reindexChanged_throwsWhenChromaReindexFails` 테스트(실제 실패는 여전히 throw)는 그대로 통과한다.
수동 검증/CI 실행에서는 운영자가 직접 `--fail-on-chroma-skip` 을 붙여 strict 검증을 할 수 있다(스크립트 플래그는 유지).

검증: `gradlew test --tests LoggingAiReviewKnowledgeReindexerTest --tests AiReviewCandidateApprovalV2ServiceTest` → BUILD SUCCESSFUL.

## 재발 방지 / 메모

- 남은 리스크: 카드 파일 쓰기/manifest 갱신과 chroma reindex 가 모두 `@Transactional` 승인 메서드 안에서
  비트랜잭션으로 일어난다. chroma 가 설치돼 있고 **실제로 실패**하면 여전히 카드 파일은 남고 DB는 롤백되는
  불일치가 생긴다. 추후 reindex 를 트랜잭션 커밋 이후(예: `@TransactionalEventListener(AFTER_COMMIT)`)로
  분리하거나, chroma reindex 를 best-effort(로그 후 무시)로 바꾸는 것을 고려.
- 이번 버그로 인해 hashCode 후보는 PENDING 으로 롤백됐지만 `auto-review-hashcode.md` 가 남아 있다.
  수정 적용 후 다시 "수정 및 승인" 하면 정상 처리되며 상태가 일치한다.
- chromadb 설치 자체는 Windows 빌드툴 문제로 보류 중([2026-05-16-ai-requirements-chromadb-build-tools.md](2026-05-16-ai-requirements-chromadb-build-tools.md)).

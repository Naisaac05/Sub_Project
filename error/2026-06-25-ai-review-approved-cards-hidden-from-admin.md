# AI review approved cards hidden from admin candidates

- 발생 일시: 2026-06-25
- 영역: backend / docker / admin
- 심각도: medium

## 증상

`ai/app/knowledge/concepts_v2` 기준 approved 카드는 85장이었지만 관리자 AI 후보 화면의 `APPROVED` 필터에는 DB 후보의 승인 건만 보였다. Docker 백엔드 API 확인에서도 처음에는 파일 기반 approved 카드가 모두 승인 탭에 잡히지 않았다.

## 원인

관리자 후보 API가 `AiReviewCandidate` DB 테이블만 조회하고 `concepts_v2` 파일 카드 저장소를 합치지 않았다. 그래서 AI fast-path/RAG가 읽는 approved 카드와 관리자 후보 화면의 데이터 원천이 달랐다.

Docker 환경에서는 백엔드 컨테이너가 `ai/app/knowledge/concepts_v2`를 마운트하지 않았고, `AI_REVIEW_CONCEPTS_V2_PATH`도 컨테이너 내부 경로로 지정되지 않아 파일 카드 조회가 불가능했다.

추가로 DB 후보에 같은 `publishedCardId`가 있더라도 해당 DB 행이 `REJECTED`이면 승인 탭에 보이지 않는데, 중복 제거가 상태를 보지 않고 `publishedCardId`만으로 파일 카드를 숨기면 approved 파일 카드 1장이 승인 탭에서 빠질 수 있었다.

관련 파일:
- backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:57
- backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:320
- backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:326
- docker-compose.yml:51
- docker-compose.yml:55

## 해결 방법

`AiReviewCandidateApprovalV2Service.listCandidates()`가 DB 후보 응답 뒤에 `concepts_v2`의 `review.card_status == "approved"` JSON 카드를 읽기 전용 `APPROVED` 후보 응답으로 합쳐 내려주도록 수정했다. 파일 카드의 `card_id`는 `publishedCardId`로 내려가므로 기존 화면의 `APPROVED` 필터와 상세 영역에서 바로 확인할 수 있다.

중복 제거는 이미 `APPROVED` 상태로 발행된 DB 행의 `publishedCardId`만 가리도록 제한했다. 비승인 DB 행이 같은 `publishedCardId`를 갖고 있어도 파일 기반 approved 카드는 별도 `APPROVED` 행으로 보인다.

Docker Compose에는 `AI_REVIEW_CONCEPTS_V2_PATH`와 read-only volume mount를 추가해 컨테이너가 현재 카드 파일을 읽게 했다. 동시에 prod 프로필 기동에 필요한 AI review env도 compose에 추가했다.

검증:
- backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:40
- backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:58
- backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:83
- `.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`
- Docker API 최종 확인: `approved_concepts_v2_ids_expected=85`, `approved_tab_contains_concepts_v2_ids=85`, `missing_from_approved_tab=[]`

## 재발 방지 / 메모

관리자 후보 화면은 DB 후보만 보는 화면인지, 발행된 지식 카드까지 보는 화면인지 데이터 원천을 명확히 해야 한다. 승인 완료 탭은 운영자가 “현재 AI가 사용할 수 있는 approved 카드”를 기대할 수 있으므로, `concepts_v2`와 DB 후보의 동기화 또는 합성 응답 테스트를 유지한다.

Docker에서 파일 기반 지식 저장소를 쓰는 기능은 컨테이너 내부 경로와 volume mount가 함께 필요하다. 로컬 테스트만 통과해도 Docker 화면에서 빠질 수 있으므로 compose 경로 검증을 같이 수행한다.

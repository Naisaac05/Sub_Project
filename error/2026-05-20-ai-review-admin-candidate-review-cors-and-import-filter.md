# AI review admin candidate review CORS and import filter

- 발생 날짜: 2026-05-20
- 분야: backend / frontend
- 심각도: medium

## 증상

관리자 AI 지식 후보 승인 관리 화면에서 승인/거절 버튼이 동작하지 않았다. 또한 "새로운 지식 후보 불러오기"에서 빈 질문으로 만들어진 `unknown` 자동 후보가 후보 목록에 섞일 수 있었다.

## 원인

승인/거절 화면은 `PATCH /api/admin/ai-review/candidates/v2/{id}/review`를 호출하지만, 백엔드 CORS 설정의 허용 메서드에 `PATCH`가 없었다. 프론트엔드가 `http://localhost:3000`에서 백엔드 API를 호출하면 브라우저 preflight 단계에서 차단된다.

자동 후보 import는 JSONL 후보를 DB로 옮기기 전에 최소 품질 필터를 거치지 않았다. 그래서 `ai/app/knowledge/candidates/auto_candidates.jsonl`에 남아 있던 `term="unknown"` 및 빈 `source_question`/`resolved_query` 행도 새 후보로 import될 수 있었다.

## 해결 방법

- `PATCH`를 CORS 허용 메서드에 추가했다: `backend/src/main/java/com/devmatch/config/CorsConfig.java:18`
- CORS 회귀 테스트를 추가했다: `backend/src/test/java/com/devmatch/config/CorsConfigTest.java:12`
- JSONL import 전에 import 가능 여부를 검사해, 빈 term 또는 문맥 없는 `unknown` 자동 후보를 건너뛰도록 했다: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:91`, `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:104`
- `unknown` 자동 후보가 저장되지 않는 회귀 테스트를 추가했다: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:187`

## 재발 방지·메모

관리자 화면에서 새 HTTP 메서드를 쓰는 API를 추가할 때는 `CorsConfig`의 허용 메서드와 함께 컨트롤러/서비스 테스트 외 CORS 설정 테스트도 확인한다. 자동 후보 queue는 보조 데이터라서 빈 질문 또는 fallback 부산물이 섞일 수 있으므로, import 단계에서 최종 방어선을 유지한다.

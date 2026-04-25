# [커뮤니티 새 글 카테고리 필터 재발]

- 발생 일시: 2026-04-24
- 영역: backend / frontend
- 심각도: medium

## 증상
- 커뮤니티에서 특정 카테고리를 선택해 글을 작성해도 해당 카테고리 탭에서는 바로 보이지 않고, 전체 목록에서만 확인되는 경우가 있었다.

## 원인
- 2026-04-18 수정은 서버 응답의 카테고리 문자열을 정규화하는 데까지는 반영됐지만, 글 작성/수정 요청은 여전히 화면 라벨 문자열을 그대로 전송하고 있었다.
- 이 경로에서 카테고리 표현이 조금만 달라져도 서버가 요청값을 안정적으로 해석하지 못하거나, 프론트 상태의 문자열 비교가 실패해 카테고리 탭 필터에서 빠질 수 있었다.
- 프론트도 목록/상세/좋아요 응답을 별도 정규화 없이 상태에 넣고 있어, 서버와 클라이언트 사이의 카테고리 표현 차이를 흡수하지 못했다.

## 해결 방법
- 서버 카테고리 정규화기에 ASCII slug(`question`, `study`, `review`, `career`, `free`)도 허용해 요청 카테고리를 안정적으로 해석하도록 보강했다.
  - `backend/src/main/java/com/devmatch/util/CommunityCategoryNormalizer.java:23`
  - `backend/src/main/java/com/devmatch/util/CommunityCategoryNormalizer.java:52`
- 프론트 community API 계층에서 카테고리 라벨을 slug로 직렬화해 작성/수정 요청을 보내고, 목록/상세/좋아요 응답은 다시 표준 카테고리 라벨로 정규화한 뒤 화면 상태에 반영하도록 변경했다.
  - `frontend/src/lib/community.ts:45`
  - `frontend/src/lib/community.ts:64`
  - `frontend/src/lib/community.ts:77`
  - `frontend/src/lib/community.ts:98`
  - `frontend/src/lib/community.ts:116`
  - `frontend/src/lib/community.ts:147`

## 재발 방지 / 메모
- 카테고리처럼 UI 라벨과 저장값이 섞일 수 있는 필드는 화면 문자열을 API 계약값으로 그대로 쓰지 말고, slug나 enum 같은 canonical value를 한 번 거쳐 전송하는 편이 안전하다.
- 이후 커뮤니티 기능을 확장할 때도 필터 기준값은 프론트와 백엔드 모두 같은 canonical value 한 종류로만 비교하는지 먼저 확인한다.

# 커뮤니티 더미 게시글을 실제 작성 흐름으로 교체

- 발생 일시: 2026-04-18
- 영역: backend / frontend
- 심각도: high

## 증상
커뮤니티 화면은 질문/답변, 학습 공유, 멘토링 후기, 취업/이직, 자유게시판 탭이 보이지만 실제로는 더미 배열만 렌더링하고 있어서 글쓰기, 댓글, 좋아요가 작동하지 않았습니다.

## 원인
백엔드 게시글 API는 기본 CRUD와 댓글/좋아요는 있었지만 화면 구성을 위한 `카테고리`와 `조회 수`가 없었고, 프론트 `community/page.tsx`는 API를 전혀 쓰지 않은 채 하드코딩된 게시글 목록에만 의존하고 있었습니다.

## 해결 방법
- 게시글 엔티티와 서비스에 카테고리, 조회 수, 댓글 수 감소 처리까지 추가해 실제 게시판 화면이 필요한 데이터를 응답하도록 확장했습니다. 관련 파일은 `backend/src/main/java/com/devmatch/entity/Post.java:31`, `backend/src/main/java/com/devmatch/service/PostService.java:40` 입니다.
- 게시글 생성/응답 DTO를 카테고리와 조회 수를 포함하는 구조로 교체했습니다. 관련 파일은 `backend/src/main/java/com/devmatch/dto/community/PostCreateRequest.java:1`, `backend/src/main/java/com/devmatch/dto/community/PostResponse.java:1` 입니다.
- 프론트 커뮤니티 페이지를 실제 API 기반으로 다시 작성해 카테고리별 글쓰기, 목록 필터, 상세 보기, 댓글 작성/삭제, 좋아요, 본인 글 수정/삭제가 가능하도록 바꿨습니다. 관련 파일은 `frontend/src/app/community/page.tsx:76`, `frontend/src/lib/community.ts:4` 입니다.

## 재발 방지 / 메모
- 프론트 타입 검사는 `frontend`에서 `tsc --noEmit` 통과로 확인했습니다.
- 백엔드는 `backend`에서 `gradlew.bat compileJava` 통과로 확인했습니다.
- `posts` 테이블에 `category`, `view_count` 컬럼이 추가되므로 운영 환경이 자동 스키마 변경을 사용하지 않으면 DB 마이그레이션이 필요합니다.

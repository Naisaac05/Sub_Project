# 커뮤니티 카테고리별 글쓰기 경험 부족 및 이미지 미지원

- 발생일: 2026-04-18
- 영역: frontend / backend
- 심각도: medium

## 증상
커뮤니티에서 질문/답변, 학습 공유 같은 카테고리를 눌러도 작성 폼이 카테고리별로 달라 보이지 않았고, 글을 작성해도 해당 카테고리 게시판에 쓴 느낌이 약했습니다.  
또 게시글에 이미지를 넣을 수 있는 입력 위치가 없어 화면이 단조롭게 보였습니다.

## 원인
게시글 데이터 모델이 `category`, `title`, `content` 중심으로만 구성되어 있었고, 프론트도 카테고리별 작성 가이드나 이미지 필드를 제공하지 않았습니다.  
LMS 쪽은 `frontend/src/app/lms/layout.tsx` 공통 레이아웃이 없어 타입 산출물 기준으로 레이아웃 참조가 꼬이고 있었고, 이 때문에 일부 LMS 화면 검증도 불안정했습니다.

## 해결 방법
- `frontend/src/app/community/page.tsx:41` 에 카테고리별 메타 정보와 작성 가이드를 추가하고, 선택한 카테고리 보드 카드와 전용 작성 폼이 보이도록 화면을 재구성했습니다.
- 같은 파일 `frontend/src/app/community/page.tsx:647` 근처에 대표 이미지 URL 입력과 미리보기를 추가하고, 카드/상세에서도 이미지가 함께 보이도록 반영했습니다.
- `backend/src/main/java/com/devmatch/entity/Post.java:38`, `backend/src/main/java/com/devmatch/dto/community/PostCreateRequest.java:24`, `backend/src/main/java/com/devmatch/dto/community/PostResponse.java:19`, `backend/src/main/java/com/devmatch/service/PostService.java:49` 에 `imageUrl` 필드를 추가해 저장/조회까지 연결했습니다.
- `frontend/src/app/lms/layout.tsx:1` 를 추가하고 `frontend/src/app/lms/(dashboard)/layout.tsx:1` 를 정리해 LMS 공통 사이드바 레이아웃을 루트로 통일했습니다.

## 재발 방지 / 메모
- 게시판 카테고리가 여러 개인 경우, 단순 필터만 두지 말고 카테고리별 작성 의도와 입력 가이드를 함께 설계해야 사용자가 “어디에 쓰는지”를 직관적으로 이해할 수 있습니다.
- `posts` 테이블에 `image_url` 컬럼이 추가되므로 운영 DB가 자동 스키마 업데이트를 쓰지 않으면 별도 마이그레이션이 필요합니다.

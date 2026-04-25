# 커뮤니티 카테고리 필터 오작동, 게시글 삭제 실패, 이미지 업로드 부재

- 발생 일시: 2026-04-18
- 영역: backend / frontend
- 심각도: medium

## 증상
- 질문/답변 같은 카테고리로 글을 작성해도 해당 카테고리 탭에서 보이지 않고 전체에서만 보였다.
- 게시글 상세에서 삭제 버튼을 눌러도 삭제가 되지 않았다.
- 이미지는 URL 입력만 가능해서 폴더 선택이나 드래그앤드롭 업로드가 불가능했다.

## 원인
- 커뮤니티 카테고리 문자열이 이전 인코딩 깨짐 상태로 저장된 데이터와 현재 정상 한글 카테고리가 섞여 있었는데, 목록/상세 응답에서 이를 정규화하지 않아 프론트 필터가 같은 카테고리로 인식하지 못했다.
- 게시글 삭제 시 `posts`만 삭제하고 `comments`, `post_likes` 연관 데이터를 먼저 정리하지 않아 FK 제약으로 삭제가 실패할 수 있었다.
- 이미지 첨부는 `imageUrl` 텍스트 입력만 연결돼 있었고, 실제 파일 업로드 API와 정적 파일 제공 경로가 없었다.

## 해결 방법
- 카테고리 정규화 유틸을 추가해 기존 깨진 카테고리 값을 정상 한글 카테고리로 변환하고, 생성/수정/응답 모두 같은 기준을 사용하도록 맞췄다.
  - `backend/src/main/java/com/devmatch/util/CommunityCategoryNormalizer.java:6`
  - `backend/src/main/java/com/devmatch/service/PostService.java:58`
  - `backend/src/main/java/com/devmatch/dto/community/PostResponse.java:32`
- 게시글 삭제 전에 댓글과 좋아요를 먼저 삭제하도록 바꿔서 실제 삭제가 가능하게 했다.
  - `backend/src/main/java/com/devmatch/service/PostService.java:103`
  - `backend/src/main/java/com/devmatch/repository/CommentRepository.java:12`
  - `backend/src/main/java/com/devmatch/repository/PostLikeRepository.java:14`
- 커뮤니티 이미지 업로드 API와 `/uploads/**` 정적 경로를 추가하고, 프론트 글쓰기 모달을 파일 선택/드래그앤드롭 업로드 방식으로 교체했다.
  - `backend/src/main/java/com/devmatch/controller/PostController.java:48`
  - `backend/src/main/java/com/devmatch/service/PostService.java:117`
  - `backend/src/main/java/com/devmatch/config/WebConfig.java:18`
  - `frontend/src/lib/community.ts:80`
  - `frontend/src/app/community/page.tsx:230`
  - `frontend/src/app/community/page.tsx:298`
  - `frontend/next.config.js:11`

## 재발 방지 / 메모
- 프론트 `next.config.js`에 `/uploads/**` 리라이트가 추가돼서 dev server 재시작 전에는 새 업로드 이미지가 바로 안 보일 수 있다.
- 운영 환경에서 `file.upload-dir`를 별도로 쓰면 `/uploads/**` 정적 경로도 같은 디렉터리를 바라보는지 함께 확인해야 한다.

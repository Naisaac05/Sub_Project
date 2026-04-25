# 커뮤니티 업로드 이미지 403

- 발생 일자: 2026-04-25
- 영역: backend / frontend
- 심각도: medium

## 증상

커뮤니티 글쓰기에서 이미지를 업로드한 뒤 게시글 카드나 미리보기에서 이미지가 표시되지 않았다.
로컬에서 기존 업로드 파일 URL인 `/uploads/community/68e0df3d-eff1-480c-9bb3-c84c4d404a51.png`를 조회하면 403 Forbidden이 반환됐다.

## 원인

`WebConfig`는 `/uploads/**`를 정적 리소스로 제공하도록 등록했지만, Spring Security 설정에서 해당 경로를 허용하지 않아 `.anyRequest().authenticated()` 규칙에 걸렸다.
Next.js의 `/uploads/:path*` rewrite도 결국 백엔드 `/uploads/**`로 전달되므로, 업로드 자체가 성공해도 브라우저가 이미지를 가져올 때 인증 없는 이미지 요청이 403으로 막혔다.

관련 파일:

- `backend/src/main/java/com/devmatch/config/WebConfig.java:20`
- `backend/src/main/java/com/devmatch/config/SecurityConfig.java:47`
- `frontend/next.config.js:11`

## 해결 방법

`SecurityConfig`에서 `/uploads/**` 요청을 `permitAll()`로 허용했다.
커뮤니티 이미지 URL은 게시글 본문 안의 정적 파일 링크로 렌더링되므로, 인증 API와 별도로 공개 접근이 가능해야 한다.

수정 파일:

- `backend/src/main/java/com/devmatch/config/SecurityConfig.java:47`

## 재발 방지·메모

새로 업로드한 이미지를 확인하려면 백엔드 서버 재시작 후 `/uploads/community/{파일명}`이 200으로 내려오는지 먼저 확인한다.
업로드 API가 201을 반환해도 정적 제공 경로가 막혀 있으면 프론트에서는 "사진이 안 올라간 것처럼" 보일 수 있다.

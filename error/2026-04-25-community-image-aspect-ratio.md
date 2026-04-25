# 커뮤니티 이미지 원본 비율 훼손

- 발생 일자: 2026-04-25
- 영역: frontend
- 심각도: low

## 증상

커뮤니티에 업로드한 사진이 목록 카드, 글쓰기 미리보기, 상세 모달에서 원본 느낌과 다르게 일그러지거나 잘려 보였다.

## 원인

커뮤니티 이미지 렌더링이 고정 높이 컨테이너 안에서 `object-cover`를 사용하고 있었다.
이 설정은 카드 영역을 꽉 채우는 데는 좋지만, 원본 사진의 가로세로 비율이 컨테이너와 다르면 일부가 잘리거나 원본 구도가 훼손된다.

관련 파일:

- `frontend/src/app/community/page.tsx:561`
- `frontend/src/app/community/page.tsx:763`
- `frontend/src/app/community/page.tsx:817`

## 해결 방법

목록 카드, 업로드 미리보기, 상세 모달 이미지 클래스를 `object-contain`으로 변경해 원본 비율을 보존하도록 했다.
남는 여백은 어두운 배경 안에 표시되도록 컨테이너 배경을 유지했다.

수정 파일:

- `frontend/src/app/community/page.tsx:561`
- `frontend/src/app/community/page.tsx:763`
- `frontend/src/app/community/page.tsx:817`

## 재발 방지·메모

사용자가 직접 업로드한 사진은 기본적으로 원본 비율 보존을 우선한다.
프로필 썸네일처럼 의도적으로 크롭해야 하는 UI가 아니라면 `object-cover` 대신 `object-contain` 또는 자연 비율 기반 레이아웃을 사용한다.

# Admin Posts 수동 스모크 (2026-04-24)

전제: 마이그레이션 SQL(§4.1) 적용 완료. 시드 데이터에 게시물/댓글 있음.

1. ADMIN 계정 로그인 → `/admin/posts` 접근 → 목록 200 OK
2. 카테고리 Select → "질문" 선택 → 필터 적용됨
3. 검색 `JPA` 입력 → 300ms debounce 후 결과 갱신
4. 삭제된 글 포함 체크 해제 → deleted=false 만 노출
5. 임의 게시물 클릭 → `/admin/posts/{id}` 상세 진입
6. "게시물 삭제" 클릭 → 사유 9자 입력 시 에러 / 10자 이상 입력 후 "삭제 확정" → toast 성공 + "🗑 삭제됨" 배지
7. 동일 게시물에서 "게시물 삭제" 버튼 숨김 확인 (sticky footer 사라짐)
8. 일반 사용자 계정으로 `/posts/{방금삭제한id}` 접근 → 404
9. 일반 사용자 `/posts` 커뮤니티 목록에서 해당 글 안 보임
10. 상세에서 정상 댓글 1개 "삭제" → dim + "관리자에 의해 삭제됨" 라벨 + commentCount 감소
11. DB: `SELECT action_type, target_type, target_id, reason FROM admin_audit_log ORDER BY id DESC LIMIT 2;` → `POST_DELETE`/`COMMENT_DELETE` + sanitized metadata JSON
12. 이미 삭제된 게시물 DELETE API 재호출 → 400 "이미 삭제된 게시물입니다"
13. 비 ADMIN (USER) 계정으로 `/admin/posts` → 403 페이지

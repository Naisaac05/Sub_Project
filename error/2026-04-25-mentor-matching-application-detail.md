# 멘토 매칭 내역에서 멘티 신청서를 확인할 수 없던 문제

- 발생 날짜: 2026-04-25
- 범위: backend / frontend
- 심각도: medium

## 증상

멘티가 신청서를 작성하고 결제 후 자동 매칭까지 완료되어도, 멘토 화면의 매칭 내역에서는 해당 멘티가 작성한 신청서 내용을 확인할 수 없었다. 멘토는 승인/거절 권한은 없어야 하지만, 실제 멘토링 준비를 위해 매칭된 멘티의 신청서 내용은 읽을 수 있어야 했다.

## 원인

자동 매칭 시 `matchings.application_id`에는 신청서 ID가 저장되고 있었지만, 멘토 매칭 목록 응답에는 `applicationId`가 내려가지 않았고 본인 매칭에 연결된 신청서를 조회하는 API도 없었다. 프론트엔드 매칭 화면도 신청서 상세를 열 수 있는 UI가 없어, 신청서가 멘토에게 전달되지 않는 것처럼 보였다.

## 해결 방법

매칭 응답에 `applicationId`를 포함하고, 본인 매칭에 연결된 신청서를 조회하는 API를 추가했다. 프론트엔드 멘토 매칭 카드에는 `신청서 보기` 버튼을 추가하고, 관리자 멘토 신청서 상세와 비슷한 읽기 전용 상세 모달로 신청서 내용을 확인할 수 있게 했다.

- `backend/src/main/java/com/devmatch/dto/matching/MatchingResponse.java:17` - 매칭 목록 응답에 `applicationId` 추가
- `backend/src/main/java/com/devmatch/controller/MatchingController.java:80` - `/api/matching/{id}/application` 신청서 조회 API 추가
- `backend/src/main/java/com/devmatch/service/MatchingService.java:128` - 매칭 참여자 권한 확인 후 연결된 신청서 반환
- `frontend/src/lib/matching.ts:43` - 매칭 신청서 조회 클라이언트 함수 추가
- `frontend/src/lib/types.ts:204` - 신청서 응답 타입 추가
- `frontend/src/app/matching/page.tsx:114` - 멘토가 보는 신청서 상세 모달 추가
- `frontend/src/app/matching/page.tsx:414` - 멘토 매칭 카드에 `신청서 보기` 버튼 추가

검증:

```text
cd backend
GRADLE_USER_HOME=../.gradle-user-home ./gradlew compileJava --no-daemon
BUILD SUCCESSFUL

cd frontend
npm.cmd run build
Compiled successfully
```

## 재발 방지·메모

자동 매칭은 승인/거절과 별개로, 멘토가 멘티의 신청 배경을 읽을 수 있어야 한다. 앞으로 매칭 생성 시 `applicationId`가 누락되지 않도록 유지하고, 멘토 화면에서는 신청서를 읽기 전용으로만 노출한다.

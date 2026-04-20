# [멘토링 코스 루트 404 및 가짜 후기/정적 신청 배너 문제]

- 발생 일시: 2026-04-18
- 영역: frontend
- 심각도: medium

## 증상
헤더에서 `멘토링 코스`를 누르면 `/mentors` 루트 페이지가 없어 404가 발생했다.  
코스 상세 페이지는 분야별 텍스트가 많이 깨져 있었고, 실제 작성되지 않은 후기가 고정 문구로 노출됐다.  
하단 신청 카드도 `6월`, `7월`, `D-13` 같은 정적 값이 박혀 있어 시간이 지나면 잘못된 정보를 보여주게 되어 있었다.

## 원인
[frontend/src/app/mentors/[id]/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/mentors/[id]/page.tsx:1) 에 코스 상세 화면과 정적 후기/정적 신청 카드가 한 파일에 강하게 결합돼 있었고, `/mentors` 루트에 해당하는 페이지 자체가 없었다.  
코스 데이터도 공통 구조 없이 파일 내부에 박혀 있어 유지보수 중 텍스트 품질과 일관성이 쉽게 무너질 수 있는 상태였다.

## 해결 방법
[frontend/src/app/mentors/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/mentors/page.tsx:1) 를 추가해 `멘토링 코스` 루트 404를 제거하고, 열려 있는 과정과 준비 중인 과정을 나눠 보여주도록 구성했다.  
[frontend/src/lib/course-catalog.ts](/C:/Users/User/Desktop/Sub_Project/frontend/src/lib/course-catalog.ts:1) 에 공통 코스 데이터와 월별 신청 카드 계산 로직을 분리했다.  
[frontend/src/app/mentors/[id]/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/mentors/[id]/page.tsx:1) 는 깨진 텍스트와 가짜 후기를 제거하고, 실제 수강 이력이 있는 멘티만 후기 작성이 가능하도록 `matching` 기반 가드를 추가했다.  
[frontend/src/app/apply/payment/page.tsx](/C:/Users/User/Desktop/Sub_Project/frontend/src/app/apply/payment/page.tsx:1) 도 같은 신청 플랜 계산 로직을 사용하도록 맞췄다.

## 재발 방지 / 메모
코스 소개, 후기, 신청 배너처럼 여러 화면에서 재사용되는 정보는 한 파일 안에 정적으로 복붙하지 말고 공통 카탈로그/계산 함수로 분리하는 편이 안전하다.  
실사용 데이터가 없는 후기 영역은 반드시 빈 상태를 표시하고, 작성 권한은 실제 이용 이력 기반으로 제한해야 신뢰도를 유지할 수 있다.

---
id: auto-review-recyclerview
category: auto-review
difficulty: intermediate
version: admin-approved-candidate
last_updated: 2026-05-20
---

# RecyclerView

## 핵심 설명
RecyclerView는 목록 형태의 데이터를 표시하는 뷰 위젯입니다. 예를 들어, 페이스북의 게시물 목록을 렌더링할 때 사용됩니다. ViewHolder는 각 아이템의 뷰를 재사용해 성능을 향상시킵니다.

## 대표 해결
- 긴 목록이나 반복되는 아이템 UI는 RecyclerView로 구성하고, Adapter에서 데이터와 ViewHolder를 연결한다.
- ViewHolder 패턴을 사용해 아이템 뷰를 재사용하고 스크롤 성능을 안정적으로 유지한다.

## 흔한 오해
- RecyclerView가 데이터를 자동으로 저장하거나 동기화해 주는 것은 아니다. 목록을 효율적으로 보여주는 UI 컴포넌트에 가깝다.
- ViewHolder는 별도 화면이 아니라 각 아이템 뷰를 잡아 두고 재사용하기 위한 구조다.

## 평가 키워드
- RecyclerView
- ViewHolder
- Adapter
- 목록 렌더링

## 사용 맥락
- 원 질문: RecyclerView가 뭔가여?
- 해석된 질문: RecyclerView가 뭔가여?
- 승인자: admin-ui

## 주의할 점
- 승인된 후보 답변을 우선 사용하되, 더 구체적인 문제 맥락이 있으면 RAG 생성 답변에서 함께 고려한다.

## 검색 키워드
- RecyclerView
- auto-review
- source:auto-2e9709f87381

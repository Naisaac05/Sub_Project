---
type: eval
category: evaluation
status: active
updated: 2026-06-18
---

# EXAONE 라이브 E2E 품질 평가

이 평가는 동일한 질문에 대해 실제 AI 리뷰 워크플로를 다음 두 모드로 비교합니다.

- `rag`: BGE-M3로 의미 기반 검색을 수행한 뒤 기존 EXAONE 워크플로를 실행합니다.
- `no_rag_forced`: 검색 결과가 강제로 빈 컨텍스트를 반환하도록 설정한 뒤 동일한 워크플로를 실행합니다.

두 모드 모두 평가기 내부의 경량·정적 답변 처리기를 비활성화합니다. 따라서 평가 대상인 모든 답변은 실제 EXAONE 생성 단계까지 도달합니다. 프로덕션 코드와 기본 프로덕션 라우팅은 변경하지 않습니다.

이 평가기에서는 의미 평가기(Semantic Judge)를 일시적으로 비활성화합니다. 두 번째 EXAONE 평가 요청으로 인한 타임아웃이나 CPU 평가 시간 증가를 방지하기 위한 조치이며, 프로덕션 워크플로의 동작에는 영향을 주지 않습니다.

## 실행 방법

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\evaluate_exaone_live_e2e.py
```

빠른 스모크 테스트:

```powershell
.\.venv\Scripts\python.exe scripts\evaluate_exaone_live_e2e.py --limit 1
```

## 결과 파일

- `REPORT.json`: 기계가 읽을 수 있는 평가 결과와 요약입니다.
- `REPORT.md`: 사람이 검토할 수 있는 전체 답변입니다.

## 결과 해석 시 주의사항

- 필수 키워드와 금지 표현 검사는 규칙 기반의 보조 지표일 뿐입니다.
- 의미 평가기 관련 필드는 이 평가에서 의도적으로 `skipped`로 기록됩니다.
- 실행 성공은 라이브 워크플로가 정상적으로 수행됐음을 의미하지만, 그것만으로 답변의 사실적 품질을 보장하지는 않습니다.
- 지연 시간은 답변 생성을 강제한 평가의 측정값이며, 프로덕션 기본 빠른 경로(fast path)가 섞인 성능을 나타내지 않습니다.
- 이 결과를 운영 품질 기준으로 사용하기 전에 반드시 사람이 검토해야 합니다.

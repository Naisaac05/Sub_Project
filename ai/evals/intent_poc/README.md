---
type: eval
category: evaluation
status: active
updated: 2026-06-18
---

# 의도분류 PoC (intent classification)

AI 꼬리질문에서 학습자 질문의 **의도분류 성능**을 측정하는 독립 실행 PoC.
목표 taxonomy 는 10-class: `ANSWER_REASON, WRONG_ANSWER_REASON, CONCEPT_DEFINITION,
COMPARISON, EXAMPLE_REQUEST, PRACTICAL_USAGE, DEBUG_OR_ERROR, FOLLOW_UP, OFF_TOPIC, UNKNOWN`.

## 경계 규칙 (배포 안전)

- **이건 dev 전용이다. 배포 산출물이 아니다.** 런타임 앱(`app/`)은 이 폴더를 **import 하지 않는다.**
- 의존성은 **항상 PoC → app 단방향.** 앱 코드가 PoC 를 부르면 그때부터 배포가 꼬인다. 그 한 줄만 지키면 된다.
- 앱(`app`)에 대한 결합은 **`classifiers.py` 한 파일에만** 있다. 다른 파일은 app 을 모른다.
- 향후 `ai/` 를 컨테이너화할 때를 대비해 `ai/.dockerignore` 가 `evals/ tests/ experiments/` 를 제외한다.

## 구성

| 파일 | 역할 |
|---|---|
| `seeds.jsonl` | 시드 30개 (의도 10 × 3원형: 전형/다른개념/약신호). **라벨은 여기서만 손작성.** |
| `augment.py` | 결정론적 증강기. 글자오타(자동) + 큐레이션 변형(드리프트 방지) → `dataset.jsonl` |
| `dataset.jsonl` | 증강 결과 240행 (의도당 24, dev/holdout 50:50). `augment.py` 산출물. |
| `classifiers.py` | **앱 결합 단일점.** 평가 대상 분류기 레지스트리 (`current`, `phase1`). |
| `evaluate.py` | dataset 평가 → 정확도·혼동행렬·변형축별 점수 → `REPORT.md` |
| `run.py` | `augment → evaluate` 한 번에 실행 |
| `REPORT.md` | 자동 생성 리포트 (재실행 시 갱신) |

## 실행

```bash
# ai/ 디렉토리에서. (venv: ai/.venv)
python evals/intent_poc/run.py                      # 전체: 데이터 생성 + 평가(current)
python evals/intent_poc/evaluate.py                 # 평가만 (current)
python evals/intent_poc/evaluate.py --classifier phase1   # Phase 1 추출기 평가(구현 후)
```

표준 라이브러리만 쓰므로 추가 의존성 없음. (`classifiers.py` 의 `current` 만 `app.workflow.intent` 를 import)

## 분류기 추가 방법 (예: Phase 1 추출기)

1. `classifiers.py` 의 `_phase1(question) -> 10-class label` 을 실제 구현으로 채운다.
2. `python evals/intent_poc/evaluate.py --classifier phase1` 실행.
3. 같은 `dataset.jsonl` 로 baseline(current) 과 정확도를 직접 비교한다.

## 데이터 확장

`seeds.jsonl` 에 시드를 추가하고 `augment.py` 를 재실행하면 `dataset.jsonl` 이 결정론적으로 재생성된다(난수 없음).

# 프롬프트 v2 비교 실험

`follow_up` 프롬프트 v1 (현재 운영) 과 v2 (개선안) 을 같은 입력에 돌려서 결과를 비교하는 격리된 실험 폴더입니다.

## 무엇을 하는가

- v1 = `ai/app/prompts.py`의 `build_prompt("follow-up", ...)` 결과 (운영 코드 그대로)
- v2 = `ai/app/knowledge/prompts/follow_up_v2.prompt` 템플릿 (한국어 지시문 + few-shot 예시 2개 포함)
- 5개 학생 응답 시나리오에 대해 같은 모델로 v1/v2 각각 호출 → 결과를 사람이 비교

## 폴더 구성

```
ai/experiments/prompt_v2_comparison/
├── README.md          ← 이 파일
├── compare.py         ← 실행 스크립트
├── samples.jsonl      ← 5개 비교 시나리오 (모름/부분정답/오개념/자신있게오답/2단계)
└── results/           ← 실행하면 채워짐
    ├── comparison.md      ← 사람이 보는 비교표 (가장 중요)
    ├── v1_outputs.jsonl   ← v1 원본 출력
    └── v2_outputs.jsonl   ← v2 원본 출력
```

## 실행 전 준비

1. Ollama 서버 실행 중인지 확인
   ```
   ollama serve
   ```
2. 모델 pull (기본은 현재 운영 모델인 qwen3:1.7b)
   ```
   ollama pull qwen3:1.7b
   ```
   다른 모델로 비교하고 싶다면 (예: exaone3.5:2.4b):
   ```
   ollama pull exaone3.5:2.4b
   ```

## 실행

기본 실행 (현재 운영 모델 사용):
```
python ai/experiments/prompt_v2_comparison/compare.py
```

다른 모델로 비교:
```
# PowerShell
$env:COMPARE_MODEL="exaone3.5:2.4b"; python ai/experiments/prompt_v2_comparison/compare.py

# bash
COMPARE_MODEL=exaone3.5:2.4b python ai/experiments/prompt_v2_comparison/compare.py
```

실행 시간 예상 (i5-10210U CPU only, 5개 샘플 × 2회 = 10회 호출):
- qwen3:1.7b → 약 1~3분
- exaone3.5:2.4b → 약 2~5분
- qwen3:4b → 약 5~10분

## 결과 보는 방법

`results/comparison.md` 를 열면 샘플마다 다음 형태로 정리되어 있습니다:

```markdown
## 샘플 followup-001-dont-know — 학습자가 모른다고 답함

**문제**: JPA의 N+1 문제가 무엇인가요?
**학습자 답변**: 모르겠어요

### v1 출력 (1240ms)
...

### v2 출력 (1380ms)
...

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 메모:
```

채점 항목에 직접 점수 매기시면 됩니다. 5개 샘플 × 2개 지표 = 10개 점수만 매기면 v1과 v2 우열이 정량적으로 나옵니다.

## v2가 좋다고 결론 났다면

⚠️ **이 실험은 운영 코드를 전혀 건드리지 않습니다.** v2가 좋으면 다음 중 하나로 운영에 반영:

**옵션 A: 단순 교체 (작은 변경)**
- `ai/app/prompts.py`의 follow-up 분기 안 프롬프트 문자열을 `follow_up_v2.prompt` 내용으로 교체

**옵션 B: 파일 기반으로 리팩토링 (큰 변경)**
- `prompts.py`가 `.prompt` 파일들을 읽어오도록 구조 변경
- 장점: 향후 프롬프트 수정 시 코드 재배포 불필요
- 단점: 작업량 큼, 테스트 코드 영향 있을 수 있음

당장은 A 옵션이 무난하고, B는 나중에 시간 날 때 정리하는 게 좋습니다.

## v2가 별로면

폴더만 삭제하면 끝:
```
# 실험 폴더 통째로 제거
Remove-Item -Recurse ai/experiments/prompt_v2_comparison

# v2 프롬프트 파일도 제거
Remove-Item ai/app/knowledge/prompts/follow_up_v2.prompt
```

운영 코드는 그대로이므로 사이드 이펙트 없습니다.

## 추가 실험 아이디어

이 구조를 그대로 활용해서:
- v3, v4 추가 시도 (`follow_up_v3.prompt` 만들고 compare.py에 v3 분기 추가)
- 같은 v2 프롬프트를 여러 모델에 돌려서 어느 모델이 v2를 가장 잘 따르는지 비교
- 샘플을 골든셋(`ai/evals/golden_dataset.jsonl`)에서 더 가져와서 확장

## 주의사항

- 이 폴더 안의 `.py`는 운영 의존성이 아닙니다 (테스트에도 포함되지 않음)
- `compare.py`는 운영 코드(`ai/app/prompts.py`)를 **읽기만** 합니다 (수정 없음)
- 실험 결과는 모델/시간/난수에 따라 다르므로 같은 입력도 매번 약간씩 다를 수 있음

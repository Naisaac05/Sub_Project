# Ollama repeated AI review answer paragraphs

- 발생 일시: 2026-05-12
- 영역: ai / backend / frontend
- 심각도: medium

## 증상
스마트 개념 복습의 자유 질문 답변에서 같은 설명 문단이 여러 번 반복되어 긴 AI 메시지가 표시됐다. 예를 들어 컴포넌트 생명주기와 소프트웨어 개발 생명주기 차이를 설명하는 문장이 반복됐다.

## 원인
로컬 Ollama 모델 호출에서 `num_predict`가 백엔드 설정값 기준 최대 1000까지 전달될 수 있었다. 프롬프트에는 3문장 제한이 있었지만, 로컬 모델이 종료 조건을 지키지 못하면 같은 패턴을 계속 생성했다. 또한 반복 억제 옵션과 응답 후처리가 없어 반복 문단이 그대로 화면에 저장됐다.

관련 파일:
- ai/app/service.py:10
- ai/app/service.py:109
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java:149
- backend/src/main/resources/application.yml:77
- backend/src/main/resources/application.yml:83

## 해결 방법
Python AI와 백엔드 Ollama 직접 호출 모두에서 생성 길이와 반복을 제한했다.

수정 내용:
- mode별 `num_predict` 상한 적용 (`free-question` 140, 그 외 90)
- Ollama 옵션에 `repeat_penalty`, `repeat_last_n`, `stop` 추가
- 모델 출력 후 반복 문단 제거
- 최종 응답을 2~3문장 수준으로 자르는 후처리 추가
- 프롬프트에 "Stop after..." 규칙 추가

수정 파일:
- ai/app/service.py
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java

## 재발 방지 / 메모
로컬 소형 모델은 문장 수 지시만으로는 생성을 안정적으로 멈추지 못할 수 있다. 프롬프트 제한, `num_predict` 제한, 반복 억제 옵션, 서버 후처리를 함께 적용해야 한다. Python AI 서버는 수정 후 재시작해야 새 옵션이 반영된다.

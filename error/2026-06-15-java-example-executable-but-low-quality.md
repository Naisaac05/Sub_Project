# Java 예시가 실행되지만 동작 검증 품질 점수가 낮음

- 발생 일시: 2026-06-15
- 영역: AI validation
- 심각도: medium

## 증상
`java-loop-control`, `java-functional-interface`, `java-stream`, `java-reflection` 예시는 실제 컴파일과 실행에 성공했지만 `example_quality`가 0.5로 계산되어 `PATCHES_READY` 승격에서 보류됐다.

## 원인
기존 예시는 assertion 결과만 확인하고 실제 객체 문맥, 상태 변경, 조회 동작을 충분히 드러내지 않았다. 실행 성공 여부와 개념 동작을 검증하는 예시 품질은 별도 기준이므로 실행 성공만으로 승인할 수 없었다.

## 해결 방법
각 예시가 핵심 개념의 실제 동작을 관찰하도록 수정했다.

- `switch`: 분기 결과를 목록에 저장하고 조회
- 함수형 인터페이스: `Predicate`를 실제 데이터에 호출
- Stream: `collect()`로 최종 결과를 생성하고 조회
- Reflection: 런타임에 메서드를 조회하고 동적 호출

관련 파일: `ai/app/scripts/prepare_factchecked_next20_drafts.py:34`

## 재발 방지·메모
예시 승인 시 컴파일·실행 성공과 함께 `example_quality >= 0.7`, 실제 상태 변화, 런타임 결과 관찰 여부를 모두 확인한다.

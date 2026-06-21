---
id: cs-blocking-io
category: cs
difficulty: intermediate
version: general
last_updated: 2026-06-01
description: "Blocking IO와 Non-Blocking IO 핵심 개념 정리 및 동작 원리"

---

# Blocking IO와 Non-Blocking IO

## 핵심 설명
Blocking IO는 입출력이 끝날 때까지 호출한 스레드가 멈춰서(블록) 기다리는 방식이고, Non-Blocking IO는 기다리지 않고 즉시 반환해 그 스레드가 다른 일을 할 수 있는 방식이다.

## 대표 해결
- Blocking은 요청 하나당 스레드 하나를 쓰는 모델(예: 기본 Tomcat)에 적합하고 코드가 단순하다.
- Non-Blocking은 적은 수의 스레드로 많은 연결을 처리(예: Netty, Spring WebFlux)해 CPU 활용률을 높인다.

## 흔한 오해
- Blocking으로 기다리는 동안 스레드가 CPU를 계속 쓴다고 오해하기 쉽다. 실제로는 대기(waiting) 상태라 CPU를 쓰지 않고 놀린다. 그래서 동시 요청이 많으면 Tomcat은 그만큼 스레드를 많이 띄운다.
- Non-Blocking이 항상 더 빠르다고 오해하기 쉽다. 구현 복잡도가 높고, CPU를 많이 쓰는 작업에는 이점이 작다.

## 평가 키워드
- 블로킹 대기
- 논블로킹
- 요청당 스레드
- CPU 활용률

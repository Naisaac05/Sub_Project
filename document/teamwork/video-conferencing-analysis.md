# DevMatch 화상 회의 기능 — 무료 구현 방안 분석서

> **작성일:** 2026-04-08
> **목적:** 포트폴리오 프로젝트에서 화상 회의를 무료로 구현하는 방법 비교 및 결정
> **현재 상태:** Phase 4에서 Google Calendar + Meet 연동 코드 작성됨 (스텁 모드 동작 중)

---

## 1. 현재 상태 분석

### 기존 구현 현황

| 파일 | 상태 | 설명 |
|------|------|------|
| `GoogleCalendarConfig.java` | 작성 완료 | Service Account 기반, credentials 없으면 null 반환 |
| `GoogleCalendarService.java` | 작성 완료 | Calendar 이벤트 + Meet 링크 생성, null이면 스텁 모드 |
| `SessionService.java` | 작성 완료 | 세션 생성 시 GoogleCalendarService 호출 |
| `MentoringSession.java` | 작성 완료 | `meetLink`, `calendarEventId` 필드 보유 |

### 문제점

Google Meet 링크를 자동 생성하려면 **Google Workspace 유료 계정**(월 $7.20~/사용자)이 필요합니다.
무료 Google 계정의 Service Account로는 `conferenceData`를 통한 Meet 링크 자동 생성이 불가능합니다.
현재는 credentials 파일이 없어 **스텁 모드**(로그만 출력)로 동작 중입니다.

---

## 2. 무료 화상 회의 후보 비교

### 2.1 비교표

| 항목 | Jitsi Meet | WebRTC 직접 구현 | Daily.co 무료 | Discord |
|------|-----------|-----------------|-------------|---------|
| **비용** | 완전 무료 | 완전 무료 | 무료 (월 10,000분) | 완전 무료 |
| **API 키 필요** | 불필요 | 불필요 | 필요 | 필요 (봇) |
| **LMS 임베딩** | iframe 가능 | 직접 구현 | iframe 가능 | 불가 |
| **링크 생성** | URL 규칙만으로 생성 | 시그널링 서버 필요 | REST API 호출 | 초대 링크 |
| **녹화** | 무료 (셀프호스팅) | 직접 구현 | 무료 (클라우드) | 불가 (무료) |
| **화면 공유** | 가능 | 직접 구현 | 가능 | 가능 |
| **구현 난이도** | 낮음 | 매우 높음 | 낮음 | 중간 (봇) |
| **포트폴리오 어필** | 중상 | 최상 | 중 | 하 |
| **안정성** | 높음 (운영 검증) | 낮음 (직접 관리) | 높음 | 높음 |

### 2.2 각 옵션 상세

#### A. Jitsi Meet — 실용적 무료 솔루션

```
방식: meet.jit.si 공용 서버 사용 (또는 Docker로 셀프 호스팅)
링크 생성: https://meet.jit.si/{room-name}
인증: 불필요
임베딩: <iframe src="https://meet.jit.si/{room}" allow="camera;microphone" />
```

**장점:**
- API 키, OAuth, 인증 과정 전혀 불필요
- URL만 만들면 즉시 사용 가능
- Jitsi IFrame API로 LMS 페이지 내에 화상 회의 임베딩 가능
- 오픈소스(Apache 2.0), 셀프 호스팅하면 포트폴리오에 Docker 활용도 추가

**단점:**
- 공용 서버(meet.jit.si) 사용 시 품질이 접속자 수에 따라 변동
- 방 이름을 유추하면 외부인 접근 가능 (비밀번호 설정으로 해결)

**포트폴리오 어필 포인트:**
- 외부 서비스 연동 + iframe 임베딩 경험
- (선택) Docker로 Jitsi 셀프 호스팅 → 인프라 역량 어필

---

#### B. WebRTC 직접 구현 — 최고의 기술 어필

```
방식: WebRTC API + 시그널링 서버 직접 구현
기술 스택: WebRTC (브라우저 API) + WebSocket (시그널링) + STUN/TURN 서버
```

**장점:**
- 네트워크/미디어 스트리밍의 핵심 기술을 직접 다룸 → 기술 깊이 최상
- 외부 의존 없이 완전한 자체 솔루션
- 면접에서 "WebRTC를 직접 구현했다"는 매우 강력한 어필 포인트

**단점:**
- 구현량이 매우 많음 (시그널링 서버, STUN/TURN, 미디어 핸들링, UI)
- TURN 서버 운영 비용 발생 가능 (NAT 환경에서 필수)
- 브라우저 호환성, 네트워크 환경별 디버깅 난이도 높음
- 프로젝트 전체 일정에 큰 부담

**필요 구성 요소:**
```
1. 시그널링 서버: Spring Boot WebSocket 또는 별도 Node.js
2. STUN 서버: Google 무료 STUN (stun:stun.l.google.com:19302)
3. TURN 서버: Metered.ca 무료 (월 500MB) 또는 coturn 셀프호스팅
4. 프론트엔드: RTCPeerConnection API, MediaStream API
5. UI: 카메라/마이크 토글, 화면 공유, 채팅
```

**포트폴리오 어필 포인트:**
- WebRTC, WebSocket, 미디어 스트리밍 기술 깊이
- P2P 통신, NAT Traversal 이해
- 실시간 시스템 설계 역량

---

#### C. Daily.co 무료 플랜 — 가장 빠른 구현

```
방식: REST API로 방 생성 → iframe 임베딩
인증: API 키 1개
무료 한도: 월 10,000분 (1:1 기준 약 83시간)
```

**장점:**
- REST API 호출 한 번으로 방 생성, 응답에 URL 포함
- 고품질 UI가 기본 제공됨
- 녹화 기능 무료 포함

**단점:**
- 외부 SaaS 의존도 높음 → 포트폴리오에서 "본인이 구현한 부분"이 적음
- 월 10,000분 초과 시 유료 전환 필요
- 서비스 종료/변경 리스크

**포트폴리오 어필 포인트:**
- 외부 API 연동 경험 (REST API, Webhook)
- 하지만 기술적 깊이 어필은 약함

---

#### D. Discord — 소통 통합 (비권장)

```
방식: Discord 봇으로 서버/채널 자동 생성, 음성 채널로 화상 회의
```

**장점:**
- Slack + 화상 회의를 하나로 통합
- 완전 무료, 안정적

**단점:**
- LMS 페이지에 임베딩 불가 → 별도 앱으로 이동해야 함
- "화상 회의를 구현했다"고 말하기 어려움 (Discord를 사용한 것일 뿐)
- 포트폴리오에서 기술적 어필이 거의 없음

---

## 3. 포트폴리오 관점 최종 평가

### 평가 기준별 점수 (5점 만점)

| 평가 기준 | Jitsi Meet | WebRTC 직접 | Daily.co | Discord |
|-----------|:---------:|:-----------:|:--------:|:-------:|
| 기술적 깊이 (면접 어필) | 3 | 5 | 2 | 1 |
| 구현 난이도 (투자 시간) | 쉬움 | 매우 어려움 | 쉬움 | 쉬움 |
| 완성도 (데모 품질) | 4 | 3 | 5 | 2 |
| 무료 유지 가능성 | 5 | 4 | 3 | 5 |
| 기존 코드 변경량 | 적음 | 많음 | 적음 | 중간 |
| **종합 권장도** | **1순위** | 도전 시 | 2순위 | 비권장 |

---

## 4. 권장안: Jitsi Meet (MVP) + WebRTC (고도화)

### 전략

```
Phase 6 MVP  →  Jitsi Meet 연동 (1~2일)
  - 빠르게 동작하는 화상 회의 기능 완성
  - LMS 페이지 내 iframe 임베딩
  - 포트폴리오 데모에서 실제 동작 시연 가능

Phase 7 고도화  →  WebRTC 직접 구현으로 교체 (선택, 2~3주)
  - Jitsi를 자체 WebRTC 솔루션으로 교체
  - 면접에서 "처음에는 Jitsi로 빠르게 구현하고, 이후 WebRTC로 직접 교체했다"
  → 실용적 판단력 + 기술적 깊이 모두 어필
```

### 이 전략이 포트폴리오에 좋은 이유

1. **실용적 판단력 증명**: "MVP에서는 검증된 솔루션으로 빠르게, 이후 기술적 도전"
2. **동작하는 데모 확보**: Jitsi 연동은 1~2일이면 완성 → 데모 품질 보장
3. **기술 깊이는 선택적 확장**: 여유가 있으면 WebRTC로 교체하여 깊이 추가
4. **면접 스토리**: 기술 선택의 근거(비용, 일정, 품질)를 논리적으로 설명 가능

---

## 5. Jitsi Meet 구현 설계

### 5.1 기존 코드 변경 범위

| 파일 | 변경 내용 |
|------|----------|
| `GoogleCalendarService.java` | 유지 (Calendar 일정 등록은 그대로, Meet 링크 생성만 분리) |
| `SessionService.java` | Meet 링크 생성 로직을 Jitsi로 교체 |
| **신규** `JitsiMeetService.java` | Jitsi 방 URL 생성 + 설정 |
| `MentoringSession.java` | 변경 없음 (`meetLink` 필드 그대로 활용) |
| `build.gradle` | 변경 없음 (추가 의존성 불필요) |

### 5.2 JitsiMeetService 설계

```java
@Service
public class JitsiMeetService {

    private static final String JITSI_BASE_URL = "https://meet.jit.si";

    /**
     * 멘토링 세션용 Jitsi Meet 방 URL을 생성합니다.
     *
     * 방 이름 규칙: devmatch-{matchingId}-{sessionDate}-{uuid8자리}
     * 예: https://meet.jit.si/devmatch-42-20260410-a1b2c3d4
     *
     * UUID를 포함하여 방 이름 유추에 의한 외부 접근을 방지합니다.
     */
    public String generateMeetLink(Long matchingId, LocalDate sessionDate) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String roomName = String.format("devmatch-%d-%s-%s",
                matchingId,
                sessionDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                uuid);
        return JITSI_BASE_URL + "/" + roomName;
    }
}
```

### 5.3 SessionService 변경 흐름

```
기존 흐름:
  세션 생성 → GoogleCalendarService.createMentoringEvent()
           → Calendar 이벤트 + Meet 링크 동시 생성 (스텁 모드)

변경 후 흐름:
  세션 생성 → JitsiMeetService.generateMeetLink()
           → meetLink 즉시 저장 (항상 성공, 외부 API 호출 없음)
           → GoogleCalendarService.createMentoringEvent() (선택적, Calendar 일정만)
```

### 5.4 프론트엔드 — LMS 임베딩

```tsx
// /lms/sessions/[id]/meet — 화상 회의 페이지

// 옵션 A: 외부 링크 (간단)
<a href={session.meetLink} target="_blank">화상 회의 참여하기</a>

// 옵션 B: iframe 임베딩 (LMS 내에서 바로 참여)
<iframe
  src={session.meetLink}
  allow="camera; microphone; display-capture; fullscreen"
  style={{ width: '100%', height: '600px', border: 'none' }}
/>

// 옵션 C: Jitsi IFrame API (가장 풍부한 제어)
// Jitsi가 제공하는 JavaScript API로 회의 상태 제어 가능
// - 참여/퇴장 이벤트 감지 → 출석 자동 체크에 활용 가능
// - 회의 시간 측정 → 세션 자동 완료 처리에 활용 가능
```

### 5.5 Jitsi IFrame API 활용 (포트폴리오 차별점)

```
Jitsi IFrame API를 활용하면 단순 링크 제공을 넘어
LMS와 화상 회의를 깊이 통합할 수 있습니다:

1. 참여자 감지 → 멘토/멘티 입장 시 출석 자동 체크
2. 회의 종료 감지 → 세션 상태 자동 COMPLETED 전환
3. 회의 시간 측정 → 실제 멘토링 시간 기록
4. 화면 공유 상태 → 코드 리뷰 진행 중 표시

이런 통합은 "단순히 외부 서비스를 붙인 것"이 아니라
"비즈니스 로직과 연동한 것"으로 포트폴리오에서 차별화됩니다.
```

---

## 6. (선택) WebRTC 직접 구현 시 구조

Jitsi MVP 이후 도전할 경우의 설계입니다.

### 6.1 아키텍처

```
┌──────────┐     WebSocket      ┌──────────────────┐
│  멘티     │ ←──(시그널링)──→   │  Spring Boot     │
│  브라우저  │                    │  WebSocket 서버   │
└────┬─────┘                    └──────────────────┘
     │                                    ↑
     │ WebRTC (P2P 미디어 스트림)            │ WebSocket (시그널링)
     │                                    │
┌────┴─────┐                    ┌────────┴─────────┐
│  멘토     │ ←──(시그널링)──→   │  STUN/TURN 서버   │
│  브라우저  │                    │  (Google 무료 STUN)│
└──────────┘                    └──────────────────┘
```

### 6.2 필요 구현 목록

| # | 구현 항목 | 기술 | 난이도 |
|---|----------|------|--------|
| 1 | 시그널링 서버 | Spring WebSocket (STOMP) | 중 |
| 2 | SDP Offer/Answer 교환 | WebRTC API | 중 |
| 3 | ICE Candidate 교환 | WebRTC + STUN | 중 |
| 4 | 미디어 스트림 핸들링 | getUserMedia, RTCPeerConnection | 중상 |
| 5 | 카메라/마이크 토글 UI | React 상태 관리 | 하 |
| 6 | 화면 공유 | getDisplayMedia | 중 |
| 7 | TURN 서버 (NAT 통과) | coturn 또는 Metered.ca 무료 | 중 |
| 8 | 연결 상태 관리 | ICE 상태 감지, 재연결 | 상 |

### 6.3 무료 STUN/TURN 서버

| 서비스 | 종류 | 무료 범위 | 비고 |
|--------|------|----------|------|
| Google STUN | STUN | 무제한 | `stun:stun.l.google.com:19302` |
| Metered.ca | TURN | 월 500MB | 포트폴리오 데모용으로 충분 |
| coturn (셀프호스팅) | STUN+TURN | 서버 비용만 | Docker로 배포 가능 |

---

## 7. 최종 결정 요약

| 항목 | 결정 |
|------|------|
| **MVP 화상 회의** | Jitsi Meet (meet.jit.si 공용 서버) |
| **비용** | 완전 무료 |
| **구현 기간** | 1~2일 |
| **기존 코드 영향** | `meetLink` 필드 그대로 활용, Google Calendar 코드 유지 |
| **포트폴리오 차별점** | Jitsi IFrame API로 LMS-화상 회의 깊은 통합 |
| **고도화 (선택)** | WebRTC 직접 구현으로 교체 |

### Google Calendar 코드 처리

기존 `GoogleCalendarService.java`와 `GoogleCalendarConfig.java`는 **삭제하지 않고 유지**합니다.

- Calendar 일정 등록 기능 자체는 유효 (Google Workspace 환경에서 동작)
- Meet 링크 생성만 Jitsi로 분리
- 포트폴리오에서 "Google API 연동 경험"으로 어필 가능
- 스텁 모드가 이미 잘 설계되어 있어 credentials 없이도 에러 없이 동작

---

## 8. 팀 논의 필요 사항

1. **Jitsi 공용 서버 vs 셀프 호스팅:** MVP는 meet.jit.si, 배포 시 Docker로 셀프 호스팅할지
2. **Jitsi IFrame API 범위:** 출석 자동 체크까지 구현할지, 링크 제공만 할지
3. **WebRTC 직접 구현 여부:** Phase 7에서 도전할지, Jitsi로 확정할지
4. **Google Calendar 코드:** 유지 vs 정리 (현재 유지 권장)

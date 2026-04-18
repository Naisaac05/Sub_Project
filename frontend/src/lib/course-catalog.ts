export type CourseSection = {
  title: string;
  summary: string;
  bullets: string[];
};

export type CourseReview = {
  authorId: number;
  authorName: string;
  rating: number;
  content: string;
  createdAt: string;
};

export type CourseCatalogItem = {
  slug: string;
  title: string;
  shortTitle: string;
  categoryLabel: string;
  iconKey:
    | 'server'
    | 'database'
    | 'layout'
    | 'smartphone'
    | 'cloud'
    | 'brain'
    | 'layers';
  summary: string;
  headline: string;
  description: string;
  level: string;
  durationLabel: string;
  matchKeywords: string[];
  outcomes: string[];
  sections: CourseSection[];
  availability: 'open' | 'upcoming';
  comingSoonNote?: string;
};

const OPEN_COURSES: CourseCatalogItem[] = [
  {
    slug: 'java-backend',
    title: 'Java Backend + AI',
    shortTitle: 'Java Backend',
    categoryLabel: 'Backend',
    iconKey: 'server',
    summary: 'Spring Boot와 대규모 서비스 설계를 함께 다루는 백엔드 취업 집중 코스입니다.',
    headline: '실무형 Java 백엔드 역량을 프로젝트와 코드 리뷰로 단단하게 만듭니다.',
    description:
      '기본 문법 정리에서 끝나지 않고 Spring Boot, 데이터 모델링, 인증, 캐시, 메시징, 운영 관점까지 한 흐름으로 묶어 학습합니다. 포트폴리오와 면접 답변까지 연결되는 구조로 설계했습니다.',
    level: '기초 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['java', 'backend', 'spring', 'kotlin'],
    outcomes: [
      '실무형 Spring Boot API 서버 설계',
      'JPA와 QueryDSL 기반 데이터 처리 역량 강화',
      '캐시·비동기·배포까지 포트폴리오 완성',
    ],
    sections: [
      {
        title: '기초 체력',
        summary: 'Java, 객체지향, 테스트 코드를 함께 다지며 이후 과정의 공통 기초를 만듭니다.',
        bullets: ['Java 문법과 객체지향 재정리', '테스트 가능한 코드 구조', '예외 처리와 리팩터링 습관'],
      },
      {
        title: '실무 백엔드',
        summary: '현업에서 자주 만나는 API 서버 패턴을 직접 만들며 설계 감각을 익힙니다.',
        bullets: ['Spring Boot REST API', 'JPA/QueryDSL 데이터 접근', '인증·인가와 보안 기본기'],
      },
      {
        title: '고도화 프로젝트',
        summary: '캐시, 메시징, 운영 관점을 더해 한 단계 깊은 포트폴리오로 확장합니다.',
        bullets: ['Redis 캐시와 성능 개선', 'Kafka 등 비동기 흐름 이해', 'Docker 기반 배포와 운영 점검'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'node-backend',
    title: 'Node.js Backend + AI',
    shortTitle: 'Node.js Backend',
    categoryLabel: 'Backend',
    iconKey: 'database',
    summary: 'TypeScript와 Node.js로 빠르고 안정적인 API 서버를 만드는 과정입니다.',
    headline: 'Node.js 특유의 비동기 흐름을 이해하고 실무형 서버 구조로 연결합니다.',
    description:
      'Express 또는 NestJS 기반 서버를 만들며 타입 안전성, 모듈 구조, 데이터 접근, 실시간 처리까지 폭넓게 다룹니다. 프로젝트를 통해 실제 협업에 가까운 개발 루틴까지 익힙니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['node', 'backend', 'typescript', 'express', 'nest'],
    outcomes: [
      'TypeScript 중심 서버 아키텍처 이해',
      'NestJS 또는 Express 기반 API 설계',
      '실시간 기능과 운영 포인트 경험',
    ],
    sections: [
      {
        title: '언어와 런타임',
        summary: 'TypeScript와 Node.js 런타임의 핵심 개념을 먼저 정리합니다.',
        bullets: ['타입 설계와 추론', '이벤트 루프와 비동기 처리', '에러 흐름과 로깅 습관'],
      },
      {
        title: '서비스 구현',
        summary: 'API 서버 구조를 세우고 인증, 데이터 접근, 검증을 실전처럼 구성합니다.',
        bullets: ['NestJS/Express 서버 구조', 'ORM 활용과 스키마 설계', 'JWT 인증과 유효성 검증'],
      },
      {
        title: '확장과 운영',
        summary: '서비스 확장 시 꼭 만나는 실시간·캐시·배포 주제를 다룹니다.',
        bullets: ['Socket 기반 실시간 처리', 'Redis 캐시와 Pub/Sub', '배포 자동화와 모니터링 기초'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'python-backend',
    title: 'Python Backend + AI',
    shortTitle: 'Python Backend',
    categoryLabel: 'Backend',
    iconKey: 'brain',
    summary: 'FastAPI와 Django를 바탕으로 AI 서비스와 잘 맞는 백엔드 흐름을 설계합니다.',
    headline: 'Python 웹 백엔드와 AI 연동을 함께 가져가는 실무형 트랙입니다.',
    description:
      'API 서버와 데이터 처리, 비동기 작업, 모델 서빙 연결까지 Python이 강한 영역을 자연스럽게 묶습니다. AI 프로젝트를 백엔드 포트폴리오로 확장하고 싶은 분께 맞습니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['python', 'backend', 'fastapi', 'django', 'ai'],
    outcomes: [
      'FastAPI 중심 서비스 설계 역량',
      '비동기 작업과 배치 처리 경험',
      'AI 서빙 연동 포트폴리오 완성',
    ],
    sections: [
      {
        title: 'Python 서비스 기초',
        summary: '언어 특성과 웹 프레임워크의 차이를 실제 예제로 이해합니다.',
        bullets: ['Python 문법과 패키지 구조', 'FastAPI와 Django 비교', '요청·응답 흐름과 검증'],
      },
      {
        title: '데이터와 비동기',
        summary: '현업 서비스에서 필요한 배치, 큐, 저장 전략을 익힙니다.',
        bullets: ['ORM과 스키마 설계', 'Celery/Redis 작업 처리', '파일·데이터 파이프라인 구성'],
      },
      {
        title: 'AI 연동',
        summary: '모델 서빙이나 외부 AI API를 안전하게 붙이는 패턴을 다룹니다.',
        bullets: ['모델 추론 API 연결', '요청 비용과 지연 시간 관리', '운영 가능한 AI 기능 설계'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'frontend',
    title: 'Frontend + AI',
    shortTitle: 'Frontend',
    categoryLabel: 'Frontend',
    iconKey: 'layout',
    summary: 'React와 Next.js 기반으로 화면 구현부터 성능, 사용자 경험까지 끌어올리는 과정입니다.',
    headline: '보이는 화면을 넘어서 서비스 완성도를 만드는 프론트엔드 코스입니다.',
    description:
      '컴포넌트 설계, 상태 관리, 접근성, 성능, 배포를 함께 다루며 채용 시장에서 설명 가능한 프론트엔드 포트폴리오를 만듭니다. AI 기능을 UI에 녹이는 패턴도 다룹니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['frontend', 'react', 'next', 'ui', 'web'],
    outcomes: [
      'Next.js 기반 실전 서비스 구현',
      '상태 관리와 렌더링 최적화 역량 강화',
      'UX 품질이 드러나는 포트폴리오 제작',
    ],
    sections: [
      {
        title: '기본기와 설계',
        summary: 'React 기본기와 컴포넌트 설계 원칙을 탄탄히 쌓습니다.',
        bullets: ['컴포넌트 분리 기준', '상태 관리와 데이터 흐름', '타입 안전성과 폴더 구조'],
      },
      {
        title: '서비스 구현',
        summary: 'Next.js 기반으로 실제 서비스 화면과 API 연동을 구성합니다.',
        bullets: ['App Router와 서버/클라이언트 경계', '폼과 비동기 처리', '재사용 가능한 UI 패턴'],
      },
      {
        title: '완성도 개선',
        summary: '성능과 사용자 경험, 배포 품질까지 끝까지 챙깁니다.',
        bullets: ['접근성과 반응형 UI', '성능 측정과 최적화', '애니메이션과 인터랙션 품질 개선'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'android',
    title: 'Android + AI',
    shortTitle: 'Android',
    categoryLabel: 'Mobile',
    iconKey: 'smartphone',
    summary: 'Kotlin과 Android 현대 개발 스택으로 앱 구조와 사용자 경험을 함께 끌어올립니다.',
    headline: 'Compose 중심 안드로이드 앱 개발을 실무 포트폴리오 수준으로 정리합니다.',
    description:
      'Kotlin, Jetpack Compose, 비동기 처리, 아키텍처 설계, 로컬 저장, 배포 전 점검까지 안드로이드 개발의 핵심 흐름을 프로젝트로 연결합니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['android', 'kotlin', 'mobile', 'app'],
    outcomes: [
      'Compose 기반 앱 UI 구현',
      'MVVM 계열 구조와 상태 처리 이해',
      '실서비스형 앱 포트폴리오 제작',
    ],
    sections: [
      {
        title: 'Kotlin 기본기',
        summary: '안드로이드 개발에 필요한 Kotlin 문법과 비동기 처리 감각을 익힙니다.',
        bullets: ['Kotlin 문법과 컬렉션', 'Coroutines와 Flow', '안전한 상태 관리 습관'],
      },
      {
        title: '앱 구조',
        summary: 'Compose와 아키텍처 패턴을 사용해 유지보수 가능한 앱을 만듭니다.',
        bullets: ['Jetpack Compose UI', 'MVVM/MVI 흐름', '의존성 주입과 테스트 기초'],
      },
      {
        title: '실전 완성',
        summary: '저장소, 작업 처리, 성능 점검까지 서비스형 앱으로 다듬습니다.',
        bullets: ['Room 데이터 저장', 'WorkManager 백그라운드 작업', '성능 점검과 배포 체크리스트'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'ios',
    title: 'iOS + AI',
    shortTitle: 'iOS',
    categoryLabel: 'Mobile',
    iconKey: 'smartphone',
    summary: 'Swift와 SwiftUI로 iOS 앱의 구조, 상태 관리, 성능을 차근차근 완성합니다.',
    headline: 'SwiftUI 기반 iOS 개발을 포트폴리오 수준까지 끌어올리는 과정입니다.',
    description:
      'Swift 기본기부터 UI 구성, 아키텍처, 로컬 저장, 메모리 이슈와 성능 점검까지 iOS 개발에 필요한 필수 주제를 실제 앱 제작과 함께 다룹니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['ios', 'swift', 'mobile', 'app'],
    outcomes: [
      'SwiftUI 중심 앱 개발 역량 확보',
      '상태 관리와 아키텍처 설계 감각 강화',
      '면접에서 설명 가능한 iOS 프로젝트 완성',
    ],
    sections: [
      {
        title: 'Swift 기초',
        summary: 'Swift 문법과 iOS 생명주기를 실제 예제로 익힙니다.',
        bullets: ['Swift 문법과 타입 시스템', '비동기 처리와 Combine 감각', '뷰 생명주기 이해'],
      },
      {
        title: '앱 구조',
        summary: 'SwiftUI와 아키텍처 패턴으로 유지보수 가능한 구조를 설계합니다.',
        bullets: ['SwiftUI 화면 구성', 'MVVM/TCA 계열 구조', '네트워크와 로컬 상태 연결'],
      },
      {
        title: '품질 개선',
        summary: '앱의 완성도를 좌우하는 성능과 디버깅 영역까지 다룹니다.',
        bullets: ['메모리 관리와 성능 점검', '에러 처리와 사용자 경험 개선', '실기기 테스트와 배포 준비'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'flutter',
    title: 'Flutter + AI',
    shortTitle: 'Flutter',
    categoryLabel: 'Mobile',
    iconKey: 'layers',
    summary: '하나의 코드베이스로 멀티 플랫폼 앱을 만드는 Flutter 실전 과정입니다.',
    headline: 'Flutter의 생산성과 아키텍처 감각을 함께 키우는 크로스플랫폼 트랙입니다.',
    description:
      'Dart, 위젯 구조, 상태 관리, 네이티브 연동, 배포 흐름까지 다루며 앱 서비스 포트폴리오를 빠르게 완성합니다. 크로스플랫폼 전략이 필요한 분에게 잘 맞습니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['flutter', 'dart', 'mobile', 'cross'],
    outcomes: [
      'Dart와 Flutter 위젯 구조 이해',
      '상태 관리 패턴 실전 적용',
      '멀티 플랫폼 앱 포트폴리오 완성',
    ],
    sections: [
      {
        title: 'Flutter 기본기',
        summary: 'Dart와 위젯 구조를 기반으로 안정적인 앱 개발 리듬을 만듭니다.',
        bullets: ['Dart 핵심 문법', '위젯 트리와 렌더링 개념', '레이아웃과 반응형 UI'],
      },
      {
        title: '앱 상태 관리',
        summary: '프로젝트 규모에 맞는 상태 관리 전략을 익힙니다.',
        bullets: ['Provider/Riverpod/Bloc 이해', '폼과 비동기 요청 처리', '재사용 가능한 화면 구조'],
      },
      {
        title: '플랫폼 확장',
        summary: '네이티브 기능 연결과 배포 흐름까지 함께 다룹니다.',
        bullets: ['플랫폼 채널과 외부 SDK 연결', '앱 성능과 디버깅', '스토어 배포 준비'],
      },
    ],
    availability: 'open',
  },
  {
    slug: 'react-native',
    title: 'React Native + AI',
    shortTitle: 'React Native',
    categoryLabel: 'Mobile',
    iconKey: 'layers',
    summary: 'React 기반 개발 경험을 모바일 앱으로 확장하고 싶은 분을 위한 과정입니다.',
    headline: '웹 경험을 살려 모바일 서비스 개발까지 확장하는 React Native 코스입니다.',
    description:
      'React Native의 구조, 네이티브 브리지 감각, 상태 관리, 퍼포먼스, 배포 준비를 프로젝트와 함께 익힙니다. 웹과 앱을 함께 이해하는 포트폴리오를 만들 수 있습니다.',
    level: '입문 이상',
    durationLabel: '4개월 집중 과정',
    matchKeywords: ['react native', 'mobile', 'react', 'app'],
    outcomes: [
      'React Native 핵심 구조 이해',
      '모바일 앱 상태 관리와 성능 대응',
      '웹-앱 연계 프로젝트 제작',
    ],
    sections: [
      {
        title: '기본 구조',
        summary: 'React Native 개발 흐름과 웹과의 차이를 체감하며 익힙니다.',
        bullets: ['React Native 실행 구조', '네비게이션과 화면 흐름', 'API 통신과 상태 연결'],
      },
      {
        title: '모바일 UX',
        summary: '모바일 앱다운 경험을 만드는 데 필요한 요소를 다룹니다.',
        bullets: ['제스처와 애니메이션', '리스트와 폼 최적화', '기기별 대응 포인트'],
      },
      {
        title: '고급 주제',
        summary: '네이티브 연동과 성능 이슈까지 포함해 실제 서비스 수준으로 확장합니다.',
        bullets: ['네이티브 모듈 연결', '성능 병목 분석', '배포와 QA 체크리스트'],
      },
    ],
    availability: 'open',
  },
];

const UPCOMING_COURSES: CourseCatalogItem[] = [
  {
    slug: 'devops',
    title: 'DevOps Engineering',
    shortTitle: 'DevOps',
    categoryLabel: 'Infra',
    iconKey: 'cloud',
    summary: 'CI/CD, 클라우드, 컨테이너, 관측성을 중심으로 준비 중인 트랙입니다.',
    headline: 'DevOps 트랙은 현재 커리큘럼 고도화 중입니다.',
    description: '실서비스 배포와 운영 자동화에 초점을 맞춘 과정으로 곧 오픈 예정입니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['devops', 'infra', 'cloud'],
    outcomes: ['배포 자동화 이해', '클라우드 운영 감각 강화', '관측성과 안정성 지표 이해'],
    sections: [{ title: '오픈 준비 중', summary: '현재 커리큘럼과 멘토 구성을 정리하고 있습니다.', bullets: ['클라우드 운영', 'CI/CD 자동화', '관측성과 장애 대응'] }],
    availability: 'upcoming',
    comingSoonNote: 'DevOps 트랙은 곧 공개될 예정입니다. 먼저 루트 페이지에서 오픈된 과정부터 확인해 주세요.',
  },
  {
    slug: 'data-engineer',
    title: 'Data Engineer + AI',
    shortTitle: 'Data Engineer',
    categoryLabel: 'Data',
    iconKey: 'database',
    summary: '데이터 파이프라인과 저장·처리 구조를 다루는 과정으로 준비 중입니다.',
    headline: '데이터 엔지니어 트랙은 오픈 준비 중입니다.',
    description: 'ETL, 데이터 웨어하우스, 스트리밍과 운영 관점을 묶은 커리큘럼으로 구성될 예정입니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['data', 'engineer', 'pipeline'],
    outcomes: ['데이터 파이프라인 설계 감각', '배치와 스트리밍 처리 이해', '데이터 플랫폼 운영 기초'],
    sections: [{ title: '오픈 준비 중', summary: '현재 멘토와 세부 커리큘럼을 정리 중입니다.', bullets: ['배치 처리', '스트리밍', '데이터 저장 전략'] }],
    availability: 'upcoming',
    comingSoonNote: '데이터 엔지니어 과정은 곧 공개될 예정입니다.',
  },
  {
    slug: 'ml-engineer',
    title: 'ML Engineer',
    shortTitle: 'ML Engineer',
    categoryLabel: 'AI',
    iconKey: 'brain',
    summary: '모델 학습과 서빙, MLOps까지 묶어 다루는 트랙을 준비 중입니다.',
    headline: 'ML Engineer 트랙은 현재 준비 중입니다.',
    description: '모델 개발 이후 배포와 운영까지 연결하는 흐름 중심으로 구성될 예정입니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['ml', 'machine learning', 'ai'],
    outcomes: ['학습 파이프라인 이해', '모델 서빙 구조 이해', 'MLOps 기초 개념 정리'],
    sections: [{ title: '오픈 준비 중', summary: '커리큘럼과 멘토 풀을 정리 중입니다.', bullets: ['모델 학습', '서빙', 'MLOps'] }],
    availability: 'upcoming',
    comingSoonNote: 'ML Engineer 과정은 준비가 끝나는 대로 공개됩니다.',
  },
  {
    slug: 'game-server',
    title: 'Game Server',
    shortTitle: 'Game Server',
    categoryLabel: 'Backend',
    iconKey: 'server',
    summary: '실시간 서버 구조와 게임 서버 운영을 중심으로 한 트랙입니다.',
    headline: 'Game Server 과정은 현재 오픈 준비 중입니다.',
    description: '실시간 처리, 상태 동기화, 운영 이슈까지 묶은 고난도 트랙으로 준비하고 있습니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['game', 'server', 'backend'],
    outcomes: ['실시간 서버 구조 이해', '상태 동기화 기초', '멀티플레이 운영 이슈 이해'],
    sections: [{ title: '오픈 준비 중', summary: '커리큘럼 공개 전입니다.', bullets: ['실시간 처리', '세션 관리', '운영 안정성'] }],
    availability: 'upcoming',
    comingSoonNote: '게임 서버 트랙은 추후 순차적으로 오픈될 예정입니다.',
  },
  {
    slug: 'short-term',
    title: 'Short-Term Career Sprint',
    shortTitle: '단기 커리어',
    categoryLabel: 'Career',
    iconKey: 'layout',
    summary: '짧은 기간 안에 이직·취업 준비를 집중 점검하는 스프린트형 코스입니다.',
    headline: '단기 커리어 스프린트는 현재 준비 중입니다.',
    description: '포트폴리오 점검과 면접 대비를 짧고 밀도 있게 진행하는 형태로 준비하고 있습니다.',
    level: '경력자',
    durationLabel: '오픈 예정',
    matchKeywords: ['career', 'job', 'interview'],
    outcomes: ['이력서 점검', '포트폴리오 개선', '면접 전략 수립'],
    sections: [{ title: '오픈 준비 중', summary: '짧고 강한 집중 코스로 구성 중입니다.', bullets: ['이력서', '포트폴리오', '면접'] }],
    availability: 'upcoming',
    comingSoonNote: '단기 스프린트 과정은 준비 중입니다.',
  },
  {
    slug: 'firststep',
    title: 'First Step Backend',
    shortTitle: 'First Step',
    categoryLabel: 'Backend',
    iconKey: 'layout',
    summary: '완전 입문자를 위한 첫 개발 코스로 준비 중인 과정입니다.',
    headline: 'First Step 과정은 기초 입문용으로 준비 중입니다.',
    description: '비전공자와 완전 초심자가 첫 프로젝트를 완주할 수 있도록 설계하는 트랙입니다.',
    level: '완전 입문',
    durationLabel: '오픈 예정',
    matchKeywords: ['backend', 'java', 'entry'],
    outcomes: ['개발 입문 기초 확립', '첫 포트폴리오 완성', '학습 루틴 정착'],
    sections: [{ title: '오픈 준비 중', summary: '입문자 전용 흐름으로 구성 중입니다.', bullets: ['기초 문법', '프로젝트 입문', '학습 습관'] }],
    availability: 'upcoming',
    comingSoonNote: 'First Step 과정은 입문자용으로 별도 준비 중입니다.',
  },
  {
    slug: 'distributed-lock',
    title: 'Distributed Lock Deep Dive',
    shortTitle: '분산 락',
    categoryLabel: 'Backend',
    iconKey: 'cloud',
    summary: '짧고 깊게 특정 주제를 파고드는 Deep Dive 시리즈입니다.',
    headline: '분산 락 Deep Dive는 현재 준비 중입니다.',
    description: '실전 문제 해결형 특강으로 공개될 예정입니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['backend', 'distributed', 'lock'],
    outcomes: ['동시성 문제 이해', '락 전략 비교', '실전 대응 감각 강화'],
    sections: [{ title: '오픈 준비 중', summary: '특정 기술을 짧고 깊게 다루는 시리즈입니다.', bullets: ['동시성', '락 전략', '실전 사례'] }],
    availability: 'upcoming',
    comingSoonNote: 'Deep Dive 특강 시리즈는 순차적으로 오픈됩니다.',
  },
  {
    slug: 'kafka',
    title: 'Kafka Deep Dive',
    shortTitle: 'Kafka',
    categoryLabel: 'Backend',
    iconKey: 'database',
    summary: 'Kafka 구조와 운영 포인트를 깊게 다루는 스페셜 트랙입니다.',
    headline: 'Kafka Deep Dive는 현재 준비 중입니다.',
    description: '실제 운영 문제를 중심으로 한 심화 특강 형태로 공개될 예정입니다.',
    level: '중급 이상',
    durationLabel: '오픈 예정',
    matchKeywords: ['kafka', 'backend', 'event'],
    outcomes: ['이벤트 기반 아키텍처 이해', 'Kafka 운영 포인트 정리', '비동기 설계 감각 강화'],
    sections: [{ title: '오픈 준비 중', summary: 'Kafka 특화 심화 과정으로 준비 중입니다.', bullets: ['브로커 구조', '컨슈머 전략', '운영 이슈'] }],
    availability: 'upcoming',
    comingSoonNote: 'Kafka Deep Dive 과정은 추후 공개됩니다.',
  },
  {
    slug: 'expert-msa',
    title: 'Kotlin/MSA Expert Track',
    shortTitle: 'Expert MSA',
    categoryLabel: 'Backend',
    iconKey: 'server',
    summary: '대규모 분산 시스템 설계와 운영을 깊게 다루는 최고급 과정입니다.',
    headline: 'Expert MSA 트랙은 고급자 대상 과정으로 준비 중입니다.',
    description: '분산 시스템 설계와 운영 경험을 더 깊게 다루는 상위 트랙입니다.',
    level: '고급',
    durationLabel: '오픈 예정',
    matchKeywords: ['msa', 'kotlin', 'backend'],
    outcomes: ['분산 시스템 설계 이해', '운영 안정성 관점 확보', '대규모 시스템 면접 대응 강화'],
    sections: [{ title: '오픈 준비 중', summary: '고급 백엔드 엔지니어 대상 커리큘럼입니다.', bullets: ['서비스 분리', '메시징', '장애 대응'] }],
    availability: 'upcoming',
    comingSoonNote: 'Expert MSA 과정은 별도 공지 후 오픈됩니다.',
  },
];

export const COURSE_CATALOG = [...OPEN_COURSES, ...UPCOMING_COURSES];

export function getCourseBySlug(slug: string) {
  return COURSE_CATALOG.find((course) => course.slug === slug);
}

export function getOpenCourses() {
  return COURSE_CATALOG.filter((course) => course.availability === 'open');
}

export function getUpcomingCourses() {
  return COURSE_CATALOG.filter((course) => course.availability === 'upcoming');
}

export function matchesCourseCategory(course: CourseCatalogItem, rawCategory: string) {
  const normalized = rawCategory.toLowerCase().replace(/\s+/g, '');
  return course.matchKeywords.some((keyword) => normalized.includes(keyword.toLowerCase().replace(/\s+/g, '')));
}

function daysUntil(date: Date) {
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diff = target.getTime() - startOfToday.getTime();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

function formatMonthLabel(date: Date) {
  return `${date.getMonth() + 1}월 시작 얼리버드`;
}

function formatDeadlineBadge(deadline: Date) {
  const diff = daysUntil(deadline);
  if (diff > 0) {
    return `마감 D-${diff}`;
  }
  if (diff === 0) {
    return '오늘 마감';
  }
  return '다음 차수 준비 중';
}

function buildMonthlyPrice(monthlyPrice: number) {
  return `${monthlyPrice.toLocaleString('ko-KR')}원 / 12개월(무이자 기준)`;
}

export function getEnrollmentPlans(now = new Date()) {
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth();
  const nextStart = new Date(currentYear, currentMonth + 1, 1);
  const secondStart = new Date(currentYear, currentMonth + 2, 1);

  const firstDeadline = new Date(nextStart.getFullYear(), nextStart.getMonth(), 10);
  const secondDeadline = new Date(secondStart.getFullYear(), secondStart.getMonth(), 10);

  return [
    {
      id: 'IMMEDIATE',
      title: '즉시 시작',
      badge: '신청 즉시 매칭 시작',
      badgeTone: 'blue' as const,
      desc: '결제 후 바로 멘토 매칭을 시작하고, 일정 조율이 끝나는 대로 멘토링을 진행합니다.',
      duration: '4개월 집중 과정',
      originalPrice: 5_980_000,
      price: 4_680_000,
      monthly: buildMonthlyPrice(390_000),
    },
    {
      id: 'EARLY_BIRD_1',
      title: formatMonthLabel(nextStart),
      badge: formatDeadlineBadge(firstDeadline),
      badgeTone: 'red' as const,
      desc: `${nextStart.getMonth() + 1}월 차수에 맞춰 멘토를 미리 매칭하고 시작 일정을 안정적으로 확보합니다.`,
      duration: '4개월 집중 과정',
      originalPrice: 4_980_000,
      price: 4_580_000,
      monthly: buildMonthlyPrice(381_000),
    },
    {
      id: 'EARLY_BIRD_2',
      title: formatMonthLabel(secondStart),
      badge: formatDeadlineBadge(secondDeadline),
      badgeTone: 'orange' as const,
      desc: `${secondStart.getMonth() + 1}월 차수를 여유 있게 준비하고 커리큘럼 계획까지 먼저 맞춥니다.`,
      duration: '4개월 집중 과정',
      originalPrice: 4_980_000,
      price: 4_480_000,
      monthly: buildMonthlyPrice(373_000),
    },
  ];
}

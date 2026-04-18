'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { Database, Code2, Layout, Server, Cpu, Smartphone, Layers, Cloud, Users } from 'lucide-react';
import React from 'react';

// === 코스 데이터 정의 ===
const COURSE_DATA: Record<string, any> = {
  'java-backend': {
    title: 'AI+ Java 백엔드',
    subtitle: '깊이 있는 학습과 고퀄리티 프로젝트 수행을 통해 채용 경쟁력을 높이는 1:1 심화형 멘토링 코스',
    iconString: '☕',
    descriptionTitle: '단순히 "써봤다"를 넘어\n제대로 알고 대답할 수 있도록 교육합니다.',
    descriptionText: 'MSA, Kafka까지 써봤다 하더라도 이 기술들은 국비/부트캠프에서도 다루는 흔한 스펙이고 이제 누구나 쉽게 쓸 수 있는 것들이기에,\n"왜 썼는지"를 깊게 설명하지 못하고 "써봤다"만으로는 채용 시장에서 경쟁력을 가지기 어렵습니다.',
    boxes: [
      {
        icon: <Layout size={16}/>, title: '기본기', color: 'cyan',
        tags: ['컴퓨터 사이언스', 'Java', 'Effective Java'],
        desc: '무작정 프레임워크를 쓰는 것을 넘어 CS 지식과 Java 언어 자체의 기본기를 확립합니다.'
      },
      {
        icon: <Code2 size={16}/>, title: '응용', color: 'blue',
        tags: ['Kotlin', 'Spring Boot', 'JPA/QueryDSL', 'Spring Security'],
        desc: 'Spring의 내부 구조를 파고들고, JPA 연관관계 매핑과 최적화, Kotlin 기반 설계 등을 배웁니다.'
      },
      {
        icon: <Database size={16}/>, title: '심화 / 프로젝트', color: 'indigo', isWide: true,
        tags: ['대규모 트래픽 아키텍처', 'Redis 분산락', 'Kafka', 'Docker & K8s', 'MSA'],
        desc: '동시성 제어(Redis), MQ(Kafka)를 이용한 비동기 통신 설계 등 실제 트래픽 이슈들을 실무 관점에서 다루어 포트폴리오를 고도화합니다.'
      }
    ]
  },
  'node-backend': {
    title: 'Node.js Backend + AI',
    subtitle: '실시간 통신과 고성능 비동기 서버 아키텍처를 마스터하는 심화형 멘토링',
    iconString: 'JS',
    descriptionTitle: '단순한 CRUD를 넘어\n고성능 비동기 아키텍처를 다룹니다.',
    descriptionText: 'JavaScript/TypeScript 백엔드 환경에서 Event Loop의 이해부터 Redis, Socket.io를 활용한 대규모 트래픽 처리를 경험해보세요.',
    boxes: [
      {
        icon: <Layout size={16}/>, title: '코어', color: 'cyan',
        tags: ['TypeScript', 'Node.js Core', 'Event Loop'],
        desc: 'JS/TS의 타입 시스템과 Node.js 런타임의 핵심 아키텍처를 이해합니다.'
      },
      {
        icon: <Code2 size={16}/>, title: '프레임워크', color: 'blue',
        tags: ['NestJS', 'Express', 'TypeORM', 'Prisma'],
        desc: '가장 많이 쓰이는 NestJS 생태계와 ORM을 활용해 클린 아키텍처 기반 서버를 구축합니다.'
      },
      {
        icon: <Database size={16}/>, title: '심화 / 실시간 통신', color: 'indigo', isWide: true,
        tags: ['Socket.io', 'Redis Pub/Sub', 'WebRTC', 'AWS / K8s'],
        desc: '채팅, 알림, 실시간 서비스 등 Node.js가 가장 잘하는 분야의 아키텍처를 설계하고 배포합니다.'
      }
    ]
  },
  'python-backend': {
    title: 'Python Backend + AI',
    subtitle: '백엔드 생태계와 AI 서빙을 결합한 최적의 실무 밀착 멘토링',
    iconString: '🐍',
    descriptionTitle: '데이터와 백엔드의 브릿지,\nPython 서버 서빙 최적화.',
    descriptionText: 'Django, FastAPI의 깊은 이해와 더불어 AI 모델(PyTorch/LLM)을 어떻게 빠르고 안정적으로 서빙할 수 있는지 학습합니다.',
    boxes: [
      {
        icon: <Layout size={16}/>, title: '기본기', color: 'cyan',
        tags: ['Python', 'Django', 'FastAPI'],
        desc: '동기/비동기 프레임워크의 장단점을 파악하고 최신 FastAPI 생태계를 익힙니다.'
      },
      {
        icon: <Cpu size={16}/>, title: 'AI 서빙', color: 'blue',
        tags: ['LLM 연동', 'ONNX', 'Triton Server'],
        desc: '인공지능 모델을 마이크로서비스 형태로 서빙하기 위한 아키텍처를 구축해봅니다.'
      },
      {
        icon: <Database size={16}/>, title: '인프라/스케일링', color: 'indigo', isWide: true,
        tags: ['Celery', 'Redis', 'Docker Compose', 'Gunicorn'],
        desc: '병렬 처리와 비동기 큐 워커를 활용하여 Python 서버의 한계를 깨는 확장성 설계를 다룹니다.'
      }
    ]
  },
  'frontend': {
    title: 'Frontend + AI',
    subtitle: '프론트엔드 성능 최적화와 트러블슈팅, 최신 기술 스택을 다루는 심화 코스',
    iconString: '⚛️',
    descriptionTitle: '보이는 것 그 이상,\n사용자 경험(UX)과 성능의 극대화를 이룹니다.',
    descriptionText: 'Next.js의 SSR/SSG/ISR 혼합 렌더링, Web Vitals 최적화, 상태관리 패턴 등 프론트엔드 엔진의 동작 원리를 뜯어봅니다.',
    boxes: [
      {
        icon: <Layout size={16}/>, title: '코어 UI', color: 'cyan',
        tags: ['React', 'TypeScript', '브라우저 렌더링'],
        desc: 'Virtual DOM의 이해와 브라우저 렌더링 파이프라인 최적화를 실습합니다.'
      },
      {
        icon: <Code2 size={16}/>, title: '메타 프레임워크', color: 'blue',
        tags: ['Next.js', 'App Router', 'State Management'],
        desc: '모던 웹 개발의 핵심인 Next.js App Router 생태계와 서버 단 데이터 페칭을 고도화합니다.'
      },
      {
        icon: <Layers size={16}/>, title: 'UX & 성능 고도화', color: 'indigo', isWide: true,
        tags: ['CI/CD', 'Web Vitals', 'Framer Motion', 'Micro-Frontend'],
        desc: '웹 성능 최적화, 마이크로 프론트엔드 아키텍처 및 화려하고 자연스러운 인터랙션을 구현하여 압도적인 포트폴리오를 만듭니다.'
      }
    ]
  },
  'android': {
    title: 'Android + AI',
    subtitle: '모던 안드로이드 앱 아키텍처와 Compose, 성능 최적화 마스터 과정',
    iconString: '🤖',
    descriptionTitle: '안드로이드 네이티브의 끝판왕,\n안정적이고 유려한 앱을 만듭니다.',
    descriptionText: 'Jetpack Compose와 MVVM/MVI 아키텍처, Memory Leak 방지 기술 등 현업 안드로이드 팀이 선호하는 필수 역량을 다집니다.',
    boxes: [
      {
        icon: <Smartphone size={16}/>, title: '기본 & 패러다임', color: 'cyan',
        tags: ['Kotlin', 'Coroutines', 'Flow'],
        desc: 'Kotlin의 강력한 비동기 처리와 선언형 프로그래밍 방식을 완벽하게 이해합니다.'
      },
      {
        icon: <Layout size={16}/>, title: 'UI & 아키텍처', color: 'blue',
        tags: ['Jetpack Compose', 'MVVM/MVI', 'Hilt'],
        desc: '기존 XML 뷰에서 탈피하여 100% Compose 기반으로 앱 UI 레이어를 설계하고 의존성 주입을 다룹니다.'
      },
      {
        icon: <Server size={16}/>, title: '심화 / 오프라인 퍼스트', color: 'indigo', isWide: true,
        tags: ['Room', 'WorkManager', 'Modularization', 'ExoPlayer'],
        desc: '대규모 앱 스케일링을 위한 멀티 모듈 아키텍처 설계와 로컬 캐싱 전략을 통한 오프라인 최적화를 실습합니다.'
      }
    ]
  },
  'devops': {
    title: 'DevOps 엔지니어 육성',
    subtitle: 'CI/CD 빌드 파이프라인부터 클라우드 네이티브 아키텍처까지',
    iconString: '⚙️',
    descriptionTitle: '인프라를 코드로 구성하고,\n자동화로 생산성을 극대화합니다.',
    descriptionText: 'AWS 환경에서의 IaC(Terraform), 클러스터 오케스트레이션(K8s) 등 실무에서 환영받는 DevOps 툴체인을 경험합니다.',
    boxes: [
      {
        icon: <Cloud size={16}/>, title: '클라우드 & IaC', color: 'cyan',
        tags: ['AWS', 'Terraform', 'Linux'],
        desc: 'AWS의 심화 네트워킹과 컴퓨팅 리소스를 코드로 정의하고 프로비저닝 합니다.'
      },
      {
        icon: <Server size={16}/>, title: 'CI/CD', color: 'blue',
        tags: ['GitHub Actions', 'Jenkins', 'ArgoCD'],
        desc: '개발부터 배포까지 무중단 스무스 파이프라인을 구축하여 빌드 시간을 획기적으로 줄여봅니다.'
      },
      {
        icon: <Layers size={16}/>, title: '컨테이너 오케스트레이션', color: 'indigo', isWide: true,
        tags: ['Docker', 'Kubernetes', 'Helm', 'Prometheus'],
        desc: 'K8s 클러스터 운영 및 로깅/모니터링 체계를 바탕으로 장애 복원력(Resiliency)을 갖춘 인프라를 설계합니다.'
      }
    ]
  },
  'ios': {
    title: 'iOS + AI',
    subtitle: '모던 iOS 앱 아키텍처와 SwiftUI 마스터 과정',
    iconString: '🍎',
    descriptionTitle: '부드러운 경험을 만드는\n최상급 iOS 애플리케이션',
    descriptionText: 'SwiftUI, Combine 기반의 선언형 패러다임과 TCA(The Composable Architecture) 등 최신 iOS 생태계를 학습합니다.',
    boxes: [
      {
        icon: <Smartphone size={16}/>, title: '기본 & 패러다임', color: 'cyan',
        tags: ['Swift', 'SwiftUI', 'Combine'],
        desc: 'Swift 언어의 핵심과 SwiftUI의 선언적 UI 구성 방식을 완벽하게 이해합니다.'
      },
      {
        icon: <Layout size={16}/>, title: '모던 아키텍처', color: 'blue',
        tags: ['TCA', 'MVVM', 'Clean Architecture'],
        desc: '대규모 애플리케이션에서 상태를 예측 가능하게 관리하기 위한 현대적 아키텍처를 도입해봅니다.'
      },
      {
        icon: <Server size={16}/>, title: '퍼포먼스/최적화', color: 'indigo', isWide: true,
        tags: ['CoreData', 'Memory Management', 'Instruments'],
        desc: 'Memory Leak 방지와 앱 크래시 분석, 렌더링 최적화를 통한 프리미엄 사용자 경험을 제공합니다.'
      }
    ]
  },
  'flutter': {
    title: 'Flutter + AI',
    subtitle: '크로스 플랫폼의 한계를 뛰어넘는 최적화 및 네이티브 연동',
    iconString: '🦋',
    descriptionTitle: '하나의 코드로 두 배의 가치를,\n크로스 플랫폼의 완성.',
    descriptionText: '단순 UI 클론을 넘어 렌더링 최적화, 상태관리 패턴, 그리고 네이티브(채널) 연동까지 깊게 파고드는 전문가 과정입니다.',
    boxes: [
      {
        icon: <Smartphone size={16}/>, title: 'Dart & 코어', color: 'cyan',
        tags: ['Dart', 'Widget Lifecycle', 'Element Tree'],
        desc: 'Flutter 엔진이 화면을 그리는 3가지 트리(Widget, Element, RenderObject)의 동작 원리를 이해합니다.'
      },
      {
        icon: <Layout size={16}/>, title: '상태 관리', color: 'blue',
        tags: ['Provider', 'Riverpod', 'Bloc'],
        desc: '현업에서 가장 많이 쓰이는 상태관리 라이브러리들을 비교하고 상황에 맞게 최적화합니다.'
      },
      {
        icon: <Layers size={16}/>, title: '네이티브 & 심화', color: 'indigo', isWide: true,
        tags: ['Method Channel', 'Isolates', 'CI/CD'],
        desc: '스레드(Isolate) 분리를 통한 성능 최적화와 결제, 푸시 등 네이티브 연동을 마스터합니다.'
      }
    ]
  },
  'react-native': {
    title: 'React Native + AI',
    subtitle: '웹 개발 경험으로 시작하는 최고 수준의 앱 배포',
    iconString: '📱',
    descriptionTitle: '웹과 모바일의 브릿지,\n빠른 속도로 시장을 선점합니다.',
    descriptionText: 'React 생태계를 그대로 활용하며, 브릿지의 한계를 넘기 위한 최신 JSI 아키텍처와 애니메이션 최적화를 배웁니다.',
    boxes: [
      {
        icon: <Smartphone size={16}/>, title: '코어 개념', color: 'cyan',
        tags: ['React', 'Metro', 'Native Bridge'],
        desc: 'React Native의 브릿지 통신 원리와 동작 메커니즘을 뜯어봅니다.'
      },
      {
        icon: <Layout size={16}/>, title: 'UI & 애니메이션', color: 'blue',
        tags: ['Reanimated', 'Gesture Handler', 'Skia'],
        desc: '선언형 애니메이션을 작성하여 60fps를 방어하는 네이티브 수준의 UI를 렌더링합니다.'
      },
      {
        icon: <Layers size={16}/>, title: '인프라 & 배포', color: 'indigo', isWide: true,
        tags: ['Expo EAS', 'CodePush', 'App Store Connect'],
        desc: 'OTA 업데이트를 구상하고 원클릭 배포 파이프라인을 구축하여 유지보수 비용을 최소화합니다.'
      }
    ]
  },
  'data-engineer': {
    title: 'Data Engineer + AI',
    subtitle: '대용량 데이터 파이프라인 구축 및 실시간 스트리밍 처리 기술',
    iconString: '📊',
    descriptionTitle: '데이터의 강이 흐르는\n견고한 파이프라인 설계.',
    descriptionText: '빅데이터 에코시스템(Hadoop, Spark)부터 최신 모던 데이터 스택(Airflow, dbt)까지 대용량 데이터를 안전하고 빠르게 처리하는 아키텍처를 학습합니다.',
    boxes: [
      {
        icon: <Database size={16}/>, title: '데이터 수집 & 저장', color: 'cyan',
        tags: ['Hadoop', 'S3', 'Data Lake'],
        desc: '다양한 소스에서 발생한 데이터를 분산 저장소에 안정적으로 적재하는 기초를 다룹니다.'
      },
      {
        icon: <Cloud size={16}/>, title: '스트리밍 & 배치', color: 'blue',
        tags: ['Spark', 'Kafka', 'Flink'],
        desc: '실시간 데이터 처리와 대규모 배치 트랜잭션을 구현하여 데이터의 정합성을 보장합니다.'
      },
      {
        icon: <Layers size={16}/>, title: '파이프라인 자동화', color: 'indigo', isWide: true,
        tags: ['Airflow', 'dbt', 'Snowflake'],
        desc: '복잡한 데이터 파이프라인을 시각적으로 오케스트레이션하고 데이터 웨어하우스(DW)에 최적화합니다.'
      }
    ]
  },
  'ml-engineer': {
    title: 'ML Engineer',
    subtitle: '머신러닝 모델의 학습, 평가 및 프로덕션 환경 배포의 모든 것',
    iconString: '🧠',
    descriptionTitle: '연구를 넘어 실전으로,\n살아 숨쉬는 AI 시스템 구축.',
    descriptionText: '모델 아키텍처 설계와 하이퍼파라미터 튜닝을 넘어, MLOps 기반으로 모델을 지속 가능하게 서비스하는 기술을 배웁니다.',
    boxes: [
      {
        icon: <Cpu size={16}/>, title: '모델링 코어', color: 'cyan',
        tags: ['PyTorch', 'TensorFlow', 'Scikit-Learn'],
        desc: '심층 신경망(DNN)의 기초 구조부터 시계열, 비전, 자연어 등 분야별 실무형 모델 아키텍처를 실습합니다.'
      },
      {
        icon: <Layout size={16}/>, title: '서빙 아키텍처', color: 'blue',
        tags: ['FastAPI', 'ONNX', 'Triton Server'],
        desc: '무거운 AI 모델을 압축, 최적화하여 짧은 레이턴시를 보장하는 API 서버 형태를 구축해봅니다.'
      },
      {
        icon: <Server size={16}/>, title: 'MLOps 파이프라인', color: 'indigo', isWide: true,
        tags: ['Kubeflow', 'MLflow', 'Docker/K8s'],
        desc: '모델 개발, 배포, 모니터링 라이프사이클 전체를 자동화해 안정성 높은 AI 서비스를 유지합니다.'
      }
    ]
  },
  'game-server': {
    title: 'Game Server',
    subtitle: '초당 수천 번의 인터랙션을 처리하는 실시간 멀티플레이 서버 아키텍처',
    iconString: '🎮',
    descriptionTitle: '0.01초의 딜레이도 허용하지 않는\n극한의 게임 서버 튜닝.',
    descriptionText: 'C++/C#을 기반으로 한 소켓 프로그래밍부터 동시성 제어, 매치메이킹 시스템 등 실제 게임 서버의 코어 로직을 작성합니다.',
    boxes: [
      {
        icon: <Code2 size={16}/>, title: '네트워크 & 코어', color: 'cyan',
        tags: ['C++/C#', 'TCP/UDP', 'IOCP'],
        desc: '운영체제의 네트워크 I/O 구조를 뜯어보고 효율적인 소켓 입출력 통신망을 구현합니다.'
      },
      {
        icon: <Server size={16}/>, title: '멀티스레딩 & 동기화', color: 'blue',
        tags: ['Lock-free', 'Deadlock 방지', 'Memory Pool'],
        desc: '멀티스레드 환경의 크리티컬 섹션 제어부터 메모리 풀링 최적화 기술을 파고듭니다.'
      },
      {
        icon: <Layers size={16}/>, title: '분산 & 매치메이킹', color: 'indigo', isWide: true,
        tags: ['Redis', 'gRPC', 'AWS GameLift'],
        desc: '글로벌 서비스 상황의 데이터 동기화와 원활한 유저 매치메이킹 레이어를 구축합니다.'
      }
    ]
  },
  'short-term': {
    title: '단기 취업/이직',
    subtitle: '빠른 속도로 실무 역량을 증명하고 목표하는 기업에 합격하는 1개월 밀착 코스',
    iconString: '⚡',
    descriptionTitle: '군더더기 없이 핵심만,\n합격을 위한 가장 빠른 지름길.',
    descriptionText: '알고리즘 코딩테스트부터 과제형 전형, 모의 면접, 이력서 첨삭 등 채용의 A to Z를 함께하는 극강의 단기 매니지먼트 멘토링입니다.',
    boxes: [
      {
        icon: <Code2 size={16}/>, title: '코딩테스트/과제', color: 'cyan',
        tags: ['알고리즘', '자료구조', '리팩토링'],
        desc: '자주 출제되는 유형을 족집게처럼 짚어주며 깔끔하게 과제를 완성하는 전략을 배웁니다.'
      },
      {
        icon: <Layout size={16}/>, title: '서류/포트폴리오', color: 'blue',
        tags: ['이력서', 'Readme', '트러블슈팅'],
        desc: '내가 한 경험을 가장 돋보이게 작성하는 이력서 레이아웃과 서술 방식을 1:1로 피드백합니다.'
      },
      {
        icon: <Users size={16}/>, title: '모의 면접', color: 'indigo', isWide: true,
        tags: ['CS 질문', '인성 면접', '기술 심층 면접'],
        desc: '실제 면접관 출신 멘토와 함께 예상 질문 리스트를 도출하고 꼬리물기 압박 면접을 대비합니다.'
      }
    ]
  },
  'firststep': {
    title: 'First Step: Java Backend',
    subtitle: '비전공자/입문자도 따라할 수 있는 탄탄한 웹 백엔드 첫걸음',
    iconString: '🌱',
    descriptionTitle: '처음이라고 두려워 마세요,\n기본부터 든든하게 다집니다.',
    descriptionText: 'Java 언어의 기초부터 시작해 웹의 동작 원리와 Spring Boot를 활용한 첫 서버 배포까지 끝맺음하는 과정입니다.',
    boxes: [
      {
        icon: <Code2 size={16}/>, title: '언어의 기초', color: 'cyan',
        tags: ['Java 17', '객체지향', '컬렉션'],
        desc: '변수, 반복문부터 시작해 객체지향 4대 특징과 SOLID 프로그래밍 관점을 쉽게 이해합니다.'
      },
      {
        icon: <Layout size={16}/>, title: '웹 프레임워크', color: 'blue',
        tags: ['Spring Boot', 'REST API', 'MySQL'],
        desc: '간단한 게시판 형식의 API를 만들고, 데이터베이스에 정보를 지속적으로 저장하는 법을 실습합니다.'
      },
      {
        icon: <Cloud size={16}/>, title: '내 생의 첫 배포', color: 'indigo', isWide: true,
        tags: ['AWS EC2', 'Linux', 'GitHub'],
        desc: '클라우드 환경에 내 서버를 올려보고 전 세계 누구나 접속할 수 있도록 포트폴리오 첫 줄을 장식합니다.'
      }
    ]
  },
  'distributed-lock': {
    title: '분산 락 (Distributed Lock) Deep Dive',
    subtitle: '수만 명의 선착순 트래픽을 놓치지 않고 완벽히 제어하는 특강',
    iconString: '🔒',
    descriptionTitle: '단 한 건의 동시성 오류도 용납하지 않는\n초정밀 트래픽 제어.',
    descriptionText: '티켓팅, 수강신청, 타임세일 이벤트와 같은 극단적인 동시성 상황에서 정합성을 지키기 위한 시스템을 집중 설계합니다.',
    boxes: [
      {
        icon: <Database size={16}/>, title: 'RDB Lock 전략', color: 'cyan',
        tags: ['Pessimistic Lock', 'Optimistic Lock', 'JPA'],
        desc: '데이터베이스의 배타 락, 공유 락 개념을 파고들며 가장 기초적인 동시성 제어를 구현합니다.'
      },
      {
        icon: <Server size={16}/>, title: '분산 환경의 락', color: 'blue',
        tags: ['Redis', 'Lettuce', 'Redisson'],
        desc: '여러 대의 서버 인스턴스에서도 정합성이 깨지지 않도록 Redis 기반 분산 락 메커니즘을 적용합니다.'
      },
      {
        icon: <Cpu size={16}/>, title: '도메인 적용', color: 'indigo', isWide: true,
        tags: ['쿠폰 발급', '결제 트랜잭션', '재고 차감'],
        desc: 'Spring의 AOP나 Facade 패턴을 활용해 비즈니스 로직과 락 기능을 우아하게 분리하고 테스트 코드로 검증합니다.'
      }
    ]
  },
  'kafka': {
    title: 'Kafka Deep Dive',
    subtitle: '대규모 메시지 큐와 이벤트 드리븐 설계 패턴 완전 정복',
    iconString: '📨',
    descriptionTitle: '시스템 간의 완벽한 징검다리,\n이벤트 기반 아키텍처.',
    descriptionText: '결합도를 낮추고 처리량을 높여주는 Kafka의 내부 동작 원리를 파악하고, 실제 MSA 환경에서 어떻게 활용하는지 심층 실습합니다.',
    boxes: [
      {
        icon: <Server size={16}/>, title: 'Kafka 코어', color: 'cyan',
        tags: ['Broker', 'Topic & Partition', 'Offset'],
        desc: 'Kafka의 튼튼한 분산 저장 원리와 Replication, Zookeeper(또는 KRaft) 체제에 대해 학습합니다.'
      },
      {
        icon: <Code2 size={16}/>, title: '프로듀싱 & 컨슈밍', color: 'blue',
        tags: ['Spring Kafka', 'Ack 튜닝', 'Idempotence'],
        desc: '메시지를 유실하지 않기 위한 설정과 중복 처리를 막기 위한 At-Least/Exactly-Once 전략을 실습합니다.'
      },
      {
        icon: <Layers size={16}/>, title: 'EDA 기반 서비스 분리', color: 'indigo', isWide: true,
        tags: ['Event-Driven', 'Outbox Pattern', 'Saga'],
        desc: '데이터베이스 트랜잭션과 메시지 발행을 일치시키는 Transactional Outbox 패턴 등 고급 MSA 주제를 다룹니다.'
      }
    ]
  },
  'expert-msa': {
    title: 'Kotlin/MSA 최고급 과정',
    subtitle: '현업 시니어 및 테크리드를 위한 엔터프라이즈 아키텍처 설계',
    iconString: '🏛️',
    descriptionTitle: '수백 개의 마이크로 서비스,\n복잡성 속에서 질서를 찾습니다.',
    descriptionText: '모놀리식 분리를 고민하거나 트래픽 임계점에 도달한 시스템을 책임지는 시니어 개발자들을 위한 1:1 맞춤형 컨설팅 및 교육입니다.',
    boxes: [
      {
        icon: <Code2 size={16}/>, title: '도메인 주도 설계', color: 'cyan',
        tags: ['DDD', 'Clean Architecture', 'Hexagonal'],
        desc: '비즈니스 도메인을 명확하게 분리하여 마이크로서비스 간의 경계(Bounded Context)를 정의하는 법을 체득합니다.'
      },
      {
        icon: <Layers size={16}/>, title: '마이크로서비스 핵심', color: 'blue',
        tags: ['Spring Cloud', 'API Gateway', 'Circuit Breaker'],
        desc: '서비스 디스커버리와 장애 전파 차단을 위한 기술 스택을 도입해 안정적인 백엔드망을 오케스트레이션합니다.'
      },
      {
        icon: <Database size={16}/>, title: '분산 모니터링 & 트랜잭션', color: 'indigo', isWide: true,
        tags: ['Spring Cloud Data Flow', 'Zipkin', '2PC/Saga'],
        desc: '흩어진 서비스의 로그를 추적하고, 시스템 전체에 걸친 분산 트랜잭션을 최종적 정합성(Eventual Consistency)으로 해결합니다.'
      }
    ]
  }
};

// Fallback 데이터 (그 외의 데이터가 없을 때)
const FALLBACK_DATA = {
  title: '전문 멘토링 코스',
  subtitle: '수많은 합격생을 배출한 1:1 맞춤형 밀착 코딩 멘토링',
  iconString: '🚀',
  descriptionTitle: '실무의 벽을 허무는\n맞춤형 프로젝트 멘토링',
  descriptionText: '현업에 바로 투입되어도 손색없는 지식과 경험을 공유해 드립니다.\n단순 코딩을 넘어 소프트웨어 아키텍처와 성능 최적화까지 다룹니다.',
  boxes: [
    {
      icon: <Layout size={16}/>, title: '기초 확립', color: 'cyan',
      tags: ['프로그래밍 핵심 지식', '프레임워크 활용', 'CS 기본기'],
      desc: '해당 분야에 꼭 필요한 핵심 원리와 작동 방식을 탐구합니다.'
    },
    {
      icon: <Code2 size={16}/>, title: '실무 기술', color: 'blue',
      tags: ['아키텍처 설계', '디자인 패턴', '성능 개선'],
      desc: '현업에서 고민하는 기술적 난제와 해결 프로세스를 배웁니다.'
    },
    {
      icon: <Layers size={16}/>, title: '프로젝트 스펙업', color: 'indigo', isWide: true,
      tags: ['포트폴리오 고도화', '이력서/면접 컨설팅', 'CI/CD 배포', '협업 경험'],
      desc: '진짜 트래픽과 진짜 데이터를 다룰 수 있는 프로젝트를 완성하고 최종 목표(취업/이직/승진)를 달성합니다.'
    }
  ]
};

export default function CourseDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  
  // React.use() 대신 간단하게 컴포넌트 단에서 params 처리 (NextJS 13+)
  // 서버/클라이언트 컴포넌트 여하에 따라 params가 동기/비동기가 달라지지만 기본적인 형태.
  const courseId = params?.id || 'java-backend';
  const data = COURSE_DATA[courseId] || FALLBACK_DATA;

  const bgGradients = {
    cyan: 'from-cyan-500 to-blue-500',
    blue: 'from-blue-500 to-indigo-500',
    indigo: 'from-indigo-500 to-purple-500',
  };
  const textGradients = {
    cyan: 'text-cyan-400',
    blue: 'text-blue-400',
    indigo: 'text-indigo-400',
  };

  return (
    <>
      <Header />
      <main className="min-h-screen bg-black">
        {/* Hero Section */}
        <section className="relative pt-32 pb-24 overflow-hidden border-b border-white/10" style={{ background: 'radial-gradient(ellipse at top, #0A192F 0%, #000000 100%)' }}>
          <div className="absolute inset-0 opacity-20" style={{ backgroundImage: 'radial-gradient(circle at 2px 2px, white 1px, transparent 0)', backgroundSize: '40px 40px' }}></div>
          
          <div className="max-w-6xl mx-auto px-6 relative z-10 flex flex-col md:flex-row items-center justify-between">
            <div className="md:w-1/2 text-left mb-10 md:mb-0">
              <h1 className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight mb-4 whitespace-pre-line">
                {data.title}
              </h1>
              <p className="text-gray-300 text-lg leading-relaxed mb-8 max-w-md">
                {data.subtitle}
              </p>
              <button 
                onClick={() => router.push('/apply')}
                className="px-8 py-3 bg-[#0066FF] hover:bg-blue-600 text-white font-bold rounded-lg transition-colors text-sm"
              >
                신청하러 가기
              </button>
            </div>
            
            <div className="md:w-1/2 flex justify-center md:justify-end">
              <div className="w-64 h-64 rounded-3xl bg-gradient-to-br from-[#00A8FF] to-[#0066FF] flex items-center justify-center p-8 shadow-[0_0_50px_rgba(0,102,255,0.4)] relative border-4 border-[#0F2A52]">
                <div className="w-full h-full border-4 border-white/20 rounded-2xl flex items-center justify-center relative overflow-hidden">
                   <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent"></div>
                   <span className="text-8xl font-bold text-white drop-shadow-lg z-10">{data.iconString}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Info Box Section */}
        <section className="border-b border-white/10 bg-[#0A0A0A]">
          <div className="max-w-6xl mx-auto flex flex-col md:flex-row divide-y md:divide-y-0 md:divide-x divide-white/10 text-gray-400 text-sm">
            
            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">대상</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>신입, 경력(2년 이하) 채용자<br/>(이직자 가능)</li>
                <li>개발 경험이 있는 비전공</li>
                <li>전공자 미취업</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">멘토링 방식</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>1대1 온라인 화상 방식</li>
                <li>프로젝트 스터디/코드 리뷰</li>
                <li>녹화본/스크립트 제공</li>
                <li>이력서/면접/포트폴리오 코칭</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">멤버십 기간</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>기본 4개월 (주 1회 기준)</li>
                <li>개인 맞춤 학습진도 진행</li>
                <li>월 멤버십 연장 가능</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">진행 시간</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>주 1회 1시간 화상 멘토링<br/>(상담/진도 체크 위주)</li>
                <li>메신저 상시 소통</li>
                <li>Github 상시 코드 리뷰</li>
              </ul>
            </div>

          </div>
        </section>

        {/* Sticky Nav */}
        <div className="sticky top-16 z-40 bg-black/80 backdrop-blur-md border-b border-white/10 flex justify-center text-xs font-bold text-gray-400">
           <div className="flex w-full max-w-4xl justify-around py-4">
             <span className="text-white">커리큘럼</span>
             <span className="hover:text-white cursor-pointer" onClick={() => {
                document.getElementById('reviews')?.scrollIntoView({ behavior: 'smooth' });
             }}>후기</span>
             <span className="transition-colors hover:text-white cursor-pointer text-[#0066FF]" onClick={() => router.push('/apply')}>신청하기</span>
           </div>
        </div>

        {/* Content Section */}
        <section className="py-24 bg-black">
          <div className="max-w-4xl mx-auto px-6 text-center">
            
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white mb-6 whitespace-pre-line">
              {data.descriptionTitle.replace('제대로 알고 대답', '<span class="text-[#0066FF]">제대로 알고 대답</span>')}
            </h2>
            
            <div className="w-12 h-1 bg-gray-800 mx-auto mb-6"></div>

            <p className="text-gray-400 text-sm leading-relaxed mb-16 whitespace-pre-line">
              {data.descriptionText}
            </p>

            <div className="grid md:grid-cols-2 gap-4">
              {data.boxes.map((box: any, idx: number) => (
                <div key={idx} className={`border border-[#0066FF]/30 bg-[#001433]/50 rounded-xl p-6 text-left relative overflow-hidden ${box.isWide ? 'md:col-span-2 mt-4' : ''}`}>
                  <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${(bgGradients as any)[box.color]}`}></div>
                  <h4 className={`${(textGradients as any)[box.color]} text-sm font-bold mb-4 flex items-center gap-2`}>
                    {box.icon}
                    {box.title}
                  </h4>
                  <div className="flex flex-wrap gap-2 mb-4">
                    {box.tags.map((tag: string) => (
                      <span key={tag} className="px-3 py-1 bg-blue-900/30 border border-blue-800 rounded text-gray-300 text-xs">{tag}</span>
                    ))}
                  </div>
                  <p className="text-gray-400 text-xs leading-relaxed">
                    {box.desc}
                  </p>
                </div>
              ))}
            </div>

          </div>
        </section>

        {/* Reviews Section */}
        <section id="reviews" className="py-24 bg-[#050B14] border-t border-white/5">
          <div className="max-w-4xl mx-auto px-6 text-center">
            
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white mb-12">
              수강생 후기
            </h2>

            <div className="grid md:grid-cols-2 gap-6 text-left">
              <div className="bg-[#0A111E] p-6 rounded-xl border border-white/10">
                <div className="flex items-center gap-1 mb-4 text-[#0066FF]">
                  {'★★★★★'}
                </div>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  "학원에서 수박 겉핥기로 배웠던 부분들을 바닥까지 파헤쳐 볼 수 있었습니다. 진짜 현업에서 고민하는 문제들을 멘토님이 1:1로 지도해주셔서 결국 좋은 핏의 기업에 합격할 수 있었습니다."
                </p>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-blue-900 text-xs flex justify-center items-center font-bold text-white">익명</div>
                  <span className="text-gray-500 text-xs">최종 합격자 / 주니어</span>
                </div>
              </div>

              <div className="bg-[#0A111E] p-6 rounded-xl border border-white/10">
                <div className="flex items-center gap-1 mb-4 text-[#0066FF]">
                  {'★★★★★'}
                </div>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  "그동안 해왔던 사이드 프로젝트의 아키텍처를 분리하고 동시성 제어까지 적용해보니 시야가 확 트였습니다. 가장 좋았던 것은 이력서 리뷰와 코드 리뷰를 현직자 시선에서 집중적으로 진행해주신 점이었습니다."
                </p>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-purple-900 text-xs flex justify-center items-center font-bold text-white">익명</div>
                  <span className="text-gray-500 text-xs">이직 성공 / 실무 2년차</span>
                </div>
              </div>
            </div>

            <div className="mt-20 mb-8 max-w-5xl mx-auto">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {[
                  {
                    id: 'IMMEDIATE',
                    title: '즉시 시작',
                    badge: '시작일 선택 가능',
                    badgeColor: 'bg-blue-900/30 text-blue-400 border-blue-800/50',
                    desc: '결제 즉시 멘토님 매칭 (시작일 선택 가능)',
                    duration: '4개월',
                    originalPrice: 5980000,
                    price: 4680000,
                    monthly: '390,000원 / 12개월(무이자)',
                  },
                  {
                    id: 'EARLY_BIRD_6',
                    title: '6월 시작 얼리버드',
                    badge: '마감 D-13',
                    badgeColor: 'bg-red-900/30 text-red-400 border-red-800/50',
                    desc: '6월 내 멘토님 매칭, 시작일 선택 가능',
                    duration: '4개월',
                    originalPrice: 4980000,
                    price: 4580000,
                    monthly: '381,000원 / 12개월(무이자)',
                  },
                  {
                    id: 'EARLY_BIRD_7',
                    title: '7월 시작 얼리버드',
                    badge: '마감 D-13',
                    badgeColor: 'bg-orange-900/30 text-orange-400 border-orange-800/50',
                    desc: '7월 내 멘토님 매칭, 시작일 선택 가능',
                    duration: '4개월',
                    originalPrice: 4980000,
                    price: 4480000,
                    monthly: '373,000원 / 12개월(무이자)',
                  }
                ].map((plan) => (
                  <div key={plan.id} className="bg-[#0a111a] border border-white/10 rounded-2xl p-8 flex flex-col text-left transition-all hover:border-blue-500/50 hover:shadow-2xl hover:shadow-blue-500/10">
                    <div className="flex justify-between items-start mb-6">
                      <h3 className="text-xl font-bold text-white tracking-tight">{plan.title}</h3>
                      <span className={`px-2.5 py-1 rounded-md text-[10px] font-bold border ${plan.badgeColor}`}>
                        {plan.badge}
                      </span>
                    </div>

                    <div className="space-y-4 mb-8 flex-grow">
                      <div>
                        <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-1">시작</p>
                        <p className="text-sm text-gray-300 font-medium leading-relaxed">{plan.desc}</p>
                      </div>
                      <div>
                        <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-1">기간</p>
                        <p className="text-sm text-gray-300 font-bold">{plan.duration}</p>
                      </div>
                    </div>

                    <div className="pt-6 border-t border-white/5 mb-6">
                      <p className="text-xs text-gray-500 line-through mb-1">{(plan.originalPrice).toLocaleString()}원</p>
                      <p className="text-3xl font-black text-white tracking-tighter">
                        {(plan.price).toLocaleString()}<span className="text-sm font-bold text-gray-400 ml-0.5 tracking-normal">원</span>
                      </p>
                    </div>

                    <div className="p-4 bg-white/5 rounded-xl mb-6">
                      <p className="text-xs text-gray-300 mb-1 flex items-center gap-1">
                        <span className="w-1 h-1 rounded-full bg-blue-500"></span>
                        {plan.monthly}
                      </p>
                      <p className="text-[10px] text-gray-500 leading-tight">
                        *최대 할인 적용시 금액<br/>
                        *12개월 무이자 할부는 일부 카드사만 해당됩니다.
                      </p>
                    </div>

                    <button 
                      onClick={() => router.push('/apply')}
                      className="w-full py-4 rounded-xl font-bold text-sm bg-blue-600/20 text-blue-400 border border-blue-500/30 hover:bg-blue-600 hover:text-white transition-all"
                    >
                      해당 플랜으로 지원하기
                    </button>
                  </div>
                ))}
              </div>
            </div>

          </div>
        </section>

      </main>
      <Footer />
    </>
  );
}

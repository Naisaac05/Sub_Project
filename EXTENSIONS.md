# 🛠️ VS Code 추천 익스텐션 가이드 (DevMatch 프로젝트)

Spring Boot + Next.js 프로젝트를 효율적으로 개발하기 위해 권장되는 VS Code 익스텐션 목록입니다.

VS Code 좌측의 **Extensions(확장) 아이콘(`Ctrl+Shift+X`)**에서 아래 이름들을 검색하여 설치하세요.

## ☕ 1. Backend (Java 17 + Spring Boot 3.x) 필수

- **Extension Pack for Java** (제작자: Microsoft)
  - Java 언어 지원, 디버거, Maven/Gradle 연동 등을 모두 포함하는 자바 개발 필수 패키지입니다.
- **Spring Boot Extension Pack** (제작자: VMware)
  - `Spring Initializr` 지원, `application.yml` 자동완성, 스프링 부트 대시보드를 제공해 개발 생산성을 크게 높여줍니다.

## ⚛️ 2. Frontend (Next.js 14 + TypeScript + Tailwind) 필수

- **Tailwind CSS IntelliSense** (제작자: Tailwind Labs)
  - Tailwind 클래스명을 타이핑할 때 자동완성을 지원하고 색상/속성을 미리 보여주는 필수 도구입니다.
- **ESLint** (제작자: Microsoft)
  - JavaScript/TypeScript 코드의 문법 오류 및 안티 패턴을 실시간으로 잡아줍니다.
- **Prettier - Code formatter** (제작자: Prettier)
  - 파일 저장 시 코드를 프로젝트 규칙에 맞게 깔끔하게 자동 정렬해줍니다.
- **ES7+ React/Redux/React-Native snippets** (제작자: dsznajder)
  - `rfce` 등의 단축키를 입력하면 React 컴포넌트 뼈대 코드를 순식간에 만들어줍니다.

## 🐳 3. 인프라 및 기타 (선택 / 권장)

- **Docker** (제작자: Microsoft)
  - `Dockerfile` 작성 시 문법 강조/자동완성을 지원하며, 실행 중인 컨테이너들을 VS Code 화면 내에서 바로 관리할 수 있습니다.
- **Database Client (또는 MySQL)** (제작자: cweijan 등)
  - DBeaver 같은 외부 DB 툴을 열 필요 없이, VS Code 안에서 MySQL 8.0 데이터베이스에 붙어서 테이블을 조회하고 쿼리를 실행할 수 있습니다.
- **DotENV** (제작자: mikestead)
  - 프론트엔드나 백엔드의 환경변수(`.env` 파일) 문법을 보기 좋게 하이라이팅 해줍니다.

---

> **💡 핵심 요약**
> 백엔드 개발을 위해 **Java / Spring Boot 확장팩 2개**, 프론트엔드 개발을 위해 **Tailwind 확장팩**은 개발 시작 전에 반드시 설치해두시는 것을 추천합니다!

## cmd 명령어

##

code --install-extension vscjava.vscode-java-pack --install-extension vmware.vscode-boot-dev-pack --install-extension bradlc.vscode-tailwindcss --install-extension dbaeumer.vscode-eslint --install-extension esbenp.prettier-vscode --install-extension dsznajder.es7-react-js-snippets --install-extension ms-azuretools.vscode-docker --install-extension cweijan.vscode-database-client2 --install-extension mikestead.dotenv

##

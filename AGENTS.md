# DevMozip 작업 규칙

- 현재 목적은 기획 발표용 React 프론트엔드 프로토타입이다.
- 먼저 기존 코드와 문서를 확인하고 동작하는 기능을 보존한다.
- 요청받은 단계만 개발하며 완료 후 다음 단계를 자동 시작하지 않는다.
- 백엔드는 Java 21 / Spring Boot / Gradle / Spring Web 기본 틀만 유지한다. Controller, Service, Repository, DTO, Entity, 실제 API를 구현하지 않는다.
- DB, Gemini, Kakao, Toss Payments, 인증 서버, WebSocket에 연결하지 않는다.
- 기능 데이터는 mocks, 상태/통신 경계는 services, 도메인 UI는 features, 라우팅 화면은 pages, 공통 UI는 components, 타입은 types에 둔다. 컴포넌트에 Mock 데이터를 직접 넣지 않는다.
- 상태는 화면 간 일관성을 유지하고 가능한 경우 버전이 있는 localStorage에 보존한다.
- PC 1440px 우선, 화이트·연회색 SaaS 디자인과 보라색·시안색 포인트를 사용한다.
- 매 단계 npm run build 및 가능한 테스트를 실행하고 변경 파일, 기능, 실행/빌드 결과, 미완료 사항을 보고한다. 실행하지 않은 검증을 성공으로 보고하지 않는다.
- 기획 전체와 단계별 범위는 docs/project-brief.md 및 docs/frontend-pages.md를 참조한다.

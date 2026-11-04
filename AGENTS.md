# DevMozip 작업 규칙

- 현재 목적은 기획 발표용 React 프론트엔드 프로토타입이다.
- 먼저 기존 코드와 문서를 확인하고 동작하는 기능을 보존한다.
- 요청받은 단계만 개발하며 완료 후 다음 단계를 자동 시작하지 않는다.
- 백엔드는 Java 21 / Spring Boot / Gradle을 유지한다. 사용자 승인 범위인 JWT 인증·권한·헬스체크·회원가입(회원·직군·기술 스택·관심 분야 저장)·협업 가능 상태 및 시간 관리(PATCH /api/v1/users/me/availability)와 배포 인프라만 구현한다. 그 외 업무 도메인 Controller, Service, Repository, DTO, Entity 및 실제 업무 API는 구현하지 않는다.
- PostgreSQL·Redis·인증 연결은 승인된 인증·회원가입/배포 인프라에 한한다. 기존 Mock 도메인을 DB로 이전하지 않는다. Gemini, Kakao, Toss Payments, WebSocket에 연결하지 않는다.
- 코드 변경은 develop에서 먼저 검증한다. main 병합·푸시는 사용자 요청이 있을 때만 한다. 인증 모드·팀 협업 안내는 docs/team-auth-deployment.md를 참조한다.
- 프론트엔드는 frontend/app의 Next.js App Router를 사용한다. 페이지 전용 컴포넌트와 CSS Module은 각 경로 근처 _components에, 재사용 UI는 components에 둔다. 상태·검증·저장은 lib, 원본 데이터는 mocks, 도메인 타입은 types에 둔다. src/pages·features·services·styles와 별도 라우터 설정을 다시 만들지 않는다. 컴포넌트에 Mock 데이터를 직접 넣지 않는다.
- 상태는 화면 간 일관성을 유지하고 가능한 경우 버전이 있는 localStorage에 보존한다.
- PC 1440px 우선, 화이트·연회색 SaaS 디자인과 보라색·시안색 포인트를 사용한다.
- 매 단계 npm run build 및 가능한 테스트를 실행하고 변경 파일, 기능, 실행/빌드 결과, 미완료 사항을 보고한다. 실행하지 않은 검증을 성공으로 보고하지 않는다.
- 기획 전체와 단계별 범위는 docs/project-brief.md 및 docs/frontend-pages.md를 참조한다.

- 커밋과 푸시는 사용자가 명시적으로 요청한 변경에 대해서만 수행한다.

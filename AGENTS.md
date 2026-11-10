# DevMozip 작업 규칙

- 기존 프론트엔드는 기획 발표용 프로토타입이다. AI 추천 백엔드는 별도 단계로 준비하며, 현재 요청된 범위만 작업한다.
- 먼저 기존 코드와 문서를 확인하고 동작하는 기능을 보존한다.
- 요청받은 단계만 개발하며 완료 후 다음 단계를 자동 시작하지 않는다.
- 백엔드는 Java 21 / Spring Boot / Gradle을 유지한다. 기존 구현 범위는 JWT 인증·권한·헬스체크·회원가입(회원·직군·기술 스택·관심 분야 저장)·협업 가능 상태 및 시간 관리(PATCH /api/v1/users/me/availability)·카카오 로그인·가입·연동과 비밀번호 없는 카카오 회원의 비밀번호 설정(docs/kakao-auth-api.md)·비밀번호 재설정과 변경(사용자 요청 2026-11-05, docs/password-reset-api.md)·프리미엄 구독과 정기결제(사용자 요청 2026-11-09, docs/premium-payment-plan.md)·팔로우(WBS 9.0, 사용자 요청 2026-11-09, docs/follow-plan.md·docs/follow-api.md)·개발자 탐색(WBS 6.1~6.10, 사용자 요청 2026-11-10, docs/explore-plan.md 1~5단계·docs/search-api.md. 고급 검색 6단계는 별도 요청)·SNS 피드(WBS 8.0, 사용자 요청 2026-11-10, docs/feed-plan.md·docs/feed-api.md)·알림(WBS 12.1~12.5, 사용자 요청 2026-11-10, docs/notification-plan.md·docs/notification-api.md. 프로젝트·리뷰 이벤트로 만드는 서비스 알림이며 외부 푸시·메일은 보내지 않는다)·개인정보 설정의 계정 잠금(비공개 계정, WBS 17.7, 사용자 요청 2026-11-10, docs/privacy-settings-plan.md·docs/privacy-settings-api.md)과 배포 인프라다. 메신저(WBS 11.1~11.9)와 프로젝트 단톡방(WBS 7.18~7.23)은 사용자 요청(2026-11-06)으로 승인된 docs/messenger-plan.md 범위에서 구현한다. AI 추천 업무 코드는 사용자가 구체적으로 요청한 단계에서만 추가한다. 문서 작성 요청은 기능 구현 승인이 아니다. 다른 업무 도메인은 구현하지 않는다.
- PostgreSQL·Redis·인증·회원가입 연결은 기존 승인 범위를 보존한다. 향후 AI 추천은 확인된 실제 DB 데이터를 사용하되 기존 Mock 도메인을 임의로 DB로 이전하지 않는다. Gemini에 연결하지 않는다. 결제(토스페이먼츠·카카오페이)는 사용자 요청(2026-11-09)으로 승인된 docs/premium-payment-plan.md 범위에서 포트원 V2 API로만 연결하며, 결제대행사 API를 직접 호출하지 않는다. Kakao는 사용자 요청(2026-11-05)으로 승인된 docs/kakao-oauth-plan.md 범위에서만 연결한다. 메일(SMTP)은 사용자 요청(2026-11-05)으로 승인된 docs/password-reset-plan.md 범위에서만 연결한다. WebSocket과 Redis Pub/Sub은 사용자 요청(2026-11-06)으로 승인된 docs/messenger-plan.md 범위에서만 사용한다. 업로드 이미지 폴더(IMAGE_STORAGE, public/images)와 nginx의 /images/ 제공은 사용자 요청(2026-11-10)으로 승인된 docs/feed-plan.md 범위에서 쓴다. 외부 AI 연결은 별도 명시적 요청이 필요하다.
- 코드 변경은 develop에서 먼저 검증한다. main 병합·푸시는 사용자 요청이 있을 때만 한다. 인증 모드·팀 협업 안내는 docs/team-auth-deployment.md를 참조한다.
- 프론트엔드는 frontend/app의 Next.js App Router를 사용한다. 페이지 전용 컴포넌트와 CSS Module은 각 경로 근처 _components에, 재사용 UI는 components에 둔다. 상태·검증·저장은 lib, 원본 데이터는 mocks, 도메인 타입은 types에 둔다. 회원 입력의 개발 직군·기술 스택·관심 분야 허용 값은 백엔드 enum이 기준이다. src/pages·features·services·styles와 별도 라우터 설정을 다시 만들지 않는다. 컴포넌트에 Mock 데이터를 직접 넣지 않는다.
- 상태는 화면 간 일관성을 유지하고 가능한 경우 버전이 있는 localStorage에 보존한다.
- PC 1440px 우선, 화이트·연회색 SaaS 디자인과 보라색·시안색 포인트를 사용한다.
- 매 단계 npm run build 및 가능한 테스트를 실행하고 변경 파일, 기능, 실행/빌드 결과, 미완료 사항을 보고한다. 실행하지 않은 검증을 성공으로 보고하지 않는다.
- 기획 전체와 단계별 범위는 docs/project-brief.md 및 docs/frontend-pages.md를 참조한다.

- 커밋과 푸시는 사용자가 명시적으로 요청한 변경에 대해서만 수행한다.

## 새 채팅에서 먼저 읽을 문서

- AI 기능 및 확정 정책: [docs/ai-matching.md](docs/ai-matching.md)
- AI API 확정 범위와 미확정 계약: [docs/ai-api-spec.md](docs/ai-api-spec.md)
- 구현 현황·검증 결과·다음 작업: [docs/ai-progress.md](docs/ai-progress.md)
- 인증/BFF·브랜치 운영: [docs/team-auth-deployment.md](docs/team-auth-deployment.md)
- 카카오 OAuth 계획·진행 기록: [docs/kakao-oauth-plan.md](docs/kakao-oauth-plan.md)
- 비밀번호 재설정·변경 계획·진행 기록: [docs/password-reset-plan.md](docs/password-reset-plan.md)
- 메신저·프로젝트 단톡방 계획·진행 기록: [docs/messenger-plan.md](docs/messenger-plan.md)
- 팔로우 계획·진행 기록과 API: [docs/follow-plan.md](docs/follow-plan.md), [docs/follow-api.md](docs/follow-api.md)
- 개발자 탐색 계획·진행 기록과 API: [docs/explore-plan.md](docs/explore-plan.md), [docs/search-api.md](docs/search-api.md)
- SNS 피드 계획·진행 기록과 API: [docs/feed-plan.md](docs/feed-plan.md), [docs/feed-api.md](docs/feed-api.md)
- 알림 계획·진행 기록과 API: [docs/notification-plan.md](docs/notification-plan.md), [docs/notification-api.md](docs/notification-api.md)
- 개인정보 설정(계정 잠금) 계획·진행 기록과 API: [docs/privacy-settings-plan.md](docs/privacy-settings-plan.md), [docs/privacy-settings-api.md](docs/privacy-settings-api.md)

이전 기획서와 API 초안의 Mock 정책·후보 URL을 확정된 서버 계약으로 사용하지 않는다. AI 정책은 위 문서의 확정 항목을 기준으로 삼고, TODO는 임의로 결정하지 않는다. 사용자 최신 요청이 문서와 다르면 해당 범위에 반영하고 변경 근거를 기록한다.

## 백엔드 구조와 팀 작업 경계

- 작업 전 실제 Spring Boot·Java·Gradle 버전, DB 설정, JPA 의존성과 엔티티 유무를 확인한다. 현재 설정은 Spring Boot 4.1.2, Java 21, Gradle 9.8.2, PostgreSQL 17, Spring Data JPA이며 확인일은 2026-11-03이다. 설정 변경 여부는 파일로 재확인한다.
- 소스 루트는 `backend/src/main/java/com/devmozip/backend`, 설정은 `backend/src/main/resources`, 테스트는 `backend/src/test/java`다. 기존 `auth` 패키지, 탭 들여쓰기, 생성자 주입, Java 클래스·메서드 네이밍을 따른다. AI 패키지의 세부 배치는 구현 단계에서 기존 구조를 확인하고 정한다.
- Controller는 HTTP 입출력과 인증 주체 전달, Service는 정책·계산·트랜잭션, Repository는 영속성 접근, DTO는 API 입출력을 담당한다. 엔티티를 API 응답 계약으로 직접 사용하지 않는다. 기존 인증 DTO가 패키지 바로 아래에 있으므로 별도 전역 구조 개편을 강제하지 않는다.
- AI API 기본 경로는 `/api/v1/ai`다. 사용자 식별은 Spring Security 인증 정보에서 얻는다. 요청 본문·쿼리로 받은 사용자 ID를 요청자 신원으로 신뢰하지 않는다.
- ERD와 실제 엔티티·DDL을 먼저 비교한다. 누락된 엔티티·컬럼·연관관계와 불일치는 먼저 보고하며 임의로 추가하지 않는다. ERD가 없으면 대조 불가로 기록한다.
- 사용자·프로필·프로젝트·포트폴리오·구독 등 다른 팀원 담당 코드와 공통 인증·배포 설정을 임의로 수정하지 않는다. AI 구현에 변경이 필요하면 필요한 계약과 영향을 먼저 보고한다. 관련 없는 리팩터링과 다른 사람의 미커밋 변경 되돌리기를 하지 않는다.

## 단계 종료 규칙

- 구현 시작 전 이번 단계의 범위를 확인하고, 완료 후 다음 단계를 자동 시작하지 않는다. 문서의 예정 단계는 실행 지시가 아니다.
- 매 단계 `frontend`에서 `npm run build`와 가능한 관련 테스트를 실행한다. 백엔드 코드를 변경한 단계는 `backend`에서 `./gradlew test` 및 필요한 관련 검증을 실행한다. 문서만 변경한 단계에는 새 기능 테스트를 만들지 않는다.
- 실행 명령·성공/실패·실행하지 못한 이유를 정확히 기록한다. 과거 보고서나 테스트 코드의 존재를 이번 실행 성공으로 보고하지 않는다. Mock 테스트 통과는 실제 DB·AI API 검증을 뜻하지 않는다.
- `docs/ai-progress.md`에 변경 파일, 완료 기능, 테스트 결과, 미완료 문제와 다음 작업을 갱신한다. 기능 문서·API 문서도 실제 확정 내용과 일치시킨다.

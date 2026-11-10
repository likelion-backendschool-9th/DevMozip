# 프로젝트 지원·승인 및 팀원 관리 (#48)

## 구현 범위

기존 프로젝트·회원 인증·JSONB 모집 직군·프로젝트 관리 구조를 재사용한다. 프론트 Mock ON 화면과 스타일은 변경하지 않고, OFF의 API 화면은 기존 공고/지원 모달/지원자 카드/팀원 카드 CSS와 공통 UI를 사용한다. 모바일 추가 작업은 하지 않았다.

| 기능 | API |
|---|---|
| 지원 | POST /api/v1/projects/{projectId}/applications |
| 지원자 목록 (주최자) | GET /api/v1/projects/{projectId}/applications?status=PENDING |
| 승인·거절·본인 취소 | PATCH /api/v1/projects/{projectId}/applications/{applicationId} |
| 본인 지원 상태·선택 정보 | GET /api/v1/projects/{projectId}/application-options |
| 현재 팀원 (팀원 전용) | GET /api/v1/projects/{projectId}/members?jobRole=SRE |
| 지원 중인 프로젝트 | GET /api/v1/projects/mine?tab=APPLYING |

지원 본문은 `jobRole`, `portfolioIds`(서로 다른 본인 포트폴리오 1~3개), `introduction`(10~1500자)다. 변경 본문은 `status`(APPROVED/REJECTED/CANCELLED), 거절 시 `rejectionReason`(한 줄 1~200자), 패널티 대상 취소 시 `acknowledgePenalty: true`다. 사용자 ID는 인증 정보로 확인하며 클라이언트 신원을 신뢰하지 않는다. BFF는 허용 필드만 전달한다.

## 정책과 트랜잭션

- 프로젝트에 실제 등록한 모집 직군만 지원·승인 가능하다. 현재 역할별 ACTIVE 참여 인원을 확인한다. 주최자는 직군별 정원을 소모하지 않는다.
- 승인·지원·취소는 같은 프로젝트 행 잠금을 사용한다. 마지막 자리의 동시 승인을 막고 승인과 팀원 등록을 같은 트랜잭션으로 처리한다.
- 프로젝트·회원별 지원 이력은 한 건이며 취소/거절 이후 재지원할 수 없다. 제출 포트폴리오는 연결 테이블을 사용하며 제목 스냅샷과 순서를 추가하지 않는다.
- 지원자 목록은 현재 주최자만, 본인 지원 상태는 본인만 조회한다. 거절 사유는 이 두 응답에서만 제공하며 공개 상세에는 포함하지 않는다.
- PENDING 취소에는 패널티가 없다. APPROVED 취소는 서울 날짜 기준 모집 마감 7일 전까지(해당 날짜 포함) 면제다. 이후에는 안내 확인이 필요하다. 반복 취소는 409로 차단한다.
- 승인 후 취소는 팀원을 LEFT로 변경하고 종료 요청을 제거한다. 진행 중 남은 팀원의 종료 동의가 절반을 초과하면 종료를 즉시 다시 판정한다.
- 구독을 해지한 기존 주최자도 기존 공고를 관리할 수 있다.

## 연결 경계와 미완료 사항

실제 팀 DB 확인 당시 `project_applications`, `project_application_portfolios`, `project_members`는 존재하나 포트폴리오 원본 테이블은 없었다. 기존 ERD/DDL을 유지하고 다른 팀 담당 테이블이나 임의 포트폴리오 ID를 생성하지 않았다.

`ProjectPortfolioAccess`가 본인 포트폴리오 목록·존재·소유권을 확인한다. 기본 어댑터는 사용 불가 상태로 동작하며 선택 정보의 `portfoliosAvailable=false`, `canApply=false`를 반환한다. 실제 신규 지원은 503으로 차단한다. 포트폴리오 담당 계약이 확정되면 이 인터페이스의 실제 어댑터를 연결해야 한다. 기존 지원 이력의 조회·승인·거절·취소는 사용할 수 있다. 포트폴리오 제목·상세 열람도 원본 연동 전까지 제공하지 않는다.

`ProjectApplicationChanged` 이벤트는 지원/승인/거절/취소와 고정 eventKey, 패널티 대상 여부를 제공한다. 알림·채팅 소비자와 신뢰 점수 계산기는 아직 연결하지 않았다. 숫자 패널티를 임의 계산하지 않는다. 이벤트는 현재 프로세스 내부 이벤트이며 영속 큐/재전송을 보장하지 않는다. 해당 담당자는 AFTER_COMMIT 소비·중복 제거와 필요 시 outbox를 설계해야 한다. 이번 단계에서 리뷰·강퇴 투표를 새로 구현하지 않는다.

## 주요 변경 파일

- backend entity/ProjectApplication, ProjectApplicationPortfolio, ProjectMember
- backend controller/ProjectApplicationController, dto/project/*Application*, ProjectMemberResponse
- backend service/ProjectApplicationService, ProjectPortfolioAccess, impl/ProjectApplicationServiceImpl
- backend config/ProjectPortfolioAccessConfiguration, event/ProjectApplicationChanged
- backend repository/ProjectApplicationRepository, ProjectApplicationPortfolioRepository, ProjectMemberRepository, ProjectQueryRepository
- frontend/app/api/projects/[projectId]/{applications,application-options,members}/route.ts 및 applications/[applicationId]/route.ts
- frontend/app/projects/_components/api/ApiProjectApplications.tsx, ApiProjectDetail.tsx
- frontend/types/projectApplicationApi.ts, lib/projects/projectApplicationRoutes.test.ts
- backend project/ProjectApplicationIntegrationTest 및 기존 조회 테스트

## 검증 (2026-11-06)

- PostgreSQL Testcontainers 신규 통합 테스트 7개 통과: 승인·팀원 일관성, 포트폴리오 중복/개수/소유권, 미연동 차단, 권한·거절 사유, 서울 날짜 7일 경계, 마지막 자리 동시 승인, 중복 지원/직군/팀원 권한, 탈퇴 후 종료 정족수 재판정.
- frontend `npm test -- --maxWorkers=1`: 63개 파일 / 668개 통과. BFF 허용 필드·보안 차단·필터·동의 값 테스트 포함.
- frontend `npm run build -- --webpack`: 통과. 최초 제한 환경의 TypeScript 하위 프로세스 오류 후 실행 환경을 변경해 재검증했다.
- backend `./gradlew test`: 439개 중 427개 통과, 11개 스킵, 기존 KakaoClientTest.java:153 로그 검증 1개 실패. 카카오 코드는 수정하지 않았다.
- 실행용 백엔드 재시작 시 기존 PostgreSQL 스키마 검증 및 기동 성공. 팀 DB 테이블 자동 변경/샘플 추가를 수행하지 않았다.
- 프로젝트 패키지 재실행: 100개 중 89개 통과, 11개 스킵, 실패 0개.
- 1440px Chromium: 주최자 지원자 조회 200, 타인 조회 403, 팀원 카드/탭, 지원 선택 정보와 원본 미연동 차단, Mock 복원 확인. 가로 넘침과 pageerror 없음. 실제 공유 DB는 읽기만 수행했다.
- 실제 신규 지원 전체 흐름은 포트폴리오 원본 미연동으로 검증하지 못했다. 통합 테스트의 포트폴리오 어댑터는 테스트 대역이며 실제 DB 포트폴리오 연동 성공을 의미하지 않는다.

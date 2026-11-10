# 프로젝트 종료 후 협업 리뷰 (#50)

현재 기준: 2026-11-06. #49 강퇴 투표는 보류하며 기존 Mock 디자인을 유지한다. 작업 브랜치는 feat/50-project-reviews이며 리뷰 구현은 e879f41로 커밋했다. 사용자 요청으로 develop 대상 PR을 준비한다.

## 데이터 구조

| 테이블 | 역할 |
|---|---|
| reviews | 평가자/대상 project_members FK, 별점, 요청/제출 상태, 토큰 해시, 기한 및 업무 시각 |
| review_keywords | 등록 키워드 원본, name 유일, is_active로 신규 선택 여부 관리 |
| review_keyword_selections | review_id/keyword_id FK, 두 값의 UNIQUE, 공통 생성·수정 시각 |

reviews에 selected_keywords 컬럼을 두지 않는다. 선택 관계는 연결 테이블에서만 저장하고 조회·키워드 집계 시 원본을 조인한다. 응답의 keywords 이름 배열은 기존 형식을 유지한다. 비활성 원본의 과거 선택도 표시하며 원본 이름을 바꾸면 기존 리뷰 표시명에도 반영된다. 원본 삭제보다 비활성화를 사용한다.

기본 긍정 키워드는 14개다. 사용자 임의 추가/직접 입력 API는 없고 등록된 활성 목록에서 리뷰마다 0~5개만 선택한다. 별점 1~5점은 필수다. 선택하지 않으면 연결 행 0개이며 별점만 제출한다. 중복·미등록·비활성·6개 이상 선택은 서버에서 거절한다.

## 요청·제출 정책

- 프로젝트 종료 이벤트와 같은 트랜잭션에서 당시 ACTIVE 팀원(주최자 포함)의 평가자→대상 PENDING 행을 생성한다. 본인은 제외한다. 종료 재처리는 기존 대상을 바꾸거나 요청을 중복 생성하지 않는다.
- 프로젝트+평가자+대상은 유일하다. 별도 리뷰 요청 테이블은 없다. 종료 이후 참여 상태가 달라져도 종료 당시 평가 대상은 유지한다.
- 만료는 completed_at + 정확히 30일이다. 현재 시각이 만료 시각 이상이면 제출을 차단한다.
- 미제출 대상 전체를 한 번에 제출하며 일부/중복 대상 제출, 타인 링크, 제출 후 수정은 거절한다. 자유 서술은 저장하지 않는다.
- 프로젝트 행 잠금으로 종료 처리·전체 제출·링크 재발급·재알림을 직렬 처리한다. 리뷰 상태와 선택 연결은 함께 커밋/롤백한다.
- 32바이트 난수 토큰을 생성해 SHA-256 해시만 DB에 저장한다. 같은 평가자는 토큰과 만료를 공유한다. 재발급하면 이전 링크가 무효화된다. 토큰은 URL fragment와 제출 본문으로 전달하며 localStorage에 저장하지 않는다. 토큰을 포함하는 DTO/이벤트의 toString은 비밀을 숨긴다.
- 재알림은 미완료 평가자에게 마지막 알림(없으면 생성)부터 3일 후 한 번 처리한다. last_reminded_at으로 중복을 막고 완료자·만료자는 제외한다. 재알림은 새 링크를 발급하며 과거 누락 알림을 일괄 생성하지 않는다. 실제 스케줄러는 없다.
- 받은 리뷰 집계는 SUBMITTED만 포함한다. 평균·별점 분포·키워드 빈도·받은 리뷰 목록을 제공하는 내부 서비스 계약을 유지한다.

## API

| 메서드 | 경로 | 권한/기능 |
|---|---|---|
| GET | /api/v1/projects/{projectId}/reviews/me | 본인 요청·대상·제출 상태·DB 활성 키워드 |
| POST | /api/v1/projects/{projectId}/reviews/me/link | 본인 작성 링크 발급/재발급 |
| PUT | /api/v1/projects/{projectId}/reviews/me | 본인 최신 토큰으로 전체 제출 |
| GET | /api/v1/projects/{projectId}/reviews/status | 주최자의 요청 현황, 비밀 토큰 제외 |
| POST | /api/v1/projects/{projectId}/reviews/reminders | 주최자의 재알림 대상 처리 |

제출 본문은 `{token, entries:[{targetMemberId, rating, keywords:[]}]}`다. 평가자는 Spring Security로 식별하고 BFF는 허용 필드만 전달한다. 다른 사람의 링크만 알아도 제출할 수 없으며 본인 인증과 최신 토큰이 모두 필요하다. 응답에 token_hash를 포함하지 않는다.

## 프론트

Mock ON은 기존 시연 상태·날짜 시뮬레이션을 유지한다. OFF는 기존 리뷰 페이지 CSS Module·별점 라디오·선택 칩·팀원 카드·공통 버튼을 재사용한다. 키워드 선택 수/5를 표시하고 6번째 선택을 막으며 선택 해제는 허용한다. 관리자 원본 목록의 과거 12개 제한은 제거했지만 일반 회원의 키워드 관리 권한을 추가하지 않았다. 기존 저장된 관리자 목록은 임의 초기화하지 않는다.

종료 프로젝트에서 리뷰 페이지로 이동하고, 본인 인증 후 링크를 발급해 전체 제출/결과를 확인한다. 주최자 화면은 기존 리뷰 요청 현황 스타일로 제출 수와 만료를 표시한다. 실제 환경에서는 서버 시간만 판정에 사용하고 날짜 경계는 주입 Clock 기반 통합 테스트로 검증한다. 새 디자인/모바일 작업은 하지 않았다.

## SQL 적용

- 신규 DB: project-schema.sql → review-keywords.sql → review-keyword-selections.sql → project-demo-data.sql.
- 기존 DB: review-keyword-selections.sql로 선택값을 이관하고 reviews-normalize-keywords.sql로 원본 배열과 연결 이름이 모두 일치하는지 검사한 뒤 중복 컬럼을 제거한다. 불일치하면 롤백한다.
- 기존 DB 이관 SQL은 다른 환경에 필요하므로 삭제하지 않는다. 현재 DB에 중복 컬럼을 다시 생성하는 실행 코드/설정은 없다.
- 실제 팀 DB에 리뷰 9건·연결 12건·긍정 키워드 14개가 있으며 기존 리뷰 내용/상태를 보존했다. 기존 종료 이력에 요청을 임의 소급 생성하지 않았다.

## 미연결 범위

실제 알림 발송·영속 outbox/재전송은 미연결이다. ProjectReviewNotificationEvent 소비자는 AFTER_COMMIT에 연결해야 하며 현재 프로세스 종료 시 발송을 보장하지 않는다. 원문 토큰은 발급 응답/이벤트 순간에만 존재한다.

프로필의 실제 회원 식별·공개 범위 API·신뢰 점수 계산식 연결은 남았다. 집계 서비스를 준비했으며 기존 Mock 온도 공식을 실제 점수로 적용하지 않는다. 키워드 관리용 관리자 CRUD API도 추가하지 않았다.

## 주요 파일

- backend entity/ProjectReview, ReviewKeyword, ReviewKeywordSelection
- backend repository/ProjectReviewRepository, ReviewKeywordRepository, ReviewKeywordSelectionRepository
- backend service/ProjectReviewService, controller/ProjectReviewController, dto/project/*Review*, event/ProjectReviewNotificationEvent
- backend project/ProjectReviewIntegrationTest 및 테스트 DB 자식 테이블 우선 정리
- frontend app/api/projects/[projectId]/reviews/**/route.ts, reviews/_components/ApiProjectReviewForm.tsx
- frontend app/projects/_components/api/ApiProjectReviewStatus.tsx, ApiProjectDetail.tsx
- frontend types/projectReviewApi.ts, lib/projects/projectReviewRoutes.test.ts, Mock 리뷰 검증/목록

## 검증 이력과 이번 정리

정규화 단계에서 frontend 빌드/TypeScript와 674개 테스트, PostgreSQL 리뷰 통합 10개가 통과했다. 전체 backend 449개 중 437개 통과·11개 스킵·기존 KakaoClientTest.java:153 로그 검증 1개 실패였고 프로젝트 110개 중 99개 통과·11개 스킵이었다. 실제 DB를 사용한 1440px Chromium 조회/권한 확인에 pageerror/가로 넘침은 없었다. UI의 5개 선택·6번째 차단·해제 검증에는 응답 대역을 사용했으며 공유 DB에 신규 리뷰를 제출하지 않았다.

이번 정리는 DB 미연결 시 임시 인터페이스/설정인 ProjectReviewKeywords·ProjectReviewConfiguration, 활성 키워드 중복 전체 조회·중복 검증, 미사용 import, 지원 목록의 오래된 501 미구현 안내를 제거한다. API·권한·트랜잭션·토큰 정책·디자인은 유지한다. 중복/미등록/비활성 검증은 실제 키워드 Repository 조회와 DTO 제약에서 계속 수행한다. 이전 JSONB 스냅샷 보존 안내는 현재 연결 구조로 통일했다.


### 이번 정리 검증 결과

- backend ./gradlew test: 449개 중 438개 통과, 11개 스킵, 실패 없음. 이전 카카오 로그 테스트는 이번 실행에서 통과했으며 관련 코드는 수정하지 않았다.
- frontend npm run build -- --webpack/TypeScript 성공, npm test -- --maxWorkers=1: 64개 파일·674개 통과.
- 실행 서버 재시작 후 실제 DB 리뷰 조회 3개·제출 후 변경 차단·타인 조회 403 확인. 1440px Chromium에 pageerror/가로 넘침 없음.
- 정리한 파일: ProjectReviewService, 삭제한 ProjectReviewKeywords/ProjectReviewConfiguration, ProjectLifecycleRepository 주석, 프로젝트 BFF의 미사용 import, ApiProjectDetail import, 리뷰 클라이언트 선언 공백, projectProxy의 구형 501 안내, 이 문서/청사진/진행 기록.
- git diff --check 통과. DB 스키마·데이터와 YAML은 이번 정리에서 변경하지 않았다. 기존 Mock·다른 도메인·후속 연동용 집계 서비스 및 마이그레이션 SQL은 보존한다.


## PR 준비: 최신 develop 통합

- 사용자 요청으로 리뷰 구현 커밋 e879f41을 생성하고 origin/develop 0719b2f의 프로필 외부 링크 변경을 통합했다. 문서 충돌은 양쪽 진행 기록을 보존했다. 선행 #81/#85가 미병합이므로 이번 develop 대상 PR에도 포함된다.
- 통합 후 backend 전체 466개 중 454개 통과·11개 스킵·기존 KakaoClientTest.java:153 로그 검증 1개 실패. 프로젝트 영역은 110개 중 99개 통과·11개 스킵·실패 없음. 카카오 코드를 수정하지 않았다.
- 이번 PR 준비 단계에서 팀 DB/스키마를 변경하지 않았으며 통합된 최신 develop으로 실제 DB·브라우저를 다시 검증하지 않았다. 앞선 실제 DB/브라우저 결과는 구현 단계 기록이다.
- 통합 후 frontend npm run build -- --webpack/TypeScript 검사 성공, npm test -- --maxWorkers=1: 66개 파일·704개 통과.
- PR #86 생성 직후 develop에 #63이 병합되어 f47ca02를 추가 통합했다. 충돌 8개 파일을 비교한 결과 현재 구현이 #46 기능과 후속 직접 입력/인가/지원/상태 확장을 모두 포함하므로 확장 구현을 보존했다. 해결 직후 파일 트리가 직전 검증 상태와 동일함을 git diff HEAD --quiet로 확인했다. 프론트 소스 변경은 없다.
- f47ca02 추가 통합 후 최종 backend 재실행: 466개 중 455개 통과·11개 스킵·실패 없음. 직전 실행의 카카오 로그 실패는 이번 재실행에서 통과했으며 카카오 코드는 변경하지 않았다. frontend 704개/빌드 통과 결과를 유지한다. PR #86은 GitHub mergeable=true 확인.

## 2026-11-09 최신 develop a04608f 통합

진행 문서의 양쪽 기록을 보존하고 GitHub 충돌 목록에 있던 프로젝트 코드의 자동 병합 결과를 확인했다. 리뷰 업무 코드는 직전 구현을 유지한다. 문자열 모집 직군 계약에 맞춰 AI 테스트 입력 2곳만 수정했다. 프론트 빌드·TypeScript/795개 테스트 통과. 백엔드 전체 631개 통과·12개 스킵·기존 카카오 로그 검증 1개 실패, 프로젝트는 103개 통과·11개 스킵·실패 없음(리뷰 통합 10개 포함). 팀 DB/서버/브라우저 재검증은 하지 않았다. 현재 로컬 병합 상태이며 커밋·푸시는 별도 요청 전 진행하지 않는다.

## DTO·이벤트 명명 정리 (2026-11-10)

응답 record는 dto/project에 ProjectReviewResponse, ProjectReviewLinkResponse, ProjectReviewRequestStatusResponse, ProjectReviewSummaryResponse, ProjectReviewReminderResponse로 구분한다. SubmitProjectReviewsRequest는 입력 DTO로 유지한다. API 경로·JSON 필드와 기존 정책은 바꾸지 않는다.

ProjectReviewNotificationEvent는 최초 리뷰 요청과 재알림을 알림 담당자에게 전달하는 내부 이벤트다. HTTP 요청 DTO나 실제 발송 완료 이벤트가 아니며 AFTER_COMMIT 소비·영속 발송은 기존 후속 연동 범위다. 링크 및 이벤트의 toString에서는 토큰을 계속 숨긴다.

## 프로필 받은 리뷰 조회 연결 (2026-11-10, #123)

사용자 요청으로 프로필 담당 브랜치 feat/123-profile-posts에서 기존 received 집계를 재사용하는 본인/공개 리뷰 API와 화면을 추가했다. [프로필 리뷰 계약](profile-reviews-api.md)을 따른다. 공개 프로필에서도 별점·키워드·작성자·프로젝트명을 표시하되 타인에게 비공개/삭제 프로젝트 이름·링크는 숨긴다. 기존 리뷰 요청·제출·Entity·집계 서비스는 수정하지 않는다. 신뢰 온도는 기존 프로필의 저장값을 표시할 뿐 실제 점수 계산식 연결을 완료한 것은 아니다. #110 계정 비공개 공통 정책과의 통합은 여전히 남는다.

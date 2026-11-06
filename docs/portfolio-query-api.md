# 포트폴리오 목록·상세 조회 — #84

## 범위

사용자 요청으로 기존 Mock 포트폴리오 화면을 보존하면서 실제 조회 API와 BFF를 추가했다. 등록·수정·삭제·파일 업로드, AI 추천 점수·Gemini 분석, 프로젝트 지원 연결은 이번 범위에 없다. ERDCloud 및 공유 DB 스키마는 변경하지 않았다.

작업 브랜치: `feat/84-portfolio-query`, 기준 `origin/develop`.

## 구조

- `PortfolioQueryController`: 요청·인증 주체 전달
- `PortfolioQueryService`: 이메일 기반 회원 식별, 소유권·현재 DB 역할·계정 상태 확인, 공개/상세 응답 구성
- `PortfolioQueryRepository`: NamedParameterJdbcTemplate로 기존 ERD의 `portfolios` 조회. Entity·DDL·업무 데이터 생성 없음
- `PortfolioSummaryResponse` / `PortfolioPageResponse` / `PortfolioDetailResponse`: record 응답 DTO
- Next BFF: 쿠키의 기존 인증 정보를 백엔드에 전달하고 응답을 검증·필드 제한. 모든 응답 `Cache-Control: no-store`
- `ApiPortfolioTab` / `ApiPortfolioDetail`: API 카드·상세, 로딩·빈 목록·권한 오류·실패·재시도·페이지 이동 표시. 계정·대상 변경 시 기존 요청 취소 및 이전 응답 숨김

조회는 read-only REPEATABLE_READ 트랜잭션을 사용한다. 공유 `Member` Entity 및 인증 응답은 수정하지 않았다.

## API

| 메서드·경로 | 인증 | 설명 |
| --- | --- | --- |
| GET `/api/v1/users/me/portfolios?page=0&size=20` | 필요 | 본인의 PUBLIC·PRIVATE 목록 |
| GET `/api/v1/users/{slug}/portfolios?page=0&size=20` | 선택 | ACTIVE 회원의 PUBLIC 기본 목록 |
| GET `/api/v1/portfolios/{id}` | 필요 | 권한이 확인된 상세 |

- 사용자 slug는 `users.tag`. 예약 경로 me를 별도로 처리한다. 본인 목록은 tag가 NULL이어도 이메일로 식별한다.
- page=0부터, 최대 1,000,000. size 기본 20, 1~100.
- created_at 내림차순(NULL은 마지막), 동률은 id 내림차순.
- 목록: items/page/size/totalElements/totalPages.
- 기본 DTO: id/userId/title/thumbnailUrl/topic/summary/role/techStack/visibility/createdAt/updatedAt.
- 상세 DTO: portfolio(기본 DTO), author(id/nickname/tag/profileImage), description, attachments(name/url).
- 목록에는 description·attachments·인증/결제 정보가 없다. 실제 AI 점수 계산과 프로젝트 지원 API는 연결하지 않았다.
- BFF 경로는 `/api/profiles/{slug}/portfolios`, `/api/profiles/me/portfolios`, `/api/portfolios/{id}`. userId/email 등 신원 쿼리는 전달하지 않는다.

## 권한 및 잠정 계약

추가 합의가 도착하기 전 아래는 기존 화면 정책과 제공된 ERD를 기준으로 한 구현 기준이다. 실제 SNS/구독 정책으로 확정된 사실을 뜻하지 않는다.

| 조건 | 결과 |
| --- | --- |
| 본인 ACTIVE/WARNED | 자신의 PUBLIC·PRIVATE 목록/상세 가능 |
| 익명 | 타인의 PUBLIC 기본 목록만 가능 |
| 일반 USER/OWNER | 타인 PUBLIC 기본 목록 가능, 상세 403 |
| PREMIUM/ADMIN | ACTIVE 작성자의 PUBLIC 상세 가능 |
| 타인 PRIVATE·미확정 visibility·비활성 작성자 | 상세 404, 목록 비노출 |
| 정지된 요청자 | 인증 단계 또는 서비스에서 차단 |

상세 권한은 현재 `users.role`을 기준으로 검사한다. 프론트 구독 데모·JWT의 오래된 PREMIUM 값으로 권한을 부여하지 않는다. 실제 구독 테이블/만료 판정 원천은 확인되지 않아 추가하지 않았다. ADMIN도 다른 사람 PRIVATE는 열람하지 않는다.

ERD 필드 매핑:

| DB | 응답 | 처리 |
| --- | --- | --- |
| id / user_id | id / userId | 실제 숫자 ID |
| thumbnail_url / topic | thumbnailUrl / topic | 목록·상세 대표 이미지/주제 |
| summary / role | summary / role | 한 줄 소개/담당 역할 |
| tech_stack JSON | techStack | 문자열 배열, NULL은 빈 배열 |
| description TEXT | description | 일반 텍스트로 표시, HTML 실행 없음 |
| attachments JSON | attachments | 잠정 형식 `[{"name":"설계","url":"https://..."}]`, NULL은 빈 배열 |
| visibility | visibility | PUBLIC·PRIVATE만 알려진 값으로 처리 |
| created_at / updated_at | createdAt / updatedAt | 공통 LocalDateTime 형식, 표시 시 한국 시각 가정 |

프론트 자유형 작성기는 blocks·HTML/Markdown·기간·팀 규모·다중 분야를 갖지만 제공 ERD에 대응 필드/저장 계약이 없다. 그 값을 임의 컬럼으로 추가하거나 실제 값처럼 생성하지 않았다. 실제 조회 상세는 기존 hero·대표 이미지·개요·목차·본문 스타일을 재사용하며 ERD의 설명을 텍스트로 보여준다. blocks 저장 규격은 등록/수정 단계에서 별도 합의해야 한다. 첨부 JSON 형식도 담당자 확인 후 필요한 경우 함께 조정해야 한다.

첨부파일은 서버가 가져오거나 실행하지 않고 안전한 http(s)/동일 출처 URL만 화면에 연결한다. 상세 파일의 실제 저장소 공개/서명 URL 정책은 파일 저장 단계에서 정해야 하며, 이번 API가 외부 저장소의 직접 접근 권한까지 제어하지는 않는다.

## 기존 화면 보존 및 연동 경계

- 실제 프로필 탭은 ApiPortfolioTab으로 연결한다. 기존 카드·개인 화면 사이드바 유지.
- 숫자 ID의 `/portfolios/{id}`는 API 상세를 사용한다. API 실패를 Mock 데이터로 대체하지 않는다.
- 기존 문자열 ID의 Mock 포트폴리오 상세는 `PrototypePortfolioDetail`로 이동해 작성기·미리보기와 함께 보존한다.
- API 목록의 등록 버튼은 등록 API 연결 전까지 비활성화한다. API 상세에서 Mock 수정·삭제·AI 피드백을 수행하지 않는다.
- 외부 링크 PR #66도 ApiProfileTabs를 수정하므로 병합 시 양쪽 기능을 함께 보존해야 한다. 최신 develop에서 구현했으며 미병합 PR을 임의로 포함하지 않았다.
- 유리님 AI #32/#35/#83: 공개 기본 DTO와 권한 확인된 상세 데이터 계약을 맞출 결합 지점이다. AI 엔진 파일은 수정하지 않았다.
- 프로젝트 #48: portfolios.id/user_id를 사용한 소유권 확인 및 제출 연결, 삭제/비공개 전환 시 정책은 별도 결합이다.

## 오류 및 DB 준비 상태

400 잘못된 조회 조건, 401 미인증, 403 상세 권한/계정 상태, 404 미존재·공개 불가, 503 테이블 미준비·조회 오류·JSON 구조 불일치. BFF는 연결/응답 검증 실패를 502로 반환한다. 내부 SQL·접속 정보는 전달하지 않는다. 실패를 빈 목록으로 바꾸지 않는다.

공유 개발 PostgreSQL 메타데이터를 읽기 전용으로 확인한 결과 `portfolios`와 `subscriptions`는 없었다. API의 실제 공유 DB 정상 응답은 테이블 준비와 계약 확인 후 검증할 수 있다. 이번 테스트용 portfolios 및 데이터는 별도 PostgreSQL 17 Testcontainers 안에서만 생성했다.

## 검증

- 신규 PostgreSQL 통합 테스트 18개: 공개/본인 목록, NULL, 정렬·페이지, 인증·소유권·실제 DB 역할, PRIVATE 비노출, 민감 정보 제한, 데이터 미변경, 테이블/JSON 오류, 잘못된 조건.
- 신규 프론트 테스트 31개: DTO·BFF 검증, 위험 URL 제거, 응답 식별자 일치, 오류 구분, 카드·상세 렌더링·본문 이스케이프. 기존 프로필 탭 테스트의 portfolio 기대값을 연결 전 안내에서 로딩 상태로 갱신했다.
- 최종 전체 테스트·빌드 결과는 docs/ai-progress.md의 이번 단계 기록을 따른다. 공유 DB 실데이터와 브라우저 수동 확인은 미실행이다.

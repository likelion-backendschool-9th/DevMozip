# 좋아요·포트폴리오 테이블 수동 검토안

공유 DB의 reports 실제 컬럼과 프로젝트 신고의 Report Entity를 참고했다. 신고의 업무 필드를 복제하지 않고 IDENTITY ID, created_at/updated_at의 TIMESTAMP WITHOUT TIME ZONE, 명시적 외래키 형식을 맞췄다. 피드 값과 이름은 원격 feat/91-feeds의 docs/feed-plan.md(2026-11-09)를 기준으로 한다.

**2026-11-09 사용자 위임으로 공유 개발 DB(devmozip, 외부 포트 5442)에 세 테이블을 적용했다. 재실행하지 않는다.**

PostgreSQL 17 도구로 public 스키마를 백업한 뒤 하나의 트랜잭션으로 생성했다. 적용 직후 feeds/feed_likes/portfolios는 각각 0행이며 기존 테이블·데이터는 변경하지 않았다. 백업은 로컬 `/private/tmp/devmozip-schema-before-20261109-9661a486.sql`에 보관했다.

## 적용 순서

1. users가 있는지, 대상 테이블이 이미 생성됐는지 확인한다.
2. 피드 담당자와 `feeds-schema-review.sql`의 컬럼·NOT NULL을 확인하고 feeds를 먼저 준비한다.
3. `feed-likes-schema-review.sql`을 검토한다. feeds가 없으면 외래키 때문에 실행되지 않는다.
4. `portfolio-schema-review.sql`은 users만 선행하면 독립적으로 사용할 수 있다.

세 파일은 각각 트랜잭션으로 묶였고, 기존 테이블이 있으면 오류로 중단한다. IF NOT EXISTS로 다른 스키마를 정상으로 간주하지 않는다. 기본 키는 PostgreSQL용이며 DB마다 생성 방식이 다를 수 있다. 실행 도구에서 오류 발생 시 중단하고 열린 트랜잭션은 ROLLBACK해야 한다. 샘플 데이터·DROP·기존 테이블 ALTER는 포함하지 않았다. created_at/updated_at은 신고 테이블처럼 앱에서 저장하는 시각을 받으며 임의 NOW() 기본값을 넣지 않았다.

## 필드 역할

### feed_likes

| 한글명 | 컬럼 | 타입 | 제약 |
| --- | --- | --- | --- |
| 사용자 ID | user_id | BIGINT | users.id FK, 복합 PK |
| 피드 ID | feed_id | BIGINT | feeds.id FK, 복합 PK |
| 좋아요 시각 | created_at | TIMESTAMP | NOT NULL |

신고와 달리 최신 ERD의 `(user_id, feed_id)` 복합 키를 유지하므로 별도 id와 updated_at은 없다. 기존 프로젝트용 likes와 별개이며 두 곳에 같은 피드 좋아요를 이중 저장하지 않는다. 공통 likes의 예약 post_id를 앞으로 피드에 사용할지는 담당자끼리 별도로 맞춘다. 이 안은 새 피드 설계의 feed_likes를 기준으로 한다.

### portfolios

| 한글명 | 컬럼 | 타입 |
| --- | --- | --- |
| 포트폴리오 ID | id | BIGINT IDENTITY PK |
| 작성자 ID | user_id | BIGINT FK |
| 제목 | title | VARCHAR(255) |
| 대표 이미지 경로 | thumbnail_url | VARCHAR(2048) |
| 대표 주제/분야 | topic | VARCHAR(255) |
| 한 줄 소개 | summary | TEXT |
| 상세 설명/편집 문서 | description | TEXT |
| 담당 역할 | role | VARCHAR(100) |
| 기술 스택 | tech_stack | JSONB |
| 첨부 메타데이터 | attachments | JSONB |
| 공개 범위 | visibility | VARCHAR(20), 기본 PRIVATE |
| 생성 시각 | created_at | TIMESTAMP |
| 수정 시각 | updated_at | TIMESTAMP |

이미지 파일을 프론트에서 받아 서버 폴더/저장소에 저장해도 DB에는 파일 식별자·경로가 필요하다. 파일 바이너리/base64를 이 컬럼에 넣는 설계가 아니다. 파일 업로드·보존 정책은 후속 파일 기능에서 구현한다.

## 적용 전 맞출 내용

- 피드의 VISIBLE/HIDDEN과 포트폴리오의 PUBLIC/PRIVATE는 서로 다른 도메인 값이다.
- 기존 좋아요 조회 PR #82의 매핑 변경은 별도 worktree `/private/tmp/DevMozip-liked-feed-schema`의 feat/75-liked-posts에서 진행했다. posts/post_likes/PUBLIC/original_post_id를 feeds/feed_likes/VISIBLE/original_feed_id로 맞췄다. 아직 커밋·푸시 전이다. 테이블만 만든다고 기존 API가 바로 호환되지는 않는다.
- 피드 content는 전달된 ERD처럼 NULL을 허용한다. 원글 본문 필수 여부와 퍼가기 의견 생략은 피드 서비스에서 검증한다. 상세 enum·퍼가기 중복/원본 권한 검증은 별도 피드 구현 범위다.
- 포트폴리오 description에 편집 문서 JSON을 저장하는 #88 기본안과 JSONB 메타데이터 형식은 AI/포트폴리오 담당자와 맞춰야 한다. 컬럼 추가 없이 저장할 수 있지만 raw description을 항상 일반 텍스트로 읽으면 안 된다.
- project_application_portfolios.portfolio_snapshot JSONB는 현재 공유 DB에 없다. 프로젝트 지원 담당자가 제출 트랜잭션·불변 스냅샷·AI 피드백 제외·파일 보존을 함께 구현해야 한다. 이번 파일은 해당 테이블을 변경하지 않는다.
- portfolio_id 외래키·원본 삭제·스냅샷 보존 정책도 아직 별도 합의 대상이다. 현재 #88 삭제 API는 필요한 참조 제약 미준비 시 보류하도록 작성되어 있다.

## 검증

최종 검증 결과는 docs/ai-progress.md의 이번 SQL 검토 기록을 따른다. 격리 PostgreSQL 17에서 PK·FK·기본값·NULL 퍼가기 본문 및 중복 실행 중단을 검증했다. 공유 DB에는 샘플 데이터를 넣지 않았다. 실제 SNS 데이터 화면 연동은 별도 확인이 필요하다. 기존 미커밋 CRUD는 보존했다.

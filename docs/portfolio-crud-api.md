# 포트폴리오 등록·수정·삭제 — #88

## 범위와 선행 작업

- WBS 그대로 POST `/api/v1/portfolios`, PUT·DELETE `/api/v1/portfolios/{id}`.
- #84/PR #87의 조회를 재사용하며 미병합 선행 브랜치의 최신 원격 c4b0bd8에서 `feat/88-portfolio-crud`를 분기했다. 기준에는 develop 0a35674가 포함된다.
- 파일 업로드 POST `/api/v1/files`, 공개 범위 PATCH `/api/v1/portfolios/{id}/visibility`, AI 피드백은 구현하지 않는다. 실제로 구현하지 않은 기능을 저장 성공으로 표시하지 않는다.
- 이전 #84 Service 분리·lint 변경 stash는 그대로 보존한다. 이번 CRUD의 Query/Mutation 서비스는 interface/impl 방식으로 구성했다.

## 구조

`ApiPortfolioComposer` → 기존 `PortfolioEditorForm` → `portfolioMutations` → Next BFF → `PortfolioMutationController` → `PortfolioMutationService`/`impl.PortfolioMutationServiceImpl` → `PortfolioMutationRepository` → PostgreSQL.

- Query도 `PortfolioQueryService` 인터페이스와 `impl.PortfolioQueryServiceImpl`로 분리했다.
- 쓰기는 트랜잭션, 수정·삭제는 행 잠금으로 처리한다. 기존 DTO 응답만 반환하고 Entity를 노출하지 않는다.
- 구현 당시 공유 DB에 portfolios가 없어 조회 단계와 같은 JDBC 경계로 저장 구현·매핑 검토를 수행했다. 이후 2026-11-09 공유 DB에 테이블을 생성했으며 새 ORM Entity·자동 DDL은 등록하지 않았다.
- title/thumbnail_url/topic/summary/role/tech_stack/attachments/visibility 및 공통 created_at/updated_at은 ERD 컬럼을 그대로 사용한다. 신청/AI/회원 테이블에 쓰지 않는다.

## API 계약

### POST /api/v1/portfolios

- 인증 이메일의 실제 회원 ID를 작성자로 저장한다. 요청 userId/id/권한을 신뢰하지 않는다.
- ACTIVE/WARNED 회원이 사용 가능하며 ADMIN도 타인의 기록을 수정·삭제할 수 없다.
- 201 + Location `/api/v1/portfolios/{id}`, 상세 응답. BFF Location은 `/api/portfolios/{id}`.
- 공개 범위는 등록 시 PUBLIC/PRIVATE 선택, 생략 시 PRIVATE.

### PUT /api/v1/portfolios/{id}

- 제목·요약·역할·기술 스택 등 전체 편집 내용을 검증한다. 작성자·생성 시각·ID는 변경하지 않는다.
- expectedUpdatedAt에 마지막 GET의 portfolio.updatedAt을 그대로 보낸다. 기존 NULL 시각은 null.
- 행 잠금 후 현재 버전과 다르면 409. 저장 시각은 한국 시각 LocalDateTime의 마이크로초 단위로 기록하고 같은/과거 시각이면 이전보다 1마이크로초 증가시킨다.
- 공개 범위 변경은 이 이슈에서 하지 않는다. PUT의 visibility는 현재 값과 같아야 한다. 후속 PATCH에서도 updatedAt을 갱신해야 한다.
- 200 상세 응답.

### DELETE /api/v1/portfolios/{id}

- 본인 기록만 처리. 미존재·타인·미확정 공개 상태는 404.
- 프로젝트 제출 후에도 원본 수정·삭제를 허용한다(2026-11-10 사용자 확정). 제출본은 별도 project_application_portfolio_snapshots에 보관한다. 참조 중인데 보관본이 없으면 409로 차단한다.
- 검증된 비지연 portfolio_id FK가 없으면 삭제를 503으로 보류한다. 스냅샷 전환 SQL은 ON DELETE SET NULL을 사용하며 원본 삭제 시 연결 ID만 비운다. 스냅샷 자체에는 원본 FK를 두지 않아 보관본을 삭제하지 않는다.
- FK 준비 및 제출본 보관을 확인한 뒤 원본을 삭제하고 204를 반환한다. 다른 FK 참조 충돌은 409로 보존한다. 신규 구조는 docs/project-portfolio-snapshots.md를 따른다.

## 쓰기 요청 예시

```json
{
  "title": "협업 도구",
  "thumbnailUrl": null,
  "topic": "생산성",
  "summary": "팀의 회고를 기록하는 서비스",
  "description": null,
  "role": "백엔드",
  "techStack": ["Java", "Spring Boot"],
  "attachments": [],
  "visibility": "PRIVATE",
  "expectedUpdatedAt": null,
  "document": {
    "schema": "devmozip.portfolio.v1",
    "start": "2026-01",
    "end": "2026-02",
    "teamSize": "3명",
    "fields": ["생산성"],
    "blocks": [{
      "id": "description", "kind": "text", "title": "프로젝트 설명",
      "content": "## 기여\nAPI와 테스트를 구현했습니다.", "format": "markdown",
      "images": [], "links": [], "source": "description"
    }]
  }
}
```

- title 1~100, summary 1~200, role 1~100자. techStack 1~30개·항목별 100자, 대소문자 무시 중복 금지.
- 저장된 이미지 URL·첨부 메타데이터만 취급한다. data/blob/javascript URL, 프로토콜 상대 주소, 자격증명 포함 URL은 거절한다. 서버는 링크 내용을 다운로드하지 않는다.
- attachments는 기존 name/url 배열 계약, 최대 100개. 별도 파일 저장소 접근·서명 URL 정책은 파일 이슈에서 결정한다.
- document가 없으면 description 일반 텍스트를 보존한다. 구조화 문서를 description으로 직접 주입할 수 없다.

## 자유형 문서 저장 기본안 — 별도 팀 계약 확인 필요

ERD에 blocks·기간·팀 규모용 컬럼이 없어 **기존 TEXT description 안에 버전 있는 JSON 문서**를 저장하는 구현 기본안을 적용했다. 선택 질문에 대한 명시적 확정 답변으로 간주하지 않으며 AI/프로젝트 담당자와 저장 계약을 확인해야 한다. 공유 DB에는 아직 이 형식의 데이터를 쓰지 않았다.

- schema/start/end/teamSize/fields/blocks를 저장하며 DB 컬럼을 추가하지 않는다.
- 섹션 1~20개, 제목 1~80자, 개별 본문 20,000자·총 본문 150,000자, JSON 문서 및 BFF 실제 요청 크기 2MB 이내.
- text/gallery/links, text/markdown/html 형식, 섹션 순서·이미지·링크·기존 source 연결을 보존한다.
- HTML은 서버에서 정제하고 렌더링 시에도 기존 sanitize 정책을 적용한다. Markdown은 렌더 시 정제한다. API description에는 버전 JSON 대신 사람이 읽을 설명을, document에는 권한 확인된 편집 문서를 반환한다.
- 목록은 기존 기본 요약만 반환해 document·첨부·상세 본문을 노출하지 않는다. 상세 canEdit은 서버의 실제 소유권에서 계산한다. 이 값은 UI 안내용이며 쓰기에서는 소유권을 다시 검증한다.
- 일반 텍스트 데이터는 그대로 조회한다. 알려지지 않은 문서 버전은 오류로 보류해 읽기/수정 시 자동 변환·손실을 막는다.
- AI 기본 경험 계산은 title/topic/summary/role/techStack을, 권한 있는 상세 분석은 DTO의 description/document를 사용하도록 협의한다. raw description SQL 값이 항상 일반 텍스트라는 가정은 하지 않는다. AI 엔진은 이번에 수정하지 않았다.

## 화면과 초안

- /portfolios/new는 실제 API 작성기로 연결한다. 숫자 ID의 수정 경로도 API를 사용하며, 기존 문자열 Mock ID의 수정·상세는 보존한다.
- 기존 작성기 배치·미리보기·섹션 추가/정렬·Markdown/HTML을 재사용한다. 업로드 버튼은 비활성화하고 대표 이미지 URL 및 기존 URL 메타데이터를 사용한다.
- 등록 후 실제 숫자 ID의 상세로 이동한다. 본인 목록·상세에 수정·삭제가 표시된다. 다른 사람 편집 화면은 서버 응답 canEdit 확인 후 차단한다.
- 더블 클릭은 처리 중 ref/disabled로 막으며, 쓰기는 자동 재시도하지 않는다. 네트워크 오류로 서버 반영 여부가 불명확하면 목록 확인이 필요하며 서버 차원의 POST 멱등 키는 아직 없다.
- 초안은 `devmozip:portfolio-api-draft:v1:<실제 이메일>:<id 또는 new>`로 분리한다. 기존 Mock ID로 계정이 섞이지 않는다. 저장 실패·409 시 입력과 초안을 유지한다.
- 복원 초안에는 원래 updatedAt을 유지해 최신 GET 버전을 덮어쓰지 않는다. 사용자가 명시적으로 ‘초안 버리고 최신 내용 불러오기’를 선택해야 서버 내용으로 대체한다.
- 저장 한도를 초과한 미완성 본문도 저장소 크기 한도 내에서는 초안으로 복원한다. 저장소 용량 초과 시 현재 입력을 유지하고 안내한다.
- 성공 응답 확인 후에만 초안을 지운다. 명시적인 작성 취소도 초안을 지운다.

## 공유 DB 준비와 삭제 정책

2026-11-09 사용자 위임으로 공유 개발 DB에 portfolios를 생성했다. 스키마 백업 후 feeds·feed_likes와 함께 단일 트랜잭션으로 적용했으며 기존 테이블·데이터는 변경하지 않았다. `docs/sql/portfolio-schema-review.sql`은 적용 기록이며 재실행하지 않는다. 지원서 연결의 portfolio_id FK와 스냅샷은 변경하지 않았다. 기존 고아 참조를 임의 삭제·NULL 보정하지 말고 DB/프로젝트 담당자와 확인해야 한다.

정상 CRUD 및 참조/동시성 검증은 격리된 PostgreSQL 17 Testcontainers에서 수행한다. 공유 DB 실데이터·브라우저 수동 저장 확인은 미실행이다. 테이블은 준비됐으며 외래키·문서 계약 합의와 실연동 검증이 필요하다.

## 공유 DB 브라우저 검증 (2026-11-09)

- premium 개발 계정 로그인 후 본인 목록의 공개·비공개 샘플 2건 확인.
- 실제 작성 화면에서 비공개 ID 3 등록, 수정 폼의 저장값 복원 및 제목·소개 수정 성공.
- 새로고침 후 제목·소개·본문 유지. DB에서 user_id=5, PRIVATE, devmozip.portfolio.v1 문서와 수정 시각 변경 확인. 검증용 ID 3은 비공개로 남겨둔다.
- Docker 내부 Request URL과 외부 Origin 차이로 같은 출처의 POST가 403인 문제를 재현했다. 요청 Host와 URL 프로토콜을 기준으로 비교하도록 보완했다. X-Forwarded-Host는 신뢰하지 않으며 다른 도메인·포트·프로토콜·누락/잘못된 Origin은 거절한다.
- 파일 업로드·삭제는 이번 브라우저 검증에서 제외.

### 후속 파일 업로드 #113

feat/113-portfolio-file-upload에서 POST /api/v1/files 및 기존 이미지·PDF 첨부 화면 연동을 추가했다. 저장 위치·비공개 접근·파일 크기·보존 및 검증은 docs/portfolio-files-api.md를 따른다. 삭제 API·공개 범위 PATCH·제출 스냅샷은 해당 범위를 확장하지 않았다.

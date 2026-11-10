# 프로젝트 지원 포트폴리오 제출본

## 확정 동작

2026-11-10 사용자 요청으로 원본 수정을 막던 닫힌 PR #108 대신 별도 스냅샷 방식을 적용한다. 작업 브랜치는 feat/48-project-portfolio-snapshots다. d6a5720에서 작성한 뒤 PR 준비 단계에서 최신 develop 8b8f171을 통합했다.

1. 지원자는 본인의 공개 포트폴리오를 1~3개 선택한다.
2. 서버는 소유권·공개 상태를 다시 확인하고 원본 행을 잠근다.
3. 지원서·기존 제출 연결·별도 스냅샷을 같은 트랜잭션으로 저장한다. 일부 실패하면 모두 롤백한다.
4. 원본은 계속 수정·삭제할 수 있다. 지원 내역은 스냅샷을 표시하고 원본 변경을 따라가지 않는다.
5. 취소·거절 후에도 제출본을 보존한다. 제출본을 수정하거나 삭제하는 API는 제공하지 않는다.

## 저장 구조

`project_application_portfolio_snapshots`: id, application_portfolio_id(UNIQUE/FK), source_portfolio_id, title, content(JSONB), created_at, updated_at.

content는 기존 PortfolioDetailResponse의 읽기 전용 복사다. 제목·대표 이미지 URL·분야·요약·역할·기술 스택·본문·구조화 문서와 이미지/링크 블록·첨부·작성자 표시 정보를 보관하고 canEdit=false로 저장한다. 목록은 JSON 전체를 불러오지 않고 ID·원본 ID·제목만 조회한다.

원본 삭제 시 project_application_portfolios.portfolio_id만 NULL이 된다. 스냅샷은 제출 연결을 참조하며 source_portfolio_id는 기록용 숫자여서 원본 FK가 없다. 삭제가 보관본을 연쇄 삭제하지 않는다. 스냅샷 없는 참조가 남아 있으면 원본 삭제를 409로 차단한다.

## 조회 계약과 권한

GET `/api/v1/projects/{projectId}/applications/{applicationId}/portfolios/{snapshotId}`.

- 본인 지원자: 무료 회원도 자신의 제출본 조회 가능.
- 해당 프로젝트 주최자: PREMIUM/ADMIN일 때 상세 열람 가능. 구독 해지 후 공고 관리 권한과 상세 열람 권한을 구분한다.
- 다른 사용자·다른 프로젝트/지원서의 제출본·비로그인 요청은 허용하지 않는다.
- 응답은 기존 포트폴리오 상세 계약이며 canEdit=false다. URL의 snapshotId와 응답 portfolio.id(제출 당시 원본 ID)는 서로 다른 식별자다.
- 지원 응답의 submittedPortfolios(id, sourcePortfolioId, title)를 사용해 상세 링크를 구성한다. portfolioIds는 원본 삭제 후에도 보관된 sourcePortfolioId를 반환한다.

## DB 전환

`docs/sql/project-portfolio-snapshots.sql`을 사용한다. 기존 제출에 보관본이 없으면 전체 적용을 롤백하며 현재 원본을 과거 제출본으로 가장해 복사하지 않는다. 필요한 경우 담당자 확인 후 별도 이관 정책을 정한다. 테이블/FK가 준비되지 않으면 신규 지원을 차단한다.

2026-11-10 팀 DB에 적용했다. 적용 전후 원본 portfolios 23행과 기존 제출 연결 0행을 보존했고 신규 스냅샷은 0행이었다. portfolio_id FK는 검증된 ON DELETE SET NULL이다. 원본 테이블 컬럼·회원 데이터는 변경하지 않았다. 새 코드와 스키마를 함께 사용해야 하며 현재 실행 서버의 소스는 교체하지 않았다.

## 한계와 후속 계약

- 서버 업로드 파일은 기존 저장소를 재사용한다. 원본이 수정·삭제되어도 제출본에서 참조한 파일은 본인 업로더 또는 해당 프리미엄/관리자 주최자가 조회할 수 있다. 타인·익명에게 제출본만으로 파일을 공개하지 않는다.
- 이미지·첨부는 URL과 메타데이터를 보관한다. 파일 바이너리 복제는 하지 않으므로 외부 파일이 바뀌거나 사라지면 당시 파일 자체까지 보존되지 않는다. 향후 업로드 담당자는 제출본이 참조하는 파일의 보존·정리 정책을 맞춰야 한다.
- DB 관리자가 직접 스냅샷을 수정하는 것까지 방지하는 보안 저장소는 아니다. 서비스에는 수정·삭제 경로가 없다.
- Mock 시연은 기존 상태를 유지한다. 이번 구현·DB 검증은 실제 API 모드의 제출본 보관 범위다.
- 테스트와 미실행 검증은 docs/ai-progress.md의 해당 항목에 기록한다. 후속 사용자 요청으로 기능 브랜치를 커밋·푸시하고 develop 대상 새 PR을 생성한다.

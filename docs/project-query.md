# 프로젝트 조회 API — #45

2026-11-04. #44 생성 API 구조를 유지하고 목록·상세·내 프로젝트 및 관리용 기본 정보 조회를 추가했다. 실제 회원·구독·지원·프론트 연결은 수행하지 않는다.

## API 계약

| API | 접근·응답 |
|---|---|
| GET /api/v1/projects | 비로그인 가능. 공개·미삭제 공고만 페이지 조회 |
| GET /api/v1/projects/{id} | 공개는 누구나, 비공개는 현재 ACTIVE 팀원만. ProjectDetailResponse |
| GET /api/v1/me/projects | 인증 및 프로젝트 사용자 공급 필요. 본인의 현재 ACTIVE 관계 기준 페이지 |
| GET /api/v1/projects/{id}/manage | 현재 ACTIVE 주최자만. 공고 및 현재 인원만 조회하며 지원 명단을 포함하지 않음 |

공개 GET은 공통 SecurityConfig에 목록과 숫자 ID 상세 두 패턴만 추가했다. options·관리·생성·내 목록은 기존 인증 정책을 유지한다. 삭제 공고는 모두 404/목록 제외다. 비공개 상세의 비팀원·비로그인·탈퇴자는 404로 처리한다. 미승인 지원자는 project_members ACTIVE 관계가 없으므로 비공개 조회 권한이 없다. 실제 지원 데이터와의 검증은 #48 이후 연결한다.

기존 개발용 프로젝트 사용자 공급 경계와 서울 기준 Clock을 재사용한다. 개발 모드 고정 ID는 실제 인증 사용자와 결합되지 않았으므로 운영 권한 검증이 완료된 API가 아니다. 실연동 전 비공개·내 목록·관리는 개발 프로필에서만 정상 사용자 ID를 얻는다. 공개 목록·공개 상세는 해당 공급 경계를 호출하지 않는다.

## 목록 검색

| 파라미터 | 의미 |
|---|---|
| query | 제목 또는 기술 스택 부분 검색, 대소문자 무시, 최대 255자. %, _는 와일드카드가 아닌 문자로 검색 |
| jobRole | ProjectJobRole 코드 하나 |
| status | RECRUITING / RECRUITMENT_COMPLETED / IN_PROGRESS / COMPLETED |
| interests | 최대 5개 반복 파라미터. OR 조건 |
| techStacks | 최대 5개 반복 파라미터. AND 조건 |
| sort | LATEST(기본) / POPULAR / DEADLINE |
| page | 0부터, 기본 0 |
| size | 1~100, 기본 20 |

```text
GET /api/v1/projects?interests=교육&interests=게임&techStacks=React&techStacks=Java&jobRole=BACKEND&sort=LATEST&page=0&size=20
```

분야·기술은 각각 NFKC·앞뒤 공백 정리·소문자 비교한다. 모든 필터는 함께 적용한다. 잘못된 enum·범위·배열 입력은 400이다. 빈 검색 결과는 200과 빈 items, totalElements=0, totalPages=0이다. 범위를 벗어난 정상 페이지 번호도 빈 items와 실제 전체 개수를 반환한다.

- LATEST: created_at DESC, id DESC.
- POPULAR: view_count DESC, created_at DESC, id DESC.
- DEADLINE: 모집 중이고 모집 종료일이 서울 기준 오늘 이상인 공고만 recruitment_end ASC, created_at DESC, id DESC. 모집 마감일 당일까지 포함한다.
- 페이지 응답: items / totalElements / page / size / totalPages. items는 상세 본문·지원서·거절 사유를 포함하지 않는 별도 record다. 현재 인원은 ACTIVE 멤버 수이며 HOST를 포함한다. 전체 정원은 기존 1+모집 직군 정원 합이다.
- recruitmentClosed는 운영 상태가 모집 중이 아니거나 모집 종료일이 오늘보다 이전이면 true다. 상태를 DB에서 자동 변경하지 않는다. 모집 시작 전 지원 허용 여부는 #48 지원 검증 계약에서 다룬다.

JSONB 필터·개수·LIMIT/OFFSET을 PostgreSQL에서 계산하고, 페이지 ID만 JPA로 일괄 조회한다. 전체 공고를 메모리에 불러와 필터링하지 않는다. 목록·개수·인원·엔티티는 같은 REPEATABLE_READ 트랜잭션에서 읽는다. JSONB 인덱스 및 대량 데이터 성능 튜닝은 수행하지 않았다.

## 내 프로젝트·관리 조회

`GET /api/v1/me/projects?tab=HOSTED` 형태로 위 검색 파라미터를 함께 사용할 수 있다.

| tab | 현재 동작 |
|---|---|
| ALL | 본인의 ACTIVE 멤버 관계가 있는 미삭제 공고. 비공개 포함 |
| HOSTED | ALL 중 본인이 주최자 |
| PARTICIPATING | ALL 중 타인이 주최하며 아직 종료되지 않은 공고 |
| COMPLETED | ALL 중 종료된 공고 |
| APPLYING | #48 지원 테이블 미구현이므로 501과 안내 문구 반환 |

목록의 myRole은 본인의 HOST/PARTICIPANT 역할이며 공개 탐색 목록에서는 null이다. 지원 중·지원 상태 및 탈퇴 이력을 포함한 최종 내 프로젝트 계약은 #48에서 확장한다. 현재 없는 지원 테이블을 새로 만들거나 빈 지원 목록을 실제 결과처럼 반환하지 않는다.

관리 조회는 기존 공고 ProjectResponse를 project에, 현재 인원을 memberCount에, 모집 마감 여부를 recruitmentClosed에 담는다. 지원자·포트폴리오·사용자 프로필을 조회하지 않는다. 비주최자는 403, 인증 누락은 401이다. 프로젝트 수정·지원 관리 API를 추가한 것은 아니다.

## 조회수 정책과 DB 변경

사용자가 #45에서 승인한 정책: `projects.view_count BIGINT NOT NULL DEFAULT 0`을 추가하고, 비로그인을 포함하여 성공한 상세 요청마다 1 증가한다. 새로고침도 다시 집계한다. 사용자·IP·세션별 중복 제한 및 봇 제외는 적용하지 않는다. 권한 거절·없는 공고·삭제 공고·목록·관리 조회는 집계하지 않는다.

상세 응답의 `project.viewCount`와 목록 `items[].viewCount`에 현재 값을 제공한다. 상세 조회는 READ_COMMITTED 트랜잭션과 프로젝트 행 잠금 안에서 권한 확인·증가를 처리하여 동시 요청의 증가 손실을 막는다. 조회수 증가만으로 공고 수정 시각을 변경하지 않는다.

기존 팀 ERD export/엔티티에 조회수 필드가 없었으며 사용자 승인으로 추가했다. ERDCloud 반영은 별도로 필요하다. 실제 팀 공용 DB에는 접속하거나 DDL을 실행하지 않았다. 팀 DB 담당자에게 다음 변경을 전달할 수 있다.

```sql
ALTER TABLE projects ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;
```

기존 개발 설정의 Hibernate ddl-auto=update도 해당 컬럼 생성을 시도하므로 공용 DB에서는 팀의 스키마 변경 절차를 따른다. 테스트는 독립 PostgreSQL 17 컨테이너에서만 진행했다.

## 검증·남은 작업

- 신규 PostgreSQL 통합 테스트 8개: 공개/비공개/삭제·탈퇴·HOST/참여자, JSONB OR/AND, 검색 문자열 이스케이프, 페이지·정렬·오늘/어제 경계, 조회수·동시 상세 요청 8개, 내 목록·관리 권한 검증.
- backend `./gradlew test --no-daemon`: 207개 통과, 실패·오류·스킵 0.
- frontend `npm test`: 48개 파일·525개 통과. `npm run build`: 성공(TypeScript 포함).
- 프론트 코드는 그대로 유지했으며 빌드가 변경한 next-env.d.ts 경로는 기존 상태로 되돌렸다.
- 미완료: #48 지원 중 목록·지원 상태 최종 연결, 실제 회원/구독과 프로젝트 사용자 ID 결합, 프론트 API 연결, 공용 DB 및 최신 ERDCloud 대조. 실제 브라우저 연동 테스트는 수행하지 않았다.
- 커밋·푸시·PR·이슈 상태 변경은 수행하지 않았다. #44 PR은 열린 상태였으므로 #45 로컬 브랜치는 #44 구현을 기반으로 만들었다.


## #45 팀 PostgreSQL 재검증 — 2026-11-05

#44 후속 PR의 최신 develop 기반 f925f0a에서 별도 검증했다. 제품 조회 코드는 수정하지 않았다. 추가 파일은 `ProjectQueryTeamDatabaseIntegrationTest.java`이며 #44의 프로젝트 전용 JPA 설정을 재사용한다. 기본 테스트에서는 PROJECT_TEAM_DB_TEST opt-in이 없으면 스킵한다.

- 실제 팀 DB 테스트 3개 통과: 기존 공개 데이터의 최신/인기/마감순 결과와 직접 SQL 결과 일치, JSONB 분야 OR·기술 AND·직군/상태 필터·페이지/빈 결과, 비공개 익명 접근 거절·주최자 조회·조회수 증가·관리/내 목록·논리 삭제 제외.
- 기존 생성/조회 통합 테스트 25개 통과(실패·스킵 0). 독립 PostgreSQL Testcontainers 테스트이며 팀 DB 테스트와 구분한다.
- frontend `npm run build -- --webpack` 성공(TypeScript 포함). 이번에는 프론트 제품 변경 없음. 생성 next-env.d.ts는 이전 내용으로 복원.
- 검증은 팀 DB에서 프로젝트 엔티티만 validate하고 임시 생성/변경을 트랜잭션 롤백한다. 기존 기본 프로젝트의 상세는 호출하지 않아 조회수를 변경하지 않는다. 사후 프로젝트 8개·멤버 21개·조회수 합계 2215·임시 공고 0개 확인.
- standalone 서비스/JPA/JDBC 연결 검증이며 실제 포트 HTTP·JWT 인증·실제 회원 권한·프론트 API 왕복은 검증하지 않았다. 개발용 주최자 101을 사용하며 실제 계정 연결이 아니다.

남은 사항:

1. 실제 인증 회원 ID 공급과 전체 서버 엔티티 테이블 준비.
2. 프론트 목록·상세·내 프로젝트·관리 조회의 API 연결. 현재 화면은 Mock이다.
3. APPLYING 지원 중 목록은 여전히 501. DB에 project_applications가 있어도 엔티티/Repository/조회 어댑터가 없어 #48에서 연결해야 한다.
4. JSONB 인덱스·대량 데이터 성능 검증과 실제 브라우저 통합은 미실행.

이번 범위에서 프론트 연결 코드·#48 기능·커밋·푸시·PR은 추가하지 않았다.

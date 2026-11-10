# 프로젝트 북마크 — #56

## 구현

- 공통 `bookmarks`를 사용한다. 프로젝트 저장은 `project_id`만 채우며 `post_id`는 NULL이다. 사용자+프로젝트 중복과 대상 하나만 선택하는 제약을 둔다.
- 인증된 회원 ID로만 저장·해제·조회한다. POST/DELETE 반복 호출은 안전하며 동시 저장은 프로젝트 행 잠금으로 중복을 막는다.
- 공개 공고와 현재 ACTIVE 팀원이 열람할 수 있는 비공개 공고만 저장·조회한다. 삭제·열람 권한 상실 시 목록에서 제외하되 본인의 기록 해제는 허용한다.
- Mock 켜기는 기존 시연 상태를 유지한다. Mock 끄기는 실제 API를 사용하며 상세·탐색 카드·내 북마크함이 공통 상태를 공유한다. 로그인 계정 변경 시 이전 목록을 버리고 서버에서 다시 읽는다. 실제 북마크는 localStorage에 저장하지 않는다.

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| POST | /api/v1/projects/{id}/bookmark | projectId, bookmarked: true |
| DELETE | /api/v1/projects/{id}/bookmark | projectId, bookmarked: false |
| GET | /api/v1/projects/bookmarks | 현재 회원이 열람 가능한 공고 목록 |

프론트 BFF는 `/api/projects` 아래 동일한 경로를 사용하고 서버 세션을 전달한다. 변경 요청은 동일 출처만 허용한다.

## DB 반영

2026-11-09 사용자 승인으로 [공통 테이블 DDL](sql/project-bookmarks.sql)을 팀 PostgreSQL에 실행했다. 컬럼 6개, FK 2개, PK, 중복 방지 2개와 대상 검증 CHECK를 확인했다. 기존 회원·프로젝트 데이터는 변경하지 않았다. 게시글 테이블 FK 연결 및 게시글 실제 API는 해당 담당자와 결합할 범위다.

## 주요 파일

- 백엔드: `entity/Bookmark`, `repository/BookmarkRepository`, `dto/project/ProjectBookmarkResponse`, `service/ProjectBookmarkService`, `service/impl/ProjectBookmarkServiceImpl`, `controller/ProjectBookmarkController`
- 프론트: `lib/projects/projectBookmarkStore`, `components/projects/ProjectBookmarkButton`, `ProjectSummaryCard`, `ApiProjectDetail`, `app/(user)/bookmarks/page` 및 BFF 라우트
- 테스트: `ProjectBookmarkIntegrationTest`, `projectBookmarkStore.test.ts`, 북마크 BFF `route.test.ts`

## 검증

검증 명령과 최종 결과는 `docs/ai-progress.md`의 이번 단계 기록을 참조한다. 별도 커밋·푸시·PR은 요청 전 수행하지 않는다.

## 실행 서버 반영 — 2026-11-09

Docker가 별도 `project-api` 폴더를 실행하여 북마크 구현이 화면에 반영되지 않았던 문제를 확인했다. 실행 소스에 북마크 관련 변경만 복사하고 백엔드를 재시작했다. 프로젝트 카드·상세 버튼 및 개인 북마크함을 연결했고 지원·리뷰 코드는 보존했다. 프론트 빌드/TypeScript 및 관련 테스트 26개, 분리 실행한 북마크 PostgreSQL 통합 테스트 4개 통과. 기존 테스트의 직군 enum/String 컴파일 오류 6건과 실제 로그인 401은 남아 있다. 비로그인 프로젝트 페이지 200, 양쪽 북마크 API 401을 확인했으며 브라우저 시각 검증은 하지 않았다.

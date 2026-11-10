# 프로젝트 자체 신고 — #51 일부

이번 범위는 프로젝트 자체 신고다. 사용자·팀원·게시글·댓글 신고 및 관리자 처리는 공통 `reports`를 공유하되 이번에 구현하지 않는다.

## 데이터·API

- DB에 있는 `reports` 13개 컬럼과 IDENTITY ID를 확인했다. 테이블·컬럼·제약을 추가하지 않았다.
- PR #90의 `Report`·`ReportRepository`를 재사용하고 `ofProject` 및 프로젝트 신고 중복 조회만 확장했다. 기존 `ofUser`·사용자 신고 조회 메서드는 보존한다. 아직 #90은 develop 미병합이므로 병합 시 공통 파일 확장을 함께 유지해야 한다.
- `POST /api/v1/projects/{id}/reports`: 인증 회원만 가능하며 성공 시 201과 `{id, projectId, status, createdAt}` 응답 record를 반환한다. 요청은 `{reason, detail?}`다. 프론트 BFF는 `/api/projects/{id}/reports`다.
- `reporter_id`는 실제 인증 회원, `project_id`는 URL의 프로젝트 ID로 지정한다. `reported_user_id`, `post_id`, `comment_id`는 NULL이다. `status`는 AWATING, 관리자 처리 필드는 NULL이다.
- 신고 사유는 허위 모집 정보/스팸 또는 광고/부적절한 내용/기타. 상세 설명은 선택·최대 1,000자다. 기존 `reason` TEXT에 선택 사유와 비어 있지 않은 상세 설명을 빈 줄로 구분해 보관한다. 별도 상세 컬럼은 만들지 않는다.
- 기존 Mock과 같이 본인 공고는 신고 불가, 같은 회원+프로젝트는 한 번만 허용한다. 상태에 따른 재신고는 허용하지 않는다. 삭제 공고는 신고 불가, 비공개 공고는 ACTIVE 팀원만 가능하다. 종료 공고는 열람 가능하다면 신고할 수 있다.
- 같은 프로젝트 행 잠금으로 동시 중복 신고와 삭제를 직렬화한다. 사용자·게시글·댓글 신고와 중복 판정을 분리한다. 공고 논리 삭제 후 신고 기록은 유지한다.

## 화면

- `/projects/{projectId}/report` 별도 페이지. 프로젝트명/소개, 사유 선택, 상세 입력, 접수 완료, 중복/본인 공고/비로그인/통신 오류 안내를 제공한다.
- Mock 켜기는 기존 모집 상태/localStorage를 사용하고, 끄기는 실제 API를 사용한다. 통신 실패를 Mock 접수로 바꾸지 않는다.
- 상세의 기존 신고 모달을 이 페이지로 연결했다. 좋아요·공유·북마크 및 기존 지원·리뷰 화면은 유지한다.

## 검증 — 2026-11-09

- 프론트 `npm test -- --maxWorkers=1`: 73개 파일·742개 통과. 신고 BFF·입력 검증·페이지 렌더링 테스트 포함.
- 프론트 `npm run build -- --webpack`: 빌드·TypeScript 성공.
- 백엔드 신규 `ProjectReportIntegrationTest`: 격리 PostgreSQL/JWT 6개 통과. 프로젝트 대상만 저장, 공통 타 신고 보존, 입력/인증/본인/삭제/비공개/정지 회원, 동시 중복을 검증했다.
- 백엔드 전체 `./backend/gradlew -p backend test --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx384m`: 총 577개, 565개 통과·11개 스킵·기존 KakaoClientTest 로그 검증 1개 실패. 관련 없는 카카오 코드는 수정하지 않았다.
- 1440×1100 Chrome 빌드 프리뷰에서 테스트 세션 응답을 주입해 Mock 접수→완료→새로고침 후 중복 안내, 본인 공고·비로그인 제한, 입력 네 개·길이 제한·가로 넘침·콘솔 오류를 확인했다. [결과](artifacts/project-reports/browser-report.json). 이 브라우저 검증은 실제 로그인/DB 신고 접수를 뜻하지 않는다.
- 실행 소스에 관련 변경만 반영했다. 최초 백엔드 기동은 구버전 실행 폴더의 기존 StrictStringDeserializer 누락으로 컴파일 실패했으며, 해당 공통 의존성을 반영해 다시 시작했다. 실행 서버에서 신고 페이지 200, BFF/백엔드 비로그인 신고 401, 기존 프로젝트 목록 200을 확인했다. 실제 팀 DB의 인증 후 신고 접수는 실행하지 않았다. 관리자 처리·팀원 신고는 남은 범위이며 이슈를 닫거나 커밋·푸시하지 않았다.

## 신고 진입 버튼

프로젝트 상세의 북마크 바로 오른쪽에 빨간 사이렌 아이콘을 표시한다. 본인 공고에는 표시하지 않는다. Mock의 신고 여부 안내와 `/projects/{id}/report` 연결은 유지한다.

## #51 팀원 신고 및 관리자 처리 (2026-11-10)

이번 사용자 요청으로 위 ‘미구현’ 범위를 확장했다. 기존 공고 신고·사용자 신고는 유지하며, DB에 이미 매핑된 reports 필드만 사용한다. 새 테이블·컬럼·DDL은 추가하지 않았다. 최신 ERDCloud를 다시 받은 것은 아니므로 실제 팀 DB의 추가 제약 존재 여부는 배포 전 대조해야 한다.

### 팀원 신고

- `POST /api/v1/projects/{projectId}/members/{memberId}/reports`, 본문 `{reason, detail?}`. memberId는 project_members.id이며 users.id나 chat_members.id가 아니다.
- 신고자는 인증 정보에서 얻고 현재 ACTIVE 팀원이어야 한다. 대상은 같은 프로젝트의 참여 이력(현재·탈퇴·강퇴)을 가진 다른 회원이며 주최자도 신고 가능하다. 삭제 프로젝트·다른 프로젝트의 팀원·본인은 거절한다. 종료 프로젝트도 현재 ACTIVE 참여 관계가 있으면 접수한다.
- `project_id`는 신고 맥락, `reported_user_id`는 대상 회원으로 함께 저장한다. 다른 post_id/comment_id는 NULL이다. 기존 ‘대상 하나만’ 초안은 팀원 신고에 한해 이 복합 맥락으로 확장한다.
- 사유는 UserReportReason의 기존 5개, 상세 설명은 선택·최대 1,000자다. reason TEXT에 합쳐 저장한다.
- 신고자+프로젝트+대상 회원별 1회. 일반 사용자 신고(project_id=NULL), 공고 신고(reported_user_id=NULL), 다른 프로젝트 신고와 중복 판정을 분리한다. 프로젝트 행 잠금으로 동시 접수·팀원 변동·삭제를 직렬화한다.
- 실제 프로젝트 채팅의 기존 작은 빨간 사이렌 버튼과 모달을 재사용한다. API 실패를 Mock 성공으로 바꾸지 않는다. Mock은 선택 projectId를 기존 userReports에 저장하며 이전 v1 localStorage도 읽는다.

### 관리자 처리

- `GET /api/v1/admin/project-reports?status=&page=0&size=20`: 공고·팀원 신고만 조회, size 1~50, 최신 ID순, 페이지 및 전체 수 반환.
- `PATCH /api/v1/admin/project-reports/{id}`: `{status, resolution}`. 상태는 REVIEWING/RESOLVED/REJECTED. 완료·기각은 공백 아닌 결과가 필수이며 최대 1,000자다. AWATING 복귀 및 완료 후 재처리는 허용하지 않는다.
- JWT 관리자 권한과 실제 DB 회원의 ADMIN 역할·이용 가능 상태를 모두 확인한다. 처리자 handled_by, 처리 시각 handled_at은 서버에서 결정한다. 행 잠금으로 동시 최종 처리를 한 번만 적용한다.
- 공통 reports의 일반 사용자·SNS·댓글 신고는 이 관리자 API에서 제외한다. 신고 검토만 기록하며 계정 정지·강퇴·공고 삭제·신뢰 점수 감점은 자동 실행하지 않는다.
- BFF `/api/admin/project-reports`, `/api/admin/project-reports/{id}`와 `/admin/reports` 기존 화면에 연결한다. 상태 필터·페이지·로딩/빈 상태/오류·처리 모달·중복 제출 방지를 제공한다. Mock ON의 기존 공고 신고 디자인을 유지하고 프로젝트 팀원 신고도 같은 목록에 표시한다.

### 검증

이번 실제 실행 결과는 `docs/ai-progress.md`의 #51 절을 따른다. 테스트의 격리 PostgreSQL과 브라우저 응답 대역은 실제 팀 DB 신고 접수나 운영 배포와 구분한다. 이 단계에서는 공유 DB 변경·운영 서버 재시작·커밋·푸시·PR 생성·이슈 종료를 실행하지 않는다.

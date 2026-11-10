# 프로젝트 자체 신고 — #51 일부

이번 범위는 프로젝트 자체 신고다. 사용자·팀원·게시글·댓글 신고 및 관리자 처리는 공통 `reports`를 공유하되 이번에 구현하지 않는다.

## 데이터·API

- DB에 있는 `reports` 13개 컬럼과 IDENTITY ID를 확인했다. 테이블·컬럼·제약을 추가하지 않았다.
- PR #90의 `Report`·`ReportRepository`를 재사용하고 `ofProject` 및 프로젝트 신고 중복 조회만 확장했다. 기존 `ofUser`·사용자 신고 조회 메서드는 보존한다. 아직 #90은 develop 미병합이므로 병합 시 공통 파일 확장을 함께 유지해야 한다.
- `POST /api/v1/projects/{id}/reports`: 인증 회원만 가능하며 성공 시 201과 `{id, projectId, status, createdAt}` 응답 record를 반환한다. 요청은 `{reason, detail?}`다. 프론트 BFF는 `/api/projects/{id}/reports`다.
- `reporter_id`는 실제 인증 회원, `project_id`는 URL의 프로젝트 ID로 지정한다. `reported_user_id`, `post_id`, `comment_id`는 NULL이다. `status`는 RECEIVED, 관리자 처리 필드는 NULL이다.
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

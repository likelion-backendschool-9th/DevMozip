# 프로필 활동 탭 프로젝트 연결

기준: 2026-11-10, develop 24b524e 이후 로컬 변경. 이번 범위는 실제 프로필 활동 탭의 프로젝트 이력 조회다.

## 경로와 권한

| 구분 | 경로 |
| --- | --- |
| 본인 조회 | GET /api/v1/users/me/projects |
| 타인 조회 | GET /api/v1/users/{slug}/projects |
| 프론트 BFF | GET /api/projects/activity/{slug} |

본인은 slug 없이 me로 조회한다. 타인은 프로필 태그로 조회하며 사용자 ID는 신원으로 전달하지 않는다. page/size만 허용하고 다른 쿼리를 전달하지 않는다. 비공개는 현재 ACTIVE 팀원만, 삭제된 공고는 누구에게도 노출하지 않는다. 개인정보·지원서·거절 사유는 반환하지 않는다.

활동은 실제 project_members 관계만 사용한다. 승인 전 지원서는 제외하며 LEFT 이력은 ‘참여 종료’로 표시한다. 개발 시작일/ID 내림차순, 기본 10개씩 페이지 조회한다. 계획 기간·실제 종료일·담당 직군·프로젝트 상태를 표시하고 제목에서 상세로 이동한다. Mock 탭은 유지한다.

## 구조와 변경

- 백엔드: ProjectActivityController → ProjectActivityService → ProjectActivityRepository, ProjectActivityResponse/ProjectActivityPageResponse record.
- 인증 설정: 본인 활동은 로그인 필요, 다른 사용자 활동은 공개 GET으로 허용한다. 비공개 프로젝트 인가는 서비스/조회 조건에서 수행한다.
- 프론트: ApiProfileTabs → ApiActivityTab, 기존 ActivityTab CSS와 공통 Card/Button 및 useProjectResource 재사용.
- BFF: app/api/projects/activity/[slug]/route.ts, types/projectActivity.ts.
- 테스트: ProjectQueryIntegrationTest에 프로필 활동 권한/이력/페이지 테스트 추가, projectActivityRoutes.test.ts 및 ApiActivityTab.test.tsx.

DB 엔티티·DDL·데이터는 변경하지 않는다. Member에 프로필 전체 비공개 필드가 없으므로 현재 활성 회원 조회 정책을 따르며, 추후 프로필 공개 범위 계약이 생기면 함께 적용한다.

## 검증

- 프론트 `npm test -- --maxWorkers=1`: 120개 파일·1,381개 통과. 신규 BFF 10개·화면 4개 및 기존 프로필 탭 회귀 포함.
- `NODE_ENV=production npm run build`: Next.js 빌드·TypeScript 성공. 개발 컨테이너의 NODE_ENV=development로 실행한 초기 빌드는 prerender 중 실패하여 올바른 환경으로 재실행했다. 처음 발생한 활동 분기 타입 오류도 수정했다.
- 실제 공유 DB 공개 프로필 이력 API: 프로젝트 4건 조회. 공유 DB의 스키마/데이터는 변경하지 않았다.
- 1440px Chromium: 실제 프로젝트 카드 4개·상세 링크, Mock OFF, 가로 넘침 없음·pageerror 0, 본인 비로그인 401·없는 프로필 404 확인. [결과](artifacts/project-profile-activity/browser-report.json).
- 실계정 로그인 본인 화면·비공개/LEFT UI 및 전체 페이지 이동 클릭은 브라우저로 실행하지 않았다. 권한 조건은 격리 PostgreSQL 통합 테스트에서 검증한다.
- 백엔드 전체 `./gradlew test --no-daemon --max-workers=1` (권한 충돌 회피용 `/tmp` 빌드/프로젝트 캐시 사용): 1,142개 중 1,128개 통과·12개 스킵·2개 실패. 실패는 수정하지 않은 `KakaoClientTest.java:153`, `PortOneClientTest.java:105`의 오류 로그 검증이다. 전체 성공으로 보고하지 않는다.
- 프로젝트 패키지 140개 중 129개 통과·11개 opt-in 스킵·실패 없음. `ProjectQueryIntegrationTest` 12개(신규 활동 4개 포함)는 격리 PostgreSQL에서 모두 실행·통과했다.
- 컨테이너 내부의 첫 조회 테스트 실행은 Docker 소켓 부재로 12개 모두 스킵됐다. 따라서 그 실행을 성공 근거로 삼지 않고 호스트에서 재실행했다. 호스트 첫 시도도 컨테이너 소유 Gradle 캐시 잠금 권한으로 실패해 임시 경로를 사용했다.
- `git diff --check` 통과. 실행 프론트 3000/백엔드 8080과 헬스 UP 확인. 중단 후 재실행 시 ncafe 프론트가 3000을 점유하여 기존 사용자 승인대로 해당 프론트만 중지하고 DevMozip 포트 바인딩을 복구했다.
- 커밋·푸시·PR 생성 및 다른 기능 구현은 수행하지 않았다.

## 활동 관리 버튼 위치 변경 (2026-11-10)

프로젝트 수가 늘어도 목록 아래로 내려가지 않도록 ‘내 프로젝트 관리’를 활동 제목 오른쪽 상단으로 이동했다. 실제 ApiActivityTab과 Mock ActivityTab에 동일하게 적용하고 본인에게만 표시하는 조건은 유지한다. ActivityTab.module.css에 제목/버튼 정렬을 추가하고 사용하지 않는 footer 스타일은 제거했다. frontend 빌드/TypeScript 및 프로필 관련 3개 파일·32개 테스트 통과. 이번 단순 배치 변경의 별도 브라우저 검증은 실행하지 않았다. 백엔드·DB 변경 없음.

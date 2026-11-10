# 내 프로젝트·상세 복귀 경로와 활동 디자인

## 변경 내용

- 상세 상단의 고정 `/projects` 링크를 진입 경로에 맞는 복귀 링크로 변경했다.
- 내 프로젝트 카드를 클릭하면 `returnTo`에 `/my-projects`와 선택 탭·검색·페이지 조건을 보존한다. 새로고침해도 URL로 복귀 경로가 유지된다.
- 직접 접속은 프로젝트 모집으로, 내 프로젝트 참여 화면은 내 프로젝트로 돌아간다. 프로필 활동에서 연 상세는 해당 프로필의 활동 탭으로 복귀한다.
- 복귀 경로는 내부 목록/프로필 경로만 허용한다. 외부 URL·API 경로·역슬래시/제어문자를 차단한다.
- 내 프로젝트의 Mock/API 목록은 기존 RecruitmentCard/ProjectSummaryCard를 재사용해 모집 목록과 같은 커버·기술 태그·직군·일정 구성을 사용한다. PC 1440px에서 3열이며 관리/참여 정보/기존 Mock 채팅 안내를 유지한다.
- 내 프로젝트 카드는 단일 제목 링크의 클릭 영역을 카드 전체로 확장한다. 별도 관리하기/조회하기/참여 정보 진입 링크는 제거하고 모두 `/projects/{id}`로 진입한다. 북마크·Mock 채팅 링크는 별도 조작 영역을 유지한다.
- 프로젝트 상세는 응답의 HOST 참여 관계와 실제 인증 회원 ID를 함께 확인해 주최자에게 관리 기능을 즉시 표시한다. 비주최자는 일반 조회 화면이며 관리 명령은 노출하지 않는다. 기존 지원·북마크 등의 일반 기능은 유지한다.
- Mock 주최자도 상세에서 기존 관리 화면을 바로 렌더링한다. 관리 화면 안의 동일 상세로 되돌아가는 중복 링크는 제거했다. 기존 관리 URL은 호환용으로 유지하며 백엔드 관리 API 권한은 변경하지 않는다.
- 활동 탭은 ActivityProjectCard를 공유한다. Mock의 기존 색상 마크·상태 배지·제목·기간/직군 배치를 유지하고 API에 추가했던 ‘주최/참여’ 글자 마크를 제거했다. API의 저장된 프로젝트 대표 이미지를 사용한다. 이미지가 없거나 로드에 실패하면 제목 첫 글자와 기존 색상 팔레트를 사용한다.

## 주요 파일

- lib/projects/projectNavigation.ts, components/projects/ProjectBackLink.tsx
- components/projects/ProjectSummaryCard.tsx·module.css, RecruitmentCard.tsx
- app/projects/[projectId]/page.tsx, _components/api/ApiProjectDetail.tsx·ApiProjectList.tsx
- app/my-projects/page.tsx·module.css, [projectId]/manage/page.tsx
- app/(user)/users/[userId]/_components/ActivityProjectCard.tsx·ActivityTab.tsx·ApiActivityTab.tsx·ActivityTab.module.css
- lib/projects/projectNavigation.test.tsx, tests/screens/MyProjectsLayout.test.tsx·ApiActivityTab.test.tsx

## 검증 범위

- 1440px Chromium: 내 프로젝트의 3열 모집 카드·커버 확인, 상세 이동 후 내 프로젝트 탭/검색 조건 복원, 가로 넘침 없음·pageerror 0.
- 내 프로젝트 UI 시나리오는 실제 공개 API의 프로젝트를 재사용하되 테스트용 쿠키와 목록/세션/상세 응답 대역을 사용했다. 실제 로그인·주최자 관리 변경·DB 쓰기 검증을 뜻하지 않는다.
- 활동 탭은 실제 공개 프로필 API로 프로젝트 4개·제목 첫 글자 마크·상세 URL의 프로필 활동 복귀 정보를 확인했다.
- 결과: [브라우저 보고서](artifacts/project-navigation-layout/browser-report.json).
- 최종 `npm test -- --maxWorkers=1`: 122개 파일·1,393개 통과. 복귀 경로 10개·내 프로젝트 카드 2개·활동 카드 4개 및 기존 회귀 포함.
- `NODE_ENV=production npm run build`: 성공, TypeScript 검사 포함. Docker dev 환경의 빌드를 위해 production 환경변수를 명시했다.
- `git diff --check` 통과. 자동 생성 next-env.d.ts 변경만 기존 dev 타입 경로로 복구했다.
- 이번 작업에서 백엔드·DB·API 계약은 변경하지 않아 Gradle 테스트를 재실행하지 않았다. 실계정 로그인 왕복·권한별 관리 동작의 브라우저 검증 및 모바일 검증은 이번 범위에서 수행하지 않았다.
- 커밋·푸시·PR 생성 없음.

## 주최자 자동 진입 후속 검증 (2026-11-10)

- frontend `npm test -- --maxWorkers=1`: 123개 파일·1,397개 통과. API 주최자/비주최자/회원 ID 불일치 3개와 Mock 자동 진입 검증, 별도 카드 진입 링크 제거를 포함한다.
- `NODE_ENV=production npm run build`: 빌드·TypeScript 통과. `git diff --check` 통과, next-env.d.ts의 생성 차이만 기존 dev 경로로 복구했다.
- 1440px Chromium에서 제목 대신 카드 커버를 클릭해 자동 진입, 같은 URL에서 주최자 관리/비주최자 조회 분기, 관리 버튼 제거, 내 프로젝트 탭 복귀를 확인했다. pageerror 0. [결과](artifacts/project-owner-entry/browser-report.json).
- 브라우저는 실제 공개 프로젝트 레코드와 테스트용 쿠키·신원/상세 응답 대역을 사용했다. 실제 로그인 회원 권한으로 프로젝트를 변경하거나 DB에 기록하지 않았다.
- 이번 후속 변경은 프론트·문서에 한정한다. 기존 백엔드 API 인가와 DB는 수정하지 않았으며 Gradle은 재실행하지 않았다. 커밋·푸시 없음.

## 헤더 수정·조회수 배치 (2026-11-10)

공고 수정은 헤더 오른쪽 위에 연필 아이콘과 함께 두고, 좋아요/공유/북마크 그룹은 그 아래 정렬한다. 조회수는 설명 아래 왼쪽의 눈 아이콘/보조 텍스트로 분리했다. 변경 파일은 ApiProjectDetail.tsx와 projects/[projectId]/page.module.css다.

프론트 전체 123개 파일·1,397개 테스트와 NODE_ENV=production npm run build/TypeScript 통과. 1440px Chromium의 응답 대역 화면에서 수정 버튼 오른쪽·아이콘 그룹 위, 조회수 제목 아래 왼쪽 배치를 좌표/스크린샷으로 확인했다. 주최자/비주최자 분기·목록 복귀도 유지했고 pageerror 0. [배치 결과](artifacts/project-header-position/browser-report.json). 백엔드 미변경으로 Gradle은 재실행하지 않았다.

공고 수정 버튼 크기 후속: 아래 아이콘 3개 묶음과 같은 너비·52px 높이·14px 모서리로 통일했다. 1440px 브라우저 실측 동일 크기, 빌드/TypeScript 및 관련 화면 테스트 3개 통과(2026-11-10).

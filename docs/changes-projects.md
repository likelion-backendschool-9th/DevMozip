# 프로젝트 모집·상세·지원 구현 보고

## 구현

- 목록: 이름/기술 검색, 직군/분야/모집 상태/기술 필터, 최신·인기·마감 임박 정렬, 빈 결과, URL 조건 복원. 마감 임박은 현재 모집 중 공고부터 가까운 마감일 순으로 표시합니다.
- 공고 카드: 직군별 참여/정원, 최소 경력, 기술, 개발 기간/마감일, 북마크 및 현재 계정 지원 상태.
- 상세: 소개/목표/개발 기간/모집 기간/직군별 인원·기술·최소 경력/주최자 프로필, 북마크/신고/공유/쪽지 경로.
- 지원: 본인 포트폴리오와 직군, 멘트 검증. 무료 회원 지원 가능, 주최자/기존 참여자/기간 외/충원 완료/경력 미달/중복 지원 방지. 지원 상태/제출 정보 표시, 취소 및 기간 내 재지원.
- 마감 3일 전 한국 시각 0시부터 취소 확인. −0.5°C는 안내용 임시 예시이며 온도에는 적용하지 않습니다.
- 프리미엄/관리자의 기본 공고 생성 폼. 무료/비로그인은 권한 안내. 실제 구독/결제/공고 편집/지원 승인/팀 관리는 추가하지 않았습니다.
- ProjectProvider가 생성 공고·지원·북마크·신고를 관리하고 localStorage `devmozip:recruitment:v1`에 저장합니다. 현재 계정의 상태만 표시하고 로그아웃은 내역을 삭제하지 않습니다.
- 새 공고는 메인·추천·통합 검색·목록·상세·주최자 프로필 활동에 반영됩니다. 승인 전 지원서는 협업 활동 이력에 추가하지 않습니다.
- 공유는 URL 복사와 수동 복사 대안을 제공합니다. 쪽지 버튼은 기존 준비 경로이며 실제 메시지를 보내지 않습니다.
- HTTP LAN에서 Web Crypto UUID가 없어도 새 Mock 레코드 생성이 가능하도록 공통 ID 유틸리티를 추가하고 프로필 레코드 생성에도 적용했습니다.

## 변경 파일

src 경로는 frontend 기준입니다.

신규:
- src/types/project.ts
- src/mocks/project.ts
- src/services/projectService.ts, projectService.test.ts
- src/app/providers/ProjectProvider.tsx
- src/utils/createMockId.ts
- src/features/project/ApplicationModal.tsx, ProjectActions.tsx, RecruitmentCard.tsx, CreateProjectForm.tsx
- src/pages/ProjectsPage.tsx, ProjectDetailPage.tsx, CreateProjectPage.tsx, ProjectsPage.test.tsx
- src/styles/project.css

수정:
- src/app/router/AppRouter.tsx, src/main.tsx, src/components/layout/AppLayout.tsx (경로/상태/초기화)
- src/services/homeService.ts, src/features/project/ProjectPreview.tsx, src/features/matching/RecommendationPanel.tsx (공통 공고 데이터)
- src/mocks/search.ts, src/services/searchService.ts, src/pages/SearchPage.tsx, src/features/search/ProjectResults.tsx (공통 공고/마감 상태/북마크)
- src/features/user/ActivityTab.tsx (주최자 활동), src/services/profileService.ts (Mock ID 호환)
- src/pages/ProfilePage.test.tsx, SearchPage.test.tsx (공통 공급자 연결)
- README.md, docs/frontend-pages.md, docs/decisions-pending.md, docs/demo-scenarios.md, docs/changes-projects.md

## 검증

- npm run build 성공 (TypeScript 검사 + Vite 빌드).
- npm test: 10개 파일, 136개 통과. 기존 106개 + 프로젝트 서비스 19개 + 화면 렌더링 11개.
- 서비스: 검색/정렬/마감 경계, 생성 권한/필수값/날짜/직군/인원, 지원/포트폴리오 소유/경력/중복/마감/주최자/기존 참여자, 취소·재지원·확인 필요 경계·실제 온도 불변, 계정별 북마크, 신고 중복, HTTP LAN ID 생성, 저장/손상/실패 처리, 새 공고 검색·메인 반영.
- 렌더링: 목록 인원/경력, 상세 정보/주최자, 무료/비로그인/종료 지원 제한, 북마크·지원 상태 복원, 본인 포트폴리오만 표시, 취소 안내/체크, 프리미엄 생성 권한, 잘못된 주소/빈 결과.
- localhost:5173의 목록/상세/작성 및 ProjectProvider 모듈 HTTP 200 확인.
- 실제 브라우저의 클릭·배치·클립보드·새로고침 통합 검증은 도구가 없어 미수행. 서비스/정적 렌더링 테스트와 HTTP 응답 확인으로 구분합니다.
- 의존성/백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 수정한 테스트 실패

종료 상태 안내 테스트에서 이미 DevLog 참여자인 프리미엄 계정을 사용해 ‘이미 참여’ 안내가 먼저 표시되었습니다. 종료 제한을 검증하도록 비참여 프로젝트 StudyBridge로 테스트 조건을 정정한 뒤 전체 136개가 통과했습니다.

## 남은 범위

지원 승인/거절, 팀원 관리, 공고 수정/삭제/기간 연장, 프로젝트 상태 변경, 단체 채팅/알림, 실제 API/DB/인증/AI/결제는 구현하지 않았습니다. 현재 요청 범위만 완료했으며 다음 단계는 진행하지 않습니다.

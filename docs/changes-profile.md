# 사용자 프로필 구현 보고

## 구현 내용

상단 공통 프로필 + 하단 5개 가로 탭. 왼쪽 고정 정보 패널은 만들지 않았습니다.

- 배너, SVG 기본 프로필 이미지, 닉네임/아이디, 직군/경력/소개/가입일/안전한 외부 링크/연락 시간/협업 상태/팔로우 수/신뢰 온도와 별 색상.
- 소개 수정과 기술·관심 분야. 임의 HTML 삽입 없이 React 텍스트/줄바꿈 표현.
- 본인 포트폴리오 CRUD, 요약/상세 권한, 실제 AI 호출 없는 입력 기반 피드백.
- 내 글은 세로형. 직접 작성한 글 편집/숨김/삭제, 퍼간 글 의견/숨김/취소 및 원본 연결. 원본이 삭제/숨김되면 안내 표시.
- DevMozip의 모집/참여 프로젝트 이력만 표시. 실제 Mock 프로젝트 ID를 상세 준비 라우트에 연결.
- 협업 기록/종료/기간/중복/자기 자신 조건을 검사하는 별점 리뷰, 선택 키워드, 횟수/비율 원형 버블, 온도/별 색상 변화.
- 본인/타인/비로그인/프리미엄/관리자 표시 차이. 존재하지 않는 사용자와 잘못된 탭의 안내/복원.
- 탭 URL `?tab=about|portfolio|posts|activity|reviews`, 탭 키보드 방향키/Home/End 및 ARIA 연결.
- 팔로우 목록 및 상태, 글 수정/숨김/삭제가 메인 피드에도 반영. 공통 프로필 변경을 다음 추천에 사용.
- 프로필 관련 상태는 `devmozip:profiles:v1`에 저장. 손상된 저장값은 초기 데이터로 복원. 데모 초기화는 해당 상태도 복원, 로그아웃은 작성 데이터를 유지.

## 변경 파일

아래 src 경로는 frontend 기준입니다.

신규:
- src/types/profile.ts
- src/mocks/profile.ts
- src/services/profileService.ts, profileService.test.ts
- src/app/providers/ProfileProvider.tsx
- src/pages/ProfilePage.tsx, ProfilePage.test.tsx
- src/features/user/ProfileAvatar.tsx, ProfileHeader.tsx, ProfileEditor.tsx, AboutTab.tsx, PortfolioTab.tsx, PostsTab.tsx, ActivityTab.tsx
- src/features/review/ProfileReviews.tsx
- src/styles/profile.css

수정:
- src/app/providers/AuthProvider.tsx: 비밀번호 없는 공개 사용자 목록 제공
- src/app/providers/HomeProvider.tsx: 프로필의 공통 팔로우 관계 사용
- src/app/router/AppRouter.tsx, src/main.tsx: 프로필 경로/상태 공급자/스타일 연결
- src/components/common/Modal.tsx: 넓은 폼 클래스와 고유 ARIA 제목 ID
- src/components/layout/AppLayout.tsx: 프로필 상태 초기화 연결
- src/features/post/FeedPreview.tsx: 공통 글과 작성자 프로필 사용
- src/features/matching/RecommendationPanel.tsx: 변경된 프로필 기반 후보 사용
- src/features/project/ProjectPreview.tsx: 공통 프로젝트 상태 표시
- src/types/home.ts, src/mocks/home.ts, src/services/homeService.ts: 활동용 Mock 프로젝트 추가, 추천 동점 정렬, 공통 데이터 경계
- README.md, docs/frontend-pages.md, docs/decisions-pending.md, docs/demo-scenarios.md, docs/changes-profile.md

## 검증

- 최종 `npm run build`: TypeScript + Vite 빌드 성공.
- 최종 `npm test`: 6개 파일, 84개 통과. 기존 52개와 프로필 서비스 20개, 화면 통합 렌더링 12개.
- 서비스 검증: 포트폴리오 CRUD/필수값/기간/권한, 소개 및 링크 검증, 원본 글/퍼가기 참조, 메인 숨김·팔로잉 연계, 활동 프로젝트 참조/상태 일치, 리뷰 권한/기간/필수 별점/키워드/중복/온도/색상/비율/저장 복원.
- React 정적 렌더링 검증: 5개 탭에서 상단 정보 유지, 무료·프리미엄 잠금, 본인 CRUD UI, 원본 작성자/링크, 버블/별점/선택 키워드, 잘못된 사용자/특수 ID/탭 안내.
- 실행 중인 localhost:5173의 본인 포트폴리오 경로, 타인 리뷰 경로 및 ProfilePage 모듈 HTTP 200.
- 브라우저 실제 배치·클릭·스크롤·포커스 통합 테스트는 도구가 없어 미수행. 정적 렌더링과 HTTP 확인은 브라우저 시각 검증과 구분합니다.
- 의존성 추가 없음. 백엔드는 변경하지 않았으며 이번에는 백엔드 빌드를 재실행하지 않았습니다.

## 수정한 실패

- 첫 빌드 명령을 루트에서 실행해 package.json 부재로 실패 → frontend에서 재실행.
- Mock 소개/게시글의 실제 줄바꿈이 단일 따옴표 문자열에 들어가 TypeScript 구문 오류 → 이스케이프된 줄바꿈으로 수정.
- 활동용 종료 프로젝트가 추천 동점에서 앞서 기존 테스트 1개 실패 → 동점일 때 최신 생성일 순으로 정렬해 기존 결과를 유지.
- 이후 빌드 및 전체 테스트 통과.

## 범위/권한 안내

- 무료 회원도 본인의 상세 포트폴리오는 관리/조회할 수 있습니다. 타인 상세는 프리미엄/관리자만 표시합니다. 피드백은 프리미엄/관리자 전용 데모입니다.
- 리뷰 초기 온도/임시 계산/30일/별 색상은 미확정 정책 문서에 기록했습니다. 키워드 비율은 모든 키워드 선택 수를 기준으로 합니다.
- 기본 이미지/배너만 제공하며 사용자 사진 업로드는 이번 요청에 추가하지 않았습니다.
- 프로필 상세 페이지는 구현했으나 검색/프로젝트 상세/전체 SNS 상세/쪽지/관리자 기능은 기존 준비 라우트로 유지합니다.
- 실제 DB/인증 서버/AI/OAuth/결제/API는 사용하지 않았으며 다음 단계는 진행하지 않았습니다.

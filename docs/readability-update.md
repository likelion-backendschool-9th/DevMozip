# 전반적인 글씨 가독성 개선

- 기본 본문은 14px에서 16px, 줄 높이는 1.65로 조정했다.
- 기존 8~12px 글씨는 14px, 13~14px 글씨는 15px로 조정했다. rem 및 반응형 clamp로 정의된 작은 글씨에도 같은 기준을 적용했다. 제목 등 이미 큰 글씨는 유지한다.
- 보조 회색 토큰은 #858797에서 #686b7d로 조정했다.
- 공통 헤더·버튼·입력창·태그 및 메인, 인증, 프로필, 프로젝트, AI 추천, SNS, 메신저, 알림, 프리미엄, 관리자 등 기존 CSS Module을 해당 위치에서 수정했다. 전역 일괄 !important 규칙으로 덮어쓰지 않는다.
- 헤더 검색 아이콘 제거 정책에 맞게 반응형 검증 스크립트도 갱신했다.
- 사용자 작성 HTML에 지정된 글자 크기는 이번 변경에서 강제로 덮어쓰지 않는다.

## 실제 검증

- npm test: 27개 파일, 376개 테스트 통과.
- npm run build: TypeScript 검사 포함 성공.
- Chromium: 1920 / 1440 / 1280 / 1024 / 768 / 390px, 각 36개 화면·상태 점검으로 총 216건 통과.
- 메인·프로필 5개 탭·개인 메뉴·설정·프로젝트 목록/상세/관리·AI 추천 및 결과·SNS·채팅·검색·프리미엄·관리자·인증·404·온보딩을 확인했다.
- 가로 넘침, 헤더 겹침, 접힌 메뉴 상태 점검 통과. 콘솔 오류 및 pageerror 0건, 비즈니스 API 호출 0건.
- PC 메인과 모바일 프로필 캡처도 직접 확인했다. 자동 검증이 모든 문구의 시각적 품질을 보장하는 것은 아니며, 서비스 전체 업무 흐름을 이번에 재실행한 것은 아니다.
- 결과: [검증 JSON](artifacts/readability/report.json), [PC 메인](artifacts/readability/1440-home.png), [모바일 프로필](artifacts/readability/390-profile.png).

## PR 브랜치 검증 및 범위

이 PR은 최신 develop을 기준으로 가독성과 헤더 변경만 적용했다. 다른 미병합 PR의 프로필 HTML 편집·채팅 개편은 포함하지 않는다. PR 브랜치에서 npm test 26개 파일·363개 테스트 및 npm run build(TypeScript 검사 포함)가 통과했다. 위 376개 테스트 및 216개 브라우저 검증은 다른 프로필·채팅 변경도 적용된 작업 디렉터리의 결과다. 그 상태에서 생성한 화면 캡처이므로 일부 UI는 이 PR 단독 상태와 다를 수 있다.

## 변경 CSS 파일

- frontend/app/_components/FeedPreview.module.css
- frontend/app/_components/HomeScreen.module.css
- frontend/app/_components/MatchingStart.module.css
- frontend/app/_components/PlannedPage.module.css
- frontend/app/_components/ProjectPreview.module.css
- frontend/app/_components/RouteDirectory.module.css
- frontend/app/_components/SetupPage.module.css
- frontend/app/admin/_components/AdminContent.module.css
- frontend/app/admin/_components/AdminScreen.module.css
- frontend/app/chats/[projectId]/_components/KickVotePanel.module.css
- frontend/app/chats/[projectId]/_components/ProjectChatScreen.module.css
- frontend/app/feed/_components/FeedScreen.module.css
- frontend/app/following/_components/FollowingScreen.module.css
- frontend/app/forgot-password/_components/PasswordRecoveryForm.module.css
- frontend/app/globals.css
- frontend/app/login/_components/LoginForm.module.css
- frontend/app/matching/_components/ChatConditions.module.css
- frontend/app/matching/_components/ChatResults.module.css
- frontend/app/matching/_components/MatchingScreen.module.css
- frontend/app/matching/_components/RecommendationPanel.module.css
- frontend/app/messages/_components/MessagesScreen.module.css
- frontend/app/my-projects/[projectId]/manage/_components/MyProjectManageScreen.module.css
- frontend/app/my-projects/_components/MyProjectsScreen.module.css
- frontend/app/notifications/_components/FollowNotices.module.css
- frontend/app/notifications/_components/NotificationsScreen.module.css
- frontend/app/onboarding/_components/OnboardingPreview.module.css
- frontend/app/onboarding/_components/OnboardingScreen.module.css
- frontend/app/onboarding/_components/TagChoices.module.css
- frontend/app/posts/[postId]/_components/Comments.module.css
- frontend/app/premium/_components/PlanComparison.module.css
- frontend/app/premium/_components/PremiumScreen.module.css
- frontend/app/profile/me/bookmarks/_components/BookmarksScreen.module.css
- frontend/app/profile/me/likes/_components/LikedPostsScreen.module.css
- frontend/app/projects/[projectId]/_components/ApplicationModal.module.css
- frontend/app/projects/[projectId]/_components/ProjectActions.module.css
- frontend/app/projects/[projectId]/_components/ProjectDetailScreen.module.css
- frontend/app/projects/[projectId]/manage/_components/ApplicantPortfolioButton.module.css
- frontend/app/projects/[projectId]/manage/_components/ProjectManageScreen.module.css
- frontend/app/projects/[projectId]/reviews/write/_components/ProjectReviewWriteScreen.module.css
- frontend/app/projects/_components/ProjectsScreen.module.css
- frontend/app/projects/new/_components/CreateProjectScreen.module.css
- frontend/app/search/_components/AdvancedSearch.module.css
- frontend/app/search/_components/DeveloperResults.module.css
- frontend/app/search/_components/ProjectResults.module.css
- frontend/app/search/_components/SearchBar.module.css
- frontend/app/search/_components/SearchFilters.module.css
- frontend/app/search/_components/SearchScreen.module.css
- frontend/app/settings/account/_components/AccountSettingsScreen.module.css
- frontend/app/signup/_components/SignupForm.module.css
- frontend/app/users/[userId]/_components/AboutTab.module.css
- frontend/app/users/[userId]/_components/ActivityTab.module.css
- frontend/app/users/[userId]/_components/PortfolioTab.module.css
- frontend/app/users/[userId]/_components/PostsTab.module.css
- frontend/app/users/[userId]/_components/ProfileHeader.module.css
- frontend/app/users/[userId]/_components/ProfileReviews.module.css
- frontend/app/users/[userId]/_components/ProfileScreen.module.css
- frontend/components/auth/AuthShell.module.css
- frontend/components/auth/DemoAccounts.module.css
- frontend/components/layout/AccountLayout.module.css
- frontend/components/layout/AppLayout.module.css
- frontend/components/messaging/ChatWindow.module.css
- frontend/components/posts/PostActions.module.css
- frontend/components/posts/PostCard.module.css
- frontend/components/posts/PostForm.module.css
- frontend/components/posts/editor/PostEditorScreen.module.css
- frontend/components/projects/CreateProjectForm.module.css
- frontend/components/projects/ProjectDateSimulator.module.css
- frontend/components/projects/ProjectNotices.module.css
- frontend/components/projects/RecruitmentCard.module.css
- frontend/components/projects/ReviewRequestStatus.module.css

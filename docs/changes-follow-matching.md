# 팔로우·Mock AI 추천 고도화

## 구현

- 팔로워/팔로잉 전용 페이지, 검색·협업 상태 필터, 프로필 이동, 공통 팔로우/언팔로우.
- 프로필 목록 모달에서 전용 목록 연결, 팔로우한 프로필의 알림 설정.
- 사용자별 새 글 활동/프로젝트 모집 알림 설정. 기본 꺼짐, 새 이벤트만 생성, 언팔로우 시 해제. 기존 프로젝트 알림 화면과 헤더 알림 표시에 통합.
- 메인 및 추천 전용 화면에서 같은 입력/최근 결과/월 사용량 제공.
- 자연어 기술 별칭·분야·직군 Mock 추출. 빠진 항목/모호한 직군은 추가 질문, 미정 응답 가능.
- 기술/분야/직군/협업 상태 규칙 기반 개발자·유사 프로젝트 추천과 적합도 퍼센트. 본인/관리자 후보 제외. 상세 이유는 프리미엄/관리자 UI로 구분.
- 무료 월 15회, 프리미엄/관리자 월 100회 임시 확대. 한국 시각 월별·계정별 집계, 성공만 차감, 남은 횟수와 제한 도달 안내.
- 로그아웃은 추천 입력만 초기화하며 사용량은 보존합니다. 데모 전체 초기화는 사용량도 복원합니다. 저장 결과는 계정과 입력 키로 구분합니다.
- 기존 프로필/팔로우/게시글/지원 데이터를 유지하면서 새 필드를 이관합니다.

## 변경 파일

src 경로는 frontend 기준입니다.

신규:
- src/types/follow.ts, matching.ts
- src/mocks/follow.ts, matching.ts
- src/services/followService.ts, followService.test.ts, matchingService.ts, matchingService.test.ts
- src/features/follow/FollowSettings.tsx, FollowNotices.tsx
- src/pages/FollowingPage.tsx, MatchingPage.tsx, FollowingMatching.test.tsx
- src/styles/follow-matching.css

수정:
- src/types/profile.ts, home.ts, src/mocks/profile.ts, home.ts
- src/services/profileService.ts, socialService.ts, homeService.ts
- src/app/providers/HomeProvider.tsx, ProfileProvider.tsx, ProjectProvider.tsx
- src/features/matching/RecommendationPanel.tsx, src/features/user/ProfileHeader.tsx
- src/components/layout/AppLayout.tsx, src/pages/ProjectNotificationsPage.tsx
- src/app/router/AppRouter.tsx, src/main.tsx
- README.md, docs/frontend-pages.md, docs/decisions-pending.md, docs/demo-scenarios.md, docs/changes-follow-matching.md

## 검증

- npm run build 성공 (TypeScript + Vite).
- npm test: 17개 파일, 218개 통과. 기존 187개 + Mock 분석/사용량 서비스 15개 + 팔로우/알림 서비스 6개 + 화면 렌더링 10개.
- 서비스: 기술/분야/직군 추출과 단어 오인 방지, 부족 정보·미정/복수 직군 질문, 조건별 순위·이유 변화, 무료 15회 차단/프리미엄 한도/계정 분리/한국 시각 월 경계/실패 미차감/본인 제외/저장 및 이관.
- 팔로우: 공통 관계·피드 반영, 기본 설정·권한, 선택한 새 글/모집 알림 생성과 중복 방지, 언팔로우 시 해제, 기존 저장값 이관.
- 렌더링: 목록·검색·프로필·협업 상태·알림 설정, 공개 목록/빈 상태, 계정별 알림 표시, 메인/추천 공통 사용량, 무료 잠금/프리미엄 상세·한도, 제한 도달, 타 계정 결과 비노출, 비로그인 안내.
- localhost:5173의 팔로잉/추천 및 설정/추천 모듈 HTTP 200.
- 실제 브라우저의 클릭·추가 질문 진행·배치·월 경과 자동 갱신은 직접 검증하지 않았습니다. 서비스 시나리오/정적 렌더링/HTTP 확인과 구분합니다.
- 의존성 및 백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 수정한 실패

- 빌드 명령을 루트에서 실행해 package.json 부재로 실패 → frontend에서 재실행했습니다.
- 목록의 null 필터가 TypeScript 타입을 좁히지 못해 명시적 타입 가드로 수정했습니다.
- 별칭 정규식 문자열의 이스케이프 구문 오류를 수정하고 리터럴 마침표/JavaScript·Java 오인 방지 테스트를 추가했습니다.
- 수정 후 빌드 및 218개 전체 테스트 통과.

## 정책과 한계

- 월 100회, 점수 가중치, 추가 질문 조건, 알림 기본값은 임시 데모 정책이며 실제 서비스 정책은 미확정입니다.
- 사용량은 브라우저 저장소 기반입니다. 저장소를 다른 브라우저/컴퓨터와 동기화하거나 실제 서버에서 제한하지 않습니다. 저장 실패는 현재 세션 유지 안내를 표시합니다.
- 프로젝트와 프로필 저장 경계 사이에서 새 공고 알림 이벤트를 연결했으며 외부 푸시·메일·실시간 서버는 없습니다.
- 실제 Gemini/OpenAI API, DB, 인증 서버, 결제, 실제 채팅, 전체 SNS 반응 알림/읽음 처리는 추가하지 않았습니다. 현재 단계만 진행했습니다.

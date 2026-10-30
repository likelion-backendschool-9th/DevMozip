# 프론트엔드 1차 수정 — 개인 공간 및 회원가입 온보딩

현재 요청한 1차 수정만 구현했습니다. React + TypeScript + Vite와 기존 도메인 폴더·공통 상태 구조를 유지했고 Spring Boot·API·DB·결제/OAuth는 변경하지 않았습니다.

## 구현

### 프로필 및 개인 공간

- 본인 `/users/:userId`: 왼쪽 개인 계정 Aside + 오른쪽 기존 배너/사용자 정보 + 소개/포트폴리오/내 글/활동/리뷰 5개 가로 탭. 다른 사용자의 공개 프로필에는 Aside가 없습니다.
- `/profile/me/likes`: 기존 SNS 좋아요 상태로 목록·작성자·본문·원본 이동·취소·날짜를 표시합니다. 새 좋아요 시각을 공통 상태에 저장하고 취소하면 목록과 날짜를 즉시 제거합니다. 이전 좋아요는 보존하되 날짜를 임의 생성하지 않습니다.
- `/profile/me/bookmarks`: 게시글/프로젝트 탭과 공통 북마크 상태를 사용합니다. 원본 이동과 해제는 기존 PostCard/RecruitmentCard/버튼을 재사용합니다. 숨김/삭제된 원본은 목록에 노출하지 않습니다.
- 비로그인 상태에는 개인 기록을 노출하지 않습니다. 비공개 계정의 본인 활동은 유지합니다. 헤더 프로필 메뉴에도 개인 페이지 링크를 추가했습니다.

### 개인정보 및 계정 설정

- 기존 Mock 이메일 표시, 현재 비밀번호 확인·새 비밀번호 형식/일치·확인 모달.
- **비밀번호 입력은 React 메모리에서만 사용하고 제출 후 비웁니다. 변경 확인 시각만 저장하며 새 비밀번호 원문/해시는 저장하지 않고 실제 로그인 비밀번호도 바꾸지 않습니다.** 기존 가입 데모의 로그인용 해시 방식은 유지했습니다.
- 카카오 연동/해제는 확인 모달과 계정별 boolean 상태만 제공합니다. 실제 OAuth/토큰/외부 요청은 없습니다.
- 기존 구독 상태·가상 카드·결제 예시·구독 관리 이동을 연결했습니다. 실제 결제 정보 입력/저장은 없습니다.
- 계정 잠금의 의미를 **비공개 계정**으로 정정했습니다. 로그인과 본인 기능을 유지하며 타인/비로그인 방문자에게 프로필 상세·팔로우 목록·SNS 작성 글을 숨깁니다. 검색·추천·피드·퍼간 원본·관련 활동 알림에서도 해당 상세 내용을 숨깁니다. 공개로 되돌리면 다시 표시합니다. 관리자 정지와 별개입니다.
- 계정 전환 시 설정 화면의 비밀번호 폼 상태를 초기화합니다.

### 4단계 온보딩

- 신규 가입 후 자동 로그인하여 `/onboarding`으로 이동합니다. 기존/데모/이전 가입 계정은 기본 완료 상태를 유지합니다.
- 1단계: 선택 사진·닉네임·한 줄 소개. PNG/JPEG/WebP 256KB 이하, 기본 이미지 사용 가능.
- 2단계: 복수 직군·경력·복수 기술·관심 개발 분야·기타 직접 입력(최대 5개/각 30자).
- 3단계: 협업 여부·관심 프로젝트 분야·연락 시간·선택 GitHub/블로그.
- 4단계: 프로필 미리보기, 전체 재검증, 완료 후 내 프로필.
- 이전/다음·필수값/닉네임 중복/URL 검증·선택 태그 삭제·계정별 초안/단계 복원·완료 후 초안 제거.
- 완료 정보는 프로필·사진·헤더 닉네임·추가 직군 검색·관심 분야에 반영합니다. 기존 대표 `job` 필드를 유지하면서 복수 `jobs`를 추가하여 기존 기능을 보존했습니다.
- 실제 개인정보 대신 발표용 가상 값만 입력하도록 안내합니다. 미완료 계정에는 이어서 작성 안내를 제공합니다. 전체 앱의 강제 온보딩 라우팅은 추가하지 않았습니다.

## 저장 및 이전 데이터

- 새 키 `devmozip:account-area:v1`: Mock 연동/공개 설정/비밀번호 확인 시각/온보딩 초안·단계·완료 상태. 허용한 필드만 직렬화하여 예상하지 않은 이메일·비밀번호 필드는 제거합니다.
- 기존 `devmozip:profiles:v1`: 선택적 사진/복수 직군/추가 분야/프로젝트 관심 정보 및 `social.likedAt`을 확장했습니다. 기존 좋아요/북마크/글/리뷰/프로필 데이터를 유지합니다.
- 기존 계정에 온보딩 필드가 없으면 완료로 해석하며 새 가입 계정에만 미완료 표시를 만듭니다.
- 기존 `devmozip:accounts:v1`은 완료된 신규 계정의 닉네임/대표 직군 등 비민감 Mock 프로필 정보와 완료 표시를 갱신합니다. 역할은 기존 기본 일반 회원 구조를 유지합니다.
- 전체 데모 초기화에 개인 설정/온보딩 초안도 포함합니다. 가입 계정은 기존 정책대로 유지합니다.
- 저장소 오류 시 현재 세션 상태를 유지하고 저장 실패 안내를 표시합니다. 저장이 차단되면 새로고침 후 보존을 보장하지 않습니다.

## 수정 파일

`frontend/src/` 기준 신규:

- `types/account.ts`, `mocks/account.ts`
- `services/accountAreaService.ts`, `services/onboardingService.ts`, `services/personalLibraryService.ts`
- `components/layout/AccountLayout.tsx`
- `features/onboarding/TagChoices.tsx`, `features/onboarding/OnboardingPreview.tsx`
- `pages/LikedPostsPage.tsx`, `pages/BookmarksPage.tsx`, `pages/AccountSettingsPage.tsx`, `pages/OnboardingPage.tsx`
- `styles/account.css`, `services/accountRevision.test.ts`

기존 수정:

- `types/auth.ts`, `types/profile.ts`, `types/social.ts`: 선택적 완료·사진/복수 정보·좋아요 시각.
- `services/authService.ts`, `services/profileService.ts`, `services/socialService.ts`, `services/searchService.ts`: 가입 표시·프로필 반영/이관·좋아요 날짜·복수 직군 검색.
- `app/providers/AuthProvider.tsx`: 공통 개인 설정·공개 여부·가입 후 세션·완료 메타데이터.
- `pages/ProfilePage.tsx`: 본인만 AccountLayout 적용.
- `features/auth/SignupForm.tsx`: 온보딩 이동·가상 데이터 안내.
- `features/user/ProfileAvatar.tsx`, `ProfileHeader.tsx`, `AboutTab.tsx`: 사진·복수 직군·프로젝트 관심 표시.
- `components/layout/AppLayout.tsx`: 개인 메뉴·미완료 안내·전체 초기화.
- `app/router/AppRouter.tsx`, `mocks/routes.ts`, `main.tsx`: 신규 네 경로·스타일·지연 로딩.

검증/문서:

- `scripts/verify-account-revision.mjs` 신규.
- `docs/artifacts/revision-1/` 신규 UI 결과·스크린샷, `regression/` 기존 흐름 재검증 결과.
- `README.md`, `docs/frontend-pages.md`, `docs/decisions-pending.md`, `docs/demo-scenarios.md`, 본 문서.

## 실제 검증 결과

| 항목 | 결과 |
|---|---|
| `npm run build` | TypeScript/Vite 성공, 최종 경고 없음 |
| `npm test` | 22개 파일, **300개 통과** |
| 신규 서비스 테스트 | 17개: 온보딩/기존 계정 이관/프로필·검색/민감 필드 제거/비공개 이관/저장 오류/좋아요 날짜/북마크 |
| 신규 실제 Chromium | **12개 검사 통과**, PC 1440×1000 |
| 기존 브라우저 회귀 | **48개 검사 통과**, A~E·37개 화면·초기화·저장 오류 포함 |
| 콘솔/페이지 오류 | 신규·회귀 모두 0건 |
| XHR/fetch 비즈니스 요청 | 신규·회귀 모두 0건 |
| 백엔드 | 변경/실행/재빌드 없음 |

신규 브라우저 검사는 본인/공개 Aside 차이·5개 탭, 좋아요 날짜/취소/계정 분리, 두 종류 북마크 해제/새로고침, 비밀번호 불일치/확인/원문 미저장, 카카오 확인 취소/연동/해제, 비공개/타인 접근 차단/공개 복원, 데모 3종 완료 상태, 실제 가입→온보딩, 사진 로딩·태그 삭제·초안 복원·이전/다음·필수/URL 검증·미리보기·완료, **가입 닉네임을 온보딩에서 수정한 후 헤더/프로필/새로고침 일치**, 비로그인 접근을 확인했습니다.

- [신규 12개 원본 결과](artifacts/revision-1/report.json)
- [회귀 48개 원본 결과](artifacts/revision-1/regression/report.json)
- [본인 프로필 Aside](artifacts/revision-1/own-profile.png), [공개 프로필](artifacts/revision-1/public-profile.png)
- [좋아요](artifacts/revision-1/likes.png), [북마크](artifacts/revision-1/bookmarks.png), [설정](artifacts/revision-1/settings.png), [비공개 설정](artifacts/revision-1/private-settings.png), [타인의 비공개 프로필](artifacts/revision-1/private-profile.png)
- [개발자 온보딩](artifacts/revision-1/onboarding-developer.png), [최종 미리보기](artifacts/revision-1/onboarding-preview.png), [완료 프로필](artifacts/revision-1/onboarded-profile.png)

검증은 격리 브라우저와 프로덕션 미리보기 `http://localhost:4173`를 사용하여 일반 브라우저의 기존 데이터에는 영향을 주지 않았습니다.

## 실행 및 재현

```bash
cd frontend
npm run dev
# 또는 npm run build 후 npm run preview -- --port 4173 --strictPort
```

검증용 Playwright가 준비된 환경에서 저장소 루트:

```bash
PLAYWRIGHT_BROWSERS_PATH=/tmp/devmozip-browser/browsers \
DEVMOZIP_PLAYWRIGHT_MODULE=/tmp/devmozip-browser/node_modules/playwright/index.mjs \
  node scripts/verify-account-revision.mjs

PLAYWRIGHT_BROWSERS_PATH=/tmp/devmozip-browser/browsers \
DEVMOZIP_PLAYWRIGHT_MODULE=/tmp/devmozip-browser/node_modules/playwright/index.mjs \
DEVMOZIP_BASE_URL=http://localhost:4173 \
DEVMOZIP_VERIFY_OUTPUT=docs/artifacts/revision-1/regression \
  node scripts/verify-presentation.mjs
```

도구 설치 방법은 [이전 최종 검증](final-verification.md)의 재현 절을 참고하세요. 프로젝트 실행 의존성은 추가하지 않았습니다.

## 중간 실패와 남은 범위

초기 빌드의 500KB 청크 경고는 프로필 화면 지연 로딩으로 해결했고 최종 주 JS는 약 477KB입니다. 신규 브라우저 테스트 1개는 닫힌 프로필 메뉴에서 전체 닉네임을 읽으려 해 실패했으며 메뉴를 실제로 열고 확인하도록 수정 후 전체 재통과했습니다. 최종 실패 항목은 없습니다.

실제 비밀번호 변경/인증·카카오 OAuth·결제·계정 보안 서버·API·DB는 의도적으로 구현하지 않았습니다. 본인 프로필 사진은 브라우저 Data URL이며 서버 업로드가 아닙니다. 다른 브라우저/기기의 상태 동기화와 모바일 실기기 검증은 하지 않았습니다. **2차 작업은 시작하지 않았습니다.**

## 계정 잠금 의미 정정

로그인 제한으로 해석했던 동작을 사용자 의도에 맞게 비공개 계정으로 수정했습니다. 저장 필드는 `isPrivate`를 사용하고 이전 `locked` boolean은 읽을 때 자동 이관합니다. 본인 기능/로그인은 제한하지 않습니다.

추가·수정 파일: `services/privacyService.ts` 신규, 계정 타입/저장/AuthProvider/AccountLayout/설정/ProfilePage, 검색/HomeProvider, 전체·메인 피드/PostCard/상세/퍼간 글/좋아요·북마크 목록, FollowingPage/ProfileHeader/MessagingProvider, 서비스 및 브라우저 테스트, 현재 문서. 실제 서버·API는 변경하지 않았습니다.

비공개 계정은 본인만 상세/작성 SNS 글을 볼 수 있는 Mock 정책입니다. 팔로우 승인이나 승인된 팔로워 예외 기능은 이번 수정에 추가하지 않았습니다. 관리자의 기존 운영 관리 화면과 공개 프로젝트 이력은 별도 운영 범위를 유지합니다.

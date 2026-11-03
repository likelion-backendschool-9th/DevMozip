# 로그인·회원가입 프론트엔드

## 구현

- Mock 이메일 로그인, 로그인 유지 옵션, 로그아웃, 카카오 데모 안내, 비밀번호 찾기 화면.
- 회원가입 필드: 이메일/비밀번호/확인/닉네임/직군/경력/다중 기술/다중 관심 분야/협업 가능 여부.
- 이메일과 닉네임은 Mock 중복 확인 필수. 입력을 바꾸면 확인 결과를 무효화하며, 등록 직전에도 중복/폼 검사를 수행합니다.
- 비밀번호는 영문·숫자 포함 8~64자, 닉네임은 한글·영문·숫자·공백·밑줄 2~20자. 직군/경력, 기술 및 관심 분야 하나 이상 필수. 현재 데모용 입력 규칙입니다.
- 가입 완료 후 로그인 화면으로 이동합니다. 신규 가입자는 일반 회원이며 가입 계정으로 재로그인할 수 있습니다.
- 일반/프리미엄/관리자 데모 계정. 헤더와 로그인 화면의 공통 DemoAccounts로 전환합니다.
- 공통 AuthProvider가 유일한 로그인 상태를 관리하며 HomeProvider는 역할을 읽기만 합니다. 헤더 닉네임/프로필 ID/관리자 링크, 추천 이유와 팔로잉 표시가 일관되게 반영됩니다.
- 관리자 데모도 상세 추천 이유를 볼 수 있습니다. 관리자 화면 자체는 기존 준비 라우트입니다.

## 저장과 로그아웃

- 로그인 유지 선택: localStorage. 미선택: sessionStorage. 둘 다 현재 탭 새로고침은 유지합니다.
- 사용자 ID만 세션에 기록하고 로드 시 계정 목록에서 역할을 복원합니다. 손상/알 수 없는 ID/버전은 복원하지 않습니다.
- 새 계정은 별도 localStorage에 유지합니다. 원문 비밀번호는 저장하지 않고 salt와 SHA-256 데모 해시로 비교합니다. 이 로직은 실제 보안 인증을 대체하지 않습니다.
- 저장 실패 시 가입은 실패 안내, 로그인은 메모리 상태로 유지하고 알림을 표시합니다.
- 로그아웃 시 양 저장소의 세션과 메인 추천 입력이 초기화됩니다. 가입 계정은 삭제하지 않습니다.
- 이전 `devmozip:home:v1`의 역할은 폐기하고 기존 추천 입력/초기 팔로잉 데이터는 복원합니다.
- 비밀번호 찾기는 올바른 이메일 입력 후 동일한 안내를 보여주며 메일/계정 조회/비밀번호 변경은 하지 않습니다. 카카오도 OAuth를 호출하지 않습니다.

## 변경 파일 (frontend 기준)

신규:
- src/types/auth.ts
- src/mocks/auth.ts
- src/services/authService.ts, authService.test.ts
- src/app/providers/AuthProvider.tsx
- src/features/auth/AuthShell.tsx, LoginForm.tsx, SignupForm.tsx, PasswordRecoveryForm.tsx, DemoAccounts.tsx
- src/pages/LoginPage.tsx, SignupPage.tsx, PasswordRecoveryPage.tsx
- src/styles/auth.css

수정:
- src/app/providers/HomeProvider.tsx, src/app/router/AppRouter.tsx, src/main.tsx
- src/components/layout/AppLayout.tsx
- src/features/matching/RecommendationPanel.tsx
- src/types/home.ts, src/mocks/home.ts, src/mocks/routes.ts
- src/services/homeService.ts, homeService.test.ts
- 루트 README.md, docs/frontend-pages.md, docs/changes-auth.md

## 검증

- npm run build 성공 (TypeScript + Vite).
- npm test: 4개 파일, 52개 통과. 기존 메인/설정/라우트 테스트와 신규 인증 테스트 16개. 경로 추가로 라우트 테스트 1개 증가.
- 신규 검증: 필수값/비밀번호 일치/잘못된 선택지, 중복/재중복 확인, 일반·프리미엄·관리자 로그인, 잘못된 로그인 거절, 가입·저장·재로그인, 원문 비밀번호 미저장, 손상 데이터, 유지/미유지 저장소 선택, 세션 복원/제거, 저장 실패.
- 기존 개발 서버의 /login, /signup, /forgot-password 및 AuthProvider 모듈 HTTP 200.
- 브라우저 시각/클릭 통합 검증은 도구가 없어 미수행. HTTP 200은 SPA 응답 확인이며 실제 렌더링 검증이 아닙니다.
- 백엔드·외부 연동·의존성 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.
- 중간 보완 스크립트의 소스 상대 경로 오류는 src/ 접두사로 수정했습니다. 최종 빌드/테스트 실패 없음.

## 범위 외

실제 인증/토큰/이메일/OAuth/DB/API, 실제 비밀번호 재설정, 관리자 기능 및 프로필 상세는 구현하지 않았습니다. 다음 단계는 진행하지 않습니다.

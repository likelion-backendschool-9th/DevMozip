# Next.js App Router 마이그레이션 보고서

2026-11-01. 기존 React/TypeScript Mock 프로토타입을 **Next.js 16.4.3 App Router**로 이전했습니다. [분석·계획](nextjs-migration-plan.md)에 따라 기존 URL·기능·브라우저 저장 상태를 보존했습니다. Spring Boot와 실제 API/DB/외부 AI/결제/WebSocket은 구현하거나 변경하지 않았습니다. 커밋/푸시는 수행하지 않았습니다.

## 변경된 구조

프론트엔드 프로젝트 루트는 `frontend/`이며 그 바로 아래 `app/`이 있습니다.

```text
frontend/
  app/
    layout.tsx                 서버 루트 레이아웃, 전역 CSS 먼저 로드
    globals.css                토큰·reset·기본 태그·공유 유틸리티
    ClientProviders.tsx        마운트 후 Mock Provider 복원
    error.tsx, not-found.tsx, icon.svg
    page.tsx
    _components/               홈 전용 추천 시작/모집/SNS 미리보기
    login/page.tsx, _components/
    signup/page.tsx, _components/
    forgot-password/page.tsx, _components/
    onboarding/page.tsx, _components/
    users/[userId]/page.tsx, _components/    프로필 상단/5개 탭
    profile/me/likes/, profile/me/bookmarks/
    settings/account/
    projects/page.tsx, _components/
    projects/new/
    projects/[projectId]/page.tsx, _components/
    projects/[projectId]/manage/page.tsx, _components/
    projects/[projectId]/reviews/write/
    my-projects/, my-projects/[projectId]/manage/
    matching/page.tsx, _components/
    feed/, posts/[postId]/, posts/[postId]/edit/, posts/new/
    following/, messages/, chats/[projectId]/, notifications/, premium/, admin/
  components/
    common/, layout/, navigation/, auth/
    projects/, posts/, profile/, follow/, messaging/
  lib/
    state/                     기존 Context 6개
    account/, profiles/, projects/, matching/, social/, messaging/
    administration/, search/, settings/, shared/, utils/
    navigation.ts              Next URL 조작 도우미, 경로 등록 없음
  mocks/, types/, public/
  tests/                       Next mock·화면·실제 경로 파일·SSR 경계 검사
  next.config.ts, next-env.d.ts, tsconfig.json, vitest.config.ts
  package.json, package-lock.json, .env.example
```

`src` 디렉터리 자체를 제거했습니다. 따라서 금지한 `src/features`, `src/pages`, `src/services`, `src/styles`, `src/app/providers`, `src/app/router`도 없습니다. 업무 라우팅은 28개의 실제 `app/**/page.tsx`로 구성하며 React Router나 catch-all SPA 우회 라우터를 사용하지 않습니다. 전체 [파일 이동 매핑 JSON](nextjs-file-map.json), [경로 매핑](routing.md)을 제공합니다.

## 주요 변경 파일

| 범위 | 주요 파일 |
|---|---|
| Next 설정·명령·의존성 | `frontend/package.json`, `package-lock.json`, `next.config.ts`, `next-env.d.ts`, `tsconfig.json`, `.env.example`, 루트 `.gitignore` |
| 서버/클라이언트 진입 | `frontend/app/layout.tsx`, `ClientProviders.tsx`, `error.tsx`, `not-found.tsx` |
| 파일 기반 경로 | `frontend/app/**/page.tsx` 28개 |
| 공통 UI | `frontend/components/layout/{AppLayout,AccountLayout}.tsx`, `components/navigation/ActiveLink.tsx`, `components/common/*`와 인접 CSS Module |
| 페이지 전용 UI | 각 경로의 `_components/*Screen.tsx`와 해당 페이지의 전용 컴포넌트 |
| 기존 상태/Mock 로직 | `frontend/lib/state/*`, `lib/{account,profiles,projects,matching,social,messaging,...}`, `mocks`, `types` |
| CSS | 인접 CSS Module 76개, `frontend/app/globals.css` |
| 정적 자산 | `frontend/public/devmozip-logo.svg`, `public/fonts/*`, `app/icon.svg` |
| 단위 검사 | `frontend/tests/routes.test.ts`, `hydration-boundary.test.tsx`, `tests/helpers/RouterFixture.tsx`, `tests/setup.tsx`, 이전한 기존 도메인/화면 검사 |
| 브라우저 검사 | 신규 `scripts/verify-next-migration.mjs`, 기존 `scripts/verify-*.mjs`의 Next 내비게이션/접근성 알림/문서 로드 구분 |

제거한 실행 파일은 `frontend/src/main.tsx`, 기존 AppRouter, `frontend/index.html`, `vite.config.ts`와 이전 배포 산출물입니다. Vite 및 React Router 직접 의존성은 제거했습니다. Vitest 내부의 Vite 도구 의존성은 테스트 실행기용이며 프론트엔드 실행 프레임워크가 아닙니다.

## 기능·상태 유지 방식

- `next/link`, `next/navigation`으로 링크·동적 ID·쿼리·뒤로가기/앞으로가기를 처리합니다. URL 탭/필터/알림 action/리뷰 링크를 유지합니다. `lib/navigation.ts`는 Next 호출만 감싸고 경로를 등록하지 않습니다.
- 홈→추천은 공유 Home 상태에 첫 메시지와 새 대화를 만든 뒤 `/matching`으로 이동합니다. location.state와 재실행 효과를 제거했습니다.
- 루트 레이아웃·각 page와 metadata는 Server Component입니다. 폼/대화/Context 등 상호작용은 Client Component입니다. 서버와 첫 클라이언트 렌더에는 같은 로딩 UI를 표시하고 마운트 후 저장소 기반 Provider를 함께 초기화합니다.
- 기존 localStorage/sessionStorage 키·버전·검증·오류 처리·사용량을 유지했습니다. 서버가 저장소에 접근하거나 Provider 초기 저장 효과가 이전 상태를 덮어쓰지 않습니다. 실제 저장 상태를 복원한 본문은 JavaScript 마운트 후 표시하며, 전체 업무 본문의 SSR/SEO는 이번 범위가 아닙니다.
- CSS는 컴포넌트 옆의 Module로 이전하고 기존 의미 있는 클래스도 유지합니다. 모듈 scope와 cascade layer로 적용 범위를 제한합니다. 전역 선언을 컴포넌트 CSS보다 먼저 불러와 페이지 이동/직접 접근 시 동일한 우선순위를 보장합니다. Mock 데이터의 색상 이름은 공유 유틸리티로 유지합니다.
- `suppressHydrationWarning`을 사용하지 않았습니다. 테스트용 URL fixture는 정적 화면 검사에서만 사용하고 런타임 라우팅에는 포함하지 않습니다.
- 데모 초기화는 Next.js 비동기 이동 후 reload 경합을 피하도록 상태 초기화 후 메인으로 직접 이동합니다.

## 실행·빌드·타입·단위 검사

```bash
cd frontend
npm ci
npm run dev         # http://localhost:5173, Next.js 실제 기동 확인
npm run build      # Next.js 프로덕션 빌드 성공
npm run start      # http://localhost:4173, 실제 기동/화면 확인
npm run typecheck  # next typegen + tsc --noEmit 성공
npm test           # 26개 파일, 363개 통과
```

프로덕션 빌드에서 28개 업무 경로가 정적/동적 페이지로 생성됐습니다. 새로고침과 직접 URL 접근도 Next 서버가 처리합니다. Next 아이콘/프레임워크 경로는 업무 페이지 수에 포함하지 않습니다. 개발·프로덕션 모두 기존 포트를 유지하고 0.0.0.0에서 수신합니다. 백엔드는 기동/빌드하지 않았으며 기본 소스·설정·Wrapper 파일 **12개의 SHA-256 비교 결과 변경 없음**을 확인했습니다.

단위 검사에는 기존 상태/권한/추천/리뷰/투표 검사, 28개 실제 page 파일과 경로 중복, 서버 렌더의 저장소 읽기/쓰기 차단, CSS Module과 컴포넌트 인접 배치를 포함합니다.

## 실제 브라우저·서버 검사

격리 Chromium으로 실제 Next 서버를 검사했습니다. 아래 **398개 검사 모두 통과**했습니다. 발표자의 기존 브라우저 저장소는 사용하지 않았습니다.

| 검사 | 결과 | 근거 |
|---|---|---|
| 기존 화면·A~E·초기화/저장소 오류 | 48/48 | [JSON](artifacts/nextjs-migration/regression/report.json) |
| 계정·비공개·가입/온보딩 | 12/12 | [JSON](artifacts/nextjs-migration/account/report.json) |
| 프로젝트·기간·리뷰/온도 | 12/12 | [JSON](artifacts/nextjs-migration/project/report.json) |
| 강퇴 투표·모집/상세 권한 | 12/12 | [JSON](artifacts/nextjs-migration/chat/report.json) |
| 대화형 추천·기록·15/100회·월 경계 | 13/13 | [JSON](artifacts/nextjs-migration/matching/report.json) |
| 6개 해상도의 화면/상태 | 216/216 | [JSON](artifacts/nextjs-migration/responsive/report.json) |
| 6개 해상도의 실제 조작/모달/종료/리뷰 | 42/42 | [JSON](artifacts/nextjs-migration/interactions/report.json) |
| Next 개발/프로덕션 경계·URL·404·전체 HTTP 경로 | 43/43 | [JSON](artifacts/nextjs-migration/framework/report.json) |

6개 너비는 1920/1440/1280/1024/768/390px, 높이는 1000px입니다. 가로 넘침·헤더 겹침·작은 화면의 실제 접힘 표시를 확인했습니다. 768/390px 조작은 Chromium 터치 에뮬레이션도 사용합니다.

Next 전용 43개는 개발/프로덕션 각 7개 검사(서버/첫 클라이언트 경계, 기존 로그인 복원·동적 URL·새로고침, 쿼리와 뒤로가기/앞으로가기, CSS Module/공유 색상, 메인 첫 메시지/추천 기록, 390px 메뉴의 실제 숨김/펼침, 404 복구 화면)와 28개 업무 URL HTTP 200/미등록 URL HTTP 404를 확인합니다. 업무 데이터 없음/Mock 권한 안내는 클라이언트 화면으로 처리하므로 미등록 URL의 404와 구분합니다.

최종 각 보고서의 콘솔 오류·런타임 오류·비즈니스 API 요청은 0건입니다. hydration 관련 콘솔/런타임 오류도 없습니다. Next의 정상 RSC/정적 청크 요청은 프레임워크 요청으로 분리했습니다. 의도적으로 접근한 `/missing-page`의 HTTP 404 네트워크 메시지는 오류 집계에서 구분합니다. 다른 자산/스크립트 오류를 일괄 무시하지 않습니다.

## 발견 후 수정한 항목

- 기존 location.state 전달을 공유 상태 기반으로 이전하고, demo 초기화의 비동기 이동/reload 경합을 수정했습니다.
- Next의 접근성 route announcer가 `role=alert`를 사용하므로 폼 오류 검사는 본문으로 범위를 제한했습니다.
- Next 라우팅의 smooth scroll 안내에 따라 루트 html에 `data-scroll-behavior`를 설정했습니다.
- 실제 시각 검사에서 접힌 Aside가 보이는 cascade layer 순서 문제를 발견했습니다. 루트 전역 CSS를 ClientProviders보다 먼저 import하도록 고쳤고, ARIA 값뿐 아니라 메뉴의 실제 visibility를 검사하도록 보강했습니다.
- Next 문서/자산 로드와 다중 Chromium의 초기 요청에서 발생한 타임아웃은 DOMContentLoaded 이후 실제 화면 표시를 기다리도록 검증을 조정하고 내비게이션 타임아웃을 분리한 뒤 재실행했습니다. 화면/상태 조건 자체는 유지했습니다.
- 기본 샌드박스에서 TypeScript 하위 프로세스 출력 확인이 중단된 초기 빌드는 허용된 실행 환경에서 재실행해 성공을 확인했습니다.

최종 미통과 항목은 없습니다.

## 남은 범위

실제 모바일 하드웨어, Safari/Firefox, 모든 높이/확대/가상 키보드 조합, 실제 LAN의 다른 PC 접속은 이번에 재검증하지 않았습니다. 기존 임시 서비스 정책과 브라우저 내 저장/기기 간 비동기화의 한계는 유지합니다. 전체 본문 SSR·실제 서버 인가·API/DB/AI/결제는 후속 작업이며 이번에 연결하지 않았습니다.

[실행/구조](../README.md), [Mock 구조](mock-data.md), [현재 아키텍처](architecture.md), [시연 순서](demo-scenarios.md), [미확정 정책](decisions-pending.md). 현재 요청한 마이그레이션만 수행했습니다.

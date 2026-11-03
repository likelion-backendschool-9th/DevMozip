# 현재 프론트엔드 구조

2026-11-01 Next.js 마이그레이션 기준입니다. React 19/TypeScript와 Mock 상태를 유지하며 Next.js 16.4.3 App Router를 사용합니다. 이전 `src` 계층과 React Router/Vite 진입점은 제거했습니다.

## 위치와 책임

- `frontend/app`: 프론트엔드 프로젝트 루트의 App Router. `page.tsx`와 `[id]`가 경로를 정하며 루트 layout, error, not-found, globals를 포함합니다.
- `app/{경로}/_components`: 해당 화면의 UI와 인접 `*.module.css`. 프로필 탭은 `app/users/[userId]/_components`, AI 대화는 `app/matching/_components` 등입니다.
- `frontend/components`: 둘 이상의 경로에서 사용하는 공통 레이아웃·컨트롤·프로젝트/게시글/메신저 UI. 각 스타일 파일도 컴포넌트 옆에 둡니다.
- `frontend/lib/{account,profiles,projects,matching,social,messaging,administration,search,settings,shared,utils}`: 기존 서비스의 검증·상태 변경·저장 함수. 실제 API 호출은 없습니다.
- `frontend/lib/state`: 기존 6개 Context. 컴포넌트 사이 상태를 공유하고 기존 저장 키/버전을 유지합니다.
- `frontend/mocks`, `types`: 원본 데이터·선택지·임시 정책, 도메인 타입.
- `frontend/public`: 로고/로컬 글꼴. `app/icon.svg`는 Next.js 아이콘 규칙입니다.
- `frontend/tests`: 화면 SSR 단위 검사의 테스트 전용 URL fixture와 실제 App Router 파일 검사. 애플리케이션 라우팅은 여기서 실행하지 않습니다.

## 서버/클라이언트 경계

루트 레이아웃·각 page/metadata는 Server Component입니다. `ClientProviders`가 마운트 전에는 일관된 로딩 UI를 반환하고 이후 Prototype→Auth→Profile→Project→Messaging→Home을 순서대로 마운트합니다. 서버에서 브라우저 저장소에 접근하지 않고 초기 저장 효과가 기존 상태를 덮어쓰지 않도록 합니다. 상태 기반 화면은 클라이언트에서 렌더링하며 전체 본문 SSR은 후속 사항입니다. `suppressHydrationWarning`으로 오류를 숨기지 않습니다.

`next/link`와 `next/navigation`을 사용하고 `lib/navigation.ts`는 URL 쿼리/뒤로가기 도우미만 제공합니다. 별도 경로 등록/브라우저 라우터는 없습니다. 메인에서 새 추천 대화를 공유 상태에 기록하고 `/matching`으로 이동하므로 location.state에 의존하지 않습니다.

## 스타일

화면/컴포넌트 스타일은 인접 CSS Modules에서 로컬 scope로 제한합니다. 기존 의미 있는 클래스명도 유지하여 도메인 상태/공통 유틸리티/브라우저 검증을 보존합니다. cascade layer로 이전 테마/반응형 우선순위를 고정합니다. 토큰·reset·기본 태그·공유 컨트롤 유틸리티/Mock 색상 이름은 `app/globals.css`에 둡니다. 별도 styles 폴더는 없습니다.

## 이전 기록과 백엔드

최초 참고 저장소 `LuvZian/superOfTheKing`은 당시 404여서 분석 불가였고 추측하지 않았습니다. 그 후 사용자가 요청한 구조 변경을 현재 기준으로 반영했습니다. `.github/workflows/notify.yml`과 Spring Boot 파일은 이번 작업에서 변경하지 않았습니다. 백엔드 비즈니스 로직/API/DB/외부 SDK 연결은 이번 범위가 아닙니다.

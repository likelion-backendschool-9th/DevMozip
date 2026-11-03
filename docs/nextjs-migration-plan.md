# Next.js 마이그레이션 계획과 결정

2026-11-01. 분석 후 제시한 계획에 따라 기존 Mock 프로토타입을 Next.js App Router로 이전합니다.

## 분석 기준

기존 구조는 `frontend/src`의 Vite 진입점, React Router 명시 경로 28개, Prototype/Auth/Profile/Project/Messaging/Home Context 6개, 도메인별 Mock 검증/저장 함수, 전역 CSS 묶음입니다. 브라우저 초기 렌더에서 localStorage를 읽고 프로젝트 상태를 저장하는 효과가 있어 서버 렌더링에 그대로 올리면 상태 불일치 또는 이전 저장소 덮어쓰기 위험이 있습니다.

## 계획

1. Next.js 16.4.3과 기존 React 19/TypeScript를 사용합니다. 앱 루트는 **프론트엔드 프로젝트 루트 `frontend/app`**입니다. 모든 기존 경로를 개별 `page.tsx`와 `[projectId]`, `[postId]`, `[userId]`로 옮깁니다.
2. 페이지 전용 화면/컴포넌트를 해당 경로의 `_components`에 배치합니다. 실제 여러 경로에서 사용하는 UI는 루트 `components`, Mock/타입은 `mocks`/`types`, 상태·검증·저장은 `lib`에 둡니다. `src`와 금지한 하위 폴더는 제거합니다.
3. `next/link`, `next/navigation`을 사용합니다. URL 쿼리 변경 도우미는 경로 정의 없이 Next Router 호출만 담당합니다. 홈→추천 설명은 공유 Context에서 새 대화를 만든 뒤 이동하여 별도의 React Router location state를 없앱니다.
4. 루트 레이아웃/개별 page와 metadata는 Server Component, 상호작용/Context는 Client Component로 분리합니다. 마운트 전에는 동일 로딩 상태만 표시하고 이후 저장소 기반 Provider를 함께 마운트합니다. 초기 UI의 완전한 서버 렌더링은 이번 Mock 브라우저 구조의 목표가 아닙니다.
5. CSS는 인접한 CSS Module로 분산하고 공통 토큰/기본 태그/공유 유틸리티만 `app/globals.css`에 둡니다. 기존 의미 있는 DOM 클래스는 유지하면서 모듈 scope를 추가합니다. CSS cascade layer로 기존 스타일 우선순위를 유지합니다. 로고/글꼴은 `public`에서 로컬 제공됩니다.
6. 기존 단위 테스트는 Next 내비게이션 mock으로 바꾸고 실제 경로 파일 존재를 검사합니다. Next dev/start의 실제 Chromium 검사와 전체 기능·6개 해상도 회귀로 검증합니다. Next의 RSC/청크 요청은 서비스 API 요청과 분리합니다.

백엔드와 실제 서비스 API/DB는 변경하지 않습니다. 기존 localStorage 키/버전도 유지합니다. 이번 작업은 커밋/푸시하지 않습니다.

## 참고

- [공식 Vite 이전 안내](https://nextjs.org/docs/app/guides/migrating/from-vite)
- [공식 서버/클라이언트 컴포넌트](https://nextjs.org/docs/app/getting-started/server-and-client-components)
- [공식 CSS Modules 안내](https://nextjs.org/docs/app/getting-started/css)
- [공식 hydration 오류 안내](https://nextjs.org/docs/messages/react-hydration-error)
- 설치된 Next.js 버전의 `node_modules/next/dist/docs/`도 확인했습니다.
- 실제 완료 결과: [마이그레이션 보고서](changes-nextjs-migration.md)

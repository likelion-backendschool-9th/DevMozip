# 공통 레이아웃 및 메인 페이지 구현

## 변경 파일

- 신규: `src/types/home.ts`, `src/mocks/home.ts`, `src/services/homeService.ts`, `src/services/homeService.test.ts`.
- 신규: `src/app/providers/HomeProvider.tsx`, `src/components/common/{Button,Card,Input}.tsx`.
- 신규: `src/features/matching/RecommendationPanel.tsx`, `src/features/project/ProjectPreview.tsx`, `src/features/post/FeedPreview.tsx`.
- 신규: `src/pages/HomePage.tsx`, `src/styles/home.css` (추가 디자인 토큰 포함).
- 수정: `src/main.tsx`, `src/app/router/AppRouter.tsx`, `src/components/layout/AppLayout.tsx`, `src/mocks/routes.ts`, `src/pages/PlannedPage.tsx`, `src/pages/NotFoundPage.tsx`.
- 문서: `README.md`, `docs/frontend-pages.md`, `docs/changes-main.md`.

위 src 경로는 frontend 기준입니다. 기존 SVG 로고, 초기 설정 화면 소스, 공통 Modal/Toast, 백엔드는 유지했습니다. 기존 초기 설정 화면은 현재 홈 경로에서 서비스 메인으로 대체했습니다.

## 동작

- 로고/공통 내비게이션, 대상 선택 검색창, 로그인/일반/프리미엄 헤더, 프로필 드롭다운, 데모 전환·초기화 모달.
- 검색은 추후 검색 라우트에 대상과 검색어를 전달합니다. 실제 검색 결과와 인증은 구현하지 않았습니다.
- 프로젝트 설명 10자 이상 검증, 기술 다중 선택, 관심 분야 선택, 450ms 로딩 상태 후 결정적 Mock 추천.
- 추천 점수 = 기본 55 + 일치 기술 수 × 11 + 일치 관심 분야 수 × 13, 최대 98. 임시 데모 점수이며 실제 AI 분석이 아닙니다.
- 상위 개발자 3명/유사 프로젝트 2개. 일반/비로그인은 상세 이유 잠금, 프리미엄 데모에서 이유 공개.
- 모집 공고 최신·인기(조회 수)·마감일 오름차순 정렬, 상위 3개 표시.
- SNS 가로 슬라이드(카드 단위 화살표, scroll snap, 키보드/직접 스크롤), 끝에서는 화살표 비활성화. 추천·팔로잉 및 카테고리 조합, 로그인 안내/빈 상태.
- likes/comments는 미리보기 수치 표시이며 기능 버튼으로 위장하지 않습니다. 상세 글로 이동해 읽는 흐름만 제공합니다.
- 계정 역할과 추천 입력은 버전이 있는 localStorage에 유지. 손상값/저장 불가 방어. 입력 변경 시 추천 결과 초기화. 추천 결과/필터는 화면 로컬 상태.

## 검증

- `npm run build`: TypeScript 및 Vite 프로덕션 빌드 성공.
- `npm test`: 3개 파일, 35개 통과. 기존 29개와 추천 순위·점수 범위/정렬/필터/저장 복원 등 신규 6개.
- 실행 중인 localhost:5173 홈 및 HomePage.tsx 모듈 HTTP 200.
- 첫 빌드 명령은 루트에서 실행해 package.json 부재로 실패했으며 frontend 디렉터리로 정정 후 성공했습니다.
- 브라우저 및 자동화 도구가 없어 실제 렌더링·슬라이드 클릭·키보드·모달 포커스는 미검증. HTTP 응답/자동 테스트 결과와 구분합니다.
- 의존성 변경 및 백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 범위 외

검색 결과/로그인 폼/프로필/공고 상세/피드 상세/지원/실제 팔로우/알림/채팅/구독은 다음 요청 전까지 구현하지 않습니다. 무료 추천 월 횟수 제한과 실제 구독 정책도 이번 범위에 추가하지 않았습니다.

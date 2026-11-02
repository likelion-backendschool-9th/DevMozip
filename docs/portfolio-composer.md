# 포트폴리오 전용 작성·수정 페이지

## 사용자 흐름

- 프로필 포트폴리오 탭의 등록 → `/portfolios/new`
- 목록 또는 상세의 수정 → `/portfolios/[portfolioId]/edit`
- 신규 등록 후 내 프로필의 목록으로 이동, 수정 저장 후 해당 상세로 이동
- 본인 무료 회원도 작성·수정·본인 상세 확인 가능. 타인의 수정은 프리미엄/관리자여도 불가능하며 타인 상세 제한은 기존 정책 유지

## 자유 구성

프로젝트 설명, 나의 기여, 주요 기능, 문제 해결, 결과, 이미지 갤러리를 기본 틀로 제공한다. 제목·내용을 수정하고 섹션을 추가·삭제하거나 순서를 바꿀 수 있다. 기본 제목/섹션 순서를 강제하지 않는다.

- 텍스트 섹션: 일반 텍스트 / Markdown / HTML
- 이미지 갤러리와 링크 모음: 제목·본문 내 위치를 변경 가능. 첨부 내용은 이미지·링크 관리 패널에서 편집
- 정렬: 핸들을 마우스/포인터로 드래그. 키보드는 핸들 포커스 → Space → 방향키 → Space. 위/아래 버튼도 제공
- 섹션 1~20개, 제목 80자, 섹션 본문 20,000자. 갤러리와 링크 모음은 각각 1개까지
- 제목·목록·코드·표·링크와 기본 서식 도구를 제공. 형식 전환은 원문을 유지하며 자동 변환하지 않음
- 전체 미리보기와 실제 상세는 같은 본문 컴포넌트로 렌더링하여 제목·순서·서식을 일관되게 반영
- HTML은 저장·렌더링 시, Markdown은 렌더링 시 허용 목록으로 정리. 실행 스크립트·이벤트·위험 URL 제거
- 기본 프로젝트 요약/기간/역할/기술 스택과 첨부 이미지·링크 기능 유지. 직접 입력 기술 스택도 허용

## 초안과 호환성

초안 키는 `devmozip:portfolio-draft:v1:{userId}:{portfolioId|new}`. 500ms 지연 저장 및 페이지 종료 시 저장을 시도하고, 저장 실패 시 입력을 유지하며 안내한다. 계정별로 분리한다.

돌아가기는 초안을 유지한다. 취소 → 변경 취소를 확인하면 해당 초안과 저장하지 않은 변경을 버리고 원본으로 돌아간다. 등록·수정에 성공하면 초안을 지운다.

Portfolio.blocks는 선택 필드다. 기존 데이터는 기본 섹션으로 읽고, 자유 편집 문서의 제목·순서·원문을 보존한다. 기존 프로젝트 지원/AI 피드백이 참조하는 레거시 필드는 기본 섹션의 내용에서 요약 동기화하고, 섹션을 삭제하면 해당 필드는 비운다. 실제 전체 원문은 blocks에 유지한다. 자유 문서는 기존 Mock 문구 자동 갱신 대상에서 제외한다.

실제 백엔드 연결 없이 기존 Mock 상태와 localStorage만 사용한다. 향후 REST API에는 문서 섹션 배열의 저장·검증과 서버 측 HTML 및 권한 검증이 필요하다. 현재 구현하지 않는다.

## 주요 변경 파일

- app/portfolios/new/page.tsx, app/portfolios/[portfolioId]/edit/page.tsx
- app/portfolios/_components/PortfolioComposer.tsx 및 CSS Module
- app/portfolios/_components/PortfolioBlockEditor.tsx 및 CSS Module
- app/portfolios/_components/PortfolioAssetsEditor.tsx 및 CSS Module: 기존 편집기에서 경로 이동
- components/portfolio/PortfolioContent.tsx 및 CSS Module: 미리보기·상세 공통 본문
- components/portfolio/PortfolioGallery.tsx 및 CSS Module: 기존 상세 근처에서 공통 위치로 이동
- components/profile/ProfileTagEditor.tsx 및 CSS Module: 프로필/작성기 공통 태그 입력
- app/users/[userId]/_components/PortfolioTab.tsx, ProfileEditor.tsx, PortfolioTab.module.css
- app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx
- lib/profiles/portfolioBlocks.ts, portfolioDraft.ts, portfolioMockMigration.ts, profileService.ts
- mocks/portfolioEditor.ts, mocks/routes.ts, types/profile.ts, package.json, package-lock.json
- lib/profiles/portfolioBlocks.test.ts, profileService.test.ts, tests/routes.test.ts
- scripts/verify-portfolio-composer.mjs, verify-portfolio-composer-keyboard.mjs, helpers/portfolio-editor.mjs 및 기존 포트폴리오 검증 스크립트
- README.md, docs/frontend-pages.md, docs/routing.md

위 frontend 경로는 모두 frontend/ 아래다.

## 실제 검증

- npm run dev: 0.0.0.0:5173에서 실행, 신규 작성 페이지 HTTP 200 확인
- npm run typecheck: 성공
- npm test: 34개 파일·404개 테스트 통과
- npm run build: 성공, TypeScript 검사 포함
- 신규 App Router 경로 2개 포함 총 31개 경로 파일 검증
- Chromium: 전용 등록/수정, 기존 내용 불러오기, 제목 변경, Markdown/HTML 미리보기, 섹션 추가/삭제 및 실제 마우스 드래그 통과
- 초안 새로고침 복원, 위아래 버튼, 수정 저장 후 상세의 제목/순서/서식 반영, 위험 HTML 제거 확인
- 필수값 검증 및 신규 등록 → 프로필 목록 → 무료 본인 상세 확인
- 1920/1440/1280/1024/768/390px 작성 화면 가로 넘침 없음, pageerror 0건
- 별도 Chromium: 키보드 정렬과 명시적 취소 시 초안 삭제·원본 유지 확인
- 기존 이미지/링크 추가·편집·삭제·취소·저장/복원, 갤러리 확대, 타인 AI 숨김, 비로그인 제한, 인쇄/PDF 검증을 새 편집 페이지 기준으로 재실행하여 통과
- PC 편집 화면 캡처 직접 확인. 실제 터치 기기에서 손가락 드래그는 미검증. 터치 포인터를 받는 정렬 핸들과 위/아래 대체 버튼을 제공
- 전체 서비스의 프로젝트 승인·SNS 등 업무 흐름은 이번에 재실행하지 않음

[검증 결과](artifacts/portfolio-composer/report.json) · [편집 화면](artifacts/portfolio-composer/editor-top.png) · [섹션 편집](artifacts/portfolio-composer/editor-blocks.png) · [모바일](artifacts/portfolio-composer/390.png)

이후 모든 내용 섹션에 색상 블록 디자인을 적용하고 블록 안에서 직접 이미지·링크를 첨부하도록 확장했다. [블록별 첨부 및 검증 결과](portfolio-block-media.md) 참고.

최신 수정에서는 공통 이미지·링크 관리 패널을 제거하고 종류별 블록에서 관리한다. 분야 복수 선택과 단일 선택 목록 UI는 [선택 UI 개편](portfolio-selection-update.md)을 참고한다.

최종 정리에서는 대표 이미지를 폼 맨 위로 옮기고 텍스트 블록의 첨부 버튼을 제거했다. 현재 동작과 검증 범위는 [최종 점검](portfolio-final-review.md)을 기준으로 한다.

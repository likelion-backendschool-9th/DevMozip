# 미디어 종류 분리 및 자연스러운 선택 목록

## 변경

- 프로젝트 이미지 블록에서는 이미지만 편집·표시하고, 링크 블록에서는 링크만 편집·표시한다. 텍스트 블록의 이미지/링크 첨부는 기존 기능으로 유지한다.
- 기존 데이터에서 이미지 블록에 링크가 섞였거나 그 반대인 경우, 자료를 삭제하지 않고 별도 종류의 블록으로 나눈다. 동일 종류의 블록도 여러 개 추가할 수 있다.
- 상단 공통 이미지·링크 관리와 해당 목차를 제거했다. 대표 이미지 선택은 기본 정보에 유지하고 자료는 블록 내부에서 관리한다.
- 이전 공통 링크가 있는 기존 포트폴리오는 링크 블록을 제공한다. 편집 후 첫 링크 블록의 내용은 상단 바로가기에도 반영해 오래된 링크가 남지 않게 한다.
- 분야는 기본 목록 + 직접 입력 + 복수 선택을 지원한다. 기술 스택도 같은 선택 UI를 사용한다.
- 선택한 항목을 하단에 반복 출력하지 않는다. 같은 목록에서 체크와 색상으로 선택 여부를 표시하며 다시 누르면 해제한다. 직접 입력한 값은 그 목록에 선택 상태로 추가되고 Enter 또는 +로 확정한다.
- 이미 있는 항목을 직접 입력하면 새 항목을 만들지 않고 해당 항목을 선택한다. 대소문자 무시 중복 및 30개 제한을 유지한다. 현재 선택 개수도 표시한다.
- 공통 ProfileTagEditor를 사용하는 프로필 기술/관심 입력도 동일한 중복 없는 목록으로 개선했다.

Portfolio.fields는 선택 필드이며 기존 field 문자열은 단일 분야로 읽는다. 저장 시 첫 분야를 field에 동기화하고, 복수 값은 fields에 유지한다. 기존 자료 및 직접 작성한 내용은 유지한다.

## 주요 변경 파일

- frontend/components/profile/ProfileTagEditor.tsx 및 CSS Module
- frontend/app/portfolios/_components/PortfolioComposer.tsx, PortfolioBlockEditor.tsx
- frontend/components/portfolio/PortfolioContent.tsx
- frontend/app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx
- frontend/app/users/[userId]/_components/PortfolioTab.tsx
- frontend/types/profile.ts, mocks/portfolioEditor.ts
- frontend/lib/profiles/portfolioBlocks.ts, portfolioMedia.ts
- frontend/lib/profiles/portfolioSelection.test.ts
- scripts/verify-portfolio-selection.mjs, helpers/portfolio-editor.mjs 및 관련 기존 검증 스크립트

## 실제 검증

- npm run typecheck: 통과
- npm test: 36개 파일·410개 테스트 통과
- npm run build: TypeScript 포함 성공
- Chromium: 공통 관리 제거, 종류별 편집/표시 분리, 복수 분야·직접 입력·선택/해제·중복 표시 없음 확인
- 미리보기·저장·새로고침 복원 및 상단 링크 동기화 확인
- 1440/1024/768/390px 작성 화면 가로 넘침 없음, pageerror 0건
- 기존 갤러리 확대·이미지/링크 편집·취소/삭제/저장·권한·6개 해상도 상세·인쇄/PDF 시나리오 재실행 통과
- 기술 선택 UI 캡처 직접 확인
- 전체 서비스 업무 흐름 및 실제 모바일 터치 드래그는 이번에 별도 검증하지 않음

[검증 결과](artifacts/portfolio-selection/report.json) · [기술 선택 UI](artifacts/portfolio-selection/stack-picker.png)

백엔드 및 실제 API 변경 없음. 커밋·푸시는 수행하지 않았다.

# 색상 블록 및 블록 안 이미지·링크

## 변경 내용

- 상세·전체 미리보기의 모든 내용 섹션을 같은 연보라색 배경과 왼쪽 포인트 선으로 통일했다. 이미지/링크 섹션에도 같은 스타일을 적용한다. 편집 블록에도 은은한 배경을 적용했다.
- 각 편집 블록 아래 이미지/링크 버튼을 제공한다. 이미지는 블록 내부 패널에서 + → 파일 선택 → 캡션 입력/삭제, 링크는 + → 이름/URL 입력/삭제로 관리한다.
- 첨부는 해당 블록에 귀속된다. 블록 제목/본문/순서와 함께 초안·문서 저장 및 상세/미리보기에 반영하며 다른 블록에는 표시하지 않는다.
- 이전 갤러리/링크 블록은 공통 자료를 상속하되 블록에서 개별 편집하면 개별 첨부가 우선한다. 명시적인 빈 배열(삭제)은 유지한다. 상단의 공통 이미지/링크 관리도 유지한다.
- 각 블록은 이미지 최대 6장(PNG/JPEG/WebP 장당 256KB), 링크 최대 6개를 지원한다. 저장/복원 시 이미지와 URL을 검증하고, 아직 이름/URL을 입력하지 않은 초안은 보존하되 등록/저장 시 검증한다.
- 이미지 확인 중 블록 삭제·첨부 전환·문서 저장을 막는다. 비동기 처리 시 다른 블록/본문의 변경이 덮어써지지 않도록 갱신한다.
- 기존 프리미엄·본인 권한, 정렬, Markdown/HTML 기능은 유지한다. 백엔드와 실제 API 변경은 없다.

## 변경 파일

- types/profile.ts: 블록별 선택적 images/links
- lib/profiles/portfolioBlocks.ts, portfolioDraft.ts: 검증·조회·미완성 초안 복원
- app/portfolios/_components/PortfolioBlockEditor.tsx 및 CSS Module: 블록별 첨부 버튼·패널
- app/portfolios/_components/PortfolioAssetsEditor.tsx: 이미지/링크 선택 표시 지원
- app/portfolios/_components/PortfolioComposer.tsx: 업로드 상태와 블록별 변경 연결
- app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx: 블록 갤러리 기준 목차 표시
- components/portfolio/PortfolioContent.tsx 및 CSS Module: 색상 블록과 위치별 미디어 표시
- lib/profiles/portfolioBlockMedia.test.ts, scripts/verify-portfolio-block-media.mjs: 검증

위 frontend 경로는 모두 frontend/ 아래에 위치한다.

## 실제 검증

- npm test: 35개 파일·407개 테스트 통과
- npm run build: TypeScript 검사 포함 성공
- Chromium: 블록 내부 버튼으로 실제 파일 선택, 캡션·링크 입력, 첨부와 블록 함께 정렬 확인
- 미리보기에서 모든 섹션의 배경색 일치 확인
- 초안 복원, 수정 저장·새로고침 후 해당 블록의 이미지/링크 표시, 블록 내부 삭제 확인
- 1440/1024/768/390px 가로 넘침 없음. pageerror 0건
- 기존 전체 서비스 흐름과 실제 터치 드래그는 이번에 별도 재검증하지 않음

[검증 결과](artifacts/portfolio-block-media/report.json) · [PC](artifacts/portfolio-block-media/1440.png) · [모바일](artifacts/portfolio-block-media/390.png)

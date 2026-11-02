# 포트폴리오 최종 정리

## 변경 내용

- 대표 이미지를 기본 정보 입력 폼의 맨 위에 배치했다.
- 일반 내용 블록에서 이미지·링크 첨부 버튼과 링크 삽입 도구 버튼을 제거했다. Markdown·HTML 본문 편집은 유지한다.
- 이미지는 이미지 갤러리, 링크는 링크 모음 블록에서만 첨부한다.
- 기존 텍스트 블록에 저장된 첨부 항목은 바로 다음의 전용 블록으로 분리해 보존한다.
- Markdown·HTML 입력과 코드 표시의 한글 글꼴 대체를 보완했다. 실제 캡처에서 한글 표시를 확인했다.
- 섹션 삭제 확인 문구를 실제 저장 동작에 맞게 수정했다.

## 주요 변경 파일

- frontend/app/portfolios/_components/PortfolioComposer.tsx
- frontend/app/portfolios/_components/PortfolioBlockEditor.tsx 및 CSS Module
- frontend/components/portfolio/PortfolioContent.tsx 및 CSS Module
- frontend/lib/profiles/portfolioBlocks.ts
- frontend/lib/profiles/portfolioBlockMedia.test.ts
- scripts/verify-responsive.mjs: 등록·수정·상세 화면을 반응형 점검 목록에 추가
- docs/portfolio-composer.md 및 이 문서와 artifacts/portfolio-final

## 실제 검증 결과

- npm test --prefix frontend: 36개 파일, 410개 테스트 통과.
- npm run build --prefix frontend: Next.js 빌드 및 TypeScript 검사 성공.
- Chromium: 1920, 1440, 1280, 1024, 768, 390px에서 총 234개 화면 점검 통과. 가로 넘침, 헤더 겹침 및 화면별 기본 레이아웃 조건 검사.
- 메인, 프로필 5개 탭·Aside, 계정 설정, 프로젝트, AI 추천, SNS, 채팅, 관리자, 온보딩 및 포트폴리오 화면을 점검 목록에 포함.
- 브라우저 콘솔 오류 0건, 페이지 실행 오류 0건, 업무 API 요청 0건. 예상된 404 화면의 콘솔 출력은 검사에서 제외한다.
- 별도 편집 흐름: 대표 이미지 위치, 5개 텍스트 블록 첨부 버튼 제거, 전용 이미지·링크 블록 유지, 미리보기, 수정 저장 및 새로고침 복원 통과.
- 해당 편집 화면에서 undefined, NaN, [object Object] 노출 없음.
- PC·모바일 편집 캡처와 Markdown 한글 입력 표시를 직접 확인했다.

[반응형 결과](artifacts/portfolio-final/responsive-report.json) · [편집 흐름 결과](artifacts/portfolio-final/flow-report.json) · [대표 이미지](artifacts/portfolio-final/cover-top.png) · [텍스트 블록](artifacts/portfolio-final/text-block.png) · [PC](artifacts/portfolio-final/1440-portfolio-edit.png) · [모바일](artifacts/portfolio-final/390-portfolio-edit.png)

반응형 검사는 페이지 배치 검사이며 프로젝트 승인·리뷰 등 전체 업무 시나리오의 재실행을 의미하지 않는다. 실제 모바일 기기, 모든 사용자 작성 HTML·문구 조합은 검증하지 않았다. 백엔드와 실제 API는 변경하지 않았다.

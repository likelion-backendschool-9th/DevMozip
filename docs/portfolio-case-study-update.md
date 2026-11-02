# 프로젝트 사례 중심의 포트폴리오 상세

## 구현 내용

- 대표 이미지와 제목/요약에 이어 프로젝트 설명 → 주요 구현 기능 → 문제 해결 경험 → 개발 과정/결과를 읽도록 구성했다.
- 본인 기여를 별도 강조하고 기간·역할·팀 구성·기술·관련 링크를 프로젝트 개요에 모았다. 좁은 화면에서는 개요가 본문 위로 이동한다.
- 프로젝트 이미지 최대 6장, 장당 PNG/JPEG/WebP 256KB 이하 업로드와 캡션(150자)을 지원한다. 이미지 확대 모달과 이전/다음 탐색을 제공한다.
- GitHub/서비스 배포/기술 문서 등 링크를 이름과 URL로 최대 6개 등록한다. 이름 40자, URL 500자이며 http/https만 허용하고 새 탭에 noopener/noreferrer를 적용한다.
- 이미지와 링크는 편집창의 +/× 버튼으로 추가/삭제한다. 업로드 처리 중 저장을 막고 이미지 디코딩을 확인한다. 저장 전 취소하면 기존 값을 유지한다.
- 팀 구성 100자, 나의 기여 3,000자 입력을 추가했다.
- 인쇄/PDF 버튼과 인쇄 스타일을 추가하여 헤더·관리 버튼·AI 피드백 없이 출력한다. 예시 A4 PDF를 생성했다.

## Mock 데이터 및 호환성

회고 서비스(DevLog), 디자인 시스템(Flow UI), API 설계(Pocket API)의 팀 구성·기여·이미지·링크 예시를 제공한다. 화면 및 다이어그램은 프로젝트 로컬 SVG로 만들었고, 가상의 Mock 화면임을 캡션과 이미지에 표시한다. GitHub/서비스/문서 링크는 예시 주소다. 실제 운영 서비스나 채용 성과를 주장하는 데이터가 아니다.

Portfolio의 images/links/teamSize/contribution은 선택 필드다. 기존 저장 데이터에서 필드가 없는 기존 Mock 포트폴리오는 예시를 보완하지만, 사용자가 빈 배열로 삭제한 값은 유지한다. 기존 저장소 버전/키를 유지하며 이미지 형식·개수와 링크를 저장·복원 시 검증한다.

기존 프리미엄 상세 접근 제한과 작성자 본인만 보는 AI 피드백을 유지한다. 이미지·기여·링크는 요약 목록에 상세 정보로 노출하지 않는다. 실제 업로드 API, DB, AI 호출, 백엔드 변경은 없다.

## 주요 파일

- frontend/types/profile.ts: 갤러리·관련 링크·팀/기여 타입
- frontend/mocks/portfolioShowcases.ts, mocks/profile.ts: Mock 사례 및 기존 예시 연동
- frontend/public/portfolio-demos/*.svg: 대시보드, 회고 에디터, 디자인 시스템, API 구조 Mock 이미지 4개
- frontend/lib/profiles/portfolioMedia.ts, profileService.ts: 미디어/링크 검증과 저장 데이터 보완
- frontend/app/users/[userId]/_components/PortfolioAssetsEditor.tsx 및 CSS Module, PortfolioTab.tsx: 편집 UI
- frontend/app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx 및 CSS Module, PortfolioGallery.tsx 및 CSS Module: 상세·갤러리·인쇄 UI
- frontend/lib/profiles/portfolioMedia.test.ts, scripts/verify-portfolio-case-study.mjs: 테스트

## 실제 검증 결과

- npm test: 32개 파일·393개 테스트 통과.
- npm run build: TypeScript 검사 포함 성공.
- Chromium: Mock 이미지·관련 링크·갤러리 확대/다음 이미지 확인.
- 이미지·캡션·링크·팀·기여 수정과 저장·새로고침 복원, 이미지 수 제한, 취소 및 삭제 확인.
- 링크의 href/target/rel 확인. 타인의 수정·AI 피드백 숨김과 비로그인 상세 제한 확인.
- 1920/1440/1280/1024/768/390px 상세 화면 가로 넘침 없음. pageerror 0건.
- 인쇄 미디어에서 헤더 숨김 확인 및 A4 PDF 생성. PDF 내부 페이지별 시각 검수는 별도로 수행하지 않았다.
- PC 캡처 직접 확인. 전체 서비스 회귀 흐름 및 Mock 외 신규 포트폴리오 생성/삭제의 브라우저 시나리오는 이번에 재실행하지 않았다.

결과: [검증 JSON](artifacts/portfolio-case-study/report.json), [PC 화면](artifacts/portfolio-case-study/1440.png), [모바일 화면](artifacts/portfolio-case-study/390.png), [Mock 예시 PDF](artifacts/portfolio-case-study/portfolio-demo.pdf).

이후 상세 디자인을 [개발자 포트폴리오 구성](portfolio-editorial-redesign.md)으로 추가 개편했다. 현재는 사이드바 대신 상단 요약과 목차, 넓은 기술 사례 본문을 사용한다. 위 결과 파일은 최신 디자인으로 갱신했다.

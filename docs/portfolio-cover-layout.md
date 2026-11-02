# 입력 너비 및 배너형 대표 이미지

프로젝트명만 남은 2열 컨테이너를 제거하여 입력칸이 한 줄 소개와 같은 전체 너비를 쓰도록 조정했다. 기간·역할 등 다른 2열 항목은 유지한다.

대표 이미지는 배너형 미리보기와 중앙 + 버튼으로 변경한다. 기본 파일 선택 입력은 숨기고, +로 파일 선택창을 열며 이미지가 있으면 × 삭제 버튼을 제공한다. 기존 PNG/JPEG/WebP 256KB 제한과 저장/복원을 유지하고 실제 디코딩 확인 중 저장을 막는다.

변경 파일: frontend/app/portfolios/_components/PortfolioComposer.tsx, PortfolioCoverEditor.tsx 및 PortfolioCoverEditor.module.css. 검증 스크립트 scripts/verify-portfolio-cover.mjs.

검증: npm test 36개 파일·410개 테스트 통과. npm run build(TypeScript 포함) 성공. Chromium에서 1440/1024/768/390px 입력 폭·가로 넘침 확인. 실제 + 파일 선택, 숨긴 입력, 미리보기·저장·새로고침 복원·삭제 확인. pageerror 0건. 모바일 배너 캡처 직접 확인.

결과 및 화면: docs/artifacts/portfolio-cover. 백엔드 변경 및 커밋·푸시 없음.

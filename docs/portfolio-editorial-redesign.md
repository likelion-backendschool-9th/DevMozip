# 개발자 포트폴리오 상세 디자인 개편

기존의 큰 외곽 카드와 정보 사이드바를 제거하고 프로젝트 사례를 중심으로 읽는 레이아웃으로 변경했다.

- 상단: 큰 프로젝트 제목·요약·작성자와 역할·외부 링크, 프로젝트 화면을 배치한 2열 히어로
- 요약: 기간·담당 역할·팀 구성·기술 스택을 가로로 정리
- 목차: 프로젝트 설명·구현 기능·문제 해결·결과·이미지에 바로 이동. PC에서는 고정, 모바일에서는 일반 흐름으로 표시
- 본문: 왼쪽에 번호와 섹션 제목, 오른쪽에 기술 설명과 본인 기여를 배치. 모바일에서는 세로로 전환
- 이미지: 기존 확대 갤러리 유지. 사진/링크/기여/팀 편집, 저장·취소·복원 유지
- 프리미엄 상세 제한, 작성자 전용 편집 및 AI 피드백을 유지
- 인쇄/PDF 스타일을 새 구성에 맞춰 조정하고 예시 PDF 갱신

실제 화면 확인 후 하단에 전역 footer 스타일이 영향을 주던 정렬과 모바일 팀 구성 줄바꿈도 수정했다. 실제 API·백엔드·DB 변경은 없다.

## 변경 파일

- frontend/app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx
- frontend/app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.module.css
- frontend/mocks/profile.ts: 섹션 설명용 메타데이터
- docs/artifacts/portfolio-case-study: 최종 캡처·검증 결과·예시 PDF

## 최종 검증

- npm test: 32개 파일, 393개 테스트 통과
- npm run build: TypeScript 검사 포함 성공
- 기존 포트폴리오 브라우저 시나리오 재실행: 갤러리 확대/다음 이미지, 이미지·캡션·링크·팀/기여 편집·저장·복원·취소·삭제, 개수 제한, 타인의 수정/AI 피드백 숨김, 비로그인 제한 확인
- 1920/1440/1280/1024/768/390px 가로 넘침 없음. pageerror 0건
- 별도 Chromium 확인: PC/모바일 목차 5개 표시 및 문제 해결 섹션으로 앵커 이동
- PC·모바일 캡처 직접 확인. 인쇄 스타일 및 A4 PDF 생성 확인. PDF 페이지별 시각 검수는 수행하지 않음
- 전체 서비스 업무 흐름은 이번 범위에서 재실행하지 않음

[PC 미리보기](artifacts/portfolio-case-study/1440.png) · [모바일 미리보기](artifacts/portfolio-case-study/390.png) · [Mock PDF](artifacts/portfolio-case-study/portfolio-demo.pdf) · [브라우저 결과](artifacts/portfolio-case-study/report.json)

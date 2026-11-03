# 본인 무료 상세 열람 및 Mock 서술 개선

## 동작

- 본인 포트폴리오는 무료 회원도 프로필 목록 카드와 직접 주소로 상세 열람할 수 있다.
- 무료 타인/비로그인은 기존 프리미엄 안내를 유지한다. 프리미엄·관리자는 공개 가능한 타인 상세를 열람할 수 있다.
- AI 피드백의 프리미엄 + 작성자 본인 조건은 변경하지 않는다.
- 프로젝트 설명과 나의 기여는 각각 독립된 번호·제목·목차·앵커를 가진 섹션이다. 현재 디자인을 유지하며 기여 섹션은 배경색으로 강조한다.

## Mock 내용

DevLog, Flow UI, Pocket API 사례를 서비스 대상과 배경 → 개인 담당 범위/팀 역할 분담 → 기술 구현 → 문제/선택/검증 → 결과와 후속 과제로 정리했다. 프로젝트 설명은 서비스 전체를 다루고 기여는 개인의 책임과 수행을 설명한다. 근거 없는 운영 수치나 배포 실적을 추가하지 않았으며 Mock 사례임을 구분한다.

기존 저장 데이터에서 예전 Mock 문구와 정확히 일치하는 필드만 새 예시로 바꾼다. 사용자가 수정한 내용과 빈 이미지/링크 배열은 유지하며, 데이터 초기화 없이 새 예시를 볼 수 있다. 이 보완은 기존 예시 ID에만 적용한다.

## 주요 파일

- lib/profiles/profileService.ts: 목록·상세의 본인 무료 권한
- app/users/[userId]/_components/PortfolioTab.tsx: 본인 목록 상세 링크 및 설명
- app/portfolios/[portfolioId]/_components/PortfolioDetailScreen.tsx 및 CSS Module: 기여 독립 섹션
- mocks/profile.ts, portfolioShowcases.ts, portfolioLegacyCopy.ts: 섹션 구성 및 새/이전 예시 문구
- lib/profiles/portfolioMockMigration.ts: 작성 내용 보존하는 예시 갱신
- lib/profiles/portfolioDetail.test.ts, portfolioMockMigration.test.ts, tests/screens/ProfilePage.test.tsx: 권한·요약 비노출·갱신 검증
- scripts/verify-portfolio-owner-access.mjs 및 기존 포트폴리오/지원자 검증 스크립트: 변경된 예시 문구 반영

## 최종 검증

- npm test: 33개 파일, 397개 테스트 통과
- npm run build: TypeScript 검사 포함 성공
- Chromium: 무료 본인의 목록→상세·직접 접근·새로고침, 타인 무료 제한·프리미엄 허용·비로그인 제한 확인
- 설명/기여 각각 표시, 6개 목차와 기여 앵커 이동 확인
- 1920/1440/1280/1024/768/390px 상세 가로 넘침 없음. pageerror 0건
- 기존 갤러리/링크 편집·저장/복원·취소/삭제·인쇄/PDF 시나리오도 재실행
- 전체 프로젝트 지원/승인 시나리오는 이번에 재실행하지 않음. 해당 스크립트의 예시 문구 기대만 갱신함

[무료 본인 PC 화면](artifacts/portfolio-owner-access/1440.png) · [모바일 화면](artifacts/portfolio-owner-access/390.png) · [검증 결과](artifacts/portfolio-owner-access/report.json)

백엔드 및 실제 API 변경 없음. 커밋·푸시 없이 작업 디렉터리에 적용한다.

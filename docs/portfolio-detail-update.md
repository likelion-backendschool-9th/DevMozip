# 포트폴리오 요약 및 프리미엄 상세 페이지

프로필 포트폴리오 탭은 제목·대표 이미지·한 줄 설명·기술 스택·분야만 표시한다. 기간, 역할, 상세 본문과 AI 피드백은 목록에서 제외한다. 작성자는 기존 등록·수정·삭제 버튼을 유지한다.

## 상세 및 권한

- 신규 경로 `/portfolios/[portfolioId]`는 별도 상세 페이지다. 프리미엄 및 관리자 데모 회원에게 상세 설명, 기능, 문제 해결, 결과물, 기간과 역할을 제공한다.
- 무료 회원과 비로그인 사용자는 목록 카드 클릭 시 안내를 표시하고, 직접 상세 주소로 접근해도 프리미엄 안내를 표시한다. 본인은 무료 상태에서도 상세 페이지를 열람하고 내용을 편집할 수 있다.
- 비공개 계정·삭제된 항목·없는 ID는 상세 내용을 표시하지 않는다.
- AI 포트폴리오 피드백은 프리미엄 권한이 있는 작성자 본인만 상세 화면에서 실행·조회한다. 타인 페이지에는 버튼과 결과가 없다. 결과는 로컬 UI 상태에만 두고 포트폴리오나 공개 저장 데이터에 넣지 않는다. 계정 전환 시에도 타인에게 표시하지 않는다.
- 관리자도 타인의 AI 피드백은 보지 못한다. 관리자 상세 열람은 기존 데모 권한과 동일하게 유지한다.
- 이 권한 검사는 Mock UI 정책이다. 실제 서비스에서는 상세 데이터와 AI 결과의 서버 측 접근 제어가 필요하다.

## 데이터 및 편집

Portfolio에 선택적 coverImage, field를 추가했다. 기존 localStorage와 호환하며 대표 이미지가 없으면 공통 SVG 이미지를 사용하고 분야가 없으면 미등록으로 표시한다. 원본 Mock 데이터에 분야를 추가했다.

등록/수정창에서 대표 이미지(PNG/JPEG/WebP 256KB 이하)와 분야(40자 이내)를 편집한다. 기존 제목, 설명, 기술, 기간, 역할, 상세 내용 편집을 유지한다. 실제 파일 업로드와 AI API는 사용하지 않는다.

## 주요 파일

- `frontend/app/users/[userId]/_components/PortfolioTab.tsx` 및 CSS Module: 요약 카드, 기존 편집창 확장, 프리미엄 안내
- `frontend/app/portfolios/[portfolioId]/page.tsx` 및 `_components/PortfolioDetailScreen.tsx`, CSS Module: 상세 페이지 및 권한·본인 피드백
- `frontend/public/portfolio-cover.svg`: 기본 대표 이미지
- `frontend/types/profile.ts`, `frontend/mocks/profile.ts`: 추가 필드, Mock 분야, 공통 상세 항목
- `frontend/lib/profiles/profileService.ts`: 대표 이미지/분야 검증, 상세 및 피드백 권한
- `frontend/mocks/routes.ts`, `frontend/tests/routes.test.ts`, `docs/frontend-pages.md`, `docs/routing.md`: 29개 경로 반영
- `frontend/tests/screens/ProfilePage.test.tsx`, `frontend/lib/profiles/portfolioDetail.test.ts`, `scripts/verify-portfolio-detail.mjs`: 회귀 및 새 정책 검증

## 검증

- npm run build: TypeScript 검사 포함 성공.
- npm test: 31개 파일·389개 테스트 통과. 기존 목록 상세 렌더링 기대를 변경된 정책으로 조정했으며, 신규 경로 개수 기대도 29개로 수정했다.
- Chromium: 요약만 표시, 무료 클릭·직접 주소 제한, 프리미엄 상세 이동, 본인 전용 AI 피드백과 계정 전환 시 숨김, 대표 이미지/분야 수정·저장·새로고침 복원 확인.
- 1440/1024/768/390px 목록 및 상세 페이지 가로 넘침 없음 확인. 없는 항목·비로그인 안내 확인. pageerror 0건.
- 결과 및 캡처: `docs/artifacts/portfolio-detail`. 목록 PC 화면은 직접 확인 후 카드 크기를 조정했다.
- 사용자 비공개 전환, 전체 포트폴리오 신규 등록/삭제와 다른 서비스 업무 흐름은 이번 브라우저 검증에서 재실행하지 않았다. 포트폴리오 CRUD의 기존 단위 테스트는 통과했다.

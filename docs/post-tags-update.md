# 게시글 기술 스택 및 관심 분야

## 변경 내용

- 게시글 등록·수정에서 공통 ProfileTagEditor를 재사용한다. 기존 기술 스택 목록 선택 및 직접 입력(+ 또는 Enter), 선택 해제를 지원한다.
- 관심 분야 복수 선택·직접 입력을 추가했다. 선택 항목은 카드와 상세에 표시하며 피드 검색에도 포함한다.
- 기술 스택·분야는 각각 선택 사항이며 최대 30개, 항목당 1~30자, 대소문자·앞뒤 공백 기준 중복을 제한한다.
- GitHub·외부 URL 입력란을 제거했다. 기존 저장 링크는 편집 시 보존하고 기존 게시글 표시도 유지한다.
- 기존 localStorage v1을 유지한다. 관심 분야가 없는 예전 데이터는 빈 선택으로 읽고 신규 항목은 공통 프로필 상태에 저장한다.

## 변경 파일

- frontend/components/posts/PostForm.tsx 및 CSS Module
- frontend/components/posts/PostCard.tsx 및 CSS Module
- frontend/types/social.ts, frontend/types/profile.ts
- frontend/mocks/social.ts
- frontend/lib/social/socialService.ts 및 테스트
- frontend/lib/profiles/profileService.ts
- frontend/tests/screens/FeedPage.test.tsx
- scripts/verify-post-tags.mjs

## 실제 검증

- npm test --prefix frontend: 36개 파일, 418개 테스트 통과.
- npm run build --prefix frontend: Next.js 빌드·TypeScript 검사 성공.
- Chromium: 기술 직접 추가, 관심 분야 직접 입력 및 기본 선택·해제, URL 입력란 없음 확인.
- 등록 → 상세 → 새로고침 → 수정의 태그·분야 복원과 분야 제거 저장 확인. 테스트 게시글 삭제까지 확인.
- 1440/768/390px 문서 가로 넘침 없음, 캡처 확인, 페이지 실행 오류 0건.
- 실제 모바일 기기 및 관련 없는 전체 업무 흐름은 재검증하지 않았다. 백엔드 수정·커밋·푸시는 하지 않았다.

[결과](artifacts/post-tags/report.json) · [PC](artifacts/post-tags/1440.png) · [모바일](artifacts/post-tags/390.png)

# 포스팅 복귀 경로 및 카테고리 스크롤 정리

## 변경

- 게시글 작성·수정 상단의 `← 전체 피드`를 `← 내 포스팅`으로 바꾸고 `/users/{본인 ID}?tab=posts`로 연결했다.
- 새 게시글 작성 취소도 본인 포스팅으로 연결했다. 기존 게시글 수정 취소는 원본 상세로 돌아가는 동작을 유지한다.
- 공통 탭과 프로필 탭에서 선택 밑줄이 아래로 1px 넘치는 문제를 수정했다.
- 카테고리·탭에 공통으로 적용되던 자동 스크롤을 제거하고 높이는 내용에 맞추며, 항목이 많으면 줄바꿈한다. 피드 슬라이드·메시지 등 본래 스크롤이 필요한 영역은 유지한다.

## 변경 파일

- frontend/components/posts/editor/PostEditorScreen.tsx
- frontend/components/posts/PostForm.tsx
- frontend/app/globals.css
- frontend/app/users/[userId]/_components/ProfileScreen.module.css
- frontend/tests/screens/FeedPage.test.tsx
- scripts/verify-category-layout.mjs

## 검증

- npm test --prefix frontend: 36개 파일, 412개 테스트 통과.
- npm run build --prefix frontend: Next.js 빌드·TypeScript 검사 성공.
- Chromium: 1920/1440/1280/1024/768/390px × 주요 11개 페이지 = 66건 점검. 수정 전 42건에서 확인된 탭 스크롤 문제가 수정 후 0건으로 감소했다. 페이지 가로 넘침과 실행 오류도 0건이다.
- 대상: 메인, 피드, 프로필 포스팅, 북마크, 검색, 프로젝트 목록, 내 프로젝트, AI 추천, 알림, 게시글 작성, 팔로잉 페이지. 카테고리·탭·칩·피드 필터 DOM의 실제 scrollHeight/clientHeight 및 scrollWidth/clientWidth를 비교했다.
- 상단 복귀 링크와 신규 작성 취소 버튼이 본인 프로필 포스팅 탭으로 이동하는 것을 실제 클릭으로 확인했다.
- 브라우저가 제공하는 select 팝업의 내부 UI나 실제 모바일 기기는 별도로 검증하지 않았다.
- 커밋·푸시 및 백엔드 변경은 하지 않았다.

[수정 전](artifacts/category-layout/before.json) · [수정 후](artifacts/category-layout/after.json) · [PC](artifacts/category-layout/profile-1440.png) · [모바일](artifacts/category-layout/profile-390.png)

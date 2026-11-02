# 프로필 포스팅 UI 정리

- 프로필 `내 글` 탭 이름을 `포스팅`으로 바꿨다. 기존 `?tab=posts` 경로와 5개 탭은 유지한다.
- 작성 글·퍼간 글의 관리 버튼은 카드 오른쪽 위 눈/감은 눈, 연필, 휴지통 아이콘으로 표시한다. 접근성 이름과 툴팁을 제공한다.
- 숨긴 글 보기 체크박스를 제거했다. 본인에게는 숨긴 포스팅도 계속 표시하며, 숨김 표시와 아이콘으로 상태를 확인하고 해제할 수 있다. 타인에게는 숨긴 글을 제외한다.
- 새 게시글 작성은 제목 영역 오른쪽 버튼으로 제공한다.
- 게시글 등록·수정 이미지 영역 위의 + 버튼으로 파일을 선택한다. 기본 파일 입력은 숨기고 빈 이미지 블록·미리보기·개별 삭제를 제공한다. 기존 형식·용량·장수 제한은 유지한다.
- 모바일은 관리 버튼과 작성자 정보를 별도 줄에 배치한다. 작은 원본 이미지도 일정한 비율의 미리보기로 표시한다.

## 변경 파일

- frontend/app/users/[userId]/_components/PostsTab.tsx 및 CSS Module
- frontend/components/posts/PostOwnerActions.tsx 및 CSS Module (신규 공통 UI)
- frontend/components/posts/PostCard.tsx 및 CSS Module
- frontend/components/posts/PostForm.tsx 및 CSS Module
- frontend/components/posts/PostActions.tsx, frontend/app/feed/_components/FeedScreen.tsx
- frontend/mocks/profile.ts, frontend/mocks/routes.ts
- frontend/tests/screens/FeedPage.test.tsx
- scripts/verify-profile-posting.mjs

## 검증

- npm test --prefix frontend: 36개 파일, 411개 테스트 통과. 숨긴 글의 본인 표시 및 타인 비노출 회귀 테스트 포함.
- npm run build --prefix frontend: Next.js 빌드 및 TypeScript 검사 통과.
- Chromium: 포스팅 탭 이름, 숨김 즉시 반영 및 새로고침 복원, 타인 비노출, 우측 상단 아이콘, 작성 버튼 이동, + 버튼 이미지 첨부·삭제 확인.
- 1440/768/390px 가로 넘침 없음. 모바일 캡처 직접 확인. 브라우저 페이지 실행 오류 0건.
- 백엔드 수정·실제 API 연결·커밋·푸시는 수행하지 않았다.

[브라우저 결과](artifacts/profile-posting/report.json) · [PC](artifacts/profile-posting/1440.png) · [모바일](artifacts/profile-posting/390.png) · [이미지 영역](artifacts/profile-posting/image-add.png)

전체 프로젝트 승인·리뷰 등 관련 없는 업무 시나리오와 실제 터치 기기는 이번에 재검증하지 않았다.

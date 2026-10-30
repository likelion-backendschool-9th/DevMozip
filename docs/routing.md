# 라우팅과 공통 레이아웃

5차는 새 경로를 추가하지 않았습니다. **명시 경로 28개 + `*` 404**를 유지합니다. 전체 경로·화면·권한 표는 [페이지 목록](frontend-pages.md)이 단일 기준입니다. 코드 기준은 `frontend/src/app/router/AppRouter.tsx`와 `frontend/src/mocks/routes.ts`입니다.

## 진입점

- 모든 경로는 AppLayout의 헤더·공통 안내·Outlet·푸터를 공유합니다. 헤더 검색 필드는 제거했고 **통합 검색 아이콘 → `/search?target=developer`**로 이동합니다. 검색 페이지에서 개발자/프로젝트를 전환하고 검색어/필터를 유지합니다.
- 본인 `/users/:userId`, `/profile/me/likes`, `/profile/me/bookmarks`, `/settings/account`는 AccountLayout을 공유합니다. 다른 사용자의 공개 프로필에는 개인 Aside가 없습니다. 800px 이하에서는 **개인 계정 메뉴** 버튼으로 펼칩니다.
- `/matching`은 기존 경로의 대화형 추천입니다. 메인에서 시작할 때 Router location state로 설명을 첫 메시지에 전달하고, 처리 후 state를 지워 새로고침/재렌더에 같은 대화를 중복 생성하지 않습니다. 기록은 localStorage에서 복원합니다. 800px 이하에서는 기록 메뉴를 펼칩니다.
- 프로젝트 관리는 `/my-projects/:projectId/manage`를 사용하며 이전 `/projects/:projectId/manage`도 유지합니다. 경로 이동은 로그인/주최자/참여/구독 등 기존 Mock 권한 검사에 따릅니다.
- 리뷰 링크는 `/projects/:projectId/reviews/write?request=...&token=...`입니다. 프로필 리뷰 탭은 조회 전용이며 직접 작성 경로를 제공하지 않습니다.
- `/projects/new`, `/posts/new`와 편집 경로가 동적 ID 경로와 충돌하지 않도록 React Router 경로를 유지합니다. 관리/추천 등은 기존 Suspense와 지연 로딩을 사용합니다.

## URL과 접근

`/search` 필터, 프로필 `?tab=...`, 관리자 `?tab=...`, 메시지 `?userId=...|conversation=...`, 프로젝트 `?tab=settings&action=extend|end|edit`를 유지합니다. 새로고침/주소 직접 접근은 Vite dev/preview에서 확인했습니다. 향후 정적 호스팅은 SPA fallback을 설정해야 합니다.

잘못된 경로/ID, 로그인 필요, 비공개 계정, 비주최자 관리, 강퇴/삭제된 채팅방, 만료된 리뷰 링크에는 기존 안내를 표시합니다. 브라우저 Mock 가드는 실제 서버 보안 검증이 아닙니다.

5차의 [실제 반응형 검사](artifacts/revision-5/responsive/report.json), [라우트/흐름 회귀 검사](artifacts/revision-5/regression/report.json), [최종 보고서](changes-responsive-integration.md)를 참고하세요.

# Next.js App Router 라우팅

현재 프론트엔드 루트는 `frontend/`, App Router 디렉터리는 `frontend/app/`입니다. 명시 페이지 28개와 `not-found.tsx`를 유지합니다. 전체 화면/권한은 [페이지 목록](frontend-pages.md)을 참고하세요.

## 파일 기반 경로

| URL | 파일 |
|---|---|
| `/` | `app/page.tsx` |
| `/login`, `/signup`, `/forgot-password`, `/onboarding` | 각 디렉터리 `page.tsx` |
| `/users/:userId` | `app/users/[userId]/page.tsx` |
| `/profile/me/likes`, `/profile/me/bookmarks` | 각 디렉터리 `page.tsx` |
| `/settings/account` | `app/settings/account/page.tsx` |
| `/projects`, `/projects/new` | `app/projects/page.tsx`, `app/projects/new/page.tsx` |
| `/projects/:projectId` | `app/projects/[projectId]/page.tsx` |
| `/projects/:projectId/manage` | `app/projects/[projectId]/manage/page.tsx` |
| `/projects/:projectId/reviews/write` | `app/projects/[projectId]/reviews/write/page.tsx` |
| `/my-projects`, `/my-projects/:projectId/manage` | 해당 경로의 `page.tsx` |
| `/matching`, `/feed`, `/following` | 해당 디렉터리 `page.tsx` |
| `/posts/new` | `app/posts/new/page.tsx` |
| `/posts/:postId`, `/posts/:postId/edit` | `app/posts/[postId]/page.tsx`, `app/posts/[postId]/edit/page.tsx` |
| `/messages`, `/notifications`, `/premium`, `/admin` | 해당 디렉터리 `page.tsx` |
| `/chats/:projectId` | `app/chats/[projectId]/page.tsx` |
| 기타 URL | `app/not-found.tsx`, HTTP 404 |

Next.js 자체 아이콘/정적 자산/RSC 엔드포인트는 위 업무 페이지 수에서 제외합니다. 별도 `src`나 경로 등록 파일은 없습니다. `mocks/routes.ts`는 메뉴/문서 제목용 메타데이터로, 라우팅을 실행하지 않습니다.

## 링크·쿼리·이동

`next/link`, `next/navigation`을 사용합니다. `lib/navigation.ts`는 Next Router에 URL과 쿼리를 전달하고 뒤로가기/앞으로가기를 호출하는 도우미이며 경로 목록을 등록하지 않습니다. 다음 URL 조건은 유지합니다.

- 검색 `target=developer|project&q=...` 및 필터
- 프로필 `tab=about|portfolio|posts|activity|reviews`
- 주최자 관리 `tab=applicants|team|settings`, 알림 동작 `action=extend|end|edit`
- 리뷰 `request=...&token=...`
- 메시지 `userId=...` 또는 `conversation=...`
- 팔로우 `user=...&tab=followers|following&q=...&available=yes`
- 관리자 `tab=회원` 등, 게시글 댓글 `#comments`

메인→추천은 새 대화를 Context에 만든 뒤 `/matching`으로 이동합니다. 기존 React Router location.state는 사용하지 않습니다. Next의 비동기 내비게이션을 고려해 전체 데모 초기화는 저장소 복원 후 메인으로 직접 이동합니다.

## 레이아웃·권한·직접 접근

`app/layout.tsx`가 공통 Provider/헤더/푸터를 유지합니다. 각 경로의 page는 Server Component이며 상호작용 화면을 Client Component로 연결합니다. localStorage 기반 Provider는 클라이언트 마운트 후 초기화됩니다. 사용자/프로젝트 ID 및 쿼리는 Next hooks로 읽습니다.

개인 Aside는 본인 프로필과 개인 페이지에만 표시합니다. 프리미엄 공고 작성, 주최자 관리, 참여자 채팅, 강퇴/삭제된 채팅방, 비공개 프로필, 리뷰 요청 만료 등 기존 Mock 권한을 유지합니다. 업무 데이터가 존재하지 않을 때의 클라이언트 안내와 실제 미등록 URL의 HTTP 404는 구분합니다. 실제 서버 인가는 후속 Spring Boot 범위입니다.

개발/프로덕션 서버가 직접 접근·새로고침과 동적 경로를 처리합니다. Vite의 정적 dist/SPA fallback 방식은 현재 실행 방식이 아닙니다. 정적 export는 이번 작업에서 설정하지 않았습니다.

[마이그레이션 검증](changes-nextjs-migration.md), [라우트/서버 응답 및 hydration 검사](artifacts/nextjs-migration/framework/report.json).

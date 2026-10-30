# SNS 전체 피드·게시글 구현 보고

## 구현

- 전체 피드는 세로 리스트, 메인 피드는 가로 슬라이드 유지. 공통 게시글 상태 사용.
- 추천/팔로잉, 인기/조회/최신/세션 Mock 추천순, 정보/피드백/홍보/자유 카테고리와 검색어 필터, URL 조건 유지, 빈 결과/비로그인 안내.
- 글 작성·수정·삭제, 제목·본문·카테고리·기술 태그·GitHub/외부 링크·이미지. 본인만 수정/삭제하며 HTML 삽입 없이 텍스트로 표현합니다.
- 이미지 최대 3장/장당 512KB의 PNG/JPEG/WebP/GIF를 FileReader로 읽어 브라우저에 저장. 파일 읽기 중 제출 방지, 개수/크기/형식 검사, 미리보기/제거, 저장 용량 실패 안내.
- 좋아요/취소, 계정별 북마크, 댓글 작성·본인 수정/삭제, 게시글/댓글 신고. 댓글 개수와 반응은 상세·프로필·메인에서 공통 계산합니다.
- 조회는 세션 토큰으로 중복 방지하며 비로그인 조회도 기록합니다. 기본 Mock 조회/좋아요 수와 실제 데모 증분을 구분해 계산합니다.
- 바로/의견 추가 퍼가기, 원본 작성자·원본 연결, 의견 수정/취소, 내 프로필 반영. 원본 변경을 참조하며 숨김/삭제는 안내 처리합니다.
- 외부 공유는 URL 복사만 제공하고 퍼가기를 생성하지 않습니다. 클립보드 사용 불가 시 URL 수동 복사 UI.
- 공통 ProfileProvider에 social 상태를 추가해 기존 저장 글/프로필/퍼가기를 이관합니다. 새 글은 메인/피드/상세/프로필에서 함께 사용합니다.
- 삭제는 댓글/반응/북마크/관련 신고를 정리하고 퍼가기 원본 참조는 안내용으로 남깁니다.

## 변경 파일

src 경로는 frontend 기준입니다.

신규:
- src/types/social.ts, src/mocks/social.ts
- src/services/socialService.ts, socialService.test.ts
- src/utils/externalUrl.ts
- src/features/post/ReportDialog.tsx, PostActions.tsx, PostCard.tsx, Comments.tsx, PostForm.tsx
- src/pages/FeedPage.tsx, FeedPage.test.tsx, PostEditorPage.tsx, PostDetailPage.tsx
- src/styles/social.css

수정:
- src/types/profile.ts, src/mocks/profile.ts, src/mocks/routes.ts
- src/services/profileService.ts, homeService.ts
- src/app/providers/ProfileProvider.tsx, src/app/router/AppRouter.tsx, src/main.tsx
- src/features/post/FeedPreview.tsx, src/features/user/PostsTab.tsx
- README.md, docs/frontend-pages.md, docs/decisions-pending.md, docs/demo-scenarios.md, docs/changes-social.md

## 검증

- npm run build 성공 (TypeScript + Vite).
- npm test: 14개 파일, 187개 통과. 기존 161개 + 수정 경로 매칭 1개 + SNS 서비스 16개 + 화면 정적 렌더링 9개.
- 서비스: 작성/수정/소유권/입력/태그/링크/이미지 제한, 좋아요/북마크 계정별 토글/저장, 조회 중복, 댓글 CRUD/권한/수량, 신고 대상/중복/본인 제한, 의견 퍼가기/원본 참조, 삭제 정리, 숨김/팔로잉/정렬/검색, 기존 저장값 이관.
- 화면: 세로 피드, 새 글·첨부의 피드/상세/프로필/메인 반영, 링크/댓글 입력, 반응/댓글 표시 일치, 퍼가기 의견/원본 작성자/취소, 원본 삭제 안내, 본인 편집/비로그인·빈 결과·숨김 제한, 퍼가기/외부 공유 분리.
- localhost:5173의 피드/상세/작성 및 PostForm 모듈 HTTP 200.
- 실제 브라우저의 파일 선택·클립보드·클릭·반응형 배치 검증은 도구가 없어 미수행. 첨부 데이터 검증/정적 렌더링/서비스 테스트는 실제 파일 선택 동작 검증과 구분합니다.
- 의존성·백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 수정한 실패

메인 미리보기의 공통 필터가 반환 타입을 기본 Post로 축소해 images/hidden 확장 필드에 TypeScript 오류가 발생했습니다. 입력 게시글 타입을 보존하는 제네릭 오버로드로 수정한 뒤 빌드 및 전체 테스트가 통과했습니다.

## 제한 및 범위

- 이미지 포함 localStorage 용량을 넘으면 메모리 세션에만 유지된다는 안내를 제공합니다. 실제 파일 서버·압축/클라우드 업로드는 구현하지 않았습니다.
- 이미지는 업로드된 파일을 표시하는 기능이며 임의 이미지 생성 API는 사용하지 않습니다.
- 실제 서버/DB/인증/OAuth/AI/결제/실시간 메시지/SNS 알림/관리자 신고 처리는 추가하지 않았습니다.
- 기존 프로젝트·주최자·프로필 기능과 LAN 공유 설정을 보존했습니다. 현재 SNS 단계만 진행했습니다.

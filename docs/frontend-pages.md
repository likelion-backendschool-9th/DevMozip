# 페이지 및 라우팅 목록

현재는 Next.js App Router로 31개 경로를 구현했습니다. 각 `frontend/app/**/page.tsx`가 기존 화면·기능을 연결하고 공통 레이아웃/Mock 상태를 공유합니다. 미등록 URL은 `app/not-found.tsx`와 HTTP 404로 처리합니다. 저장소 기반 UI는 마운트 후 복원하며 실제 Next.js 개발/프로덕션 서버가 직접 접근·새로고침을 지원합니다. [파일 경로 매핑](routing.md)을 참고하세요.

| 경로 | 화면 및 동작 | 권한 |
|---|---|---|
| `/` | 추천 설명 입력/시작 → 대화 페이지·모집 미리보기·SNS 가로 슬라이드 | 공개 |
| `/my-projects` | 전체/내 모집/참여/지원 중/종료 프로젝트·날짜 시뮬레이션 | 로그인 |
| `/my-projects/:projectId/manage` | 주최자 운영 또는 참여자 정보·채팅·리뷰 요청 | 주최자/팀원/대기 지원자 |
| `/projects/:projectId/reviews/write` | 계정별 임시 링크로 팀원 전체 평가 | 종료 프로젝트 참여자·유효 요청/토큰·기간·미사용 |
| `/onboarding` | 가입 후 4단계 기본/개발자/협업/미리보기 | 로그인 신규 계정, 기존/데모는 완료 안내 |
| `/profile/me/likes` | 내가 좋아요한 글·날짜·취소 | 로그인, 본인 전용 Aside |
| `/profile/me/bookmarks` | 게시글/프로젝트 북마크·해제 | 로그인, 본인 전용 Aside |
| `/settings/privacy` | 비밀번호 변경·카카오 연동/해제·비밀번호 없는 카카오 회원의 첫 비밀번호 설정·결제·공개/비공개 전환 | 본인 계정, 비공개여도 모든 본인 기능 유지. 공개/비공개 전환은 Mock 토글 OFF이면 서버 값(`/api/v1/users/me/account-lock`), ON이면 Mock 저장값을 쓴다 ([privacy-settings-api.md](privacy-settings-api.md)) |
| `/login` | 서버 로그인·카카오 로그인·계정 전환 | 공개 |
| `/signup` | 입력 검증·DB 중복 확인·서버 가입 후 자동 로그인·카카오 가입(`?kakao=signup`이면 비밀번호 입력 없음) | 공개, localhost/HTTPS 권장 |
| `/forgot-password` | 재설정 메일 요청·메일 링크로 새 비밀번호 저장 | 공개 |
| `/search` | 개발자/프로젝트 검색·필터·팔로우 | 공개, 고급 온도 조건/정렬은 프리미엄·관리자 |
| `/portfolios/new` | 전용 작성·섹션 자유 편집·초안·미리보기 | 로그인 |
| `/portfolios/:portfolioId/edit` | 전용 수정·HTML/Markdown·드래그 정렬 | 작성자 |
| `/portfolios/:portfolioId` | 대표 이미지·상세 내용·작성자 프로필·본인 전용 AI 피드백 | 본인 또는 프리미엄·관리자, 비공개 계정 접근 제한 적용 |
| `/users/:userId` | 공통 상단 + 소개/포트폴리오/내 글/활동/리뷰 5개 가로 탭 | 공개, 수정은 본인·상세 포트폴리오는 권한별 |
| `/projects` | 모집 검색·필터·정렬·인원/최소 경력 | 공개 |
| `/projects/:projectId` | 상세·지원/취소·북마크·신고·공유·주최자 연락 | 조회 공개, 지원은 로그인 |
| `/projects/:projectId/report` | 프로젝트 자체 신고·사유/선택 설명·접수 완료 | 로그인 |
| `/matching` | 대화형 Mock 추천·복수 조건/인원·기록·적합도·월 사용량 | 조건 수집 공개, 결과 생성 로그인, 상세 이유 프리미엄·관리자 |
| `/projects/new` | 모집 공고 작성 | 프리미엄만, 관리자 생성 우회 없음 |
| `/projects/:projectId/manage` | 수정/삭제·지원 승인/거절·팀원·상태/기간/종료 | 해당 주최자 |
| `/feed` | 세로 SNS·추천/팔로잉·정렬·카테고리·검색 | 공개 |
| `/posts/new` | 글·이미지·기술 태그·링크 작성 | 로그인 |
| `/posts/:postId/edit` | 글 전체 필드 수정 | 작성자 |
| `/posts/:postId` | 본문·댓글·좋아요·북마크·신고·퍼가기·공유 | 조회 공개, 반응/댓글은 로그인 |
| `/following` | 팔로워/팔로잉 검색·협업 필터·알림 설정 | 공개 목록, 변경은 로그인 |
| `/messages` | 1:1 목록·대화·읽음·사용자 신고. 실제 경로는 `/messages/chats`, `/messages/chats/:id`다. Mock 토글 OFF는 서버 API와 실시간 신호·입력 중 표시를 쓴다 | 로그인 |
| `/chats/:projectId` | 멤버·대화·강퇴 투표·종료 시 보관/삭제. 실제 경로는 `/messages/projects/:projectId`다. Mock 토글 OFF는 서버 프로젝트 채팅(멤버·읽음 n/m·초대·퇴장 문구·입력 중 표시)을 쓰고 강퇴 투표와 기한 시뮬레이터를 숨긴다 | 채팅 제외 대상이 아닌 주최자/팀원. 토글 OFF는 승인된 팀원(모집 시작일부터) |
| `/notifications` | SNS·팔로우·프로젝트·팔로잉 소식·읽음/안 읽음 | 로그인, 현재 계정 알림만 |
| `/subscription` | 프리미엄 상품 안내(월 ₩9,900)·요금제 비교 | 공개 |
| `/subscription/checkout` | 결제 수단 선택(토스페이먼츠 카드·카카오페이)·결제 전 고지와 동의·포트원 결제창 | 로그인, 결제는 무료(USER) 회원만 |
| `/subscription/manage` | 구독 상태·다음 결제일·결제 수단·해지(기간 끝까지 유지) | 로그인 |
| `/subscription/history` | 결제 내역(첫 결제·매월 결제·실패), 다음 페이지 표시 | 로그인 |
| `/admin` | 통계·회원/신고/콘텐츠/키워드·온도/AI/구독 | 관리자 |

## URL 조건

- 검색: `target=developer|project`, `q`, 직군·경력·기술·분야·협업·상태 등 필터. 고급 온도 조건/정렬은 페이지 상태입니다.
- 프로필: `tab=about|portfolio|posts|activity|reviews`, 리뷰 프로젝트 `project=pocket`.
- 주최자: `tab=applicants|team|settings`.
- 팔로우: `user=yuna&tab=followers|following&q=...&available=yes`.
- 메시지: `userId=demo-premium` 또는 `conversation=...`.
- 관리자: `tab=회원` 등 관리 메뉴명.
- 게시글 댓글 위치: `#comments`.

실제 예시 ID: 사용자 `demo-user`, `demo-premium`, `yuna`; 프로젝트 `pocket`, `devgarden`; 게시글 `type-tips`, `haneul-retro`.

## 오류·빈 상태

존재하지 않는 사용자/프로젝트/게시글, 잘못된 경로, 로그인 필요·권한 없음·채팅 멤버 아님·삭제된 채팅방, 검색 결과 없음·글/댓글/알림 없음에 각각 안내와 복구 경로를 제공합니다. SPA 특성상 화면 오류/권한 안내와 HTTP 응답 코드는 별개입니다.

메인 SNS는 가로 슬라이드, 전체 피드와 프로필 내 글은 세로 목록을 유지합니다. 프로필 상단은 5개 탭에서 유지합니다. 동적 상세 경로도 화면별 문서 제목을 사용하며 `/projects/new`, `/posts/new` 같은 정적 경로가 우선합니다.

최신 검증은 [Next.js 마이그레이션 보고서](changes-nextjs-migration.md), [6개 해상도 검사](artifacts/nextjs-migration/responsive/report.json), [Next.js 경로/응답 검사](artifacts/nextjs-migration/framework/report.json)를 참고하세요. 이전 단계 결과는 당시 기록으로 보존했습니다.

## 1차 수정 개인 공간

본인의 `/users/:userId`는 `개인 계정 Aside + 기존 배너/사용자 정보 + 5개 가로 탭`을 유지합니다. 다른 사용자의 공개 프로필에는 Aside를 표시하지 않습니다. 개인용 세 경로는 로그인 시 Aside를 공유하며 비로그인 상태에는 개인 목록을 표시하지 않습니다. 비공개 계정의 프로필·팔로우 목록·SNS 작성 글은 본인만 볼 수 있습니다. 북마크 탭은 `?tab=posts|projects`를 사용합니다.

신규 회원가입은 자동 로그인 후 `/onboarding`, 완료 후 자신의 `/users/:userId`로 이동합니다. 저장된 초안/현재 단계는 계정별로 복원합니다. 기존/데모 계정은 온보딩 완료 안내를 봅니다. [1차 수정 검증](changes-account-onboarding.md)에서 실제 결과를 확인하세요.

## 2차 프로젝트 관리와 리뷰

`/my-projects/:projectId/manage`를 정식 내 프로젝트 진입점으로 사용하고 기존 주최자 전용 `/projects/:projectId/manage`도 유지합니다. 알림 동작은 `?tab=settings&action=extend|end|edit`로 해당 모달을 열며 처리 후 action을 URL에서 제거합니다.

리뷰 링크는 `?request={요청ID}&token={Mock토큰}`입니다. 사용자/프로젝트/종료 상태/참여/기한/사용 여부를 브라우저에서 확인합니다. 프로필 `?tab=reviews`는 받은 리뷰 조회만 제공하고 직접 작성 폼은 없습니다. `review-policy`의 만료/온도 공식은 임시 정책이며 실제 보안은 후속 서버 범위입니다.

## 3차 변경

새 라우트 없이 기존 단체 채팅 화면에 강퇴 투표를 추가했습니다. 강퇴 대상은 해당 방의 메시지 조회/작성/직접 경로 접근이 제한됩니다. 내 프로젝트의 참여 이력은 유지합니다. 기존 모집/내 프로젝트 작성 버튼은 무료 회원에게 프리미엄 안내 모달을 열고 `/projects/new` 직접 접근도 프리미엄만 허용합니다. 지원자 상세 모달은 해당 공고의 프리미엄 주최자 전용입니다. 해지 후 기존 공고 관리 임시 유지 정책은 [미확정 정책](decisions-pending.md)에 기록했습니다.

## 4차 `/matching` 개편

기존 경로에서 대화형 추천을 제공합니다. 왼쪽 계정별 대화 기록, 중앙 메시지/질문/추천 카드, 하단 조건 설정/전송. 메인은 추천 시작으로 이동하고 입력 설명을 첫 메시지로 전달합니다. 프로필/유사 프로젝트 링크는 기존 경로를 사용합니다. [4차 상세](changes-matching-chat.md)

## 5차 공통 레이아웃

헤더 오른쪽 검색창을 통합 검색 아이콘으로 교체했습니다. 기존 검색 페이지와 대상/필터는 유지합니다. 800px 이하에서는 헤더 메뉴를 다음 행으로 배치하고 개인 계정 Aside/AI 기록을 접이식 메뉴로 전환합니다. 프로필 5개 탭은 유지하며 작은 화면에서는 필요하면 줄바꿈합니다. 카드와 Aside 본문은 가용 너비에 맞춰 조절하고 긴 문구/이미지는 컨테이너 안에 표시합니다. 페이지 전체 가로 스크롤은 숨기지 않고 실제 요소 너비를 조정했습니다. SNS 슬라이드·메시지·긴 비교 표 등 필요한 내부 스크롤은 유지합니다.

[라우팅 동작](routing.md), [Mock 데이터 구조](mock-data.md).

## Next.js 마이그레이션

URL과 기능/권한은 유지하고 실행 라우터를 App Router 파일 구조로 교체했습니다. 신규 업무 경로는 추가하지 않았습니다. 화면별 `_components`와 인접 CSS Module, 공통 `components`, 검증/저장 `lib` 구조를 사용합니다. React Router는 제거했습니다.

포트폴리오 상세는 프로젝트 이미지 갤러리·이름/URL 링크·팀 구성·본인 기여를 지원하며 인쇄/PDF 스타일을 제공한다. [프로젝트 사례 상세 개편](portfolio-case-study-update.md) 참고.

포트폴리오 등록·수정은 전용 페이지에서 섹션 제목/본문/형식/순서를 편집한다. [자유 구성 작성기](portfolio-composer.md) 참고.

### 프로젝트 지원 포트폴리오 제출본 (2026-11-10)

- `/projects/[projectId]/applications/[applicationId]/portfolios/[snapshotId]`: 제출 당시 내용의 읽기 전용 상세. 기존 포트폴리오 상세 디자인을 재사용한다. 본인 지원자 또는 프리미엄/관리자 주최자만 조회한다.
- BFF `/api/projects/[projectId]/applications/[applicationId]/portfolios/[snapshotId]` → GET `/api/v1/projects/{projectId}/applications/{applicationId}/portfolios/{snapshotId}`.
- 실제 지원 내역의 제목 링크는 원본 /portfolios/{id}가 아닌 제출본으로 연결한다. 원본 수정·삭제 후에도 유지한다. Mock 시연 데이터 구조를 실제 DB로 임의 이관하지 않는다.

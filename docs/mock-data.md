# Mock 데이터와 상태 구조

프론트 업무 Mock 상태 기준. 로그인·회원가입·프로필 일부는 실제 API와 연결되어 있으며, 나머지 데모 콘텐츠는 브라우저 저장소를 사용합니다. 실제 AI·결제 요청은 없습니다. 상단 Mock ON/OFF는 화면과 폼을 유지하면서 샘플 레코드만 비우고 복원합니다. OFF 직접 작성 데이터는 별도 작업 공간에 보관합니다. [표시 전환 범위](mock-visibility.md)를 참고하세요. 원본 선택지/데모 데이터는 `frontend/mocks`, 타입은 `types`, 검증/변경/복원은 `lib`의 도메인별 함수, 화면 공유는 `lib/state`가 담당합니다.

## 저장소

| 키 | 주요 필드/역할 | 공유 상태 |
|---|---|---|
| `devmozip:mock-visibility:v2` | `version`, `enabled` | 표시 설정 version 2, 기본 OFF·전환 버튼 숨김. 다른 도메인/인증 불변 |
| `devmozip:empty-mode:v1:<원래 키>` | OFF에서 직접 작성한 도메인 상태 | ON 샘플 저장소와 분리 |
| `devmozip:prototype:v1` | 안내 표시 설정 | 공통 안내 |
| `devmozip:auth:v1` | 계정 ID 세션 | 로그인 유지 시 localStorage, 해제 시 sessionStorage |
| `devmozip:profiles:v1` | `profiles`, `portfolios`, `posts`, `reposts`, `reviews`, `following`, `followPreferences`, `followNotices`, `social`, `socialNotices` | 프로필·SNS·검색·추천 |
| `devmozip:account-area:v1` | 계정별 `kakaoLinked`(기존 저장값 호환용, 화면 표시에 쓰지 않음), `isPrivate`(Mock 토글 ON의 공개 설정과 Mock 화면 판정에만 사용. 실제 계정 값은 서버 `users.is_private`), `passwordConfirmedAt`, `onboardingCompleted`, `draft`, `step` | Aside 화면·공개 범위·온보딩 |
| `devmozip:recruitment:v1` | `created`, `overrides`, `deletedIds`, `applications`, `bookmarks`, `reports`, `notifications`, `chatMembers`, `endedAt`, `deletedChatIds`, `reviewRequests`, `kickVotes`, `chatExclusions`, `simulationDate` | 모집·내 프로젝트·리뷰 요청·채팅 멤버 |
| `devmozip:messaging:v1` | `conversations`, `rooms`, `notificationReads`, `userReports` | 1:1/단체 대화·알림 읽음/개수. Mock 토글 OFF의 1:1·프로젝트 채팅·메신저 신고·알림은 서버에 저장하고 이 키를 쓰지 않는다. `notificationReads`는 토글 ON의 Mock 알림에만 쓴다 |
| `devmozip:home:v1` | `input`, `matching.usage`, `matching.last`, `chat.conversations`, `chat.active` | 메인 입력·계정별 월 사용량·대화/결과 기록 |
| `devmozip:administration:v1` | `subscriptions`, `members`, `resolutions`, `keywords`, `audit`, `payments` | 회원 제재·운영 화면. `subscriptions`·`payments`는 관리자 구독 현황의 Mock이며 로그인 계정의 역할에는 반영하지 않는다 |

이번 마이그레이션은 키/스키마/버전을 변경하지 않았습니다. 접이식 메뉴의 펼침 상태는 화면 로컬 상태이며 새로고침 시 작은 화면에서 기본 접힘으로 시작합니다. 기존 저장 데이터와 대화/사용량은 그대로 복원합니다.

## 관계와 갱신

- 계정 ID를 프로필/작성자/주최자/지원자/수신자 ID로 공유합니다. 데모는 `demo-user`, `demo-premium`, `demo-admin`입니다.
- SNS 좋아요·좋아요 날짜·북마크는 `profiles.social`의 동일 상태입니다. 피드/내 글/좋아요 목록/북마크함에서 별도 복사본을 만들지 않습니다. 퍼가기는 원본 게시글 ID를 참조합니다.
- 프로젝트는 초기 모집 데이터에 `created`와 `overrides`를 합치고 `deletedIds`를 제외해 조회합니다. 지원서는 프로젝트 ID·사용자 ID·포트폴리오 ID와 제목 스냅샷을 보존합니다.
- 승인 시 참여자와 채팅 멤버가 연결됩니다. 취소는 팀원/채팅에서 제외합니다. `chatExclusions[projectId]`는 강퇴된 사용자이며 프로젝트 참여 이력과 별개입니다. 화면 목록뿐 아니라 메시지 서비스에서도 접근을 검사합니다.
- 강퇴 투표는 대상/고정 유권자/계정별 찬반/생성·만료·종료 상태를 보존합니다. 결과 알림은 이벤트/수신자로 중복을 막습니다.
- 프로젝트 종료는 실제 종료 시각과 계정별 리뷰 요청을 생성합니다. 요청은 대상 ID·임시 토큰·만료·사용 시각을 가집니다. 제출된 별점/키워드는 `profiles.reviews`에 반영되어 평균·버블·온도/별 색상을 계산합니다.
- 구독 상태는 서버 API(`/api/v1/subscription/me`)와 서버 역할(USER/PREMIUM)로 정해집니다. 브라우저에 저장된 이전 데모 구독 전환은 로그인 계정의 역할을 바꾸지 않습니다. 공고 생성/지원자 상세 권한은 같은 현재 역할을 검사합니다. 관리자도 공고 생성의 프리미엄 조건을 우회하지 않습니다.
- AI 대화는 `chat.conversations[userId]`에 `{id,title,createdAt,updatedAt,requirements,messages}`로 저장합니다. 메시지는 사용자/assistant 구분, 본문, 생성 시각, 추천 시점 조건/결과를 가집니다. 분야·관심·기술은 배열, 직군은 `{name,count}[]`입니다.
- AI 사용량은 `matching.usage[userId]['YYYY-MM']`이며 한국 시각 월 기준입니다. 결과 생성과 차감량은 동일 Home 상태 변경으로 저장합니다. 일반 메시지·추가 질문·대화 조회/삭제는 차감하지 않습니다. 과거 추천은 스냅샷이며 현재 비공개 계정의 카드는 다시 숨깁니다.

## 복원·오류·초기화

각 서비스가 저장 데이터를 검증하며 이전 필드가 없으면 기본값을 적용합니다. 이전 단일 추천은 대화로 이관하고 삭제한 대화는 다시 만들지 않습니다. 저장소 손상은 해당 서비스의 복원 정책에 따라 초기값/유효 레코드로 돌아갑니다. 데이터가 바뀌지 않았으면 초기 원본은 아직 localStorage에 없을 수도 있습니다.

접근 거부/용량 초과 시 메모리 내 기능과 변경은 유지하고 저장 실패를 안내합니다. 이 경우 새로고침 후 보존은 보장하지 않습니다. 로그아웃은 콘텐츠를 지우지 않습니다. 전체 데모 초기화는 구독·반응·지원·대화·리뷰 요청·사용량·공개 설정 등을 복원하고 로그아웃하되 신규 가입 계정은 유지합니다.

이미지는 제한된 Data URL이며 실제 파일 업로드가 아닙니다. 계정 설정의 새 비밀번호 원문/해시는 저장하지 않고 확인 시각만 기록합니다. 비공개 계정은 로그인 잠금이나 운영자의 회원 정지와 다릅니다.

여러 탭/기기 간 실시간 동기화·충돌 해결, 서버 인증/원자적 사용량 차감, 실제 리뷰 링크 보안은 없습니다. 프로젝트 날짜 시뮬레이션은 프로젝트 알림/리뷰/투표에 적용되고 AI 사용량은 실제 브라우저 한국 시각을 사용합니다.

## Next.js에서 복원

`app/ClientProviders.tsx`는 서버/첫 클라이언트 렌더에 동일한 로딩 상태를 표시하고 마운트 후 기존 Provider를 초기화합니다. 따라서 localStorage 데이터를 서버에서 읽거나 Provider의 초기 저장 효과로 덮어쓰지 않습니다. 일반 Next 링크/쿼리 이동 중에는 루트 Provider가 유지됩니다. 새로고침은 같은 키로 복원합니다. Next RSC/청크 요청은 프레임워크 내비게이션이며 실제 서비스 API 요청이 아닙니다.

## MVP 완성 후 제거

기본 MVP가 모두 완성되면 frontend의 목업 시연용 Service 계층을 모두 제거합니다. 업무 규칙은 백엔드 Service가 맡습니다. 그 전까지는 Mock 토글 ON 시연을 위해 유지합니다.

- 대상은 브라우저 저장소(`readDemoStorage`, `writeDemoStorage`)에 업무 상태를 저장하는 서비스입니다.
  - `lib/messaging/messagingService.ts`
  - `lib/projects/projectService.ts`
  - `lib/profiles/profileService.ts`
  - `lib/account/accountAreaService.ts`
  - `lib/administration/administrationService.ts`
  - `lib/matching/homeService.ts`
- 이 서비스들을 쓰는 `lib/state` 저장소, Mock 토글(`lib/demo/mockVisibility.ts`)과 위 표의 데모 저장소 키도 함께 정리합니다.
- 서버와 연결된 화면이 아직 쓰는 검증·표시 함수가 이 파일들에 들어 있으면, 제거하기 전에 해당 도메인 모듈로 옮깁니다.
- `messagingService.ts`는 1:1·프로젝트 채팅·사용자 신고의 Mock 외에 토글 ON의 Mock 알림(`components/notifications/MockNotificationList.tsx`, 헤더 Mock 알림 배지)도 맡습니다. 토글 OFF의 알림은 서버 API(`lib/state/notificationStore.ts`)를 씁니다. 서버 알림은 프로젝트·리뷰 종류만 있으므로, 팔로우·피드 알림이 서버에 연결된 뒤에 Mock 알림을 제거합니다.

# 메신저 구현 계획

WBS 대분류 11.0 메신저(11.1~11.9)와 프로젝트 단톡방(7.18~7.23)을 브라우저 Mock에서 실제 서버 기능으로 옮기고, WebSocket 실시간 전달과 입력 중 표시를 추가하는 단계별 수행 계획이다.

- 상태: 15단계까지 완료했다. 15단계의 사용자 수동 확인과 16단계(운영 배포)가 남았다. 단계별 상태는 6번, 진행 내용은 8번에 기록한다. 확정 계약은 [메신저 API](messenger-api.md)에 있다.
- 근거: [이슈 #78](https://github.com/likelion-backendschool-9th/DevMozip/issues/78), WBS 11.1~11.9·7.18~7.23, 2026-11-06 사용자 요청.
- 3.2의 결정은 확정했다. 4번의 테이블은 ERD대로 구현했다. 5번의 API·프레임은 작업 중의 초안이며, 확정 계약은 [메신저 API](messenger-api.md)로 옮겼다(15단계).
- 이 문서는 구현 승인이 아니다. 각 단계는 사용자 요청을 받아 시작하고, 완료 보고 후 다음 단계를 자동으로 시작하지 않는다.
- 작업은 develop 기준으로 검증한다. 커밋·푸시·main 병합·배포는 사용자가 요청할 때만 한다.

## 1. 범위

| WBS | 작업 | 페이지 | API |
|---|---|---|---|
| 11.1 | 1:1 대화방 목록(프로젝트 단톡방 목록 포함) | `/messages/chats` | `GET /api/v1/messages/chats` |
| 11.2 | 1:1 메시지 조회 | `/messages/chats/{id}` | `GET /api/v1/messages/chats/{id}` |
| 11.3 | 1:1 메시지 전송 | `/messages/chats/{id}` | `POST /api/v1/messages/chats/{id}` |
| 11.4 | 메신저 사용자 신고 | `/messages/chats/{id}` | `POST /api/v1/users/{slug}/reports` |
| 11.5 | 1:1 대화 시작 | `/users/{slug}` → `/messages/chats/{id}` | `POST /api/v1/messages/chats` |
| 11.6 | 1:1 읽음 처리 | `/messages/chats/{id}` | `PATCH /api/v1/messages/chats/{id}/read` |
| 11.7 | 1:1 미확인 메시지 수 | 공통 헤더 | `GET /api/v1/messages/chats/unread-count` |
| 11.8 | 1:1 메시지 실시간 전달 | - | `/ws/messages` (WebSocket) |
| 11.9 | 1:1 메시지 입력중 표시 | `/messages/chats/{id}` | `/ws/messages` (WebSocket) |
| 7.18 | 프로젝트 대화 시작 | `/projects/{id}` → `/messages/projects/{id}` | 별도 API 불필요 |
| 7.19 | 프로젝트 단톡방 생성·관리 | `/messages/projects/{id}` | `GET /api/v1/messages/projects/{id}`, `POST /api/v1/messages/projects/{id}`, `POST /api/v1/messages/projects/{id}/delete` |
| 7.20 | 프로젝트 단톡방 읽음 처리 | `/messages/projects/{id}` | `PATCH /api/v1/messages/projects/{id}/read` |
| 7.21 | 프로젝트 단톡방 미확인 메시지 수 | 공통 헤더 | `GET /api/v1/messages/projects/unread-count` |
| 7.22 | 프로젝트 단톡방 실시간 전달 | 공통 | `/ws/messages` (WebSocket) |
| 7.23 | 프로젝트 단톡방 입력 중 표시 | `/messages/projects/{id}` | `/ws/messages` (WebSocket) |

실시간 전달은 새 메시지와 읽음 신호를 바로 전달하며, 서버 간 전달은 Redis Pub/Sub이 맡는다. 입력 중 표시는 저장하지 않는다.

다음은 이번 범위에 넣지 않는다.

| 구분 | 항목 | 비고 |
|---|---|---|
| 다른 대분류 | 6.7 개발자 카드의 대화 제안 버튼 | 현재 화면에 버튼이 없다. 7단계의 대화 시작 API를 나중에 그대로 쓸 수 있다. |
| 다른 대분류 | 7.11 강퇴 투표(#49) | 투표는 #49 범위다. 이 작업은 가결 시 호출할 채팅 제외 처리만 제공한다. |
| 다른 담당 코드 | `/projects/{id}/manage`의 채팅방 삭제 선택을 서버에 연결 | 프로젝트 화면 담당 범위다(PR #81, #85). 이 작업은 논리 삭제 API를 제공하고 PR에 적는다. |
| 별도 브랜치 | 가입 시 `tag` 자동 생성 | 다른 브랜치의 별도 작업이다(D4). |
| 다른 대분류 | 12.x 알림, 14.8 신고 처리 | 새 메시지 알림은 WBS에 없다. 신고는 저장까지만 한다. |
| WBS에 없음 | 첨부, 메시지 수정·삭제, 대화방 나가기, 차단, 협업 제안, 전송 횟수 제한 | 필요하면 별도 요청으로 추가한다. |
| 데이터 | Mock 대화·신고의 DB 이전 | 시드와 localStorage 내용을 옮기지 않는다. |

## 2. 현재 상태

| 위치 | 현재 | 변경 |
|---|---|---|
| 백엔드 | 1:1 메시지 API([MessageController](../backend/src/main/java/com/devmozip/backend/controller/MessageController.java))와 사용자 신고 API([UserReportController](../backend/src/main/java/com/devmozip/backend/controller/UserReportController.java))가 있다(2~4단계). `/ws/messages` WebSocket 서버가 메시지·읽음·입력 중 신호를 Redis Pub/Sub으로 전달한다(8·9·11단계). 프로젝트 채팅 API([ProjectChatController](../backend/src/main/java/com/devmozip/backend/controller/ProjectChatController.java))가 있다(12단계). REST 경로는 [SecurityConfig](../backend/src/main/java/com/devmozip/backend/config/SecurityConfig.java)의 `anyRequest().authenticated()`에 해당한다. | 완료했다. REST 경로의 보안 설정은 바꾸지 않았고 WebSocket 경로만 추가했다(3.4). |
| [1:1 화면](../frontend/app/messages/chats/page.tsx) | Mock 토글 OFF는 서버 API와 WebSocket 신호를 쓰고 입력 중 표시를 보여 준다. ON은 localStorage `devmozip:messaging:v1`을 쓴다(D5, 6·10·11단계). | 완료했다. |
| [프로젝트 채팅 화면](<../frontend/app/messages/projects/[projectId]/page.tsx>) | Mock 토글 OFF는 서버 API·실시간 신호·#49 강퇴 투표를 사용한다. 날짜 시뮬레이터는 Mock 전용이다. ON은 같은 저장소의 Mock이다(D5, D9, 14단계). | 완료했다. |
| [ProjectMember](../backend/src/main/java/com/devmozip/backend/entity/ProjectMember.java) | 주최자 행만 만들어진다. 승인 팀원은 #48(PR #85)에서 추가된다. | 수정하지 않고 메신저 쪽 [ProjectChatAccessRepository](../backend/src/main/java/com/devmozip/backend/repository/ProjectChatAccessRepository.java)로 읽기만 한다(3.5, 12단계). |
| [messagingService](../frontend/lib/messaging/messagingService.ts), [messagingStore](../frontend/lib/state/messagingStore.ts) | 1:1, 프로젝트 방, 알림 읽음, 신고를 한 상태로 관리한다. | Mock 경로로 그대로 둔다. 알림은 토글과 무관하게 지금처럼 동작한다. |
| [ChatWindow](../frontend/app/messages/_components/ChatWindow.tsx), [ConversationSidebar](../frontend/app/messages/_components/ConversationSidebar.tsx), [UserReportDialog](../frontend/app/messages/_components/UserReportDialog.tsx) | 프로젝트 채팅과 공유한다. 서버 대화용 prop(`history`, `typing` 등)을 추가했고, 넘기지 않으면 Mock과 같이 동작한다(6·7·11단계). | 프로젝트 채팅을 서버에 연결할 때 같은 prop을 쓴다(14단계). |
| 로그인 사용자 ID | 세션 응답은 `username`과 `role`뿐이고, `user.id`는 [데모 ID](../frontend/lib/account/serverIdentity.ts)다. 화면은 실제 `users.id`를 모른다. | 서버가 메시지마다 `mine`을 내려준다. 세션 응답은 바꾸지 않는다. |
| [헤더 배지](../frontend/app/_components/AppLayout.tsx) | Mock 토글 OFF는 서버의 1:1과 프로젝트 채팅 안 읽은 수의 합계를 "안 읽은 메시지"로, ON은 Mock의 `directUnread`를 "안 읽은 쪽지"로 쓴다(D12, 14단계). | 완료했다. |
| 대화 시작 경로 | Mock 토글 OFF는 [실제 프로필 헤더](<../frontend/app/(user)/users/[userId]/_components/ApiProfileHeader.tsx>)의 메시지 보내기로 `tag` 기준으로 시작한다(7단계). `?userId=` 진입은 Mock 전용이다. | 완료했다. |
| Redis | [리프레시 토큰](../backend/src/main/java/com/devmozip/backend/security/jwt/RedisRefreshTokenStore.java) 저장에만 쓴다. 블루그린의 두 색이 하나를 공유하고, `local` 프로필은 메모리를 쓴다. | Pub/Sub 전달을 추가한다. 메시지는 저장하지 않는다. |
| 배포 | [팀 nginx](../nginx/server.conf)와 [호스트 nginx 템플릿](../deploy/setup-host.sh)에 `/ws/messages`만 백엔드로 보내는 경로를 넣었다. [블루그린 스크립트](../deploy/blue-green.sh)가 백엔드 업스트림을 함께 쓴다(13단계). 운영 서버에는 아직 배포하지 않았다. | 새 포트는 열지 않는다. 배포는 16단계다. |

## 3. 결정 사항

### 3.1 WBS만으로 구현되지 않았던 부분

처음 WBS(11.1~11.4, 7.18)의 공백이다. 6.7의 API 변경을 빼면 현재 WBS에 등록됐다.

1. **대화 시작:** 6.7과 11.3이 같은 `POST /messages/chats/{id}`를 쓴다. `{id}`가 대화방 ID라면 방이 없는 첫 대화를 시작할 수 없다.
2. **읽음:** 현재 화면은 읽음·안 읽음과 안 읽은 수를 보여주지만 WBS에 읽음 API가 없었다.
3. **헤더 배지:** 모든 화면에서 안 읽은 수가 필요한데 전용 API가 없었다.
4. **태그 없는 회원:** [가입](../backend/src/main/java/com/devmozip/backend/service/MemberService.java)이 `tag`를 채우지 않아, 프로필을 수정하기 전까지 `tag`가 NULL이다. 이런 회원이 메시지를 보내면 상대는 프로필로 이동할 수 없고 11.4의 `{slug}`로 신고할 수도 없다. 별도 작업으로 해결한다(D4).
5. **ERD 미대조:** 저장소에 ERD 파일이 없다. [프로젝트 청사진](project-backend-blueprint.md)으로 `reports`의 컬럼과 `chat_members`의 존재만 확인했다. 채팅 테이블은 ERD 이미지를 받아 4번에서 대조했다.
6. **실시간:** WBS에 실시간 항목이 없었다. 브라우저가 토큰을 읽지 못하는 BFF 구조이고 nginx가 프론트로만 프록시하므로, WebSocket에는 별도 경로와 인증 처리가 필요하다. 구조는 3.4에 있다.
7. **프로젝트 채팅:** 단톡방 `POST`가 방 생성인지 메시지 전송인지 정해져 있지 않았고, 목록·읽음 API가 없었다. 구조는 3.5에 있다.

### 3.2 결정 항목

2026-11-06에 확정했다.

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| D1 | 새 메시지 수신 방식 | WebSocket과 Redis Pub/Sub. 전송과 본문 조회는 REST를 유지하고, 폴링은 연결이 끊겼을 때의 보조 수단으로 남긴다. 메시지 원본은 PostgreSQL이다. Netty, SSE, Redis Streams 큐, HBase는 쓰지 않는다. | 참여 대화방 수에 제한이 없고 여러 대화를 동시에 실시간으로 볼 수 있어야 한다. SSE는 HTTP/1.1에서 브라우저 연결 제한(사이트당 6개)에 걸려 탭을 늘릴 수 없다. PostgreSQL을 원본으로 두면 Redis 장애에도 메시지가 유실되지 않는다. |
| D2 | `{id}`의 의미와 대화 시작 | `{id}`는 대화방 ID다. `POST /messages/chats`를 추가하고 6.7의 API를 이것으로 바꾼다. | WBS의 다른 경로가 회원은 `{slug}`, 자원은 `{id}`로 구분하고 현재 화면 경로도 대화 ID 기준이다. |
| D3 | 읽음 처리 | `PATCH /messages/chats/{id}/read`를 추가한다. | 폴링하는 GET에 쓰기 부작용을 넣지 않는다. 화면에 실제로 표시한 메시지까지만 읽음으로 남긴다. |
| D4 | 태그 없는 회원 | 가입 시 `tag` 자동 생성으로 해결한다. 다른 브랜치의 별도 작업이며 이 계획에 포함하지 않는다. 메신저에는 태그 없는 회원을 막는 규칙을 두지 않는다. | 근본 원인이 회원 가입에 있다. 그 작업 전까지의 제약은 7번에 적었다. |
| D5 | Mock 토글과의 관계 | ON이면 기존 Mock을 유지하고, OFF면 서버를 쓴다. 1:1과 프로젝트 채팅에 같이 적용한다. | 발표용 Mock 시나리오를 보존한다. 화면은 같은 데이터 형태를 받고 출처만 나눠 분기를 줄인다. |
| D6 | 공통 `reports` 작성 주체 | 이 작업에서 `Report` 엔티티를 청사진 컬럼대로 만든다. 별도 합의 단계 없이 PR에 사용 방식을 적어 #51 담당자에게 알린다. 4단계 시작 전에 #51의 PR이 생겼는지 확인한다. | 코드에 아직 없고 #51의 PR도 없다. #51도 같은 테이블을 쓰므로 알릴 필요는 있다. |
| D7 | 정책 값 | [기존 임시 정책](decisions-pending.md)을 유지한다. 본문 1~2,000자, 신고는 신고자·대상당 1회, 사유는 현재 5개 선택지다. | 현재 화면과 같다. |
| D8 | 테이블 구조 | ERD의 `chat_rooms`, `chat_members`, `messages`를 따른다. 엔티티는 `ChatRoom`, `ChatMember`, `Message`다. ERD 대조 후 결정한 E1~E3은 4번에 있다. | [AGENTS.md](../AGENTS.md)가 불일치를 먼저 보고하고 임의로 추가하지 않도록 정한다. |
| D9 | 프로젝트 채팅 | 프로젝트 단톡방(7.18~7.23)을 이 계획에 포함한다(12·14단계). 실시간 경로는 1:1과 공유한다. 강퇴 투표(#49)는 포함하지 않는다. | 1:1과 프로젝트 채팅을 함께 실시간으로 볼 수 있어야 한다. 실제 승인 팀원 데이터는 #48이 먼저 필요하다. |
| D10 | 프로젝트 채팅방 삭제 | 논리 삭제다. `DELETE` 메서드를 쓰지 않고 `POST /messages/projects/{id}/delete`로 나눈다. WBS 7.19의 "관리"에 포함한다. | 메시지를 DB에 남긴다. 메시지 전송과 권한·응답이 달라 경로를 나눈다. |
| D11 | 채팅방 목록 API | `GET /messages/chats` 하나로 조회하고 `type`으로 1:1과 프로젝트를 구분한다. `GET /messages/projects`는 만들지 않는다. | WBS 11.1이 프로젝트 단톡방 목록을 포함하고, 화면도 같은 사이드바의 두 탭이다. 탭마다 표시 항목·검색 기준·커서가 달라 한 응답에 섞지 않는다. |
| D12 | 헤더 배지 | 토글 OFF에서 1:1(11.7)과 프로젝트(7.21)의 합계를 표시한다. ON은 지금처럼 1:1만 센다. | 다른 페이지에서도 프로젝트 채팅의 새 메시지를 알 수 있다. 목업에 없는 배지를 새로 만들지 않는다. |
| D13 | 프로젝트 입장 | 전용 API를 만들지 않는다(WBS 7.18). | 입장 버튼이 프로젝트 ID로 바로 이동하고 방은 첫 접근 때 서버가 만든다. |

### 3.3 설계 기준

| 항목 | 기준 |
|---|---|
| 사용자 식별 | [MemberController](../backend/src/main/java/com/devmozip/backend/controller/MemberController.java)처럼 `Authentication.getName()`(이메일)으로 회원을 찾는다. 요청 본문·쿼리의 사용자 ID를 신원으로 쓰지 않는다. 서비스는 회원 ID를 인자로 받는다. WebSocket 프레임 처리에는 `SecurityContextHolder`가 없어 같은 서비스를 재사용하려면 필요하다. |
| 접근 거부 | 참여자가 아니면 403이 아니라 404로 응답해 대화방의 존재를 드러내지 않는다. |
| 대화방 유일성 | 두 회원 쌍당 방 하나를 보장한다. 두 회원의 `users` 행을 ID 순서로 잠근 뒤 `chat_members`에서 기존 방을 조회하고, 없을 때만 만든다(E2). ID 순서로 잠가 교착을 막는다. DB 제약이 없으므로 1:1 방 생성은 이 경로 하나로만 한다. |
| 메시지 순서 | 전송 시 대화방 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 저장한다. ID 순서와 커밋 순서가 어긋나면 `after` 폴링이 메시지를 놓칠 수 있다. `sent_at`도 잠금 안에서 정하고 같은 방의 이전 메시지보다 늦게 둔다. DB 정밀도인 마이크로초로 자르고, 이전 메시지보다 늦지 않으면 1마이크로초 뒤로 둔다. 읽음 위치와 안 읽은 수가 `sent_at` 기준이라, 시각이 같으면 안 읽은 메시지를 읽은 것으로 셀 수 있다. |
| 읽음 위치 | 조건을 넣은 UPDATE 한 문장으로 옮겨, 동시 요청이 위치를 되돌리지 못하게 한다. |
| 본문 | 앞뒤 공백을 제거한 뒤 길이를 검사하고 서버 검증을 기준으로 한다. NUL 문자는 PostgreSQL에 저장할 수 없어 거부한다. 일반 텍스트만 저장하며 화면에서 HTML로 렌더링하지 않는다. |
| 로그 | 메시지 본문과 신고 내용을 로그에 남기지 않는다. |
| 시각 | 저장은 [기존 규칙](project-backend-blueprint.md)(`LocalDateTime`, Asia/Seoul)을 따르고 응답에 `+09:00`을 붙인다. 컬럼에는 시간대가 저장되지 않고 컨테이너 시간대는 UTC이므로, `joined_at`, `sent_at` 등은 `LocalDateTime.now()`가 아니라 Asia/Seoul `Clock`(`messengerClock`)으로 정한다. `ChatWindow`가 `new Date()`로 읽으므로 오프셋이 없으면 브라우저 시간대로 해석된다. |
| DB 호환 | JPQL과 JPA 매핑만 사용한다. PostgreSQL과 `local` 프로필(H2)에서 모두 동작해야 한다. |
| 응답 | 엔티티를 직접 반환하지 않고 `dto/message`의 record를 쓴다. 목록의 상대 정보·마지막 메시지·안 읽은 수는 N+1 없이 조회한다. |
| BFF | 브라우저는 `/api/messages/...`와 `/api/users/{slug}/reports`만 호출한다. 쓰기 요청의 Origin과 JSON 형식을 확인한다. Origin은 `X-Forwarded-Host`나 `Host`와 비교한다. 처음에는 [profileProxy](../frontend/lib/profiles/profileProxy.ts)처럼 `request.url`과 비교했으나, 컨테이너의 Next 서버에서는 이 값이 `0.0.0.0`으로 만들어져 모든 쓰기가 403이 돼 고쳤다(15단계). 쿼리 파라미터와 응답 필드는 허용한 것만 전달하고, 백엔드의 오류 내용 대신 경로별 안내 문구를 내려보낸다. 시각은 브라우저 해석 차이를 막기 위해 밀리초까지 잘라 전달한다. 만료 토큰 갱신은 [proxy](../frontend/proxy.ts)가 이미 처리한다. WebSocket만 BFF를 거치지 않는다(3.4). |

### 3.4 실시간 구조

WebSocket은 새 메시지가 있다는 신호만 전달한다. 메시지 본문은 REST로 보내고 받는다. 권한 검사와 본문 처리가 한 경로에 남고, 본문이 Redis를 거치지 않는다.

```text
보내는 사람 → BFF → POST /messages/chats/{id} → PostgreSQL 커밋
                                                    │ 커밋 후 발행 (본문 없음)
                                                    ▼
                                            Redis Pub/Sub 채널
                                                    │ 모든 백엔드가 구독
                                                    ▼
받는 사람 ← WebSocket 신호 ← 이 회원이 연결된 백엔드
받는 사람 → BFF → GET /messages/chats/{id}?after=  (본문 조회)
```

아래 수치는 초안이다.

| 항목 | 기준 |
|---|---|
| 서버 | `spring-boot-starter-websocket`의 `TextWebSocketHandler`를 기존 Tomcat 8080에서 실행한다. STOMP는 쓰지 않는다. 브로커 릴레이가 Redis를 지원하지 않고, 회원 단위 전달에는 구독 모델이 필요 없다. |
| 전달 단위 | 회원 단위로 보내고 방 구독은 두지 않는다. 받는 사람은 서버가 `chat_members` 기준으로 정하므로, 프로젝트 방도 같은 경로를 쓸 수 있다. |
| 세션 관리 | 회원 ID별 세션을 메모리에 둔다. 연결은 탭마다 하나이고, 연결 하나로 그 회원의 모든 대화방 신호를 받으므로 참여 대화방 수의 영향을 받지 않는다. 참여 대화방 수와 회원당 연결 수를 제한하지 않는다. `ConcurrentWebSocketSessionDecorator`로 전송을 직렬화하고, 전송이 5초를 넘거나 대기 버퍼가 64KB를 넘는 연결은 4500으로 닫는다. Tomcat의 기본 전송 대기 시간(20초)도 5초로 줄여, 멈춘 연결이 다른 연결의 전달을 오래 막지 않게 한다. |
| 인증 | 핸드셰이크에서 `devmozip_access` 쿠키를 읽어 `JwtProvider`와 `SuspendedMemberStore`로 검증한다. [JwtAuthenticationFilter](../backend/src/main/java/com/devmozip/backend/security/jwt/JwtAuthenticationFilter.java)는 Bearer 헤더만 읽으므로 `SecurityConfig`에서 `/ws/messages`만 허용하고, 검증은 핸드셰이크 인터셉터가 맡는다. |
| Origin | 쿠키가 자동 전송되므로 필수다. 기존 `DOMAINNAME`만 허용하고, 값이 없으면 모두 거부한다. `local` 프로필은 `http://localhost:3000`을 기본으로 둔다. 스프링의 기본 Origin 검사는 Origin이 없거나 Host와 같으면 통과시키므로, 핸드셰이크 인터셉터가 먼저 정확히 일치하는지 확인한다. |
| 토큰 만료 | 1초 주기의 연결 확인에서 토큰 만료 시각이 지나면 4401로 닫는다. 핸드셰이크는 `proxy`를 거치지 않으므로, 클라이언트는 `/api/auth/session`을 호출해 토큰을 갱신한 뒤 재접속한다. `JwtProvider`에 만료 시각 조회를 추가한다. |
| 핸드셰이크 거부 | 쿠키 없음·위조·만료는 401, Origin 불일치와 정지 회원은 403으로 응답한다. 브라우저 JavaScript는 이 상태 코드를 볼 수 없고 연결이 열리기 전에 1006으로 닫힌 것만 알 수 있다. |
| 정지 회원 | 핸드셰이크에서 정지 목록과 DB 상태를 확인한다. 연결 후에는 프레임 처리, 신호 전달, 1초 주기의 연결 확인에서 `isSuspended`를 확인해 4403으로 닫는다. 관리자 제재 API가 `suspend()`를 호출하면 추가 연결 없이 같은 서버의 연결이 1초 안에 닫힌다. 정지 목록은 서버별 메모리다. |
| 연결 유지 | 서버가 30초마다 `ping`을 보내고 클라이언트가 `pong`으로 답한다. 브라우저 JavaScript는 프로토콜 Ping을 볼 수 없어 애플리케이션 프레임으로 처리한다. 70초 동안 응답이 없으면 4500으로 끊고 재접속한다. nginx 두 계층의 타임아웃은 이보다 긴 75초로 둔다. |
| 재접속 | 지수 백오프에 무작위 지연을 더한다. 배포 때 연결이 한꺼번에 끊기기 때문이다. 상한은 1초에서 두 배씩 30초까지 늘고, 상한의 절반에 무작위로 나머지 절반 안의 시간을 더해 기다린다. 열리면 1초로 되돌린다. 4401은 세션을 갱신한 뒤 바로 다시 연결한다. 재접속 직후 `after`로 놓친 메시지를 조회한다. 신호는 중복돼도 결과가 같다. |
| 폴링 | 연결 중에는 60초, 끊기면 6단계 주기(열린 대화 5초, 목록·배지 30초)로 동작한다. |
| 숨겨진 탭 | 여러 대화를 동시에 보므로 WebSocket 연결을 유지하고 신호도 처리한다. 읽음 처리만 탭이 보일 때까지 미룬다. |
| 입력 중 표시 | 클라이언트는 3초에 1회만 보내고, 서버는 회원·방당 1초에 1회를 넘으면 무시한다(`messenger.realtime.typing-interval`). 받는 화면은 5초 뒤 자동으로 지운다. 방 참여 여부는 연결마다 10초 동안 기억한다(`typing-access-cache`). 그동안의 퇴장·삭제·정지는 최대 10초 늦게 반영된다. `canSend`가 false인 방에서는 보내지 않고, 서버도 무시한다. 보낼 수 없는 방의 신호는 연결을 닫지 않고 무시하며, 형식이 틀린 프레임만 1008로 닫는다. 프로젝트 방은 누가 입력 중인지 표시하도록 닉네임을 함께 보낸다. 1:1은 대화 기록 맨 아래에 상대 말풍선을 띄우고 그 안에서 점 세 개가 움직인다. 프로젝트 채팅은 기록 밖의 높이 고정 영역에 이름과 함께 문구로 보여 준다. |
| 발행 | 커밋 후 전용 스레드 하나의 대기열(1,000개)에 넣는다. 발행이 실패하거나 구독 중인 서버가 없으면 5초 동안 Redis를 건너뛰고 같은 서버의 연결로 직접 전달한다. 건너뛰지 않으면 장애 중에 신호마다 타임아웃(2초)을 기다려 같은 서버의 전달까지 밀린다. 대기열이 가득 차면 요청 스레드에서 직접 전달한다. |
| 구독 | Redis에서 받은 신호는 같은 전용 스레드로 넘겨 전달하고, 대기열이 가득 차면 버린다. 리스너 컨테이너는 첫 구독에 실패하면 예외를 던져 서버 시작을 막으므로, 구독은 별도 스레드에서 성공할 때까지 5초마다 다시 시도한다. 한 번 구독한 뒤의 재연결과 재구독은 Lettuce가 맡는다. |
| 프로필 | `messenger.realtime.redis.enabled`로 Redis 사용을 고른다. 기본값은 true이고 `local`과 `test` 프로필은 false다. 프로필 없이 실행하는 통합 테스트도 있어 `build.gradle`의 test 작업에서 시스템 속성으로 함께 끄고, Redis를 시험하는 테스트만 실행 인자로 켠다. false면 같은 서버의 연결로만 전달한다. |
| 개발 환경 주소 | HTTPS 페이지는 같은 Origin의 `wss`로 연결한다. HTTP 페이지는 `NEXT_PUBLIC_MESSENGER_WS_PORT`가 있으면 그 포트로 직접 연결하고, 없으면 같은 Origin으로 시도한다. 쿠키는 포트를 구분하지 않으므로 `localhost:3000`의 쿠키가 8080으로 전달된다. 값은 빌드할 때 들어가며 설정 방법은 [frontend/.env.example](../frontend/.env.example)에 있다. |

**Redis 장애 시 동작**

PostgreSQL이 원본이므로 Redis가 멈춰도 메시지는 유실되지 않는다. 실시간 신호만 영향을 받는다.

| 기능 | 장애 중 | 복구 후 |
|---|---|---|
| 메시지 전송·조회·읽음 | 정상. 발행은 전용 실행기에서 처리해 응답을 늦추지 않는다. 동기로 발행하면 전송마다 Redis 타임아웃(2초)만큼 늦어진다. | 영향 없음 |
| 실시간 신호 | 발행이 실패하면 같은 서버의 연결로 직접 전달한다. 평상시에는 한 색만 운영하므로 실시간이 유지된다. 다른 색에 연결된 회원은 60초 폴링이나 재접속으로 받는다. | Lettuce가 다시 연결해 구독을 복구한다. 유실된 신호를 다시 보내지 않으므로 폴링과 `after`가 보완한다. |
| 서버 시작 | 뜬다. 구독만 5초마다 다시 시도한다. 헬스체크는 기존과 같이 DOWN이다. | 구독이 성공하면 바로 정상 |
| 입력 중 표시 | 같은 서버 안에서만 전달한다. 유실을 허용한다. | 바로 정상 |
| WebSocket 연결 | 유지된다. 핸드셰이크와 연결 유지에 Redis를 쓰지 않는다. | 영향 없음 |
| 로그인·토큰 재발급 (기존 동작) | 500으로 실패한다. `proxy`가 재발급 실패 이유를 구분하지 않고 쿠키를 지우므로, 장애 중 액세스 토큰이 만료된 회원은 로그아웃된다. | 다시 로그인해야 한다. |
| 헬스체크·배포 (기존 동작) | `/actuator/health`가 DOWN이 되어 블루그린 전환이 진행되지 않는다. 실행 중인 컨테이너는 그대로 동작한다. | 영향 없음 |

### 3.5 프로젝트 채팅 구조

프로젝트 코드를 고치지 않도록 멤버 정보를 읽어서 판단한다. 기존 [임시 정책](decisions-pending.md)을 서버에 옮긴다.

| 항목 | 기준 |
|---|---|
| 멤버 기준 | `project_members`의 ACTIVE 행을 읽는다. 승인·취소 코드(#48)에 채팅 호출을 넣지 않는다. 취소로 LEFT가 되면 과거 기록에도 접근할 수 없다. |
| 방 생성 | 프로젝트당 하나다. 모집 시작일(서울 날짜) 당일부터 첫 접근 때 만든다. 시작일 전에는 입장할 수 없다. 입장 전용 API는 두지 않는다(D13). 프로젝트와 방은 `chat_rooms.project_id`로 연결한다(E1). 방이 없을 때만 프로젝트 행을 잠근 뒤 다시 확인해 동시 첫 접근을 한 줄로 세운다. 처음 계획한 "유일 제약 위반 후 새 트랜잭션에서 재조회"는 요청마다 DB 연결을 두 개 잡아 동시 접근이 몰리면 연결 풀이 바닥날 수 있어 바꿨다(12단계). 잠금 순서는 항상 프로젝트, 방이다. |
| 목록 | `GET /messages/chats?type=project`로 조회한다. `project_members`가 ACTIVE이고 모집 시작일이 지난 프로젝트를 반환하며, 방 행이 아직 없어도 포함한다. 삭제된 방과 프로젝트, 제외된 회원의 방은 뺀다. `id`는 프로젝트 ID다. `memberCount`는 ACTIVE 멤버 중 채팅에서 제외되지 않은 인원이다. |
| 안 읽은 수 | 방별 수와 합계(7.21)는 `last_read_at` 뒤의 메시지 중 내가 보내지 않은 것을 센다. 승인 전 메시지도 포함한다. 초대·퇴장 문구는 메시지가 아니므로 세지 않는다. `chat_members` 행이 있고 `left_at`이 없으며 프로젝트 멤버가 ACTIVE인, 삭제되지 않은 방만 센다. 조회에서 쓰기를 하지 않으므로 새 팀원은 그 방의 다음 접근 전까지 0이다. |
| 초대·퇴장 문구 | 메시지로 저장하지 않는다. 접근·전송 때 `chat_members`와 `project_members`를 비교해 참여 행을 만들거나 `left_at`을 채우고, 화면은 `joined_at`, `left_at`으로 문구를 표시한다. 실제 승인 시각이 아니라 다음 접근 시각이 기준이며, 그 전까지 신호도 가지 않는다. |
| 7.19 `POST` | `POST /messages/projects/{id}`는 메시지 전송이고, `POST /messages/projects/{id}/delete`는 방 논리 삭제다. `DELETE` 메서드는 쓰지 않는다(D10). 방 생성은 위 규칙으로 서버가 처리한다. |
| 종료 | `Project.status`가 COMPLETED면 읽기 전용이다. 삭제된 프로젝트(`deleted_at`)는 404다. |
| 방 삭제 | 임시 정책과 달리 논리 삭제다. 종료 후 주최자만 할 수 있다. 메시지와 멤버 행은 남긴다. 삭제된 방은 목록에서 빠지고 모든 접근이 404이며 입력 중 신호도 보내지 않는다. 삭제는 `chat_rooms.deleted_at`을 채운다(E3). 같은 요청을 다시 보내도 204로 응답하고 처음 삭제한 시각을 유지한다. 삭제 자체는 실시간 신호를 보내지 않아, 보고 있던 멤버는 다음 조회 때 404를 받는다. |
| 삭제 후 재생성 | 삭제된 방은 다시 만들지 않는다. 방이 아직 없는 프로젝트를 삭제하면 `deleted_at`을 채운 방 행을 만들어, 다음 접근 때 새 방이 생기지 않게 한다(E3). |
| 삭제 선택 화면 | 기존 화면과 같이 `/projects/{id}/manage`의 프로젝트 종료 창에서 고른다. 채팅 화면에는 삭제 버튼을 두지 않는다. 이 화면의 서버 연결은 프로젝트 화면 담당 범위다. |
| 강퇴 | 가결 시 #49가 `ProjectChatService.excludeMember(projectId, memberId)`를 호출한다. `chat_members.left_at`을 채우며, 방이나 참여 행이 없으면 만들어 둔다. 제외된 회원은 프로젝트 참여 이력과 무관하게 입장할 수 없다. #48(PR #85)은 참여 기록이 있는 회원을 다시 승인하지 않으므로("이미 참여 기록이 있는 회원입니다") 퇴장 후 재승인을 구분할 필요가 없다. `left_at`이 채워진 회원은 다시 들어오지 못한다. 투표는 #49 범위다. |
| 다른 담당 코드 | 프로젝트 코드는 수정하지 않는다. #85가 `ProjectMemberRepository`를 함께 고치므로 충돌을 피하려고 이 파일도 바꾸지 않고, 메신저 쪽에 읽기 전용 [ProjectChatAccessRepository](../backend/src/main/java/com/devmozip/backend/repository/ProjectChatAccessRepository.java)를 두었다. 프로젝트 행 잠금은 기존 `ProjectRepository.findLiveForUpdate`를 쓴다. 필요한 변경은 PR에 적는다. |

## 4. 테이블

ERD의 채팅 테이블을 1단계에서 그대로 구현했다(D8). ERD는 컬럼과 타입을 보여 준다. NOT NULL, `type` 값, 유일 제약, 인덱스는 이 계획에서 정했다. `chat_members.user_id`는 `users`, `chat_rooms.project_id`는 `projects`로 이어진다. `tag`는 바뀔 수 있으므로 저장하지 않고 응답할 때 회원에서 읽는다.

채팅 테이블의 시각 컬럼은 TIMESTAMP를 쓴다. 다른 테이블의 ERD 표기는 DATETIME이지만 PostgreSQL의 실제 타입은 모두 `timestamp without time zone`으로 같다. TIMESTAMPTZ는 쓰지 않는다.

**chat_rooms** (`ChatRoom`)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 대화방 ID |
| type | VARCHAR(20) | `DIRECT`, `PROJECT` |
| project_id | BIGINT FK NULL | `projects.id`. UNIQUE. 1:1 방은 NULL이다(E1). |
| deleted_at | TIMESTAMP NULL | 프로젝트 방의 논리 삭제 시각. 1:1 방은 NULL이다(E3). |

**chat_members** (`ChatMember`)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 대화 참여 ID |
| room_id | BIGINT FK | `chat_rooms.id`. UNIQUE(room_id, user_id) |
| user_id | BIGINT FK | `users.id`. 인덱스 추가 |
| last_read_at | TIMESTAMP NULL | 마지막 읽은 시각. 앞으로만 이동한다. |
| joined_at | TIMESTAMP | 참여 시각 |
| left_at | TIMESTAMP NULL | 퇴장 시각. 프로젝트 방의 퇴장과 강퇴에 쓴다. 1:1 방은 NULL이다. |

**messages** (`Message`)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 정렬과 커서 기준 |
| chat_member_id | BIGINT FK | 발신 참여자 `chat_members.id`. INDEX(chat_member_id, id) |
| content | TEXT | 1~2,000자 |
| sent_at | TIMESTAMP | 전송 시각 |
| deleted_at | TIMESTAMP NULL | 삭제 시각. 메시지 삭제 API는 범위 밖이다(1번). |

**이전 초안과 달라진 점**

| 이전 초안 | ERD | 처리 |
|---|---|---|
| `chat_messages`, `ChatMessage` | `messages`, `Message` | ERD대로 만들었다. |
| `body`, `created_at` | `content`, `sent_at` | ERD대로 만들었다. API의 `body`, `createdAt`은 DTO에서 변환한다. |
| 메시지의 `room_id`, `sender_id` | `chat_member_id` | 방과 보낸 사람은 참여자를 거쳐 찾는다. |
| `last_read_message_id` | `last_read_at` | API는 메시지 ID를 유지한다. `PATCH read`는 그 메시지의 `sent_at`을 저장하고, 안 읽은 수는 `sent_at > last_read_at`으로 센다. 응답의 읽음 위치는 `last_read_at`까지의 마지막 메시지 ID로 계산한다. |
| `last_message_id` | 없음 | 저장하지 않고 조회할 때 `MAX(messages.id)`로 구한다. 메시지가 없는 1:1 방은 목록에 표시하지 않는다. |
| `messages.kind`, `SYSTEM` 메시지 | 없음. 발신 참여자가 필수다. | 초대·퇴장 문구를 저장하지 않고 `joined_at`, `left_at`으로 표시한다(3.5). |
| `excluded_at` | 없음 | 강퇴도 `left_at`을 채운다(3.5). |
| `created_at`, `updated_at` (`BaseEntity`) | 없음 | 쓰지 않는다. |
| 없음 | `joined_at`, `messages.deleted_at` | ERD대로 추가했다. |

**ERD 대조 후 결정한 것**

처음 받은 ERD의 컬럼만으로는 구현할 수 없거나 보장이 약해지는 항목이었다. E1과 E3의 컬럼은 ERD에도 추가했다.

| # | 기능 | 결정 | 이유·제약 |
|---|---|---|---|
| E1 | 프로젝트와 방 연결 (7.18~7.23) | `chat_rooms.project_id`를 추가한다. | 이 컬럼이 없으면 프로젝트 ID로 방을 찾을 수 없다. UNIQUE로 프로젝트당 방 하나를 보장한다. |
| E2 | 두 회원 쌍당 1:1 방 하나 (11.5) | 컬럼을 추가하지 않고 잠금으로 처리한다(3.3). | 방은 `chat_members`로 찾을 수 있고 1:1 방을 만드는 경로는 11.5 하나뿐이다. DB 제약이 없어 이 경로를 벗어난 코드는 중복 방을 만들 수 있다. 잠금을 잡은 동안 같은 회원 행을 수정하는 요청은 기다린다. |
| E3 | 프로젝트 방 논리 삭제 (D10) | `chat_rooms.deleted_at`을 추가한다. | 메시지와 멤버 행을 바꾸지 않고 방 하나만 표시하면 되며, 방이 없는 프로젝트의 삭제도 기록할 수 있다. |

**reports** (`Report`)는 [청사진의 컬럼](project-backend-blueprint.md)을 그대로 쓰고, 청사진 2번의 공통 기준대로 `created_at`, `updated_at`을 상속한다. 1:1 신고는 `reporter_id`, `reported_user_id`, `reason`, `status`만 채운다. `status`는 `RECEIVED`, `REVIEWING`, `RESOLVED`, `REJECTED`이고 접수 시 `RECEIVED`다. 어느 대화에서 신고했는지는 컬럼이 없어 저장하지 않는다. 중복 신고의 DB 제약은 #51의 팀원 신고와 함께 정한다. 그 전까지는 신고자 행을 잠근 뒤 확인해 동시 요청의 중복을 막는다.

## 5. API 계약 초안

| 구분 | Method·경로 (`/api/v1` 생략) | 역할 |
|---|---|---|
| 11.1 | `GET /messages/chats?type=&q=&cursor=&size=` | 내 채팅방 목록. `type`은 `direct`(기본값)와 `project`이고 사이드바 탭마다 따로 호출한다(D11). `direct`는 메시지가 있는 방만 최근 순으로 반환하고 `q`는 상대 닉네임 검색이다. `project`는 3.5의 입장 가능한 방을 프로젝트 ID 역순으로 반환하고 `q`는 프로젝트명 검색이다. `size` 기본 30, 최대 100 |
| 11.2 | `GET /messages/chats/{id}?before=&after=&size=` | 메시지 조회. 파라미터가 없으면 최신 메시지, `before`는 이전 기록, `after`는 신호 수신·폴링·재접속 복구용이다. 결과는 항상 오름차순이다. `after` 결과가 `size`개이면 마지막 ID로 다시 조회한다. `size` 기본 50, 최대 100 |
| 11.3 | `POST /messages/chats/{id}` | 메시지 전송. `{ "body": "..." }` → 201 + 메시지. 보낸 사람의 읽음 위치도 옮긴다. |
| 11.4 | `POST /users/{slug}/reports` | 사용자 신고. `{ "reason": "스팸 또는 광고" }` → 201, 응답 본문 없음 |
| 11.5 | `POST /messages/chats` | 대화 시작. `{ "targetSlug": "..." }` → 200 `{ "id": 12 }`. 이미 있으면 기존 방을 반환한다. |
| 11.6 | `PATCH /messages/chats/{id}/read` | 읽음 처리. `{ "lastReadMessageId": 340 }` → 204. 서버는 그 메시지의 `sent_at`을 `last_read_at`에 저장한다. 다른 방의 메시지 ID는 거부하고, 더 이전 메시지는 무시한다. |
| 11.7 | `GET /messages/chats/unread-count` | 1:1 안 읽은 수. 헤더 배지에서 7.21과 합산한다(D12). `{ "count": 3 }` |
| 7.18 | 별도 API 없음 | 입장 버튼이 `/messages/projects/{id}`로 이동하고, 첫 접근 때 서버가 방을 만든다(D13). |
| 7.19 | `GET /messages/projects/{id}?before=&after=&size=` | 프로젝트 채팅 조회. 멤버, 읽음 위치, 메시지, 전송 가능 여부를 반환한다. |
| 7.19 | `POST /messages/projects/{id}` | 메시지 전송. `{ "body": "..." }` → 201 + 메시지 |
| 7.19 | `POST /messages/projects/{id}/delete` | 방 논리 삭제. 종료된 프로젝트의 주최자만 할 수 있다(3.5, D10). 요청 본문 없음 → 204. 이미 삭제된 방도 204다. |
| 7.20 | `PATCH /messages/projects/{id}/read` | 읽음 처리. 1:1과 같다. |
| 7.21 | `GET /messages/projects/unread-count` | 프로젝트 채팅의 안 읽은 수 합계(3.5). 헤더 배지에서 11.7과 합산한다(D12). `{ "count": 2 }` |

`GET /messages/chats?type=direct` 응답:

```json
{
  "items": [
    {
      "id": 12,
      "peer": { "tag": "sample_dev", "nickname": "샘플개발자", "profileImage": null, "jobCategories": ["백엔드"], "collaborationStatus": "AVAILABLE" },
      "lastMessage": { "id": 340, "body": "안녕하세요", "mine": false, "createdAt": "2026-11-06T10:15:00+09:00" },
      "unreadCount": 2
    }
  ],
  "nextCursor": null
}
```

`GET /messages/chats?type=project` 응답:

```json
{
  "items": [
    { "id": 5, "title": "샘플 프로젝트", "status": "IN_PROGRESS", "memberCount": 4, "unreadCount": 2 }
  ],
  "nextCursor": null
}
```

`GET /messages/chats/{id}` 응답:

```json
{
  "id": 12,
  "peer": { "tag": "sample_dev", "nickname": "샘플개발자", "profileImage": null, "jobCategories": ["백엔드"], "collaborationStatus": "AVAILABLE" },
  "canSend": true,
  "peerLastReadMessageId": 339,
  "messages": [
    { "id": 340, "body": "안녕하세요", "mine": false, "createdAt": "2026-11-06T10:15:00+09:00" }
  ],
  "nextBefore": null
}
```

`GET /messages/projects/{id}` 응답:

```json
{
  "projectId": 5,
  "title": "샘플 프로젝트",
  "status": "IN_PROGRESS",
  "canSend": true,
  "isHost": false,
  "members": [
    { "id": 31, "me": false, "tag": "sample_dev", "nickname": "샘플개발자", "profileImage": null, "role": "HOST", "lastReadMessageId": 339, "joinedAt": "2026-11-01T09:00:00+09:00", "leftAt": null }
  ],
  "messages": [
    { "id": 340, "body": "안녕하세요", "mine": false, "sender": { "memberId": 31, "tag": "sample_dev", "nickname": "샘플개발자" }, "createdAt": "2026-11-06T10:15:00+09:00" }
  ],
  "nextBefore": null
}
```

`members[].id`(참여 행 ID), `me`, `sender.memberId`는 14단계에서 더했다. 화면이 읽음 n/m에서 나를 빼고 태그가 없는 회원도 구분하는 데 쓴다. 확정 계약은 [메신저 API](messenger-api.md)에 옮겼다.

오류는 공통 [ErrorResponse](../backend/src/main/java/com/devmozip/backend/dto/common/ErrorResponse.java) 형식이다.

| 상황 | 상태 |
|---|---|
| 로그인하지 않음, 토큰 만료 | 401 |
| 없는 대화방, 참여하지 않은 대화방, 없는 대상, 대화를 시작할 수 없는 상태의 대상, 입장할 수 없거나 삭제된 프로젝트 채팅 | 404 |
| 주최자가 아닌 회원의 프로젝트 채팅 삭제 | 403 |
| 본인과의 대화·본인 신고, 본문 길이 위반, 없는 신고 사유, `before`와 `after` 동시 사용, 범위를 벗어난 `size`, 숫자가 아닌 파라미터, 지원하지 않는 `type`, 다른 방의 메시지 ID | 400 |
| 상대가 정지되어 전송 불가, 종료된 프로젝트 채팅에 전송, 종료 전 프로젝트 채팅 삭제, 중복 신고 | 409 |

회원 조회 기준은 다음과 같다.

- 대화 시작 대상은 프로필과 같은 `findByTagAndStatus(slug, ACTIVE)`로 찾는다.
- 이미 있는 대화의 전송은 상대가 SUSPENDED일 때만 막는다.
- 신고 대상은 상태와 무관하게 찾으므로 `MemberRepository`에 조회 메서드를 추가한다. 다른 회원 코드는 바꾸지 않는다.

### WebSocket 계약 초안

1:1(11.8, 11.9)과 프로젝트 단톡방(7.22, 7.23)이 연결 하나를 함께 쓰고 `roomType`으로 구분한다. `roomId`는 1:1이면 대화방 ID, 프로젝트면 프로젝트 ID로 화면 주소와 같다. 연결 주소는 `/ws/messages`이고, 프레임은 JSON이며 최대 4KB다. Redis 채널은 `messenger:signals`로, 받는 회원 ID와 아래 신호를 `{"recipients":[1,2],"frame":"{...}"}` 형식으로 담는다.

| 방향 | 프레임 | 의미 |
|---|---|---|
| 서버 → 클라이언트 | `{"type":"message.created","roomType":"direct","roomId":12,"messageId":341}` | 새 메시지. `after`로 본문을 조회한다. 보낸 사람의 다른 탭에도 전달한다. |
| 서버 → 클라이언트 | `{"type":"message.read","roomType":"direct","roomId":12,"lastReadMessageId":341}` | 읽음 위치가 실제로 옮겨졌을 때만 보낸다. 다른 참여자의 읽음 표시와 내 다른 탭의 안 읽은 수를 갱신한다. 전송으로 옮겨진 보낸 사람의 위치는 따로 보내지 않고 `after` 응답의 `peerLastReadMessageId`로 전달한다. 프로젝트 방은 읽은 사람의 `memberTag`를 함께 보낸다. 태그가 없는 회원이면 넣지 않는다. |
| 서버 → 클라이언트 | `{"type":"typing","roomType":"project","roomId":5,"nickname":"샘플개발자"}` | 상대가 입력 중이다. 1:1 방은 `nickname`이 없다. 닉네임이 없는 회원이면 넣지 않는다. 보낸 사람의 다른 탭에는 보내지 않는다. |
| 서버 → 클라이언트 | `{"type":"ping"}` | 연결 확인 |
| 클라이언트 → 서버 | `{"type":"typing","roomType":"direct","roomId":12}` | 입력 중. 보낸 사람은 세션의 회원으로 판단한다. `roomType`이 `direct`·`project`가 아니거나 `roomId`가 양의 정수가 아니면 1008로 닫는다. 참여하지 않았거나 보낼 수 없는 방이면 연결을 유지한 채 무시한다. |
| 클라이언트 → 서버 | `{"type":"pong"}` | 연결 확인 응답 |

| 종료 코드 | 상황 | 클라이언트 동작 |
|---|---|---|
| 1001 | 서버 종료, 배포 | 재접속 |
| 4401 | 토큰 만료 | `/api/auth/session` 호출 후 재접속. 실패하면 로그인 안내 |
| 4403 | 정지 회원 | 재접속하지 않음 |
| 4500 | `pong` 없음, 느린 연결 | 재접속 |
| 1008, 1009 | 허용되지 않은 프레임, 크기 초과 | 재접속 |
| 1006 | 핸드셰이크 거부(상태 코드를 볼 수 없다), 네트워크 끊김 | 연결이 열리기 전이면 `/api/auth/session`을 호출한 뒤 재접속. `identity`가 없으면 로그인 안내 |

## 6. 단계별 수행 내용

백엔드(1~4)가 끝나야 프론트(5~7)를 실제 서버로 검증할 수 있다. 0단계에서 계약을 확정하면 프론트는 응답 fixture로 먼저 진행할 수 있다. 실시간(8~11)은 REST(1~7)가 끝난 뒤 진행한다. 본문 조회와 재접속 복구가 `after` 조회와 폴링에 의존하기 때문이다. 프로젝트 채팅(12·14)은 같은 실시간 경로를 쓰고 #48 병합이 필요해 그 뒤에 진행한다. nginx·블루그린 경로(13)는 main 배포를 마지막에 하므로 14단계 바로 앞에 둔다.

| 단계 | 내용 | WBS | 수행 주체 | 상태 |
|---|---|---|---|---|
| 0 | 범위 승인·결정·ERD 대조 | 전체 | 사용자 + 구현 | 완료 |
| 1 | 테이블·엔티티·리포지토리 | 공통 | 구현 | 완료 |
| 2 | 대화 시작·목록·안 읽은 수 API | 11.1, 11.5, 11.7 | 구현 | 완료 |
| 3 | 메시지 조회·전송·읽음 API | 11.2, 11.3, 11.6 | 구현 | 완료 |
| 4 | 사용자 신고 API | 11.4 | 구현 | 완료 |
| 5 | BFF·프론트 API 클라이언트 | 전체 | 구현 | 완료 |
| 6 | 목록·대화 화면 연결과 폴링 | 11.1~11.3, 11.6, 11.7 | 구현 | 완료 |
| 7 | 신고 화면·대화 시작 경로 | 11.4, 11.5 | 구현 | 완료 |
| 8 | WebSocket 서버 (연결·인증·세션) | 11.8, 7.22 | 구현 | 완료 |
| 9 | Redis Pub/Sub 전달과 메시지·읽음 신호 | 11.8, 7.22 | 구현 | 완료 |
| 10 | 화면 실시간 연결 | 11.8 | 구현 | 완료 |
| 11 | 입력 중 표시 | 11.9 | 구현 | 완료 |
| 12 | 프로젝트 채팅 API | 11.1, 7.18~7.23 | 구현 | 완료 (#48 병합 후 재검증) |
| 13 | nginx·블루그린 경로 | 11.8, 7.22 | 구현 + 서버 관리자 | 완료 (서버 작업은 16단계) |
| 14 | 프로젝트 채팅 화면 | 11.1, 7.18~7.23 | 구현 | 완료 (#48 병합 후 재검증) |
| 15 | 통합 검증·문서 정리 | 전체 | 구현 + 사용자 | 자동 확인 완료, 사용자 수동 확인 대기 |
| 16 | 운영 배포 | 전체 | main 병합 후 별도 진행 | 이번 작업에서 제외 |

모든 단계의 공통 종료 조건은 다음과 같다.

- `frontend`에서 `npm run build`와 관련 테스트를 실행한다. 백엔드를 변경한 단계는 `backend`에서 `./gradlew test`도 실행한다.
- 통합 테스트는 `@Testcontainers(disabledWithoutDocker = true)`이므로 Docker가 없으면 건너뛴다. 건너뜀을 통과로 보고하지 않는다.
- 실행 명령, 성공·실패, 실행하지 못한 이유를 보고한다. Mock 테스트 통과를 실제 DB 검증으로 보고하지 않는다.
- 이 문서의 상태 표와 8번 진행 기록을 갱신한다.

### 0단계: 범위 승인·결정·ERD 대조

- [x] 3.2의 D1~D13을 확정하고 이 문서의 3~5번을 고쳤다.
- [x] [AGENTS.md](../AGENTS.md)의 구현 범위에 메신저, 프로젝트 단톡방, WebSocket, Redis Pub/Sub 사용을 추가하고 이 문서를 "먼저 읽을 문서"에 넣었다.
- [x] 이슈 #78을 등록하고 `feat/78-messages` 브랜치를 만들었다.
- [x] ERD 이미지를 받아 채팅 테이블을 대조했다. 결과는 4번에 있다(D8).
- [x] 다른 담당자에게 알릴 내용은 별도 합의 단계 대신 PR에 적기로 했다(15단계).
- [x] 호스트 nginx 파일을 수정했다. 배포 때 작업 담당자가 reload한다(16단계).
- [x] WBS 갱신을 마무리했다(사용자 수행).

**완료 조건:** 위 항목이 끝나고 사용자가 1단계 시작을 요청한다.

### 1단계: 테이블·엔티티·리포지토리

- [x] `entity`에 `ChatRoom`, `ChatMember`, `Message`를 추가했다. 컬럼은 ERD와 같고 `BaseEntity`를 쓰지 않는다(4번).
- [x] `chat_rooms`에 `project_id`, `deleted_at`을 추가했다(E1, E3). `direct_key`는 추가하지 않는다(E2).
- [x] `repository`에 `ChatRoomRepository`, `ChatMemberRepository`, `MessageRepository`를 추가했다. 조회 메서드는 각 API 단계에서 추가한다.
- [x] 유일 제약과 인덱스를 `@Table`에 선언했다. 제약 없이 이미 있는 테이블에도 `ddl-auto: update`가 제약·인덱스·외래 키를 추가하는 것을 확인했다.
- [x] 생성된 DDL을 PostgreSQL 컨테이너에서 확인하고 `local`(H2) 프로필 기동을 확인했다.

**주의:** `ddl-auto: update`이므로 `.env`가 원격 DB를 가리키면 백엔드를 띄우는 순간 공용 DB에 테이블이 생긴다. compose의 개발 백엔드는 `backend` 폴더를 마운트해 `bootRun`으로 실행하므로, 컨테이너를 재시작하면 작업 중인 엔티티가 그대로 반영된다. 스키마가 확정되기 전에는 로컬 DB로만 실행한다.

**완료 조건:** 제약과 인덱스가 설계대로 만들어지고 기존 테스트가 통과한다.

### 2단계: 대화 시작·목록·안 읽은 수 API (11.1, 11.5, 11.7)

- [x] `dto/message`, `service`의 메시지 서비스, `controller`의 `MessageController`를 만들었다.
- [x] `POST /messages/chats`: 대상 확인, 본인 차단, 기존 방 반환, 동시 생성 처리(3.3).
- [x] `GET /messages/chats`(`type=direct`): 메시지 있는 방만 최근 순, 상대 정보·마지막 메시지·안 읽은 수, `q`, 커서. `type=project`는 12단계에서 추가하며 그 전까지 400이다.
- [x] `GET /messages/chats/unread-count`.
- [x] 경로 충돌을 막기 위해 `{id:[0-9]+}`를 쓴다.
- [x] 테스트: 401, 대상 없음·본인, 동시 요청에도 방 1개, 빈 방 제외, 정렬·검색·커서, 내가 보낸 메시지는 안 읽은 수에서 제외, 방 수와 무관한 목록 조회 횟수.

**완료 조건:** 두 회원 사이에 방이 하나만 생기고, 내 대화만 목록에 나타난다.

### 3단계: 메시지 조회·전송·읽음 API (11.2, 11.3, 11.6)

- [x] `GET /messages/chats/{id}`: 최신·`before`·`after` 조회, `peerLastReadMessageId`, `canSend`.
- [x] `POST /messages/chats/{id}`: 방 잠금, 본문 검증, 메시지 저장, 보낸 사람의 `last_read_at` 갱신을 한 트랜잭션으로 처리한다. `sent_at`은 3.3의 기준으로 정한다.
- [x] `PATCH /messages/chats/{id}/read`: 같은 방의 메시지인지 확인하고 그 메시지의 `sent_at`을 `last_read_at`에 저장한다. 앞으로만 이동한다.
- [x] 테스트: 비참여자 404(조회·전송·읽음 모두), 본문 0·1·2,000·2,001자와 공백만, `before`·`after` 경계, 읽음 역행 무시, 다른 방 메시지 ID 거부, 상대 정지 시 전송 409, 연속·동시 전송의 `sent_at` 순서, 읽음 경계의 안 읽은 수, 프로젝트 방 접근 404.

**완료 조건:** 참여자만 읽고 쓸 수 있고, `after` 폴링이 메시지를 빠뜨리지 않는다.

### 4단계: 사용자 신고 API (11.4)

- [x] 시작 전에 #51의 PR이 생겼는지 확인했다(D6). PR과 원격 브랜치에 `reports` 코드가 없어 `Report` 엔티티·리포지토리를 청사진 컬럼대로 추가했다.
- [x] `POST /users/{slug}/reports`: 본인 차단, 사유 검증, 중복 신고 409. 사유 목록의 기준은 서버의 `UserReportReason`이다.
- [x] 테스트: 401, 본인, 없는 대상, 잘못된 사유, 중복, 동시 중복, 정지 회원 신고, 저장 값 확인.

**완료 조건:** 신고가 `reports`에 저장된다. 관리자 처리(14.8)는 포함하지 않는다.

### 5단계: BFF·프론트 API 클라이언트

- [x] [frontend/AGENTS.md](../frontend/AGENTS.md)대로 작성 전에 `node_modules/next/dist/docs/`의 Route Handler 안내를 확인했다.
- [x] `lib/messaging/messagingProxy.ts`: `backendFetch` 기반의 서버 전용 프록시, Origin 확인, 상태별 안내 문구.
- [x] `app/api/messages/chats/route.ts`, `[id]/route.ts`, `[id]/read/route.ts`, `unread-count/route.ts`, `app/api/users/[slug]/reports/route.ts`.
- [x] `lib/messaging/directChatApi.ts`: 브라우저 호출과 응답 검증([profileDetail](../frontend/lib/profiles/profileDetail.ts) 방식). 타입은 `types/messaging.ts`에 추가했다.
- [x] 테스트: 경로별 인증 전달, Origin 거부, JSON 형식, 길이 사전 차단, 오류 변환, 잘못된 응답 거부.

**완료 조건:** 토큰이 브라우저 JavaScript에 노출되지 않고 모든 API를 BFF로 호출할 수 있다.

### 6단계: 목록·대화 화면 연결과 폴링 (11.1~11.3, 11.6, 11.7)

- [x] 토글에 따라 출처를 고른다(D5). ON은 기존 `useMessaging`을 그대로 쓰고, OFF는 서버 데이터를 쓴다. 대화 영역은 [useConversationPane](../frontend/app/messages/chats/_hooks/useConversationPane.ts), 사이드바는 [useDirectConversations](../frontend/app/messages/_hooks/useDirectConversations.ts)에서만 출처를 고른다. 토글을 바꾸면 [DirectChatSync](../frontend/app/_components/DirectChatSync.tsx)가 불러온 서버 데이터를 비운다.
- [x] `lib/state/directChatStore.ts`: 목록·열린 대화·안 읽은 수와 폴링을 담당한다. 폴링은 `lib/messaging/directChatPoller.ts`가 이전 요청이 끝난 뒤 다음 타이머를 걸어 요청이 겹치지 않게 한다.
- [x] 폴링 규칙: 열린 대화 5초, 목록·배지 30초. 탭이 숨겨지면 멈추고 돌아오면 바로 조회한다. 실패하면 간격을 늘린다. 401이면 멈추고 로그인으로 안내한다.
- [x] [useActiveConversation](../frontend/app/messages/chats/_hooks/useActiveConversation.ts), [ChatsLayout](../frontend/app/messages/chats/_components/ChatsLayout.tsx), [대화 화면](../frontend/app/messages/chats/page.tsx)을 서버 데이터로 바꿨다. 주소에 대화가 없으면 가장 최근 대화로 이동해, 목록 순서가 바뀌어도 보던 대화가 바뀌지 않는다.
- [x] `ChatWindow`를 확장했다. 전송을 비동기로 받고 대기 중 중복 전송을 막는다. 작성자 이름·링크와 하단 문구를 prop으로 받는다. 프로젝트 채팅은 넘기지 않아 지금과 같다.
- [x] 메시지는 ID로 병합해 폴링과 전송 응답의 중복을 없앤다.
- [x] 이전 메시지 더 보기를 추가했다. 이전 기록을 불러올 때와 사용자가 위로 올려 읽는 중에는 맨 아래로 스크롤하지 않는다. 서버 대화는 페이지 전체를 움직이지 않고 대화 영역 안에서만 스크롤한다.
- [x] 화면에 보인 메시지까지 `PATCH read`를 호출한다. 탭이 숨겨졌거나 위로 올려 읽는 동안에는 호출하지 않는다.
- [x] `ConversationSidebar`의 1:1 탭을 서버 목록으로 바꾸고 검색은 지연 입력 후 `q`로 조회한다. 프로젝트 탭은 그대로 둔다.
- [x] `AppLayout`의 배지는 토글 OFF에서 `unread-count`를 쓴다. 프로젝트 채팅 합산은 14단계에서 한다.
- [x] `messagingService`와 기존 localStorage 내용은 Mock 경로로 그대로 둔다.
- [x] 테스트: 토글 OFF의 1:1 화면 테스트를 API fixture로 추가했다. [MessagesPage.test.tsx](../frontend/tests/screens/MessagesPage.test.tsx)의 기존 테스트는 그대로 통과한다.

**완료 조건:** 토글 OFF에서 서로 다른 브라우저의 두 회원이 메시지를 주고받고 읽음과 배지가 갱신된다. 토글 ON과 프로젝트 채팅은 전과 같이 동작한다.

### 7단계: 신고 화면·대화 시작 경로 (11.4, 11.5)

- [x] `UserReportDialog`가 제출 동작을 prop으로 받게 했다. 토글 OFF는 API를 호출하고 ON은 기존 Mock을 유지한다. "실제 관리자 서버에는 전송하지 않습니다" 문구는 ON에만 남긴다.
- [x] `ApiProfileHeader`에 메시지 보내기 버튼을 추가했다. 실제 회원끼리의 대화이므로 토글 OFF에서만 표시한다. 본인 프로필에는 표시하지 않고 비로그인이면 로그인으로 보낸다. 누르면 `POST /messages/chats` 후 `/messages/chats/{id}`로 이동한다.
- [x] 토글 OFF의 메시지 작성자·상대 링크를 `/users/{tag}`로 바꿨다. 상대 표시에는 [profileDetail](../frontend/lib/profiles/profileDetail.ts)의 `collaborationLabel`, `profileImageUrl`을 재사용한다. `tag`가 없는 상대는 링크와 신고 버튼을 표시하지 않는다(7번 참조).
- [x] [프로젝트 상세](../frontend/app/projects/[projectId]/page.tsx)의 주최자 연락 링크는 토글 OFF에서 대화 목록으로 보낸다(7번 참조). ON은 지금 동작을 유지한다.
- [x] 테스트: 신고 성공·중복·실패 표시, 버튼의 본인·비로그인·토글 ON 분기, 대화 시작 후 이동.

**완료 조건:** 실제 프로필에서 대화를 시작할 수 있고 신고가 서버에 저장된다.

### 8단계: WebSocket 서버 (연결·인증·세션) (11.8, 7.22)

- [x] `build.gradle`에 `spring-boot-starter-websocket`을 버전 없이 추가한다.
- [x] `config`에 WebSocket 설정을 만든다. `/ws/messages`, 허용 Origin. 최대 프레임 4KB는 연결마다 정한다(8번 기록 참고).
- [x] 핸드셰이크 인터셉터를 만든다. 쿠키의 토큰 검증, 정지 회원 거부, 회원 ID를 세션 속성에 저장.
- [x] `SecurityConfig`에서 `/ws/messages`만 허용하고 `JwtProvider`에 만료 시각 조회를 추가한다. 공통 코드이므로 0단계에서 공유한 범위 안에서만 바꾼다.
- [x] 세션 저장소를 만든다. 회원별 여러 연결 관리, 전송 직렬화, 서버 종료 시 1001로 먼저 닫기.
- [x] `ping`·`pong` 처리와 토큰 만료 시 4401 종료를 구현한다.
- [x] 테스트: 실제 WebSocket 클라이언트로 쿠키 없음·위조·만료·정지·다른 Origin 거부, 정상 연결, 같은 회원의 여러 탭 동시 연결, 크기 초과, 잘못된 JSON.

**완료 조건:** 로그인한 회원만 허용된 Origin에서 연결할 수 있고, 토큰이 만료되면 연결이 닫힌다.

### 9단계: Redis Pub/Sub 전달과 메시지·읽음 신호 (11.8, 7.22)

- [x] `MessengerSignalPublisher` 인터페이스와 Redis·메모리 구현을 만든다. 3.4의 설정으로 고른다.
- [x] 메시지 전송과 읽음 처리의 커밋 후 이벤트(`@TransactionalEventListener`)에서 신호를 발행한다. 신호에 본문을 넣지 않는다.
- [x] 발행은 대기열이 제한된 전용 실행기에서 처리한다. 실패하거나 대기열이 차면 같은 서버의 세션으로 직접 전달한다.
- [x] `RedisMessageListenerContainer`로 구독해 자기 세션에 연결된 회원에게만 전달한다.
- [x] 발행 실패 로그는 1분에 한 번만 남기고 신호 내용을 넣지 않는다.
- [x] 테스트: Redis 컨테이너로 신호 전달 확인, 커밋 실패 시 신호 없음, Redis 중단 중 전송 응답이 늦어지지 않음과 같은 서버 직접 전달, Redis 재시작 후 구독 복구, 수신자 외 회원에게 전달되지 않음.

**완료 조건:** 메시지와 읽음 신호가 두 참여자에게 전달되고, Redis가 멈춰도 전송이 정상 동작한다.

### 10단계: 화면 실시간 연결 (11.8)

- [x] 로컬 HTTP 화면이 백엔드에 직접 연결하도록 `NEXT_PUBLIC_MESSENGER_WS_PORT` 설정 방법을 [frontend/.env.example](../frontend/.env.example)에 안내했다(3.4). 처음 계획한 `compose.override.yaml`은 Git에서 빠져 있어(루트 `.gitignore`의 `*.yaml`) 팀에 전달되지 않는다. develop이 같은 파일 끝에 다른 값을 추가해 병합 충돌을 피하려고 파일 맨 위에 두었다.
- [x] [messengerSocket.ts](../frontend/lib/messaging/messengerSocket.ts)를 만들었다. 프로젝트 채팅도 같은 연결을 쓰므로 계획의 `directChatSocket.ts` 대신 이 이름으로 정했다. 연결, 재접속, `ping`·`pong`, 종료 코드별 처리, 3.4의 주소 규칙. 열리기 전의 실패(1006)와 4401은 `/api/auth/session`으로 세션을 갱신·확인한 뒤 재접속하고, 세션이 없으면 로그인 안내 상태로 바꾼다. 4403은 다시 연결하지 않는다.
- [x] 로그인 회원이고 토글이 OFF일 때만 연결하는 [MessengerSocketSync](../frontend/app/_components/MessengerSocketSync.tsx)를 `app/layout.tsx`에 넣었다. 로그아웃하거나 토글을 ON으로 바꾸면 연결을 닫는다. 탭별 연결과 구독은 [messengerSocketStore](../frontend/lib/state/messengerSocketStore.ts)가 맡는다.
- [x] `directChatStore`에서 신호를 처리한다. `message.created`는 열린 대화면 `after`로 조회하고 아니면 목록·배지를 갱신한다. 짧은 시간의 신호는 요청 중이면 한 번의 재조회로 묶는다. `message.read`는 상대 읽음 위치를 앞으로만 옮기고 안 읽은 수를 다시 읽는다. 1:1 신호에는 읽은 사람이 없고 내 다른 탭도 받는다. 읽음 위치가 옮겨지는 메시지는 항상 상대가 보낸 것이고 상대의 위치도 그 메시지까지 와 있으므로, 내가 읽어서 온 신호로 갱신해도 값이 틀리지 않는다. 프로젝트 신호는 14단계에서 처리한다.
- [x] 연결 상태에 따라 폴링 주기를 바꾼다(3.4). 연결되면 놓친 메시지를 바로 조회하고 열린 대화·목록·배지를 60초마다 조회한다. 끊기면 바로 5초·30초 주기로 되돌린다. 숨겨진 탭도 연결을 유지하고 신호로 조회하되 `PATCH read`는 미룬다. 폴러에 주기 함수, `poke({ evenIfHidden })`, `reschedule()`을 추가했다.
- [x] 테스트: 가짜 WebSocket으로 재접속 지연, 4401 흐름, 4403 중지, 신호 처리, 폴링 전환. `MessengerSocketSync`의 로그아웃·토글 ON 종료는 화면 효과라 단위 테스트 대신 실제 브라우저로 확인했다(8번).

**완료 조건:** 두 브라우저에서 메시지와 읽음 표시가 바로 반영되고, WebSocket을 끊어도 폴링으로 계속 동작한다.

### 11단계: 입력 중 표시 (11.9)

- [x] 서버에서 `typing` 프레임을 처리한다([MessengerTypingRelay](../backend/src/main/java/com/devmozip/backend/websocket/MessengerTypingRelay.java)). 참여자 확인, 전송 가능 여부 확인, 회원·방당 1초 간격 제한, 상대에게만 전달. 참여 여부는 연결마다 10초 기억한다. 신호는 9단계의 전용 스레드와 Redis 경로를 그대로 쓰고, 트랜잭션이 없으므로 디스패처에 바로 보내는 `dispatch`를 추가했다. 조회가 실패해도 연결을 유지한다.
- [x] `ChatWindow`에 `typing` prop(`text`, `onTyping`)을 추가했다. 토글 ON에서는 넘기지 않아 동작이 바뀌지 않는다. 프로젝트 채팅은 14단계에서 연결한다.
- [x] 입력창이 바뀌면(빈 값 제외) 같은 대화에 3초에 1회 보낸다. 연결이 없거나 `canSend`가 false면 보내지 않는다. 받는 화면은 상대 메시지 도착 시 또는 마지막 신호 5초 뒤 표시를 지운다.
- [x] 표시는 `role="log"` 영역 밖의 높이 고정 영역에 두어 스크린 리더가 기록을 반복해서 읽지 않게 하고, 표시될 때 레이아웃이 흔들리지 않게 했다. 이후 1:1은 기록 안의 말풍선으로 바꿨다(8번 11-08 1:1 입력 중 말풍선).
- [x] 테스트: 비참여 방의 프레임 무시, 본인 화면에 표시되지 않음, 횟수 제한, 자동 해제, 토글 ON에서 표시되지 않음, 형식이 틀린 프레임 1008, Redis를 거친 다른 서버 전달.

**완료 조건:** 상대가 입력하면 대화 화면에 표시되고, 입력을 멈추면 사라진다.

### 12단계: 프로젝트 채팅 API (11.1, 7.18~7.23)

**선행:** #48(PR #85)과 실제 회원 ID 연결(PR #76)이 develop에 병합돼야 실제 승인 팀원으로 검증할 수 있다. 그 전에는 테스트 데이터로 개발하고, 병합 후 다시 검증한다.

- [x] 3.5의 규칙으로 입장 판단과 방 생성을 구현했다([ProjectChatService](../backend/src/main/java/com/devmozip/backend/service/ProjectChatService.java)). 동시 첫 접근은 프로젝트 행 잠금으로 한 줄로 세운다(3.5).
- [x] `GET /messages/chats?type=project`, `GET`·`POST /messages/projects/{id}`, `POST /messages/projects/{id}/delete`, `PATCH /messages/projects/{id}/read`, `GET /messages/projects/unread-count`를 만들었다. 목록은 기존 `MessageController`가 `type`으로 나눠 부른다.
- [x] `chat_members`와 `project_members`를 비교해 참여 행을 만들거나 `left_at`을 채운다. 바꿀 것이 있을 때만 방을 잠그고 다시 읽어 중복을 막는다. 같은 시각에 들어온 회원은 회원 ID 순으로 만든다. 초대·퇴장 문구는 이 값으로 표시한다(3.5).
- [x] #49가 호출할 `ProjectChatService.excludeMember(projectId, memberId)`를 만들었다.
- [x] 메시지·읽음·입력 중 신호를 나가지 않은 참여자에게 보낸다. 9·11단계의 경로를 재사용한다. 1:1과 프로젝트 공통 규칙은 `MessengerRules`로 옮겼다.
- [x] `ProjectMemberRepository` 대신 메신저 쪽 읽기 전용 `ProjectChatAccessRepository`에 조회를 두었다(3.5). 프로젝트 코드는 바꾸지 않았다.
- [x] 테스트: 비멤버·LEFT·제외 회원 404, 모집 시작 전 입장 불가, 방 1개만 생성, 참여 행 중복 없음(방이 없을 때와 있을 때), COMPLETED 전송 409, 삭제 권한과 시점, 논리 삭제 후 목록 제외·접근 404·입력 중 신호 거부·재생성 없음·메시지 행 유지, 방이 없는 프로젝트의 삭제, 삭제된 프로젝트 404, `type`별 목록 구분, 안 읽은 수 합계(삭제된 방·LEFT·제외 회원 제외, 승인 전 메시지 포함), 대화방 수와 무관한 신호 전달, 목록 조회 횟수 고정. #48 병합 전이라 승인 팀원은 #48과 같은 결과의 행을 SQL로 넣었다.

**완료 조건:** 승인된 팀원만 프로젝트 채팅을 읽고 쓸 수 있고, 프로젝트 코드를 변경하지 않는다.

### 13단계: nginx·블루그린 경로 (11.8, 7.22)

새 포트는 열지 않는다. 운영의 외부 진입점은 443 하나로 유지하고, 이 경로만 내부의 백엔드 8080으로 보낸다. main 배포는 메신저 작업을 모두 마친 뒤 한 번에 하므로(16단계), 아래 스크립트와 경로 변경이 같은 배포에 들어간다. 다음 두 가지를 스크립트에서 처리한다.

- `server.conf`는 파일 하나만 마운트돼 있어, `git pull`로 파일이 교체되면 `nginx -s reload`로는 새 내용을 읽지 못하고 재시작해야 한다. 컨테이너가 다시 시작될 때는 디스크의 파일을 읽는다.
- 새 `server.conf`와 이전 `active.conf`(백엔드 업스트림 없음)가 만나면 `nginx -t`가 실패하고, 이 상태로 재시작하면 사이트 전체가 멈춘다. 이전 `server.conf`와 새 `active.conf`의 조합은 정상이다. 배포 명령은 `git pull` 뒤에 스크립트를 실행하므로, 스크립트가 `active.conf`를 늦게 쓰면 그동안 디스크에 이 조합이 남는다. 헬스체크가 실패해 스크립트가 끝나면 다음 nginx 재시작 때 사이트가 멈춘다.

**스크립트**

- [x] [blue-green.sh](../deploy/blue-green.sh)의 `write_active`가 아래 업스트림도 쓰게 한다. `active_color`는 프론트 줄만 읽으므로 그대로 동작한다.
- [x] `deploy`와 `rollback`의 시작 부분에서 활성 색 기준으로 `write_active`를 실행한다. 헬스체크가 실패해 끝나도 디스크의 두 파일이 맞는다. 활성 색이 없는 첫 배포는 기존처럼 헬스체크 뒤에 쓴다.
- [x] `nginx/server.conf`와 컨테이너 안 설정의 해시가 다르면 전환 전에 nginx를 재시작한다. `rollback`도 같다. 재시작 중에는 전체 트래픽이 잠깐 끊긴다.
- [x] [test_blue_green.py](../scripts/test_blue_green.py)를 함께 고쳤다. 가짜 docker·curl로 업스트림, 이전 형식의 `active.conf` 갱신, 설정이 바뀐 때만 재시작, 핸드셰이크 확인을 시험한다(12개). CI가 실행한다.

```nginx
upstream backend_upstream {
    zone backend_upstream 64k;
    resolver 127.0.0.11 valid=5s;
    server backend-$1:8080 resolve;
}
```

**경로**

- [x] 운영 서버의 호스트 nginx 파일에 경로를 넣었다.
- [ ] 배포 전에 호스트 nginx를 reload한다(16단계). 팀 nginx 적용 전에는 프론트로 전달돼 404가 되며 다른 동작에 영향이 없다.
- [x] [nginx/server.conf](../nginx/server.conf)의 `location /` 위에 아래 경로를 추가한다. 정확히 일치하는 경로만 백엔드로 보내 `/api/v1`과 `/actuator`를 노출하지 않는다.

```nginx
location = /ws/messages {
    proxy_pass http://backend_upstream;
    proxy_http_version 1.1;
    proxy_set_header Upgrade           $http_upgrade;
    proxy_set_header Connection        "upgrade";
    proxy_set_header Host              $http_host;
    proxy_set_header X-Real-IP         $http_x_real_ip;
    proxy_set_header X-Forwarded-For   $http_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
    proxy_set_header X-Forwarded-Host  $http_x_forwarded_host;
    proxy_read_timeout 75s;
    proxy_send_timeout 75s;
}
```

- [x] [setup-host.sh](../deploy/setup-host.sh)의 템플릿에 16단계의 호스트 경로를 추가한다. 새 서버를 구성할 때만 쓰인다.
- [x] [nginx/local.conf](../nginx/local.conf)에 경로를 추가한다. 대상은 `http://backend:8080`, 헤더는 기존 `location /`과 같은 값을 쓴다. 이 경로로 연결하려면 backend의 `DOMAINNAME`이 `https://localhost`여야 한다.
- [x] 로컬에서 `SKIP_PULL=1`로 블루그린 전환과 롤백을 시험한다(8번 기록). 로컬 개발 compose와 프로젝트 이름(`devmozip`)이 같으므로 다른 프로젝트 이름과 포트를 쓴다. 다음 경우를 함께 확인한다.
  - develop의 스크립트·설정으로 띄운 상태에서 이 작업의 커밋으로 바꿔 한 번에 배포한다.
  - 같은 상황에서 헬스체크가 실패한 뒤 nginx를 재시작해도 nginx가 뜬다.
  - 배포 뒤 이전 커밋으로 되돌려 배포하면 전환이 실패하고 기존 색이 계속 응답한다(16단계의 되돌리기).
- [x] 폴링이 장애를 가리므로 배포 스크립트에 핸드셰이크 확인을 넣는다. Origin을 `DOMAINNAME`으로 넣고 쿠키 없이 요청해 401이면 정상, 404면 경로가 적용되지 않은 것이다. Origin이 없으면 403이 온다. 실패하면 배포는 유지하고 종료 코드 3으로 끝나 배포 작업이 실패로 표시된다. 되돌리는 `rollback`에서는 확인하지 않는다.

**완료 조건:** nginx를 거쳐 WebSocket이 연결되고, 색을 전환해도 활성 색의 백엔드로 재접속한다. `/ws/messages` 외의 경로는 백엔드로 전달되지 않는다. develop의 설정에서 한 번에 배포해도, 그 배포의 헬스체크가 실패한 뒤 nginx가 재시작돼도 사이트가 멈추지 않는다.

### 14단계: 프로젝트 채팅 화면 (11.1, 7.18~7.23)

- [x] `app/api/messages/projects`의 BFF 경로와 API 클라이언트([projectChatApi](../frontend/lib/messaging/projectChatApi.ts))를 추가했다. `[id]/delete`와 `unread-count`도 포함한다. 목록 BFF(`/api/messages/chats`)는 `type`에 맞는 형식으로 응답을 확인한다. 삭제는 API 클라이언트(`deleteProjectChat`)까지 만들고 화면에는 연결하지 않았다.
- [x] [프로젝트 채팅 화면](<../frontend/app/messages/projects/[projectId]/page.tsx>)이 토글 OFF에서 서버 데이터를 쓴다([ServerProjectChat](<../frontend/app/messages/projects/[projectId]/_components/ServerProjectChat.tsx>), [projectChatStore](../frontend/lib/state/projectChatStore.ts)). ON은 지금 코드 경로를 그대로 쓴다. 상태 저장소는 1:1과 같은 주기·신호 규칙이다.
- [x] `ConversationSidebar`의 프로젝트 탭을 토글 OFF에서 `GET /messages/chats?type=project`로 바꿨다. 탭이 보이는 동안만 조회하고 프로젝트명으로 검색한다.
- [x] `AppLayout`의 배지를 토글 OFF에서 1:1과 프로젝트의 합계로 바꾸고(D12), 안내 문구를 "안 읽은 메시지"로 바꿨다. 프로젝트 방의 신호로도 배지를 갱신한다. ON은 1:1만 세고 문구도 그대로 둔다. 동기화는 기존 `DirectChatSync`가 두 저장소를 함께 시작한다.
- [x] 멤버 목록, 읽음 `n/m` 표시, 입력 중인 사람(여러 명), 사용자 신고를 서버 데이터로 연결했다. 읽음 수와 작성자 구분을 위해 조회 응답에 `members[].id`, `me`, `sender.memberId`를 더했다(5번). 초대·퇴장 문구는 `joinedAt`, `leftAt`으로 만들고, 방이 생길 때 함께 들어온 팀원은 문구를 붙이지 않는다.
- [x] 강퇴 투표 패널은 #49에서 토글 OFF에 연결했다. 날짜 시뮬레이터는 Mock 전용으로 유지한다.
- [x] 종료된 프로젝트의 읽기 전용 표시를 연결하고, 삭제된 방은 입장 거부 화면으로 보여 준다. 삭제 선택 화면은 연결하지 않는다(3.5).
- [x] 테스트: 토글 OFF의 입장 거부·멤버·메시지·삭제된 방·배지 합산, 토글 ON의 1:1 배지와 기존 프로젝트 채팅 테스트 유지. 저장소·화면 변환·BFF 테스트도 더했다.

**완료 조건:** 토글 OFF에서 팀원들이 프로젝트 채팅을 실시간으로 주고받고, 안 읽은 수가 헤더 배지에 합산된다. 토글 ON은 전과 같이 동작한다.

### 15단계: 통합 검증·문서 정리

- [x] `backend`에서 `./gradlew test`, `frontend`에서 `npm test`와 `npm run build`를 실행한다(8번).
- [x] 로컬 compose(PostgreSQL·Redis)와 `local`(H2, Redis 없음) 프로필에서 각각 실행한다. compose는 저장소의 개발용 compose 파일을 스크래치로 복사해 포트만 바꿔 띄웠다. 이때 메신저의 쓰기 요청이 모두 403이 되는 문제를 찾아 고쳤다(8번).
- [x] 브라우저 두 개로 서로 다른 데모 계정에 로그인하고 Mock 토글을 OFF로 바꿔 확인한다. 자동 브라우저 시험으로 아래 항목을 확인했다(8번). 사용자의 수동 확인은 남아 있다.
  - 프로필에서 대화 시작, 첫 메시지 전송, 상대 화면에 바로 표시
  - 입력 중 표시, 배지·목록 갱신, 1:1과 프로젝트 채팅의 배지 합산, 읽음 표시, 새로고침 후 유지, 이전 메시지 더 보기
  - 한 브라우저에서 대화 탭을 HTTP 연결 제한(6개)보다 많이 열어, 모든 탭의 실시간 수신과 다른 페이지 응답이 정상인지 확인
  - 제3의 계정으로 대화방 URL 직접 접근 시 차단
  - 사용자 신고와 중복 신고
  - 토큰 만료 후 재접속, 프로젝트 채팅의 승인·취소·종료 흐름, 방 삭제(화면 연결 전에는 API로 확인)
  - 토글을 ON으로 바꿨을 때 기존 Mock 1:1·프로젝트 채팅·알림 회귀
- [x] 장애 상황을 확인한다(8번).
  - WebSocket을 끊었을 때 폴링으로 계속 동작하고 재접속 후 놓친 메시지가 나타남
  - Redis를 멈췄을 때 전송 응답이 늦어지지 않음. 3.4의 표대로 동작함
  - Redis를 다시 켰을 때 구독 복구
  - 블루그린 전환 중 재접속과 메시지 누락 없음
- [x] 1440px 레이아웃을 확인한다. 1:1과 프로젝트 채팅 화면 모두 가로로 넘치지 않았다.
- [x] 문서를 갱신한다.
  - 새 문서 `docs/messenger-api.md`에 확정 계약과 WebSocket 프레임을 옮긴다.
  - [api-plan.md](api-plan.md)의 구현된 API 목록, [team-auth-deployment.md](team-auth-deployment.md)의 "아직 Mock인 기능", [deployment-operations.md](deployment-operations.md)의 nginx 구성, [mock-data.md](mock-data.md), [frontend-pages.md](frontend-pages.md), [demo-scenarios.md](demo-scenarios.md), [decisions-pending.md](decisions-pending.md), [project-backend-blueprint.md](project-backend-blueprint.md)의 7.18(현재 WBS 7.19), [README](../README.md)
- [x] PR 설명에 다른 담당자에게 알릴 내용을 아래와 같이 정리했다. PR은 사용자가 만든다.
  - `reports`: `Report` 엔티티를 이 작업에서 만들었고, 1:1 신고의 사용 컬럼과 중복 판정(#51)
  - 채팅 테이블: `chat_rooms`, `chat_members`, `messages` 구조와 강퇴 시 호출할 `ProjectChatService.excludeMember(projectId, memberId)`(#49)
  - 프로젝트 코드: 바꾸지 않았다. 메신저의 `ProjectChatAccessRepository`가 `project_members`의 ACTIVE·LEFT를 읽는 방식, 방이 없을 때 첫 접근이 `ProjectRepository.findLiveForUpdate`로 프로젝트 행을 잠근다는 점, 재승인을 허용하면 채팅에서 나간 회원은 다시 들어오지 못한다는 점(#48)
  - 프론트 설정: 로컬 HTTP 실시간 연결의 `NEXT_PUBLIC_MESSENGER_WS_PORT`([frontend/.env.example](../frontend/.env.example))
  - 프로젝트 화면: `/projects/{id}/manage`의 종료 창에서 채팅방 삭제를 고르면 상태 변경(7.13) 뒤에 `POST /api/messages/projects/{id}/delete`를 호출해야 한다(PR #81, #85)
  - 공통 코드: `SecurityConfig`, `JwtProvider`, nginx, 블루그린 스크립트, compose 변경
  - `proxy.ts`의 재발급 실패 시 쿠키 삭제 동작(7번)
  - 프로필 BFF: [profileProxy](../frontend/lib/profiles/profileProxy.ts)의 쓰기 확인은 Origin을 `request.url`과 비교한다. 운영 이미지처럼 `HOSTNAME=0.0.0.0`으로 띄운 Next 서버에서는 같은 사이트의 프로필 수정도 403이 된다(8번). 메신저 BFF는 `X-Forwarded-Host`·`Host` 비교로 고쳤고 프로필 코드는 바꾸지 않았다.
  - 회원가입: 요청 필드와 `SignupInput`의 `stacks`를 `skills`로 변경

**완료 조건:** 위 검증 결과를 8번에 기록하고 사용자가 수동 시나리오를 확인한다.

### 16단계: 운영 배포 (이번 작업에서 제외)

메신저 작업을 모두 마친 뒤 main에 한 번 병합해 배포한다. 13단계의 스크립트와 경로 변경도 이 배포에 함께 들어간다. 방화벽이나 포트, 인증서는 바꾸지 않는다. 새 환경변수도 없다. 가입 시 `tag` 자동 생성(D4)이 먼저 배포됐는지 확인한다.

**배포 전에 서버에서 직접 할 일**

호스트 nginx 파일(`/etc/nginx/sites-available/devmozip`)은 이미 수정했다. 배포 서버에 SSH로 접속해 설정을 적용한다.

```bash
ssh team2@cloud.newlecture.com
sudo nginx -t && sudo systemctl reload nginx
```

- `nginx -t`가 실패하면 reload가 실행되지 않는다. 다른 조와 같은 nginx를 쓰므로 원인을 해결한 뒤 다시 실행한다.
- 파일을 저장해 두었으므로 그 전에 서버의 다른 작업으로 reload되면 함께 적용된다. 팀 nginx에 경로가 없는 동안에는 이 경로만 프론트로 전달돼 404가 되고, 다른 동작에는 영향이 없다.

같은 접속에서 다음도 확인한다.

1. `sudo nginx -T | grep -E 'worker_(processes|connections)'`로 연결 한도를 확인한다. WebSocket 하나가 연결 2개를 쓴다. 한도가 부족하면 공용 설정이므로 서버 관리자와 협의한다.
2. 서버 `.env`의 `DOMAINNAME`이 `https://devmozip.newlecture.com`인지 확인한다. 다르면 WebSocket 연결이 모두 거부된다.

서버를 새로 구성했거나 파일을 되돌렸다면, HTTPS `server` 블록(`listen 443 ssl`)의 `location /` 위에 아래 경로를 다시 넣는다.

```nginx
location = /ws/messages {
    proxy_pass http://127.0.0.1:3002;
    proxy_http_version 1.1;
    proxy_set_header Upgrade           $http_upgrade;
    proxy_set_header Connection        "upgrade";
    proxy_set_header Host              $http_host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host  $http_host;
    proxy_read_timeout 75s;
    proxy_send_timeout 75s;
}
```

**배포 후 확인**

- 운영 DB에 새 테이블이 생겼고 이전 색 컨테이너에 영향이 없는지 확인한다.
- Origin을 `DOMAINNAME`으로 넣고 쿠키 없이 핸드셰이크를 요청해 401이 오는지 확인한다. Origin이 없으면 403이 온다.
- 브라우저 개발자 도구에서 WebSocket이 101로 연결되는지 확인한다.
- 토글 OFF에서 메시지를 보내고 읽어, `POST /api/messages/chats/{id}`가 201, `PATCH .../read`가 204로 응답하는지 확인한다. 403이면 nginx가 `Host`나 `X-Forwarded-Host`를 넘기지 않는 것이다.

**되돌리기**

- `bash deploy/blue-green.sh rollback`으로 멈춰 둔 이전 색을 켜거나, `IMAGE_TAG`로 이전 이미지를 배포한다. 둘 다 디스크에 있는 새 스크립트를 쓴다.
- main을 revert해 배포하면 이전 스크립트가 백엔드 업스트림 없이 `active.conf`를 쓴다. 컨테이너의 nginx는 아직 새 `server.conf`를 보고 있어 전환 검사가 실패하고 배포가 실패한다. 사이트는 기존 색으로 계속 응답한다. 이때는 nginx를 재시작한 뒤 배포를 다시 실행한다.

## 7. 알려진 제한

- 1:1 목록은 `last_message_id` 컬럼이 없어 조회할 때마다 내 방의 메시지를 집계한다. 메시지가 많아지면 느려질 수 있다.
- 운영은 배포(16단계) 전까지 WebSocket이 연결되지 않아, 새 메시지가 열린 대화에서 최대 5초, 목록과 배지에서 최대 30초 늦게 표시된다. 그 뒤에도 WebSocket이 끊긴 동안에는 같은 주기로 동작하고, 연결된 동안에는 60초마다 보조 조회를 한다.
- 로컬 HTTP에서 `NEXT_PUBLIC_MESSENGER_WS_PORT` 없이 띄우면 화면과 같은 주소로 연결을 시도해 실패한다. 최대 30초 간격으로 다시 시도하면서 그때마다 `/api/auth/session`을 부르고, 화면은 폴링으로 동작한다.
- 입력 중 신호의 참여 여부는 연결마다 10초 동안 기억한다. 그 사이의 퇴장·방 삭제·상대 정지는 최대 10초 늦게 반영돼, 그동안 입력 중 표시가 전달될 수 있다.
- 배포 때마다 WebSocket 연결이 끊기고 재접속한다. 이전 색을 멈추면 연결도 함께 닫힌다.
- 신호 전달은 서버당 스레드 하나가 처리한다. 수신을 멈춘 연결이 있으면 그 연결이 닫힐 때까지(최대 5초) 다른 회원의 신호도 늦어질 수 있다. 대기열이 가득 차면 요청 스레드에서 직접 전달하므로, 이때 수신을 멈춘 연결이 겹치면 전송 응답이 같은 시간만큼 늦어질 수 있다.
- BFF가 토큰을 갱신해도 열린 연결의 만료 시각은 바뀌지 않는다. 액세스 토큰 수명(30분)마다 연결이 4401로 닫히고 다시 연결된다. 그 사이의 신호는 `after` 조회로 보완한다.
- Redis 장애 중에는 로그인과 토큰 재발급이 실패하고, 액세스 토큰이 만료된 회원은 로그아웃된다(3.4). 메신저와 별개인 기존 동작이다. 이 계획에서 고치지 않으며 [proxy](../frontend/proxy.ts) 담당자에게 공유가 필요하다.
- LAN 주소로 접속하면 Origin이 `DOMAINNAME`과 달라 WebSocket이 거부되고 폴링으로만 동작한다.
- WebSocket 연결은 열린 탭 수만큼 늘어난다. 회원당 제한이 없으므로 전체 한도는 nginx의 `worker_connections`와 Tomcat의 최대 연결 수로 정해진다.
- [Mock 토글](../frontend/lib/demo/mockVisibility.ts)의 기본값이 ON이다. 토글을 OFF로 바꾼 사용자에게만 서버 메신저와 실시간이 동작한다. ON과 OFF는 서로 대화를 주고받지 못한다.
- #48(PR #85)이 병합되기 전에는 승인 기능이 없어 서버 프로젝트 채팅에 주최자 한 명만 들어갈 수 있다. 여러 팀원의 흐름은 #48과 같은 결과의 행을 넣어 확인했다(8번).
- `server.conf`가 바뀌는 배포는 블루그린 스크립트가 팀 nginx를 다시 시작하므로 그때 전체 트래픽이 잠깐 끊긴다. 메신저를 처음 배포할 때 한 번 일어난다.
- 프로젝트 채팅의 읽음 신호는 읽은 사람을 태그로 알린다. 태그가 없는 회원이 읽으면 화면이 채팅을 다시 조회해 반영한다.
- 헤더 배지는 `/messages/chats`로 이동하고 이 화면은 1:1 탭이 기본이다. 프로젝트 채팅에만 안 읽은 메시지가 있으면 프로젝트 탭으로 옮겨야 보인다.
- 프로젝트 화면의 서버 연결(PR #81)이 끝나기 전에는 토글 OFF의 프로젝트 화면 ID가 서버 ID와 달라, 프로젝트 화면의 채팅 링크로 들어가지 못한다. 서버 프로젝트 채팅은 사이드바에서 들어간다.
- 프로젝트 채팅의 초대·퇴장 문구는 승인 시각이 아니라 다음 접근 시각을 기준으로 표시된다(3.5). 초대 사실도 다음 접근 전까지 실시간으로 알리지 않는다. 이 문구는 메시지가 아니라서 안 읽은 수에 들어가지 않는다.
- `local`(H2) 프로필에서는 `projects`의 `jsonb` 컬럼 때문에 `projects` 테이블이 만들어지지 않는다. 메신저 이전부터 있던 동작이다. 채팅 테이블은 만들어지지만 `chat_rooms.project_id`의 외래 키는 빠지고, 프로젝트 채팅(12·14단계)은 PostgreSQL로만 확인할 수 있다. `local` 프로필에서 `GET /messages/chats?type=project`와 프로젝트 채팅 API는 500으로 응답하고, 프로젝트 방의 입력 중 신호는 무시된다. 그래서 `local` 프로필의 토글 OFF 화면은 프로젝트 탭에 불러오지 못했다는 안내를 보이고, 헤더 배지는 1:1만 센다.
- 강퇴 투표(#49)는 [프로젝트 강퇴 계약](project-member-removal.md)에 따라 토글 OFF에서도 연결한다.
- 가입 시 `tag` 자동 생성(D4) 전에는 `tag`가 없는 회원이 대화를 시작하면 상대가 프로필로 이동하거나 신고할 수 없다. 그래서 그 작업을 먼저 배포한다.
- Mock 프로젝트의 주최자는 서버 회원이 아니고 [프로젝트 상세 응답](../backend/src/main/java/com/devmozip/backend/dto/project/ProjectDetailResponse.java)에 주최자 정보도 없다. 그래서 토글 OFF에서 프로젝트 상세의 주최자 연락은 특정 상대와의 대화를 열지 못하고 목록으로만 이동한다. 프로젝트 API 연결 때 `tag`를 받아 다시 연결한다.
- 프로젝트 관리 화면에 삭제 호출이 연결되기 전에는 종료 창의 삭제 선택이 서버 채팅방에 반영되지 않는다. API만 동작한다.
- 삭제한 프로젝트 채팅방의 메시지는 화면에서 사라지지만 DB에 남는다. 보존 기한과 복구 방법은 정해지지 않았다.
- 프로젝트 채팅방 삭제는 실시간 신호를 보내지 않는다. 보고 있던 멤버는 다음 조회(연결 중 최대 60초) 때 404를 받는다.
- 프로젝트 채팅의 첫 접근은 방이 없을 때만 `projects` 행을 잠근다. 그동안 같은 프로젝트를 수정하는 요청(승인 등)은 잠깐 기다린다.
- 신고는 저장만 되며 관리자 화면(14.8)에 연결되지 않는다.
- 메시지 보존 기한, 회원 탈퇴 시 처리, 관리자의 대화 열람 정책은 미확정이다.

## 8. 진행 기록

### 2026-11-06 계획 문서 작성

- WBS 11.0~11.4와 관련 항목(6.7, 7.18, 12.4, 14.8), 현재 코드, 열린 이슈(#49, #51)를 대조해 이 문서를 작성했다. 코드는 변경하지 않았다.
- ERD는 저장소에 파일이 없고 ERDCloud 페이지 응답에서 내용을 읽을 수 없어 대조하지 못했다. 0단계에서 수행한다.
- 문서만 변경했다. 현재 커밋(998eec4)의 `frontend` 복사본에서 `npm ci`, `npm run build`가 성공했고, `messagingService.test.ts`와 `MessagesPage.test.tsx`의 26개 테스트가 통과했다. 전체 `npm test`와 `./gradlew test`는 실행하지 않았다.

### 2026-11-06 WebSocket 규칙 변경

- 사용자 요청으로 [AGENTS.md](../AGENTS.md)의 WebSocket 연결 금지를 제거하고, 이 문서의 1번, 3.1, D1을 그에 맞게 고쳤다. 수신 방식은 아직 확정되지 않았다.
- 사용자 결정으로 Netty를 쓰지 않기로 하고 D1의 대안에서 뺐다.

### 2026-11-06 실시간 구조 확정

- 사용자 결정으로 D1을 WebSocket과 Redis Pub/Sub으로 확정했다. PostgreSQL을 메시지 원본으로 두고 Redis에는 본문 없는 신호만 보낸다.
- 사용자 요청으로 입력 중 표시를 범위에 넣었다. 8~12단계를 추가하고 기존 8·9단계를 13·14단계로 옮겼다. 기존 화면의 읽음 표시는 같은 신호로 바로 갱신한다.
- 3.4의 Redis 장애 동작은 코드와 Lettuce 7.5.2, spring-data-redis 4.1.2 소스로 확인한 내용이다. 실제 장애를 일으켜 검증하지는 않았다. 13단계에서 검증한다.
- 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-06 WebSocket 유지와 nginx 설정 시험

- SSE를 검토한 뒤 사용자 결정으로 WebSocket을 유지했다. 참여 대화방 수에 제한이 없으므로 대화방 수와 회원당 연결 수를 제한하지 않는다.
- 프로젝트 채팅의 실시간 범위를 D9로 추가했다.
- 10·14단계의 설정을 임시 컨테이너(nginx:1.27.5 두 계층, 가짜 백엔드·프론트, Node WebSocket 클라이언트)로 시험한 결과는 다음과 같다.
  - 핸드셰이크는 쿠키가 없으면 401, 있으면 101이다. 8개 동시 연결이 모두 메시지를 주고받았다.
  - `/ws/messages` 외의 경로는 백엔드로 전달되지 않았다.
  - 색 전환 후 기존 연결은 이전 색에 남고 새 연결은 새 색으로 갔다. 이전 색을 멈추면 1001로 닫히고 재접속은 새 색으로 갔다.
  - 30초 `ping`이 있으면 85초 동안 유지됐고, 없으면 75초에 끊겼다.
  - 마운트된 파일을 교체하면 reload로는 반영되지 않고 재시작하면 반영됐다. 새 `server.conf`와 이전 `active.conf`의 조합은 `nginx -t`가 실패했다.
- 실제 브라우저, TLS, 운영 호스트 nginx, Spring WebSocket 서버는 시험하지 못했다. 13·14단계에서 확인한다.
- 문서만 변경했고 저장소의 nginx·배포 파일은 바꾸지 않았다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-06 호스트 nginx 파일 수정

- 작업 담당자가 운영 서버의 `/etc/nginx/sites-available/devmozip`에 `/ws/messages` 경로를 넣고 `nginx -t` 통과를 확인했다. 적용을 위한 reload 안내를 14단계(현재 16단계)에 넣었다.
- 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-06 0단계 진행

- 사용자 결정으로 D1~D9를 확정했다. 달라진 내용은 다음과 같다.
  - D4: 가입 시 `tag` 자동 생성을 다른 브랜치의 별도 작업으로 분리했다.
  - D5: Mock 토글 ON은 기존 Mock을 유지하고 OFF만 서버를 쓴다.
  - D6: `reports`는 별도 합의 없이 PR에 적어 #51 담당자에게 알린다.
  - D9: 프로젝트 단톡방(7.18)을 포함해 13·14단계를 추가하고, 기존 13·14단계를 15·16단계로 옮겼다. 위 기록의 단계 번호는 당시 기준이다.
  - 메시지 저장소에 HBase를 쓰지 않는다.
- [AGENTS.md](../AGENTS.md)에 메신저, 7.18, WebSocket, Redis Pub/Sub 범위와 이 문서를 추가했다.
- ERD는 저장소, 다른 브랜치, ERDCloud 어디에서도 내용을 읽지 못해 대조 불가로 기록했다.
- 사용자 결정으로 프로젝트 채팅방 삭제를 논리 삭제로 정하고 `chat_rooms.deleted_at`을 추가했다. `DELETE` 메서드 대신 `POST /messages/projects/{id}/delete`로 처리하고, 삭제는 단톡방 "관리" 행에 포함한다(D10).
- 사용자가 WBS를 7.18~7.23과 11.1~11.9로 재구성해, 이 문서와 [AGENTS.md](../AGENTS.md)의 번호를 맞췄다. 프로젝트 입장 전용 API는 두지 않는다(D13).
- WBS 11.1이 프로젝트 단톡방 목록을 포함하게 되어 `GET /messages/projects`를 빼고 `GET /messages/chats`의 `type`으로 구분한다(D11). `type`은 구현을 위해 이 문서에서 정한 값이다.
- 사용자 결정으로 헤더 배지를 토글 OFF에서 1:1과 프로젝트의 합계로 표시한다(D12).
- 코드를 확인한 결과 삭제 선택은 채팅 화면이 아니라 `/projects/{id}/manage`의 종료 창에 있다. 14단계에서 삭제 화면 연결을 빼고 15단계의 PR 알림에 넣었다.
- 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.
- WBS 갱신이 남았다.

### 2026-11-06 ERD 대조와 1단계

- 작업 담당자가 WBS 갱신을 마쳐 0단계를 완료했다.
- 채팅 테이블 ERD 이미지를 받아 4번의 초안과 대조했다. 테이블과 컬럼을 ERD에 맞추고 엔티티를 `ChatRoom`, `ChatMember`, `Message`로 만들었다. 달라진 점은 4번에 있다.
- ERD에 없는 `chat_rooms.project_id`, `direct_key`, `deleted_at`은 추가하지 않고 E1~E3으로 남겼다. 0단계 기록의 `chat_rooms.deleted_at`은 E3이 정해질 때까지 확정이 아니다.
- ERD에 발신자 없는 메시지를 저장할 수 없어 초대·퇴장 문구를 `joined_at`, `left_at`으로 표시하도록 3.5를 바꿨다. 이 문구는 안 읽은 수에 포함되지 않는다.
- 추가한 파일은 다음과 같다.
  - `entity`: `ChatRoom`, `ChatMember`, `Message`
  - `repository`: `ChatRoomRepository`, `ChatMemberRepository`, `MessageRepository`
  - 테스트 `message` 패키지: `ChatEntityRoundTripTests`, `ChatSchemaIntegrationTest`, `ChatSchemaUpdateIntegrationTest`, `ChatEntityH2Test`
- `backend/build`에 root 소유 파일이 있어 저장소 안에서는 `compileJava`가 실패했다. 아래 검증은 모두 변경 파일을 포함한 스크래치 복사본에서 실행했다.
  - 현재 커밋(67bff78)에 변경 파일을 더한 복사본의 `sh ./gradlew test`: 370개 통과, 실패 0, 건너뜀 0. PostgreSQL 컨테이너 테스트도 실행됐다.
  - origin/develop(0a35674)에 새 파일을 더한 복사본의 `sh ./gradlew test`: 530개 중 실패 0, 건너뜀 4. 건너뛴 4개는 공용 팀 DB 연결이 필요한 기존 프로젝트 테스트다.
  - `local` 프로필: Git에서 제외된 `application-local.yml`을 복사본에 넣어 `bootRun`으로 기동했고 `/actuator/health`가 200이었다. 일회성 테스트로 H2에 채팅 테이블 3개와 인덱스가 생기고 저장·조회가 되는 것을 확인했다. 이 파일은 CI에 없어 저장소의 H2 테스트는 같은 URL 형식의 인라인 설정을 쓴다.
  - 프론트는 변경하지 않았다. 복사본에서 `npm ci`, `npm run build`가 성공했고 `messagingService.test.ts`, `MessagesPage.test.tsx`의 26개 테스트가 통과했다. 전체 `npm test`는 실행하지 않았다.
- 공용 DB에는 연결하지 않았다. PostgreSQL 검증은 테스트 컨테이너로만 했다.
- `local`(H2)에서 `projects` 테이블 생성이 `jsonb` 때문에 실패하는 것을 확인했다. 기존 동작이며 7번에 적었다.
- 미완료: E1~E3 결정, 전체 프론트 테스트. 다음 단계는 2단계이며 E2가 먼저 정해져야 한다.

### 2026-11-06 E1~E3 결정

- 사용자 결정으로 E1과 E3은 컬럼을 추가하고, E2는 `direct_key` 없이 잠금으로 처리한다. 작업 담당자가 ERD에 두 컬럼을 추가했다. 갱신된 ERD를 직접 대조하지는 못해 타입과 외래 키는 4번의 값으로 구현했다.
- `ChatRoom`에 `project_id`(UNIQUE, `projects.id` 외래 키)와 `deleted_at`을 추가했다. 프로젝트 없는 프로젝트 방을 만들지 못하도록 생성자를 `ChatRoom.direct()`, `ChatRoom.project(project)`로 나눴다. 삭제 처리 메서드는 13단계에서 추가한다.
- 스키마·왕복 테스트를 새 컬럼에 맞게 고치고 다음을 추가했다. 프로젝트 방 저장, 같은 프로젝트의 두 번째 방 거부, 없는 프로젝트 거부, 1:1 방 여러 개 저장, 이전 구조의 `chat_rooms`에 `ddl-auto: update`로 컬럼 추가.
- 3.3, 3.5, 4번, 6번, 7번을 결정에 맞게 고쳤다. 3.3에 있던 유일 제약 위반 처리는 프로젝트 방에만 해당하므로 3.5로 옮겼다.
- 아래 검증은 모두 변경 파일을 포함한 스크래치 복사본에서 실행했다.
  - 현재 커밋(67bff78)에 변경 파일을 더한 복사본의 `sh ./gradlew test`: 376개 통과, 실패 0, 건너뜀 0. PostgreSQL 컨테이너 테스트도 실행됐다.
  - origin/develop(058397e)에 새 파일을 더한 복사본의 `sh ./gradlew test`: 566개 중 실패 0, 건너뜀 11. 건너뛴 11개는 공용 팀 DB 연결이 필요한 기존 프로젝트 테스트다.
  - `local` 프로필: Git에서 제외된 설정을 복사본에 넣어 `bootRun`으로 기동했고 `/actuator/health`가 200이었다. 일회성 왕복 테스트 2개도 통과했다. `projects`가 없어 `project_id`의 외래 키를 만들지 못한다는 경고가 기동 로그에 하나 늘었다(7번).
  - 프론트는 변경하지 않았다. 복사본에서 `npm run build`가 성공했고 `messagingService.test.ts`, `MessagesPage.test.tsx`의 26개 테스트가 통과했다. 전체 `npm test`는 실행하지 않았다.
- 공용 DB에는 연결하지 않았다. 동시 요청에도 1:1 방이 하나만 생기는지는 잠금을 구현하는 2단계에서 시험한다.
- 미완료: 전체 프론트 테스트. 다음 단계는 2단계다.

### 2026-11-06 시각 컬럼 타입 결정

- 사용자 결정으로 채팅 테이블의 시각 컬럼을 TIMESTAMP로 유지한다. TIMESTAMPTZ는 쓰지 않는다.
- PostgreSQL 테스트 컨테이너에서 일회성 테스트로 확인한 결과, 채팅 테이블을 포함한 시각 컬럼 18개가 모두 `timestamp without time zone`, 정밀도 6이었다. 이 테스트는 스크래치 복사본에서만 실행했고 저장소에 넣지 않았다.
- 개발 백엔드 컨테이너의 시간대가 UTC이고 compose와 Dockerfile에 시간대 설정이 없음을 확인했다. 그래서 3.3에 Asia/Seoul `Clock` 기준을 추가했다.
- 코드는 바꾸지 않았고 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-06 갱신된 ERD 대조

- 갱신된 ERD 이미지를 받아 대조했다. 채팅 테이블 3개의 컬럼과 타입은 구현과 같다.
- 이미지에서 잘린 두 선의 대상은 작업 담당자가 확인했다. `chat_members.user_id`는 `users`, `chat_rooms.project_id`는 `projects`로 이어진다.
- 프로젝트와 방을 잇는 선의 `chat_rooms` 쪽 끝은 프로젝트당 방 하나에 맞게 "0 또는 1"로 고쳐졌다. `projects` 쪽 끝의 표기는 이미지에 없어 확인하지 못했다.
- `chat_rooms`와 `chat_members`를 잇는 선은 `chat_rooms` 쪽 1, `chat_members` 쪽 0개 이상이다. 나머지 두 선도 구현과 같다.
- 코드는 바꾸지 않았고 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-07 2~4단계

- 1:1 메시지 API 6개(11.1~11.3, 11.5~11.7)와 사용자 신고 API(11.4)를 구현했다. 초안에 없던 400 응답과 `after` 사용법을 5번에 반영했다.
- #51의 PR과 원격 브랜치에 `reports` 코드가 없어 `Report`를 추가했다(D6). `reports`의 ERD는 받지 못해 청사진과 대조했다. `reporter_id`, `reported_user_id`만 `users` 외래 키로 두고, 나머지 대상 컬럼은 값만 저장한다.
- 추가하거나 바꾼 메신저 파일은 다음과 같다.
  - `controller`: `MessageController`, `UserReportController`
  - `service`: `MessageService`, `UserReportService`
  - `entity`: `Report`, `enums/UserReportReason`
  - `repository`: `ReportRepository`, 채팅 리포지토리의 조회 메서드, `MemberRepository`의 `findByTag`, `findForMessengerById`
  - 그 밖: `dto/message`, `dto/report`, `config/MessengerConfiguration`
  - 테스트 `message` 패키지: `MessageApiTests`, `UserReportApiTests`(각각 PostgreSQL·H2에서 실행), `MessengerConfigurationTest`
- 회원가입 요청 필드와 `SignupInput`의 `stacks`를 `Member`와 같은 `skills`로 바꿨다. 로그인 사용자 정보(`AuthUser`), 온보딩, Mock 프로필의 `stacks`는 별개의 값이라 그대로 뒀다.
- PostgreSQL의 잠금 SQL을 로그로 확인했다. `users`, `chat_rooms` 모두 `for no key update`라서 다른 INSERT의 외래 키 확인과 충돌하지 않는다.
- 아래 검증은 모두 스크래치 복사본에서 실행했다.
  - 현재 커밋(67bff78)에 변경을 더한 복사본의 `sh ./gradlew test`: 469개 통과, 실패 0, 건너뜀 0. PostgreSQL 컨테이너 테스트와 동시 요청 테스트도 실행됐다.
  - origin/develop(058397e)에 변경을 더한 복사본의 `sh ./gradlew test`: 659개 중 실패 0, 건너뜀 11. 건너뛴 11개는 공용 팀 DB 연결이 필요한 기존 프로젝트 테스트다.
  - `local` 프로필 서버를 띄워 실제 HTTP로 가입(`skills`), 대화 시작, 전송, 목록, 조회, 읽음, 안 읽은 수, 신고를 확인했다. 36개 항목이 모두 통과했고 서버 ERROR 로그는 0건이다. 일회성 스크립트라 저장소에 넣지 않았다.
  - 현재 커밋 프론트: `npm ci`, `npm run build` 성공. `npm test`는 627개 중 626개 통과, 1개 실패.
  - origin/develop에 프론트 변경을 3-way로 적용한 복제본: 충돌 없음. `npm ci`, `npm run build` 성공. `npm test`는 707개 중 706개 통과, 1개 실패.
  - 실패한 1개는 두 곳 모두 `lib/projects/projectService.test.ts`의 공고 생성 테스트다. 변경 전 커밋(67bff78)에서도 같은 테스트가 실패한다. 프로젝트 코드라 수정하지 않았고 원인은 조사하지 않았다.
- 공용 DB에는 연결하지 않았다. 브라우저 화면 연결은 5단계 이후라 확인하지 않았다.
- 미완료: 5단계 이후 전체. 다음 단계는 5단계다.

### 2026-11-07 5단계

- 1:1 메시지와 사용자 신고의 BFF 경로 5개와 브라우저용 API 클라이언트를 추가했다. 화면은 아직 이 클라이언트를 쓰지 않으며 6단계에서 연결한다.
- 추가하거나 바꾼 파일은 다음과 같다.
  - `lib/messaging`: `messagingProxy.ts`, `directChatApi.ts`
  - `app/api/messages/chats`: `route.ts`, `unread-count/route.ts`, `[id]/route.ts`, `[id]/read/route.ts`
  - `app/api/users/[slug]/reports/route.ts`
  - `types/messaging.ts`의 `DirectChat*` 타입, 테스트 픽스처 `tests/helpers/directChatFixture.ts`
  - 테스트: `directChatApi.test.ts`와 각 경로의 `route.test.ts`, 모두 101개
- 태그와 닉네임이 없는 상대도 응답을 거부하지 않는다. 가입 시 `tag` 자동 생성(D4) 전에도 대화 화면이 깨지지 않게 하기 위해서다.
- 아래 검증은 모두 스크래치 복사본에서 실행했다.
  - 현재 작업 내용: `npx tsc --noEmit` 오류 0, `npm run build` 성공. `npm test`는 728개 중 727개 통과, 1개 실패.
  - origin/develop(058397e)에 프론트 변경을 3-way로 적용한 복제본: 충돌 없음. `npm ci`, `npm run build` 성공. `npm test`는 808개 중 807개 통과, 1개 실패.
  - 실패한 1개는 두 곳 모두 4단계 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - `local` 프로필 백엔드와 `next start`를 함께 띄워 실제 HTTP로 BFF를 호출했다. 쿠키 인증 전달, Origin·JSON 거부, 길이 사전 차단, 오류 변환, 내부 값 미노출을 포함한 46개 항목이 모두 통과했고 서버 오류 로그는 0건이다. 일회성 스크립트라 저장소에 넣지 않았다.
- 백엔드 코드는 바꾸지 않아 `./gradlew test`는 다시 실행하지 않았다. 공용 DB에는 연결하지 않았다.
- 메시지 경로에서 만료 토큰이 갱신되는 흐름은 시험하지 않았다. 갱신은 기존 [proxy](../frontend/proxy.ts)가 맡는다. 실제 브라우저도 시험하지 않았다. 6단계와 15단계에서 확인한다.
- 미완료: 6단계 이후 전체. 다음 단계는 6단계다.

### 2026-11-07 6·7단계

- Mock 토글 OFF에서 1:1 메신저 화면을 서버 API에 연결하고, 프로필의 대화 시작과 사용자 신고를 연결했다. 토글 ON과 프로젝트 채팅은 전과 같이 동작한다.
- 추가하거나 바꾼 파일은 다음과 같다.
  - 상태·로직: `lib/state/directChatStore.ts`, `lib/messaging/directChatPoller.ts`, `lib/messaging/directChatView.ts`
  - 출처 선택: `app/messages/chats/_hooks/useConversationPane.ts`, `app/messages/_hooks/useDirectConversations.ts`, `useActiveConversation.ts`
  - 화면: `ChatWindow`, `ConversationSidebar`, `UserReportDialog`, `ChatsLayout`, 대화 화면, `AppLayout`의 배지, `ApiProfileHeader`, `StartChatButton`, 프로젝트 상세의 주최자 연락
  - 동기화: `app/_components/DirectChatSync.tsx`를 `app/layout.tsx`에 넣었다.
  - 테스트: `directChatPoller.test.ts`, `directChatView.test.ts`, `directChatStore.test.ts`, `DirectChatScreens.test.tsx`, 모두 73개
- 서버 대화의 스크롤은 대화 영역 안에서만 움직인다. 기존 `scrollIntoView`는 페이지 전체도 움직여, 폴링으로 메시지를 받으면 페이지가 튈 수 있기 때문이다.
- develop에서 프로필의 `tag`가 `null`일 수 있어 태그가 없는 프로필에는 메시지 보내기를 표시하지 않는다.
- 아래 검증은 모두 스크래치 복사본에서 실행했다.
  - 현재 작업 내용: `npx tsc --noEmit` 오류 0, `npm run build` 성공. `npm test`는 801개 중 800개 통과, 1개 실패.
  - origin/develop(058397e)에 프론트 변경을 3-way로 적용한 복제본: 충돌 없음. `npx tsc --noEmit` 오류 0, `npm ci`, `npm run build` 성공. `npm test`는 881개 중 880개 통과, 1개 실패.
  - 실패한 1개는 두 곳 모두 4단계 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - `local` 프로필 백엔드와 `next start`를 띄우고 Playwright의 헤드리스 Chromium으로 회원별 브라우저 환경을 분리해 시험했다. 17개 항목이 모두 통과했고 브라우저 오류와 서버 오류 로그는 0건이다. 항목은 프로필에서 대화 시작, 양쪽 메시지 교환, 읽음 표시, 배지 갱신, 중복 전송 차단, 숨겨진 탭, 이전 메시지, 위로 올려 읽기, 검색, 신고와 중복 신고, 토글 전환, 세션 만료다.
- 브라우저 시험 스크립트와 Playwright는 스크래치에만 두었다. 저장소의 테스트 환경에 DOM이 없어, 저장소 테스트는 저장소·폴러 로직과 정적 렌더링만 확인한다. 클릭과 스크롤 동작은 이번 브라우저 시험으로만 확인했다.
- 백엔드 코드는 바꾸지 않아 `./gradlew test`는 다시 실행하지 않았다. 공용 DB에는 연결하지 않았다.
- 시험하지 않은 항목은 다음과 같다. 15단계에서 확인한다.
  - nginx를 거친 실제 배포 경로, Chromium 외의 브라우저
  - 만료된 액세스 토큰이 메시지 경로에서 갱신되는 흐름(세션 쿠키가 사라진 경우만 시험했다)
  - 프로젝트 상세의 주최자 연락 링크는 정적 렌더링 테스트로만 확인했다.
- 미완료: 8단계 이후 전체. 다음 단계는 8단계다.

### 2026-11-07 8단계

- `/ws/messages` WebSocket 서버를 추가했다. 로그인 회원만 허용된 Origin에서 연결할 수 있고, 토큰이 만료되면 4401로 닫힌다. `ping` 외의 신호는 아직 보내지 않으며(9단계) 화면은 바꾸지 않았다(11단계).
- 추가하거나 바꾼 파일은 다음과 같다.
  - 새 파일: `websocket/MessengerHandshakeInterceptor`, `MessengerSessionRegistry`, `MessengerWebSocketHandler`, `config/MessengerWebSocketConfig`, `MessengerRealtimeProperties`
  - 공통 파일: `build.gradle`(의존성), `SecurityConfig`(`GET /ws/messages` 허용 한 줄), `JwtProvider`(`getExpiration` 추가), `application.yaml`(`messenger.realtime.allowed-origin`)
  - 테스트: `websocket/MessengerWebSocketTest`, `MessengerWebSocketShutdownTest`, `MessengerWebSocketNoOriginConfiguredTest`, `MessengerSessionRegistryTest`, `TestSocket`, 모두 59개
- 구현하면서 계획과 달라졌거나 구체화한 내용은 다음과 같다.
  - 4KB 제한은 연결마다 정한다. `ServletServerContainerFactoryBean`을 빈으로 두면 실제 서버가 없는 MockMvc 컨텍스트가 시작되지 못한다(`ServerContainer not found`). 스크래치에서 실패를 확인했다.
  - 스프링의 기본 Origin 검사는 Origin이 없거나 Host와 같으면 통과시켜 `DOMAINNAME`이 없을 때 모두 거부하지 못한다. 인터셉터가 토큰보다 먼저 정확히 일치하는지 확인한다.
  - 연결 확인 스레드는 빈으로 등록하지 않는다. `ThreadPoolTaskScheduler`를 빈으로 두면 `applicationTaskExecutor`가 만들어지지 않는 것을 스크래치에서 확인했다.
  - 토큰 만료와 정지 확인, `ping` 전송을 1초 주기의 확인 한 곳에서 처리한다.
  - 응답이 없거나 느린 연결의 종료 코드를 4500으로 정했다. 스프링의 `SESSION_NOT_RELIABLE` 값이다. 5번의 표에 이 코드와 1006을 추가했다.
  - 핸드셰이크가 거부되면 브라우저에는 1006만 보여, 재접속 때의 토큰 만료와 정지를 종료 코드로 구분할 수 없다. 11단계 항목에 반영했다.
  - `typing` 프레임은 12단계 전까지 다른 알 수 없는 프레임처럼 1008로 닫는다.
- 아래 검증은 모두 스크래치 복사본과 스크래치 컨테이너에서 실행했고 공용 DB에는 연결하지 않았다.
  - `./gradlew test`: 현재 작업 내용 528개 통과, 건너뜀 0. origin/develop(058397e)에 변경을 3-way로 적용한 복제본은 충돌 없이 718개 중 실패 0, 건너뜀 11(팀 DB).
  - 프론트 코드는 바꾸지 않았다. 현재 작업 내용은 `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 801개 중 800개 통과. 복제본은 `npx tsc --noEmit` 오류 0, `npm ci`, `npm run build` 성공, `npm test` 881개 중 880개 통과. 실패한 1개는 두 곳 모두 4단계 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - 테스트가 실제로 문제를 잡는지 방어 코드 12개를 하나씩 없애 확인했다. Origin 검사, DB 정지 상태, 토큰 종류, Tomcat 전송 시간, 종료 시 닫기, 토큰 만료, `pong` 시간 초과와 기록, 크기 제한, 프레임 검사, 연결 제거, 경로 제한이다. 12개 모두 해당 테스트가 실패했다.
  - 운영과 같은 조합(jar, default 프로필, PostgreSQL 17, Redis, `next start`)을 띄워 헤드리스 Chromium으로 시험했다. 시간 값은 기본값을 쓰고 토큰 수명만 100초로 줄였다. 18개 항목이 모두 통과했고 서버와 Next.js의 오류 로그는 0건이다. 확인한 내용은 다음과 같다.
    - 실제 로그인으로 받은 HttpOnly·SameSite=Lax 쿠키로 포트가 다른 백엔드에 연결된다.
    - 같은 쿠키라도 다른 Origin 페이지에서는 연결되지 않는다.
    - `ping`은 30.5초와 60.5초에 왔고, 응답하지 않은 연결은 70.5초에 4500으로 닫혔다. 응답한 연결은 토큰이 만료될 때 4401로 닫혔다.
    - 만료된 토큰으로는 다시 연결되지 않고, `/api/auth/session` 호출 후에는 연결된다. 11단계의 복구 흐름이 성립한다.
    - SIGTERM을 보내면 브라우저가 1001과 이유 문구를 받고 서버가 정상 종료한다.
  - `local` 프로필에서 `DOMAINNAME` 없이 실행하면 `http://localhost:3000`은 101, `http://localhost:3001`은 403, 쿠키 없음은 401로 응답한다.
- 브라우저 시험 스크립트는 스크래치에만 두었다. 저장소 테스트는 Java 클라이언트로 실제 Tomcat에 연결하는 범위까지 확인한다.
- 시험하지 않은 항목은 다음과 같다.
  - nginx와 `wss`를 거친 경로(10단계), Chromium 외의 브라우저(15단계)
  - compose 개발 컨테이너에서의 실행. 의존성이 늘어 이 컨테이너는 재시작해야 WebSocket을 쓸 수 있다.
- 미완료: 9단계 이후 전체. 다음 단계는 9단계다.

### 2026-11-07 9단계

- 메시지 전송과 읽음 처리의 신호를 커밋 후 Redis 채널로 보내고, 구독한 서버가 자기에게 연결된 회원에게 전달한다. 화면은 바꾸지 않았다(11단계).
- 추가하거나 바꾼 파일은 다음과 같다.
  - 새 파일: `websocket/MessengerSignal`, `MessengerSignalPublisher`, `LocalMessengerSignalPublisher`, `RedisMessengerSignalPublisher`, `RedisSignalPayload`, `MessengerSignalDispatcher`, `RedisMessengerSignalSubscription`, `dto/message/MessageCreatedFrame`, `MessageReadFrame`
  - 수정: `MessageService`(신호 발행), `MessengerSessionRegistry`(여러 회원 전달), `application.yaml`, `application-test.yaml`, `build.gradle`(test 작업의 시스템 속성)
  - 테스트: `MessengerSignalDispatcherTest`, `RedisMessengerSignalPublisherTest`, `MessengerSignalFlowTest`, `MessengerRedisSignalIntegrationTest`, 모두 28개. 8단계 테스트의 회원 생성은 `TestMembers`로 모았다.
- 구현하면서 계획을 구체화한 내용은 다음과 같다.
  - `RedisMessageListenerContainer.start()`는 첫 구독이 실패하면 예외를 던져 Redis가 멈춘 동안 서버가 시작되지 못한다. 메신저 이전에는 Redis 없이도 서버가 떴으므로, 구독을 별도 스레드에서 5초마다 다시 시도한다.
  - 발행이 실패하면 5초 동안 Redis를 건너뛴다. 건너뛰지 않으면 장애 중에 신호마다 타임아웃(2초)을 기다려 같은 서버의 전달까지 밀린다.
  - 구독 중인 서버가 없으면 발행이 성공해도 아무도 받지 못하므로 실패로 처리한다. Redis가 재시작한 직후에 생기는 상황이다.
  - 읽음 위치가 실제로 옮겨졌을 때만 `message.read`를 보낸다. 전송으로 옮겨진 보낸 사람의 위치는 따로 보내지 않는다.
  - 저장소 루트의 `.gitignore`가 새 `*.yaml` 파일을 무시한다. 처음에 테스트 설정을 새 yaml 파일로 만들었다가 커밋과 스크래치 복사본에서 빠지는 것을 발견해, 이미 추적 중인 `application-test.yaml`과 `build.gradle`로 옮기고 전체 검증을 다시 실행했다. 아래는 다시 실행한 결과다. `MessengerSignalFlowTest`가 Redis 발행기를 쓰지 않는지 확인하는 테스트도 추가했다.
- 아래 검증은 모두 스크래치 복사본과 스크래치 컨테이너에서 실행했고 공용 DB에는 연결하지 않았다.
  - `./gradlew test`: 현재 작업 내용 556개 통과, 건너뜀 0. origin/develop(058397e)에 변경을 3-way로 적용한 복제본은 충돌 없이 746개 중 실패 0, 건너뜀 11(팀 DB).
  - 프론트 코드는 바꾸지 않았다. 현재 작업 내용은 `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 801개 중 800개 통과. 복제본은 `npx tsc --noEmit` 오류 0, `npm ci`, `npm run build` 성공, `npm test` 881개 중 880개 통과. 실패한 1개는 두 곳 모두 4단계 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - 방어 코드 12개를 하나씩 없애 확인했고 12개 모두 해당 테스트가 실패했다. 커밋 후 발행, 전용 스레드, Redis 건너뛰기, 같은 서버 전달, 로그 제한, 구독자 없음 처리, 시작을 막지 않는 구독, 구독 재시도, 수신자 2종, 읽음 신호 조건과 발행이다.
  - 시간에 민감한 테스트를 반복 실행했다. `MessengerSignalDispatcherTest` 900회, `MessengerSessionRegistryTest` 850회, `MessengerSignalFlowTest` 50회 모두 실패 0이다. 첫 시도에서 Mockito 스터빙 경쟁으로 가끔 실패하는 테스트를 발견해 고쳤다.
  - 운영과 같은 조합(jar, default 프로필, PostgreSQL 17, Redis, `next start`)으로 백엔드 2대를 띄우고, 실제 로그인한 Chromium 2개를 각 서버에 연결해 시험했다. 전송은 BFF를 거쳐 한쪽 서버에서 처리했다. 22개 항목이 모두 통과했고 서버 ERROR 로그는 0건, 메시지 본문이 든 로그는 0건이다. 확인한 내용은 다음과 같다.
    - 다른 서버의 상대가 전송 시작부터 98ms에 신호를 받았다. 신호에 본문이 없고, 상대는 `after` 조회로 본문을 받는다.
    - 읽음 신호가 두 참여자에게 가고, 위치가 그대로면 보내지 않는다.
    - Redis가 응답하지 않을 때 전송 응답은 16~24ms, 꺼졌을 때 17~23ms였다. 같은 서버의 회원은 신호를 계속 받고, 다른 서버의 회원은 조회로 메시지를 받는다.
    - Redis가 꺼진 동안에도 서버가 시작되고(헬스체크 503), Redis가 돌아오면 구독해 다른 서버 전달과 헬스체크가 정상으로 돌아온다.
- 이 브라우저 시험은 테스트 설정을 고치기 전에 실행했다. 그 뒤에는 테스트 파일과 test 작업의 설정만 바꿨고 운영 코드와 `application.yaml`은 바꾸지 않아 다시 실행하지 않았다. 브라우저 시험 스크립트는 스크래치에만 두었다.
- 시험하지 않은 항목은 다음과 같다.
  - nginx와 `wss`를 거친 경로(10단계), 실제 블루그린 전환(10·15단계), Chromium 외의 브라우저(15단계)
  - compose 개발 컨테이너에서의 실행
- 미완료: 10단계 이후 전체. 다음 단계는 10단계다.

### 2026-11-08 배포 방식 변경

- 사용자 결정으로 main 배포를 메신저 작업을 모두 마친 뒤 한 번에 하기로 했다. 그 시점의 develop에는 스크립트와 `server.conf` 변경이 함께 들어가므로, 10단계의 두 번 배포를 한 번 배포로 바꿨다.
- 기존 계획대로 헬스체크 뒤에 `active.conf`를 쓰면, 한 번 배포에서 헬스체크가 실패했을 때 디스크에 새 `server.conf`와 업스트림 없는 `active.conf`가 남아 다음 nginx 재시작 때 사이트가 멈출 수 있다. 그래서 `deploy`와 `rollback`의 시작 부분에서 `active.conf`를 쓰도록 10단계를 고치고, 이를 확인하는 로컬 시험과 완료 조건을 추가했다.
- 16단계에 되돌리기 방법을 추가했다. 위 동작은 스크립트와 11-06 nginx 시험 결과로 판단한 것이며 10단계의 로컬 시험에서 확인한다.
- 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-08 단계 순서 변경

- 사용자 결정으로 nginx·블루그린 경로를 14단계 바로 앞의 13단계로 옮겼다. main 배포를 마지막에 하므로 먼저 진행할 필요가 없다. 기존 11~13단계는 10~12단계가 됐다. 위 기록의 단계 번호는 당시 기준이다.
- 화면 실시간 연결(10단계)은 로컬에서 백엔드에 직접 연결해야 하므로 `NEXT_PUBLIC_MESSENGER_WS_PORT` 항목을 nginx 단계에서 10단계로 옮겼다. 처음 계획한 `compose.override.yaml`이 Git에서 빠져 있어 안내 위치는 10단계에서 정한다.
- 다음 단계는 10단계다.
- 문서만 변경했다. 빌드와 테스트는 다시 실행하지 않았다.

### 2026-11-08 10~12단계

- 1:1 화면이 WebSocket 신호로 메시지·읽음을 바로 반영하고(10단계), 1:1 방에 입력 중 표시를 붙였다(11단계). 프로젝트 단톡방 API와 신호·입력 중 표시를 서버에 만들었다(12단계). 프로젝트 채팅 화면은 바꾸지 않았다(14단계).
- 추가하거나 바꾼 파일은 다음과 같다.
  - 백엔드 새 파일: `controller/ProjectChatController`, `service/ProjectChatService`, `service/MessengerRules`, `repository/ProjectChatAccessRepository`, `websocket/MessengerTypingRelay`, `dto/message`의 `ProjectChatListResponse`, `ProjectChatListItemResponse`, `ProjectChatDetailResponse`, `ProjectChatMemberResponse`, `ProjectChatMessageResponse`, `ChatSenderResponse`, `TypingFrame`
  - 백엔드 수정: `MessageController`(`type=project` 분기), `MessageService`(공통 규칙을 `MessengerRules`로 옮기고 입력 중 대상 조회 추가), `ChatRoom`·`ChatMember`(논리 삭제·퇴장), `ChatRoomRepository`·`ChatMemberRepository`·`MessageRepository`(프로젝트 방 조회), `MessengerWebSocketHandler`(typing 프레임), `MessengerSignalDispatcher`(`dispatch`), `MessengerRealtimeProperties`(입력 중 간격·캐시), `MessageCreatedFrame`·`MessageReadFrame`(프로젝트 신호)
  - 백엔드 테스트: 새 `ProjectChatApiIntegrationTest`(18), `ProjectChatRealtimeIntegrationTest`(6), `MessengerTypingTest`(8), `MessengerTypingRelayTest`(7), `ProjectChatTestData`. `MessengerRedisSignalIntegrationTest`에 다른 서버로 가는 입력 중 신호 1개를 더했다. `MessengerWebSocketTest`의 1008 목록에서 올바른 typing 프레임을 빼고 형식이 틀린 typing 프레임 9개를 넣었다. `MessageApiTests`의 400 목록에서 이제 지원하는 `type=project`를 `type=group`으로 바꿨다.
  - 프론트 새 파일: `lib/messaging/messengerSocket.ts`, `lib/state/messengerSocketStore.ts`, `app/_components/MessengerSocketSync.tsx`, 테스트 `lib/messaging/messengerSocket.test.ts`, 도우미 `tests/helpers/messengerSocketMock.ts`
  - 프론트 수정: `directChatStore`(신호·폴링 주기·입력 중), `directChatPoller`(주기 함수, 숨긴 탭 조회, `reschedule`), `ChatWindow`·`ChatWindow.module.css`(입력 중 표시), `useConversationPane`, `messages/chats/page.tsx`, `app/layout.tsx`, `.env.example`, 테스트 `directChatPoller.test.ts`·`directChatStore.test.ts`·`DirectChatScreens.test.tsx`
- 계획과 다르게 정한 점은 다음과 같다. 해당 절에도 반영했다.
  - 연결 모듈 이름을 `directChatSocket.ts` 대신 `messengerSocket.ts`로 했다. 프로젝트 채팅이 같은 연결을 쓴다.
  - 동시 첫 접근은 새 트랜잭션 재조회 대신 프로젝트 행 잠금으로 한 줄로 세운다(3.5). 요청마다 DB 연결 두 개를 잡지 않는다.
  - `ProjectMemberRepository` 대신 메신저 쪽 읽기 전용 리포지토리로 `project_members`를 읽는다. #85가 같은 파일을 고친다.
  - #48은 참여 기록이 있는 회원을 다시 승인하지 않아, 채팅에서 나간 회원은 다시 들어오지 않는 것으로 정했다(3.5).
  - 프로젝트 채팅방 삭제는 다시 요청해도 204로 응답한다(5번).
  - `NEXT_PUBLIC_MESSENGER_WS_PORT` 안내는 `frontend/.env.example` 맨 위에 두었다. develop이 같은 파일 끝에 값을 추가해, 끝에 두면 병합 충돌이 났다.
- 아래 검증은 모두 스크래치 복사본과 스크래치 컨테이너에서 실행했고 공용 DB에는 연결하지 않았다. 마지막 코드로 다시 실행한 결과다.
  - `./gradlew test`: 현재 작업 내용 604개 통과, 건너뜀 0. origin/develop(058397e)에 3-way로 적용한 복제본은 충돌 없이 794개 중 실패 0, 건너뜀 11(팀 DB 환경변수가 필요한 테스트).
  - 프론트: 현재 작업 내용은 `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 832개 중 831개 통과. 복제본은 `npx tsc --noEmit` 오류 0, `npm ci`, `npm run build` 성공, `npm test` 912개 중 911개 통과. 실패한 1개는 두 곳 모두 이전 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - 방어 코드를 하나씩 없애 확인했다. 백엔드 32개(입력 중 처리, 입장, 동시 접근 잠금, 참여 행 반영, 전송·읽음·삭제·제외, 목록·안 읽은 수), 프론트 33개(신호 처리, 폴링 주기, 입력 중, 재접속·종료 코드, 주소 규칙, 표시 위치)다. 처음에는 백엔드 1개(LEFT 회원의 ACTIVE 확인)와 프론트 1개(세션 확인 중 연결을 닫은 뒤의 만료 안내)를 테스트가 잡지 못했다. 참여 행에 퇴장이 반영되기 전 LEFT 회원의 읽음·삭제·입력 중 신호와, 닫은 뒤 돌아온 만료 결과를 테스트에 추가해 두 변경 모두 해당 테스트가 실패하는 것을 확인했다.
  - 시간에 민감한 테스트(`MessengerTypingTest`, `MessengerTypingRelayTest`, `ProjectChatRealtimeIntegrationTest`, `ProjectChatApiIntegrationTest`) 39개를 6번 반복해 234회 중 실패 0이다.
  - 운영과 같은 조합(jar, default 프로필, PostgreSQL 17, Redis, `next start`, `NEXT_PUBLIC_MESSENGER_WS_PORT`를 넣은 빌드)으로 띄우고, 로그인한 Chromium 2개로 12개 항목을 시험해 모두 통과했다. 토큰 만료를 확인하려고 액세스 토큰 수명을 75초로 줄였다. 서버 ERROR 로그는 0건이다.
    - 보낸 메시지가 상대 화면에 141~375ms, 읽음 표시가 101ms, 보고 있지 않은 대화의 헤더 배지가 36ms에 반영됐다.
    - 숨긴 탭은 신호로 메시지를 받고, 읽음 처리는 탭이 보일 때 했다.
    - 입력 중 표시가 대화 기록 밖에 나타나고 기록 영역의 위치와 크기가 바뀌지 않았다. 입력한 사람의 화면에는 나타나지 않았다. 신호는 3초에 한 번만 보냈고, 입력을 멈추면 5.0초 뒤, 상대 메시지가 오면 바로 사라졌다.
    - 연결을 막으면 폴링으로 5.0초에 받았고, 다시 연결된 뒤에는 147ms에 받았다.
    - 토글을 켜면 연결을 닫고 끄면 다시 연결했다. 4401로 닫힌 뒤 세션을 갱신해 다시 연결하고 다음 메시지를 146ms에 받았다. 로그아웃하면 연결을 닫았다.
  - 처음 브라우저 시험에서 4401 항목이 실패했다. 시험 준비가 쿠키용 토큰을 얻으려고 다시 로그인해, 쿠키의 리프레시 토큰이 서버에 남은 것과 달라진 것이 원인이었다(회원당 하나만 인정). 시험 준비를 고친 뒤 통과했고 제품 코드는 바꾸지 않았다. 브라우저 시험 스크립트는 스크래치에만 두었다.
- 시험하지 않은 항목은 다음과 같다.
  - nginx와 `wss`를 거친 경로와 실제 블루그린 전환(13·16단계), Chromium 외의 브라우저, compose 개발 컨테이너
  - #48의 실제 승인 코드로 만든 팀원. PR #85 병합 전이라 같은 결과의 행을 SQL로 넣었다.
  - 프로젝트 채팅 화면(14단계). 프로젝트 API와 신호는 MockMvc와 실제 WebSocket 클라이언트 테스트로만 확인했다.
  - 입력창을 비울 때 입력 중 신호를 보내지 않는 동작은 코드에만 있고 테스트가 없다.
  - `local`(H2) 프로필의 프로젝트 API(테이블이 없어 500, 7번)
- 미완료: 13단계 이후 전체. 다음 단계는 13단계다. 12단계는 #48 병합 뒤 실제 승인 팀원으로 다시 검증한다.

### 2026-11-08 13~15단계

- 블루그린 스크립트와 팀 nginx에 `/ws/messages` 경로를 넣고(13단계), 프로젝트 채팅 화면·사이드바·헤더 배지를 서버 데이터로 연결했다(14단계). 전체를 다시 검증하고 확정 계약을 [메신저 API](messenger-api.md)로 옮겼다(15단계). 운영 배포(16단계)는 하지 않았다.
- 추가하거나 바꾼 파일은 다음과 같다.
  - 배포: `deploy/blue-green.sh`(두 업스트림 기록, 시작 부분의 `active.conf` 갱신, `server.conf`가 바뀌면 전환 전 nginx 재시작, 끝의 핸드셰이크 확인과 종료 코드 3), `nginx/server.conf`, `nginx/local.conf`, `deploy/setup-host.sh`, `scripts/test_blue_green.py`(8개 추가, 모두 12개)
  - 백엔드: `dto/message`의 `ChatSenderResponse`(`memberId`), `ProjectChatMemberResponse`(`id`, `me`), `ProjectChatMessageResponse`, `ProjectChatService`(내 참여 행 구분), 테스트 `ProjectChatApiIntegrationTest`
  - 프론트 새 파일: `lib/messaging/projectChatApi.ts`, `lib/messaging/projectChatView.ts`, `lib/state/projectChatStore.ts`, `app/api/messages/projects/[id]`의 `route.ts`·`read`·`delete`, `app/api/messages/projects/unread-count`, `app/messages/_hooks/useProjectConversations.ts`, `app/messages/projects/[projectId]/_components/ServerProjectChat.tsx`, 테스트 `projectChatStore.test.ts`, `projectChatView.test.ts`, `messagingProxy.test.ts`, `app/api/messages/projects/[id]/route.test.ts`, `tests/screens/ProjectChatScreens.test.tsx`, 도우미 `tests/helpers/projectChatFixture.ts`
  - 프론트 수정: `types/messaging.ts`, `directChatApi.ts`(공통 확인 함수 공개), `messagingProxy.ts`(같은 사이트 확인), `app/api/messages/chats/route.ts`(`type`별 응답 확인), `ConversationSidebar`, `messages/projects/[projectId]/page.tsx`, `AppLayout`, `DirectChatSync`, 테스트 `DirectChatScreens.test.tsx`와 BFF 테스트 4개(`Host` 헤더 추가)
  - 문서: 새 `docs/messenger-api.md`. `api-plan.md`, `team-auth-deployment.md`, `deployment-operations.md`, `mock-data.md`, `frontend-pages.md`, `decisions-pending.md`, `project-backend-blueprint.md`, `demo-scenarios.md`, `README.md`, 이 문서
- 계획과 다르게 정하거나 새로 정한 점은 다음과 같다. 해당 절에도 반영했다.
  - 프로젝트 채팅 조회 응답에 `members[].id`, `me`, `sender.memberId`를 더했다. 태그가 없는 회원도 읽음 수와 작성자를 구분하기 위해서다(5번).
  - 메신저 BFF의 같은 사이트 확인을 Origin과 `request.url`의 비교에서, Next 서버 액션과 같은 Origin 호스트와 `X-Forwarded-Host`·`Host`의 비교로 바꿨다(3.3). 운영 이미지는 Next 서버를 `HOSTNAME=0.0.0.0`으로 띄워 `request.url`이 `0.0.0.0` 주소가 되므로, 이전 코드로는 운영과 compose 개발 환경에서 메시지 전송·읽음·대화 시작·신고·삭제가 모두 403이 된다. 5단계부터 있던 결함이며, 이전 검증은 호스트에서 `next start`를 호스트 이름 없이 띄워 드러나지 않았다. 운영에 배포되기 전에 고쳤다. 같은 방식을 쓰는 프로필 BFF는 바꾸지 않고 15단계의 PR 알림에 넣었다.
  - `api-plan.md`의 메신저 안내는 develop이 같은 줄을 고쳐 충돌하므로 별도 문단으로 두었다.
- 아래 검증은 모두 스크래치 복사본과 스크래치 컨테이너에서 실행했고 공용 DB와 사용자의 컨테이너는 쓰지 않았다. 마지막 코드로 다시 실행한 결과다.
  - `./gradlew test`: 현재 작업 내용 604개 통과, 건너뜀 0. origin/develop(058397e)에 3-way로 적용한 복제본은 794개 중 실패 0, 건너뜀 11(팀 DB 환경변수가 필요한 테스트).
  - 프론트: 현재 작업 내용은 `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 877개 중 876개 통과. 복제본은 `npm ci`, `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 957개 중 956개 통과. 실패한 1개는 두 곳 모두 이전 기록과 같은 `lib/projects/projectService.test.ts`의 공고 생성 테스트다.
  - CI의 배포 설정 확인(`bash -n`, `test_blue_green.py` 12개, `compose.prod.yaml` 설정 확인)이 두 곳 모두 통과했다.
  - 복제본 적용에서 `AGENTS.md`가 develop의 비밀번호 재설정 변경과 같은 줄을 고쳐 충돌했다. 병합할 때 두 내용을 모두 남기면 된다. 다른 파일은 충돌하지 않았다.
  - 운영과 같은 조합(jar, default 프로필, PostgreSQL 17, Redis, `next start`)으로 띄우고 로그인한 Chromium으로 시험했다. 서버 ERROR 로그는 0건이다.
    - 프로젝트 채팅 9개: 사이드바 목록에서 입장, 메시지가 상대 화면에 361ms에 표시되고 읽음 반영, 새로 승인된 팀원의 입장·들어온 문구·읽음 2/2, 두 명의 입력 중 표시, 1:1과 프로젝트의 배지 합산, 참여 취소 뒤 입장 거부와 나간 문구, 팀원이 아닌 회원 차단, 1440px, 종료된 프로젝트의 읽기 전용과 삭제 뒤 입장 거부(메시지 행 5개와 삭제 시각 유지). 프로젝트와 승인 팀원은 #48과 같은 결과의 행을 SQL로 넣었다.
    - 시나리오 8개: 프로필에서 대화 시작과 첫 메시지 382ms, 새로고침 유지와 이전 메시지 더 보기, 탭 8개 모두 428ms 수신과 다른 요청 46ms, 제3자 차단, 신고와 중복 신고, Redis 정지 중 같은 서버 전달과 재시작 뒤 구독 복구(281ms, 구독 1), 1440px, 토글 ON의 Mock 화면 회귀
    - 1:1 실시간 11개(액세스 토큰 75초로 4401 재연결 포함)와 `local` 프로필 10개. `local`에서 프로젝트 채팅 API가 500인 것은 7번의 제한과 같다.
  - 개발용 compose(bootRun, `next dev`, PostgreSQL, Redis)를 스크래치로 복사해 띄웠다. 처음에는 위의 403으로 메시지가 전달되지 않았고, 고친 뒤 1:1 실시간 10개가 통과했다. `next dev`의 모든 화면에서 "instant navigation" 콘솔 오류와 첫 화면의 두 번째 탐색이 나타났는데, 이 작업 전 코드(HEAD)에서도 같아 메신저와 관계없는 개발 모드 동작이다. 두 번째 탐색 때 첫 연결이 1006으로 닫히고 바로 다시 연결된다.
  - 블루그린을 로컬에서 실제 이미지로 시험했다(old는 develop, new는 이 작업을 적용한 develop).
    - develop에서 한 번에 배포: nginx 재시작, 스크립트의 확인 401, 사이트 200, Origin 일치 401·없음 403·다른 값 403, `/actuator/health`·`/api/v1`·`/ws/messages/x`는 백엔드로 가지 않음
    - 브라우저: 팀 nginx를 거친 연결, 쓰기 요청 성공(전송 201, 읽음 204), 전환 중 보낸 15개가 모두 한 번씩 표시, 이전 색이 멈출 때 1001 뒤 재연결, 전환 뒤 357ms 표시
    - 되돌리기: 이전 스크립트의 전환은 실패하고 사이트는 200, nginx 재시작 뒤 다시 배포하면 성공
    - 헬스체크 전에 실패한 배포: `active.conf`에 백엔드 업스트림이 남고 nginx를 다시 시작해도 사이트 200, `/ws/messages` 401
    - 대조: 이전 방식이면 같은 상황에서 nginx가 `backend_upstream`을 찾지 못해 사이트가 멈춘다. `nginx/local.conf`는 `nginx -t`를 통과했다.
  - 방어 코드를 하나씩 없애 확인했다(프론트 20개: 신호 처리, 읽음 위치, 입력 중, 읽기 전용, 숨긴 탭, 404, 문구, 응답 확인, BFF, 토글, 배지). 숨긴 탭의 프로젝트 신호 조회와 나간 팀원의 읽음 제외를 처음에는 테스트가 잡지 못해 테스트를 더했고, 두 변경 모두 해당 테스트가 실패하는 것을 확인했다. 같은 사이트 확인 테스트는 이전 코드에서 실패한다.
  - 시험 준비를 두 번 고쳤다. 입력 중 단계가 앞 단계의 3초 제한 안에서 입력해 신호가 보내지지 않았고(정상 동작), 삭제 단계의 메시지 수 기대값이 실제 보낸 수보다 컸다. 제품 코드는 바꾸지 않았다.
- 시험하지 않은 항목은 다음과 같다.
  - 실제 서버의 호스트 nginx와 `wss`(16단계), Chromium 외의 브라우저
  - #48의 실제 승인 코드로 만든 팀원. PR #85 병합 전이라 같은 결과의 행을 SQL로 넣었다.
  - 사용자의 수동 시나리오 확인
- 미완료: 15단계의 사용자 수동 확인과 16단계(운영 배포). 14단계는 #48 병합 뒤 실제 승인 팀원으로 다시 검증한다.

### 2026-11-08 1:1 입력 중 말풍선

- 사용자 요청으로 1:1의 입력 중 표시를 문구에서 말풍선으로 바꿨다. 상대가 입력하는 동안 대화 기록 맨 아래에 상대 말풍선이 생기고 그 안에서 점 세 개가 차례로 오르내린다. 프로젝트 채팅은 사용자 결정으로 바꾸지 않았다(기록 밖 문구). 신호와 서버는 바꾸지 않았다.
- 바꾼 파일: `ChatWindow.tsx`(`typing.display`의 `text`·`bubble`, 말풍선, 말풍선이 생기거나 사라질 때의 스크롤), `ChatWindow.module.css`, `useConversationPane.ts`(1:1은 `bubble`), 테스트 `DirectChatScreens.test.tsx`·`ProjectChatScreens.test.tsx`, 문서 `demo-scenarios.md`와 이 문서(3.3, 11단계)
- 정한 점은 다음과 같다.
  - 말풍선은 대화 기록의 마지막 항목이다. 화면 읽기 프로그램에서는 숨기고, "○○님이 입력 중이에요…"는 기록 밖의 보이지 않는 상태 영역(`role="status"`)이 읽는다. 기록(`aria-live`)에 넣으면 기록이 다시 읽힌다.
  - 맨 아래를 보고 있으면 말풍선이 보이게 따라 내려가고, 위로 올려 읽는 중에는 움직이지 않는다. 기록 밖에 두어 화면이 움직이지 않게 했던 11단계의 결정은 1:1에서 이 규칙으로 바뀌었다.
  - 점은 메시지 글자와 같은 문단 규칙 안에 두어 한 줄 메시지와 높이가 같다. 실제 메시지가 오면 그 자리에 들어와 덜 움직인다. 움직임 줄이기 설정에서는 점이 멈춘다. 이름과 시각은 붙이지 않는다.
- 검증은 스크래치 복사본에서 실행했다.
  - 프론트: `npx tsc --noEmit` 오류 0, `npm run build` 성공, `npm test` 877개 중 876개 통과(실패 1개는 이전과 같은 `projectService.test.ts`)
  - 운영과 같은 조합(jar, PostgreSQL, Redis, `next start`)에서 Chromium으로 1:1 실시간 11개와 프로젝트 채팅 9개가 통과했다. 말풍선은 1,172ms에 나타났고 점 세 개의 애니메이션이 동작했다. 높이는 한 줄 메시지와 같은 55px이었다. 맨 아래를 따라가고, 위로 올린 동안은 움직이지 않았다. 입력을 멈추면 5.0초 뒤 사라졌고, 메시지가 오면 그 자리에 들어왔다. 움직임 줄이기에서는 멈췄다. 프로젝트 채팅은 기존 문구 그대로였다.
  - 시험 준비를 한 번 고쳤다. 메시지를 연달아 보내는 시험이 앞 전송이 끝나기 전에 다음 글을 넣어, 앞 전송의 성공 처리가 입력창을 비웠다.
- 시험하지 않은 항목: Chromium 외의 브라우저, 실제 화면 읽기 프로그램

## #49 연결 변경 (2026-11-10)

채팅 멤버 응답에 projectMemberId를 추가하고 project.members.changed 신호로 멤버·목록·투표를 재조회한다. 가결 시 프로젝트 KICKED 이력과 채팅 퇴장을 같은 트랜잭션에서 처리한다. 주최자는 강퇴 대상에서 제외한다. 자세한 계약과 전역 알림함 연결의 미완료 범위는 [강퇴 투표](project-member-removal.md)를 참조한다. 기존 단계의 ‘Mock 전용’ 설명은 당시 구현 이력이다.

## 2026-11-11 강퇴 투표를 대화 시간순으로 표시

프로젝트 채팅의 강퇴 투표 카드는 생성 시각에 해당하는 메시지 사이에 표시한다. 이후 메시지는 카드 아래에 쌓이며 투표 수·종료 상태 갱신으로 위치를 바꾸지 않는다. 실제 API와 Mock 채팅에 함께 적용했다. 서버 투표의 한국 LocalDateTime과 채팅 offset 시각을 같은 기준으로 비교한다. 새 투표가 마지막 항목으로 추가되면 하단 열람 중에만 따라가고, 이전 대화를 읽는 중에는 강제로 이동하지 않는다. 백엔드·DB 계약 변경 없음.

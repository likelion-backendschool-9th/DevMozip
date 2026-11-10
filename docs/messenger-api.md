# 메신저 API

1:1 메신저(WBS 11.1~11.9)와 프로젝트 단톡방(7.18~7.23)의 확정 계약이다. 구현 과정과 결정 이유는 [메신저 구현 계획](messenger-plan.md)에 있다.

## 1. 공통 규칙

- 백엔드 기본 경로는 `/api/v1`이다. 브라우저는 백엔드를 직접 부르지 않고 같은 Origin의 BFF(`/api/messages/...`, `/api/users/{slug}/reports`)를 부른다. BFF가 HttpOnly 쿠키의 토큰을 Bearer로 바꿔 전달한다. WebSocket만 BFF를 거치지 않는다(5번).
- 요청한 회원은 인증 정보(토큰의 이메일)로만 정한다. 요청 본문이나 쿼리의 회원 ID를 신원으로 쓰지 않는다.
- 참여하지 않은 대화방, 입장할 수 없는 프로젝트 채팅은 존재를 드러내지 않도록 403이 아니라 404로 응답한다.
- 시각은 서울 시간에 `+09:00`을 붙여 응답한다. BFF는 밀리초까지 잘라 전달한다.
- 메시지 본문은 앞뒤 공백을 제거한 뒤 1~2,000자이고 NUL 문자를 쓸 수 없다. 일반 텍스트로만 표시한다.
- 메시지 조회는 파라미터가 없으면 최신 메시지, `before`는 이전 기록, `after`는 새 메시지다. 결과는 항상 오름차순이다. `after` 결과가 `size`개이면 마지막 ID로 다시 조회한다. `size` 기본 50, 최대 100이다.
- 쓰기 BFF는 같은 Origin의 `application/json` 요청만 받는다(403, 415). 같은 Origin은 Next 서버 액션과 같이 Origin의 호스트가 `X-Forwarded-Host`나 `Host`와 같은지로 판단한다. 컨테이너의 Next 서버는 `HOSTNAME=0.0.0.0`으로 떠서 `request.url`이 브라우저 주소와 다르므로 비교에 쓰지 않는다. BFF는 허용한 쿼리 파라미터와 응답 필드만 전달하고, 백엔드 오류 내용 대신 경로별 안내 문구를 돌려준다.

## 2. 경로

| WBS | 브라우저(BFF) | 백엔드 | 역할 |
|---|---|---|---|
| 11.1 | `GET /api/messages/chats?type=&q=&cursor=&size=` | `GET /messages/chats` | 채팅방 목록. `type`은 `direct`(기본값), `project` |
| 11.5 | `POST /api/messages/chats` | `POST /messages/chats` | 1:1 대화 시작. `{ "targetSlug": "..." }` → 200 `{ "id": 12 }`. 이미 있으면 기존 방 |
| 11.2 | `GET /api/messages/chats/{id}?before=&after=&size=` | `GET /messages/chats/{id}` | 1:1 메시지 조회 |
| 11.3 | `POST /api/messages/chats/{id}` | `POST /messages/chats/{id}` | 1:1 메시지 전송. `{ "body": "..." }` → 201 + 메시지. 보낸 사람의 읽음 위치도 옮긴다 |
| 11.6 | `PATCH /api/messages/chats/{id}/read` | `PATCH /messages/chats/{id}/read` | 읽음 처리. `{ "lastReadMessageId": 340 }` → 204. 더 이전 메시지는 무시한다 |
| 11.7 | `GET /api/messages/chats/unread-count` | `GET /messages/chats/unread-count` | 1:1 안 읽은 수 `{ "count": 3 }` |
| 11.4 | `POST /api/users/{slug}/reports` | `POST /users/{slug}/reports` | 사용자 신고. `{ "reason": "스팸 또는 광고" }` → 201 본문 없음 |
| 7.19 | `GET /api/messages/projects/{id}?before=&after=&size=` | `GET /messages/projects/{id}` | 프로젝트 채팅 조회. 첫 접근이면 방을 만든다 |
| 7.19 | `POST /api/messages/projects/{id}` | `POST /messages/projects/{id}` | 프로젝트 메시지 전송 → 201 + 메시지 |
| 7.19 | `POST /api/messages/projects/{id}/delete` | `POST /messages/projects/{id}/delete` | 방 논리 삭제 → 204. 종료된 프로젝트의 주최자만. 다시 보내도 204 |
| 7.20 | `PATCH /api/messages/projects/{id}/read` | `PATCH /messages/projects/{id}/read` | 읽음 처리. 1:1과 같다 |
| 7.21 | `GET /api/messages/projects/unread-count` | `GET /messages/projects/unread-count` | 프로젝트 채팅 안 읽은 수 합계 |
| 11.8·11.9·7.22·7.23 | `/ws/messages`(WebSocket) | 같음 | 신호 수신, 입력 중 전송(5번) |

`{id}`는 1:1이면 대화방 ID, 프로젝트면 프로젝트 ID다. 프로젝트 입장 전용 API는 없다(7.18).

## 3. 응답

**`GET /messages/chats?type=direct`**

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

메시지가 있는 방만 최근 순이다. `q`는 상대 닉네임, `cursor`는 마지막 메시지 ID다. `size` 기본 30, 최대 100이다.

**`GET /messages/chats/{id}`**

```json
{
  "id": 12,
  "peer": { "tag": "sample_dev", "nickname": "샘플개발자", "profileImage": null, "jobCategories": ["백엔드"], "collaborationStatus": "AVAILABLE" },
  "canSend": true,
  "peerLastReadMessageId": 339,
  "messages": [{ "id": 340, "body": "안녕하세요", "mine": false, "createdAt": "2026-11-06T10:15:00+09:00" }],
  "nextBefore": null
}
```

`canSend`는 상대가 정지되면 false다. `tag`와 `nickname`은 아직 설정하지 않은 회원이면 null이다.

**`GET /messages/chats?type=project`**

```json
{
  "items": [{ "id": 5, "title": "샘플 프로젝트", "status": "IN_PROGRESS", "memberCount": 4, "unreadCount": 2 }],
  "nextCursor": null
}
```

`project_members`가 ACTIVE이고 모집 시작일(서울 날짜)이 지난 프로젝트를 프로젝트 ID 역순으로 반환한다. 방 행이 아직 없어도 포함하고, 삭제된 방·프로젝트와 채팅에서 제외된 방은 뺀다. `q`는 프로젝트명, `cursor`는 프로젝트 ID다. `memberCount`는 ACTIVE 멤버 중 채팅에서 제외되지 않은 인원이다. `status`는 `RECRUITING`, `RECRUITMENT_COMPLETED`, `IN_PROGRESS`, `COMPLETED`다.

**`GET /messages/projects/{id}`**

```json
{
  "projectId": 5,
  "title": "샘플 프로젝트",
  "status": "IN_PROGRESS",
  "canSend": true,
  "isHost": false,
  "members": [
    { "id": 31, "me": false, "tag": "sample_dev", "nickname": "샘플개발자", "profileImage": null, "role": "HOST",
      "lastReadMessageId": 339, "joinedAt": "2026-11-01T09:00:00+09:00", "leftAt": null }
  ],
  "messages": [
    { "id": 340, "body": "안녕하세요", "mine": false, "sender": { "memberId": 31, "tag": "sample_dev", "nickname": "샘플개발자" },
      "createdAt": "2026-11-06T10:15:00+09:00" }
  ],
  "nextBefore": null
}
```

- `members`는 나간 회원도 포함한다. `id`는 이 방의 참여 행 ID, `me`는 요청한 회원인지다. `sender.memberId`는 `members[].id`와 같아 태그가 없는 회원도 구분한다.
- `joinedAt`, `leftAt`은 승인·취소 시각이 아니라 채팅에 반영된 시각(다음 접근 시각)이다. 화면은 이 값으로 초대·퇴장 문구를 표시한다.
- 조회와 전송 때 `project_members`와 비교해 승인된 회원의 참여 행을 만들고 ACTIVE가 아닌 회원에 `leftAt`을 채운다.
- `canSend`는 프로젝트가 `COMPLETED`면 false다.
- 안 읽은 수는 `last_read_at` 뒤의 메시지 중 내가 보내지 않은 것이며, 승인 전에 오간 메시지도 센다. 참여 행이 생기기 전(다음 접근 전)에는 0이다.

## 4. 오류

공통 [ErrorResponse](../backend/src/main/java/com/devmozip/backend/dto/common/ErrorResponse.java) 형식이다. BFF는 같은 상태 코드에 안내 문구만 담는다.

| 상황 | 상태 |
|---|---|
| 로그인하지 않음, 토큰 만료 | 401 |
| 없는 대화방, 참여하지 않은 대화방, 없는 대상, 대화를 시작할 수 없는 상태의 대상, 입장할 수 없는 프로젝트 채팅(멤버 아님·LEFT·제외·모집 시작 전·삭제된 방·삭제된 프로젝트) | 404 |
| 주최자가 아닌 멤버의 프로젝트 채팅 삭제 | 403 |
| 본인과의 대화·본인 신고, 본문 길이 위반, 없는 신고 사유, `before`와 `after` 동시 사용, 범위를 벗어난 `size`, 숫자가 아닌 파라미터, 지원하지 않는 `type`, 다른 방의 메시지 ID | 400 |
| 상대가 정지되어 전송 불가, 종료된 프로젝트 채팅에 전송, 종료 전 프로젝트 채팅 삭제, 중복 신고 | 409 |
| BFF: 다른 Origin의 쓰기 요청 / JSON이 아닌 쓰기 요청 / 백엔드 연결 실패·잘못된 응답 | 403 / 415 / 502 |

## 5. WebSocket

### 연결

- 주소는 `/ws/messages` 하나다. 1:1과 프로젝트 채팅이 연결 하나를 함께 쓴다. 회원 단위로 전달하므로 참여 대화방 수와 무관하다.
- HTTPS 페이지는 같은 Origin의 `wss`로 연결한다(운영은 호스트 nginx → 팀 nginx → 활성 색 backend). 로컬 HTTP 페이지는 `NEXT_PUBLIC_MESSENGER_WS_PORT`가 있으면 그 포트의 백엔드로 바로 연결한다([frontend/.env.example](../frontend/.env.example)).
- 핸드셰이크는 `devmozip_access` 쿠키와 Origin으로 확인한다. Origin은 백엔드의 `DOMAINNAME`(`local` 프로필 기본값 `http://localhost:3000`)과 정확히 같아야 한다.
- 핸드셰이크 거부: Origin 없음·불일치와 정지 회원은 403, 쿠키 없음·위조·만료·없는 회원은 401이다. 브라우저 JavaScript는 이 상태 코드를 볼 수 없고 열리기 전의 1006만 본다.
- 메시지 본문은 WebSocket과 Redis를 거치지 않는다. 신호를 받으면 REST `after`로 본문을 조회한다.

### 프레임

JSON 텍스트 프레임이며 최대 4,096자다. `roomId`는 1:1이면 대화방 ID, 프로젝트면 프로젝트 ID다.

| 방향 | 프레임 | 의미 |
|---|---|---|
| 서버 → 클라이언트 | `{"type":"message.created","roomType":"direct","roomId":12,"messageId":341}` | 새 메시지. 커밋 후 보낸다. 보낸 사람의 다른 탭에도 간다 |
| 서버 → 클라이언트 | `{"type":"message.read","roomType":"direct","roomId":12,"lastReadMessageId":341}` | 읽음 위치가 실제로 옮겨졌을 때만 참여자 모두에게 보낸다. 프로젝트 방은 읽은 사람의 `"memberTag"`를 붙인다(태그가 없으면 빠진다) |
| 서버 → 클라이언트 | `{"type":"typing","roomType":"project","roomId":5,"nickname":"샘플개발자"}` | 입력 중. 1:1은 `nickname`이 없다. 보낸 사람의 탭에는 가지 않는다 |
| 서버 → 클라이언트 | `{"type":"ping"}` | 30초마다. 클라이언트는 `pong`으로 답한다 |
| 클라이언트 → 서버 | `{"type":"pong"}` | 연결 확인 응답 |
| 클라이언트 → 서버 | `{"type":"typing","roomType":"direct","roomId":12}` | 입력 중. 3초에 한 번 보낸다 |

- `typing`의 `roomType`이 `direct`·`project`가 아니거나 `roomId`가 양의 정수가 아니면 1008로 닫는다. 참여하지 않았거나 보낼 수 없는 방(상대 정지, 종료·삭제된 프로젝트)이면 연결을 유지한 채 무시한다.
- 서버는 회원·방당 1초에 한 번 넘게 온 `typing`을 무시한다. 참여 여부는 연결마다 10초 기억한다.
- 받는 화면은 입력 중 표시를 5초 뒤나 그 사람의 메시지가 도착하면 지운다.

### 종료 코드

| 코드 | 상황 | 클라이언트 동작 |
|---|---|---|
| 1001 | 서버 종료, 배포(블루그린 전환) | 재접속 |
| 4401 | 토큰 만료(연결 중 1초 주기 확인) | `/api/auth/session`으로 갱신한 뒤 바로 재접속. 세션이 없으면 로그인 안내 |
| 4403 | 정지 회원 | 재접속하지 않음 |
| 4500 | 70초 동안 `pong` 없음, 전송 5초 초과·버퍼 64KB 초과 | 재접속 |
| 1008, 1009 | 허용되지 않은 프레임, 4,096자 초과 | 재접속 |
| 1006 | 핸드셰이크 거부, 네트워크 끊김 | 열리기 전이면 `/api/auth/session` 확인 후 재접속. 세션이 없으면 로그인 안내 |

재접속은 1초에서 두 배씩 30초까지 늘어나는 상한의 절반에 무작위 지연을 더해 기다린다. 연결되면 `after`로 놓친 메시지를 조회하고, 연결 중에는 60초마다, 끊기면 열린 대화 5초·목록과 배지 30초마다 폴링한다.

### 서버 간 전달

블루그린의 두 색이 같은 Redis를 쓴다. 커밋 후 전용 스레드가 Redis 채널 `messenger:signals`에 `{"recipients":[1,2],"frame":"{...}"}`를 발행하고, 모든 서버가 구독해 자기에게 연결된 회원에게 보낸다. Redis 발행이 실패하면 5초 동안 같은 서버로 직접 전달한다. 입력 중 신호도 같은 경로다.

## 6. 설정

| 속성 | 기본값 | 설명 |
|---|---|---|
| `messenger.realtime.allowed-origin` | `${DOMAINNAME}` | 허용 Origin. 비어 있으면 모든 핸드셰이크를 거부한다. `local` 프로필은 `http://localhost:3000` |
| `messenger.realtime.redis.enabled` | `true` | false면 같은 서버로만 전달한다. `local`·`test`는 false |
| `messenger.realtime.ping-interval` / `pong-timeout` / `check-interval` | 30s / 70s / 1s | 연결 확인. nginx 타임아웃(75초)보다 짧다 |
| `messenger.realtime.send-time-limit` | 5s | 느린 연결의 전송 한도 |
| `messenger.realtime.typing-interval` / `typing-access-cache` | 1s / 10s | 입력 중 간격 제한, 참여 여부 기억 시간 |

## 7. 운영 경로

- 팀 nginx([server.conf](../nginx/server.conf))는 정확히 `/ws/messages`만 `backend_upstream`으로 보낸다. `backend_upstream`은 [블루그린 스크립트](../deploy/blue-green.sh)가 `nginx/upstream/active.conf`에 활성 색으로 쓴다.
- 호스트 nginx의 같은 경로는 [setup-host.sh](../deploy/setup-host.sh) 템플릿과 [계획 16단계](messenger-plan.md)에 있다.
- 블루그린 배포는 끝에 팀 nginx로 핸드셰이크를 확인한다. 쿠키 없이 `DOMAINNAME` Origin으로 요청해 401이면 정상이다. 실패하면 배포는 유지하고 종료 코드 3으로 알린다(404 경로 누락, 403 `DOMAINNAME` 불일치).

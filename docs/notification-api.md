# 알림 API (WBS 12.1~12.5)

[알림 계획](notification-plan.md) 2~4단계에서 구현한 API와 내부 이벤트 처리다. 결정 근거와 단계별 기록은 계획 문서에 있다.

## 공통

- 모든 경로는 로그인이 필요하다(비로그인 401). 요청자는 JWT의 이메일로만 찾으며, 요청 본문이나 쿼리의 사용자 ID는 쓰지 않는다.
- 본인 알림만 조회·변경한다. 다른 사람의 알림 ID는 없는 알림과 같이 404다.
- 알림을 만드는 외부 API는 없다. 프로젝트·리뷰 도메인이 발행한 내부 이벤트를 받아 서버가 만든다(아래 "생성").
- 오류는 공통 `ErrorResponse` 형식(`status`, `error`, `message`, `path`, `timestamp`)이다.
- 시각은 한국 시각(`projectClock`, Asia/Seoul)의 `LocalDateTime`이며 시간대 표기 없이 `2026-11-10T16:05:00.123456` 형식이다(프로젝트·리뷰 API와 같다).

| 코드 | 메서드·경로 | 동작 |
|---|---|---|
| 12.1·12.4·12.5 | `GET /api/v1/notifications` | 본인 알림 목록과 전체 안 읽음 수 |
| 12.2 | `PATCH /api/v1/notifications/{id}/read` | 읽음 처리. 본문으로 안 읽음 전환도 한다(D5 A) |
| D5 A (WBS 외) | `PATCH /api/v1/notifications/read-all` | 본인 알림 모두 읽음 |

## 목록

`GET /api/v1/notifications`

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `status` | `all` | `all`, `unread`(읽지 않았고 처리 완료가 아닌 알림), `read`(그 밖의 알림). 소문자만 받는다 |
| `page` | `0` | 0 이상 |
| `size` | `20` | 1~100 |

```json
{
  "items": [
    {
      "id": 31,
      "type": "PROJECT_REVIEW_REQUESTED",
      "title": "협업 리뷰 작성 요청",
      "body": "프로젝트 이름: 2026-12-06 12:00까지 본인을 제외한 팀원을 평가해 주세요.",
      "actionUrl": "/projects/12/reviews",
      "projectId": 12,
      "read": false,
      "resolved": false,
      "review": { "status": "PENDING", "expiresAt": "2026-12-06T12:00:00" },
      "createdAt": "2026-11-06T12:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false,
  "unreadCount": 3
}
```

- 최신순(`created_at` 내림차순, 같으면 `id` 내림차순)이다. `hasNext`는 한 건을 더 읽어 정한다.
- `read`는 읽은 시각(`read_at`)이 있는지다. `resolved`는 리뷰 작성 완료·기간 만료로 처리가 끝났는지이며 `read`와 따로 계산한다.
- `review`는 리뷰 알림(`PROJECT_REVIEW_REQUESTED`, `PROJECT_REVIEW_REMINDED`)에만 있고 다른 종류는 `null`이다.
  - `status`: `PENDING`(작성 대기), `COMPLETED`(작성 완료), `EXPIRED`(기간 만료).
  - 조회할 때 `reviews`로 계산한다(D4 A). 알림의 `review_id` 행 작성자가 같은 프로젝트에서 가진 행에 `PENDING`이 없으면 작성 완료, `PENDING`이 남았고 지금이 그 행의 `expires_at` 이상이면 기간 만료다.
  - `expiresAt`은 `review_id` 행의 만료 시각이다.
- `resolved`는 리뷰 상태가 `COMPLETED`·`EXPIRED`이면 `true`, 다른 종류는 항상 `false`다. 처리 완료 알림은 읽음 여부와 관계없이 `unread`에서 빠지고 `unreadCount`에 세지 않는다.
- `unreadCount`는 탭·페이지와 관계없는 본인의 전체 안 읽음 수다. 헤더 배지도 이 값을 쓴다(배지만 갱신할 때는 `size=1`).
- `token`, `review_id`, 수신자 ID는 응답에 넣지 않는다.
- `actionUrl`은 서버가 만든 내부 경로다. 리뷰 알림은 `/projects/{id}/reviews`이며 리뷰 임시 토큰을 넣지 않는다(D3 A). 리뷰 화면이 로그인한 평가자에게 "리뷰 작성 시작"으로 링크를 발급한다.

## 읽음·안 읽음

`PATCH /api/v1/notifications/{id}/read`

| 본문 | 결과 |
|---|---|
| 없음 | 읽음 |
| `{ "read": true }` | 읽음 |
| `{ "read": false }` | 안 읽음 |

```json
{ "id": 31, "read": true, "unreadCount": 2 }
```

- 이미 읽은 알림을 다시 읽음 처리하면 처음 읽은 시각을 유지한다. 반복해도 결과가 같다.
- 리뷰 알림의 읽음은 리뷰 작성 상태를 바꾸지 않고, 리뷰를 제출해도 `read`는 바뀌지 않는다(12.2).
- `unreadCount`는 변경 후 본인의 전체 안 읽음 수다.

## 모두 읽음 (D5 A, WBS 외)

`PATCH /api/v1/notifications/read-all` (본문 없음)

```json
{ "unreadCount": 0 }
```

- 본인의 안 읽은 알림을 모두 읽음으로 바꾼다. 이미 읽은 알림의 읽은 시각은 그대로다.

## 오류

| 상태 | 상황 |
|---|---|
| 400 | `status`·`page`·`size` 형식 오류(값이 여러 개인 경우 포함), 숫자가 아닌 알림 ID, 읽음 본문 형식 오류(`{}`, `{"read":null}`, 문자열 값, 배열, 깨진 JSON) |
| 401 | 로그인 필요 |
| 404 | 없거나 다른 사람의 알림 |

## 생성 (내부 이벤트)

알림은 `NotificationService.create`로만 만든다. `NotificationEventHandler`가 원래 요청이 커밋된 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) 이벤트를 받아 수신자별로 새 트랜잭션에서 저장한다.

- 롤백된 요청은 알림을 만들지 않는다. 알림 저장이 실패해도 원래 요청의 결과는 바뀌지 않고, 종류·수신자 ID와 예외 이름만 로그에 남긴다.
- 같은 수신자에게 같은 `token`의 알림은 하나만 만든다. 동시에 들어와 유일 제약에 걸려도 무시한다.
- 1:1 메시지·단체 채팅방으로는 보내지 않는다. 외부 푸시·메일도 보내지 않는다.

| `type` | 이벤트 | 수신자 | 제목 | 본문 | `actionUrl` | `token` |
|---|---|---|---|---|---|---|
| `PROJECT_APPLICATION_SUBMITTED` | `ProjectApplicationChanged` SUBMITTED | 주최자 | 프로젝트 지원 도착 | {지원자 닉네임}님이 지원했습니다. | `/projects/{id}` | 이벤트 `eventKey` |
| `PROJECT_APPLICATION_CANCELLED` | 같은 이벤트 CANCELLED | 주최자 | 프로젝트 지원 취소 | {지원자 닉네임}님이 지원을 취소했습니다. | `/projects/{id}` | 이벤트 `eventKey` |
| `PROJECT_APPLICATION_APPROVED` | 같은 이벤트 APPROVED | 지원자 | 지원 승인 | {프로젝트}: 팀원으로 등록되었습니다. | `/projects/{id}` | 이벤트 `eventKey` |
| `PROJECT_APPLICATION_REJECTED` | 같은 이벤트 REJECTED | 지원자 | 지원 거절 | {프로젝트}: 지원이 거절되었습니다. | `/projects/{id}` | 이벤트 `eventKey` |
| `PROJECT_STATUS_CHANGED` | `ProjectLifecycleChanged` STATUS_CHANGED | `ACTIVE` 팀원, `PENDING` 지원자 | 프로젝트 상태 변경 | {프로젝트}: {변경된 상태} | `/projects/{id}` | 아래 |
| `PROJECT_PERIOD_EXTENDED` | 같은 이벤트 PERIOD_EXTENDED | `ACTIVE` 팀원, `PENDING` 지원자 | 프로젝트 기간 연장 | {프로젝트}: 변경된 모집 조건과 기간을 확인해 주세요. | `/projects/{id}` | 아래 |
| `PROJECT_COMPLETED` | 같은 이벤트 COMPLETED | `ACTIVE` 팀원 | 프로젝트 종료 | {프로젝트} 프로젝트가 종료되었습니다. | `/projects/{id}` | 아래 |
| `PROJECT_REVIEW_REQUESTED` | `ProjectReviewNotificationEvent` (`reminder = false`) | 이벤트의 수신자(리뷰 작성자) | 협업 리뷰 작성 요청 | {프로젝트}: {만료 yyyy-MM-dd HH:mm}까지 본인을 제외한 팀원을 평가해 주세요. | `/projects/{id}/reviews` | 이벤트 `eventKey` |
| `PROJECT_REVIEW_REMINDED` | 같은 이벤트 (`reminder = true`) | 이벤트의 수신자 | 협업 리뷰 작성 재알림 | {프로젝트}: 아직 평가하지 않은 팀원이 있습니다. | `/projects/{id}/reviews` | 이벤트 `eventKey` |

- 프로젝트 상태·기간·종료 알림의 행위자 본인(`actorUserId`)은 받지 않는다(D6 A). 종료 이벤트는 종료 동의 과반(`ProjectLifecycleServiceImpl`)과 승인 팀원의 참여 취소로 과반이 된 경우(`ProjectApplicationServiceImpl`) 두 곳에서 발행된다.
- `ProjectLifecycleChanged`의 `token`은 `project-lifecycle:{프로젝트 ID}:{종류}:{변경 후 상태}:{발생 시각}`이다.
- 상태 이름은 화면과 같은 한글(모집 중, 모집 완료, 프로젝트 진행 중, 프로젝트 종료)이다. 프로젝트 이름이 없으면 "프로젝트", 지원자 닉네임이 없으면 "지원자"로 쓴다.
- 리뷰 알림의 `review_id`는 그 프로젝트에서 수신자가 작성자인 `reviews` 행 중 가장 작은 `id`다. 이벤트의 `href`(임시 토큰 포함)는 저장하지도 로그에 남기지도 않는다.
- 문구는 생성 시점 기준이다. 나중에 프로젝트 이름이 바뀌어도 이미 만든 알림은 그대로다.

## 테이블

ERD `notifications`를 따른다. NULL 여부·제약·인덱스는 계획 2.2에서 정한 값이다.

| 필드 | 타입 | NULL | 의미 |
|---|---|---|---|
| id | BIGINT PK (identity) | 불가 | 알림 ID |
| recipient_id | BIGINT FK `users.id` ON DELETE CASCADE | 불가 | 수신 사용자 |
| project_id | BIGINT FK `projects.id` ON DELETE CASCADE | 가능 | 관련 프로젝트 |
| review_id | BIGINT FK `reviews.id` ON DELETE CASCADE | 가능 | 리뷰 요청(리뷰 알림만) |
| token | VARCHAR(200) | 불가 | 식별값(중복 방지). 리뷰 임시 링크 토큰과 관계없다 |
| type | VARCHAR(50) | 불가 | 알림 종류 |
| title | VARCHAR(255) | 불가 | 제목 |
| body | TEXT | 불가 | 내용 |
| action_url | VARCHAR(2048) | 불가 | 서버가 만든 내부 경로 |
| read_at | TIMESTAMP | 가능 | 읽은 시각 |
| created_at | TIMESTAMP | 불가 | 생성 시각 |

- `uk_notifications_recipient_token UNIQUE (recipient_id, token)`, `idx_notifications_recipient_created (recipient_id, created_at, id)`.
- FK 세 개는 `ON DELETE CASCADE`다(사용자 결정 2026-11-10). 운영 코드는 회원·프로젝트·리뷰를 물리 삭제하지 않는다(프로젝트는 논리 삭제).
- 테이블은 `ddl-auto: update`로 만든다. 별도 SQL 파일은 없다.

## BFF (프론트엔드)

브라우저는 BFF만 호출하며 사용자 ID를 보내지 않는다.

| BFF 경로 | 메서드 | 백엔드 |
|---|---|---|
| `/api/notifications` | GET | `/api/v1/notifications` (확인한 `status`·`page`·`size`만 전달) |
| `/api/notifications/[id]/read` | PATCH | `/api/v1/notifications/{id}/read` (본문은 `{ "read": boolean }`으로 다시 만들어 전달) |
| `/api/notifications/read-all` | PATCH | `/api/v1/notifications/read-all` |

- 쓰기 요청은 `Origin`이 브라우저가 보낸 `Host`와 같아야 한다(아니면 403). `[id]`는 양의 정수만 받는다.
- 응답은 허용한 필드만 전달한다. 내부 경로 조건(`/`로 시작, `//`·`/\`로 시작하지 않음, 공백·제어 문자·`\` 없음)을 통과하지 못한 `actionUrl`은 `null`로 바꾸고 화면은 이동하지 않는다.
- 백엔드 400·401·403·404는 상태별 안내 문구로 바꾸고, 그 밖의 실패·연결 오류·형식이 다른 응답은 502다.

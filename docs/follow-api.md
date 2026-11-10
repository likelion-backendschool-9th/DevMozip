# 팔로우 API (WBS 9.1~9.5)

[팔로우 계획](follow-plan.md) 3단계에서 구현한 API다. 결정 근거와 단계별 기록은 계획 문서에 있다.

## 공통

- `{slug}`는 `users.tag` 원문이다(프로필 조회 `GET /api/v1/users/{slug}`와 같다). 대소문자를 그대로 비교한다.
- 대상 사용자는 프로필 조회와 같은 조건으로 찾는다. `status = ACTIVE`이고 직군이 하나 이상 있어야 하며, 아니면 404다.
- 대상이 비공개 계정(WBS 17.7 계정 잠금)이면 본인이 아닌 요청자와 비로그인 요청에 팔로우 상태·목록 조회와 팔로우·언팔로우를 403 "비공개 계정입니다."로 응답한다. 알림 설정은 공개 여부를 보지 않는다. 목록 항목에서는 비공개 계정을 빼고 요청자 본인만 남긴다. 기준은 [privacy-settings-api.md](privacy-settings-api.md)에 있다.
- 요청자는 JWT의 이메일로만 찾는다. 요청 본문이나 쿼리의 사용자 ID는 쓰지 않는다.
- 조회 3개는 공개다. 토큰이 있으면 요청자 기준 값(`followed` 등)을 채우고, 만료되거나 잘못된 토큰은 비로그인으로 처리한다.
- 변경 3개는 로그인이 필요하다. 요청자는 `ACTIVE`·`WARNED` 회원이어야 한다.
- 오류는 공통 `ErrorResponse` 형식(`status`, `error`, `message`, `path`, `timestamp`)이다. `message`는 화면에 그대로 보여 줄 수 있는 문장이다.

| 코드 | 메서드·경로 | 인증 | 동작 |
|---|---|---|---|
| 추가 | `GET /api/v1/users/{slug}/follow` | 공개 | 팔로워·팔로잉 수, 요청자의 팔로우 여부·알림 설정 |
| 9.1 | `POST /api/v1/users/{slug}/follow` | 로그인 | 팔로우 |
| 9.2 | `DELETE /api/v1/users/{slug}/follow` | 로그인 | 언팔로우. 알림 설정도 함께 지운다 |
| 9.3 | `GET /api/v1/users/{slug}/followers` | 공개 | 팔로워 목록 |
| 9.4 | `GET /api/v1/users/{slug}/following` | 공개 | 팔로잉 목록 |
| 9.5 | `PUT /api/v1/users/{slug}/notify` | 로그인 | 내가 팔로우한 `{slug}`의 활동 알림 켜기·끄기 |

## 팔로우 상태

`GET /api/v1/users/{slug}/follow`

```json
{ "followerCount": 12, "followingCount": 8, "followed": false, "activityNotificationEnabled": false }
```

- 수는 대상 사용자 기준이며 회원 상태·태그·공개 여부와 관계없이 `follows`의 관계를 모두 센다. 그래서 목록의 `totalCount`(비공개 계정을 뺀 수)와 다를 수 있다.
- `followed`, `activityNotificationEnabled`는 요청자 기준이다. 비로그인이거나 본인 프로필이면 `false`다.

## 팔로우·언팔로우

`POST /api/v1/users/{slug}/follow`, `DELETE /api/v1/users/{slug}/follow` (본문 없음)

```json
{ "followed": true, "activityNotificationEnabled": false, "followerCount": 13 }
```

- 응답은 요청 처리 후의 최종 상태이고 `followerCount`는 대상의 팔로워 수다.
- 이미 팔로우한 상태의 POST와 팔로우하지 않은 상태의 DELETE는 아무것도 바꾸지 않고 현재 상태를 200으로 응답한다.
- 같은 POST가 동시에 와서 기본 키가 충돌해도 이미 팔로우된 상태로 보고 현재 상태를 200으로 응답한다.
- 팔로우 직후 알림 설정은 `false`다. 언팔로우하면 행이 지워져 알림 설정도 사라지고, 다시 팔로우하면 `false`로 시작한다.

## 팔로워·팔로잉 목록

`GET /api/v1/users/{slug}/followers`, `GET /api/v1/users/{slug}/following`

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `q` | 없음 | 닉네임·태그·직군·기술 스택 중 하나에 포함되면 찾는다. 대소문자 구분 없음. 앞뒤 공백은 지우고, 공백뿐이면 검색하지 않는다. 100자 이내 |
| `available` | `false` | `true`면 `collaboration_status = AVAILABLE`인 사용자만 |
| `page` | `0` | 0 이상 |
| `size` | `20` | 1~100 |

```json
{
  "items": [
    {
      "tag": "dev-tag",
      "nickname": "개발자",
      "profileImage": null,
      "jobCategories": ["백엔드 개발자"],
      "career": "2년 차",
      "skills": ["Java", "Spring Boot"],
      "collaborationStatus": "AVAILABLE",
      "followed": true,
      "activityNotificationEnabled": false
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false,
  "totalCount": 1
}
```

- 정렬은 최근에 팔로우한 순서(`follows.created_at` 내림차순, 같으면 사용자 id 내림차순)다.
- 목록에는 회원 상태·태그 조건이 없다. 태그가 없는 회원은 `tag`가 `null`이고, 직군·기술 스택이 없으면 빈 배열이다. 검색·필터가 없고 목록에 비공개 계정이 없을 때 `totalCount`는 팔로우 상태 API의 수와 같다.
- 비공개 계정은 목록 조건에서 빼므로 `totalCount`·`hasNext`·페이지 크기에도 들어가지 않는다. 요청자 본인 항목은 비공개여도 남는다.
- `totalCount`는 검색·필터를 적용한 전체 수다. `hasNext`는 다음 페이지에 항목이 있는지다. 범위를 넘은 페이지는 빈 `items`로 응답한다.
- `followed`, `activityNotificationEnabled`는 요청자가 그 사람을 팔로우하는지와 알림 설정이다. 비로그인이면 `false`이고, 목록에 요청자 본인이 있으면 본인 항목도 `false`다.
- 이메일, 사용자 id, 결제 정보는 넣지 않는다. 내 항목인지는 화면이 내 프로필의 태그와 비교한다.
- 목록에는 `ACTIVE`가 아니거나 직군이 없는 회원도 나오지만, 그 회원을 `{slug}`로 쓰는 요청(팔로우·언팔로우·목록 등)은 대상 조건에 따라 404다.
- 직군·기술 스택 검색에 PostgreSQL JSONB 함수를 쓴다. H2를 쓰는 `local` 프로필에서는 목록 조회가 동작하지 않는다.

## 알림 설정

`PUT /api/v1/users/{slug}/notify` (`Content-Type: application/json`)

```json
{ "activityNotificationEnabled": true }
```

- 응답도 같은 형태다. 이 값 하나로 상대의 새 게시글 알림과 새 프로젝트 모집 알림을 함께 켜고 끈다.
- 저장까지만 한다. 이 값으로 실제 알림을 만드는 처리는 알림 기능(WBS 12.0)이 생길 때 `follows`에서 `activity_notification_enabled = true`인 팔로워를 읽으면 된다.

## 오류

| 상태 | 경우 | `message` |
|---|---|---|
| 400 | 대상이 자기 자신(팔로우·언팔로우·알림 설정) | 자기 자신은 팔로우할 수 없습니다. |
| 400 | `page`·`size` 범위 밖 | 페이지와 조회 개수를 확인해 주세요. |
| 400 | `page`·`size`·`available` 형식 오류 | 조회 조건을 확인해 주세요. |
| 400 | `q`가 100자 초과 | 검색어는 100자 이내로 입력해 주세요. |
| 400 | 알림 설정 본문이 없거나 값이 `null`, JSON 형식 오류 | 알림 설정 값을 선택해 주세요. / 요청 본문의 형식이 올바르지 않습니다. |
| 401 | 변경 요청에 로그인 정보가 없음, 토큰의 회원이 없음 | 공통 인증 오류 문구 / 로그인이 필요합니다. |
| 403 | 요청자가 `ACTIVE`·`WARNED`가 아님 | 이 계정 상태에서는 이용할 수 없습니다. |
| 403 | 대상이 비공개 계정이고 요청자가 본인이 아님 (상태·목록·팔로우·언팔로우) | 비공개 계정입니다. |
| 404 | 대상이 없거나 프로필 조회 조건에 맞지 않음 | 사용자를 찾을 수 없습니다. |
| 409 | 팔로우하지 않은 사용자의 알림 설정 | 팔로우한 개발자의 알림만 설정할 수 있습니다. |
| 415 | 알림 설정 본문이 JSON이 아님 | 지원하지 않는 요청 형식입니다. |

정지 회원의 토큰은 JWT 필터에서 막혀 변경 요청은 403, 공개 조회는 비로그인으로 처리된다.

## 저장 구조

- 테이블 `follows`: `follower_id`·`following_id`(복합 기본 키, 둘 다 `users.id` 외래 키), `activity_notification_enabled`(NOT NULL, 기본값 `false`), `created_at`(NOT NULL, 한국 시각).
- 팔로워 목록 조회용 인덱스 `idx_follows_following_id(following_id)`.
- `ddl-auto: update`인 서버가 이 코드로 시작하면 연결된 DB에 테이블과 인덱스가 생긴다.

## 구현 위치

| 위치 | 파일 |
|---|---|
| entity | `Follow`, `FollowId` |
| repository | `FollowRepository`(관계 조회·삭제·알림 설정·수), `FollowQueryRepository`(목록 검색 SQL) |
| security/member | `CurrentMemberProvider`(요청자 식별. 피드·개발자 탐색과 함께 쓴다) |
| service | `FollowService`, `FollowQueryService`, `FollowTargetFinder`, `impl/FollowServiceImpl`, `impl/FollowQueryServiceImpl` |
| controller | `FollowController`, `FollowExceptionHandler` |
| dto/follow | `FollowStatusResponse`, `FollowResultResponse`, `FollowPageResponse`, `FollowUserResponse`, `FollowNotificationRequest`, `FollowNotificationResponse` |
| config | `SecurityConfig`에 공개 GET 3개 추가 |
| 테스트 | `follow/FollowRepositoryTest`, `follow/FollowApiIntegrationTest` (Testcontainers PostgreSQL) |

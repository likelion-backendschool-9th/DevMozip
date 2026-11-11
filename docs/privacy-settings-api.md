# 개인정보 설정 API (WBS 17.1~17.7)

[개인정보 설정 계획](privacy-settings-plan.md)으로 구현한 계약이다. 결정 근거와 단계별 기록은 계획 문서에 있다.

## 공통

- 모든 설정 API는 로그인이 필요하고, JWT의 이메일로 찾은 회원만 대상으로 한다. 요청 본문·쿼리의 사용자 ID는 쓰지 않는다.
- 브라우저는 백엔드를 직접 부르지 않고 BFF(서버 액션)를 거친다. 화면은 `/settings/privacy` 하나다.
- 오류는 공통 `ErrorResponse` 형식(`status`, `error`, `message`, `path`, `timestamp`)이다.

| 코드 | 메서드·경로 | 계약 문서 |
|---|---|---|
| 17.2 | `PATCH /api/v1/users/me/password` | [password-reset-api.md](password-reset-api.md) |
| 17.3 | `GET /api/v1/users/me/social-accounts`, `POST`·`DELETE /api/v1/users/me/social-accounts/{providerId}` | [kakao-auth-api.md](kakao-auth-api.md) |
| 17.4 | `GET /api/v1/payments/me` | [premium-payment-api.md](premium-payment-api.md) |
| 17.5 | `GET /api/v1/payment-methods/me` | 같음 |
| 17.6 | `GET /api/v1/subscription/me` | 같음 |
| 17.7 | `GET`·`PATCH /api/v1/users/me/account-lock` | 이 문서 |

## 계정 잠금 (17.7)

계정 잠금은 비공개 계정이다. 로그인 제한이 아니며 관리자 정지(`users.status = SUSPENDED`)와도 별개다. 본인은 로그인과 모든 기능을 그대로 쓴다.

### 저장

| 컬럼 | 타입 | NULL | 기본값 | 의미 |
|---|---|---|---|---|
| `users.is_private` | BOOLEAN | 불가 | FALSE | TRUE면 본인 외에는 공개 조회에서 숨긴다 |

- 가입 API는 바꾸지 않았다. 새 회원은 기본값으로 공개 계정이다.
- DB 기본값이 있어 `ddl-auto: update`로 컬럼이 추가될 때 기존 회원은 공개 계정으로 채워진다. 컬럼 없이 행을 넣는 SQL(테스트·초기 데이터)도 공개 계정이 된다.

### 조회

`GET /api/v1/users/me/account-lock` → 200

```json
{ "isPrivate": false }
```

### 변경

`PATCH /api/v1/users/me/account-lock` → 200

```json
{ "isPrivate": true }
```

- 본문은 원하는 최종 값이다. 같은 값을 다시 보내도 200이고 결과가 같다.
- 응답은 변경 후의 값이며 필드는 `isPrivate` 하나다.
- `isPrivate`는 JSON boolean만 받는다. 문자열 `"true"`, 숫자 `1`·`0`, 배열은 boolean으로 바꾸지 않고 400으로 응답한다.
- `is_private` 컬럼만 갱신한다(`@DynamicUpdate`). 같은 회원의 다른 컬럼 변경을 덮어쓰지 않는다.
- 바꾼 값은 다음 조회부터 반영한다. 서버에 공개 여부 캐시를 두지 않는다.

| 상태 | 상황 |
|---|---|
| 400 | 본문 없음·JSON 아님, `isPrivate` 누락·`null`, boolean이 아닌 값 |
| 401 | 로그인 필요, 잘못되거나 만료된 토큰 |
| 403 | 정지 회원 (JWT 필터) |

## 공개 조회의 판정 기준

다른 도메인은 `AccountVisibilityPolicy`(`service` 패키지)를 주입받아 같은 기준을 쓴다.

- 요청자가 대상 본인이거나 대상이 공개 계정이면 볼 수 있다. 관리자·프리미엄도 공개 조회에서는 예외가 아니다. 비로그인 요청자는 공개 계정만 본다.
- 요청자 ID는 각 도메인이 인증 정보의 이메일로 찾은 회원 ID다. 비로그인이면 `null`이다.
- 판정은 서버의 조회에서 한다. 화면에서 숨기는 것만으로는 막지 않는다.

| 메서드 | 용도 | 볼 수 없을 때 |
|---|---|---|
| `canView(viewerId, target)` | 단건 판정 | `false` |
| `requireVisibleAccount(viewerId, target)` | 사람 단위 조회(프로필과 하위 정보, 팔로우 상태·목록·팔로우 변경) | 403, `message`: "비공개 계정입니다." |
| `requireVisibleContent(viewerId, author, notFoundMessage)` | 글 단위 조회·쓰기(게시글 상세·댓글, 포트폴리오 상세, 좋아요·댓글·퍼가기·북마크 요청) | 404, `message`: 그 도메인의 없는 글 문구 |
| `visibleAccountCondition(usersAlias, viewerId)` | 목록 조회 조건 | 조건에서 뺀다 |

- 목록 조건은 요청자가 있으면 `(u.is_private = FALSE OR u.id = :viewerId)`, 비로그인이면 `u.is_private = FALSE`다. 요청자가 있을 때 호출한 쪽이 `viewerId` 파라미터를 넣는다. JPQL에서는 같은 뜻으로 `(m.isPrivate = false OR m.id = :viewerId)`를 쓴다.
- 목록은 응답에 넣은 뒤 거르지 않고 조회 조건에서 뺀다. 그래야 페이지 크기·`hasNext`·전체 수가 맞는다.
- 비공개 계정이 있다는 사실은 403으로 드러난다. Mock의 "비공개 계정입니다." 화면과 같다. 공개 조회 경로는 비로그인도 허용하므로 정지 회원 403과 겹치지 않는다(정지 회원의 토큰은 공개 조회에서 비로그인으로 처리된다). 로그인이 필요한 변경 요청은 정지 회원도 403이므로 `message`로 구분한다.

## 적용한 공개 조회

| 도메인 | 상태 | 문서 |
|---|---|---|
| 팔로우 (WBS 9.x) | 적용 (2026-11-10) | 아래, [follow-api.md](follow-api.md) |
| SNS 피드 (WBS 8.x) | 대기. `feat/91-posts`가 develop에 병합된 뒤 적용한다 | [privacy-settings-plan.md](privacy-settings-plan.md) 4단계 |
| 개발자 찾기 (WBS 6.x) | 대기. `feat/104-find-developers`가 develop에 병합된 뒤 적용한다 | 같음 |
| 프로필·포트폴리오·AI 추천·좋아요·북마크 | 각 담당자가 위 판정 기준으로 적용한다 | 계획 문서 8절 |

### 팔로우

| API | 대상이 비공개 계정일 때 (본인 외) | 본인 |
|---|---|---|
| `GET /api/v1/users/{slug}/follow` | 403 "비공개 계정입니다." | 그대로 |
| `GET /api/v1/users/{slug}/followers`, `/following` | 403 "비공개 계정입니다." | 그대로 |
| `POST`·`DELETE /api/v1/users/{slug}/follow` | 403 "비공개 계정입니다." 관계는 바뀌지 않는다 | 자기 자신 팔로우는 기존처럼 400 |
| `PUT /api/v1/users/{slug}/notify` | 공개 여부를 보지 않는다 | — |

- 목록 항목에서는 비공개 계정을 뺀다. 요청자 본인 항목은 남는다. 전체 수(`totalCount`)·`hasNext`·페이지 크기는 뺀 뒤의 값이다. 비공개 계정 본인의 목록에서도 다른 회원의 비공개 계정은 빠진다.
- 팔로우 상태 API의 `followerCount`·`followingCount`와 팔로우·언팔로우 응답의 `followerCount`는 기존 계약대로 모든 관계를 센다.
- 없는 사용자의 404, 비로그인 변경 요청의 401, 자기 자신 팔로우의 400은 공개 여부보다 먼저 판정한다.

## 프론트엔드

- 설정 화면의 계정 공개 영역은 `app/(user)/settings/privacy/_components/AccountLockPanel.tsx`다. Mock 토글 OFF이면 서버 액션 `accountLockStatusAction()`·`updateAccountLockAction(isPrivate)`(`lib/account/accountLockActions.ts`)로 위 API를 부른다. 토글 ON이면 기존 Mock 저장값(`devmozip:account-area:v1`의 `isPrivate`)만 읽고 바꾸며 서버를 부르지 않는다.
- 서버 액션은 `isPrivate`가 boolean인 응답만 받고 그 밖의 필드는 버린다. boolean이 아닌 입력은 백엔드를 부르지 않는다. 백엔드 오류 본문 대신 상태별 문구(400·401·403·그 밖)를 돌려준다.
- 팔로우 BFF(`lib/profiles/followProxy.ts`)는 백엔드 403의 `message`가 "비공개 계정입니다."일 때만 그 문구를 넘긴다. 그 밖의 403은 기존 정지 회원 문구다. 팔로우 목록 화면은 이 응답을 받으면 Mock과 같은 "비공개 계정입니다." 화면을 보여 주고, 프로필 상단의 팔로우 영역은 수를 "—"로 두고 같은 안내를 보여 준다.

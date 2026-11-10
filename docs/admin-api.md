# 관리자 API

WBS 14.1~14.13 관리자 API의 확정 계약이다. 계획과 진행 기록은 [admin-plan.md](admin-plan.md)를 본다.

## 공통

- 경로는 모두 `/api/v1/admin` 아래다. 브라우저는 BFF `/api/admin/...`만 부른다.
- 권한: 보안 설정이 JWT의 `ADMIN` 역할을 확인하고(비로그인 401, 다른 역할 403), 서비스가 DB의 회원이 지금도 `ADMIN`이며 `ACTIVE`인지 다시 확인한다(`AdminAccess`, 아니면 403 "관리자만 이용할 수 있습니다.").
- 요청자·처리자·처리 시각은 서버가 정한다. 요청 값으로 받지 않는다.
- 목록 쿼리: `page`(0부터, 기본 0), `size`(1~50, 기본 20). 범위를 벗어나면 400 "페이지를 확인해 주세요."
- 목록 응답: `{ "items": [...], "totalElements": 0, "page": 0, "totalPages": 0 }`. 요약이 있는 목록은 `summary`를 더한다.
- 검색어 `q`: 앞뒤 공백을 빼고 대소문자를 무시하는 부분 일치다. `%`·`_`·`\`는 글자 그대로 찾는다. 비어 있으면 조건이 없다.
- 필터 값(상태·역할·종류 등)은 enum 이름이며, 모르는 값은 400과 안내 문구다.
- 시각은 한국 시각 `LocalDateTime` 문자열(`2026-11-10T10:00:00`)이다.
- 탈퇴 회원(#135, `email`·`status`가 NULL)은 회원 목록·제재·회원 수에서 빠진다. 신고·구독·결제·AI 사용량에서는 기록이 남으며, 회원 표시(`AdminPartyResponse`)의 `withdrawn`이 `true`이고 `tag`가 `null`이다.
- 오류는 기존 `ErrorResponse`(`status`, `error`, `message`, `path`, `timestamp`) 형식이다.

| 상태 | 상황 |
|---|---|
| 400 | 잘못된 쿼리·본문, 허용하지 않는 조치 |
| 401 | 로그인 필요 |
| 403 | 관리자가 아님(JWT 또는 DB), 정지 회원(JWT 필터), 관리자 본인·관리자 계정 제재 |
| 404 | 대상 없음(삭제된 대상 포함) |
| 409 | 현재 상태와 맞지 않는 변경(같은 상태로 제재, 이미 끝난 신고, 모집 중이 아닌 공고 삭제, 키워드 동시 추가) |

회원 표시 `AdminPartyResponse`: `{ "id": 3, "tag": "kim", "nickname": "김개발", "withdrawn": false }`

## 14.1 대시보드

`GET /api/v1/admin/dashboard` → 200

```json
{
  "members": 5, "premiumMembers": 1, "suspendedMembers": 0, "projects": 2, "posts": 7, "pendingReports": 3,
  "aiRecommendationsThisMonth": 4,
  "recentActions": [{ "type": "SANCTION", "actorNickname": "운영자", "summary": "회원 경고 · 대상회원", "createdAt": "2026-11-10T10:00:00" }]
}
```

| 값 | 기준 |
|---|---|
| members | 탈퇴하지 않은 회원 |
| premiumMembers | 이용 기간 안의 구독(`ACTIVE`·`RENEWING`·`CANCEL_REQUESTED`)이 있는 회원 |
| suspendedMembers | `status = SUSPENDED` |
| projects | 삭제되지 않은 공고 |
| posts | 삭제되지 않은 `VISIBLE` 게시글 |
| pendingReports | `AWATING`·`REVIEWING` 신고(모든 종류) |
| aiRecommendationsThisMonth | 이번 달(한국 시각) `ai_usages.used_count` 합 |
| recentActions | 제재 시작·철회 기록과 신고 처리 기록을 합친 최근 12건(D7). `type`은 `SANCTION`·`REPORT`. 철회 기록은 처리자가 `null`이다(ERD에 칼럼이 없다). 숨김·삭제·키워드 변경은 기록하지 않는다 |

## 14.2 회원 조회

`GET /api/v1/admin/users?q=&status=&role=&page=&size=`

- `q`: 닉네임·이메일·tag. `status`: `ACTIVE`·`WARNED`·`SUSPENDED`. `role`: `USER`·`PREMIUM`·`OWNER`·`ADMIN`. 최신 회원 ID순.
- 항목: `{ id, tag, nickname, email, role, status, latestSanction }`. `tag`는 태그가 없으면 `null`. `latestSanction`은 가장 최근 제재 `{ type, reason, adminNickname, startsAt, endsAt, revokedAt }`이거나 `null`이며 `type`은 `WARNED`·`SUSPENDED`. `revokedAt`이 있으면 해제(철회)된 제재이고, `endsAt`은 늘 `null`(무기한)이다.
- 비밀번호 해시·카카오 ID·결제 수단은 넣지 않는다. 관리자 계정도 나온다.

## 14.3 회원 제재

`PATCH /api/v1/admin/users/{slug}/ban`, 본문 `{ "status": "SUSPENDED", "reason": "사유", "reportId": 12 }` → 200, 회원 항목

- `status`: `WARNED`(경고)·`SUSPENDED`(정지)·`ACTIVE`(해제). 없으면 400 "변경할 상태를 확인해 주세요."
- `reason`: 문자열만. 앞뒤 공백을 뺀 300자 이하. 경고·정지는 필수(400 "경고·정지 사유를 입력해 주세요.").
- `reportId`: 경고·정지에서 선택. 없는 신고면 404. 제재 기록(`sanctions.report_id`)에만 남고 신고 상태는 바꾸지 않는다.
- 해제(`ACTIVE`)는 `reason`·`reportId`를 받지 않는다(400 "해제에는 사유와 근거 신고를 함께 보내지 않습니다."). ERD에 해제 사유·해제자 칼럼이 없다.
- 기록 방식(ERD `sanctions`): 경고·정지는 새 행(`starts_at`)을 만들고, 그 전에 유효한(`revoked_at`이 빈) 제재를 철회한다. 해제는 새 행 없이 유효한 제재의 `revoked_at`만 채운다. `ends_at`은 늘 NULL이다.
- `slug`는 탈퇴하지 않은 회원의 태그다. 없는 태그·탈퇴 회원 404, 본인 403 "본인 계정은 제재할 수 없습니다.", 관리자 403 "관리자 계정은 제재할 수 없습니다.", 지금과 같은 상태 409 "이미 같은 상태인 회원입니다."
- 회원 행을 잠그고 `users.status`를 바꾼 뒤 `sanctions`에 한 행을 더한다. 정지·정지 해제는 커밋 뒤 정지 목록(`SuspendedMemberStore`)에 반영해 기존 토큰의 API와 메신저 연결을 막는다. 로그인·재발급은 DB 상태로 막는다.

### 경고 안내 (D8)

`GET /api/v1/users/me` 응답에 `status`(DB 계정 상태)와 `warningReason`(경고 회원의 최근 제재 사유, 그 밖에는 `null`)을 더했다. `username`·`role`은 그대로다. 세션 BFF(`/api/auth/session`)는 두 값을 확인해 넘긴다.

## 14.4 프로젝트 공고 관리

`GET /api/v1/admin/projects?q=&status=&restricted=&page=&size=`

- `q`: 제목. `status`: `RECRUITING`·`RECRUITMENT_COMPLETED`·`IN_PROGRESS`·`COMPLETED`. `restricted`: `true`·`false`. 삭제된 공고는 뺀다.
- 항목: `{ id, title, hostTag, hostNickname, status, isPublic, restricted, recruitmentEnd, developmentEnd, createdAt }`. `isPublic`은 주최자의 공개 여부, `restricted`는 관리자 숨김이다.

`PUT /api/v1/admin/projects/{id}`, 본문 `{ "action": "DELETE" }` → 204

- 관리자 삭제(논리 삭제, D3). 주최자 삭제(7.5)와 같이 모집 중인 공고만 되며, 그 밖은 409(숨김 안내). 지원·팀원 이력은 남는다. 되돌리는 API는 없다. `DELETE`가 아닌 조치는 400.

## 14.5 프로젝트 공고 표시 여부

`PATCH /api/v1/admin/projects/{id}/visibility`, 본문 `{ "restricted": true }` → 200, 공고 항목

- `restricted`는 JSON boolean만. 같은 값을 다시 보내도 200. 주최자가 공고를 고쳐도 관리자 숨김은 풀리지 않는다(`projects.is_restricted`, D2).
- 숨긴 공고는 비공개 공고처럼 현재 팀원만 본다: 공개 목록·프로젝트 검색·좋아요·북마크 목록·프로필 프로젝트 활동에서 빠지고, 팀원이 아닌 사람의 상세·북마크·좋아요·신고는 404, 지원은 "관리자가 숨긴 공고에는 지원할 수 없습니다."이다.

## 14.6 게시글·댓글 관리

`GET /api/v1/admin/posts?target=POST|COMMENT&q=&visibility=&page=&size=`

- `target` 기본 `POST`(D4). `q`는 제목·내용(댓글은 내용). `visibility`(`VISIBLE`·`HIDDEN`·`RESTRICTED`)는 게시글에만, 댓글에 주면 400. 삭제된 글·댓글은 뺀다.
- 게시글 항목: `{ id, type, title, content, authorTag, authorNickname, visibility, createdAt }`. 퍼간 글(`REPOST`)은 `title`이 `null`이고 `content`가 의견이다.
- 댓글 항목: `{ id, postId, postTitle, content, authorTag, authorNickname, createdAt }`.

`PUT /api/v1/admin/posts/{id}?target=POST|COMMENT`, 본문 `{ "action": "DELETE" }` → 204

- 관리자 삭제(D3). 게시글은 작성자 삭제(8.6)처럼 논리 삭제하고 커밋 뒤 이미지 파일을 지운다. 퍼간 글은 원본 안내를 보여 준다. 댓글은 논리 삭제다. 이미 삭제된 대상 404.

## 14.7 게시글 표시 여부

`PATCH /api/v1/admin/posts/{id}/visibility`, 본문 `{ "restricted": true }` → 200, 게시글 항목

- 게시글만 받는다(`target=COMMENT`면 400). `true`는 `RESTRICTED`(작성자 숨김이어도), `false`는 `RESTRICTED`일 때만 `VISIBLE`로 돌린다. 작성자 숨김(`HIDDEN`)은 관리자가 풀지 않는다. 수정 시각(수정됨 표시)은 바꾸지 않는다.

## 14.8 신고 내역 처리

기존 `/api/v1/admin/project-reports`(공고·팀원 신고만)를 대신한다(D5). 그 경로는 없어졌다.

`GET /api/v1/admin/reports?type=&status=&page=&size=`

| type | 판정 |
|---|---|
| PROJECT | `project_id`만 |
| MEMBER | `project_id`와 `reported_user_id` |
| USER | `reported_user_id`만 |
| POST | `post_id`만 |
| COMMENT | `comment_id` |

- `status`: `AWATING`·`REVIEWING`·`RESOLVED`·`REJECTED`. 최신 신고 ID순.
- 항목: `{ id, type, reporter, target, reason, status, resolution, handledByNickname, handledAt, createdAt }`. `reporter`는 회원 표시.
- `target`: `{ projectId, projectTitle, user, postId, commentId, content, deleted }`. `user`는 신고 대상 회원(팀원·사용자 신고)이거나 대상 글·댓글의 작성자이며 공고 신고는 `null`. `content`는 게시글 제목(퍼간 글은 의견)이나 댓글 내용. `deleted`는 대상이 삭제됐는지다.

`PATCH /api/v1/admin/reports/{id}`, 본문 `{ "status": "RESOLVED", "resolution": "처리 결과" }` → 200, 신고 항목

- `status`는 `REVIEWING`·`RESOLVED`·`REJECTED`(`AWATING`은 400). 완료·기각은 `resolution` 필수, 1,000자 이하, 문자열만. 최종 처리 후에는 409 "이미 처리가 끝난 신고입니다." 신고 행을 잠가 동시 최종 처리는 한 번만 적용한다.
- 처리해도 제재·숨김·삭제는 일어나지 않는다.

## 14.9 리뷰 키워드 관리

`GET /api/v1/admin/review-keywords` → 200 `{ "items": [{ "id": 1, "name": "소통이 원활해요", "active": true }] }` (비활성 포함, ID 오름차순)

`PUT /api/v1/admin/review-keywords`, 본문 `{ "keywords": ["소통이 원활해요", "코드 리뷰가 꼼꼼해요"] }` → 200, GET과 같은 형식

- `keywords`는 활성으로 둘 이름 목록이다. 문자열만, 앞뒤 공백을 뺀 1~30자, 목록 안 중복 불가(400). 빈 목록도 받는다.
- 목록의 이름은 없으면 만들고 비활성이면 다시 활성으로, 목록에 없는 활성 키워드는 비활성으로 바꾼다. 행은 지우지 않고 이름도 바꾸지 않는다. 같은 새 이름이 동시에 추가되면 409.
- 리뷰 제출은 활성 키워드만 받으므로 바로 반영된다. 기존 리뷰의 키워드 표시는 그대로다.

## 14.10 AI 사용량

`GET /api/v1/admin/ai/usage?month=YYYY-MM&page=&size=`

- `month` 기본은 이번 달(한국 시각). 형식이 틀리면 400 "월을 YYYY-MM 형식으로 입력해 주세요."
- 응답: `summary` `{ month, totalUsed, totalPending, members }` + 항목 `{ userId, tag, nickname, role, withdrawn, usedCount, pendingCount }`. 사용 횟수가 많은 순.

## 14.11 AI 오류

`GET /api/v1/admin/ai/logs?page=&size=`

- `ai_recommendation_requests`의 `FAILED` 기록(D6). 항목: `{ requestId, conversationId, userId, userTag, nickname, withdrawn, status, createdAt, completedAt }`. 최신 요청순.
- 오류 원인 칼럼이 없어 원인은 없다. 추천 조건·대화 내용은 넣지 않는다.

## 14.12 구독 관리

`GET /api/v1/admin/subscriptions?status=&page=&size=`

- `status`: `ACTIVE`·`RENEWING`·`CANCEL_REQUESTED`·`EXPIRED`·`CANCELED`.
- 응답: `summary` `{ statusCounts: { ACTIVE: 1, ... }, current }`(`current`는 이용 기간 안의 구독 수) + 항목 `{ id, member, planName, status, startsAt, currentPeriodEnd, cancelRequestedAt, endedAt }`.
- 탈퇴로 끝난 구독도 `CANCELED`로 나오며 `member.withdrawn`으로 구분한다.

## 14.13 결제 관리

`GET /api/v1/admin/payments?status=&page=&size=`

- `status`: `PENDING`·`PAID`·`FAILED`·`CANCELED`.
- 응답: `summary` `{ paidTotals: [{ currency, amount }] }`(전체 `PAID` 금액의 통화별 합) + 항목 `{ id, orderId, member, amount, currency, status, provider, method, paidAt, createdAt }`. `method`는 결제 당시 표시용 결제 수단이다.
- 빌링키와 결제 수단 원문은 넣지 않는다. 환불·취소 API는 없고 포트원을 부르지 않는다.

## BFF

| 브라우저 경로 | 백엔드 |
|---|---|
| `GET /api/admin/dashboard` | 14.1 |
| `GET /api/admin/users`, `PATCH /api/admin/users/[slug]/ban` | 14.2, 14.3 |
| `GET /api/admin/projects`, `PUT /api/admin/projects/[id]`, `PATCH /api/admin/projects/[id]/visibility` | 14.4, 14.5 |
| `GET /api/admin/posts`, `PUT /api/admin/posts/[id]`, `PATCH /api/admin/posts/[id]/visibility` | 14.6, 14.7 |
| `GET /api/admin/reports`, `PATCH /api/admin/reports/[id]` | 14.8 |
| `GET`·`PUT /api/admin/review-keywords` | 14.9 |
| `GET /api/admin/ai/usage`, `GET /api/admin/ai/logs` | 14.10, 14.11 |
| `GET /api/admin/subscriptions`, `GET /api/admin/payments` | 14.12, 14.13 |

- 쓰기 요청(`PATCH`·`PUT`)은 같은 사이트에서 온 요청만 받는다(403 "같은 사이트에서 요청해 주세요.").
- 모든 요청은 서버 세션의 역할이 `ADMIN`이어야 한다(없으면 401, 다르면 403). 화면 경로 `/admin`은 기존 `proxy.ts`가 관리자가 아닌 토큰을 홈으로 돌려보낸다.
- 쿼리는 허용한 키만, 형식을 확인한 뒤 넘긴다. 같은 키가 두 번 오면 400이다. 본문도 허용한 키·형식만 넘긴다(제재 사유·처리 결과·키워드 이름의 길이 포함).
- 응답은 형식을 확인한 필드만 넘기고 `Cache-Control: no-store`를 붙인다. 형식이 틀리면 502다.
- 백엔드의 400·404·409 안내 문구는 그대로 전달하고, 401·403은 고정 문구로 바꾼다. 5xx와 연결 오류는 내부 내용을 숨기고 502로 응답한다.

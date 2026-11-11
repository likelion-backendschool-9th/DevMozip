# SNS 피드 API (WBS 8.1~8.16)

[SNS 피드 계획](feed-plan.md) 3·4단계에서 구현한 API다. 결정 근거와 단계별 기록은 계획 문서에 있다.

## 공통

- 요청자는 JWT의 이메일로만 찾는다(`CurrentMemberProvider`). 요청 본문이나 쿼리의 사용자 ID(`authorId`, `userId` 등)는 쓰지 않는다.
- 목록·상세·댓글 목록은 공개다. 토큰이 있으면 요청자 기준 값(`mine`, `liked` 등)을 채우고, 만료되거나 잘못된 토큰은 비로그인으로 처리한다.
- 팔로잉 목록과 모든 쓰기 요청은 로그인이 필요하다. 요청자는 `ACTIVE`·`WARNED` 회원이어야 한다.
- 시각은 한국 시각(`+09:00`)의 ISO-8601 문자열이다. DB에는 `timestamp without time zone`으로 한국 시각을 저장한다.
- 오류는 공통 `ErrorResponse` 형식(`status`, `error`, `message`, `path`, `timestamp`)이다. `message`는 화면에 그대로 보여 줄 수 있는 문장이다.

### 볼 수 있는 게시글

다음을 모두 만족하는 글만 상세·댓글·반응·신고 대상이 된다. 아니면 404다(다른 사람의 숨긴 글은 있는지도 알리지 않는다).

- 삭제되지 않았다(`deleted_at IS NULL`).
- 작성자가 `ACTIVE`·`WARNED` 회원이다. 정지(`SUSPENDED`) 회원의 글은 보이지 않는다.
- `visibility = VISIBLE`이거나 요청자가 작성자다. `HIDDEN`(작성자 숨김)·`RESTRICTED`(관리자 숨김) 글은 작성자만 본다.

| 코드 | 메서드·경로 | 인증 | 동작 |
|---|---|---|---|
| 8.1 | `GET /api/v1/feeds` | 공개 | 전체 피드. 기본 정렬 최신순 |
| 8.2 | `GET /api/v1/feeds/recommands` | 공개 | 추천 피드(seed로 섞은 랜덤 추천). 기본 정렬 추천순 |
| 8.2 | `GET /api/v1/feeds/following` | 로그인 | 팔로우한 사용자의 글. 기본 정렬 최신순 |
| 8.3 | 위 세 목록의 쿼리 | — | `category`·`q` 필터와 `sort` 정렬 |
| 8.4 | `POST /api/v1/feeds` | 로그인 | 게시글 작성 (`multipart/form-data`) |
| 8.5 | `GET /api/v1/feeds/{id}` | 공개 | 상세. `view=true`면 조회수 집계 |
| 8.6 | `PUT /api/v1/feeds/{id}` | 작성자 | 수정 (`multipart/form-data`). 퍼간 글이면 의견만 |
| 8.6 | `POST /api/v1/feeds/{id}/delete` | 작성자 | 논리 삭제 |
| 8.7 | `PATCH /api/v1/feeds/{id}/likes` | 로그인 | 좋아요·취소 토글 |
| 8.8 | `POST` / `DELETE /api/v1/feeds/{id}/bookmark` | 로그인 | 북마크 추가·해제 |
| 8.9 | `GET /api/v1/feeds/{id}/comments` | 공개 | 댓글 목록 |
| 8.10 | `POST /api/v1/feeds/{id}/comments` | 로그인 | 댓글 작성 |
| 8.11 | `PUT /api/v1/comments/{id}` | 작성자 | 댓글 수정 |
| 8.12 | `DELETE /api/v1/comments/{id}` | 작성자 | 댓글 삭제(논리 삭제) |
| 8.13 | `POST /api/v1/feeds/{id}/reposts` | 로그인 | 퍼가기 |
| 8.13 | `DELETE /api/v1/feeds/{id}/reposts` | 로그인 | 내가 퍼간 글 삭제 |
| 8.15 | `POST /api/v1/feeds/{id}/reports` | 로그인 | 게시글 신고 |
| 8.16 | `POST /api/v1/comments/{id}/reports` | 로그인 | 댓글 신고 |
| — | `GET /images/{filename}` | 공개 | nginx 없이 실행할 때 업로드 이미지 제공 (아래 "이미지") |

`{id}`는 1~18자리 숫자다. 숫자가 아니면 경로가 맞지 않아 공개 경로에서도 401(비로그인) 또는 404다.

## 게시글 응답

목록 항목과 상세가 같은 모양이다.

```json
{
  "id": 12, "type": "POST", "category": "INFO",
  "title": "React 컴포넌트, 어디까지 분리하시나요?", "content": "본문",
  "techStacks": ["React"], "interests": ["교육"], "imageUrls": ["/images/feed_12_20261110101500_1.png"],
  "viewCount": 3, "likeCount": 2, "commentCount": 1, "visibility": "VISIBLE",
  "author": { "id": 1, "nickname": "김하늘", "tag": "member", "profileImage": null, "jobCategories": ["프론트엔드 개발자"] },
  "original": null,
  "createdAt": "2026-11-10T10:15:00+09:00", "updatedAt": "2026-11-10T10:15:00+09:00", "edited": false,
  "mine": false, "liked": false, "bookmarked": false, "reported": false, "myRepost": null
}
```

| 필드 | 설명 |
|---|---|
| `type` | `POST`(원본 글)·`REPOST`(퍼간 글) |
| `category` | `INFO`(정보)·`FEEDBACK`(피드백)·`PROMO`(홍보)·`COMMUNITY`(자유). 퍼간 글은 원본의 카테고리 |
| `title` | 퍼간 글은 `null` |
| `content` | 퍼간 글이면 의견이며 `null`일 수 있다 |
| `likeCount`·`commentCount` | `likes`의 `post_id` 행 수, 삭제되지 않은 댓글 수 |
| `author.tag` | 프로필 주소(`/users/{tag}`)에 쓴다. 없으면 `null` |
| `original` | 퍼간 글만 채운다. 원본이 삭제·숨김이거나 작성자가 정지면 `{ "id": 3, "available": false, "category": null, "title": null, "author": null }` |
| `edited` | 작성 뒤 수정했는지. 좋아요·조회수는 수정 시각을 바꾸지 않는다 |
| `mine`·`liked`·`bookmarked`·`reported` | 요청자 기준. 비로그인이면 `false` |
| `myRepost` | 원본 글에 요청자가 퍼간 글이 있으면 `{ "id": 30, "content": "의견" }`. 퍼간 글·비로그인은 `null` |

## 목록 (8.1~8.3)

`GET /api/v1/feeds`, `/api/v1/feeds/recommands`, `/api/v1/feeds/following`

| 쿼리 | 값 | 기본 |
|---|---|---|
| `category` | `INFO`·`FEEDBACK`·`PROMO`·`COMMUNITY` | 없음(전체) |
| `q` | 검색어 100자 이내. 공백으로 나눈 모든 단어가 제목·내용·기술 스택·관심 분야 중 한 곳에 있어야 한다(대소문자 무시) | 없음 |
| `sort` | `recommended`·`popular`·`views`·`latest` | 8.1·팔로잉 `latest`, 추천 `recommended` |
| `page` | 0부터. 1,000,000 이하 | 0 |
| `size` | 1~100 | 20 |
| `seed` | 0 이상 정수. 추천순의 섞는 값이며 다음 페이지에도 같은 값을 보낸다 | 0 |

```json
{ "items": [ { "id": 12, "...": "게시글 응답" } ], "page": 0, "size": 20, "hasNext": true, "totalCount": 41 }
```

- 목록에는 볼 수 있는 조건(공통)에 더해 `visibility = VISIBLE`인 원본 글(`type = POST`)만 나온다. 퍼간 글은 목업처럼 피드에 나오지 않고 상세(8.5)로만 본다.
- 정렬: 추천순은 seed에서 정한 두 수로 `x = (id × a + b) mod 2147483647`를 만들고 `x² mod 2147483647` 순서로 섞는다(같으면 id 순). 같은 seed는 늘 같은 순서이고, 1만 다른 seed도 순서가 달라진다. 인기순은 좋아요 수, 조회순은 조회수가 많은 순이고 같으면 최신순이다. 최신순은 작성 시각·id 내림차순이다.
- 페이지 번호 방식이라 그사이 새 글이 생기면 다음 페이지에 이미 받은 글이 다시 올 수 있다. 화면은 이미 있는 id를 다시 넣지 않는다.
- `totalCount`는 조건에 맞는 글 수다. 화면의 "N개의 이야기"에 쓴다.
- 팔로잉 목록은 `follows.follower_id = 요청자`인 사용자의 글이다.
- 잘못된 값(모르는 정렬·카테고리, 범위를 벗어난 `page`·`size`·`seed`, 숫자가 아닌 값)은 400이다.
- local 프로필(H2)에서도 같은 SQL이 동작한다. JSON 배열은 문자열로 바꿔 비교한다.

## 작성·수정 (8.4, 8.6)

`POST /api/v1/feeds`, `PUT /api/v1/feeds/{id}` — `multipart/form-data`

| 파트 | 형식 | 내용 |
|---|---|---|
| `post` | `application/json` | `category`, `title`, `content`, `techStacks`, `interests`. 수정은 `keepImageUrls`(남길 기존 이미지 경로)도 받는다 |
| `images` | 파일 0~3개 | PNG·JPEG·WebP·GIF, 각 512KB 이하 |

```json
{ "category": "INFO", "title": "제목", "content": "본문", "techStacks": ["React"], "interests": [], "keepImageUrls": ["/images/feed_12_20261110101500_1.png"] }
```

- 검증(400, 안내 문구는 작성 화면과 같다): 카테고리 필수, 제목 앞뒤 공백을 지운 1~150자, 본문 1~10,000자(공백만은 안 된다. 본문은 입력 그대로 저장), 기술 스택·관심 분야는 각각 앞뒤 공백을 지운 1~30자, 대소문자를 무시한 중복 없음, 최대 30개. 이미지는 남길 이미지와 새 이미지를 합쳐 3장 이하.
- 이미지 형식은 파일 내용(파일 앞부분과 이미지 읽기)으로 정한다. 업로드한 파일 이름과 `Content-Type`은 쓰지 않는다.
- 응답: 작성 201, 수정 200. 본문은 게시글 응답이다.
- 수정은 작성자만 한다(403). 다른 사람의 숨긴 글이면 404다. `visibility`는 바꾸지 않는다(작성자 숨김은 WBS 3.8 `PATCH /api/v1/feeds/{id}/visibility`, 관리자 숨김은 WBS 14.7 `PATCH /api/v1/admin/posts/{id}/visibility`, [admin-api.md](admin-api.md)). 관리자는 게시글·댓글을 `PUT /api/v1/admin/posts/{id}`로 논리 삭제할 수 있으며 결과는 작성자 삭제와 같다.
- 퍼간 글 수정은 `post` 파트의 `content`(의견, 1,000자 이내)만 쓰고, 이미지를 보내면 400이다.
- 남길 이미지(`keepImageUrls`)는 지금 이 글의 이미지여야 하고 중복이 없어야 한다(400). 남긴 이미지가 앞, 새 이미지가 뒤에 온다. 빠진 이미지 파일은 저장이 끝난 뒤 지운다.

## 상세·조회수 (8.5)

`GET /api/v1/feeds/{id}?view=true`

- `view=true`이면 조회수를 1 올리고 올린 값을 응답한다. 로그인한 작성자 본인의 요청과 볼 수 없는 글은 올리지 않는다. 방문 기록은 저장하지 않으므로 요청마다 오른다.
- 동시 요청은 `view_count = view_count + 1` 갱신으로 처리한다.
- 처음 읽는지는 화면이 정한다. 화면은 localStorage `devmozip:feed-views:v1`에 읽은 글 id를 저장하고, 없을 때만 `view=true`를 붙인다. 수정 화면은 `view` 없이 부른다.
- `view` 값은 Spring의 boolean 변환을 따른다(`true`·`on`·`yes`·`1`은 참). 그 밖의 값은 400이다. BFF는 `view=true`만 넘긴다.

## 삭제 (8.6)

`POST /api/v1/feeds/{id}/delete` → 204

- 작성자만 한다(403). `deleted_at`을 채우는 논리 삭제이며, 이미지 파일은 커밋 뒤에 지운다.
- 원본이 삭제돼도 퍼간 글은 남고, 퍼간 글의 `original.available`이 `false`가 된다. 댓글·좋아요·북마크 행은 남지만 삭제된 글은 어디에도 나오지 않는다.

## 좋아요·북마크 (8.7, 8.8)

`PATCH /api/v1/feeds/{id}/likes`

```json
{ "postId": 12, "liked": true, "likeCount": 3 }
```

`POST /api/v1/feeds/{id}/bookmark`, `DELETE /api/v1/feeds/{id}/bookmark`

```json
{ "postId": 12, "bookmarked": true }
```

- 공통 `likes`·`bookmarks`에 `post_id`를 채우고 `project_id`는 비운다. `(user_id, post_id)` 중복은 DB 유니크 제약과 게시글 행 잠금으로 막는다.
- 좋아요는 토글이라 같은 요청이 두 번 처리되면 원래 상태로 돌아간다. 응답의 최종 상태로 화면을 맞춘다.
- 북마크 추가는 이미 있으면 그대로 둔다. 해제는 게시글이 삭제·숨김이어도 할 수 있다.

## 댓글 (8.9~8.12)

`GET /api/v1/feeds/{id}/comments` → 작성 순서(오래된 것부터) 배열

```json
[{ "id": 5, "postId": 12, "content": "댓글", "author": { "id": 2, "nickname": "이서준", "tag": "premium", "profileImage": null, "jobCategories": [] },
   "createdAt": "2026-11-10T10:15:00+09:00", "updatedAt": "2026-11-10T10:15:00+09:00", "edited": false, "mine": false, "reported": false }]
```

- 작성 `POST /api/v1/feeds/{id}/comments` `{ "content": "..." }` → 201. 수정 `PUT /api/v1/comments/{id}` → 200. 삭제 `DELETE /api/v1/comments/{id}` → 204.
- 내용은 앞뒤 공백을 지운 1~1,000자다(400). 수정·삭제는 작성자만 한다(403).
- 게시글을 볼 수 없으면(삭제·다른 사람의 숨긴 글) 목록·작성·수정·삭제·신고 모두 404다.
- 목록은 페이지를 나누지 않는다(목업과 같다).

## 퍼가기 (8.13)

`POST /api/v1/feeds/{id}/reposts` `{ "content": "의견" }`(본문 없이 보내도 된다) → 201, 퍼간 글의 게시글 응답

- `{id}`는 원본이다. 원본이 퍼간 글이거나, 볼 수 없거나(삭제·숨김·작성자 정지) 하면 404 "원본 글을 찾을 수 없습니다."다.
- 같은 사람은 같은 원본을 한 번만 퍼간다(409 "이미 내 프로필에 퍼간 글입니다."). 원본 행을 잠근 뒤 확인하므로 동시 요청도 한 건만 저장된다. 삭제한 퍼간 글은 세지 않으므로 취소한 뒤 다시 퍼갈 수 있다.
- 본인 글도 퍼갈 수 있다(목업과 같다).
- 퍼간 글은 `type = REPOST`, `original_post_id = 원본`, 카테고리는 원본을 따르고 제목·태그·이미지는 비운다. 의견은 1,000자 이내이며 비어 있으면 `null`이다.
- 의견 수정은 `PUT /api/v1/feeds/{퍼간 글 id}`(작성·수정 참고).

`DELETE /api/v1/feeds/{id}/reposts` → 204

- 원본 `{id}`에 대해 요청자가 퍼간 글을 논리 삭제한다. 원본이 삭제·숨김이어도 할 수 있고, 퍼간 글이 없어도 204다.

## 신고 (8.15, 8.16)

`POST /api/v1/feeds/{id}/reports`, `POST /api/v1/comments/{id}/reports` `{ "reason": "허위 정보" }` → 201

```json
{ "id": 4, "targetType": "POST", "targetId": 12, "status": "AWATING", "createdAt": "2026-11-10T10:15:00+09:00" }
```

- 사유는 `스팸 또는 광고`, `욕설 또는 혐오 표현`, `허위 정보`, `저작권 침해`, `기타` 중 하나다(400 "신고 사유를 선택해 주세요."). 추가 설명은 받지 않는다.
- 본인이 쓴 게시글·댓글은 신고할 수 없다(400 "본인이 작성한 내용은 신고할 수 없습니다.").
- 같은 계정은 같은 대상을 한 번만 신고한다(409 "이미 신고한 내용입니다."). 대상 행을 잠근 뒤 확인한다.
- 저장: `reports`에 `reporter_id`와 대상 `post_id` 또는 `comment_id`, `reason`(사유 문구)을 채우고 `reported_user_id`는 비운다. `status`는 `AWATING`이다.

## 이미지

- 저장 폴더는 환경 변수 `IMAGE_STORAGE`다. 없으면 실행 폴더 기준 `../public/images`(backend 폴더에서 실행하면 프로젝트 루트의 `public/images`)다.
- 파일 이름: `feed_{게시글 id}_{업로드 시각 yyyyMMddHHmmss, 한국 시각}_{image_urls 안의 순서}.{png|jpg|webp|gif}`. DB에는 `/images/{파일 이름}`을 저장한다.
- 같은 이름의 파일이 이미 있으면 덮어쓰지 않고 503이다(같은 글을 같은 초에 다시 수정해 같은 순서의 새 이미지를 올린 경우).
- 작성이 실패하면 이미 쓴 파일을 지운다. 파일 저장이 실패하면 503 "이미지를 저장하지 못했습니다."이며 게시글도 저장하지 않는다.
- 제공: nginx가 `/images/`로 폴더를 직접 제공한다(`nginx/server.conf`, `nginx/local.conf`). nginx 없이 실행하면 Next.js의 Route Handler `app/images/[filename]/route.ts`가 실행 시의 `BACKEND_URL`로 백엔드 `GET /images/{filename}`을 불러 같은 폴더의 파일을 제공한다(`next.config.ts`의 rewrites는 주소가 빌드할 때 고정되어 쓰지 않는다). 파일 이름은 `기능접두어_이름.png|jpg|webp|gif` 형식만 받는다.
- 권한 확인 없이 제공하므로 숨긴 글의 이미지도 주소를 알면 볼 수 있다.

## 오류

| 상태 | 경우 |
|---|---|
| 400 | 입력 검증 실패, 잘못된 쿼리, `post` 파트 없음, 이미지 형식·크기·개수, 본인 대상 신고 |
| 401 | 로그인 필요 |
| 403 | 작성자가 아님(수정·삭제), `ACTIVE`·`WARNED`가 아닌 회원 |
| 404 | 없거나 삭제됐거나 볼 수 없는 게시글·댓글, 퍼갈 수 없는 원본 |
| 409 | 중복 퍼가기, 중복 신고 |
| 415 | 작성·수정이 `multipart/form-data`가 아님, `post` 파트가 JSON이 아님 |
| 503 | 이미지 파일 저장 실패 |

## 저장 구조

- 테이블: `posts`, `comments`, 공통 `likes`·`bookmarks`·`reports`. 컬럼은 [계획 2절](feed-plan.md#2-erd-변경)과 같다.
- 이미 만든 DB(공유 개발 DB·운영 DB)는 새 엔티티로 재시작하기 전에 [docs/sql/feed-schema-update.sql](sql/feed-schema-update.sql)을 실행한다. `reports` 상태 CHECK 제약을 `AWATING`으로 바꾸고 `posts` 인덱스 이름을 엔티티와 맞춘다.
- 데모 데이터: [docs/sql/post-demo-data.sql](sql/post-demo-data.sql)(PostgreSQL), local 프로필용 `backend/src/main/resources/posts_data.sql`(Git 제외).

## 구현 위치

| 위치 | 파일 |
|---|---|
| entity | `Post`, `Comment`, `enums/PostType`·`PostCategory`·`PostVisibility`·`PostReportReason`. 공통 `Like`·`Bookmark`·`Report`에 게시글용 생성 메서드 |
| repository | `PostRepository`, `PostQueryRepository`(목록 SQL), `CommentRepository`. `LikeRepository`·`BookmarkRepository`·`ReportRepository`에 게시글용 조회 |
| service | `FeedService`·`FeedQueryService`·`FeedReactionService`·`CommentService`·`FeedReportService`와 `impl`, `FeedRules`(입력·열람 규칙), `FeedPostAssembler`(응답) |
| controller | `FeedController`, `CommentController`, `FeedExceptionHandler`, `PublicImageController` |
| storage | `FeedImageStorage` |
| config | `FeedConfiguration`(`feedClock`), `SecurityConfig`의 공개 GET |
| frontend | BFF `app/api/feeds/**`·`app/api/comments/**`, 이미지 `app/images/[filename]/route.ts`, `lib/social/feedApi.ts`·`feedProxy.ts`, `types/feedApi.ts`, 화면 `app/feeds/**` |

## 메인 미리보기 (2026-11-11)

메인도 위 목록 API와 동일한 `FeedPost` 응답을 사용한다. 새 `/preview` 엔드포인트는 추가하지 않았다. 추천은 선택 카테고리와 동일 seed로 `GET /api/v1/feeds/recommands`를 `sort=recommended/popular/views/latest`, `page=0`, `size=12`로 각각 요청하고, 정렬별 상위 글을 중복 없이 번갈아 최대 12개 합친다. 팔로잉은 로그인한 경우에만 `GET /api/v1/feeds/following?sort=latest&page=0&size=12`와 선택 카테고리를 사용한다. 서버의 열람·인증·팔로우 조건은 기존과 같다. 빈 팔로잉의 팔로우 0명 여부만 기존 본인 프로필·팔로우 상태 API로 확인한다. 동작 정책은 [피드 계획의 메인 연결 기록](feed-plan.md)을 따른다.

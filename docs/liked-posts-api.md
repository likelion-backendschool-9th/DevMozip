# 내 좋아요한 글 목록 — #75

## 범위와 흐름

`/profile/me/likes` → Next BFF `GET /api/profiles/me/liked-posts` → Spring `GET /api/v1/users/me/liked-posts` → `LikedPostsService` → `LikedPostsQueryRepository` → `post_likes`, `posts`, `users` 조회.

인증 필터가 검증한 `Authentication.getName()`의 이메일로 기존 `MemberRepository.findByEmail()`을 사용한다. 요청에 회원 ID를 받지 않으며 타인 목록은 제공하지 않는다. 현재 계정 상태 ACTIVE·WARNED만 허용한다. 조회용 SNS Entity, 운영 DDL, 데이터 입력, 좋아요 추가·취소 및 SNS 글 작성 API는 추가하지 않는다.

기존 개인 화면의 사이드바·제목·카드 스타일을 유지하고 Mock 목록 대신 API 응답을 표시한다. 이전 요청은 페이지/계정 변경 시 취소하며 이전 계정 응답을 표시하지 않는다. 본문은 HTML로 실행하지 않고 문자열로 렌더링한다. 작성자 tag가 NULL 또는 me이면 공개 프로필 링크를 만들지 않는다.

## 요청

- 인증: 기존 HttpOnly 쿠키/BFF → 백엔드 Bearer 인증 방식 유지
- `page`: 0부터 시작, 기본 0, 최대 1,000,000
- `size`: 기본 20, 1~100
- 좋아요 시각 내림차순, 동일 시각은 게시글 ID 내림차순, NULL 시각은 마지막
- 공개 가능한 게시글만 집계한 `totalElements`, `totalPages` 제공
- 두 조회는 read-only REPEATABLE_READ 트랜잭션으로 같은 스냅샷 사용
- BFF 응답은 `Cache-Control: no-store`

## 응답 예시

```json
{
  "items": [{
    "id": 12,
    "type": "POST",
    "category": "정보",
    "contentPreview": "글 본문 최대 300자",
    "author": { "id": 3, "nickname": "개발자", "tag": null, "profileImage": null },
    "createdAt": "2026-11-01T12:00:00+09:00",
    "likedAt": "2026-11-02T12:00:00+09:00",
    "originalPost": null
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

REPOST의 `originalPost`에는 원본의 `id`, `contentPreview`, `author`를 제공한다. 제목·첨부 이미지·반응 수는 전달받은 ERD에 없어 응답/화면에 생성하지 않는다. 카드의 전체 글 링크는 기존 `/feeds/{id}`를 사용하며 SNS 상세 API 연결은 별도 담당 범위다.

## 사용자의 추정 진행 요청에 따른 가정

SNS 담당자의 실제 저장 값 확인 전 아래를 가정했다. 다른 값이면 조회 SQL 및 계약 테스트를 함께 조정해야 한다.

| 항목 | 가정 |
| --- | --- |
| 공개 범위 | `PUBLIC`만 표시. PRIVATE·미확정 값 제외 |
| 일반 글 | `type=POST`, `original_post_id IS NULL` |
| 퍼간 글 | `type=REPOST`. 원본이 삭제되지 않은 PUBLIC POST이고 원본 작성자가 ACTIVE일 때만 표시 |
| 작성자 상태 | ACTIVE만 표시. 제재·기타 상태 제외 |
| 중첩 퍼가기 | 제외 |
| ERD DATETIME | PostgreSQL timestamp without time zone에 저장한 한국 시각으로 가정, +09:00으로 응답 |

## 오류와 실행 제한

| 상태 | 의미 |
| --- | --- |
| 200 + 빈 items | 조회 성공, 표시할 좋아요가 없음 또는 범위 밖 페이지 |
| 400 | page/size 오류 |
| 401 | 미인증·잘못된 토큰 |
| 403 | 조회 불가 계정 상태 |
| 404 | 인증 이메일에 대응하는 회원 없음 |
| 503 | SNS 테이블 미준비 또는 목록 DB 조회 실패 |
| BFF 502 | 백엔드 연결 실패·응답 형식/페이지 불일치 |

실패를 빈 목록으로 바꾸지 않으며 DB 오류 문구·SQL을 브라우저에 전달하지 않는다. 공유 개발 DB에는 확인 당시 posts/post_likes 테이블이 없었다. ERD에 정의되어 있다는 사실과 DB에 실제 생성되어 있다는 사실은 구별한다. 공유 DB의 스키마와 데이터는 변경하지 않았다. 실제 데이터 목록 확인은 SNS 담당자의 테이블 준비와 위 저장 값 확인 후 가능하다.

## 검증

- 격리된 PostgreSQL 17 Testcontainers에서 JWT·Controller·Service·SQL 통합 테스트: 인증, 사용자 격리, 공개 범위, 원본 접근 조건, 페이지 정렬, NULL, 300자 요약, 데이터 미변경, 테이블 미준비 503.
- 프론트 parser·BFF·표시 테스트: 민감 필드 제거, 쿼리 검증, 취소 신호, 오류와 빈 목록 구별, 본문 이스케이프, 한국 시각, 원본 표시.
- 공유 DB에 좋아요 테스트 데이터 삽입 및 브라우저에서 실제 SNS 데이터 확인은 미실행.

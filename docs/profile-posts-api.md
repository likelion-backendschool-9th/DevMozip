# 프로필 포스팅 목록 (#123)

받은 리뷰도 사용자 요청으로 같은 브랜치에서 연결한다. [리뷰 계약](profile-reviews-api.md)을 참고한다.

작업 기준: develop 24b524e, 브랜치 feat/123-profile-posts. SNS #91의 쓰기 도메인과 분리된 조회 전용 구현이다.

## 조회 계약

| 경로 | 인증 | 범위 |
|---|---|---|
| GET /api/v1/users/me/posts | 필수 | 인증 이메일로 찾은 본인 ACTIVE/WARNED 회원 |
| GET /api/v1/users/{slug}/posts | 선택 | users.tag에 해당하는 ACTIVE 회원 |

두 경로 모두 page(기본 0, 0~1000000), size(기본 20, 1~100)를 사용한다. BFF는 `/api/profiles/{slug}/posts`이며 me도 같은 경로에서 백엔드 본인 API로 전달한다. 요청자 식별용 userId/email 쿼리는 전달하지 않는다.

응답은 items/page/size/totalElements/totalPages. 항목은 id/type/category/title/contentPreview/author/createdAt/originalPost. author는 id/nickname/tag/profileImage만 제공한다. 본문과 원본 미리보기는 DB에서 300자로 제한하고 날짜는 기존 timestamp 계약에 맞춰 +09:00으로 반환한다. UI는 HTML을 실행하지 않는다.

정렬은 created_at DESC NULLS LAST, id DESC. 카운트와 목록은 같은 필터·REPEATABLE_READ 트랜잭션에서 조회한다. 글 상세 이동은 기존 `/feeds/{id}`, 퍼간 글의 원본 이동도 같은 상세 경로를 사용한다. 상세/쓰기 API의 구현 완료를 의미하지 않는다.

## 실제 스키마와 공개 기준

공유 개발 DB의 posts 15개 컬럼을 읽기 전용 대조했다. author_id/original_post_id/type/category/title/content/visibility/created_at/deleted_at을 조회에 사용한다. 기존 좋아요 목록의 POST/REPOST·VISIBLE 기준을 재사용한다. 삭제·숨김·미확정 유형은 제외하고, 원본이 삭제·숨김·미존재·비활성 작성자인 퍼간 글도 원본 내용과 함께 제외한다. 본인 목록도 VISIBLE만 표시한다. 새 테이블·엔티티·DDL·공유 데이터 생성은 없다.

조회 구조: ProfilePostsController → ProfilePostsService(interface) → impl/ProfilePostsServiceImpl → ProfilePostsQueryRepository(JDBC) → 공개 record DTO. SecurityConfig는 본인 경로를 공개 패턴보다 먼저 인증 처리한다.

## 오류

400 페이지/개수 오류, 401 본인 미인증, 403 본인 계정 이용 불가, 404 대상 미존재/조회 불가, 503 DB 미준비/조회 오류. BFF 연결·응답 구조·대상/페이지 불일치는 502. 실패를 빈 목록으로 바꾸지 않는다. BFF GET 응답은 no-store이며 오류에 내부 SQL을 포함하지 않는다.

## 남은 계약과 범위

- #91 담당자와 전용 경로·응답 DTO·POST/REPOST/VISIBLE 값 및 원본 비공개 처리 정책 최종 협의 필요. 기존 피드 API의 작성·수정·삭제·댓글·좋아요·퍼가기 기능을 중복 구현하지 않았다.
- #110 비공개 계정 컬럼/공통 정책은 현재 develop·공유 DB에 없어 아직 미적용이다. 비공개 설정 기능과 동시 배포 전 작성자·원본 작성자 모두에 조회 조건을 적용해야 한다. 이 부분을 완료로 간주하지 않는다.
- 공유 posts는 확인 시 0건이다. 실제 실데이터 카드/상세 이동은 #91 게시글 등록 연결 후 추가 검증 필요. 데이터 있는 목록은 격리 테스트 DB·SSR 테스트로 검증한다.
- 활동·리뷰·배포 포폴 503은 별도 작업이다.

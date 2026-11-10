# 프로필 받은 리뷰 조회 (#123)

2026-11-10 사용자 요청으로 포스팅과 받은 리뷰 탭을 같은 브랜치에서 연결한다. 공개 프로필에서도 별점·키워드·작성자·프로젝트명을 보여 주는 정책을 사용자가 확정했다. 프로젝트 작성/종료/리뷰 요청·제출·활동 구현은 변경하지 않는다.

## API

| 경로 | 인증 | 대상 |
|---|---|---|
| GET /api/v1/users/me/reviews | 필수 | 인증 이메일로 찾은 본인 ACTIVE/WARNED |
| GET /api/v1/users/{slug}/reviews | 선택 | users.tag로 찾은 ACTIVE 공개 프로필 |

page 기본0/최대1000000, size 기본20/1~100. BFF GET `/api/profiles/{slug}/reviews`는 본인도 me로 전달하고 임의 userId/email 쿼리는 전달하지 않는다. 본인 경로는 공개 매처보다 먼저 인증 처리한다.

응답: userId/count/average/distribution/keywords/items/page/size/totalPages. distribution은 1~5점 순서의 5개 수, keywords는 name/count 목록, items는 id/projectId/projectTitle/authorUserId/authorNickname/authorTag/rating/keywords/submittedAt. 제출 시각에 +09:00을 명시한다. 제출 토큰·해시·만료일·이메일은 응답하지 않는다.

## 기존 서비스 재사용

ProfileReviewsController → ProfileReviewsService(interface) → impl/ProfileReviewsServiceImpl → 기존 ProjectReviewService.received(userId). 기존 서비스의 SUBMITTED 리뷰·유효 별점1~5·review_keyword_selections 기반 집계를 재사용한다. 비활성 키워드의 과거 선택도 보존한다. DTO를 프로필 전용 공개 응답으로 바꾸고 제출일 DESC NULLS LAST/id DESC로 정렬한 뒤 페이지를 구성한다.

현재 received가 회원의 전체 리뷰를 읽어 집계하는 계약이므로 프로필 페이지도 서버 내에서 전체 집계 후 items만 제한한다. DB 수준 집계·페이징 최적화는 미구현이다. 다른 팀원의 리뷰 서비스·Entity·Repository·신뢰 온도 계산은 수정하지 않는다.

## 공개 범위

- 타인 조회에서는 공개·미삭제 프로젝트만 이름과 ID를 보여 준다. 비공개/삭제/미존재 프로젝트는 projectId=null, projectTitle="비공개 프로젝트"로 바꿔 이름·상세 링크를 숨긴다. 별점·키워드·작성자 기록은 유지한다. 본인은 제출 당시 협업 기록의 기존 프로젝트 정보 조회를 유지한다.
- 프로필 계정 비공개 설정 #110은 현 develop/공유 DB에 컬럼·정책이 없어 아직 미연결이다. 설정 기능 배포 전 대상 프로필 및 작성자 정보 공개 정책을 공통 기준과 통합해야 한다. 현재 ACTIVE 기준을 새 비공개 설정 구현 완료로 간주하지 않는다.
- 신뢰 온도는 기존 프로필 응답 trustScore를 표시한다. 실제 별점으로 Mock 온도 공식을 적용하지 않는다.

## 화면·오류

기존 ProfileReviews.module.css의 overview·신뢰 카드·키워드 버블·리뷰 기록 배치를 재사용한다. 평균 별점/분포/선택 키워드 빈도, 작성자와 공개 프로젝트 링크, 페이지·로딩·0건·오류·재시도를 제공한다. 실패를 0건으로 표시하지 않고 사용자 입력은 React 텍스트로 이스케이프한다. 리뷰 작성/데모 추가 버튼은 실제 조회 탭에 추가하지 않는다.

400 잘못된 페이지/크기, 401 본인 미인증, 403 본인 이용 불가, 404 미존재/조회 불가, 503 DB 조회 실패. BFF 응답 파싱·집계/페이지 검증 실패와 연결 실패는 502, 모든 GET은 no-store. 내부 SQL/예외 원문은 숨긴다.

공유 DB·파일·배포 설정을 변경하지 않는다. 데이터와 함께 공개/본인 차이를 검증하는 테스트는 격리 PostgreSQL에서 수행하며 실제 브라우저 동작과 구분해 보고한다.

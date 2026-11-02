# 향후 REST API 설계 초안

**문서 초안이며 실제 API는 없습니다.** 기본 경로 `/api/v1`, 서버 `http://localhost:8080`, 프론트 개발 포트 5173.
아래 URL은 기본 경로를 생략했습니다. 인증 방식, 페이지네이션, 오류 스키마, 권한 정책은 백엔드 단계에서 확정합니다.
요청 필드/응답 필드는 개념 수준이며 최종 계약이 아닙니다. 향후 공통 응답은 `{ data, message? }`, 오류는 `{ code, message }`를 검토합니다.

| 도메인 | Method | URL | 기능 | 인증 필요 | 요청 | 응답 | 프리미엄 |
|---|---|---|---|---|---|---|---|
| Auth | POST | /auth/login | 로그인 | 아니오 | email, password | user, session | 아니오 |
| Auth | POST | /auth/signup | 회원가입 | 아니오 | email, password, nickname, profile | user | 아니오 |
| User | GET | /users/{id} | 사용자 조회 | 선택 | id | user | 아니오 |
| Profile | PATCH | /users/me/profile | 프로필 수정 | 예 | 소개, 기술, 연락 시간 | profile | 아니오 |
| Portfolio | GET | /users/{id}/portfolios | 포트폴리오 목록/상세 | 선택 | id | portfolio[] (권한에 따라 상세 제한) | 상세 예 |
| Portfolio | POST | /portfolios | 포트폴리오 등록 | 예 | 제목, 기간, 역할, 기술, 내용 | portfolio | 아니오 |
| Search | GET | /search | 개발자/프로젝트 검색 | 선택 | target, q, filters, page | items, total | 고급 검색 예 |
| Follow | POST | /users/{id}/follow | 팔로우 | 예 | id | follow | 아니오 |
| Follow | DELETE | /users/{id}/follow | 언팔로우 | 예 | id | 성공 여부 | 아니오 |
| Project | GET | /projects | 프로젝트 목록 | 아니오 | q, role, stack, status, sort | projects, total | 아니오 |
| Project | GET | /projects/{id} | 프로젝트 상세 | 아니오 | id | project | 아니오 |
| Project | POST | /projects | 모집 공고 생성 | 예 | 소개, 목표, 기간, 역할, 기술 | project | 예 |
| Project | PATCH | /projects/{id} | 공고/상태 수정 | 예 (주최자) | 변경 필드 | project | 정책 검토 |
| Project | DELETE | /projects/{id} | 공고 삭제 | 예 (주최자) | id | 삭제 결과/제한 사유 | 정책 검토 |
| Project Application | POST | /projects/{id}/applications | 지원 | 예 | portfolioId, message | application | 아니오 |
| Project Application | PATCH | /projects/{id}/applications/{applicationId} | 승인/거절 | 예 (주최자) | status | application, members | 아니오 |
| Project Application | DELETE | /projects/{id}/applications/me | 지원 취소 | 예 | id | result, penaltyNotice | 아니오 |
| Project Chat | GET | /projects/{id}/chat | 단체 채팅 조회 | 예 (참여자) | id, cursor | members, messages | 아니오 |
| Project Chat | POST | /projects/{id}/chat/messages | 단체 메시지 작성 | 예 (참여자) | body | message | 아니오 |
| Post | GET | /posts | 피드 조회 | 선택 | category, q, sort, following | posts, total | 아니오 |
| Post | POST | /posts | 게시글 작성 | 예 | title, category, body, images, tags, links | post | 아니오 |
| Post | GET | /posts/{id} | 글 상세 | 선택 | id | post | 아니오 |
| Comment | POST | /posts/{id}/comments | 댓글 작성 | 예 | body | comment | 아니오 |
| Comment | PATCH | /comments/{id} | 댓글 수정 | 예 (작성자) | body | comment | 아니오 |
| Repost | POST | /posts/{id}/reposts | 퍼가기 | 예 | optional opinion | repost, originalPost | 아니오 |
| Repost | DELETE | /posts/{id}/reposts/me | 퍼가기 취소 | 예 | id | result | 아니오 |
| Bookmark | POST | /bookmarks | 북마크 | 예 | targetType, targetId | bookmark | 아니오 |
| Review | POST | /projects/{id}/reviews | 협업 리뷰 | 예 (기간 내 협업자) | targetId, rating, keywordIds | review, temperature | 아니오 |
| Review | GET | /users/{id}/reviews | 리뷰/신뢰 통계 | 선택 | id | rating, keywords, ratios, temperature | 아니오 |
| Matching | POST | /matching/recommendations | 추천 실행 | 예 | description, stacks, interests, role | developers, projects, scores, remaining | 상세 이유 예 |
| Message | GET | /conversations | 1:1 대화 목록 | 예 | cursor | conversations | 아니오 |
| Message | POST | /conversations/{id}/messages | 1:1 메시지 작성 | 예 (참여자) | body | message | 아니오 |
| Notification | GET | /notifications | 알림 조회 | 예 | cursor, unread | notifications | 아니오 |
| Notification | PATCH | /notifications/{id} | 읽음 처리 | 예 | read | notification | 아니오 |
| Premium | GET | /premium/subscription | 구독 상태 | 예 | 없음 | subscription, payments | 아니오 |
| Premium | POST | /premium/subscription | 구독/해지 요청 | 예 | plan, action | subscription | 정책 검토 |
| Admin | GET | /admin/dashboard | 통계 조회 | 예 (관리자) | 기간 | counts, reports, usage | 아니오 |
| Admin | PATCH | /admin/users/{id} | 회원 경고/정지/해제 | 예 (관리자) | action | userStatus | 아니오 |
| Admin | PATCH | /admin/moderation/{type}/{id} | 프로젝트/글/댓글 신고 처리 | 예 (관리자) | action, reasonCode | moderationResult | 아니오 |

향후 보완: 회원/닉네임 중복확인, 비밀번호 찾기, 로그아웃, 프로필 수정, 포트폴리오 수정/삭제, 팔로워/팔로잉 목록과 알림 설정, 모집 기간 연장, 팀원 관리, 채팅 종료 정책, 글/댓글 삭제, 좋아요, 북마크 취소, 신고, 메시지 읽음, 구독 내역 및 관리자 상세 관리.
현재 단계에서는 외부 인증·Gemini·Kakao·Toss·DB 연동을 설계 계약으로 확정하거나 구현하지 않습니다.

## 최종 프로토타입 범위를 반영한 추가 REST 후보

아래도 **미구현 설계 후보**입니다. 현재 화면은 이 주소로 요청하지 않으며 결제/OAuth 연동 계약도 아닙니다.

| Method | 기본 경로 이후 URL | 목적 | 권한/비고 |
|---|---|---|---|
| POST | /auth/logout | 로그아웃 | 로그인 |
| POST | /auth/password-recovery | 비밀번호 복구 요청 | 메일 정책 미확정 |
| GET | /auth/availability | 이메일/닉네임 중복 확인 | 공개, 남용 방지 정책 필요 |
| PATCH | /portfolios/{id} | 포트폴리오 수정 | 작성자 |
| DELETE | /portfolios/{id} | 포트폴리오 삭제 | 작성자, 지원서 스냅샷 유지 검토 |
| POST | /portfolios/{id}/feedback | 포트폴리오 피드백 | 프리미엄/관리자 |
| GET | /users/{id}/followers | 팔로워 목록 | 공개 범위 검토 |
| GET | /users/{id}/following | 팔로잉 목록 | 공개 범위 검토 |
| PATCH | /users/{id}/follow-notifications | 활동/모집 알림 설정 | 팔로워 본인 |
| GET | /users/me/activities | 모집/참여 프로젝트 이력 | 본인 |
| GET | /projects/{id}/applications | 지원자 목록 | 주최자 |
| GET | /projects/{id}/members | 팀원 목록 | 접근 범위 검토 |
| PATCH | /projects/{id}/period | 모집/개발 기간 연장 | 주최자 |
| POST | /projects/{id}/close | 종료 및 채팅 보관/삭제 선택 | 주최자, 리뷰 자격 생성 |
| POST | /projects/{id}/reports | 프로젝트 신고 | 로그인 |
| PATCH | /posts/{id} | 게시글 수정/숨김 | 작성자 |
| DELETE | /posts/{id} | 게시글 삭제 | 작성자 |
| POST | /posts/{id}/likes | 좋아요 | 로그인, 멱등성 검토 |
| DELETE | /posts/{id}/likes/me | 좋아요 취소 | 본인 |
| DELETE | /comments/{id} | 댓글 삭제 | 작성자/운영 권한 |
| PATCH | /reposts/{id} | 퍼가기 의견/숨김 수정 | 본인 |
| DELETE | /bookmarks/{id} | 북마크 취소 | 본인 |
| POST | /reports | 글/댓글/사용자 신고 | 로그인, 대상별 중복 제한 |
| POST | /conversations | 1:1 대화 시작/기존 대화 반환 | 로그인, 상대 확인 |
| GET | /conversations/{id}/messages | 메시지 조회 | 참여자, 커서 |
| PATCH | /conversations/{id}/read | 읽은 위치 기록 | 참여자 |
| POST | /conversations/{id}/proposals | 협업 제안 | 참여자, 제목/본문 |
| PATCH | /projects/{id}/chat/read | 단체 채팅 읽음 | 등록 팀원 |
| PATCH | /notifications/read-all | 전체 읽음 | 현재 계정 |
| GET | /matching/usage | 한국 시각 월별 한도/사용량 | 현재 계정 |
| GET | /premium/payments | 결제/구독 이력 | 본인, 실제 결제 단계 별도 |
| GET | /admin/users | 회원 검색/상태 | 관리자 |
| GET | /admin/reports | 신고 목록/처리 상태 | 관리자 |
| PATCH | /admin/posts/{id}/visibility | 게시글 숨김/복원 | 관리자 |
| DELETE | /admin/comments/{id} | 댓글 삭제 | 관리자 |
| GET | /review-keywords | 활성 리뷰 선택지 | 공개 |
| POST | /admin/review-keywords | 키워드 추가 | 관리자 |
| DELETE | /admin/review-keywords/{id} | 신규 선택 비활성화 | 관리자, 과거 리뷰 보존 |
| GET | /admin/trust | 온도 현황 | 관리자 |
| GET | /admin/ai-usage | AI 사용량/오류 | 관리자 |
| GET | /admin/subscriptions | 프리미엄 현황 | 관리자 |

공통 후속 과제: 인증/서버 인가, 페이지네이션, 멱등성·동시 승인/정원 경쟁, 트랜잭션 기반 초대/퇴장·알림 생성, 리뷰 중복 제약, 저장/삭제/보존 정책, 이미지 서버 검증. 이 목록은 향후 서버 작업 범위를 설명하며 이번 단계에 구현하지 않았습니다.

## 2차 프로젝트 리뷰 흐름의 향후 계약 후보

현재는 아래 서버를 만들거나 호출하지 않습니다. 전부 브라우저 Mock으로 수행합니다.

| Method | URL | 후보 역할 |
|---|---|---|
| GET | /users/me/projects | 주최/참여/지원/종료 필터와 내 역할 |
| GET | /projects/{id}/review-requests | 주최자의 요청·제출 현황 (타인 토큰 노출 금지) |
| GET | /projects/{id}/review-requests/me | 본인의 리뷰 요청/만료/사용 상태 |
| GET | /project-review-requests/{id} | 실제 로그인 수신자·프로젝트 참여·기한·토큰 검증 후 대상 반환 |
| POST | /project-review-requests/{id}/submissions | 미제출 팀원 별점/키워드 묶음 제출 및 완료/알림/온도 반영 |

후속 보안 요건: 서버 시각의 만료 검증, 수신자 인증/프로젝트 종료 당시 멤버 검증, 안전한 일회성 토큰 발급·저장·폐기, 작성자/대상자/프로젝트의 고유 제약, 전체 평가와 사용 완료의 원자적 처리, 재알림의 중복 방지와 운영 정책. 프론트엔드의 날짜 시뮬레이션은 서버 기능으로 구현하지 않습니다.

## 3차 강퇴 투표·권한의 향후 계약 후보

현재 서버 구현/호출은 없습니다.

| Method | URL | 후보 역할 |
|---|---|---|
| GET | /projects/{id}/chat/kick-votes | 방 접근 권한 확인 후 진행/종료 투표 조회 |
| POST | /projects/{id}/chat/kick-votes | 대상·사유·유권자 스냅샷·만료 시각 생성 |
| POST | /projects/{id}/chat/kick-votes/{voteId}/ballots | 대상자 제외·계정별 중복 차단·찬반 제출 |
| GET | /projects/{id}/applications/{applicationId}/portfolio | 해당 프리미엄 주최자의 제출 포트폴리오 상세 |

서버 후속: 최신 구독의 생성/열람 권한 검증, 주최자/방 멤버 인가, 서버 시각 만료 처리, 중복 투표 고유 제약, 과반수 판정·채팅 제외·결과 알림의 원자성/멱등성. 프로젝트 참여 상태 변경과 해지 후 기존 공고 관리 정책 확정이 먼저 필요합니다. 실제 WebSocket/스케줄러는 이번에 구현하지 않았습니다.

## 4~5차 통합 상태를 반영한 후속 API 후보

화면 너비와 헤더 검색 UI 변경에는 새 API가 필요하지 않습니다. 실제 호출은 계속 0건이며 아래는 향후 서버 계약 후보입니다.

| Method | URL | 후보 역할 |
|---|---|---|
| GET/POST | /matching/conversations | 현재 계정의 추천 대화 목록/생성 |
| GET/DELETE | /matching/conversations/{id} | 본인 메시지·추천 스냅샷 복원/삭제 |
| POST | /matching/conversations/{id}/messages | 일반 대화/조건 추가 질문, 결과 미생성 시 미차감 |
| POST | /matching/conversations/{id}/recommendations | 복수 분야·관심·기술·직군별 인원·경력·기간 검증 후 결과 생성과 월 사용량 원자적 차감 |
| GET/PATCH | /users/me/account | Mock 공개 설정/연동 UI의 향후 계정 설정 계약 |
| GET/PATCH | /users/me/onboarding | 초안/단계 저장과 완료 시 프로필 반영 |

기존 초기 단일 리뷰 POST 후보는 현재 2차의 요청별 일괄 제출 흐름과 통합해 최종 계약을 확정해야 합니다. 검색은 기존 `/search` 계약을 재사용합니다. 본인 대화 인가·구독/월 한도·한국 시각 기준·동시 생성 멱등성·데이터 보존/삭제·개인정보 변경 보안·소셜 연동 검증은 백엔드 단계 사항입니다. 실제 AI/OAuth/결제와 비밀번호 변경 API는 이번에 연결하지 않았습니다.

## Next.js 이전 후 실행 경계

프론트엔드는 Next.js App Router로 이전했지만 실제 서비스 API 계획은 Spring Boot 기준으로 유지합니다. Next Route Handler/Server Action을 비즈니스 API로 구현하지 않았습니다. 브라우저의 RSC·정적 청크 요청은 Next.js 페이지 이동에 필요한 프레임워크 요청입니다. 미사용 Axios 공통 인스턴스는 `frontend/lib/shared/api.ts`, 선택 환경변수는 `NEXT_PUBLIC_API_BASE_URL`입니다.

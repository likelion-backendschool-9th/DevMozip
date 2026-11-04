# AI API 명세

기준일: 2026-11-04. **서버 대화·메시지 API 5개 구현, 프론트 실제 연결·실사용자 인증 연결은 미완료**다. 전체 단계·통합 과제·필요 자료는 [개발 청사진](ai-progress.md), 공통 정책은 [기능 정책](ai-matching.md)를 따른다.

## 공통 계약

- 서버 기본 경로 `/api/v1/ai`, 화면 `/ai/matching`.
- 아래 서버 영속 API는 인증을 요구한다. 비로그인 대화·메시지·조건은 프론트 로컬에서 처리하며 익명 서버 저장 API를 제공하지 않는다.
- 사용자 ID는 인증 정보에서 얻고 모든 대화 접근에 소유권을 검증한다. 현재 실사용자 연결의 미완료 사항은 아래 인증 절을 따른다.
- 현재 응답은 DTO 객체/배열이며 별도 `data` envelope를 쓰지 않는다. 입력 JSON 필드는 아래 1단계 표에 명시된 항목이다.
- 1단계 시각은 오프셋 없는 LocalDateTime ISO-8601(예: `2026-11-04T15:00:00`). 앱/DB 기준 시간대는 미확정이다.

## 1단계 구현 API

| Method·URL | 요청 | 성공 응답 |
|---|---|---|
| POST `/api/v1/ai/conversations` | 본문 없음, `{}` 또는 `{"title":"팀원 찾기"}`. title null 허용·최대 255자 | 201, `Location: /api/v1/ai/conversations/{id}`, 대화 요약 |
| GET `/api/v1/ai/conversations` | 본문 없음 | 200, 내 대화 요약 배열. `createdAt DESC, id DESC`. 페이지네이션 없음 |
| GET `/api/v1/ai/conversations/{id}` | 양수 BIGINT 대화 ID | 200, 상세. 메시지 `createdAt ASC, id ASC` |
| DELETE `/api/v1/ai/conversations/{id}` | 양수 BIGINT 대화 ID | 204, 본문 없음 |
| POST `/api/v1/ai/conversations/{id}/messages` | `{"content":"백엔드 개발자를 찾고 있어요"}`. content 필수·공백만 입력 불가 | 201, 저장한 USER 메시지. AI 응답 없음 |

### 응답 필드

| 응답 | 필드 |
|---|---|
| 대화 요약 `ConversationResponse` | `id`: 정수, `title`: 문자열/null, `createdAt`, `updatedAt`: 시각 |
| 대화 상세 `ConversationDetailResponse` | 요약 필드 + `currentConditions`: JSON 값/null, `messages`: 메시지 배열 |
| 메시지 `MessageResponse` | `id`: 정수, `role`: USER/ASSISTANT, `content`: 문자열, `createdAt`: 시각 |

`currentConditions`는 엔티티 내부에서 JSON 문자열로 보관하고 상세 응답에서 JSON 값으로 직렬화한다. 1단계는 기존 조건을 조회만 하며 생성/메시지 요청으로 조건을 저장하지 않는다. 저장 API는 role을 USER로 정하고 경로의 본인 대화에 연결한다. 사용자 ID·메시지 ID·생성 시각은 서버가 정하며 요청의 임의 식별자·역할로 바꿀 수 없다. 서버 메시지 길이 상한은 현재 구현에 별도 정의가 없고 프론트 Mock의 1,500자를 서버 계약으로 간주하지 않는다.

### 오류·삭제

| 상태 | 현재 처리 |
|---|---|
| 400 | 잘못된 JSON/ID 형식, 0 이하 ID, 255자 초과 제목, null/빈/공백 메시지 |
| 401 | 비로그인, AI 사용자 식별 미준비, 개발 ID 설정 누락·해당 users 행 부재 |
| 404 | 미존재 대화 또는 다른 사용자 소유 대화. 응답을 동일하게 처리 |
| 500 | 공통 DB 접근 오류·예상하지 못한 FK 충돌. 삭제 트랜잭션 전체 롤백 |

오류는 기존 `ErrorResponse`의 `status`, `error`, `message`, `path`, `timestamp`를 재사용한다. 정지 계정 등 기존 인증 체계의 별도 거절 정책은 그대로 따른다.

삭제는 **추천 결과 → 추천 요청 → 메시지 → 대화** 순서다. 대화 FK뿐 아니라 메시지 FK를 통해 연결된 추천 요청도 포함한다. `ai_usages`는 삭제/복원하지 않는다. 현재 쿼리는 `ai_recommendation_requests.ai_conversation_id`, `ai_message_id`, `ai_recommendations.recommended_request_id`를 직접 참조한다. 실제 테이블/컬럼이 없으면 삭제가 실패하며, 추천 저장 API가 없다는 이유로 연관 삭제를 생략하지 않는다.

## 인증·프론트 연결의 남은 계약

현재 `config/SecurityConfig.java`는 users 기반 이메일 로그인을 제공하고 JWT principal은 이메일 문자열이다. AI는 `security.ai.CurrentUserProvider#getCurrentUserId()`로 users PK를 얻는 구조지만 실서비스용 어댑터는 없다. 이전 문서의 `auth/SecurityConfig`·인메모리 계정 설명은 현재 코드와 달라 정정했다.

- `dev`/`test` 프로필 + `ai.current-user.development-enabled=true`일 때만 개발용 고정 ID를 사용한다. `prod`/`production`이 함께 활성화되면 거부한다. ID는 실제 users에 존재해야 하고 자동 INSERT하지 않는다.
- `application-dev.yaml`은 `AI_DEV_USER_ENABLED`, `AI_DEV_USER_ID`를 읽는다. 모든 인증 요청을 한 ID로 취급하는 임시 방식이므로 실제 A/B 사용자 분리 검증용이 아니다.
- 기본/운영 환경은 실제 Provider 빈이 없으면 요청을 거부한다. `ConditionalOnMissingBean` 교체 지점을 통해 실사용자 어댑터를 제공해야 한다. Controller/Service 입력에 요청자 ID를 추가하지 않는다.
- 브라우저는 HttpOnly 인증 쿠키를 사용한다. AI API용 BFF/프록시의 토큰 전달·갱신 계약은 미완료이며 [팀 인증 안내](team-auth-deployment.md)와 맞춰야 한다.
- 로그인 전후 초안 인계 API는 확정되지 않았다. 기존 생성/메시지 API에는 로컬 ID·멱등성 키·원래 작성 시각·조건 일괄 전송 필드가 없다. 저장 시점·재시도·부분 실패·중복 저장 계약을 먼저 정한다.

## 현재 매핑과 ERD 확인 항목

| 테이블 | 현재 엔티티 필드·관계 |
|---|---|
| `ai_conversations` | `id` BIGINT IDENTITY, `user_id` 필수 숫자, `title` 최대 255자/null, `current_conditions` JSON/null, `created_at`, `updated_at` 필수. type/status 없음 |
| `ai_messages` | `id` BIGINT IDENTITY, `conversation_id` 필수 ManyToOne, `role` USER/ASSISTANT, `content` TEXT 필수, `created_at` 필수. updated_at 없음 |

이 표는 코드 매핑이며 실제 DB DDL 확인 결과가 아니다. `user_id`는 JPA 연관 엔티티가 아닌 숫자다. 최신 ERD·DDL·연관 추천 테이블 FK·삭제 제약을 확보해 대조해야 한다. [PostgreSQL 검토 초안](sql/ai-conversations-proposal.sql)과 `backend/src/test/resources/ai/schema.sql`은 각각 참고용/격리 테스트용이다. 로컬 H2와 기본 PostgreSQL의 실제 검증 범위는 청사진에 기록한다.

## 후속 API 개요 — 미구현

메서드·경로와 대략적인 역할만 기록한다. 아래 본문·성공 상태·오류 코드·응답 구조는 각 단계 요청 후 구체화하며 지금 호출 가능한 계약으로 사용하지 않는다.

| 단계 | Method·URL | 역할·남은 계약 |
|---|---|---|
| 2 | PUT `/api/v1/ai/conversations/{id}/conditions` | 선택 조건 전체 교체. DTO 키·DB 코드·필수/선택·빈 값 검증 필요 |
| 3 | 외부 API 추가 없음 | 후보 조회·점수 계산 엔진만 구현하는 단계 |
| 4 | POST `/api/v1/ai/conversations/{id}/recommendations` | 실제 추천 실행·요청/결과 저장. 대화 연결 필수, 메시지 연결 선택·동일 대화 검증, 중복 요청 식별 설계 |
| 4 | GET `/api/v1/ai/conversations/{id}/recommendations` | 저장 결과 조회. 최신/특정 과거 요청 선택 계약은 미확정. 재계산 없음 |
| 5 | 기존 메시지 API 확장 방향, 세부 계약 미확정 | 자연어 분석·조건 병합·추가 질문·ASSISTANT 저장. 1단계의 저장 전용 동작과 구분 |
| 6 | POST `/api/v1/ai/recommendations/{id}/detailed-analysis` | 추천 결과의 개발자 상세 분석. 서버 프리미엄·소유권·포트폴리오 열람권 확인 |
| 7 | GET `/api/v1/ai/usage` | 한국 시간 현재 월의 사용/한도/잔여 횟수. JSON 키·한도 오류 계약 미확정 |

4단계 결과는 `recommended_user_id`/`recommended_project_id` 중 정확히 하나, `match_score`·`basic_reason`·`rank`, 초기 `detailed_reason` null 방향이다. `recommendation_type`은 추가하지 않는다. 실제 DDL과의 일치는 해당 단계에서 확인한다. 첨부의 논리 관계 `conversation_id/message_id`를 현재 물리 컬럼명과 혼동하지 않는다.

사용량 차감은 7단계에서 완성한다. 메시지·조건 변경·조회·상세 분석은 미차감이며, 성공한 신규 추천만 1회다. 4단계에서는 추후 중복 차감 방지를 위해 요청 식별을 설계한다. [이전 전역 API 초안](api-plan.md)의 `/matching/...` URL·Mock 점수식·응답 후보는 이 명세를 대체하지 않는다.

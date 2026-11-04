# AI API 명세

기준일: 2026-11-04. **서버 대화·메시지 API 5개와 실사용자 인증 연결 구현, 로컬 브라우저 로그인 왕복 검증 완료. 프론트 AI API 연결·실제 PostgreSQL 통합 검증은 미완료**다. 전체 단계·통합 과제·필요 자료는 [개발 청사진](ai-progress.md), 공통 정책은 [기능 정책](ai-matching.md)를 따른다.

## 공통 계약

- 서버 기본 경로 `/api/v1/ai`, 화면 `/ai/matching`.
- 아래 서버 영속 API는 인증을 요구한다. 비로그인 대화·메시지·조건은 프론트 로컬에서 처리하며 익명 서버 저장 API를 제공하지 않는다.
- 사용자 ID는 인증 정보에서 얻고 모든 대화 접근에 소유권을 검증한다. 실사용자 식별 방식과 남은 BFF 계약은 아래 인증 절을 따른다.
- 현재 응답은 DTO 객체/배열이며 별도 `data` envelope를 쓰지 않는다. 입력 JSON 필드는 아래 1단계 표에 명시된 항목이다.
- 1단계 시각은 오프셋 없는 LocalDateTime ISO-8601(예: `2026-11-04T15:00:00`). 앱/DB 기준 시간대는 미확정이다.

### 추가 제공 API 표 반영 (2026-11-04)

사용자 제공 표의 화면 경로 `/ai/matching` 및 10개 API 항목을 기존 명세와 대조했다. 대화 5개·조건 PUT·추천 실행/조회·상세 분석·사용량 조회 구성은 기존 명세와 일치한다. 메시지의 자연어 분석·추가 질문은 5단계 목표이며 현재 1단계는 USER 저장만 한다. 표 제공은 후속 단계 구현 지시가 아니다.

- 무료 월 추천 한도는 최신 표에 따라 **5회**, 프리미엄은 **100회**로 반영한다. '조건으로 추천 받기' 실행 후 신규 결과 생성 성공 시 1회 차감하고 일반 대화·조건 변경은 미차감이다. 실제 제한 기능은 7단계 미구현이다.
- 표의 `detailed-analysi`는 기존 확정 경로 `detailed-analysis`에서 마지막 `s`가 빠진 표기로 기록한다. 기존 확정 경로 `/api/v1/ai/recommendations/{id}/detailed-analysis`를 유지하며 별도 API를 추가하지 않는다.
- 표는 기능·메서드·경로를 제공하지만 DTO·오류·초안 인계·BFF 전달 계약 전체를 확정하지는 않는다. 미확정 항목은 아래에 유지한다.

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
| 401 | 비로그인·미인증, 인증 이메일에 해당하는 users 행 부재, 개발 ID 설정 누락·해당 users 행 부재 |
| 404 | 미존재 대화 또는 다른 사용자 소유 대화. 응답을 동일하게 처리 |
| 500 | 공통 DB 접근 오류·예상하지 못한 FK 충돌. 삭제 트랜잭션 전체 롤백 |

오류는 기존 `ErrorResponse`의 `status`, `error`, `message`, `path`, `timestamp`를 재사용한다. 정지 계정 등 기존 인증 체계의 별도 거절 정책은 그대로 따른다.

삭제는 **해당 대화의 메시지 → 본인 대화** 순서로 한 트랜잭션에서 처리한다. 사용자 최신 요청에 따라 1단계의 추천 요청·결과 테이블 참조를 제거했다. `ai_recommendations`, `ai_recommendation_requests`, `ai_usages`가 없는 H2에서도 삭제할 수 있고 다른 대화·메시지는 유지한다. 추천 이력·월 사용량을 조회/삭제/복원하지 않으며, 추천 기능 도입 후 필요한 FK·삭제 정책은 해당 단계에서 별도 확정한다.

## 인증 연결과 남은 프론트 계약

현재 `config/SecurityConfig.java`는 users 기반 이메일 로그인을 제공하고 JWT principal은 이메일 문자열이다. AI는 `security.ai.CurrentUserProvider#getCurrentUserId()`로 users PK를 얻으며 `AuthenticatedCurrentUserProvider`가 검증된 인증 정보의 이메일을 기존 `MemberRepository.findByEmail`로 조회한다. 공통 JWT 필터·로그인·사용자 엔티티는 변경하지 않았다. 이전 문서의 `auth/SecurityConfig`·인메모리 계정 설명은 현재 코드와 달라 정정했다.

- `dev`/`test` 프로필 + `ai.current-user.development-enabled=true`일 때만 개발용 고정 ID를 사용한다. `prod`/`production`이 함께 활성화되면 고정 ID 대신 실사용자 인증을 적용한다. 개발 ID는 실제 users에 존재해야 하고 자동 INSERT하지 않는다.
- `application-dev.yaml`은 `AI_DEV_USER_ENABLED`, `AI_DEV_USER_ID`를 읽는다. 모든 인증 요청을 한 ID로 취급하는 임시 방식이므로 실제 A/B 사용자 분리 검증용이 아니다.
- 기본/local/운영 환경과 dev/test opt-in이 꺼진 환경은 실사용자 어댑터를 사용한다. 익명·미인증·삭제된 사용자는 401, 정지된 사용자는 403이다. 운영 프로필에서는 개발 옵션을 켜도 고정 ID를 사용하지 않는다. `ConditionalOnMissingBean` 교체 지점은 유지하며 Controller/Service 입력에 요청자 ID를 추가하지 않는다.
- 브라우저는 HttpOnly 인증 쿠키를 사용한다. AI API용 BFF/프록시의 토큰 전달·갱신 계약은 미완료이며 [팀 인증 안내](team-auth-deployment.md)와 맞춰야 한다.
- 로컬 초안 이어받기는 위 계약으로 구현했으며, 서버 초안 동기화 API는 확정되지 않았다. 기존 생성/메시지 API에는 로컬 ID·멱등성 키·원래 작성 시각·조건 일괄 전송 필드가 없다. 저장 시점·재시도·부분 실패·중복 저장 계약을 먼저 정한다.

## 1단계 로컬 초안 계약 — 서버 API 호출 없음

- 비로그인과 로그인 모두 추천 전까지 대화·조건·전송 전 입력문을 `devmozip:home:v1`에 보관한다. `MatchingConversation.draft`는 브라우저 전용 선택 필드이며 서버 DTO에 추가하지 않았다. 이전 저장 데이터도 복원한다.
- 전송은 USER 메시지만 추가하고 선택 조건을 그대로 보존한다. 자동 Mock 답변·자연어 분석·추천·사용량 차감은 제거했다. 기존 저장된 Mock 메시지/결과는 조회 호환을 유지한다.
- 비로그인의 추천 버튼·초안 로그인 링크는 저장 성공 후 `/login?next=%2Fai%2Fmatching`으로 이동한다. 저장 실패 시 이동을 중단하고 입력을 유지한다. 로그인 사용자의 추천 버튼은 준비 중 안내만 제공한다.
- 로그인 후 활성 guest 초안 하나를 현재 계정에 옮기는 확인 버튼을 제공한다. 로그인만으로 이동하지 않는다. 저장 성공 시 guest에서 원본을 제거하고 현재 계정의 활성 대화로 선택하며 다른 guest/계정 기록은 유지한다. 실패 시 원본을 유지한다.
- 로컬 소유 키는 실제 로그인 이메일별로 구분하며, 이메일이 일치하는 기존 데모 계정은 이전 ID 키를 유지한다. 공통 Mock 프로필 ID가 같은 일반 계정도 대화는 섞이지 않게 했다. 이는 브라우저 저장 분리일 뿐 서버의 users PK·소유권 검증을 대체하지 않는다.
- 로그인 직후 서버에 전송하지 않는다. 최초 추천 시 대화·USER 메시지·조건 전송, 로컬/서버 ID 연결·원래 시각·부분 실패·중복 재시도·원자성은 4단계 후속 계약으로 남긴다. 이번에 동기화 API나 조건 PUT을 구현하지 않았다.

## 현재 매핑과 ERD 확인 항목

| 테이블 | 현재 엔티티 필드·관계 |
|---|---|
| `ai_conversations` | `id` BIGINT IDENTITY, `user_id` 필수 숫자, `title` 최대 255자/null, `current_conditions` JSON/null, `created_at`, `updated_at` 필수. type/status 없음 |
| `ai_messages` | `id` BIGINT IDENTITY, `conversation_id` 필수 ManyToOne, `role` USER/ASSISTANT, `content` TEXT 필수, `created_at` 필수. updated_at 없음 |

이 표는 코드 매핑이며 실제 DB DDL 확인 결과가 아니다. `user_id`는 JPA 연관 엔티티가 아닌 숫자다. 아래 제공 ERD와 대조했으며 실제 DB의 제약·적용 상태는 별도 확인이 필요하다. [PostgreSQL 검토 초안](sql/ai-conversations-proposal.sql)과 `backend/src/test/resources/ai/schema.sql`은 각각 참고용/격리 테스트용이다.

### 제공 ERD 판독 내용 (2026-11-04)

출처는 이번 대화에 첨부된 ERD 이미지들과 추가 API 표다. 다른 채팅에서도 참고하도록 읽을 수 있는 AI 테이블 5개를 전사했다. 추가 이미지에서 보이는 users의 AI 관련 항목은 아래에 따로 대조했다. 잘린 외부 테이블·관계선은 추정하지 않았다. 아래는 **설계도에 표시된 컬럼·타입**이며 실제 DB 조회 결과나 후속 단계 구현 승인이 아니다.

| 테이블 | ERD 컬럼·표시 타입 |
|---|---|
| `ai_conversations` | `id` BIGINT (PK), `user_id` BIGINT, `title` VARCHAR(255), `created_at` DATETIME, `current_conditions` JSONB, `updated_at` DATETIME |
| `ai_messages` | `id` BIGINT (PK), `conversation_id` BIGINT, `role` VARCHAR(20), `content` TEXT, `created_at` DATETIME |
| `ai_recommendation_requests` | `id` BIGINT (PK), `ai_conversation_id` BIGINT, `ai_message_id` BIGINT, `conditions` JSONB, `analysis_level` VARCHAR(20), `status` ENUM, `created_at` DATETIME, `completed_at` DATETIME |
| `ai_recommendations` | `id` BIGINT (PK), `recommended_request_id` BIGINT, `recommended_project_id` BIGINT, `recommended_user_id` BIGINT, `match_score` DECIMAL(5,2), `basic_reason` TEXT, `detailed_reason` TEXT, `rank` INT, `created_at` DATETIME |
| `ai_usages` | `id` BIGINT (PK), `user_id` BIGINT, `usage_month` CHAR(7), `used_count` INT, `created_at` DATETIME, `updated_at` DATETIME |

AI 내부의 참조 컬럼은 메시지 → 대화(`conversation_id`), 추천 요청 → 대화/메시지(`ai_conversation_id`/`ai_message_id`), 추천 결과 → 요청(`recommended_request_id`)이다. `recommended_request_id`는 추천 결과의 설계 참조 컬럼이며 현재 1단계 삭제 쿼리에서는 사용하지 않는다. 추가 이미지에서 대화·추천 결과·사용량의 사용자 참조와 users.id를 확인했다. 프로젝트 테이블의 정의·실제 FK 제약은 별도 ERD/DDL로 확인한다.

| 대조 항목 | 확인 결과·남은 확인 |
|---|---|
| 대화·메시지 컬럼 | 현재 엔티티의 컬럼명과 일치. 대화 type/status, 메시지 updated_at은 ERD에도 없음 |
| 조건 저장 타입 | ERD는 `current_conditions`·`conditions` 모두 JSONB. 현재 대화 엔티티는 `@JdbcTypeCode(SqlTypes.JSON)`이며 PostgreSQL 실제 컬럼 타입을 조회한 것은 아님. 이 애너테이션 이름만으로 json 컬럼 사용이나 불일치를 단정하지 않음 |
| 시각 | ERD의 DATETIME은 표시 타입. 현재 Java는 LocalDateTime, PostgreSQL 초안은 TIMESTAMP WITHOUT TIME ZONE. PostgreSQL 적용 DDL·시간대 확인 필요 |
| 삭제 쿼리 | 현재 쿼리는 ai_messages.conversation_id만 사용. H2 fixture는 사용자·대화·메시지만 포함. 실제 PostgreSQL FK/삭제 제약·추가 참조는 미검증 |
| 추천 요청/결과 | 엔티티·저장 API 미구현. `analysis_level` 허용 값·`status` ENUM 값·초기 상태·`completed_at` 설정 시점은 사진에 없음 |
| 사용량 | 엔티티·API 미구현. 추가 이미지의 `usage_month`는 CHAR(7)로 확인되어 이전 VARCHAR(7) 판독을 정정함. 1단계 H2 fixture에는 사용량 테이블을 만들지 않으며 실제 DB와의 일치는 해당 단계에서 확인 |

사진만으로 NULL 허용, 기본값, ID 자동 생성, UNIQUE/CHECK/인덱스, ON DELETE 정책은 확인할 수 없다. 아래의 추가 사용자 정리로 확정 정책과 권장 제약을 보완했으며 **DB에 적용되었는지는 별도 확인**해야 한다. 추천 요청 `conditions`는 요청 시점의 변경 불가 스냅샷으로 확정하고, `analysis_level`의 허용 값·구체적 용도는 미확정으로 유지한다.

### 5개 테이블 설계 정책·권장 제약 (2026-11-04 추가 정리)

사용자가 제공한 컬럼·제약·관계 정리를 반영했다. **확정 정책은 구현이 지켜야 할 동작**, **권장은 구현 시 검토할 DB 제약**, **미확정은 추가 결정 사항**이다. 어느 항목도 실제 DDL 적용·검증 완료를 뜻하지 않는다. ERD의 표시 내용과 현재 구현 상태는 앞 절에 별도로 유지한다.

시간 컬럼의 설계 표기는 사용자 요청에 따라 모두 **DATETIME**으로 통일한다. Java의 LocalDateTime 및 현재 API 시각 형식은 유지한다. 이는 설계 문서 표기이며 PostgreSQL에 `DATETIME`이라는 SQL 타입을 그대로 적용한다는 의미가 아니다. 실제 DB별 타입·DDL은 구현 단계에서 확인하고 이번에는 기존 SQL을 변경하지 않는다.

#### ai_conversations — 대화

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `user_id` | BIGINT | users 참조, 로그인 사용자 서버 대화는 NOT NULL |
| `title` | VARCHAR(255) | 최종 NULL 허용 정책 미확정. 현재 1단계 API는 NULL 허용 |
| `current_conditions` | JSONB | NOT NULL·기본값 `'{}'::jsonb` 권장. 현재 구현의 NULL 허용과 구분 |
| `created_at` | DATETIME | NOT NULL 권장 |
| `updated_at` | DATETIME | NOT NULL 권장 |

확정: type/status를 사용하지 않고 한 대화에서 개발자·프로젝트를 함께 추천한다. 1단계 대화 삭제 시 해당 메시지만 함께 삭제하며 추천 요청·결과는 건드리지 않는다. 비로그인 입력은 프론트 임시 보관, 서버 저장은 로그인 이후에만 수행한다. 실제 저장 시점은 별도 계약이며 로그인 사용자도 추천 전 로컬 처리하는 흐름을 유지한다. 실제 인증 사용자 연결 전 임시 사용자는 개발 환경에서만 사용하고 운영에 적용하지 않는다. 현재 dev/test opt-in 설정은 개발·테스트 전용이다.

#### ai_messages — 메시지

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `conversation_id` | BIGINT | 대화 참조, NOT NULL |
| `role` | VARCHAR(20) | NOT NULL, USER/ASSISTANT |
| `content` | TEXT | NOT NULL |
| `created_at` | DATETIME | 추가 권장 항목. 제공 ERD·현재 엔티티·API에는 이미 존재 |

대화와 메시지는 1:N이며 대화 삭제 시 함께 삭제한다. `CHECK (role IN ('USER', 'ASSISTANT'))`는 권장 DB 제약이다. 현재 엔티티는 Java Enum을 사용하고 H2 fixture에는 CHECK가 있으나 실제 팀 DB 적용은 미확인이다. `call_logs`는 제거 권장 이력이 있으나 최종 삭제 여부는 미확정이다. 제공 이미지·현재 엔티티·참고 SQL에는 없어, 실제 DDL 확인 없이 존재·삭제 완료를 단정하거나 새로 추가하지 않는다.

#### ai_recommendation_requests — 추천 실행 이력

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `ai_conversation_id` | BIGINT | 대화 참조, NOT NULL |
| `ai_message_id` | BIGINT | 메시지 참조, NULL 허용 |
| `conditions` | JSONB | NOT NULL, 요청 당시 조건의 변경 불가 스냅샷 |
| `analysis_level` | VARCHAR(20) | 허용 값 미확정 |
| `status` | VARCHAR(20) 또는 ENUM | 물리 타입·허용 값 미확정. 기존 이미지에는 ENUM 표시 |
| `created_at` | DATETIME | NOT NULL 권장 |
| `completed_at` | DATETIME | NULL 허용. 완료/실패 시 기록 시점은 별도 결정 |

확정: 한 대화에서 여러 요청을 만들 수 있고 로그인 사용자만 실행한다. 조건 버튼 실행 시 연결 메시지가 없을 수 있다. 메시지를 연결하면 동일 대화 소속인지 검증한다. 실패 요청은 미차감하며 동일 요청의 중복 실행·차감을 막는다. 스냅샷은 대화의 current_conditions를 수정해도 변경하지 않는다.

`PENDING`·`COMPLETED`·`FAILED`는 **검토 예시**이며 확정 Enum이 아니다. 이전 첨부의 SUCCESS와도 임의로 통합하지 않는다. 별도 멱등성 키·UNIQUE의 키 범위·보존 기간은 4단계부터 설계하며 지금 컬럼이나 API 필드를 확정하지 않는다.

#### ai_recommendations — 추천 결과

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `recommended_request_id` | BIGINT | 추천 요청 참조, NOT NULL |
| `recommended_user_id` | BIGINT | 사용자 참조, NULL 허용. 프로젝트 FK와 정확히 하나만 값 존재 |
| `recommended_project_id` | BIGINT | 프로젝트 참조, NULL 허용. 사용자 FK와 정확히 하나만 값 존재 |
| `match_score` | DECIMAL(5,2) | 백엔드 계산, 0~100 범위 |
| `basic_reason` | TEXT | 기본 사유. NULL 허용·길이 제한 세부 정책은 미확정 |
| `detailed_reason` | TEXT | NULL 허용, 프리미엄 상세 분석 시 생성·저장, 무료 응답에서 제외 |
| `rank` | INT | 양수 제약 권장. 개발자/프로젝트 각각 순위인지 미확정 |
| `created_at` | DATETIME | NOT NULL 권장 |

`recommendation_type`은 사용하지 않는다. 사용자·프로젝트의 참조 PK는 기존 엔티티/DDL로 확인한다. 현재 users.id는 Member에 있으나 프로젝트 PK·삭제 정책은 미확인이다. 다음은 확정된 두 대상 중 하나·점수 범위 정책을 보장하기 위한 **권장 CHECK 표현**이며 적용된 DDL이 아니다.

```sql
CHECK (
    (recommended_user_id IS NOT NULL AND recommended_project_id IS NULL)
    OR
    (recommended_user_id IS NULL AND recommended_project_id IS NOT NULL)
),
CHECK (match_score BETWEEN 0 AND 100)
```

점수 등 별도로 NOT NULL을 명시하지 않은 필드의 NULL 허용은 실제 계약에서 정한다. 위 범위 CHECK만으로 NULL을 차단한다고 간주하지 않는다.

#### ai_usages — 월별 사용량

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `user_id` | BIGINT | users 참조, NOT NULL |
| `usage_month` | CHAR(7) | NOT NULL, YYYY-MM |
| `used_count` | INT | NOT NULL, 기본값 0 |
| `created_at` | DATETIME | NOT NULL 권장 |
| `updated_at` | DATETIME | NOT NULL 권장 |

무료 월 5회·프리미엄 월 100회, 한국 시간의 월 집계, 성공한 신규 추천만 1회 차감한다. 대화·메시지·조건 변경·추천 결과 재조회·상세 분석/재조회는 미차감이다. 대화 삭제 후에도 사용량을 유지하고 동시 요청·재시도에 의한 중복 차감을 방지한다. 사용자·월별 유일성 및 음수 금지의 DB 구현 제약은 아래와 같이 정리하되 **실제 적용 여부는 미확인**이다.

```sql
UNIQUE (user_id, usage_month),
CHECK (used_count >= 0)
```

#### 관계·삭제 정책

| 부모 → 자식 | 관계 | 확정 정책 / 권장·미확정 |
|---|---|---|
| users → ai_conversations | 1:N | 사용자 탈퇴 시 보존/삭제 정책 미확정 |
| ai_conversations → ai_messages | 1:N | 대화 삭제 시 함께 삭제(CASCADE 정책) |
| ai_conversations → ai_recommendation_requests | 1:N | 1단계 삭제 대상에서 제외. 추천 기능 도입 시 FK·삭제 정책 별도 확정 |
| ai_messages → ai_recommendation_requests | 1:N, 연결 선택 | 개별 메시지 삭제 시 SET NULL 권장, 최종 FK 설정 미확정 |
| ai_recommendation_requests → ai_recommendations | 1:N | 후속 추천 단계의 설계 관계. 1단계 삭제 대상에서 제외 |
| users → ai_usages | 1:N | 사용자 탈퇴 시 보존/삭제 정책 미확정 |
| 추천 대상 users/projects → ai_recommendations | 대상별 1:N | 추천 대상 삭제 시 FK 처리 미확정 |

**메시지 동반 삭제와 DB의 ON DELETE CASCADE 적용 여부를 구분한다.** 현재 서비스는 메시지 → 대화 순서로 직접 삭제하고 트랜잭션으로 원자성을 보장한다. DB CASCADE는 확인·적용하지 않았으며 JPA cascade나 실제 DDL을 변경하지 않았다. 사용자 최신 요청으로 이전 추천 요청·결과 동반 삭제 정책을 1단계에서 제외했다. 메시지 SET NULL은 후속 설계 권장일 뿐 현재 적용하지 않으며 개별 메시지 삭제 API는 없다. 미래에 추천 FK가 생기면 보존/삭제와 FK 처리를 함께 확정해야 한다. 월 사용량은 대화 삭제 경로와 분리한다.

### users 최신 DDL 방향과 AI 데이터 계약 (2026-11-04)

이번 사용자 제공 users DDL을 최신 목표 구조로 삼는다. 이전 ERD 설명보다 우선하며 실제 DB 적용·Member 수정 완료를 뜻하지 않는다. Member는 users를 매핑하는 Java 엔티티다. 이번에는 문서만 변경한다.

| 컬럼 | 최신 제공 DDL | 현재 코드·남은 차이 |
|---|---|---|
| id | BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY | Member IDENTITY와 일치 |
| email, password_hash | VARCHAR(255) | 필드 존재 |
| kakao_id | VARCHAR(100) | 필드 존재. 외부 연결 승인이 아님 |
| nickname, tag | VARCHAR(50), VARCHAR(100) | 필드 존재 |
| role | VARCHAR(20), CHECK: USER/PREMIUM/OWNER/ADMIN | Java Enum 값 일치 |
| status | VARCHAR(20), CHECK: ACTIVE/WARNED/SUSPENDED | Java Enum 값 일치 |
| external_links, payment_methods | JSONB | 현재 JSON 문자열 매핑 |
| trust_score | NUMERIC(5,2) | BigDecimal precision=5, scale=2 일치 |
| collaboration_status | VARCHAR(255), CHECK: AVAILABLE/RESTING/BUSY/OPEN_TO_OFFERS/UNAVAILABLE | Java Enum 값 일치. 실제 길이·제약은 미검증 |
| profile_image, banner_image | VARCHAR(255) | 필드 존재 |
| summary | TEXT | 필드 존재 |
| career | VARCHAR(30) | **현재 Integer(개월 수)와 불일치**. 문자열 형식·경력 비교 변환 규칙 미확정 |
| contact_hours | VARCHAR(100) | **현재 Member에 없음** |
| job_categories | JSONB | **현재 job_category_id Long과 불일치**. 최신 DDL에는 job_category_id 없음 |
| skills, interests | JSONB | **현재 Member에 없음** |

- UNIQUE: `uk_users_email(email)`, `uk_users_nickname(nickname)`, `uk_users_tag(tag)` 방향을 따른다. 실제 DB 적용은 미검증이다.
- 제공 DDL에서 PK 외 컬럼에 NOT NULL·기본값을 지정하지 않았다. CHECK·UNIQUE가 NOT NULL을 대신하지 않는다.
- 최신 DDL에는 introduction·introduction_format이 없으므로 이전 ERD만으로 필수 추가 대상으로 취급하지 않는다.
- `CREATE TABLE IF NOT EXISTS`는 기존 users의 컬럼·제약을 변경하지 않는다. 실행만으로 기존 DB 구조가 전환된다고 간주하지 않는다.
- JSONB 내부 형식·허용 코드·공개/열람 범위와 실제 DB 적용 상태는 별도 확인한다. 다른 팀원 담당 Member·공통 인증·사용자 SQL은 이번에 수정하지 않는다.
- 비밀번호 해시·결제 수단·카카오 식별자 등은 추천 후보 정보나 AI 입력에 사용하지 않는다.

#### 최신 GitHub develop 대조

2026-11-04 원격 develop `10c3fbc7b5f4293ec9f22191e25b35cc0de3d076`을 읽기 전용 조회했다. 위 표의 현재 코드 열은 feat/ai-conditions 기준이다. 원격 Member는 career String(길이 30), contactHours, jobCategories·skills·interests List<String>을 이미 제공하며 introduction·introductionFormat도 추가되어 있다. 원격 entity/enums의 JobCategory·Skill·Interest는 label 문자열을 DB에 저장하고 검증한다. 따라서 로컬의 누락 필드를 원격에서도 누락으로 취급하지 않는다. 실제 DB 적용은 별도 미검증이다.

AI 외 코드는 공통 controller·service·repository·entity와 도메인별 dto/auth·dto/member·dto/profile을 사용한다. 확인한 AuthService·MemberService·ProfileService는 service의 구체 클래스이며 service/impl에는 AI 구현체만 있다. AI의 기존 인터페이스/Impl 구조는 유지한다. 원격 변경을 이번에 병합하지 않았다.

### 2단계 빈 조건 처리 방향 (2026-11-04)

사용자가 단순한 구현과 후속 추천 처리를 위해 승인한 방향이다. **구현 예정이며 현재 1단계의 NULL 허용·조회 전용 동작과 구분한다.**

- 새 대화의 초기 조건은 `{}`로 설정한다.
- 조건 PUT은 조건 객체 전체를 교체하며 `{}`로 전체 초기화를 허용한다.
- 기존 NULL 조건은 조회 응답에서 `{}`로 반환한다.
- 부분 조건 저장을 허용하고 추천에 필요한 필수 조건 검사는 추천 실행 단계에서 수행한다. 저장 값의 타입·허용 코드 검증은 2단계에서 정의한다.
- DB NOT NULL·기본값·기존 데이터 변경은 실제 DDL 확인 후 별도 판단한다. 요청 null·항목별 빈 값·상한·JSON 키 등은 단계별 구현 프롬프트에서 정한다.

### 2단계 확정 요구사항과 임시 기준 (2026-11-04)

사용자가 팀 계약 확인 전에 필요한 기준을 임시로 정하고, 확정 후 수정하는 방식을 허용했다. **확정 요구사항은 이번 단계가 지킬 동작이며, 임시 기준은 팀 합의가 아닌 구현 출발점이다.** 이번 요청은 문서 기록만이며 조건 API·DTO·검증은 아직 미구현이다. 아래 구분을 이전의 포괄적인 미확정 안내보다 우선한다.

#### 확정 요구사항

| 항목 | 확정 내용 |
|---|---|
| 대상·API | ai_conversations.current_conditions JSONB, PUT `/api/v1/ai/conversations/{id}/conditions` |
| 교체 방식 | 프론트가 조건 객체 전체를 전송한다. 기존 조건과 병합하지 않으며 누락한 기존 항목은 제거한다 |
| 조건 종류 | 프로젝트 분야·관심 분야·기술 스택 복수 선택, 모집 직군별 인원, 요구 경력, 예상 프로젝트 기간 |
| 검증 | 조건 DTO·유효성 검증을 둔다. 모집 인원은 1명 이상의 정수만 허용한다 |
| 인증·소유권 | 요청자 신원은 기존 인증 정보에서 얻고 본인 대화만 수정한다. 요청 userId는 신원으로 사용하지 않는다 |
| 수정 시각 | 조건 갱신 시 updated_at을 갱신한다 |
| 빈 조건·선택 사항 | `{}` 전체 초기화 및 부분 조건 저장 허용. 새 대화 초기 조건과 기존 NULL 조회 응답은 `{}`로 처리하는 방향을 적용한다 |
| 필수 구분 | 저장 시 조건 항목은 선택 사항. 제출한 값의 유효성은 검증하고, 추천 실행의 필수 조건 충족 검사는 후속 추천 단계에서 수행한다 |
| 데이터 호환 | 기존 DB·공통 Enum과 호환되게 설계하며 Mock을 확정 서버 계약으로 간주하지 않는다 |
| 범위 | 조건 저장·교체만 구현한다. Gemini 분석·추천 실행·차감·프로젝트/회원 도메인 수정은 포함하지 않는다 |
| 필수 테스트 | 전체 저장, 일부 제거 후 전체 교체, 빈 조건, 잘못된 모집 인원·Enum, 타 사용자 수정 차단 |

#### 팀 확인 전 임시 구현 기준

| 항목 | 임시 기준 | 이후 확인·수정할 내용 |
|---|---|---|
| 프로젝트 분야 | 웹, 모바일, AI / 머신러닝, 데이터, 게임, 보안, 클라우드 / 인프라. 직접 입력은 우선 미허용 | 프로젝트 화면의 생산성·핀테크 등과 의미가 다름. 별도 분야인지·코드 목록·기타 입력 정책 확인 후 DTO 허용값·테스트 수정 |
| 관심·기술·직군 | 최신 develop의 Interest·Skill·JobCategory label 문자열 재사용 방향 | 현재 브랜치에 공통 Enum이 없으므로 develop 통합 상태 확인 후 참조. 별도 중복 Enum을 확정하지 않음. 프로젝트 JSON과 실제 저장값도 대조 |
| 모집 직군 | 직군 이름과 인원 객체 배열, 같은 직군 중복 거절. 직군 객체를 보내면 이름·인원 필수 | recruitment_slots의 실제 JSON 키·직군 값·직군별 최소 경력과 매핑 확인. 상한은 별도 결정 |
| 요구 경력 | 경력 무관, 1년 이상, 2년 이상, 3년 이상, 5년 이상 중 단일 문자열. 기본 목록을 Enum으로 검증하는 방향 | 사용자 career는 문자열이며 프로젝트 Mock은 minYears 정수. 실제 recruitment_slots의 경력 단위·비교 규칙 확인 후 변환 수정 |
| 프로젝트 기간 | 1~36개월 정수 | 실제 프로젝트 시작·종료 예정일과 비교 방식·일부 날짜 누락·허용 범위 확인 후 검증/변환 수정 |

단일 요구 경력은 List<String>으로 중복 범위를 선택하지 않는다. 분야·관심·기술은 문자열 배열, 직군은 객체 배열로 전체 조건 객체 안에 저장하는 방향이다. 조건 객체 자체를 JSONB로 저장하므로 경력용 별도 컬럼/테이블은 만들지 않는다. 기본 목록·사용자 선택값 분리 원칙을 사용하되 자유 입력 허용은 자동으로 승인된 것으로 해석하지 않는다.

#### 아직 결정하지 않은 세부 계약·확인 자료

- API JSON 키, 응답 DTO·성공 상태, null·빈 문자열·빈 배열 처리, 알 수 없는 키, 중복 배열값 정규화, 길이·개수 상한은 구현 시 최소 계약으로 정하고 문서·테스트를 함께 갱신한다.
- 추천 실행에 필요한 필수 항목 목록은 후속 단계에서 확정한다. 기존 Mock이 모든 항목을 요구한다는 이유로 이번 저장 API를 차단하지 않는다.
- 프로젝트 ERD에서 recruitment_slots·tech_stacks·interests JSONB, development_start·development_end DATE를 확인했다. 별도 프로젝트 분야 컬럼은 보이지 않는다. 실제 JSON 예시·코드·경력 단위·날짜 비교 계약과 적용 DDL은 미확인이다.
- ai_conversations의 실제 JSONB 타입·FK·NULL·기본값은 적용 DDL로 확인해야 한다. DB NOT NULL·기본값 변경, 기존 데이터 일괄 갱신은 이번 문서 승인에 포함하지 않는다.
- 임시 계약 확정 후 수정 시 조건 DTO·허용값/검증·테스트·API 문서를 함께 갱신한다. 이미 저장된 조건과 프론트 입력이 있다면 호환·변환 필요 여부도 확인한다. 실제 후보 비교·점수 계산은 3단계에서 다룬다.

### 추가 API 계약의 필요 범위

ERD는 저장 구조이고 API 명세는 클라이언트·서버 간 요청/응답·인증·오류 계약이다. 제공받은 API 표의 경로·기능은 위에 반영했으며 현재 문서에 1단계 API 5개의 구체 명세가 있다. 같은 목록을 다시 작성할 필요는 없다. 팀에 상세 DTO·오류·인증 계약이 있다면 대조 기준으로 활용한다.

로컬 초안 인계와 H2 개발 계정으로 실제 브라우저 로그인 성공/실패/취소·로그아웃·계정 전환 검증을 완료했다. AI API BFF 인증 전달 계약과 실제 PostgreSQL 통합 검증은 남아 있다. 서버 최초 전송은 추천 실행 시점의 4단계 후속 계약으로 유보했으며 중복/부분 실패 처리도 그때 확정한다. 조건 DTO·Enum·추천 응답·상세 분석·사용량의 세부 API 계약은 2~7단계 중 해당 단계 요청 시 정한다. ERD의 컬럼명을 API JSON 키로 그대로 확정하거나 API 미구현 항목을 완료로 표시하지 않는다.

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

### 메인 입력의 대화 전환 (2026-11-04)

- 메인의 작성 중 입력은 새로고침 시 복원한다. 추천 시작 시 새 로컬 대화와 메인 설명 초기화를 한 번에 저장하며, 저장 성공 후에만 대화 화면으로 이동한다. 실패하면 입력·기존 대화를 유지하고 재시도한다.
- 제출 중 버튼 잠금과 최신 메인 입력 확인으로 연속 제출을 막는다. 같은 문장을 새로 작성해 별도 대화를 만드는 것은 허용한다. 기존 중복 기록은 자동 삭제/병합하지 않으며 목록에서 직접 삭제한다.
- 이전 추천 대화 열기와 로그인 후 확인을 통한 guest 초안 인계 정책은 유지한다. 서버 API·추천 실행·사용량 정책 변경은 없다.

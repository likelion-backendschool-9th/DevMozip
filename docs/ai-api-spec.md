# AI API 명세

기준일: 2026-11-04. **서버 대화·메시지 API 5개 구현, 프론트 실제 연결·실사용자 인증 연결은 미완료**다. 전체 단계·통합 과제·필요 자료는 [개발 청사진](ai-progress.md), 공통 정책은 [기능 정책](ai-matching.md)를 따른다.

## 공통 계약

- 서버 기본 경로 `/api/v1/ai`, 화면 `/ai/matching`.
- 아래 서버 영속 API는 인증을 요구한다. 비로그인 대화·메시지·조건은 프론트 로컬에서 처리하며 익명 서버 저장 API를 제공하지 않는다.
- 사용자 ID는 인증 정보에서 얻고 모든 대화 접근에 소유권을 검증한다. 현재 실사용자 연결의 미완료 사항은 아래 인증 절을 따른다.
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

AI 내부의 참조 컬럼은 메시지 → 대화(`conversation_id`), 추천 요청 → 대화/메시지(`ai_conversation_id`/`ai_message_id`), 추천 결과 → 요청(`recommended_request_id`)이다. `recommended_request_id`의 철자는 현재 삭제 코드와도 일치한다. 추가 이미지에서 대화·추천 결과·사용량의 사용자 참조와 users.id를 확인했다. 프로젝트 테이블의 정의·실제 FK 제약은 별도 ERD/DDL로 확인한다.

| 대조 항목 | 확인 결과·남은 확인 |
|---|---|
| 대화·메시지 컬럼 | 현재 엔티티의 컬럼명과 일치. 대화 type/status, 메시지 updated_at은 ERD에도 없음 |
| 조건 저장 타입 | ERD는 `current_conditions`·`conditions` 모두 JSONB. 현재 대화 엔티티는 `@JdbcTypeCode(SqlTypes.JSON)`이며 PostgreSQL 실제 컬럼 타입을 조회한 것은 아님. 이 애너테이션 이름만으로 json 컬럼 사용이나 불일치를 단정하지 않음 |
| 시각 | ERD의 DATETIME은 표시 타입. 현재 Java는 LocalDateTime, PostgreSQL 초안은 TIMESTAMP WITHOUT TIME ZONE. PostgreSQL 적용 DDL·시간대 확인 필요 |
| 삭제 쿼리 | 위 세 참조 컬럼명이 현재 네이티브 쿼리·H2 fixture와 일치. 실제 DB의 FK/삭제 제약·추가 참조는 미검증 |
| 추천 요청/결과 | 엔티티·저장 API 미구현. `analysis_level` 허용 값·`status` ENUM 값·초기 상태·`completed_at` 설정 시점은 사진에 없음 |
| 사용량 | 엔티티·API 미구현. 추가 이미지의 `usage_month`는 CHAR(7)로 확인되어 이전 VARCHAR(7) 판독을 정정함. H2 fixture의 CHAR(7)과 일치하지만 생성/수정 시각은 fixture에 없어 전체 ERD와는 다름 |

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

확정: type/status를 사용하지 않고 한 대화에서 개발자·프로젝트를 함께 추천한다. 대화 삭제 시 메시지·추천 요청·결과도 삭제한다. 비로그인 입력은 프론트 임시 보관, 서버 저장은 로그인 이후에만 수행한다. 실제 저장 시점은 별도 계약이며 로그인 사용자도 추천 전 로컬 처리하는 흐름을 유지한다. 실제 인증 사용자 연결 전 임시 사용자는 개발 환경에서만 사용하고 운영에 적용하지 않는다. 현재 dev/test opt-in 설정은 개발·테스트 전용이다.

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
| ai_conversations → ai_recommendation_requests | 1:N | 대화 삭제 시 함께 삭제(CASCADE 정책) |
| ai_messages → ai_recommendation_requests | 1:N, 연결 선택 | 개별 메시지 삭제 시 SET NULL 권장, 최종 FK 설정 미확정 |
| ai_recommendation_requests → ai_recommendations | 1:N | 요청 삭제 시 함께 삭제(CASCADE 정책) |
| users → ai_usages | 1:N | 사용자 탈퇴 시 보존/삭제 정책 미확정 |
| 추천 대상 users/projects → ai_recommendations | 대상별 1:N | 추천 대상 삭제 시 FK 처리 미확정 |

**연관 삭제 정책과 DB의 ON DELETE CASCADE 적용 여부를 구분한다.** 현재 대화 삭제는 서비스가 결과 → 요청 → 메시지 → 대화 순서로 직접 삭제하고 트랜잭션으로 원자성을 보장한다. DB CASCADE는 확인·적용하지 않았으며 이번 문서로 JPA cascade나 DDL 변경을 지시하지 않는다. 메시지 SET NULL 권장은 개별 메시지 삭제에 대한 것이며, 대화 전체 삭제 시 추천 요청·결과까지 삭제하는 정책을 바꾸지 않는다. 현재 개별 메시지 삭제 API는 없다. 월 사용량은 대화 삭제 경로와 분리한다.

### 추가 users ERD와 AI 데이터 계약

users는 다른 팀원 담당이므로 아래는 AI 추천에 필요한 데이터 계약의 차이만 기록한다. 이 문서 작업에서 Member·인증·사용자 DDL은 수정하지 않는다.

| AI 관련 항목 | 추가 ERD | 현재 `Member` 및 확인 사항 |
|---|---|---|
| 사용자 식별 | `id` BIGINT, `email` VARCHAR(255) | 필드 존재. JWT 이메일 → users PK의 AI Provider 연결은 여전히 미완료 |
| 역할·상태 | `role`·`status` VARCHAR(20), `collaboration_status` ENUM | Java Enum 존재. ERD의 허용 값은 보이지 않으므로 값 계약 확인 필요 |
| 신뢰 온도 | `trust_score` DECIMAL(5,2) | BigDecimal precision=5, scale=2로 매핑. 추천 반영 방식은 3단계에서 결정 |
| 경력(개월 수) | `career` VARCHAR(20) | **현재 Integer와 타입 불일치**. 저장 형식·변환 규칙·최신 팀 계약 확인 필요 |
| 개발 직군 | `job_categories` JSONB | **현재 `job_category_id` Long과 구조 불일치**. 복수 직군 JSON 구조·코드 기준·실제 DB 확인 필요 |
| 기술·관심 | `skills`·`interests` JSONB | **현재 Member에 필드 없음**. 추천 입력 원천·JSON 구조·실제 DB/담당자 계약 필요 |
| 소개·연락 | `summary` TEXT, `introduction` TEXT, `introduction_format` Type 표시, `contact_hours` VARCHAR(100) | summary만 현재 매핑. 나머지 필드 없음. introduction_format의 구체 타입·열람 범위는 미확정 |

추천에서 비밀번호 해시·결제 수단·카카오 식별자 등을 후보 정보로 사용하거나 AI에 전달하지 않는다. 공개 필드의 실제 공개/열람 정책은 ERD만으로 확정하지 않는다. 부족한 필드나 불일치를 해결하기 위해 다른 팀원 엔티티를 임의로 추가·수정하지 않는다.

### 추가 API 계약의 필요 범위

ERD는 저장 구조이고 API 명세는 클라이언트·서버 간 요청/응답·인증·오류 계약이다. 제공받은 API 표의 경로·기능은 위에 반영했으며 현재 문서에 1단계 API 5개의 구체 명세가 있다. 같은 목록을 다시 작성할 필요는 없다. 팀에 상세 DTO·오류·인증 계약이 있다면 대조 기준으로 활용한다.

1단계 마무리에는 로그인 후 초안 인계·서버 최초 저장 시점·중복/부분 실패 처리와 AI API의 BFF 인증 전달 계약이 추가로 필요하다. 조건 DTO·Enum·추천 응답·상세 분석·사용량의 세부 API 계약은 2~7단계 중 해당 단계 요청 시 정한다. ERD의 컬럼명을 API JSON 키로 그대로 확정하거나 API 미구현 항목을 완료로 표시하지 않는다.

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

# AI API 명세

기준일: 2026-11-08. **서버 대화·메시지 API 5개와 2단계 조건 PUT, 3단계 내부 추천 엔진과 4단계 백엔드 실행·저장·조회 구현 완료. 서버의 실사용자 인증·소유권 검증은 유지하며 비로그인 화면 입력/로그인 인계는 아래 최신 UI 계약을 따른다. 프론트 AI API 연결을 2026-11-07 승인 범위에서 구현했고, 공유 개발 DB 적용 성공·실제 연동 검토 완료를 사용자가 확인했다. 이후 UI/UX 재구성 코드를 구현했으며 새 화면의 사용자 최종 검토는 별도다.** 이전 브라우저 검증은 guest 허용 당시 기록이며 현재 검증은 진행 문서 상단의 현재 AI 상태를 따른다. 전체 단계·통합 과제·필요 자료는 [개발 청사진](ai-progress.md), 공통 정책은 [기능 정책](ai-matching.md)를 따른다.

구현 커밋 `0f7dc87`은 `origin/feat/ai-conditions`에 푸시 완료했다. 무료 Mock 한도 변경은 별도 커밋 `fcd0b4c`다. 현재 상태·최근 검증과 남은 작업은 [진행 현황의 최신 요약](ai-progress.md)을 따른다.

## 공통 계약

- 서버 기본 경로 `/api/v1/ai`, 화면 `/ai/matching`.
- 아래 서버 영속 API는 인증을 요구한다. 2026-11-07 UI 요청으로 프론트 환영/입력 화면은 비로그인도 접근하지만, 비로그인 서버 대화·메시지·조건 저장 API는 제공하지 않는다.
- 사용자 ID는 인증 정보에서 얻고 모든 대화 접근에 소유권을 검증한다. 실사용자 식별 방식과 남은 BFF 계약은 아래 인증 절을 따른다.
- 현재 응답은 DTO 객체/배열이며 별도 `data` envelope를 쓰지 않는다. 입력 JSON 필드는 아래 단계별 구현 계약에 명시된 항목이다.
- 서버 AI 시각은 오프셋 없는 LocalDateTime ISO-8601(예: `2026-11-04T15:00:00`). 앱/DB 기준 시간대는 미확정이다.

## 구현된 API 목록

모든 API는 로그인이 필요하며, 기존 대화에 접근할 때 본인 소유권을 확인한다. 직접 백엔드를 호출할 때는 `Authorization: Bearer <access-token>`을 사용한다. 브라우저용 AI BFF는 `/api/ai/*`이며 기존 HttpOnly 인증 쿠키를 서버에서 Bearer로 전달한다.

| 단계 | Method·URL | 기능 | 기본 성공 응답 |
|---|---|---|---|
| 1 | POST `/api/v1/ai/conversations` | 대화 생성 | 201, ConversationResponse |
| 1 | GET `/api/v1/ai/conversations` | 내 대화 목록 | 200, ConversationResponse 배열 |
| 1 | GET `/api/v1/ai/conversations/{id}` | 대화·조건·메시지 조회 | 200, ConversationDetailResponse |
| 1 | DELETE `/api/v1/ai/conversations/{id}` | 대화·추천 이력·메시지 삭제 | 204, 본문 없음 |
| 1 | POST `/api/v1/ai/conversations/{id}/messages` | USER 메시지 저장 | 201, MessageResponse |
| 2 | PUT `/api/v1/ai/conversations/{id}/conditions` | 추천 조건 전체 교체 | 200, ConversationConditionsResponse |
| 연동 | POST `/api/v1/ai/conversations/sync` | 추천 전 초안 동기화 | 200, ConversationDetailResponse |
| 연동 | GET `/api/v1/ai/conversations/{id}/recommendations/history` | 요청 요약 목록 | 200, RecommendationRequestSummary 배열 |
| 3 | 외부 API 없음 | 내부 후보 조회·점수 계산 엔진 | 4단계 POST 내부에서 사용 |
| 4 | POST `/api/v1/ai/conversations/{id}/recommendations` | 추천 실행·요청/결과 저장 | 신규 성공 201, RecommendationResponse |
| 4 | GET `/api/v1/ai/conversations/{id}/recommendations` | 최신 또는 특정 과거 요청 결과 조회 | 200, RecommendationResponse |

아래 단계별 표에 요청·응답·오류를 정리한다. 5~7단계의 미구현 API는 뒤의 단계별 개요와 구분한다.

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
| POST `/api/v1/ai/conversations/{id}/messages` | `{"content":"백엔드 개발자를 찾고 있어요"}`. content 필수·공백만 입력 불가·최대 1500자, 누적 입력 10000자 제한 | 201, 저장한 USER 메시지. AI 응답 없음 |

### 응답 필드

| 응답 | 필드 |
|---|---|
| 대화 요약 `ConversationResponse` | `id`: 정수, `title`: 문자열/null, `createdAt`, `updatedAt`: 시각 |
| 대화 상세 `ConversationDetailResponse` | 요약 필드 + `currentConditions`: JSON 값, DB NULL은 `{}`로 반환, `messages`: 메시지 배열 |
| 메시지 `MessageResponse` | `id`: 정수, `role`: USER/ASSISTANT, `content`: 문자열, `createdAt`: 시각 |

`currentConditions`는 엔티티 내부에서 JSON 문자열로 보관하고 상세 응답에서 JSON 값으로 직렬화한다. 2단계부터 새 대화의 초기 조건은 `{}`이고 기존 DB NULL도 조회 시 `{}`로 반환한다. 생성/메시지 요청으로 사용자 조건을 받지는 않으며 조건 PUT으로 교체한다. 저장 API는 role을 USER로 정하고 경로의 본인 대화에 연결한다. 사용자 ID·메시지 ID·생성 시각은 서버가 정하며 요청의 임의 식별자·역할로 바꿀 수 없다. 서버 메시지 길이 상한은 현재 구현에 별도 정의가 없고 프론트 Mock의 1,500자를 서버 계약으로 간주하지 않는다.

### 오류·삭제

| 상태 | 현재 처리 |
|---|---|
| 400 | 잘못된 JSON/ID 형식, 0 이하 ID, 255자 초과 제목, null/빈/공백 메시지 |
| 401 | 비로그인·미인증, 인증 이메일에 해당하는 users 행 부재, 개발 ID 설정 누락·해당 users 행 부재 |
| 404 | 미존재 대화 또는 다른 사용자 소유 대화. 응답을 동일하게 처리 |
| 500 | 공통 DB 접근 오류·예상하지 못한 FK 충돌. 삭제 트랜잭션 전체 롤백 |

오류는 기존 `ErrorResponse`의 `status`, `error`, `message`, `path`, `timestamp`를 재사용한다. 정지 계정 등 기존 인증 체계의 별도 거절 정책은 그대로 따른다.

4단계부터 삭제는 **추천 결과 → 추천 요청 → 메시지 → 본인 대화** 순서로 한 트랜잭션에서 처리한다. 다른 대화 이력은 유지하고 FK 충돌은 전체 롤백한다. 월 사용량 테이블은 조회·삭제·복원하지 않는다. 신규 두 테이블이 필요한 삭제 경로이므로 배포 전 [4단계 SQL](sql/ai-recommendations.sql)을 적용한다.

## 2단계 구현 API — 조건 전체 교체 (2026-11-05)

| Method·URL | 요청 | 성공 응답 |
|---|---|---|
| PUT `/api/v1/ai/conversations/{id}/conditions` | 양수 대화 ID, 아래 조건 JSON. interests·career·durationMonths 필수 | 200, ConversationConditionsResponse |

인증 필수, 본인 대화만 수정한다. 2026-11-05 최신 사용자 요청으로 프로젝트 분야를 `interests`로 통합했다. 별도 `fields` 입력은 제거하고 직군은 화면의 선택·직접 입력을 모두 허용한다.

PR 리뷰에 따라 요구 경력 enum은 `entity/enums/RequiredCareer`, 직군·인원 요청 원소는 `dto/ai/RecruitmentRoleConditionDto`, 정수/문자열 JSON 역직렬화 클래스는 `utils/StrictIntegerDeserializer`·`StrictStringDeserializer`에 둔다. 패키지·타입 이름만 정리하며 아래 JSON 키·선택지·검증·응답은 유지한다. 역직렬화 처리는 AI 요청 애너테이션에서만 적용하고 전역 Jackson 설정은 바꾸지 않는다.

```json
{
  "interests": ["생산성", "웹", "AI / 머신러닝"],
  "stacks": ["Java", "Spring Boot"],
  "roles": [{"name": "백엔드", "count": 2}, {"name": "보안 개발자", "count": 1}],
  "career": "3년 이상",
  "durationMonths": 3
}
```

| 요청 필드 | 타입·검증 |
|---|---|
| interests | **필수** 문자열 배열. 공통 Interest label 5개(생산성·핀테크·교육·커뮤니티·AI)와 기존 프로젝트 분야 7개(웹·모바일·AI / 머신러닝·데이터·게임·보안·클라우드 / 인프라)를 통합한 12개 선택지. 최소 1개·제출 최대 12개 |
| stacks | **선택** 문자열 배열. 기존 최대 30개·항목당 1~50자, 알려진 Skill label 표기 정규화·목록 밖 기술 입력 허용을 유지한다. 프론트도 기존 선택 버튼과 직접 입력/추가·삭제를 함께 제공한다. Enter 추가(한글 조합 중 제외), 빈 값/중복·30개/50자 제한, 알려진 기술 표기 정규화를 적용하고 로컬 재복원을 지원 |
| roles | **선택** 객체 배열. 화면처럼 선택/직접 입력 허용, 최대 12개, name은 앞뒤 공백 제거 후 1~30자 문자열. 같은 직군 이름 중복 거절. 공통 JobCategory·ProjectJobRole에 자동 등록하지 않음 |
| roles[].count | 해당 직군을 보내면 필수. 화면과 동일한 1~20의 JSON 정수. 0·음수·소수·숫자 문자열·boolean·null 거절 |
| career | **필수** 단일 문자열. 경력 무관, 1년 이상, 2년 이상, 3년 이상, 5년 이상 |
| durationMonths | **필수** 1~36의 JSON 정수. 소수·숫자 문자열 자동 변환 차단. 화면 선택지는 기존 1·2·3·6·12개월을 유지하고 과거 저장된 유효한 기간도 표시 |

- 매 PUT에 `interests`·`career`·`durationMonths`를 보낸다. 누락/null, 빈 관심 분야 배열 및 `{}` 요청은 400이다. 설명·기술·직군은 필수가 아니다. `description`은 로컬 입력이며 이 API에는 보내지 않는다.
- PUT은 전체 교체다. stacks·roles의 누락/null/빈 배열은 미선택으로 처리해 저장 JSON에서 제외하며 빠진 기존 선택 조건은 제거한다. 세 필수값만 보내 선택 조건을 초기화할 수 있다.
- 문자열 앞뒤 공백을 제거한다. interests·stacks는 같은 문자열 중복을 첫 순서대로 제거한다. 알려진 기술은 대소문자를 Skill label로 통일하고 새로운 기술명은 입력 표기를 유지한다. 직군은 앞뒤 공백 제거 후 같은 이름 중복을 거절한다. 개수 상한은 중복 제거 전 제출 개수다.
- 관심 분야·기술 원소와 직군 이름은 문자열만 받으며 숫자/boolean 자동 변환을 차단한다. 배열 내 null·공백 문자열·잘못된 선택값은 400이다. 최상위와 직군 객체의 알 수 없는 키도 400이다. 제거한 `fields`, userId·description·duration은 받지 않는다.
- 조건 갱신은 기존 본인 조회·비관적 잠금·트랜잭션을 재사용하고 마이크로초 단위 updated_at을 갱신한다. 같은 조건 재전송도 정상 PUT이다. 앱/DB 기준 시간대는 미확정이다.

### 응답 필드

| 응답 | 필드 | 설명 |
|---|---|---|
| ConversationConditionsResponse | id | 수정한 대화 ID, 정수 |
| ConversationConditionsResponse | currentConditions | 저장한 조건 JSON 객체. 전체 교체 후의 값 |
| ConversationConditionsResponse | updatedAt | 수정 시각, LocalDateTime |

성공은 **200**, `ConversationConditionsResponse`의 조건은 JSON 문자열이 아닌 객체다.

```json
{
  "id": 1,
  "currentConditions": {"interests": ["웹"], "roles": [{"name":"보안 개발자","count":1}], "career": "경력 무관", "durationMonths": 3},
  "updatedAt": "2026-11-05T10:00:00.123456"
}
```

### 오류

| 상태 | 현재 처리 |
|---|---|
| 400 | 잘못된 ID/JSON·필수값 누락·허용되지 않은 값/타입·개수/길이/인원/기간 범위 초과·중복 직군·알 수 없는 키 |
| 401 | 비로그인·미인증·삭제된 로그인 계정 |
| 403 | 정지 계정 |
| 404 | 미존재 또는 다른 사용자 소유 대화 |
| 500 | DB 접근 오류 등 서버 처리 실패 |

오류는 기존 ErrorResponse다. 검증 실패 시 기존 조건·수정 시각을 유지한다. 신규 대화 초기 `{}`와 과거 NULL 조회 정규화는 유지하며 기존 서버 저장 JSON은 조회 때 재검증하거나 일괄 변경하지 않는다. 과거 `fields`가 있는 서버 조건도 그대로 조회하고 다음 PUT부터는 새 `interests` 계약으로 전체 교체한다.

프론트 로컬 복원에서는 기존 fields의 7개 기본 선택값을 interests에 중복 없이 옮긴다. 옮긴 항목은 기존 fields에서 제거해 사용자가 통합 관심 분야에서 삭제한 값이 재등장하지 않게 한다. 과거 자유 입력 fields와 메시지/결과 스냅샷은 호환 데이터로 보존한다. 새 화면에는 별도 분야 입력·분야 직접 입력을 표시하지 않는다.

기존 `@JdbcTypeCode(SqlTypes.JSON)`과 엔티티·DB 설정은 유지한다. 실제 PostgreSQL 적용 컬럼/DDL·JSONB 실행 검증은 미완료다. 프론트는 계정별 로컬 초안을 사용하며 추천 실행 시에만 AI BFF의 sync로 동기화한다. 화면의 duration 문자열을 durationMonths 정수로 변환한다. 직군은 화면 문자열 그대로 허용하므로 공통 JobCategory label로 바꾸지 않는다. 조건 변경은 메시지·ASSISTANT·추천·사용량을 생성하지 않는다.

### 프로젝트 기간의 추천 비교 정책

추천 조건은 개월 수로 입력한다. 프로젝트 도메인은 기존 `development_start`·`development_end` 예정일을 유지하고 후속 추천 엔진이 두 날짜로 기간을 자동 계산해 비교한다. 프로젝트 엔티티·API·작성 화면에 개월 수 필드를 추가하거나 날짜 입력을 변경하지 않는다. **3단계 내부 엔진에서 시작일+조건 개월 수와 예정 종료일의 일수 차이를 계산하며 필터·점수에는 미반영**이다. 월말은 plusMonths 보정을 따르고 날짜 누락·역전·계산 불가는 비교 미확인으로 반환한다. 허용 오차 필터와 기간 점수는 추가하지 않는다.

## 3단계 내부 엔진 (2026-11-06, 외부 API 추가 없음)

협업은 AVAILABLE 후보 필터로만 적용하고 점수를 제거한다. 개발자·프로젝트 각각 최대 10건, 점수 내림차순·동점 ID 오름차순·소수 둘째 자리 표시다. 포트폴리오 연동 전 경험은 미산정으로 표시하고 사용 가능한 항목만 100점으로 환산한다. 메시지는 프로젝트 제목·설명의 단순 유사도에만 사용하며 직군·기술·경력은 직접 선택값으로 비교한다. 전체 정책과 남은 결정은 [3단계 정책](ai-matching.md#3단계-추천-엔진-정책-2026-11-06)을 따른다. 내부 진입점은 `AiRecommendationService.recommend(UpdateConditionsRequest, String message)`이며 요청자 ID는 기존 CurrentUserProvider에서 얻는다. 내부 결과는 개발자·프로젝트 목록, 0~100점·기본 이유·항목별 산출 근거, 직군별 요청/후보/반환 수, 기간 날짜 비교와 미산정 안내다. 상세 분석 권한을 대체하지 않으며 외부 노출하지 않는다. 신뢰 온도 상한 50도와 기간 계산·표시만 적용하는 정책을 반영했다. 조건 PUT JSON·선택지·검증·저장 계약과 외부 API는 변경하지 않는다. 경력 비교는 일반 N년 규칙을 따르며 기존 PUT 선택지를 유지한다.

### 내부 입출력 — HTTP API 계약 아님

| 구분 | 현재 구현 |
|---|---|
| 진입점 | `AiRecommendationService.recommend(UpdateConditionsRequest conditions, String message)` |
| 조건 입력 | 2단계 검증 DTO. 요청자 ID는 인증 정보에서 조회 |
| message | 선택 문자열, 최대 10000자. 프로젝트 제목·설명 텍스트 비교에만 사용 |
| developers | 후보 ID·닉네임·tag·일치 직군·점수·기본 이유 |
| projects | 후보 ID·제목·점수·기본 이유·기간 비교 |
| roleCoverage | 직군별 요청 인원·필터 통과 후보 수·반환 수 |
| notices | 경험 미산정·기간 비교·후보 없음 안내 |
| 점수 | 0~100, 소수 둘째 자리. 점수 내림차순·동점 ID 오름차순, 종류별 최대 10건 |
| 저장/외부 호출 | 엔진 자체는 요청·결과·사용량을 저장하지 않으며 Gemini를 호출하지 않음 |

외부 HTTP 요청·응답과 상태 코드는 4단계 명세를 따른다. 내부 DTO 전체를 외부 응답으로 노출하지 않는다.

## 4단계 실행·결과 조회 계약 (2026-11-06)

| Method·URL | 요청 | 응답 |
|---|---|---|
| POST `/api/v1/ai/conversations/{id}/recommendations` | 필수 Idempotency-Key 헤더. 본문 없음/`{}`/`{"messageId":123}` | 신규 SUCCESS 201, 재사용 SUCCESS 200, PENDING 202, FAILED 500. RecommendationResponse |
| GET `/api/v1/ai/conversations/{id}/recommendations` | 양수 대화 ID, 쿼리 없음 | 200, 최신 요청의 RecommendationResponse |
| GET `/api/v1/ai/conversations/{id}/recommendations?requestId=123` | 양수 대화 ID·추천 요청 ID | 200, 해당 과거 요청의 RecommendationResponse |

GET 두 행은 같은 API의 쿼리 사용 방식이다. 아래 POST/GET 계약과 오류 표를 함께 따른다.

### POST `/api/v1/ai/conversations/{id}/recommendations`

| 요청 위치 | 필드 | 필수 여부·검증 |
|---|---|---|
| 헤더 | Authorization | 인증된 액세스 토큰. `Bearer <access-token>` |
| 헤더 | Idempotency-Key | 필수, `[A-Za-z0-9._:-]{1,128}`. 동일 대화 안의 실행 식별자 |
| 경로 | id | 필수, 양수 BIGINT 대화 ID. 본인 소유 대화 |
| JSON 본문 | messageId | 선택, 양수 BIGINT JSON 정수 또는 null. 문자열·소수·boolean 거절. 같은 대화의 USER 메시지. 신규 요청에서 해당 ID까지 USER 메시지 전체를 포함하는 범위 기준 |

조건 버튼 실행 예시는 본문 `{}`이며 메시지 기반 실행 예시는 아래와 같다.

```http
POST /api/v1/ai/conversations/1/recommendations
Authorization: Bearer <access-token>
Idempotency-Key: recommendation-20261106-001
Content-Type: application/json
```

```json
{"messageId": 123}
```

인증 필수. 본문 없음/`{}` 또는 `{"messageId":123}`. messageId는 양수 BIGINT이며 같은 대화의 USER 메시지만 허용한다. 사용자·대화 ID·조건·점수를 본문으로 받지 않는다. 조건은 current_conditions를 현재 DTO로 재검증한다. 초기 `{}`/잘못된 과거 조건은 400이며 요청 행을 만들지 않는다. messageId가 없으면 텍스트 비교를 제외한다. 2026-11-08부터 있으면 해당 ID 이하의 같은 대화 USER 메시지 전체를 저장 순서로 두 줄바꿈으로 연결해 엔진에 전달한다. 누적 입력은 구분 줄바꿈 포함 10000자 이하다. 기존 결과는 재계산하지 않는다. 대화 자체가 누적 한도를 초과하면 신규 요청은 400이며 과거 결과 GET은 유지한다.

필수 헤더 `Idempotency-Key`: 영문·숫자·`.`·`_`·`:`·`-` 1~128자(예: UUID). 대화별 키이며 다른 대화에서 재사용 가능하다. 같은 키·정규화된 조건·메시지 ID는 기존 요청 반환, 같은 키·다른 입력은 409다. 같은 조건도 새 키면 새 추천이다. FAILED 재실행은 새 키를 사용한다. 조건이 변경된 뒤 과거 결과만 보려면 GET requestId를 사용한다.

HTTP 상태별 응답 DTO는 아래 오류·상태 코드 표를 따른다. 엔진/저장 실패의 내부 오류 내용은 서버 로그에만 남긴다.

요청 PENDING은 독립 트랜잭션으로 먼저 저장한다. 결과 전체 저장과 SUCCESS 갱신은 같은 트랜잭션이며 실패 시 롤백 후 별도 트랜잭션으로 FAILED를 기록한다. 대화 잠금으로 조건 수정·삭제·실행을 직렬화하고 키 UNIQUE로 중복 생성을 방지한다. 결과 저장 전 대화가 삭제되면 이력도 삭제되며 404다. 실패 상태 기록 자체가 DB 장애로 불가능하거나 프로세스가 종료되면 PENDING이 남을 수 있다. 자동 복구/만료는 이번 범위에 없으며 임의로 재계산하지 않는다.

### GET `/api/v1/ai/conversations/{id}/recommendations`

| 요청 위치 | 필드 | 필수 여부·검증 |
|---|---|---|
| 헤더 | Authorization | 인증된 액세스 토큰 |
| 경로 | id | 필수, 양수 BIGINT 본인 대화 ID |
| 쿼리 | requestId | 선택, 양수 BIGINT 추천 요청 ID. 해당 대화에 속해야 함 |

GET은 본문과 Idempotency-Key가 필요 없다.

- 쿼리 없음: `created_at DESC, id DESC`로 최신 요청. 실패를 숨기고 이전 성공을 대신 반환하지 않는다.
- `?requestId=123`: 해당 대화의 특정 요청. 다른 대화 요청 ID는 404.
- 인증·대화 소유권 필수. 결과 이력이 없으면 404. 유효한 요청은 PENDING/FAILED여도 200.
- 저장된 조건·대상 ID·점수·기본 이유·순위만 반환한다. 엔진·Gemini를 호출하거나 사용자/프로젝트를 현재 값으로 재조회하지 않는다. 상세 분석·포트폴리오 정보는 반환하지 않는다.

### 응답 필드 — RecommendationResponse

POST/GET은 같은 DTO를 반환한다. 아래 JSON은 필드 설명용 예시이며 실제 추천 점수·이유·ID는 DB 데이터와 조건에 따라 달라진다.

| 필드 | 타입 | 설명 |
|---|---|---|
| requestId | 정수 | 추천 요청 PK. 과거 조회의 requestId에 사용 |
| conversationId | 정수 | 대화 ID |
| messageId | 정수/null | 연결한 USER 메시지 ID. 조건 버튼 실행은 null |
| status | 문자열 | PENDING / SUCCESS / FAILED |
| conditions | JSON 객체 | 실행 당시의 검증·정규화된 조건 스냅샷 |
| createdAt | 시각 | 추천 요청 생성 시각 |
| completedAt | 시각/null | SUCCESS/FAILED 완료 시각. PENDING은 null |
| developers | 결과 항목 배열 | 개발자 저장 결과. rank 오름차순, 최대 10건 |
| projects | 결과 항목 배열 | 프로젝트 저장 결과. rank 오름차순, 최대 10건 |

| 결과 항목 필드 | 타입 | 설명 |
|---|---|---|
| id | 정수 | 추천 결과 PK. requestId와 다른 식별자 |
| recommendedUserId | 정수/null | 개발자 대상 users.id. 프로젝트 결과는 null |
| recommendedProjectId | 정수/null | 프로젝트 대상 projects.id. 개발자 결과는 null |
| matchScore | 숫자 | 저장된 0~100점, 소수 둘째 자리 |
| basicReason | 문자열 | 엔진의 기본 이유를 줄바꿈으로 합친 TEXT |
| rank | 정수 | 개발자·프로젝트 각각 1부터 시작하는 순위 |
| createdAt | 시각 | 결과 생성 시각 |

두 대상 ID는 정확히 하나만 값이 존재한다. detailed_reason은 DB에 NULL로 두고 응답에서 제외한다. 내부 엔진의 직군 집계·기간 비교·항목별 점수·후보 닉네임/제목도 이번 저장 API 응답에는 포함하지 않는다.

```json
{
  "requestId": 10,
  "conversationId": 1,
  "messageId": null,
  "status": "SUCCESS",
  "conditions": {
    "interests": ["교육"],
    "stacks": ["Java"],
    "career": "3년 이상",
    "durationMonths": 3
  },
  "createdAt": "2026-11-06T12:00:00.123456",
  "completedAt": "2026-11-06T12:00:00.456789",
  "developers": [
    {
      "id": 501,
      "recommendedUserId": 201,
      "recommendedProjectId": null,
      "matchScore": 100.00,
      "basicReason": "요청 기술 1개 중 1개가 일치합니다.\n신뢰 온도가 기본 36.5도보다 높습니다.",
      "rank": 1,
      "createdAt": "2026-11-06T12:00:00.345678"
    }
  ],
  "projects": [
    {
      "id": 502,
      "recommendedUserId": null,
      "recommendedProjectId": 301,
      "matchScore": 100.00,
      "basicReason": "관심 분야가 1개 일치합니다.\n기술 스택이 1개 일치합니다.",
      "rank": 1,
      "createdAt": "2026-11-06T12:00:00.345679"
    }
  ]
}
```

SUCCESS여도 조건에 맞는 후보가 없으면 해당 배열은 `[]`다. PENDING/FAILED는 두 배열 모두 `[]`이며 조건·요청 식별자·시각은 같은 필드로 반환한다. GET은 이 상태에 관계없이 200이고, POST의 HTTP 상태는 아래 표를 따른다.

### 오류·상태 코드

| 상태 | POST | GET | 응답 본문 |
|---|---|---|---|
| 200 | 같은 키·같은 입력의 기존 SUCCESS | 최신/지정 요청 조회 성공. PENDING/FAILED 포함 | RecommendationResponse |
| 201 | 신규 추천 SUCCESS. 0건 성공 포함 | 해당 없음 | RecommendationResponse |
| 202 | 같은 키·같은 입력의 기존 PENDING | 해당 없음 | RecommendationResponse |
| 400 | 키 누락/형식 오류, 잘못된 ID/JSON, 알 수 없는 본문 키, 잘못된 저장 조건, 다른 대화/비USER/10000자 초과 메시지 | 잘못된 id/requestId 형식·0 이하 | ErrorResponse |
| 401 | 비로그인·미인증·삭제된 로그인 계정 | 동일 | ErrorResponse |
| 403 | 정지 계정 | 동일 | ErrorResponse |
| 404 | 타인/미존재 대화. 요청 생성 이후 대화·이력이 삭제된 경우 포함 | 타인/미존재 대화, 이력 없음, 해당 대화에 없는 requestId | ErrorResponse |
| 409 | 같은 키에 다른 조건/메시지 ID 사용 | 해당 없음 | ErrorResponse |
| 500 | 신규/기존 FAILED | 해당 없음 | FAILED RecommendationResponse |
| 500 | 요청 생성·실패 상태 기록 등 DB/서버 오류 | DB/서버 오류 | ErrorResponse |

ErrorResponse의 필드는 `status`, `error`, `message`, `path`, `timestamp`다. 추천 엔진/결과 저장 실패의 FAILED 응답과 공통 ErrorResponse를 구분한다. FAILED의 GET 재조회는 200이며 엔진을 다시 실행하지 않는다. 같은 키로 FAILED를 재전송하면 POST는 500, 새 실행을 원하면 새 키를 사용한다.

### 스키마 적용

[PostgreSQL 생성 SQL](sql/ai-recommendations.sql)은 기존 users/projects/ai_conversations/ai_messages가 존재하는 DB에 적용한다. 시간은 기존 AI와 동일한 TIMESTAMP WITHOUT TIME ZONE/LocalDateTime이다. 기본값 대신 서버에서 생성 시각·상태를 넣는다. 스크립트는 앱 시작 때 자동 실행하지 않는다. **앱 기동 전 명시적으로 적용**해야 전체 CHECK·부분 UNIQUE 인덱스가 보장된다. Hibernate update만으로 SQL의 모든 제약이 적용된다고 간주하지 않는다.

대상 FK 중 하나만 존재, 점수 0~100, rank 양수, 요청별 대상 중복 금지 및 대상 종류별 rank 중복 금지를 DB에서 보장한다. 메시지의 동일 대화 소속은 서비스에서 검증한다. analysis_level은 제공 ERD에 있지만 이번 스키마·엔티티에서는 생성하지 않으며 6단계에서 허용 값·용도를 확정한다. input_hash/recommendation_type은 없다. Gemini·사용량은 미구현이다. 프론트/BFF·선택 초안 동기화는 아래 2026-11-07 추가 계약을 따른다. 2026-11-06 사용자 요청으로 개발 PostgreSQL 17.11의 devmozip/public에 이 SQL을 적용했고 컬럼·제약·인덱스를 확인했다. 같은 DB의 읽기 전용 연결로 Hibernate validate·임시 백엔드 기동·헬스 200 UP을 확인했다. 기존 member 데모 계정의 실제 엔진 POST·최신/과거 GET·중복 재요청은 개발 DB에서 확인했다. 전체 다중 사용자·실패/삭제·로그인/Redis 검증을 대신하지 않는다.

### 개발 DB 데모 데이터

[데모 SQL](sql/ai-recommendations-demo.sql)은 스키마 생성 SQL 적용 후 수동 실행한다. 기존 `member@devmozip.demo`와 SQL에 명시된 실제 대상 PK가 있는 개발 데이터셋용이다. 해당 데모 계정에 전용 대화·USER 메시지가 없으면 추가하고, 조건 버튼 실행/메시지 연결 실행의 SUCCESS 요청 2건과 결과 28건을 준비한다. 각 요청은 개발자 6명·프로젝트 8건이며 detailed_reason은 NULL이다.

점수·기본 이유는 2026-11-06 실제 DB 후보를 엔진/API로 계산한 스냅샷이다. SQL 자체는 추천을 계산하지 않는다. 대화별 고정 멱등성 키로 기존 요청을 건너뛰며 재실행 시 중복을 만들지 않는다. 새 회원/프로젝트를 생성하거나 기존 데이터를 갱신/삭제하지 않는다. 현재 개발 DB에는 이미 적용되어 있다. 이후 후보 변경을 반영할 새 추천은 POST API로 실행한다. 프론트 화면 연결은 별도 미완료다.

## 인증 연결과 남은 프론트 계약

현재 `config/SecurityConfig.java`는 users 기반 이메일 로그인을 제공하고 JWT principal은 이메일 문자열이다. AI는 `security.ai.CurrentUserProvider#getCurrentUserId()`로 users PK를 얻으며 `AuthenticatedCurrentUserProvider`가 검증된 인증 정보의 이메일을 기존 `MemberRepository.findByEmail`로 조회한다. 공통 JWT 필터·로그인·사용자 엔티티는 변경하지 않았다. 이전 문서의 `auth/SecurityConfig`·인메모리 계정 설명은 현재 코드와 달라 정정했다.

- `dev`/`test` 프로필 + `ai.current-user.development-enabled=true`일 때만 개발용 고정 ID를 사용한다. `prod`/`production`이 함께 활성화되면 고정 ID 대신 실사용자 인증을 적용한다. 개발 ID는 실제 users에 존재해야 하고 자동 INSERT하지 않는다.
- `application-dev.yaml`은 `AI_DEV_USER_ENABLED`, `AI_DEV_USER_ID`를 읽는다. 모든 인증 요청을 한 ID로 취급하는 임시 방식이므로 실제 A/B 사용자 분리 검증용이 아니다.
- 기본/local/운영 환경과 dev/test opt-in이 꺼진 환경은 실사용자 어댑터를 사용한다. 익명·미인증·삭제된 사용자는 401, 정지된 사용자는 403이다. 운영 프로필에서는 개발 옵션을 켜도 고정 ID를 사용하지 않는다. `ConditionalOnMissingBean` 교체 지점은 유지하며 Controller/Service 입력에 요청자 ID를 추가하지 않는다.
- 브라우저는 HttpOnly 인증 쿠키를 사용한다. AI BFF는 기존 backendFetch와 공통 프록시의 토큰 전달/재발급을 재사용하며 [팀 인증 안내](team-auth-deployment.md)를 따른다.
- 과거 guest 대화 이어받기는 제공하지 않으며 최신 UI 요청의 단일 입력 일회성 로그인 인계만 허용한다. 기존 단건 생성/메시지 API는 유지한다. 로그인 계정 초안의 최초 추천 시 동기화·클라이언트 키·중복 방지·서버 시각 표시는 2026-11-07 추가 계약으로 확정했다.

## 추천 전 로컬 초안 계약

- 로그인 계정만 추천 전까지 대화·조건·전송 전 입력문을 `devmozip:home:v1`에 보관한다. `MatchingConversation.draft`는 브라우저 전용이며 서버 DTO에 추가하지 않았다. 기존 계정별 대화는 복원한다.
- 비로그인 메인/AI 입력은 컴포넌트 메모리에서 유지한다. 사용자가 로그인 인계를 시작할 때만 최대 1500자 입력·resume 토큰·작성 시각을 같은 탭의 v1 sessionStorage에 10분간 보관한다. 로그인 후 유효한 토큰을 실제 로그인 계정의 로컬 USER 메시지로 한 번 저장하고 소비한다. next URL에는 입력 텍스트를 넣지 않으며, 서버 저장/추천 자동 실행은 없다.
- `/ai/matching`은 환영/입력용 공개 화면이다. 추천 버튼이나 비로그인 메시지 전송은 입력을 명시적으로 인계해 로그인으로 연결한다. 기존 계정별 저장 함수는 계속 비로그인 저장을 거절한다. 다른 보호 경로·인증 쿠키·백엔드 소유권 검증은 유지한다.
- 과거 guest 대화의 인계는 제공하지 않는다. 최신 UI 요청에 따른 단일 입력의 일회성 로그인 인계만 허용하며, 기존 guest 저장 데이터는 일괄 삭제하거나 노출·인계하지 않는다. 메인 입력의 브라우저 전용 선택 필드 `inputOwner`로 소유 계정을 구분한다. 과거 소유자 없는 메인 입력은 복원하지 않는다.
- 메시지 전송은 로컬 USER만 추가하며 자동 답변·자연어 분석·추천·사용량 차감은 실행하지 않는다. 추천 버튼은 서버 동기화·실행을 호출한다. 과거 계정별 Mock 결과의 조회 호환은 유지한다.
- 로컬 소유 키는 실제 로그인 이메일별로 구분하며, 이메일이 일치하는 기존 데모 계정은 이전 ID 키를 유지한다. 공통 Mock 프로필 ID가 같은 일반 계정도 대화는 섞이지 않게 했다. 이는 브라우저 저장 분리일 뿐 서버의 users PK·소유권 검증을 대체하지 않는다.
- 로그인 직후 기존 로컬 초안을 서버에 전송하지 않는다. 최초 추천 시 선택한 대화만 아래 sync API로 저장한다. 서버 저장 후의 편집도 다음 추천 시 동기화한다. 로컬/서버 ID와 미확정 실행 키는 계정별 기존 v1 저장소에 보존하며, 메시지 이력 시각은 서버 저장 시각을 표시한다.

## 현재 매핑과 ERD 확인 항목

| 테이블 | 현재 엔티티 필드·관계 |
|---|---|
| `ai_conversations` | `id` BIGINT IDENTITY, `user_id` 필수 숫자, `title` 최대 255자/null, `current_conditions` JSON 매핑/DB null 허용(신규 앱 초기값 `{}`), `created_at`, `updated_at` 필수. type/status 없음 |
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

AI 내부의 참조 컬럼은 메시지 → 대화(`conversation_id`), 추천 요청 → 대화/메시지(`ai_conversation_id`/`ai_message_id`), 추천 결과 → 요청(`recommended_request_id`)이다. `recommended_request_id`는 추천 결과의 설계 참조 컬럼이며 4단계 삭제 쿼리에서 사용한다. 추가 이미지에서 대화·추천 결과·사용량의 사용자 참조와 users.id를 확인했다. 프로젝트 테이블의 정의·실제 FK 제약은 별도 ERD/DDL로 확인한다.

| 대조 항목 | 확인 결과·남은 확인 |
|---|---|
| 대화·메시지 컬럼 | 현재 엔티티의 컬럼명과 일치. 대화 type/status, 메시지 updated_at은 ERD에도 없음 |
| 조건 저장 타입 | ERD는 `current_conditions`·`conditions` 모두 JSONB. 현재 대화 엔티티는 `@JdbcTypeCode(SqlTypes.JSON)`이며 PostgreSQL 실제 컬럼 타입을 조회한 것은 아님. 이 애너테이션 이름만으로 json 컬럼 사용이나 불일치를 단정하지 않음 |
| 시각 | ERD의 DATETIME은 표시 타입. 현재 Java는 LocalDateTime, PostgreSQL 초안은 TIMESTAMP WITHOUT TIME ZONE. PostgreSQL 적용 DDL·시간대 확인 필요 |
| 삭제 쿼리 | 4단계 쿼리는 추천 결과·요청·메시지를 삭제하며 H2 fixture에도 해당 두 테이블이 있다. 실제 PostgreSQL FK/삭제 제약·추가 참조는 미검증 |
| 추천 요청/결과 | 4단계 엔티티·API 구현. analysis_level 보류, status는 VARCHAR+CHECK로 PENDING/SUCCESS/FAILED. 사진만으로 제약을 확정하지 않음 |
| 사용량 | 엔티티·API 미구현. 추가 이미지의 `usage_month`는 CHAR(7)로 확인되어 이전 VARCHAR(7) 판독을 정정함. 1단계 H2 fixture에는 사용량 테이블을 만들지 않으며 실제 DB와의 일치는 해당 단계에서 확인 |

사진만으로 NULL 허용, 기본값, ID 자동 생성, UNIQUE/CHECK/인덱스, ON DELETE 정책은 확인할 수 없다. 아래의 추가 사용자 정리로 확정 정책과 권장 제약을 보완했으며 **DB에 적용되었는지는 별도 확인**해야 한다. 추천 요청 `conditions`는 요청 시점의 변경 불가 스냅샷으로 확정하고, `analysis_level`의 허용 값·구체적 용도는 미확정으로 유지한다.

### 5개 테이블 설계 정책·권장 제약 (2026-11-04 추가 정리)

사용자가 제공한 컬럼·제약·관계 정리를 반영했다. **확정 정책은 구현이 지켜야 할 동작**, **권장은 구현 시 검토할 DB 제약**, **미확정은 추가 결정 사항**이다. 설계 전사만으로 팀 DB DDL 적용·검증 완료를 뜻하지 않는다. ERD의 표시 내용과 현재 구현 상태는 앞 절에 별도로 유지한다.

시간 컬럼의 설계 표기는 사용자 요청에 따라 모두 **DATETIME**으로 통일한다. Java의 LocalDateTime 및 현재 API 시각 형식은 유지한다. 이는 설계 문서 표기이며 PostgreSQL에 `DATETIME`이라는 SQL 타입을 그대로 적용한다는 의미가 아니다. 실제 DB별 타입·DDL은 구현 단계에서 확인하고 이번에는 기존 SQL을 변경하지 않는다.

#### ai_conversations — 대화

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `user_id` | BIGINT | users 참조, 로그인 사용자 서버 대화는 NOT NULL |
| `title` | VARCHAR(255) | 최종 NULL 허용 정책 미확정. 현재 1단계 API는 NULL 허용 |
| `current_conditions` | JSONB | DB NOT NULL·기본값은 검토 대상. DB NULL 허용 유지, 2단계 신규 앱 초기값·NULL 조회 응답은 `{}` |
| `created_at` | DATETIME | NOT NULL 권장 |
| `updated_at` | DATETIME | NOT NULL 권장 |

확정: type/status를 사용하지 않고 한 대화에서 개발자·프로젝트를 함께 추천한다. 4단계부터 해당 추천 결과·추천 요청·메시지를 대화와 함께 삭제한다. 비로그인 메인 입력은 저장·인계하지 않고, 대화 이용과 서버 저장은 로그인 이후에만 허용한다. 실제 저장 시점은 별도 계약이며 로그인 사용자도 추천 전 로컬 처리하는 흐름을 유지한다. 실제 인증 사용자 연결 전 임시 사용자는 개발 환경에서만 사용하고 운영에 적용하지 않는다. 현재 dev/test opt-in 설정은 개발·테스트 전용이다.

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
| `analysis_level` | VARCHAR(20), ERD 항목 | **4단계 생성 보류. 6단계에서 허용 값·활용 계약 확정** |
| `status` | VARCHAR(20) + CHECK | 4단계 PENDING/SUCCESS/FAILED. ERD의 ENUM 표기 대신 CHECK 사용 |
| `created_at` | DATETIME | NOT NULL 권장 |
| `completed_at` | DATETIME | PENDING은 NULL, SUCCESS/FAILED 전환 시 기록 |

확정: 한 대화에서 여러 요청을 만들 수 있고 로그인 사용자만 실행한다. 조건 버튼 실행 시 연결 메시지가 없을 수 있다. 메시지를 연결하면 동일 대화 소속인지 검증한다. 실패 요청은 미차감하며 동일 요청의 중복 실행·차감을 막는다. 스냅샷은 대화의 current_conditions를 수정해도 변경하지 않는다.

4단계 확정 상태는 PENDING/SUCCESS/FAILED다. ERD 외 추가 컬럼은 idempotency_key VARCHAR(128) NOT NULL이며 (ai_conversation_id, idempotency_key) UNIQUE다. 키는 요청 이력 삭제 전까지 유지한다. input_hash는 추가하지 않고 정규화된 조건 스냅샷·메시지 ID를 직접 비교한다.

#### ai_recommendations — 추천 결과

| 컬럼 | 설계 타입 | 정책·제약 구분 |
|---|---|---|
| `id` | BIGINT | PK, 자동 생성 권장 |
| `recommended_request_id` | BIGINT | 추천 요청 참조, NOT NULL |
| `recommended_user_id` | BIGINT | 사용자 참조, NULL 허용. 프로젝트 FK와 정확히 하나만 값 존재 |
| `recommended_project_id` | BIGINT | 프로젝트 참조, NULL 허용. 사용자 FK와 정확히 하나만 값 존재 |
| `match_score` | DECIMAL(5,2) | 백엔드 계산, 0~100 범위 |
| `basic_reason` | TEXT | 4단계 NOT NULL, 엔진의 기본 이유를 줄바꿈으로 합쳐 저장 |
| `detailed_reason` | TEXT | NULL 허용, 프리미엄 상세 분석 시 생성·저장, 무료 응답에서 제외 |
| `rank` | INT | NOT NULL·양수 CHECK. 개발자/프로젝트 각각 1위부터 저장 |
| `created_at` | DATETIME | NOT NULL 권장 |

`recommendation_type`은 사용하지 않는다. 사용자·프로젝트의 참조 PK는 기존 엔티티/DDL로 확인한다. 코드의 대상 PK는 Member.id와 Project.id다. 신규 FK는 NO ACTION으로 대상 물리 삭제를 차단하며 실제 팀 DB 적용은 별도 확인한다. 다음은 확정된 두 대상 중 하나·점수 범위 정책을 보장하기 위한 **권장 CHECK 표현**이며 적용된 DDL이 아니다.

```sql
CHECK (
    (recommended_user_id IS NOT NULL AND recommended_project_id IS NULL)
    OR
    (recommended_user_id IS NULL AND recommended_project_id IS NOT NULL)
),
CHECK (match_score BETWEEN 0 AND 100)
```

4단계 생성 SQL은 match_score·basic_reason·rank·created_at에 NOT NULL을 명시한다. 범위 CHECK와 NULL 차단을 각각 적용한다.

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
| ai_conversations → ai_recommendation_requests | 1:N | 4단계부터 해당 대화 삭제 시 함께 삭제, FK는 NO ACTION |
| ai_messages → ai_recommendation_requests | 1:N, 연결 선택 | 4단계 FK는 NO ACTION. 개별 메시지 삭제 API·향후 처리 정책은 미확정 |
| ai_recommendation_requests → ai_recommendations | 1:N | 4단계 저장 관계. 해당 대화 삭제 시 함께 삭제 |
| users → ai_usages | 1:N | 사용자 탈퇴 시 보존/삭제 정책 미확정 |
| 추천 대상 users/projects → ai_recommendations | 대상별 1:N | 4단계 신규 FK는 NO ACTION. 참조 중인 대상 물리 삭제 차단 |

서비스가 추천 결과 → 요청 → 메시지 → 대화 순서를 보장한다. 신규 FK는 NO ACTION이며 자동 CASCADE/SET NULL을 사용하지 않는다. 개별 메시지 삭제 API·사용자 탈퇴 정책은 별도 범위다. 월 사용량은 이 삭제 경로와 분리한다.

### users 최신 DDL 방향과 AI 데이터 계약 (2026-11-04)

2026-11-05 원격 develop `f6ce13e` 통합 후 코드로 아래 표를 갱신했다. 과거 조건 브랜치의 Integer 경력·누락 필드 설명을 현재 상태로 사용하지 않는다. users는 다른 팀원 담당이며 Member·인증·사용자 DDL을 별도로 수정하지 않았다. 실제 PostgreSQL 적용 상태는 미검증이다.

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
| career | VARCHAR(30) | String, 길이 30 매핑 존재. 3단계에서 N년 차/N년 이상을 숫자로 비교 |
| contact_hours | VARCHAR(100) | contactHours String, 길이 100 매핑 존재 |
| job_categories | JSONB | jobCategories List<String> JSON 매핑 존재. job_category_id는 사용하지 않음 |
| skills, interests | JSONB | List<String> JSON 매핑 존재. 공통 Enum label을 검증 |

- UNIQUE: `uk_users_email(email)`, `uk_users_nickname(nickname)`, `uk_users_tag(tag)` 방향을 따른다. 실제 DB 적용은 미검증이다.
- 제공 DDL에서 PK 외 컬럼에 NOT NULL·기본값을 지정하지 않았다. CHECK·UNIQUE가 NOT NULL을 대신하지 않는다.
- 당시 제공 DDL에는 introduction·introduction_format이 없지만 현재 Member에는 두 필드가 있다. 기존 프로필 구현을 보존하며 AI를 위해 추가하거나 제거하지 않는다.
- `CREATE TABLE IF NOT EXISTS`는 기존 users의 컬럼·제약을 변경하지 않는다. 실행만으로 기존 DB 구조가 전환된다고 간주하지 않는다.
- JSONB 내부 형식·허용 코드·공개/열람 범위와 실제 DB 적용 상태는 별도 확인한다. 다른 팀원 담당 Member·공통 인증·사용자 SQL은 이번에 수정하지 않는다.
- 비밀번호 해시·결제 수단·카카오 식별자 등은 추천 후보 정보나 AI 입력에 사용하지 않는다.

#### 최신 GitHub develop 대조

2026-11-04에는 원격 develop `10c3fbc`를 읽기 전용 조회했고, 2026-11-05에는 최신 `f6ce13e`를 `feat/ai-conditions` 작업 트리에 커밋 없이 병합했다. 위 표는 통합 후 Member 코드 기준이다. entity/enums의 JobCategory·Skill·Interest는 두 브랜치에서 동일하며 label 문자열을 DB에 저장하고 검증한다. 실제 DB 적용은 별도 미검증이다.

AI 외 코드는 공통 controller·service·repository·entity와 도메인별 DTO를 사용한다. AuthService·MemberService·ProfileService는 service의 구체 클래스이며 service/impl에는 AI 외 프로젝트 구현체도 있다. AI의 기존 인터페이스/Impl 구조는 유지한다.

현재 Project는 recruitment_slots를 `ProjectRecruitmentSlot(roleCode, capacity, minimumYears)` 목록으로, tech_stacks·interests를 문자열 목록으로 JSONB 매핑한다. ProjectJobRole 코드는 AI 조건의 JobCategory label과 별도 계약이다. AI의 `roles[].name/count`·전역 요구 경력을 프로젝트 구조에 자동 대응시키거나 공통 Enum을 변경하지 않는다. 코드 구조 확인과 실제 DB 값·추천 변환 계약 확정은 구분한다.

### 2단계 확정 정책과 남은 비교 계약 (2026-11-05)

최신 사용자 요청으로 분야를 `interests`에 통합하고, 기존 5개와 삭제한 분야 7개 선택지를 함께 사용한다. 이 목록 확장은 AI 요청 DTO와 AI 프론트에 한정한다. 공통 Interest·회원·프로젝트 도메인 코드는 변경하지 않는다. 직군은 기존 화면대로 선택·직접 입력을 허용하며 이름 30자·12개·인원 1~20명으로 맞춘다. 요구 경력과 기간 선택지는 기존 화면을 유지한다. 종전 `fields` 별도 필수·직군 Enum 한정 계약을 이 절로 대체한다.

| 항목 | 현재 확정/구현 | 남은 확인 |
|---|---|---|
| 분야 | interests 12개 선택지, 최소 1개 필수. fields 신규 입력 제거 | 실제 프로젝트 interests 값과 추천 비교. AI 전용 확장값을 공통 Enum에 자동 추가하지 않음 |
| 기술 | 선택, 기존 서버 입력/정규화와 화면 선택 UI 유지 | 실제 후보 기술의 별칭·비교 규칙 |
| 직군·인원 | 선택/직접 입력 가능. 제출 시 이름·인원 필수, 공백 정리·중복 거절, 12개·각 30자·1~20명 | 사용자 직군·ProjectJobRole 코드와의 추천 비교·알 수 없는 직군 처리 |
| 요구 경력 | 필수, 기존 경력 무관·1/2/3/5년 이상 선택. 2026-11-06 개발자 후보는 경력 필터 적용·N년 차=N년 이상·별도 가산점 없음으로 확정 | 프로젝트 직군별 minimumYears와의 비교 계약. 조건 PUT 선택지는 이번 구현에서 확장하지 않음 |
| 프로젝트 기간 | 조건은 개월 수, 프로젝트 예정일 유지. 3단계는 조건 종료일과 예정 종료일의 날짜 차이만 표시 | 필터·기간 점수는 미적용. 날짜 누락·역전은 미확인 |
| 소유권 | 본인만 조회/수정, 관리자도 타인 데이터 접근 불가 | 기존 인증/소유권 회귀 유지 |
| 초기값·교체 | 초기/NULL 조회는 {}, PUT은 interests·career·durationMonths 필수 전체 교체 | DB NOT NULL·기본값·기존 행 일괄 갱신은 이번 범위 아님 |

원본 ERD 이미지는 이번에 직접 확보하지 못했다. 문서 전사·엔티티와 SQL 초안을 대조했으며 실제 PostgreSQL JSONB·FK·DDL은 미검증이다. 프론트 로컬 입력과 서버 DTO를 맞췄지만 AI BFF·서버 호출을 연결한 것은 아니다. 이 절은 2단계 당시 기록이며, 현재 후보 조회·점수 규칙은 아래 3단계 내부 엔진과 추천 정책을 따른다.

### 추가 API 계약의 필요 범위

ERD는 저장 구조이고 API 명세는 클라이언트·서버 간 요청/응답·인증·오류 계약이다. 제공받은 API 표의 경로·기능은 위에 반영했으며 현재 문서 앞부분에 1~4단계의 구현 API 8개와 내부 엔진 명세가 있다. 팀에 상세 DTO·오류·인증 계약이 있다면 대조 기준으로 활용한다.

과거 guest 인계·로그인 왕복 브라우저 검증 기록은 당시 정책 기준이다. 2026-11-05부터 guest 인계를 제거했으며 이번 정책의 검증 결과는 진행 문서 마지막 기록을 따른다. AI API BFF 인증 전달과 프론트 초안의 최초 전송·중복/부분 실패 처리는 2026-11-07 추가 계약으로 구현했다. 공유 PostgreSQL의 신규 sync 스키마 적용·실제 계정 통합 검증은 최신 진행 기록을 따른다. 조건 DTO·검증 계약은 위 2단계 구현 절을 따른다. 추천 실행·조회 응답은 앞의 4단계 계약을 따르고 상세 분석·사용량은 후속 단계 요청 시 정한다. ERD의 컬럼명을 API JSON 키로 그대로 확정하거나 API 미구현 항목을 완료로 표시하지 않는다.

## API 단계별 개요

4단계의 구현 계약은 앞의 실행·조회 절을 따른다. 5~7단계는 미구현이며 개요만 기록한다.

| 단계 | Method·URL | 역할·남은 계약 |
|---|---|---|
| 3 | 외부 API 추가 없음 | 후보 조회·점수 계산 엔진만 구현하는 단계 |
| 4 | POST `/api/v1/ai/conversations/{id}/recommendations` | 구현: 대화 소유권·저장 조건 검증, 선택 메시지·멱등성 키, 요청/결과 저장 |
| 4 | GET `/api/v1/ai/conversations/{id}/recommendations` | 구현: 최신 요청 또는 requestId로 과거 요청 조회. 재계산 없음 |
| 5 | 기존 메시지 API 확장 방향, 세부 계약 미확정 | 자연어 분석·조건 병합·추가 질문·ASSISTANT 저장. 1단계의 저장 전용 동작과 구분 |
| 6 | POST `/api/v1/ai/recommendations/{id}/detailed-analysis` | 추천 결과의 개발자 상세 분석. 서버 프리미엄·소유권·포트폴리오 열람권 확인 |
| 7 | GET `/api/v1/ai/usage` | 한국 시간 현재 월의 사용/한도/잔여 횟수. JSON 키·한도 오류 계약 미확정 |

4단계 결과는 `recommended_user_id`/`recommended_project_id` 중 정확히 하나, `match_score`·`basic_reason`·`rank`, 초기 `detailed_reason` null 방향이다. `recommendation_type`은 추가하지 않는다. 실제 DDL과의 일치는 해당 단계에서 확인한다. 첨부의 논리 관계 `conversation_id/message_id`를 현재 물리 컬럼명과 혼동하지 않는다.

사용량 차감은 7단계에서 완성한다. 메시지·조건 변경·조회·상세 분석은 미차감이며, 성공한 신규 추천만 1회다. 4단계는 대화별 멱등성 키로 요청을 식별한다. [이전 전역 API 초안](api-plan.md)의 `/matching/...` URL·Mock 점수식·응답 후보는 이 명세를 대체하지 않는다.

### 메인 입력의 대화 전환 (2026-11-05 갱신)

- 로그인 계정의 메인 작성 중 입력은 소유 계정이 일치할 때 새로고침 시 복원한다. 추천 시작 시 새 로컬 대화와 메인 설명 초기화를 한 번에 저장하며, 저장 성공 후에만 대화 화면으로 이동한다. 실패하면 입력·기존 대화를 유지하고 재시도한다.
- 제출 중 버튼 잠금과 최신 메인 입력 확인으로 연속 제출을 막는다. 같은 문장을 새로 작성해 별도 대화를 만드는 것은 허용한다. 기존 중복 기록은 자동 삭제/병합하지 않으며 목록에서 직접 삭제한다.
- 이전 추천 대화 열기도 로그인이 필요하며 guest 초안 인계는 제거했다. 서버 API·추천 실행·사용량 정책 변경은 없다.

## 2026-11-07 프론트 연동 추가 계약

기존 추천 점수·대상·인증·단건 저장 계약은 유지한다. 사용자 승인으로 최초 추천 시 선택한 초안만 저장하며, 최신 결과 기본 표시·과거 선택 조회를 연결했다. 다음 두 API를 추가했다.

### POST `/api/v1/ai/conversations/sync`

- 로그인 필수. `conversationId`를 보내면 본인 대화인지 잠금 조회하며 타인/미존재는 404다. ID는 요청자의 신원으로 사용하지 않는다.
- 본문: `{conversationId?: number, clientKey: string, title?: string, messages: [{clientKey: string, content: string}], conditions: UpdateConditionsRequest}`.
- clientKey는 영문·숫자·`._:-` 1~128자다. 제목은 기존과 같이 null 또는 255자 이하다. 새 동기화 메시지는 비어 있지 않은 1~1500자 USER만 받는다. 원래 작성 시각·역할·사용자 ID는 받지 않는다. 조건은 기존 PUT DTO를 재사용한다.
- `conversationId`가 없으면 인증 사용자+clientKey로 기존 대화를 재사용하거나 생성한다. 메시지는 대화+clientKey로 기존 행을 재사용한다. 기존 메시지 키에 다른 내용은 409이며 전체 동기화를 롤백한다. 동시 최초 업로드의 UNIQUE 충돌은 새 트랜잭션에서 한 번 재조회한다.
- 대화·메시지·조건은 한 트랜잭션이다. 응답 유실 후 같은 키로 재전송해도 행이 중복되지 않는다. 기존 메시지 삭제·수정·추천 실행은 동기화에 포함하지 않는다. 빈 messages는 허용한다.
- 200 ConversationDetailResponse. 기존 대화 요약/상세 및 MessageResponse에 nullable `clientKey`를 추가했다. 기존 단건 API로 만든 행은 null이다. 서버 ID와 clientKey를 연결해 새 메시지와 기존 메시지를 구분한다.
- 추천 실행은 동기화 성공 뒤 기존 POST recommendations를 별도로 호출한다. 두 API 전체를 하나의 트랜잭션으로 묶지 않는다. 실행 응답 유실 시 동일 추천 키·조건을 유지하며, 확정된 SUCCESS/FAILED 이후 신규 실행만 새 키를 사용한다.
- 기존 ERD에 없는 AI 전용 컬럼 두 개가 필요하다. [추가 SQL](sql/ai-draft-sync.sql)은 ai_conversations.client_key, ai_messages.client_key 및 각각 `(user_id, client_key)`, `(conversation_id, client_key)` UNIQUE 인덱스만 추가한다. 기존 행은 NULL이며 공통 설정·다른 테이블은 변경하지 않는다. 공유 DB 적용 여부는 최신 진행 기록을 따른다.

### GET `/api/v1/ai/conversations/{id}/recommendations/history`

- 로그인·본인 대화 소유권 검증. 응답은 `{requestId, status, createdAt}` 배열, createdAt DESC·id DESC다. 이력 없음은 `[]`, 타인/미존재 대화는 404다.
- 추천 실행·재계산 없이 요청 요약만 조회한다. 선택한 결과는 기존 GET recommendations?requestId로 조회한다.

### 브라우저 BFF·표시

- `/api/ai/conversations` 목록, `/{id}` 상세·삭제, `/sync`, `/{id}/recommendations` 실행·조회, `/{id}/recommendations/history`만 허용한다. 임의 프록시 경로·사용자 ID 쿼리는 거절한다.
- POST/DELETE는 동일 Origin만 허용한다. 브라우저 Authorization은 전달하지 않고 기존 backendFetch가 HttpOnly 쿠키를 사용한다. 응답은 no-store이며 일반 오류의 내부 상세는 숨긴다. FAILED 추천 DTO는 상태와 함께 유지한다.
- 결과는 서버 ID·점수·기본 이유로 표시한다. 이름·프로필 tag 등은 현재 추천 DTO에 없어 Mock 데이터나 잘못된 상세 URL로 대체하지 않는다. 자연어 분석·상세 분석·사용량 구현은 포함하지 않는다.

### 구현·적용·사용자 검토 상태 (2026-11-07)

- 위 sync/history와 BFF는 현재 작업 브랜치의 구현 계약이다. 사용자가 공유 DB 적용 성공과 실제 연동 검토 완료를 확인했다. UI 개선 요청은 남아 있어 화면 최종 승인을 뜻하지 않는다. 최신 요청은 문서 정리이며 API 계약을 새로 변경하지 않았다.
- 사용자 제공 기동 로그는 ai_conversations.client_key 누락에 의한 Hibernate validate 실패다. 새 백엔드 기동 전 위 추가 SQL의 두 컬럼과 두 UNIQUE 인덱스 적용 여부를 확인해야 한다. 이후 사용자가 DB 적용 성공과 실제 연동 검토 완료를 확인했다. 이번에 에이전트가 헬스·개별 제약·세부 시나리오를 재검증한 것으로 기록하지 않는다. 기존 단건 대화 조회도 엔티티에 추가된 컬럼을 요구하므로 프론트 버튼만 연결해 해결할 수 없다.
- 코드상 `pendingRecommendation`이 남아 있으면 기존 키·조건으로 확인/재시도한다. 이때 변경 중인 로컬 조건은 다음 신규 요청에 반영한다. 결과 화면의 단순 GET 새로고침은 추천을 실행하지 않는다. 백엔드의 장기 PENDING 자동 복구는 여전히 미구현이다.
- UI 수정 시 기존 경로·DTO·조건 스냅샷·인증/소유권·같은 키 처리 계약을 보존한다. 이번 문서 정리로 Gemini·상세 분석·사용량 API·새로운 공개 프로필 조회 계약을 확정하지 않는다.

### 2026-11-07 UI 개선·기존 API 재사용

- 백엔드 경로/DTO/엔진/DB는 변경하지 않았다. UI의 프로젝트 분야·관심 분야는 서버의 `interests` 한 필드에 통합한다. 자연어 추출은 미구현이며 수동 조건을 분석 결과로 표시하지 않는다.
- 프론트 GET `/api/ai/projects/{id}`는 기존 GET `/api/v1/projects/{id}`를 재사용하는 읽기 전용 BFF다. 양수 ID·공개 응답·일치 ID를 검사하고 `{id,title,summary,description,imageUrl,stacks,roles}`만 전달한다. 캐시하지 않으며 현재 공개 프로젝트 정보로만 보강한다. 추천 점수/이유/순위 스냅샷은 재계산하지 않는다.
- 기존 `/projects/{id}` 화면이 Mock 도메인을 사용하므로 실제 추천 ID를 강제로 매핑하지 않는다. 프로젝트 상세는 기존 API로 받은 정보를 화면 안에서 보여 준다. 개발자 ID→공개 프로필 tag 연결, 프리미엄 상세 분석/열람권, 서버 월 사용량은 추가 계약·백엔드 후속 작업이 필요하며 이번에 구현하지 않았다.
- 비로그인 입력 인계는 위 로컬 초안 계약과 기능 정책을 따른다. 인증/저장 정책 중 화면 진입 제한과 입력 미인계만 최신 사용자 요청으로 바뀌었고, 로그인 이후·추천 시에만 서버 저장하는 정책은 유지한다.

## 2026-11-08 누적 메시지·공개 개발자 표시 계약 (현재)

- 위 POST `messageId`는 누적 USER 메시지의 끝 범위다. 기존 nullable `ai_message_id`를 사용하며 별도 배열/가짜 통합 메시지/새 DB 컬럼을 만들지 않는다. 메시지는 수정/개별 삭제 API가 없고 대화 잠금 아래 추가되므로 범위와 조건이 같으면 재시도 입력도 유지된다. 프론트는 pending에 최초 로컬 메시지 키를 보존하고 sync 응답의 서버 ID로 연결한다.
- 프론트는 새 메시지가 있으면 마지막 USER까지 지정한다. 조건만 있는 대화는 `{}`로 실행한다. 기존 범위 키가 없는 pending은 종전 `{}`로 유지한다. 같은 키의 조건/범위 변경은 기존처럼 409이며 이미 완료한 결과는 재실행하지 않는다.
- POST messages와 sync의 **새 메시지**는 1~1500자다. 대화 USER 내용 사이 `\n\n`을 포함한 UTF-16 길이는 10000자 이하여야 한다. ASSISTANT는 누적 길이에서 제외한다. sync는 중복 키 재전송을 추가 길이로 세지 않으며 초과하는 배치는 전체 롤백한다. 과거 초과 데이터의 GET/삭제와 저장 결과 GET은 유지한다. 초과 오류는 기존 400 ErrorResponse, AI BFF는 기존 일반 400 메시지를 사용한다.
- 결과 `developers[].developer`를 추가한다. `{tag, nickname, profileImage, jobCategories: string[], skills: string[], career: string|null, collaborationStatus: string|null, trustScore: number|null}` 또는 `null`이다. 회원의 현재 공개 정보이며 저장된 matchScore/basicReason/rank와 구분한다. ACTIVE·직군·유효 tag 조건을 확인하고, 불가하면 null로 제공한다. 이메일·연락처·권한·상세 포트폴리오는 노출하지 않는다. 프로젝트 Item의 developer는 null이다.
- 공개 프로필 링크는 기존 `/users/{tag}` 화면을 사용한다. 공통 프로필 엔드포인트·권한 계약은 변경하지 않는다. 대상 ID·점수·기본 이유·기존 DTO 키는 유지한다. 배포 시 이 응답을 제공하는 백엔드도 갱신해야 하며 신규 SQL 적용은 필요 없다.
- 프론트의 프로젝트 분야 그룹을 제거하고 통합 **관심 분야**만 표시한다. 12개 중 최소 1개와 career/durationMonths 필수 검증은 기존 서버와 동일하다.
- history GET으로 요청 목록을 읽고 각 요청 GET으로 결과를 복원한다. 메시지 범위 기준으로 대화에 누적하며 기존 NULL 범위 결과는 서버 저장 시각을 사용한다. 로컬 미동기화 메시지는 저장 결과 뒤에 둔다. GET/새로고침으로 추천·사용량을 생성하지 않는다.

### 추천 카드의 경력·협업 상태 표시 (2026-11-08)

- `developers[].developer.career`와 `collaborationStatus`는 기존 users의 공개 프로필 필드다. 상태는 AVAILABLE/RESTING/BUSY/OPEN_TO_OFFERS/UNAVAILABLE 또는 null이며, 경력 미입력·상태 미등록을 협업 가능으로 추정하지 않는다. 별도 DB/공통 프로필 API 변경은 없다.
- 카드에는 현재 직군·경력과 협업 상태 배지를 표시한다. 과거 추천 이후 협업 상태나 경력이 바뀌어도 기존 matchScore/basicReason은 재계산하지 않는다. 현재 공개 정보와 추천 당시 스냅샷을 구분한다.

### 프론트 동일 조건 중복 실행 방지 (2026-11-08)

- 같은 대화의 마지막 SUCCESS 조건과 현재 조건이 같으면 프론트에서 신규 sync/추천 POST를 보내지 않는다. GET history/개별 결과로 성공 조건을 복원하며, 로컬 복원 정보가 없는 저장 대화는 실행 전에 GET으로 확인한다. FAILED·기존 pending 재시도는 허용한다.
- 백엔드 API/멱등성 계약은 변경하지 않는다. 직접 API로 새 키를 제출하는 것까지 금지하는 서버 정책은 이번 범위에 포함하지 않는다.

### 추천 이유·신뢰 온도 표시 (2026-11-08)

- `developers[].developer.trustScore`에 기존 공개 프로필의 현재 신뢰 온도를 제공한다(null 허용). 이름 옆에 표시하며 기본 36.5도로 임의 보충하지 않는다. 새 DB 컬럼이나 공통 프로필 API 변경은 없다.
- 새 개발자 추천의 basicReason은 직군 일치/신뢰 온도 문구를 제외하고, 실제 일치한 회원 기술명을 포함한 `Java, Spring Boot 기술 스택을 활용할 수 있습니다.` 형태로 저장한다. 직군/기술/신뢰 점수 계산은 기존과 같다. 프로젝트 이유는 변경하지 않는다.
- 과거 basicReason·점수는 DB에서 수정하거나 재계산하지 않는다. 프론트는 기존 직군/온도 문구를 숨기고, 기술 개수만 저장된 문장은 현재 공개 프로필 기술이 등록되어 있다는 안내로 표시한다. 당시 일치 기술명을 복원한 것으로 주장하지 않는다. 새 이름 포함 이유는 현재 기술이 바뀌어도 저장 문장을 유지한다.

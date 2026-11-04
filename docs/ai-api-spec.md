# AI API 명세: 확정 범위와 TODO

기준일: 2026-11-03. 1단계 대화 CRUD·사용자 메시지 저장 API를 구현했으며 나머지 AI 업무 API는 미구현이다. 팀 H2 스키마·실행 설정과의 통합은 미완료다. 사용자 제공 공통 지침과 후속 URL/API 표를 반영했다. HTTP 메서드와 경로는 아래 표를 기준으로 하며, 제공되지 않은 JSON 필드·스키마는 TODO다. 논리 구조는 호출 가능한 요청/응답 예제가 아니다.

## 확정된 공통 계약

- 기본 경로: `/api/v1/ai`.
- 직접 선택한 조건은 PUT으로 전체 교체한다.
- 자연어에서 추출한 조건 변경은 기존 조건과 병합한다.
- 한 번의 추천 실행으로 개발자·유사 프로젝트 결과를 함께 생성한다. 점수는 백엔드에서 계산한다.
- 요청자 ID는 Spring Security 인증 정보에서 가져온다. 본문·쿼리로 요청자 신원을 지정하지 않는다.
- 비로그인 추천 실행은 HTTP 401 Unauthorized다.
- 로그인 전 메시지 입력·조건 선택은 가능하지만 DB 저장과 실제 추천 실행은 로그인 후 진행한다.

## 화면 경로와 API 목록

화면 URL은 `/ai/matching`이다. 현재 `frontend/app/ai/matching/page.tsx`가 이 경로를 제공하며 사용자 명세와 일치한다. 앞선 문서의 `/matching` 설명은 현재 코드와 맞지 않아 정정했다. 화면 URL과 백엔드 API 기본 경로 `/api/v1/ai`는 서로 다른 역할이다.

| 기능 | Method | API URL | 동작·차감 |
|---|---|---|---|
| AI 대화 생성 | POST | `/api/v1/ai/conversations` | 새 대화 생성, 추천 횟수 미차감 |
| AI 대화 목록 조회 | GET | `/api/v1/ai/conversations` | 이전 대화 목록 조회, 미차감 |
| AI 대화 상세 조회 | GET | `/api/v1/ai/conversations/{id}` | 대화 및 조건 조회, 미차감 |
| AI 대화 삭제 | DELETE | `/api/v1/ai/conversations/{id}` | 본인 대화만 삭제, 미차감 |
| AI 메시지 전송 | POST | `/api/v1/ai/conversations/{id}/messages` | 자연어 요구사항 분석·기존 조건 병합·추가 질문, 미차감 |
| 추천 조건 수정 | PUT | `/api/v1/ai/conversations/{id}/conditions` | 직접 선택한 조건 전체 교체, 미차감 |
| AI 추천 실행 | POST | `/api/v1/ai/conversations/{id}/recommendations` | 직군·기술 스택·경험 등을 기반으로 적합도를 계산하고 개발자·프로젝트 결과 저장, 신규 생성 성공 시 1회 차감 |
| 추천 결과 조회 | GET | `/api/v1/ai/conversations/{id}/recommendations` | 저장된 개발자·프로젝트 결과 조회, 미차감 |
| 프리미엄 상세 분석 | POST | `/api/v1/ai/recommendations/{id}/detailed-analysis` | 기존 추천에 대한 프리미엄 전용 추가 분석, 미차감 |
| 월별 AI 추천 사용량 조회 | GET | `/api/v1/ai/usage` | 무료 월 15회·프리미엄 월 100회 기준 사용량 조회, 조회 자체는 미차감 |

`conversations/{id}` 하위의 `{id}`는 대화 식별자, `recommendations/{id}`의 `{id}`는 추천 식별자다. ID 자료형·형식과 추천 식별자가 결과 묶음/개별 결과 중 무엇을 가리키는지는 TODO다. 상세 분석 경로는 사용자가 오타임을 확인하여 `detailed-analysis`로 확정했다.

## 요청·응답의 논리 구조

현재 승인된 1단계는 대화 생성·목록·상세(메시지 포함)·삭제와 사용자 메시지 저장의 5개 API다. 위 표의 메시지 분석·조건 병합·추가 질문은 후속 전체 기능 명세이며 1단계에서는 실행하지 않는다. Gemini 호출 및 임시 ASSISTANT 메시지 자동 생성도 하지 않는다. 대화는 생성 시간 역순, 메시지는 생성 시간과 ID 기준으로 정렬한다. 사용자 추가 설계에 따라 대화 type/status는 사용하지 않고, 삭제 시 메시지·추천 요청·추천 결과는 물리 삭제하되 월 사용량은 유지한다.

| API 작업 | 요청 | 응답 | 미확정 세부 사항 |
|---|---|---|---|
| 대화 생성 | 생성에 필요한 입력 여부 TODO | 생성한 대화 표현 | 제목·초기 조건·익명 초안 전달 여부, ID 및 생성 시각 필드 |
| 대화 목록 | 조회 조건 TODO | 이전 대화 목록 | 정렬·페이지네이션·요약 필드 |
| 대화 상세 | 경로의 대화 ID | 대화·조건 | 메시지 포함 구조와 추천 결과 포함 여부 |
| 대화 삭제 | 경로의 대화 ID | 삭제 처리 결과 표현 TODO | 성공 상태, 연관 메시지·결과·사용량 보존/삭제 정책 |
| 메시지 전송 | 대화 ID와 자연어 메시지 | 요구사항 분석·병합 결과와 필요한 추가 질문 | 메시지 JSON 키·길이, 분석 실패·추가 질문 형식 |
| 조건 수정 | 대화 ID와 선택 조건 전체 | 교체된 조건 표현 TODO | 조건 스키마와 검증 규칙 |
| 추천 실행 | 대화 ID, 추가 본문 필요 여부 TODO | 개발자·프로젝트 결과와 백엔드 계산 점수 | 동기/비동기 처리, 저장 결과 ID·스냅샷·응답 필드 |
| 추천 결과 조회 | 대화 ID | 저장된 개발자·프로젝트 결과 | 최신 결과만/이력 전체 여부, 추천 없을 때 응답 |
| 상세 분석 | 추천 ID, 추가 본문 필요 여부 TODO | 상세 포트폴리오 기반 추가 분석 | 대상 범위·결과 구조·재분석 정책 |
| 사용량 조회 | 인증 주체, 조회 월 지정 가능 여부 TODO | 월별 추천 사용량 | 사용/한도/잔여 횟수·월의 JSON 키·자료형·시간대 |

본문 구조가 미확정인 항목은 빈 본문으로 확정한 것이 아니다. 생성·조회·분석 API가 정의되어도 구현 단계는 별도 요청에 따른다.

## JSON 필드와 오류 계약의 의미

요청 JSON 필드는 클라이언트가 보내는 데이터의 이름·자료형·필수 여부다. 응답 JSON 필드는 서버가 돌려주는 데이터의 이름과 구조다. 예를 들어 메시지 본문을 `content`라는 키로 받을지, 추천 목록을 `developers`와 `projects`로 나눌지 등을 정하는 일이다. 이 이름들은 설명용 예시이며 확정된 DTO가 아니다.

오류 계약은 잘못된 입력·권한 부족·한도 초과·서버 장애 등에 어떤 HTTP 상태, 오류 코드, 메시지와 본문 구조를 반환할지 정하는 약속이다. 비로그인 추천 실행의 401은 확정되었지만 다른 상태·코드·필드 형식은 아직 TODO다. API URL 확정과 별도로 각 구현 단계에서 필요한 데이터 계약을 구체화한다.

## 요청·응답 구조에서 미확정인 항목

- 조건: 허용 키, 값의 자료형, 필수·선택 항목, 문자열 길이, 배열 상한, enum, null/빈 값/누락 키 처리.
- PUT 전체 교체: 빠진 선택 필드의 초기화 방식과 필수 필드 누락 오류. 기존 값을 유지하는 부분 병합으로 구현하지 않는다.
- 자연어: 원문·변경분·병합 결과를 어떻게 표현할지, 삭제·충돌·추출 실패 처리, 기존 조건 버전 관리.
- 추천 요청: 경로의 대화 ID에 연결된 조건 선택·스냅샷 시점, 추가 본문 필요 여부, 동기/비동기 처리, 재시도·멱등성 키.
- 추천 응답: 결과 ID, 대상 ID·노출 필드, 점수 자료형·범위, 근거 제공 범위, 정렬·결과 개수, 두 결과 그룹의 빈 결과 처리, 사용량 포함 여부.
- 상세 분석: 분석 대상과 재분석·캐시 정책, 비공개 포트폴리오 원문·파생 분석의 노출 범위.
- 공통: JSON 키 이름, 성공 HTTP 상태, 응답 envelope, 페이지네이션, 날짜 형식, 오류 코드·오류 스키마.

위 목록은 전체 AI 기능의 설계 TODO다. 아래 1단계 구현 계약에서 구체화한 생성·조회·삭제·메시지 DTO/HTTP 항목은 해당 계약을 따른다. 나머지는 실제 ERD·도메인 계약과 사용자 확인 없이 확정하지 않는다. 무료 15회·프리미엄 100회 및 차감 규칙은 [기능 명세](ai-matching.md)에 따른다.

## 인증과 접근 권한

추천 버튼에서 로그인으로 이동시키는 프론트 처리는 편의 기능이다. 서버는 별도로 인증을 검증하고 비로그인 추천 실행을 401로 거절해야 한다. 프리미엄 여부도 브라우저의 데모 상태가 아닌 서버에서 확인해야 한다. 대화·추천 식별자를 사용하는 경우 타인 데이터 접근을 막는 소유권 검증이 필요하며, 관리자 예외와 거절 상태 코드는 TODO다.

서버에 저장된 대화의 생성·목록·상세·삭제·메시지·조건·추천 결과와 개인 사용량은 인증 주체를 기준으로 처리한다. 대화 삭제는 본인 대화만 가능하며 대화·추천 접근에도 소유권을 검증한다. 상세 분석은 로그인 및 프리미엄 자격을 확인한다. 로그인 전 입력 UI와 로그인 후 영속 API를 구분하며, 기존 `{id}/messages`를 익명 공개하는 것으로 해석하지 않는다. 비로그인 자연어 분석이 필요할 때의 비저장 처리 방식은 TODO다.

현재 `auth/SecurityConfig.java`는 인증 API·헬스체크 등의 허용 경로를 제외하고 인증을 요구하며 JWT 필터가 서명·만료·역할을 확인한다. `Authentication`의 principal은 username 문자열이고 실제 사용자 DB ID가 아니다. 임시 계정은 `InMemoryUserDetailsManager`로 제공된다. 이 구조를 실제 사용자 ID 및 구독 상태에 연결하는 계약은 TODO다.

기존 인증 기본 경로는 `/api/auth`이고 인증 BFF는 [팀 안내](team-auth-deployment.md)를 따른다. 신규 AI 기본 경로를 이유로 기존 인증 경로를 변경하지 않는다. 프론트에서 Spring AI API까지의 BFF/프록시 전달 계약은 TODO다.

현재 `JsonAuthenticationEntryPoint`는 JSON 401 오류를 반환한다. 1단계 API의 익명 요청/사용자 식별 실패 401은 H2 기반 통합 테스트로 검증했고 운영 임시 식별 거부는 설정 테스트로 검증했다. 실제 운영 검증은 하지 않았다. 프리미엄 권한 부족·한도 초과·검증 오류·데이터 없음·AI 장애의 상태 코드와 오류 본문은 TODO이며 기존 인증 오류를 근거 없이 전체 AI 오류 계약으로 확정하지 않는다.

## 기존 문서와의 관계

[api-plan.md](api-plan.md)의 `/matching/...` URL은 과거 미구현 후보이며 이 AI API의 확정 경로가 아니다. 기존 프론트 Mock 타입·필드·점수식도 서버 DTO 계약으로 확정하지 않는다. ERD·DDL과 실제 엔티티 대조를 마친 후 요청된 단계에 필요한 계약만 구체화한다.

## 1단계 구현 계약

아래는 이번 구현의 DTO·HTTP 계약이다. 매핑의 테이블/컬럼은 사용자 ERD를 반영했다. [이전 PostgreSQL DDL 초안](sql/ai-conversations-proposal.sql)은 참고용이며 팀의 H2 확정 스키마가 아니다. 실제 팀 스키마를 확인하거나 적용한 결과는 아니다. 추천·조건 수정·분석·사용량 API는 구현하지 않는다.

| API | 요청 | 정상 응답 |
|---|---|---|
| POST `/api/v1/ai/conversations` | 본문 없음 또는 `{}` 또는 `{"title":"팀원 찾기"}`. title은 null 허용, 최대 255자 | 201, Location 헤더, 대화 요약 객체 |
| GET `/api/v1/ai/conversations` | 본문 없음 | 200, 내 대화 요약 배열. createdAt DESC, id DESC. 페이지네이션은 이번 단계 미구현 |
| GET `/api/v1/ai/conversations/{id}` | 양수 BIGINT 대화 ID | 200, 대화 상세 및 messages 배열. 메시지는 createdAt ASC, id ASC |
| DELETE `/api/v1/ai/conversations/{id}` | 양수 BIGINT 대화 ID | 204, 본문 없음. 추천 결과→추천 요청→메시지→대화 순으로 삭제, 월 사용량 유지 |
| POST `/api/v1/ai/conversations/{id}/messages` | `{"content":"백엔드 개발자를 찾고 있어요"}`. content는 필수이며 공백만 입력 불가 | 201, 저장한 USER 메시지 객체. AI 응답 생성 없음 |

대화 요약은 `id`(정수), `title`(문자열 또는 null), `createdAt`·`updatedAt`(시간대/오프셋 없는 LocalDateTime ISO-8601)이다. 상세는 요약 필드에 `currentConditions`(JSON 값 또는 null), `messages`(배열)를 추가한다. 조건은 이번 단계에서 변경하지 않는다. 메시지 객체는 `id`(정수), `role`(USER/ASSISTANT), `content`(문자열), `createdAt`(시간대/오프셋 없는 LocalDateTime ISO-8601)이다. 저장 API는 역할을 USER로 정하고 요청자의 role·userId·conversationId를 신뢰하지 않는다. 실제 관계는 경로의 본인 대화와 연결한다.

사용자 ID는 모든 Service 진입점에서 `CurrentUserProvider`로 얻는다. 잘못된 ID 형식·0 이하 ID, 너무 긴 제목·빈 메시지는 400, 없는 대화와 타인 대화는 동일한 404다. 소유 여부를 응답 차이로 노출하지 않는다. 기본/운영 환경의 사용자 식별 미준비·개발 사용자 설정 누락/해당 users 행 없음은 기존 인증 예외 처리로 401을 반환한다. 기존 SecurityConfig의 로그인 요구도 유지한다.

오류 본문은 기존 `ErrorResponse` 구조를 재사용한다: `status`(정수), `error`(HTTP 이유), `message`(안내), `path`(요청 경로), `timestamp`(시각). JSON 파싱 오류는 기존 400 처리를, DB 접근 오류는 develop의 공통 예외 처리에 따라 500으로 반환한다. 예상하지 못한 FK 충돌도 500으로 반환하고 트랜잭션 전체를 롤백한다. 이는 충돌을 무시하거나 실제 FK가 검증되었다는 뜻이 아니다.

## 개발 사용자 식별과 인증 교체 지점

- `CurrentUserProvider#getCurrentUserId()`가 인증과 AI 업무 코드 사이의 교체 지점이다. 패키지는 `com.devmozip.backend.ai`다.
- `dev` 또는 `test` 프로필 + `ai.current-user.development-enabled=true` 두 조건을 모두 만족해야 고정 ID 구현을 활성화한다. `prod` 또는 `production`이 함께 활성화되면 고정 ID 구현을 사용하지 않는다. 기본 환경도 요청을 거부한다.
- dev 설정은 `application-dev.yaml`에서 `AI_DEV_USER_ENABLED`, `AI_DEV_USER_ID` 환경변수를 읽는다. ID 기본값은 없으며 users 테이블에 실제 행이 있어야 한다. 실행 시 테스트 사용자를 자동 INSERT하지 않는다.
- 임시 모드는 모든 인증된 요청을 설정된 한 사용자로 취급한다. 실제 다중 사용자 환경에서 사용하면 안 된다. 기존 보안 필터를 우회하지 않으므로 현재 인증의 유효한 로그인 토큰도 필요하다.
- 인증 팀이 사용자 ID를 제공하면 실서비스용 `CurrentUserProvider` 빈을 등록하고 이 임시 설정을 제거/비활성화한다. 기본 빈은 `ConditionalOnMissingBean`으로 교체 가능하다. AI Controller/Service에 username 변환이나 Spring Security 의존성을 추가할 필요가 없다.
- 배포 환경에는 dev/test 프로필 및 임시 사용자 옵션을 설정하지 않는다. 기존 compose·JWT·BFF·보안 설정은 수정하지 않았다.

1단계 입력 오류와 미존재/타인 대화 오류는 표준 Java 예외를 사용하고 AiConversationController의 로컬 ExceptionHandler가 위 ErrorResponse 형식으로 변환한다. 별도 AI 예외 클래스·예외 처리 파일은 두지 않는다. 공통 인증/DB 오류 처리는 기존 코드를 그대로 사용한다.

AI 시간 필드는 사용자 요청으로 LocalDateTime으로 통일했다. 예: `2026-11-03T15:00:00`이며 Z/UTC 오프셋을 포함하지 않는다. Service는 서버 로컬 시각의 LocalDateTime.now()를 사용한다. DDL 초안은 TIMESTAMP WITHOUT TIME ZONE 및 LOCALTIMESTAMP 기본값으로 변경했으며 실제 DB는 변경하지 않았다. 운영 애플리케이션·DB의 기준 시간대는 팀 합의 후 일치시켜야 하며 한국 시간/UTC 중 하나로 확정한 것은 아니다.

AI Service는 `service/AiConversationService` 인터페이스와 `service/impl/AiConversationServiceImpl` 구현체로 분리했다. Controller는 인터페이스를 주입받는다. Repository는 JPA 기반이며 대화/메시지는 JpaRepository, users 존재 확인은 AiConversationRepository의 nativeQuery, 추천 연관 삭제는 EntityManager 네이티브 쿼리로 처리한다. 별도 User/추천 엔티티를 추가하지 않는다.

사용자 요청에 따라 별도 AiUserRepository를 제거했다. 개발용 users 행 존재 확인은 AiConversationRepository.existsUserById의 nativeQuery로 통합했으며 대화 존재 확인인 JpaRepository.existsById와 구분한다. 개발 Provider와 설정은 동일 Repository를 사용한다. CreateConversationRequest의 요청 필드는 title 하나이며 userId·id·createdAt·updatedAt은 서버에서 결정한다. DTO와 API 본문 필드는 이번 통합에서 변경하지 않았다.

## 팀 H2 방침과 API 검증 범위

팀은 H2를 사용하기로 했으나 적용 환경/저장 방식/공통 스키마·초기화 계약은 TODO다. 현재 기본 datasource는 PostgreSQL이며 H2 의존성은 testRuntimeOnly다. application-dev.yaml의 고정 사용자 설정은 H2 연결 설정이 아니다. 기본 실행 설정·의존성·compose 변경은 별도 팀 조율 대상이며 이번 문서 작업에서 수정하지 않았다.

1단계 테스트는 H2 PostgreSQL 호환 모드와 테스트 전용 schema.sql로 수행한다. users 및 추천/사용량 fixture는 검증용 최소 정의로 팀 운영 스키마와 다를 수 있다. 따라서 API/권한/삭제 롤백의 테스트 결과와 실제 팀 H2 서버 기동·Postman 연동 완료를 구분해야 한다. DB 선택 변경으로 URL·DTO·소유권·하드 삭제·사용량 유지 정책은 바뀌지 않는다.

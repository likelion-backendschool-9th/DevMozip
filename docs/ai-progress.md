# AI 추천 개발 진행 현황

마지막 갱신: 2026-11-03. 새 채팅은 [루트 작업 규칙](../AGENTS.md), [AI 작업 규칙](../backend/src/main/java/com/devmozip/backend/ai/AGENTS.md), [기능 명세](ai-matching.md), [API 명세](ai-api-spec.md)와 이 문서를 읽고 실제 Git/코드를 다시 확인한다. 이 문서는 현재 구현을 기준으로 이전 반복 기록을 정리한 것이다.

## 1단계 상태

**AI 대화 CRUD·사용자 메시지 저장 API 5개 구현 완료. 격리된 H2 테스트와 실제 팀 실행 환경 검증은 구분한다.** 팀은 H2를 사용하기로 했지만 저장소의 기본 실행·배포 설정은 아직 PostgreSQL이며 팀 H2 환경 기동·스키마 일치 확인은 미완료다. 이번 요청은 결과 확인과 문서 갱신뿐이며 기능 코드·설정·DB 스키마는 수정하지 않는다.

브랜치는 `feat/ai-conversation`이다. 기존 문서 커밋은 `dce07a2`이며 현재 1단계 코드·문서가 스테이징되어 있다. 이번 문서 갱신은 작업 트리에만 반영하므로 커밋에 포함하려면 수정 문서를 다시 스테이징해야 한다. 커밋·푸시·병합·배포는 실행하지 않았다. 추천 커밋 제목: `feat(ai): AI 대화 CRUD 및 사용자 메시지 저장 구현`.

## 완료 기능

| API | 구현 및 응답 |
|---|---|
| POST `/api/v1/ai/conversations` | 제목 선택(null 허용, 최대 255자), 사용자 ID·생성/수정 시각 서버 설정, 201 및 Location |
| GET `/api/v1/ai/conversations` | 내 대화만 createdAt DESC / id DESC, 200 |
| GET `/api/v1/ai/conversations/{id}` | 내 대화·저장 조건·메시지 조회, 메시지 createdAt ASC / id ASC, 200 |
| DELETE `/api/v1/ai/conversations/{id}` | 소유권 확인 후 연관 데이터 물리 삭제, 204 |
| POST `/api/v1/ai/conversations/{id}/messages` | 공백이 아닌 content를 USER 메시지로 저장, conversation_id 연결·대화 수정 시각 갱신, 201 |

- 타인 대화와 미존재 대화는 동일한 404, 입력/ID 형식 오류는 400이다. 공통 ErrorResponse를 Controller에서 재사용하고 공통 인증 401·DB 오류 503 처리는 기존 코드를 유지한다.
- `owned()`가 현재 사용자 ID와 대화 ID를 함께 조회한다. 메시지 저장·삭제는 대화 행에 비관적 쓰기 잠금을 사용한다.
- 삭제는 추천 결과 → 추천 요청 → 메시지 → 대화 순으로 하나의 트랜잭션에서 실행한다. 대화 FK뿐 아니라 메시지 FK로 연결된 추천 요청도 포함한다. `ai_usages`는 삭제/차감/복원하지 않는다. FK 충돌 시 전체를 롤백한다.
- `type`, `status`는 엔티티에서 제거했다. 메시지 역할은 USER/ASSISTANT지만 저장 API는 USER만 생성한다. 기존 저장 ASSISTANT 메시지는 조회할 수 있다.
- 생성·수정 시각은 LocalDateTime이다. 생성 시각만 필요한 AiMessage에 updatedAt을 추가하지 않았다. 공통 BaseEntity도 만들지 않았다.
- 사용자 식별은 CurrentUserProvider로 분리했다. dev/test 프로필과 명시적 opt-in 모두 필요하고, 설정된 ID가 users에 실제 존재해야 한다. 기본/prod/production에서는 실제 구현체가 없으면 요청을 거부한다. 기존 SecurityConfig의 로그인 요구는 유지한다.
- Gemini·자동 AI 답변·요구사항 분석·조건 변경/병합·추천 생성·점수 계산·월 한도 적용·상세 분석·프론트 API 연결은 이번 단계에서 구현하지 않았다.

## 현재 구조와 변경 파일

소스 루트는 `backend/src/main/java/com/devmozip/backend/ai`다. AI 외 도메인·기존 인증·보안·배포 코드는 수정하지 않았다.

| 위치 | 변경 파일 및 역할 |
|---|---|
| AI 루트 | `AGENTS.md`: 코로케이션 3계층, 개별 DTO, Service/impl, 최소 구조 원칙 |
| AI 루트 | `CurrentUserProvider.java`, `CurrentUserConfiguration.java`, `DevelopmentCurrentUserProvider.java`: 식별 인터페이스·환경별 선택·개발 고정 ID |
| `controller/` | `AiConversationController.java`: API 5개와 로컬 입력/미존재 예외 처리 |
| `service/` | `AiConversationService.java`: 인터페이스 |
| `service/impl/` | `AiConversationServiceImpl.java`: 업무 로직·소유권·트랜잭션 |
| `repository/` | `AiConversationRepository.java`: 대화 조회/잠금·users 존재 확인 네이티브 쿼리 |
| `repository/` | `AiMessageRepository.java`: 메시지 저장·정렬 조회 |
| `repository/` | `AiConversationDeletionRepository.java`: JPA EntityManager 네이티브 연관 삭제 |
| `entity/` | `AiConversation.java`, `AiMessage.java`: ERD 기반 JPA 매핑 |
| `dto/` | `CreateConversationRequest.java`, `CreateMessageRequest.java`, `ConversationResponse.java`, `ConversationDetailResponse.java`, `MessageResponse.java`: 타입별 public record |
| `backend/src/main/resources/` | `application-dev.yaml`: AI_DEV_USER_ENABLED / AI_DEV_USER_ID 입력, ID 기본값 없음 |
| `backend/src/test/java/com/devmozip/backend/ai/` | `CurrentUserConfigurationTests.java`: 사용자 식별 설정 테스트 3개 |
| 테스트의 `ai/controller/` | `AiConversationTests.java`: MockMvc·실제 JPA Repository·H2 트랜잭션 통합 테스트 8개 |
| `backend/src/test/resources/ai/` | `schema.sql`: 격리 H2 테스트용 최소 테이블 |
| `docs/` | `ai-matching.md`, `ai-api-spec.md`, `ai-progress.md`: 확정 정책·API·진행 상태 |
| `docs/sql/` | `ai-conversations-proposal.sql`: 이전 PostgreSQL 검토용 초안, 자동 실행/적용되지 않음 |

`AiUserRepository`, AiConversationDtos 래퍼, 별도 AI 예외 클래스/Advice, identity/exception 폴더는 사용하지 않는다. 사용자 존재 확인은 `AiConversationRepository.existsUserById`이며 기본 `existsById`의 대화 존재 확인과 구분한다. User/추천 엔티티는 타 팀 영역이므로 추가하지 않았다. 테스트 데이터 준비/검증의 JdbcTemplate는 유지하되 AI 업무 코드는 JPA를 사용한다.

## 확정 정책 반영

- 하나의 대화에서 개발자·유사 프로젝트를 함께 추천하므로 대화 type을 쓰지 않는다. MVP 종료/보관 기능이 없어 status도 쓰지 않는다.
- 물리 삭제와 연관 추천 삭제, 월 사용량 유지가 확정되었다. 실제 저장소에 FK가 다르거나 추가 관계가 있으면 먼저 보고하고 정책과 맞춰야 한다.
- DTO는 타입별 개별 파일, Service는 인터페이스와 impl 구현체, 불필요한 파일·폴더·추상화는 추가하지 않는다. 별도 예외 처리 파일도 만들지 않는다.
- 시간은 LocalDateTime이며 서버/DB 기준 시간대는 팀 합의가 필요하다. 과거 Instant 기반 설계를 현재 정책으로 사용하지 않는다.
- 최신 사용자 안내로 H2 사용 방침을 반영했다. 개발/운영 적용 범위·in-memory/file 방식·팀 공통 초기화 스크립트·시간대는 아직 TODO다. 이전 PostgreSQL 전제를 H2 실행 완료로 바꾸어 기록하지 않는다.
- 무료 15회·프리미엄 100회, 추천 신규 생성 성공에만 1회 차감, 메시지/조건 변경/기존 추천 추가 분석 미차감 정책은 그대로다. 이번 단계에는 추천/한도 기능을 구현하지 않았다.

## 이번 검증

2026-11-03 현재 코드로 실제 재실행했다. 기능 코드·테스트 코드·설정은 변경하지 않았다.

| 검증 | 명령 / 대상 | 결과 |
|---|---|---|
| 백엔드 컴파일·전체 테스트 | backend에서 `./gradlew test --no-daemon --rerun-tasks` | 성공, 5개 작업 실제 실행, UP-TO-DATE 재사용 아님 |
| 기존 인증 회귀 | BackendApplicationTests | 10개 통과 |
| AI API·JPA/H2 통합 | AiConversationTests | 8개 통과 |
| 사용자 식별 설정 | CurrentUserConfigurationTests | 3개 통과 |
| 합계 | JUnit XML 보고서 확인 | 21개, 실패 0 / 오류 0 / 스킵 0 |
| 프론트 빌드 | frontend에서 Linux Node 24.22.1로 `npm run build` | 실패: 컴파일 후 `Could not parse output from TypeScript's --showConfig.`(종료 코드 1) |
| 문서·변경 범위 | 로컬 Markdown 링크, `git diff --check`, 작업 전후 비문서 파일 해시 | 통과, 기능 코드·기존 설정·Git 스테이징 상태 보존 |

AI 테스트 범위는 생성/목록/상세, 시간 및 ID 정렬, 메시지 저장/연결, 기존 조건 JSON·ASSISTANT 조회, 자동 AI 답변 미생성, 타인 조회/저장/삭제 차단, 미존재/잘못된 ID, 잘못된 제목/빈 메시지/역할 위조, 익명 및 설정 사용자 부재, 연관 추천 삭제, 타인 데이터·월 사용량 유지, FK 충돌 시 전체 롤백이다. 설정 테스트는 dev/test opt-in, 기본/prod/production 거부(프로필 혼용 포함), 실제 인증 어댑터 빈 교체를 확인한다.

테스트의 users/추천/사용량은 H2 fixture이며 Redis 토큰 저장소는 mock이다. 프론트 기능 테스트, 팀 공통 H2 서버 기동/Postman, 실제 인증 사용자 A/B 및 운영 검증은 이번에 실행하지 않았다. 프론트 빌드가 생성한 next-env.d.ts는 실행 직전 내용으로 보존했다. 결과 XML 위치는 `backend/build/test-results/test/`이며 생성 산출물은 커밋 대상이 아니다.

## 미해결 문제

1. **H2 실행 환경 미연결:** 기본 application.yaml은 PostgreSQL이고 H2 의존성은 testRuntimeOnly다. application-dev.yaml은 AI 사용자 옵션만 설정한다. dev 프로필만 켠다고 H2로 바뀌지 않는다. 팀 공통 DB 설정/의존성 수정은 이번 문서 요청에서 하지 않았다.
2. **팀 스키마 미확인:** 테스트는 `jdbc:h2:mem:ai-crud;MODE=PostgreSQL`과 최소 fixture를 사용한다. 팀 H2의 모드·NULL·IDENTITY·JSON·FK·실제 컬럼명·초기화 방식과의 일치는 아직 미검증이다. 테스트의 최소 users/추천/사용량 테이블을 팀 공통 스키마로 사용하지 않는다.
3. **삭제 시 추천 테이블 필요:** 현재 네이티브 쿼리는 ai_recommendation_requests의 ai_conversation_id/ai_message_id, ai_recommendations의 recommended_request_id를 참조한다. 테이블/컬럼이 없으면 삭제는 503 및 전체 롤백으로 실패한다. 이를 숨기거나 AI 두 테이블만으로 삭제 준비가 끝났다고 보고하지 않는다.
4. **인증은 임시:** 테스트 ID가 실제 users 행이어야 한다. 모든 로그인 요청이 한 고정 ID로 매핑되므로 로그인 계정만 바꿔 다중 사용자 권한을 검증할 수 없다. 실제 인증 어댑터로 교체해야 한다.
5. **프론트 빌드:** 기존 TypeScript --showConfig 파싱 실패가 남아 있다. AI 기능과 무관한 코드를 이번 작업에서 수정하지 않았다.
6. **실행 확인 미완료:** 팀 H2 환경의 서버 기동/Postman 검증, 실제 사용자 A/B 인증, 동시 요청 부하 검증 및 운영 검증은 수행하지 않았다.

## 다음 단계에서 주의할 점

- 다음 단계는 자동 시작하지 않는다. 첫 후속 작업은 팀 H2 사용 범위·연결 설정·스키마 제공자 확인이다. 공통 DB·인증·배포 변경이 필요하면 이유와 최소 변경 범위를 먼저 설명한다.
- PostgreSQL 초안은 이전 설계 참고용이다. 자동 실행 스크립트나 H2 확정 DDL로 옮기지 않는다. 포함해서 커밋해도 스키마 적용 승인이 아니다.
- 테스트 users를 실제 환경에 자동 INSERT하지 않는다. dev 환경에서만 AI_DEV_USER_ENABLED=true, AI_DEV_USER_ID를 실제 ID로 설정한다. 기존 유효 로그인 토큰도 필요하다. 운영에 dev/test·고정 사용자 옵션을 켜지 않는다.
- 인증 팀이 실제 CurrentUserProvider 빈을 제공하면 임시 구현을 비활성화하고, users 매핑과 사용자 A/B의 조회·메시지·삭제 소유권을 재검증한다. Controller/Service 인터페이스에 사용자 ID 요청 파라미터를 추가하지 않는다.
- 연관 추천 스키마가 준비되지 않았다면 삭제 정책을 임의로 생략하지 않는다. FK 순서·트랜잭션 롤백과 월 사용량 유지 테스트를 보존한다.
- 공통 BaseEntity는 팀 합의 전 도입하지 않는다. 생성 시각만 필요한 메시지에 수정 시각을 임의로 추가하지 않는다.
- 추천·조건·AI·월 한도 API는 별도 단계 요청 후 필요한 정책만 구체화한다. 외부 AI·결제·OAuth·WebSocket을 미리 연결하지 않는다.

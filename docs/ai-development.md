# AI 개발 작업 규칙

AI 관련 파일에만 적용하는 개발 안내다. 자동 적용되는 AGENTS.md가 아니며, 루트 AGENTS.md에서 필독으로 안내하는 ai-progress.md를 통해 참조한다. 다른 팀원 도메인의 작업 규칙은 변경하지 않는다.

- 2026-11-04 사용자 요청에 따라 AI Controller·Service·Repository·Entity는 backend 공통 계층 폴더, DTO는 `dto/ai`에 둔다. 사용자 식별 클래스는 `security/ai`에 둔다.
- `controller`는 HTTP 입출력, `service`는 업무 정책·트랜잭션, `repository`는 JPA 영속성 접근을 담당한다.
- Service는 `service`의 인터페이스와 `service/impl`의 `*ServiceImpl` 구현체로 분리한다. Controller는 인터페이스에 의존하고 트랜잭션은 구현체에 둔다.
- Repository는 JPA를 우선 사용한다. 엔티티가 있는 테이블은 JpaRepository를, 다른 팀 엔티티가 없거나 삭제 전용인 기존 테이블은 JPA 네이티브 쿼리를 사용한다. 개발용 users 존재 확인은 AiConversationRepository.existsUserById에 두며 별도 AiUserRepository를 만들지 않는다. AI 업무 코드에 JdbcTemplate를 혼용하거나 이를 위해 타 팀 엔티티를 만들지 않는다.
- DTO는 `dto` 안에 타입별 public record/class를 각각 별도 Java 파일로 작성한다. 요청·응답 DTO를 하나의 래퍼 클래스 안에 중첩하거나 묶지 않는다. 별도 request/response 하위 폴더는 필요할 때만 추가한다.
- DTO는 `com.devmozip.backend.dto.ai`, 엔티티는 `com.devmozip.backend.entity`에 둔다. 사용자 식별 클래스는 `security/ai`에 함께 두고 `identity`, `exception` 폴더를 따로 만들지 않는다. 사용자 식별 교체 지점은 `CurrentUserProvider`다.
- 앞으로 폴더·파일·추상화는 현재 요구에 꼭 필요한 경우에만 추가한다. 인증 교체는 기존 인터페이스로 유지하며, 미래 기능을 이유로 구조를 미리 나누지 않는다.
- AI 전용 예외 클래스·예외 처리 파일은 별도로 만들지 않는다. 단순 검증·미존재 처리는 표준 예외와 해당 Controller의 로컬 처리로 해결하고 기존 공통 오류 응답·인증·DB 예외 처리를 재사용한다.
- AI 엔티티·DTO의 생성/수정 시각은 LocalDateTime을 사용한다. 공통 BaseEntity는 팀 합의 전 별도로 추가하지 않는다.
- 테스트는 `backend/src/test/java/com/devmozip/backend/ai` 아래에서 대응하는 패키지에 둔다.
- AI와 관련 없는 도메인·기존 인증·보안·배포·프론트 코드는 수정하지 않는다. 변경이 반드시 필요하면 이유와 최소 변경 범위를 먼저 설명하고 요청 범위 안에서만 작업한다.
- 공통 타입은 기존 것을 참조할 수 있으나, 패키지 정리를 이유로 공통 코드를 이동하거나 수정하지 않는다.
- 기능 범위·정책·검증 기록은 저장소 루트의 `docs/ai-matching.md`, `docs/ai-api-spec.md`, `docs/ai-progress.md`를 따른다. 이 구조 정리는 다음 단계 구현 승인이 아니다.

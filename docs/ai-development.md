# AI 개발 작업 규칙

AI 관련 파일에만 적용하는 개발 안내다. 자동 적용되는 AGENTS.md가 아니며, [개발 청사진·진행 현황](ai-progress.md)을 통해 참조한다. 전체 단계와 현재 1단계의 남은 요구사항은 청사진에서 관리하고, 이 문서는 코드 배치·구현 원칙만 유지한다. 다른 팀원 도메인의 작업 규칙은 변경하지 않는다.

- AI 담당 범위의 설계 원칙(2026-11-04): 아래 원칙은 AI 관련 구현에만 적용한다. 다른 팀원 영역의 규칙이나 코드를 일괄 변경하지 않으며, 새 채팅에서도 이 문서를 참고한다.
- AI의 PostgreSQL 구조화 데이터 저장 타입은 `json` 대신 `jsonb`를 사용한다. API의 JSON 형식과 DB 저장 타입을 구분한다. 실제 매핑·DDL·로컬 H2 호환성은 구현 전 확인하고 필요한 부분만 수정한다.
- AI 코드에서 setter는 작성·사용하지 않는다. Lombok `@Setter`와 setter를 생성하는 `@Data`도 사용하지 않는다. 생성은 생성자·팩터리·빌더로, 상태 변경은 업무 의도와 검증을 담은 메서드로 처리한다.
- 불필요한 코드·파일·추상화·의존성·미래 기능 추가를 지양한다. 기존 구현을 재사용하고 요청한 AI 기능에 필요한 최소한의 코드만 수정한다. 관련 없는 리팩터링·포맷 변경·기존 코드/DDL 일괄 정리는 하지 않는다.
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
- AI 담당 영역 밖의 다른 팀원 도메인·공통 인증·보안·배포·프론트 코드는 임의로 수정하지 않는다. 요청된 AI 구현을 위해 반드시 필요한 경우에만 이유·영향·최소 변경 범위를 먼저 설명하고 기록한 뒤, 사용자 요청 범위 안에서 최소한으로 수정한다.
- 공통 타입은 기존 것을 참조할 수 있으나, 패키지 정리를 이유로 공통 코드를 이동하거나 수정하지 않는다.
- 기능 범위·진행·검증은 [청사진](ai-progress.md), 공통 정책은 [기능 정책](ai-matching.md), 서버 계약은 [API 명세](ai-api-spec.md)를 따른다. 예정 단계와 첨부 프롬프트는 실행 지시가 아니며 다음 구현은 사용자의 별도 요청을 기다린다.

### 최신 패키지 지침 (2026-11-04)

사용자 지시에 따라 AGENTS.md의 기존 `auth` 패키지 언급은 이번 AI 작업에서 적용하지 않는다. 위의 실제 공통 계층 구조와 dto/ai·security/ai 배치를 따른다. 공통 인증 코드를 이동하거나 개편하지 않는다.

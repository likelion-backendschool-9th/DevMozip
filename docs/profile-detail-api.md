# 사용자 프로필 기본 조회 API (#21)

## 범위

`GET /api/v1/users/{slug}`는 프로필 상단의 기본 정보를 조회한다.
`slug`는 `users.tag` 원문이며, 화면 표시용 `@`는 포함하지 않는다.
프로필 수정·프론트 API 연결·실제 DB 연결·인증 서버·외부 서비스는 이번 범위에 포함하지 않는다.
ERD는 수정하지 않았다.

## 응답

성공 시 HTTP 200과 다음 JSON을 반환한다. 공통 응답 래퍼는 아직 도입하지 않았다.

```json
{
  "id": 1,
  "tag": "dev-tag",
  "nickname": "개발자",
  "jobCategory": { "id": 2, "name": "백엔드 개발자" },
  "summary": "함께 성장할 팀을 찾습니다.",
  "career": 18,
  "contactHours": "평일 19:00~22:00 · 주말 협의",
  "profileImage": null,
  "bannerImage": null,
  "collaborationStatus": null,
  "trustScore": 36.50
}
```

`summary`, 사진·배너, 협업 상태는 NULL을 허용한다.
`career`는 users.career(INT)에 저장된 개월 수를 반환하며 18은 18개월, 0은 신입, NULL은 미입력이다.
경력 표시 문구 변환은 프론트에서 수행한다. 경력 저장·수정 및 음수 입력 검증은 이번 조회 API 범위에 포함하지 않는다.
실제 PostgreSQL에도 career 컬럼을 별도 적용해야 하며, 이 작업에서 운영 스키마를 변경하지 않는다. 이미지 필드는 저장된 경로를 반환한다.
이메일·비밀번호 해시·카카오 ID·결제 정보는 응답 DTO에 포함하지 않는다.
직군은 현재 ERD의 단일 `job_category_id` 관계를 따른다.
가입일·소개 본문·관심 분야·팔로우 수·PRO 표시는 이 API의 응답에 포함하지 않는다.
포트폴리오·게시글·활동·리뷰는 별도 API 범위다.

## 미존재 및 비공개 처리

임시 정책으로 `status = ACTIVE` 사용자만 공개 조회한다.
없는 사용자, 현재 Member 상태의 WARNED, SUSPENDED는 모두 동일한 HTTP 404를 반환한다.
기존 LOCKED 데이터가 있다면 회원 상태 모델과 통합하기 전 데이터 정책을 확인해야 한다.
계정 잠금과 프로필 비공개를 별도로 관리할지는 팀에서 확정해야 한다.
현재 ERD에는 별도 프로필 공개 필드가 없어 이를 새로 만들지 않았다.

```json
{
  "type": "about:blank",
  "title": "프로필 조회 실패",
  "status": 404,
  "detail": "프로필을 찾을 수 없습니다.",
  "instance": "/api/v1/users/missing",
  "code": "PROFILE_NOT_FOUND"
}
```

## 구현 구조

- `controller/ProfileController`: 요청 경로와 응답 처리
- `service/ProfileService`: 읽기 전용 트랜잭션, 공개 대상 조회와 DTO 변환
- `repository/MemberRepository`: 태그·ACTIVE 상태 조회, JobCategoryRepository로 직군 조회
- `entity/Member`, `entity/JobCategory`: 기존 테이블의 조회용 컬럼 매핑
- `dto/profile/ProfileDetailResponse`: 공개 데이터만 응답
- `controller/ProfileExceptionHandler`: 프로필 Controller의 오류를 ProblemDetail로 응답
- `auth/SecurityConfig`: 팀의 기존 JWT 설정에 프로필 GET 공개 조건만 추가. 다른 경로의 JWT 인증·권한 정책 유지

프로필 수정 작업에서 인증 담당의 Member Entity로 통합했다. User/UserRepository는 제거했다.
협업 상태는 Member.CollaborationStatus ENUM의 이름을 문자열로 응답한다.
직군 ID가 없거나 실제 직군이 없으면 프로필 조회는 404로 처리한다.
태그 UNIQUE는 ERD에 주석으로만 존재하므로 실제 DB 제약은 DB 담당이 확인해야 한다.
스키마 자동 생성·변경 설정 및 마이그레이션은 추가하지 않았다.

## 실행과 검증

Java 21에서 `cd backend && ./gradlew test build --no-daemon`으로 검증한다.
서비스 단위 테스트와 팀의 JWT 보안 필터를 포함한 MockMvc HTTP 테스트를 제공한다.
최신 develop의 기존 인증 통합 테스트(H2 메모리 DB, Redis 저장소 대역)는 보존했다.
서비스 단위 테스트와 MockMvc 테스트는 테스트 대역을 사용한다.
`ProfileHttpIntegrationTest`는 실제 HTTP 서버·JPA Repository·H2 메모리 DB로 조회한다.
PostgreSQL 고유 타입·제약·실제 스키마 호환성은 별도 통합 검증이 필요하다.

최신 develop의 PostgreSQL·Redis·JWT 환경 설정을 사용한다. 이 작업에서는 실제 외부 서버에 연결하지 않았다.
팀에서 DB 스키마와 실행 환경 변수를 준비한 이후 프로필 조회의 영속성 통합 검증을 진행해야 한다.
테스트용 Mock 데이터를 운영 API에서 반환하는 대체 로직은 추가하지 않았다.
프론트는 기존 Mock 상태를 그대로 사용한다.

## 로컬 HTTP 동작 검증 (2026-11-03)

```bash
cd backend
./gradlew test --tests '*ProfileHttpIntegrationTest' --no-daemon
```

- 실제 Spring Boot 서버를 127.0.0.1 임의 포트로 구동한다.
- 독립적인 H2 메모리 DB에 직군·ACTIVE·WARNED·SUSPENDED 테스트 사용자를 넣는다.
- 경력 18개월·0개월·NULL이 실제 DB 조회 후 JSON에서도 구분되는지 확인한다.
- Java HttpClient로 HTTP 요청하며 Controller·Service·Repository를 대역으로 바꾸지 않는다.
- ACTIVE 사용자: 200, 저장한 프로필·직군·신뢰 온도와 공개 응답 필드 일치.
- 선택 정보가 NULL인 ACTIVE 사용자: 200, NULL 유지.
- 미존재·WARNED·SUSPENDED 사용자: 동일한 404 및 PROFILE_NOT_FOUND 오류.
- 보호 경로 비로그인 요청: 401.
- 총 7개 HTTP 통합 검사를 제공한다. 테스트 종료 시 임시 서버와 메모리 DB를 정리한다.
- 운영 PostgreSQL·Redis 및 프론트엔드 연동은 이 검사에 포함하지 않는다.

연락 가능 시간은 contactHours로 응답한다. 최대 100자 자유 입력이며 미등록이면 NULL이다.
저장 정책과 검증은 docs/profile-contact-hours.md를 참고한다.

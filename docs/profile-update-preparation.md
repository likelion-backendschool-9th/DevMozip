# 본인 프로필 기본 정보 수정

## 인증 흐름

PUT /api/v1/users/me/profile → 기존 JWT Security 필터 검증 →
ProfileUpdateController의 Authentication.getName() → 인증된 이메일 →
ProfileUpdateService의 기존 MemberRepository.findByEmail(email) → 본인 Member 수정.

추가 BFF 인증 또는 재로그인을 구현하지 않았다. username/role 인증 응답·로그인·로그아웃·토큰 코드는 변경하지 않았다.
현재 브랜치의 JWT 필터와 확정된 이메일 Principal 계약을 기준으로 연결했다.
실제 인증 담당 브랜치 전체를 병합한 것은 아니므로 그 브랜치와의 최종 통합 회귀 검증은 필요하다.

## 공유 회원 모델

원격 feat/member-auth의 a5d1e63에서 Member와 MemberRepository를 확인하여 재사용했다.
이 버전에는 /api/v1/users/me 로그인 확인만 있고, 이전 버전의 프로필 수정 API와 MemberService는 제거되어 있다.
따라서 현재 브랜치에서 ProfileUpdateController와 ProfileUpdateService를 구현했다.
기존 User/UserRepository는 제거하고 공개 조회도 동일한 Member를 사용한다.
Member는 직군 ID를 보관하며 별도 JobCategoryRepository로 직군 정보와 존재 여부를 확인한다.
회원이 직군을 설정하지 않았거나 실제 직군이 없으면 공개 조회는 404로 처리한다.
조회 응답 JSON 필드 이름은 유지한다.
Member 상태는 ACTIVE/WARNED/SUSPENDED이며, 기존 공개 조회 정책대로 ACTIVE만 공개한다.
수정은 ACTIVE/WARNED만 허용하고 SUSPENDED와 미확정 상태는 403 처리한다.
기존 LOCKED 문자열에 대한 데이터 정책은 실제 DB에 해당 값이 있다면 통합 전 정리해야 한다.

## 요청

```json
{
  "nickname": "개발자",
  "tag": "developer-tag",
  "jobCategoryId": 1,
  "summary": "함께 개발해요",
  "career": 18,
  "collaborationStatus": "AVAILABLE"
}
```

- nickname: 앞뒤 공백 제거, 한글 완성형·영문·숫자·공백·밑줄 2~20자, 필수.
- tag: 앞뒤 공백 제거, 영문·숫자·밑줄·하이픈 2~30자, 필수. me는 대소문자와 무관하게 예약어.
- jobCategoryId: 양수·필수, 실제 직군 존재 검증.
- summary: 앞뒤 공백 제거, 최대 150자. NULL/누락/빈 문자열로 비울 수 있다.
- career: 개월 수. 0=신입, NULL/누락=미입력. 음수 거부.
- collaborationStatus: AVAILABLE/RESTING/BUSY/OPEN_TO_OFFERS/UNAVAILABLE 또는 NULL. 누락/NULL은 비운다.

전체 수정 PUT 계약이다. nickname/tag/jobCategoryId가 빠지면 400, 선택 항목 누락은 기존 값 유지가 아니라 비우기다.
협업 상태를 기본 수정에 포함했다. 추후 별도 availability API를 도입하면 같은 필드 정책으로 맞춰야 한다.
요청의 사용자 ID/이메일은 수정 대상을 선택하는 데 쓰지 않는다.
권한·계정 상태·비밀번호·신뢰 온도·사진·배너·외부 링크는 수정하지 않는다.
프론트 수정 창과 파일 업로드는 이 작업 범위가 아니다.

## 검증과 저장

Bean Validation과 ProfileUpdateValidator로 Controller와 Service 호출 시 모두 입력을 검증한다.
타인 닉네임/태그는 대소문자 무시 중복 검사하며, 본인 값은 허용한다.
이메일·닉네임·태그의 정확한 값 중복은 Member의 UNIQUE 매핑으로도 보호한다.
이 매핑은 실제 PostgreSQL 스키마 변경을 수행한 것이 아니다.
대소문자 무시 정책을 동시 수정에서도 보장하려면 실제 DB의 lower(...) UNIQUE 인덱스 또는 동등한 정책을 적용해야 한다.
실제 DB 제약/기존 중복 데이터 정리는 담당자와 통합 전 확인한다.

조회는 readOnly 트랜잭션, 수정은 쓰기 트랜잭션과 JPA dirty checking을 사용한다.
Service의 변경 필드만 허용 목록으로 대입하며 Entity 전체를 요청에서 바인딩하지 않는다.
검증 실패는 400, 중복·저장 제약 위반은 409, 없는 회원은 404, 수정 불가 상태는 403이다.
오류는 프로필 전용 ProblemDetail로 반환하며 SQL 및 내부 정보를 응답에 포함하지 않는다.
GET /users/me는 공개 wildcard보다 먼저 인증을 요구하도록 보안 규칙을 명시한다.
/users/me의 username/role 응답은 인증 담당 구현을 유지하며 이 브랜치에서 별도 Controller를 중복 추가하지 않는다.

## 테스트

ProfileUpdateValidatorTest: 실제 Bean Validation으로 형식·길이·예약어·경력·상태 검증.
ProfileUpdateIntegrationTest: 기존 JWT 검증 필터 → Controller → Service → 실제 Repository → H2.
실제 서명 토큰의 이메일로 본인을 조회하고 저장한 뒤 새 Repository 조회와 공개 GET으로 확인한다.
0/NULL 경력 구분, 본인 이름/태그 유지, 타인 중복, 입력 실패 시 미변경, 위조 토큰/비로그인 차단,
미존재 이메일 404, 정지 계정 403, 요청 본문으로 타인/권한/온도 수정 불가를 확인한다.
테스트 전용 H2 CHECK 제약으로 커밋 실패를 유발하여 모든 변경의 롤백을 확인한다.
실제 PostgreSQL·Redis·운영 인증 서버에 연결하지 않는다.

## 변경 파일

- build.gradle: Validation 의존성
- dto/profile/ProfileUpdateRequest: 수정 요청 record
- service/ProfileUpdateValidator, ProfileUpdateService: 검증과 본인 수정
- controller/ProfileUpdateController, ProfileExceptionHandler: 요청과 오류 응답
- entity/Member, repository/MemberRepository: 인증 담당의 공유 회원 모델과 기존 findByEmail 재사용
- repository/JobCategoryRepository: 직군 확인
- service/ProfileService, dto/profile/ProfileDetailResponse: Member 기반 공개 조회로 통합
- auth/SecurityConfig: me 정적 경로의 인증 우선 처리
- 관련 단위·통합 테스트와 본 문서

현재 브랜치 feat/profile-update에서 작업했다. 커밋·Push는 별도 요청 전까지 하지 않는다.

## 최종 검증

./gradlew build --no-daemon 성공. 전체 85개 테스트 통과(수정 통합 19개, 입력 검증 37개 포함).
프론트 npm run build 성공. 실제 PostgreSQL 및 인증 담당 브랜치 전체 병합 후 검증은 남아 있다.

## 제공된 로컬 설정 검토 (2026-11-04)

사용자가 제공한 backend_resources/application-local.yml을 병합 예정 설정으로 검토했다.
비밀 값은 문서에 복사하지 않고, 원본 설정과 초기 데이터 파일도 저장소에 추가하지 않았다.

- H2 메모리 DB와 H2 Console을 사용한다.
- ddl-auto=update이며 JPA 초기화 후 classpath*:*_data.sql을 실행한다.
- 현재 브랜치는 H2가 testRuntimeOnly여서 일반 로컬 실행 시에는 병합 예정 빌드의 runtimeOnly H2 설정도 필요하다.
- 현재 브랜치의 임시 인증에는 bootstrap 비밀번호 환경 설정이 필요하다. 제공된 local 파일만으로 실제 회원 인증 코드까지 대체되지는 않는다.
- application-local.yml은 local 프로필을 명시적으로 활성화해야 사용된다. 공통 application.yaml 설정과 인증 코드도 같이 확인한다.
- 같은 폴더 members_data.sql의 사용자 INSERT에는 job_category_id가 없으며 job_categories 초기 데이터도 없다.
  현재 공개 조회는 직군 ID/직군이 없으면 404이므로, 실제 공개 프로필을 검증하려면 직군 초기 데이터와 회원 연결이 필요하다.
  임의 직군을 자동 배정하거나 회원 조회 정책을 몰래 바꾸지 않았다.
- 현재 수정 DTO는 실제 존재하는 직군 ID를 필수로 요구한다. 직군 ID가 준비되어야 수정 성공 검증이 가능하다.
- *.yml은 현재 Git ignore 대상이다. 개인 설정을 강제로 커밋하지 않으며, 팀에서 관리할 설정인지 병합 시 확정한다.

이 검토에서는 제공된 설정으로 서버를 실행하거나 실제 DB에 연결하지 않았다.
기존 H2 테스트 85개 통과 기록과 제공 파일을 적용한 로컬 실행 검증은 구분한다.


## 최신 인증 코드 통합

- `origin/develop`의 인증 패키지(`config`, `security.jwt`, `security.handler`)를 기준으로 프로필 기능을 통합했다.
- `Member`의 공통 Setter를 추가하지 않고 프로필·연락 시간·이미지 변경 메서드를 사용한다.
- `GET /api/v1/users/me`는 인증이 필요하며 `username`, `role` 응답을 유지한다. 공개 프로필·이미지 GET만 비로그인 접근을 허용한다.
- 실제 로그인 → 액세스 토큰 → 내 정보 확인 → 프로필 수정 → 공개 조회를 H2와 실제 보안 필터로 검증했다. 해당 테스트의 리프레시 토큰 저장소는 Mockito 대역이며 Redis 연결은 검증하지 않는다.
- 통합 후 백엔드 테스트 116개 통과, Docker가 필요한 PostgreSQL 컨텍스트 테스트 1개 건너뜀. 백엔드 및 프론트엔드 빌드 통과.
- 실제 PostgreSQL 스키마 반영과 프론트엔드 API 연동 브랜치 통합은 별도 작업이다.

# 프로젝트 생성 API — #44

> 2026-11-05 후속 변경: 기본 프로젝트 사용자 공급을 실제 JWT 이메일→users.id로 연결했다. 과거 개발용 고정 ID/실회원 미연결 설명은 당시 기록이며 현재 동작은 [실제 회원 ID 연결](project-authenticated-user.md)을 따른다. 프론트 API 활성화·백엔드 프리미엄 인가는 별도다.


2026-11-04. 기존 controller / service / service/impl / repository / entity / dto 구조를 따른다. 응답은 record이며 기존 회원·인증·AI 코드는 변경하지 않는다.

## 범위

| API | 결과 |
|---|---|
| POST /api/v1/projects | 201, ProjectResponse와 Location 헤더 |
| GET /api/v1/projects/options | 200, 직군 코드·표시명 및 기본 기술·분야 후보 |

프로젝트 조회·수정·삭제·지원 및 프론트 API 연결은 후속 이슈다. Location은 후속 상세 조회 경로이며 이번 단계에서 GET 상세를 구현한 것은 아니다.

## 구조와 저장

- `controller/ProjectController`: HTTP 입출력, 기존 GlobalExceptionHandler의 ErrorResponse 재사용.
- `service/ProjectService`, `service/impl/ProjectServiceImpl`: 검증·날짜 비교·생성 트랜잭션.
- `entity/Project`, `ProjectMember`, `BaseEntity`: 프로젝트와 최소 HOST 관계. BaseEntity는 별도 테이블 없이 생성·수정 LocalDateTime을 상속한다. 현 단계는 생성만 구현하며 향후 변경 서비스에서 touch를 사용한다.
- `entity/ProjectRecruitmentSlot`: JSONB 원소용 record. 별도 모집 직군 테이블을 만들지 않는다.
- `repository/ProjectRepository`, `ProjectMemberRepository`: JPA 저장.
- `dto/project`: 생성 요청·생성 응답·선택지 응답.
- `security/project`: 개발 사용자 공급과 서울 기준 Clock. AI 사용자 공급과 분리한다.

청사진의 projects, project_members는 기존 코드에 없었으며 #44에 필요한 두 엔티티를 추가했다. 외부 users FK·회원 조회·구독 검사는 결합하지 않는다. 기존 ERD export와 청사진의 변경 사항을 기준으로 했으며 최신 ERDCloud 재다운로드/실제 팀 DB 대조는 수행하지 않았다. 공통 BaseEntity도 기존 코드에 없어 추가했으며 다른 팀 엔티티의 상속 구조는 바꾸지 않았다.

## 입력

```json
{
  "title": "DevGarden",
  "summary": "코드 리뷰 커뮤니티",
  "description": "코드 리뷰와 회고를 나누는 프로젝트입니다.",
  "goal": null,
  "imageUrl": "https://example.com/projects/devgarden.webp",
  "isPublic": true,
  "recruitmentSlots": [
    {"roleCode": "FRONTEND", "capacity": 2, "minimumYears": 0},
    {"roleCode": "BACKEND", "capacity": 1, "minimumYears": 1}
  ],
  "techStacks": ["React", "Spring Boot", "직접 입력 기술"],
  "interests": ["커뮤니티"],
  "recruitmentStart": "2026-11-04",
  "recruitmentEnd": "2026-11-30",
  "developmentStart": "2026-12-01",
  "developmentEnd": "2027-02-28"
}
```

- title·description·imageUrl 필수. title/summary 최대 255자, imageUrl 최대 2048자. 선택 summary/goal은 null 허용. 상세·목표 최대 길이는 정책 미확정으로 별도 제한을 추가하지 않았다.
- 이미지 1장 HTTP(S) URL만 허용한다. URL을 서버에서 다운로드하거나 파일 업로드하지 않는다. isPublic 누락은 거절한다.
- 직군은 ProjectJobRole의 코드 중 중복 없이 선택한다. 직군별 정원 1~10, 최소 경력 0~5. 주최자는 role=HOST/job_role=NULL이며 전체 정원은 1+모집 정원 합이다.
- techStacks/interests 각각 최대 5개. 배열 필수, 빈 배열은 허용한다. 최소 1개 강제는 미확정이므로 추가하지 않았다. 직접 입력 문자열은 NFKC·앞뒤 공백 정리 후 1~30자, 대소문자 무시 중복을 거절한다. 회원 enum은 기본 후보로만 재사용하고 프로젝트 직접 입력을 제한하지 않는다.
- 모집 시작≤모집 종료, 개발 시작≤개발 종료, 모집 종료≤개발 종료. 생성 시 서울 기준 오늘보다 지난 모집 마감은 거절하며 오늘은 허용한다. 모집·개발 기간의 중첩은 별도 금지하지 않는다.
- 주최자 ID·초기 상태(RECRUITING)는 서버가 결정한다. body의 hostMemberId/status를 신원·상태 지정에 사용하지 않는다.
- 프로젝트 및 HOST 등록을 하나의 트랜잭션으로 저장한다. 호스트 저장 실패 시 프로젝트도 롤백한다.

## 독립 개발 실행

기존 Spring Security 정책에 따라 두 API 모두 인증이 필요하다. 프로젝트 업무용 주최자 ID는 인증 이메일과 연결하기 전인 개발 경계이며 실제 회원 소유권·프리미엄 인가를 검증한 API가 아니다. 운영 환경에서는 개발 공급을 활성화할 수 없고 실제 어댑터 결합 전 생성 요청은 401로 차단한다.

local / dev / test 프로필에서 명시적으로 다음 속성을 설정한다. 기존 DB·JWT 환경 설정은 그대로 필요하다. 공통 인증·배포 설정은 수정하지 않는다.

```sh
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev --project.current-user.development-enabled=true --project.current-user.test-user-id=101'
```

기존 JWT로 인증하더라도 개발 모드에서는 고정 ID 101이 주최자로 기록된다. 운영 데이터나 여러 사용자가 공유하는 환경에서 사용하지 않는다. prod/production이 함께 활성화되거나 옵트인/양수 ID가 없으면 생성은 차단된다.

공개/비공개 값은 저장한다. 비로그인 공개 조회 및 비공개 현재 팀원 조회 검증은 #45에서 구현한다. 프론트 프로젝트 기능은 기존 Mock으로 유지한다. 프리미엄 제한은 구독 결합 단계다.

## 이번 실행 검증

- backend `./gradlew compileJava --no-daemon`: 성공.
- backend `./gradlew test --no-daemon`: 최종 199개 통과, 실패·오류·스킵 0. 신규 프로젝트 테스트 19개 포함. PostgreSQL 17 Testcontainers에서 JSONB 저장·JPA 재조회, HOST 생성, 롤백, 입력 오류, KST 오늘/어제 경계를 확인했다. 개발 프로필 옵트인·운영 프로필 차단 테스트도 통과했다.
- frontend `npm test`: 48개 파일·525개 통과.
- frontend `npm run build`: 성공, TypeScript 검사 포함. 빌드가 변경한 next-env.d.ts 경로는 기존 상태로 복구했다. 프론트 업무 코드는 변경하지 않았다.
- `git diff --check`: 통과.
- 실제 팀 PostgreSQL DB·이미지 저장소·회원 및 구독 실연동, 프론트 생성 폼의 실제 API 호출은 검증하지 않았다. 이번 범위의 자동 테스트는 독립 테스트 DB/개발 사용자 경계를 사용한다.
- 커밋·푸시·PR·이슈 상태 변경은 수행하지 않았다. 다음 단계는 사용자 요청 후 진행한다.

## #44 팀 DB 연결 검증 — 2026-11-05

사용자 요청으로 실제 팀 PostgreSQL의 프로젝트 생성 저장을 검증했다. 기존 생성 코드는 JPA를 사용하므로 제품 저장 로직을 별도로 교체하지 않았다.

### 추가 파일과 실행

`backend/src/test/java/com/devmozip/backend/project/ProjectTeamDatabaseIntegrationTest.java`는 명시적 opt-in 전용 테스트다. 프로젝트·멤버 두 엔티티만 `hibernate.hbm2ddl.auto=validate`로 검증하며 DDL·초기화 SQL을 실행하지 않는다. 기본 전체 테스트에서는 스킵한다.

```sh
cd backend
# 비밀번호는 터미널에서 입력한다. 저장소에 기록하지 않는다.
read -rs -p '팀 DB 비밀번호: ' PROJECT_TEAM_DB_PASSWORD
export PROJECT_TEAM_DB_PASSWORD
export PROJECT_TEAM_DB_TEST=true
export PROJECT_TEAM_DB_URL='jdbc:postgresql://cloud.newlecture.com:5442/devmozip'
export PROJECT_TEAM_DB_USER=devmozip
./gradlew test --tests '*ProjectTeamDatabaseIntegrationTest' --no-daemon --max-workers=1
unset PROJECT_TEAM_DB_PASSWORD PROJECT_TEAM_DB_TEST PROJECT_TEAM_DB_URL PROJECT_TEAM_DB_USER
```

현재 local 설정은 팀 PostgreSQL 5442와 `ddl-auto: validate`, SQL 초기화 비활성화다. DB_HOST/DB_PORT 환경변수가 있으면 local 파일 기본값보다 우선하므로 실제 서버 실행 시 확인한다.

### 검증 범위와 제약

- 생성 컨트롤러 → 실제 생성 서비스 → JPA → 팀 PostgreSQL을 연결해 HTTP 201/Location, 양수 ID, JSONB 정규화 저장, 총 정원과 HOST/ACTIVE 저장을 검증한다.
- standalone MockMvc를 사용하므로 실제 서버 포트·JWT 필터 검증은 포함하지 않는다. 테스트 주최자 ID는 101이며 실제 회원 계정이 아니다.
- 생성과 검증은 같은 외부 트랜잭션에서 실행하고 항상 롤백한다. 종료 후 생성 ID의 프로젝트·멤버가 없음을 재조회한다. PostgreSQL 시퀀스 발급은 롤백되지 않으므로 ID에 빈 번호가 생길 수 있다.
- 팀 DB에는 프로젝트 관련 테이블만 있다. 전체 애플리케이션이 요구하는 users·ai_conversations·ai_messages는 없어 전체 서버의 validate 기동은 아직 충족되지 않는다. 다른 담당 테이블을 자동 생성하거나 validate를 update로 낮추지 않았다.
- 프론트 생성 폼 API 연결, 실제 인증 회원 ID 공급, 프리미엄 검사는 이번에 추가하지 않았다. 전체 서비스 실연동 완료를 뜻하지 않는다.

### 이번 실행 결과

- 명시적 팀 DB 테스트: `./gradlew test --tests '*ProjectTeamDatabaseIntegrationTest' --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx384m` 성공, 1개 통과. 최종 TestConfiguration 분리 후 재실행도 성공.
- 기본 전체 테스트: `./gradlew test --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx384m` 성공, 239개 중 238개 통과·팀 DB opt-in 1개 스킵·실패/오류 0.
- 사후 SQL: projects 8행, project_members 21행 유지, 검증용 제목의 공고 0행. users·ai_conversations·ai_messages 부재를 to_regclass로 확인했다.
- frontend `npm run build`: 번들 컴파일 후 `Could not parse output from TypeScript's --showConfig.`로 종료 코드 1. 기존 오류이며 수정하지 않았다. 빌드가 변경한 next-env.d.ts 경로만 원복했다.
- `git diff --check` 통과. 커밋·푸시·다음 이슈 구현 없음.

## 프론트 생성 API 연결 준비 — 2026-11-05

현재 서버 연동은 활성화하지 않았다. `frontend/.env.example`의 서버 전용 `PROJECT_CREATION_API_ENABLED=false`가 기본값이다. Mock 데이터 표시 설정과 독립적인 값이며 Mock 끄기를 API 활성화로 사용하지 않는다.

### 준비한 경로

- `/projects/new` 서버 페이지가 요청 시 설정을 읽어 기존 클라이언트 작성 화면에 전달한다. 기본값에서는 기존 Mock 등록·수정·상세 이동이 유지된다.
- 활성화 시 새 공고에만 대표 이미지 HTTP(S) URL과 공개 범위 입력을 표시한다. 필수 이미지, 최대 기술 5개, 직군 코드·직군별 인원·경력, 날짜·분야를 #44 CreateProjectRequest로 변환한다. 기존 UI는 분야 단일 선택이며 서버에는 interests 배열로 보낸다.
- `frontend/lib/projects/projectCreationApi.ts`가 공통 Axios로 `POST /api/projects`를 호출한다.
- `frontend/app/api/projects/route.ts`가 기능 활성화·동일 출처·실제 서버 세션의 PREMIUM을 확인한 뒤 기존 backendFetch를 통해 HttpOnly 쿠키 토큰으로 `POST /api/v1/projects`를 호출한다. 클라이언트의 주최자 ID·status·권한 필드는 전달하지 않는다. UI 데모 프리미엄 전환만으로 실제 API 권한을 얻을 수 없다.
- 등록 중 중복 제출 차단, 실패 시 입력 유지·오류 표시. 실패를 Mock 생성으로 대체하지 않는다. 자동 재시도도 하지 않는다. 타임아웃·불완전 응답에서는 중복 등록 전 저장 여부 확인을 안내한다.
- 성공 시 서버가 발급한 ID·제목을 현재 화면에 표시한다. #45 목록/상세는 아직 Mock이므로 서버 공고를 Mock 상태에 복사하거나 숫자 ID의 미연결 상세로 이동하지 않는다. 기존 수정 기능은 Mock을 유지한다.

### 활성화 전 필수 확인

1. 회원·AI 등 전체 서버 엔티티에 필요한 테이블이 준비되어 `ddl-auto: validate`로 정상 실행되어야 한다. 프로젝트 외 테이블은 이번 작업에서 만들지 않는다.
2. 백엔드 ProjectCurrentUserProvider를 인증한 실제 회원 ID와 연결해야 한다. 현재 개발용 고정 ID 모드를 실제 운영 사용자 연결로 사용하지 않는다. users 테이블 생성만으로 이 코드가 자동 변경되지는 않는다.
3. 프로젝트 신규 생성의 프리미엄 인가를 백엔드에서도 적용·검증해야 한다. 현재 BFF PREMIUM 확인은 백엔드 직접 접근 인가의 대체가 아니다.
4. `BACKEND_URL`, 팀 DB 접속 환경 및 토큰 쿠키를 확인하고 실제 프리미엄 계정으로 인증 왕복을 검증한다.
5. 그 후 프론트 서버 환경에서 `PROJECT_CREATION_API_ENABLED=true`로 변경하고 재시작한다. 생성 후 DB의 프로젝트·HOST 소유자가 실제 로그인 회원과 일치하는지 확인한다.

#45 목록·상세 및 #46 수정/삭제의 프론트 API 연결은 이번 범위에 포함하지 않았다. 실제 브라우저/팀 DB 전체 연동 완료로 보고하지 않는다.

### 프론트 준비 코드 최종 검증

- `npm test -- --maxWorkers=2`: 53개 파일·566개 테스트 통과. DTO 변환·이미지/직군 검증·사용자 필드 제거·비활성 상태·세션/권한·Origin·서버 오류 처리·기존 Mock 화면 검증 포함.
- `npm run build`: 성공, TypeScript 및 전체 페이지 생성 포함. 샌드박스 자식 프로세스 출력 제한이 없는 환경에서 실행했다. 빌드 생성 next-env.d.ts는 이전 경로로 복원했다.
- `git diff --check` 통과. 브라우저를 통한 실제 서버 연결과 프리미엄 계정의 DB 저장은 미검증이며 기능 플래그는 활성화하지 않았다.

### 최신 develop 기반 후속 PR 검증

최신 develop a331121 기준의 후속 PR에서 팀 DB 테스트 1개, 프론트 테스트 58개 파일·643개 및 `npm run build -- --webpack`이 통과했다. 백엔드 전체 352개 중 1개 실패·1개 스킵이며 KakaoClientTest 로그 포함 검증이 실패했다. 해당 테스트와 프로젝트 테스트를 분리 재실행했을 때는 성공했다. 전체 실행 순서/로그 설정 영향은 미해결로 남긴다. 개인 설정과 아직 미병합인 #46 코드는 PR에서 제외했다.

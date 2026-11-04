# 프로젝트 생성 API — #44

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

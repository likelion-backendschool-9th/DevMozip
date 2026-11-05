# 프로젝트 공고 수정·삭제 API — #46

2026-11-05. 병합된 #44를 포함한 develop `650de14`에서 `feat/46-project-update-delete` 브랜치로 개발했다. #45 조회 PR은 아직 열려 있어 해당 커밋을 병합하지 않았다. 프론트 수정 페이지·API 연결 및 #47 상태/기간 API·#48 지원 기능은 추가하지 않는다.

## API 계약

| API | 성공 | 권한 |
|---|---|---|
| PUT /api/v1/projects/{id} | 200, 수정된 ProjectResponse record | 현재 ACTIVE 주최자 |
| DELETE /api/v1/projects/{id} | 204, 본문 없음 | 현재 ACTIVE 주최자, 모집 중 공고만 |

기존 Spring Security 인증 및 개발용 ProjectCurrentUserProvider 경계를 사용한다. 실제 회원/구독 결합은 여전히 후속 범위다. 무료 전환을 이유로 기존 주최자 관리를 막는 구독 검사는 추가하지 않았다.

- 인증 없음 401, 비주최자·비활성 주최자 403, 없는/논리 삭제 공고 404.
- 필수값·형식·날짜 순서 오류 400, 종료 공고 수정·운영 상태·기간/직군/정원 정책 위반 409.
- 중복 삭제는 이미 삭제된 공고이므로 404다. DELETE는 물리 삭제가 아니다.

## 전체 수정

생성과 같은 공고 필드를 받는 UpdateProjectRequest를 추가했다. 필수 이미지·isPublic·직군·기술·분야 배열·모집/개발 날짜를 생략하면 400이며, PATCH처럼 기존 배열을 유지하지 않는다. JSONB 목록은 전체 교체한다. summary/goal은 null로 비울 수 있다.

```json
{
  "title": "수정한 프로젝트",
  "summary": null,
  "description": "새로운 상세 소개",
  "goal": null,
  "imageUrl": "https://example.com/projects/new-cover.png",
  "isPublic": false,
  "recruitmentSlots": [
    {"roleCode": "BACKEND", "capacity": 2, "minimumYears": 1}
  ],
  "techStacks": ["Spring Boot", "직접 입력 기술"],
  "interests": ["교육"],
  "recruitmentStart": "2026-11-01",
  "recruitmentEnd": "2026-11-30",
  "developmentStart": "2026-12-01",
  "developmentEnd": "2027-03-01"
}
```

기술·분야는 각각 최대 5개·정규화 후 1~30자·대소문자 무시 중복 거절·직접 입력 허용·빈 배열 허용을 유지한다. 제목/요약 최대 255자, 이미지 HTTP(S) URL 1장 필수·최대 2048자, 직군별 정원 1~10·최소 경력 0~5다.

주최자 ID·status·생성/수정/종료/삭제 시각은 요청 DTO에 없다. 요청에 같은 이름의 추가 필드를 넣어도 상태 변경에 사용하지 않는다. created_at은 유지하고 updated_at만 서울 Clock 기준으로 갱신한다. 비공개 전환도 기존 멤버를 제거하지 않는다.

수정 폼은 #45 상세 조회 응답의 기존 공고 필드로 채우고 PUT에 전체 값을 보내는 계약이다. 계획 경로 `/projects/{id}/edit`는 이번에 구현하지 않았다. 이 브랜치 자체에는 #45 상세 Controller가 없으며 두 PR 병합 후 화면 연결이 필요하다.

## 기간·직군 정책

- 모집 시작≤모집 종료, 개발 시작≤개발 종료, 모집 종료≤개발 종료.
- 생성과 달리 과거 모집 마감일을 그대로 둔 내용 수정은 허용한다.
- 종료일을 변경하면 기존보다 줄일 수 없고 연장만 허용한다. 다른 날짜 순서도 검사하여 PUT으로 기간 정책을 우회하지 못하게 한다.
- 진행 중인 프로젝트의 모집/개발 시작일은 변경하지 못한다. 일반 소개 수정은 가능하며 종료 공고는 읽기 전용이다.
- 날짜 규칙은 ProjectPeriodPolicy에 분리했고 생성에서도 순서 검증을 재사용한다. #47에서도 동일한 규칙을 연결해야 한다. 별도 기간 변경 이력·version은 추가하지 않았다.
- 현재 ACTIVE PARTICIPANT가 있는 직군은 삭제/코드 변경할 수 없고 정원을 해당 직군 ACTIVE 인원 미만으로 줄일 수 없다. HOST는 직군 정원을 차지하지 않고 LEFT는 현재 인원에 포함하지 않는다.
- 대기/승인 지원자가 있는 직군은 ProjectApplicationUsage.usedRoles 경계에서 보호한다. 아직 지원 엔티티/테이블·Repository가 없으므로 기본 어댑터는 빈 집합이며 **실제 지원 데이터 연동 검증은 #48에서 필요**하다. 이번 테스트는 해당 경계를 대역으로 검증했으며 실제 지원 DB 검증으로 보고하지 않는다.

## 논리 삭제·동시성

RECRUITING이면 지원자·팀원 존재 자체로 삭제를 제한하지 않는다. RECRUITMENT_COMPLETED / IN_PROGRESS / COMPLETED는 409다. 삭제 시 deleted_at과 updated_at만 기록하고 프로젝트 행·멤버·참여 상태를 그대로 보존한다.

모든 수정·삭제는 ProjectRepository.findLiveForUpdate의 프로젝트 행 잠금 안에서 권한·정책·저장까지 한 트랜잭션으로 처리한다. 수정 중 실패하면 JSONB와 일반 필드 모두 롤백한다. 삭제와 수정이 경합해도 삭제 공고를 다시 수정하여 복원하지 않는다. 일반 폼 충돌을 감지하는 version은 없으므로 두 정상 수정은 마지막 저장이 앞선 내용을 덮을 수 있다.

#45의 일반 목록·상세·내 프로젝트 조회는 deleted_at IS NULL을 적용해야 한다. 해당 PR의 메서드명과 일치하는 잠금/미삭제 조회 메서드를 사용했다. 이 브랜치에서는 실제 #45 HTTP 경로와 병합 검증을 수행하지 않았으며 저장소의 미삭제 조회에서 삭제 대상이 제외되는 것까지만 테스트했다.

지원/멤버 이력은 물리 삭제하지 않는다. 삭제 안내 알림, 채팅 처리, 투표 무효화 및 수정 알림은 실제 알림/채팅 담당 영역과 커밋 이후에 연결할 후속 계약으로 남긴다. 외부 API·WebSocket·알림 발송·스케줄러는 추가하지 않았다.

## 변경 파일

- controller/ProjectController, service/ProjectService, service/impl/ProjectServiceImpl: PUT/DELETE와 트랜잭션·정책 검증.
- dto/project/UpdateProjectRequest: 전체 수정 record 계약.
- entity/Project: 허용 필드 갱신 및 논리 삭제, BaseEntity.touch 재사용.
- repository/ProjectRepository, ProjectMemberRepository: 프로젝트 잠금·미삭제 조회·주최자/직군 인원 검증.
- service/ProjectPeriodPolicy: 생성·수정 날짜 규칙.
- service/ProjectApplicationUsage, config/ProjectApplicationUsageConfiguration: #48 연결 경계.
- test/project/ProjectUpdateDeleteIntegrationTest: PostgreSQL 검증.

기존 ERD의 프로젝트/멤버 필드와 비교했으며 이번에 엔티티·컬럼·테이블을 새로 추가하지 않았다. 최신 ERDCloud export 및 팀 공용 DB에 대한 재대조는 하지 않았다.

## 실제 실행 결과

- backend `./gradlew test --tests '*ProjectUpdateDeleteIntegrationTest' --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx384m`: 신규 23개 통과.
- backend `./gradlew test --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx384m`: 전체 222개 통과, 실패·오류·스킵 0. 독립 PostgreSQL 17 Testcontainers에서 실제 JSONB 갱신·논리 삭제·멤버 보존·제약 실패 롤백·동시 수정/삭제를 검증했다.
- 최초 컴파일에서는 중첩 DTO 타입의 타입-use annotation 표기가 잘못돼 실패했고 import한 중첩 타입으로 수정했다. 중단된 전체 테스트 실행은 완료로 보고하지 않고 재실행한 최종 결과만 집계했다.
- frontend `npm test`: 48개 파일·525개 통과. `npm run build`: 성공(TypeScript 포함). 프론트 코드 변경은 없고 생성된 next-env.d.ts만 실행 전 상태로 복원했다.
- `git diff --check`: 통과. 팀 공용 DB DDL·실제 API 화면 연결·실제 지원 테이블 조회·#45 병합 후 HTTP 회귀는 미검증이다.
- 커밋·푸시·PR·이슈 완료 처리는 하지 않았다.

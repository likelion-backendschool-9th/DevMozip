# 프로젝트 상태·기간 관리 API — #47

> 2026-11-05 후속 변경: 기본 프로젝트 사용자 공급을 실제 JWT 이메일→users.id로 연결했다. 과거 개발용 고정 ID/실회원 미연결 설명은 당시 기록이며 현재 동작은 [실제 회원 ID 연결](project-authenticated-user.md)을 따른다. 프론트 API 활성화·백엔드 프리미엄 인가는 별도다.


2026-11-05. 기존 #44~#46 구현과 팀 DB 검증을 포함하는 8368e6b에서 `feat/47-project-lifecycle`로 분리했다. 요청 범위는 프로젝트 상태·기간·종료 동의이며 프론트 연결, 회원·구독·채팅·리뷰·알림의 실제 구현과 스케줄러는 포함하지 않는다.

## API

`PATCH /api/v1/projects/{id}/update` → 200, ProjectLifecycleResponse record.

| action | 추가 입력 | 권한·처리 |
|---|---|---|
| CHANGE_STATUS | status | ACTIVE 주최자의 상태 전이 |
| EXTEND_PERIOD | recruitmentEnd 또는 developmentEnd | ACTIVE 주최자, 하나 이상 실제 연장 |
| REQUEST_END | 없음 | 진행 중 프로젝트의 ACTIVE 팀원 본인 동의 |
| CANCEL_END_REQUEST | 없음 | 종료 확정 전 본인 동의 취소 |

명령에 맞지 않는 status/날짜 혼합은 400이다. userId·projectMemberId·completedAt·득표수를 요청에서 받지 않는다. 기존 Spring Security 인증과 ProjectCurrentUserProvider를 사용하며 실제 회원 ID 결합은 여전히 후속 범위다. 개발 ID 모드를 실제 회원 연결로 간주하지 않는다.

```json
{"action":"CHANGE_STATUS","status":"RECRUITMENT_COMPLETED"}
```
```json
{"action":"EXTEND_PERIOD","developmentEnd":"2027-04-30"}
```
```json
{"action":"REQUEST_END"}
```
```json
{"action":"CANCEL_END_REQUEST"}
```

응답은 project(기존 ProjectResponse), activeMemberCount, endRequestCount, requiredEndRequestCount, myEndRequested다. 기존 조회 API 응답 구조는 변경하지 않았다. 이번에는 새 프론트 버튼·동선을 추가하지 않는다.

## 상태와 날짜

- 모집 중 → 모집 완료 → 진행 중을 허용한다. 모집 완료 → 모집 중은 서울 기준 모집 시작일부터 모집 종료일까지(당일 포함)만 허용한다. 진행 중에서 이전 상태로 되돌릴 수 없다.
- 같은 상태 재요청과 허용되지 않은 전이, 주최자의 직접 COMPLETED 지정은 409다.
- 진행 시작은 실제 project_applications에서 PENDING이 하나라도 있으면 409다. #48 승인·거절 API는 구현하지 않았으며 읽기 조회만 추가했다. 새 지원·승인·취소가 추가될 때도 같은 프로젝트 행 잠금을 사용해야 한다.
- 기간 연장은 기존 종료일보다 단축할 수 없고 하나 이상 늘려야 한다. 생략한 종료일은 유지하며 날짜 순서는 #46 ProjectPeriodPolicy를 재사용한다. 시작일 변경이나 별도 이력 테이블은 없다. 종료 공고는 변경 불가다.
- 모집 마감 경과는 기존 #45 recruitmentClosed·마감 임박 필터에서 계산한다. 운영 상태를 자동 COMPLETED로 바꾸지 않는다.

## 종료 동의 확정 정책

사용자 답변으로 **진행 중에만 요청 가능, 확정 전 취소 허용**을 확정했다.

- HOST 포함 현재 ACTIVE 팀원 N명의 floor(N/2)+1명 동의가 필요하다. 4명은 3명, 5명은 3명이며 1명 팀은 본인 1명 동의로 종료한다. 주최자 동의를 자동 기록하지 않는다.
- end_requested_at에 본인 요청 시각을 기록한다. LEFT와 그 동의는 분모·분자에서 제외한다. 중복 요청·요청 없는 취소는 409이며 취소 후 재요청은 가능하다.
- 프로젝트 행 잠금 안에서 멤버 확인·기록·flush·현재 인원 집계·COMPLETED/completed_at 갱신을 처리한다. 모든 요청을 직렬화해 동시 요청의 중복 종료를 방지한다.
- 종료 후 요청·취소·상태 변경·연장은 409다. 기존 #46 PUT도 종료 공고를 수정하지 못한다.
- 현재 요청 시점의 팀원 수로 집계한다. #48 참여 승인/취소에서 팀원 수가 바뀔 때 즉시 재집계할 결합은 아직 없다. 특히 탈퇴만으로 기존 동의가 과반수를 넘는 경우의 즉시 종료는 #48에서 같은 잠금 안에 연결해야 한다. 재참여 시 과거 동의 초기화도 해당 단계에서 처리한다.

## 후속 처리 계약

상태 변경·기간 연장·종료 확정에 ProjectLifecycleChanged 불변 이벤트를 발행한다. 프로젝트/행위자 ID, 이전·현재 상태, 이전·현재 종료일, 발생 시각을 포함한다. 후속 수신자는 @TransactionalEventListener(AFTER_COMMIT)로 처리해야 한다. 롤백 시 커밋 후 이벤트를 전달하지 않으며 동시 종료 이벤트는 한 번이다. 프로세스 내부 이벤트로, 영속 outbox·외부 발송·재시도 보장을 구현한 것은 아니다.

- PERIOD_EXTENDED: 기존 종료 예정일 알림을 해소/갱신하는 연결 지점.
- COMPLETED: 종료 안내·채팅 정리 선택·종료일+30일 리뷰 요청 생성의 연결 지점. 실제 구현/호출 없음.
- 기존 일반 PUT 수정 알림은 #46 후속 계약을 유지하며 이번 새 PATCH 이벤트가 기존 PUT까지 자동 적용되는 것은 아니다.

WBS 7.14 관리자 모집 만료 알림은 이후 배치에서 `status=RECRUITING AND recruitment_end < 서울 오늘`을 대상으로 한다. 제안 중복 키는 `project-recruitment-expired:{id}:{recruitmentEnd}`이며 알림/outbox 저장소의 유일 제약으로 중복을 막아야 한다. 기간 연장 이벤트로 기존 알림 상태를 갱신한다. 실제 배치·스케줄러·알림 저장소는 만들지 않았다. #48 신규 지원 API가 모집 시작/종료일을 검사해야 실제 지원 차단이 완성된다.

## 구조·DB

- controller/ProjectLifecycleController: WBS PATCH 입출력.
- dto/project/ProjectLifecycleRequest·Response: record 계약.
- service/ProjectLifecycleService·impl/ProjectLifecycleServiceImpl: 전이·권한·과반수·트랜잭션.
- repository/ProjectLifecycleRepository: 실제 지원 테이블의 PENDING 읽기.
- Project·ProjectMember: 상태/종료일·종료 시각·본인 동의 변경. ProjectMemberRepository에 ACTIVE 멤버 조회/동의 집계 추가.
- event/ProjectLifecycleChanged: 후속 처리 계약.

새 테이블·컬럼은 추가하지 않았다. 청사진과 기존 엔티티·팀 DB의 projects.status/completed_at/종료일, project_members.end_requested_at, project_applications.project_id/status를 사용한다. 지원 엔티티는 여전히 없으며 #47은 JDBC 읽기만 한다. 테스트 전용 지원 스키마 fixture는 청사진 DDL을 사용하고 backend/.gitignore의 해당 fixture만 예외로 관리한다.

## 검증 범위

- 독립 PostgreSQL Testcontainers에서 새 API 테스트 15개 통과: 인증·잘못된 DTO·상태 전이·KST 마감일 경계·대기 지원서·주최자 권한·기간·4/5인 과반수·취소·팀원 변동·동시 요청·커밋 후 이벤트와 롤백·삭제/없는 대상.
- 실제 팀 PostgreSQL opt-in 테스트 3개 통과: 상태/기간/1인 종료, 4인 동의·취소, 실제 지원 테이블의 대기 상태 확인. 임시 데이터는 모두 롤백한다. 실제 JWT/회원/프론트 왕복 검증이 아니다.
- 팀 DB의 기본 공고와 다른 도메인 데이터는 변경하지 않는다. 시퀀스 발급 번호는 롤백되지 않는다.

### 최종 실행 결과

- `./gradlew test --tests '*ProjectLifecycleIntegrationTest' --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx384m`: 새 기능 15개 통과.
- PROJECT_TEAM_DB_TEST 명시 실행으로 `*ProjectLifecycleTeamDatabaseIntegrationTest`: 실제 팀 DB 3개 통과. 사후 공고 8개·멤버 21개·지원서 6개·조회수 합 2215 유지, db47_ 임시 공고 0개.
- `./gradlew test --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx384m`: 전체 402개 중 390개 통과·11개 opt-in 스킵·1개 실패. 프로젝트 패키지는 67개 통과·11개 스킵·실패 0. 유일한 실패는 이전 PR에서도 관찰한 KakaoClientTest 153행의 로그 검증이다. 해당 코드와 인증 설정은 변경하지 않았다.
- `frontend`의 `npm run build -- --webpack`: 성공(TypeScript 포함). 별도 worktree의 공유 의존성 링크 때문에 webpack 사용. 프론트 제품/빌드 설정 변경 없음, 생성 next-env.d.ts 복원.
- git diff --check 통과. 실제 전체 서버/프론트/JWT·회원 왕복, 실제 알림·채팅·리뷰·스케줄러는 미구현/미검증이다.
- 커밋·푸시·PR 생성·이슈 체크/종료는 하지 않았다. 다음 #48을 자동 시작하지 않는다.

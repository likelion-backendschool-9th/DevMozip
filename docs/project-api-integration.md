# 프로젝트 실제 API 연결 (#44–#47)

## 데이터 모드

- Mock 켜기: 기존 프로젝트 시연·데모 계정·localStorage 상태를 유지한다.
- Mock 끄기: 프로젝트 목록·상세·내 프로젝트·작성·수정·삭제·상태/기간/종료 동의는 실제 DB/API를 사용한다. 다른 미연결 도메인은 샘플 없이 표시한다.
- 실제 API 오류를 Mock 데이터로 대체하지 않는다. 실제 프로젝트 데이터는 localStorage에 저장하지 않는다.
- 실제 로그인은 HttpOnly 쿠키와 기존 BFF 인증을 사용한다. 데모 구독 변경은 실제 권한을 변경하지 않는다.

## 경로

| 화면 | 프론트 BFF | 백엔드 |
| --- | --- | --- |
| /projects, /explore?type=projects, 메인 미리보기 | GET /api/projects | GET /api/v1/projects |
| /my-projects | GET /api/projects/mine | GET /api/v1/me/projects |
| /projects/new | POST /api/projects | POST /api/v1/projects |
| /projects/{id} | GET /api/projects/{id} | GET /api/v1/projects/{id} |
| /projects/{id}/edit | PUT /api/projects/{id} | PUT /api/v1/projects/{id} |
| 주최자 관리 | GET /api/projects/{id}/manage | GET /api/v1/projects/{id}/manage |
| 공고 삭제 | DELETE /api/projects/{id} | DELETE /api/v1/projects/{id} |
| 상태·기간·종료 동의 | PATCH /api/projects/{id}/update | PATCH /api/v1/projects/{id}/update |
| 작성 선택지 | GET /api/projects/options | GET /api/v1/projects/options |
| 실제 회원 ID | GET /api/projects/identity | GET /api/v1/users/me/profile 및 기존 인증 검증 |

주최자 /my-projects/{id}/manage는 주최자 관리 정보를, 참여자는 본인 참여 정보와 종료 동의를 표시한다. 상세의 membership은 현재 ACTIVE 참여자에게만 제공한다. 로그인하지 않은 사용자와 비참여자에게는 null이다. 주최자 공개 정보는 id/tag/nickname만 제공한다.

## 권한·정책

- 신규 생성은 실제 DB 회원 역할 PREMIUM만 허용한다. 기존 주최자는 구독 해지 후에도 관리할 수 있다.
- 사용자 ID와 프로젝트 상태를 생성/수정 요청의 신원으로 신뢰하지 않는다. 서버가 인증 회원·주최자·현재 팀원 여부를 판단한다.
- PENDING/APPROVED 지원서가 사용하는 직군은 실제 project_applications 조회로 보호한다. 지원 CRUD는 추가하지 않는다.
- 진행 중 현재 ACTIVE 팀원(주최자 포함)의 절반 초과 동의로 종료한다. 확정 전 본인 동의 취소 가능, 완료 후 취소 불가.
- 수정 시 기간 단축 불가. 모집 중 논리 삭제, 종료된 공고 수정 불가 등 기존 #46/#47 정책을 따른다.
- 모집 직군은 코드 목록, 기술 스택·분야는 서버 선택지와 사용자 입력을 함께 지원하며 각각 최대 5개다.

## 실행

frontend 서버 환경: BACKEND_URL=http://localhost:8080, PROJECT_API_ENABLED=true. 기본 활성화이며 false면 프로젝트 BFF를 503으로 차단한다. 기존 PROJECT_CREATION_API_ENABLED 플래그는 더 이상 사용하지 않는다.

백엔드는 기존 PostgreSQL 설정·인증 설정을 사용한다. ddl-auto=validate, SQL 초기화 없이 기존 팀 DB에 연결한다. 비밀번호와 JWT 비밀값은 저장소에 포함하지 않는다.

## 미연결 범위

#48 지원·승인·취소 및 지원 중 탭은 아직 실제 API로 연결되지 않았다. 지원 중 조회는 501을 명시한다. 채팅·알림·리뷰 소비자·백그라운드 스케줄러·북마크는 실제 프로젝트 연동 범위에 포함하지 않는다. Mock 모드의 해당 기능은 보존한다. 본 단계에서 이 기능들을 완료 처리하지 않는다.

## 이슈 체크 점검

| 이슈 | 점검 결과 | 남은 결합 |
| --- | --- | --- |
| #44 | 생성·선택지·검증·HOST 원자 저장·실제 회원·프리미엄 인가 구현 | 실제 결제/구독 시스템은 담당 기능에서 연결 |
| #45 | 공개/비공개·필터·정렬·조회수·내 주최/참여/종료 조회 구현 | 지원 중 조회는 #48 이후. 전체 체크리스트 완료로 보지 않음 |
| #46 | PUT·논리 삭제·직군 보호·수정 폼 연결 구현 | 알림/채팅 후속 소비자 연결 |
| #47 | 상태/기간/과반수 종료·취소·후속 이벤트 계약 구현 | 지원 API의 마감 차단, 관리자 알림 배치, #48 팀원 변경 시 즉시 재집계 |

체크리스트는 현재 코드와 테스트를 기준으로 점검했다. GitHub 이슈 본문·상태 및 기존 PR은 이번 작업에서 변경하지 않았다.

## 이번 검증

프론트 테스트 653개와 webpack 프로덕션 빌드(TypeScript 포함) 통과. 프로젝트 패키지 테스트 74개 통과·11개 opt-in 스킵. 전체 백엔드는 413개 중 기존 카카오 로그 검증 1개 실패(401개 통과·11개 스킵)다. Chromium에서 Mock 전환, 1440/390 목록, 실제 프리미엄 생성→상세→수정→논리 삭제, 무료 회원 작성 차단을 검증했다. 실제 임시 공고 36·37은 논리 삭제했으며 기본 공개 공고 8개는 유지했다. 전체 사용자 흐름과 모든 화면 크기를 검증한 것은 아니다.

복구 후에도 프론트 테스트 653개·webpack 빌드 및 백엔드 전체 테스트를 재실행해 같은 결과를 확인했다. 복구 후 임시 공고 38도 생성·수정 후 논리 삭제했다. 작업 위치는 `.worktrees/project-api`, 브랜치는 `feat/project-api-docker-integration`이다. 프로젝트의 클라이언트 로딩 구조에 맞게 해당 layout에만 `instant=false`를 적용했다.

개발 서버의 실제 토글 전환·프로젝트 8개 표시도 확인했다. 다만 Next 개발 로그의 프로젝트 client page instant-navigation 검증 경고는 남아 있다. 프로덕션 브라우저 흐름에서는 JavaScript 오류 없이 동작했다.

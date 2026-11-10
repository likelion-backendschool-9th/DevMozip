# 프로젝트 실제 로그인 회원 ID 연결

2026-11-05 사용자 요청으로 프로젝트의 고정 개발 ID 경계를 실제 로그인 계정에 연결했다. #47 이후 별도 `fix/project-authenticated-user` 브랜치에서 작업했다. 공통 인증·회원·AI 코드는 변경하지 않았다.

## 요청자 식별

기본 ProjectCurrentUserProvider는 기존 JWT 필터가 검증한 SecurityContext의 이메일로 MemberRepository.findByEmail을 조회하고 users.id를 반환한다. 요청 body/query의 hostMemberId·userId는 신원으로 사용하지 않는다.

- 비로그인·익명·인증되지 않은 주체·삭제 회원·양수 ID가 아닌 회원은 401.
- ACTIVE·WARNED는 허용, SUSPENDED 또는 미확인 상태는 403.
- 매번 현재 회원을 확인하므로 이미 발급된 토큰도 회원 삭제/정지 후 프로젝트 요청에 사용할 수 없다.
- `project.current-user.development-enabled=true`는 local/dev/test에서만 기존 양수 고정 ID를 사용한다. 기본 false이며 실계정 연동 시 활성화하지 않는다.
- prod/production이 활성화되면 개발 플래그가 있어도 실제 인증 회원을 사용한다.

## 연결 범위

같은 공급 경계를 사용하는 생성 주최자·HOST 저장, 내 프로젝트, 비공개 멤버 권한, 관리 조회, PUT/DELETE 권한 및 #47 상태/기간/종료 요청에 적용된다. 기존 프로젝트/멤버 인가를 우회하지 않는다.

이번 작업은 회원 ID 식별 연결이다. 프로젝트 신규 생성의 백엔드 프리미엄 인가, 프론트 목록/상세/수정 연결은 별도 범위이며 기존 제한/미완료 사항을 바꾸지 않았다. `PROJECT_CREATION_API_ENABLED`도 활성화하지 않았다.

## DB 전제

이전 사용자 요청으로 팀 DB의 김하늘은 ID 1, 프리미엄 이서준은 5, 개발자 yuna/minjun/seoyeon/jiho는 6/7/8/9이며 프로젝트의 임시 회원 참조는 실제 계정으로 변환돼 있다. 이번 코드 변경에서 회원 ID/기본 데이터를 다시 변경하지 않는다.

서버는 DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD와 JWT_SECRET을 실제 환경에 맞추고 local에서 실행한다. PostgreSQL 5442, JPA validate, SQL 초기화 비활성화로 검증한다. 과거 문서의 '실제 회원 공급 미연결'은 당시 구현 기록이며 현재 기본 동작은 본 문서를 따른다.

## 테스트

- AuthenticatedProjectCurrentUserProviderTest: 검증한 이메일→ID, 매 호출 상태 확인, 익명/미인증/삭제/잘못된 ID 차단, 경고 계정 허용.
- ProjectConfigurationTest: 기본 인증 방식, 개발 옵트인과 양수 ID, 운영 환경 개발 ID 우회 차단.
- ProjectAuthenticatedUserIntegrationTest: 독립 PostgreSQL에서 실제 로그인→생성/HOST ID 확인, 요청 ID 위조 무시, 내 목록 분리·비주최자 관리/삭제 거절·#47 주최자 연결, 기존 토큰의 정지/삭제 차단·공개 목록 보존.

관련 테스트 10개가 통과했다. 최초 실행에서는 테스트 Mock 빈을 중복 등록해 설정 테스트 1개가 실패했고 독립 ApplicationContextRunner로 수정한 뒤 10개를 재실행해 통과했다.

## 최종 검증 결과

- 관련 테스트 10개 통과. 전체 백엔드 410개 중 398개 통과·11개 opt-in 스킵·1개 실패. 프로젝트 관련은 75개 통과·11개 스킵·실패 0. 유일한 전체 실패는 이전부터 관찰한 KakaoClientTest 153행의 로그 검증이며 해당 코드는 수정하지 않았다.
- frontend `npm run build -- --webpack` 성공(TypeScript 포함). 별도 worktree의 공유 의존성 링크를 지원하도록 webpack 사용, 제품 빌드 설정 변경 없음. 생성 next-env.d.ts 복원.
- 실제 팀 DB에 읽기 전용 연결한 임시 localhost:18087 서버에서 전체 스키마 validate와 health UP. 일반/프리미엄/관리자 및 개발자 4명 로그인·users/me 모두 200.
- 김하늘 내 프로젝트 200/4개, 이서준 내 프로젝트 200/4개 및 주최 목록 2개·본인 공고 관리 200, 일반 회원의 타인 공고 관리 403 확인. 이전 내 프로젝트 401이 해소됐다.
- 실제 DB에 생성·수정·삭제 요청을 보내지 않았다. 생성/HOST 저장과 #47 주최자 연결은 독립 테스트 DB에서 검증했다. 임시 서버는 종료했고 기존 상시 서버를 재시작하거나 배포하지 않았다.
- git diff --check 통과. 커밋·푸시·PR 생성 및 프론트 API 플래그 활성화는 하지 않았다.

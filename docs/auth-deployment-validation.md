# 인증·배포 분석 및 검증 기록

2026-11-03. develop에서 작업. 커밋·원격 푸시·main 병합·외부 배포는 수행하지 않았다.

## 저장소 분석과 적용 범위

- Java 21, Spring Boot 4.1.2, Gradle 9.8.2, backend/. 프레임워크·언어 버전·폴더 구조를 유지했다.
- Next.js 16.4.3 App Router, React 19.3.0, npm(package-lock.json), frontend/. Node 24.22.1로 검증했다. Next 로컬 배포 문서의 proxy, cookies, use server 안내를 읽고 구현했다.
- PostgreSQL 드라이버는 있으나 서버 버전·접속 설정·사용자 엔티티·테이블·기존 compose는 없었다. PostgreSQL 17을 선택했다. 업무 스키마/마이그레이션은 추가하지 않았으며 JPA ddl-auto=none이다. H2는 테스트 런타임에서만 사용한다.
- 인증 분류: 실제 서버 인증은 없음. 프론트에는 localStorage/sessionStorage 기반 Mock 인증과 salt + SHA-256 가입 계정이 있다. 사용자 추가 승인에 따라 Mock은 보존하고 별도 BCrypt 임시 USER/ADMIN으로 서버 인증을 구현했다.
- 사용자 역할: 기존 화면 free/premium/admin을 유지하고 서버는 USER/ADMIN이다. premium 구독은 여전히 Mock이며 서버 USER에 premium 권한을 부여하지 않는다.
- 로그인 없이 호출하는 업무 GET API는 없다. 따라서 추가 공개 업무 API 목록도 비어 있다. 공개는 POST /api/auth/login, /refresh, /logout, GET /actuator/health, /error로 좁혔다. /api/auth/me는 로그인 필수다. 프론트 /api/auth/session은 토큰 없이 mode와 identity:null만 제공한다.
- 보호 화면: /my-projects, /onboarding, /profile/me, /bookmarks, /settings, /messages, /notifications, /portfolios, /admin 접두어; /projects/new, 프로젝트별 manage/reviews, /feeds/new 및 게시글 edit. 기존 /posts, /chats 별칭도 방어적으로 보호한다. /admin은 ADMIN 전용. 공개 프로젝트·게시글·프로필 목록/상세는 유지한다. 매칭은 공개 조건 입력과 로그인 결과 생성이 섞여 있어 전체 경로를 차단하지 않는다. 기존 세부 도메인 권한 검사는 계속 Mock이다.
- 브라우저의 실제 백엔드 fetch는 없었다. 미사용 frontend/lib/shared/api.ts의 Axios 기본값에 localhost:8080/api/v1 및 공개 환경변수가 있어 동일 출처 /api로 바꿨다. 인증은 서버 액션과 서버 전용 backendFetch만 사용한다.
- 기존 backend application.properties는 애플리케이션 이름뿐이었다. 환경변수 기반 application.yaml로 교체했다. JWT 비밀키·두 임시 계정 비밀번호는 안전한 기본 비밀값을 코드에 두는 대신 빈 기본값에서 시작을 거부한다.
- 기존 Dockerfile·compose·배포 워크플로는 없어 새로 추가했다. 외부 참고 폴더는 없었다. 저장소는 likelion-backendschool-9th/DevMozip이다.
- 개발 시작 시 develop은 origin/develop을 추적하고 로컬 main과 같은 41af930 커밋이었다. 이전 대화의 40커밋 선행 상태를 이번 브랜치 상태에 적용하지 않았다.

## 구현 파일

| 영역 | 파일 |
|---|---|
| 백엔드 인증 | backend/src/main/java/com/devmozip/backend/auth/의 JWT·필터·보안·Redis 저장·인증 서비스·컨트롤러·DTO·오류 처리 |
| 백엔드 설정/테스트 | backend/build.gradle, src/main/resources/application.yaml, src/test/java/com/devmozip/backend/BackendApplicationTests.java |
| BFF | frontend/lib/auth/, frontend/proxy.ts, frontend/app/api/auth/session/route.ts, frontend/app/api/health/route.ts |
| 기존 인증 연결 | frontend/lib/state/authStore.ts, lib/account/serverIdentity.ts, types/session.ts, app/_components/ClientReady.tsx, app/login/page.tsx, components/auth/DemoAccounts.tsx |
| 실행·배포 | backend/frontend의 Dockerfile·.dockerignore, compose.yaml, compose.override.yaml, compose.prod.yaml, nginx/, deploy/blue-green.sh, .github/workflows/deploy.yml |
| 검증 | frontend/lib/auth/*.test.ts, tests/helpers/serverOnly.ts, vitest.config.ts, scripts/test_blue_green.py; 날짜 의존 기존 테스트 2개의 기준 시간만 고정 |
| 안내·정리 | AGENTS.md, README.md, docs/team-auth-deployment.md, docs/setup-after-ai.md, docs/api-plan.md, .gitignore, .env.example, frontend/.env.example, next.config.ts, 자동 생성 next-env.d.ts, 미사용 Axios 주소 및 저장소 주석 정리 |

## 실행 결과

- 백엔드 `./gradlew compileJava test --no-daemon`: 성공, 10개 테스트. 테스트는 임시 H2와 Redis 저장소 대역을 사용하므로 실제 PostgreSQL/Redis 통합 검증을 의미하지 않는다.
- 프론트 `npm run typecheck`, `npx tsc --noEmit`: 성공. 기존 .next/dev/types가 현재 경로 변경 전의 삭제된 페이지를 참조해 최초 검사가 실패했고, 생성된 구형 타입을 /tmp에 보관한 뒤 재생성하여 해결했다.
- 프론트 `npm test`: 40개 파일, 446개 테스트 성공. 기존 검색/정렬 테스트 두 개는 11월 2일 마감인 Mock 공고가 실제 날짜에 따라 탈락하여 실패했다. 해당 테스트만 2026-11-01로 고정했고 도메인 로직은 변경하지 않았다.
- 프론트 `npm run build`: 성공. 최초 실행은 WSL의 Windows npm 경로 및 샌드박스 하위 프로세스 EPERM으로 실패했다. Linux Node PATH와 실행 권한을 맞춰 빌드 성공을 확인했다.
- `npm ci`: lock 파일 기준 설치 성공. package.json/package-lock.json 변경 없음. 설치 출력의 의존성 감사는 moderate 16개를 보고했고 버전 변경이나 자동 수정은 하지 않았다.
- 변경 영역 ESLint: 성공. 전체 저장소 lint는 실행하지 않았다.
- 최종 standalone 런타임 HTTP 확인: `/`, `/login`은 200; `/messages/chats?userId=test`, `/admin/users`는 로그인으로 307이며 next 쿼리 보존; `/api/auth/session`은 200과 server/identity:null; 백엔드 미실행 상태의 `/api/health`는 503/DOWN. 확인용 서버는 종료했다. 의존성 재설치 중 기존 서버 검사에서 모듈 누락 500이 발생했으며, 설치 완료 후 최종 빌드 산출물로 재실행해 위 응답을 확인했다.
- `python3 scripts/test_blue_green.py`: 4개 성공. Docker 대역으로 첫 배포/전환/롤백/상태, 헬스 실패 시 활성 색 유지, nginx 검증 실패 시 파일 복구, 첫 배포 실패 시 활성 파일 미생성을 확인했다. 실제 nginx·Docker 네트워크 검증은 아니다.
- `bash -n deploy/blue-green.sh`: 성공.
- PyYAML로 compose 3종, workflow, application.yaml 파싱: 성공. 운영 서비스 7개와 ports가 nginx 한 곳뿐인 구조도 확인했다.
- 임시 필수 변수를 넣은 `docker compose -f compose.prod.yaml --profile blue --profile green config --quiet`: Docker 명령이 이 WSL 환경에 없어 실패. YAML 파싱으로 Compose 검증 성공을 대신 주장하지 않는다. CI test job에 `.env.example`을 이용한 실제 compose config 검사를 추가했다.
- Docker 이미지 빌드·컨테이너 up, 실제 DB/Redis, HTTPS 브라우저 쿠키 검사, GHCR 푸시, SSH·운영 블루그린 전환은 미수행이다.

## 완료 기준별 판정

| 번호 | 기준 | 판정·근거 |
|---|---|---|
| 1 | JWT 비밀키·DB 비밀번호 비포함 | 충족: 새 설정/소스/워크플로에 실제 값 없음. 환경변수와 미추적 .env만 사용; .env.example은 자리표시 |
| 2 | 보호 API 401/관리자 403 JSON 통일 | 충족: BackendApplicationTests 및 두 JSON 핸들러 |
| 3 | 리프레시 토큰을 Bearer로 사용 불가 | 충족: refreshCannotAuthenticateOrHaveRole 테스트; role 없는 토큰은 필터가 인증하지 않음 |
| 4 | HttpOnly 두 쿠키·로그아웃 후 재발급 거절 | 코드/테스트 충족, 브라우저 확인 필요: actions.test.ts 및 loginRefreshLogoutRevokesRefresh. 실제 Redis는 미검증 |
| 5 | 브라우저 직접 백엔드 호출·공개 주소 제거 | 충족: 동일 출처 Axios 및 서버 전용 BFF; 토큰을 session JSON으로 보내지 않음 |
| 6 | proxy 보호·만료 재발급 | 충족: proxy.test.ts로 리다이렉트·권한·현재 요청 쿠키 전달·실패 삭제 검증 |
| 7 | DB·Redis·JWT 환경변수 | 충족: application.yaml. 비밀값 기본은 빈 문자열로 fail closed |
| 8 | 개발 compose 일괄 실행·.env 미추적 | 구성 충족/사람 확인 필요: compose.yaml + override, git ls-files에 .env 없음. 실제 up 미수행 |
| 9 | 운영 blue/green 4개·공유 3개·nginx 포트만 공개 | 정적 확인 충족/실행 확인 필요: compose.prod.yaml, 필수 변수 :? 적용 |
| 10 | 운영 nginx 원래 전달 헤더·upstream include | 충족: nginx/server.conf에 scheme 변수 없음, upstream include 존재 |
| 11 | deploy/rollback/status·헬스 URL·셸 문법 | 충족: bash -n, 스크립트 대역 테스트 4개, 127.0.0.1:3000/api/health |
| 12 | test → main 이미지 → SSH 배포 | 구성 충족/사람 확인 필요: deploy.yml, github.repository 기반 matrix·GHCR, ~/DevMozip, git pull --ff-only, 커밋 sha 일치·불변 태그. GitHub 실행 미수행 |
| 13 | 참고 프로젝트 문자열 미잔존 | 충족: 기존 localStore 주석까지 정리하고 저장소 검색에서 일치 없음 |
| 14 | 필수 검증 전부 통과 | 미충족: 컴파일·타입·셸·YAML 통과, Docker 미설치로 compose config 실행 실패 |
| 15 | 프로젝트 실제 값의 환경 안내 5절 | 충족: docs/setup-after-ai.md. 호스트 주소·팀 번호·도메인만 운영별로 채움 |

## 사람이 이어서 확인할 항목

Docker Desktop WSL 통합 후 로컬 Compose config/build/up과 실제 Redis 폐기·재발급을 검증한다. GitHub Secrets·GHCR 권한·호스트 clone/.env/TLS/전달 헤더를 준비한다. develop 검증 결과를 리뷰한 다음 사용자 요청에 따라 main에 반영한다. 실제 무중단 및 롤백은 운영 호스트에서 확인한다. 회원가입·사용자 DB·도메인 API는 다음 단계의 별도 승인 범위다.

## 후속 서버 검증 결과

로컬 Docker 부재로 미검증이었던 항목은 이후 team2@cloud.newlecture.com의 실제 Docker에서 검증했다. 이 후속 기록이 위 최초 단계의 미수행 상태를 갱신한다. DevMozip 포트는 사용자 지정 3002다. 개발/운영 Compose config, Docker 이미지 빌드와 운영 컨테이너 기동, 실제 PostgreSQL·Redis 기반 인증 및 토큰 폐기, blue/green 전환·롤백이 통과했다. 전환 중 헬스체크 87회 실패 0이었다. 따라서 완료 기준 14의 Compose config 검증도 충족했다. 개발 compose 전체 up, GitHub Actions/GHCR, 운영 도메인과 브라우저 HTTPS 확인은 여전히 미수행이다. [서버 검증 기록](server-validation.md)에 구체적인 결과와 검증 폴더/운영 폴더 구분을 남겼다.

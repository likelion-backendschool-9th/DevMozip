# 팀 공유: 인증 분리와 배포 브랜치 운영

> 최신 정식 배포 상태·운영 명령은 [운영 안내](deployment-operations.md)를 참조하세요. 아래 미완료 항목은 해당 검증 단계의 기록입니다.

2026-11-03 사용자 합의에 따라 JWT+BFF 인증과 배포 기반을 develop에서 구현한다. 기존 발표용 Mock 기능과 데이터는 보존한다. 이 글은 저장소 내 팀 공유 문서이며 외부 채널에는 게시하지 않았다.

## 브랜치 사용

- 작업 시작 시 develop 기준으로 feature 브랜치를 만들거나 develop에서 작업한다. 테스트 후 develop 대상 PR로 통합한다.
- develop 푸시와 PR은 GitHub Actions의 검증만 실행한다. GHCR 이미지 발행·호스트 배포는 하지 않는다.
- 검증과 운영 설정을 마친 후 develop → main PR을 병합한다. main 푸시는 곧 운영 이미지 발행과 배포이므로 사용자 요청/팀 리뷰 후 진행한다.
- 이번 작업에서는 main 병합·커밋·푸시·실제 서버 배포를 수행하지 않는다. 작업 시작 시 이미 develop이었고 로컬 main과 같은 커밋이었다. 앞선 대화에서 언급된 ‘로컬 main 40커밋 선행’ 상태를 현재 상태로 가정하지 않는다.

## 계정과 인증

로그인은 백엔드 users 테이블의 계정으로 한다. 데모 계정(member·premium·admin@devmozip.demo)은 서버 DB에 등록되어 있다. 로컬 H2(local 프로필)에서는 `members_data.sql`이 시작 시 넣는다. 서버의 기본 설정은 초기 데이터 SQL을 실행하지 않는다. 회원가입은 직군·기술 스택·관심 분야를 users 테이블의 job_categories·skills·interests 칼럼(PostgreSQL JSONB)에 저장한다. 허용 값은 백엔드 entity/enums의 enum으로 검증하고, 가입 화면은 GET /api/v1/auth/signup/options로 같은 목록을 받는다. 회원정보 수정 API도 같은 enum으로 검증해야 한다. 매칭·검색·프로젝트 등 Mock 화면의 선택지는 frontend/mocks에 있다.

| 구분 | 동작 |
|---|---|
| 실행 | next dev·운영 빌드 모두 서버 인증. 로그인하려면 백엔드가 실행 중이어야 한다 |
| 로그인 | 백엔드 users 테이블 계정, BCrypt 비밀번호 비교 |
| 로그인 상태 | 액세스·리프레시 HttpOnly 쿠키 |
| 권한 근거 | JWT 서명을 백엔드가 검증한 GET /api/v1/users/me 응답 (USER·PREMIUM·ADMIN) |
| 계정 전환 | 헤더 로그아웃 후 다른 계정으로 로그인 |
| 회원가입 | POST /api/v1/auth/signup으로 users에 저장한 뒤 POST /api/v1/auth/login으로 바로 로그인 |
| 이메일·닉네임 중복 확인 | GET /api/v1/auth/check-email, /check-nickname으로 users 테이블 조회. 제출할 때도 서버가 다시 확인 |
| 카카오 로그인·가입 | 카카오 인증 후 kakao_id로 회원을 찾아 같은 JWT를 발급. 미가입이면 비밀번호 없이 같은 회원가입 API로 가입 |
| 카카오 연동·해제 | /settings/privacy에서 처리. 비밀번호가 없으면 해제할 수 없다 |
| 비밀번호 설정 | kakao_id가 있고 password_hash가 없는 회원은 현재 비밀번호 없이 첫 비밀번호를 저장 |
| 비밀번호 변경 | 비밀번호가 있는 회원은 미제공 안내 |

로그인 폼은 서버 액션을 React 전환 안에서 호출한다. 액션이 HttpOnly 쿠키를 설정한 성공 응답과 안전한 복귀 경로를 반환하면 클라이언트가 새 문서 요청으로 이동한다. 기본 목적지는 `/`, `next`가 있으면 검증한 내부 경로이며 `/login` 자기 복귀는 `/`로 대체한다. 로그인 전 화면 캐시·로컬 세션 상태를 이어서 사용하지 않고 새 페이지에서 BFF 세션을 다시 확인한다.

토큰은 localStorage나 API JSON 응답을 통해 브라우저 JavaScript에 전달하지 않는다. 브라우저는 같은 출처의 BFF와 서버 액션만 호출한다.

프로필 표시는 아직 Mock 데이터를 쓴다. 로그인한 이메일이 데모 이메일과 같으면 해당 Mock 프로필을 재사용하고, 새로 가입한 계정을 포함한 그 밖의 이메일은 첫 번째 데모 프로필을 표시한다. 화면 역할은 서버 역할을 따른다(USER→일반, PREMIUM→프리미엄, ADMIN→관리자). 브라우저의 구독 데모·관리자 데모 제재는 화면 표시에만 반영하며 이것으로 관리자 역할을 얻을 수는 없다. proxy의 /admin 접근 판단과 백엔드 인가는 토큰 역할만 사용한다.

계약과 흐름은 [카카오 인증 API](kakao-auth-api.md), 환경변수는 [환경 준비 안내](setup-after-ai.md)를 참조한다.

## 아직 Mock인 기능

프로젝트·게시글·포트폴리오·결제·구독·신고·관리자 도메인은 계속 브라우저 기반 데모다. 메신저(1:1 대화, 프로젝트 단톡방, 메신저 사용자 신고, 실시간 전달·입력 중 표시)는 Mock 토글을 끈 로그인 회원에게 서버 API와 WebSocket으로 동작한다([메신저 API](messenger-api.md)). 토글을 켜면 기존 Mock 메신저를 쓴다. 실제 데이터 저장·공유·업무 API 인가는 이번 구현에 포함하지 않는다. 관리자 화면의 데모 관리도 실제 서버 회원을 변경하지 않는다. 인증 도입만으로 업무 서비스가 운영 가능한 상태가 되는 것은 아니다.

비밀번호가 있는 회원의 실제 비밀번호 변경 API는 아직 없다.

## 토큰 동작과 제한

액세스 기본 30분, 리프레시 기본 14일이다. 한 username당 Redis 리프레시 토큰 하나를 저장하므로 새 로그인이 기존 리프레시 토큰을 대체한다. UUID jti로 같은 초에 재로그인해도 새 리프레시 토큰을 만든다. 로그아웃은 일치하는 리프레시 토큰만 원자적으로 삭제한다. 블루그린의 두 백엔드가 같은 Redis를 쓰므로 재시작·색 전환 후에도 로그인이 유지된다. local(H2) 프로필은 Redis 없이 메모리에 저장하며 재시작하면 사라진다. 이미 발행된 액세스 JWT는 만료까지 유효하며 즉시 폐기 목록은 이번 범위에 없다.

proxy의 JWT 디코딩은 화면 이동 최적화용이며 서명을 검증하지 않는다. 실제 API는 백엔드가 서명·만료·역할을 검증하고 BFF 세션도 백엔드 /api/v1/users/me로 확인한다. 서버 인증 장애에서 Mock 로그인으로 자동 전환하지 않는다. 로그아웃 서버 처리에 실패하면 쿠키를 유지하고 재시도 안내를 제공한다.

환경변수 JWT_ACCESS_EXPIRATION을 변경하면 쿠키 만료는 토큰 exp를 따른다. JWT_REFRESH_EXPIRATION을 기본 14일과 다르게 바꾸려면 프론트 REFRESH_MAX_AGE도 같이 맞춘다.

## 배포와 팀원 준비

[환경 준비 안내](setup-after-ai.md)를 로컬 → GitHub → 호스트 → 첫 배포 순서로 따른다. [분석·검증 기록](auth-deployment-validation.md)에 실제 수행한 검증과 미수행 항목을 남긴다. 운영 주소·팀 계정·SSH 키·GitHub Secrets는 저장소 파일만으로 설정되지 않는다.

Dockerfile, compose*.yaml, nginx/, deploy/blue-green.sh, .github/workflows/deploy.yml이 배포 기반이다. 호스트는 ~/DevMozip의 main을 사용한다. 검증된 커밋의 sha 태그를 배포하고 새 색의 /api/health가 백엔드까지 정상 응답한 뒤에만 nginx를 전환한다. 이전 색은 정지만 하므로 rollback으로 다시 켤 수 있다. 동시 배포는 잠금으로 차단한다. 다른 프로젝트의 컨테이너와 이미지는 정리하지 않는다.

참고: [Spring Security 인증 구조](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/architecture.html), [JJWT 공식 구현 안내](https://github.com/jwtk/jjwt), [nginx upstream resolve](https://nginx.org/en/docs/http/ngx_http_upstream_module.html). nginx의 동적 DNS resolve를 위해 1.27.5를 사용한다.

## 조별 서버 검증 후속 기록

team2@cloud.newlecture.com에서 DevMozip을 127.0.0.1:3002로 검증했다. linker는 다른 조 서비스라 변경하지 않는다. 처음의 3102 설정은 기동 전에 3002로 정정했다. develop 소스의 Docker 빌드·실제 PostgreSQL/Redis 인증·블루그린 전환과 롤백을 확인했다. main 배포와 공개 도메인 연결은 아직 하지 않았다. 자세한 상태와 미완료 항목은 [서버 검증 기록](server-validation.md)에 남긴다.

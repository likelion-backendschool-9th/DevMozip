# 팀 공유: 인증 분리와 배포 브랜치 운영

2026-11-03 사용자 합의에 따라 JWT+BFF 인증과 배포 기반을 develop에서 구현한다. 기존 발표용 Mock 기능과 데이터는 보존한다. 이 글은 저장소 내 팀 공유 문서이며 외부 채널에는 게시하지 않았다.

## 브랜치 사용

- 작업 시작 시 develop 기준으로 feature 브랜치를 만들거나 develop에서 작업한다. 테스트 후 develop 대상 PR로 통합한다.
- develop 푸시와 PR은 GitHub Actions의 검증만 실행한다. GHCR 이미지 발행·호스트 배포는 하지 않는다.
- 검증과 운영 설정을 마친 후 develop → main PR을 병합한다. main 푸시는 곧 운영 이미지 발행과 배포이므로 사용자 요청/팀 리뷰 후 진행한다.
- 이번 작업에서는 main 병합·커밋·푸시·실제 서버 배포를 수행하지 않는다. 작업 시작 시 이미 develop이었고 로컬 main과 같은 커밋이었다. 앞선 대화에서 언급된 ‘로컬 main 40커밋 선행’ 상태를 현재 상태로 가정하지 않는다.

## 계정과 인증을 분리한 이유

현재 백엔드에 사용자 엔티티·테이블·회원가입 API는 없다. 기존 회원가입은 브라우저 localStorage의 salt + SHA-256 데모 해시를 사용한다. SHA-256 해시를 BCrypt로 직접 변환할 수 없으므로 계정을 자동 이관하거나 기존 데이터를 삭제하지 않는다.

| 구분 | 개발 Mock 모드 | 서버 인증 모드 |
|---|---|---|
| 실행 | next dev, AUTH_MODE=mock 또는 미설정 | next dev + AUTH_MODE=server, 운영 빌드는 항상 server |
| 로그인 | 기존 브라우저 계정·데모 선택 | Spring Security의 임시 USER·ADMIN 두 계정 |
| 비밀번호 | 기존 SHA-256 데모 검증 유지 | 환경변수 입력을 시작 시 BCrypt로 인코딩 |
| 로그인 상태 | 기존 localStorage/sessionStorage | 액세스·리프레시 HttpOnly 쿠키 |
| 권한 근거 | 기존 Mock 상태 | JWT 서명을 백엔드가 검증한 /api/auth/me 응답 |
| 데모 계정 전환 | 기존처럼 사용 | 차단; 관리자·프리미엄 데모로 서버 권한을 얻을 수 없음 |
| 가입·비밀번호 변경 | 기존 데모 사용 | 미제공 안내; 임시 인증 계정 이용 |

AUTH_MODE는 Next 서버 전용이다. 운영에서는 AUTH_MODE=mock을 주어도 Mock 인증을 켤 수 없다. next dev만 실행하는 발표 시연은 백엔드 없이 가능하다. 로컬 환경을 전환할 때 Next 서버를 재시작한다. 토큰은 localStorage나 API JSON 응답을 통해 브라우저 JavaScript에 전달하지 않는다. 브라우저는 같은 출처의 BFF와 서버 액션만 호출한다.

프로필 표시를 기존 화면과 연결하려고 임시 인증 계정의 이메일이 데모 이메일과 같으면 해당 프로필 표시를 재사용한다. 이는 실제 사용자 DB 연동이 아니다. USER는 프리미엄 이메일이어도 free, ADMIN만 admin으로 표시한다. 기존 브라우저의 계정·프로필·구독 상태는 남아 있지만 서버 인증 역할을 결정하지 않는다.

## 아직 Mock인 기능

프로젝트·게시글·포트폴리오·메시지·결제·구독·신고·관리자 도메인은 계속 브라우저 기반 데모다. 실제 데이터 저장·공유·업무 API 인가는 이번 구현에 포함하지 않는다. 관리자 화면의 데모 관리도 실제 서버 회원을 변경하지 않는다. 인증 도입만으로 업무 서비스가 운영 가능한 상태가 되는 것은 아니다.

실제 회원가입·사용자 DB는 별도 단계에서 엔티티/스키마/역할 정책과 함께 설계한다. premium은 구독 상태이고 서버 인증 역할은 USER/ADMIN이다. 임시 계정 저장소를 실제 UserDetailsService로 교체하기 전까지 두 임시 계정은 서버 재시작 후 같은 환경변수로 재생성한다.

## 토큰 동작과 제한

액세스 기본 30분, 리프레시 기본 14일이다. 한 username당 Redis 리프레시 토큰 하나를 저장하므로 새 로그인이 기존 리프레시 토큰을 대체한다. UUID jti로 같은 초에 재로그인해도 새 리프레시 토큰을 만든다. 로그아웃은 일치하는 리프레시 토큰만 원자적으로 삭제한다. 이미 발행된 액세스 JWT는 만료까지 유효하며 즉시 폐기 목록은 이번 범위에 없다.

proxy의 JWT 디코딩은 화면 이동 최적화용이며 서명을 검증하지 않는다. 실제 API는 백엔드가 서명·만료·역할을 검증하고 BFF 세션도 백엔드 /api/auth/me로 확인한다. 서버 인증 장애에서 Mock 로그인으로 자동 전환하지 않는다. 로그아웃 서버 처리에 실패하면 쿠키를 유지하고 재시도 안내를 제공한다.

환경변수 JWT_ACCESS_EXPIRATION을 변경하면 쿠키 만료는 토큰 exp를 따른다. JWT_REFRESH_EXPIRATION을 기본 14일과 다르게 바꾸려면 프론트 REFRESH_MAX_AGE도 같이 맞춘다.

## 배포와 팀원 준비

[환경 준비 안내](setup-after-ai.md)를 로컬 → GitHub → 호스트 → 첫 배포 순서로 따른다. [분석·검증 기록](auth-deployment-validation.md)에 실제 수행한 검증과 미수행 항목을 남긴다. 운영 주소·팀 계정·SSH 키·GitHub Secrets는 저장소 파일만으로 설정되지 않는다.

Dockerfile, compose*.yaml, nginx/, deploy/blue-green.sh, .github/workflows/deploy.yml이 배포 기반이다. 호스트는 ~/DevMozip의 main을 사용한다. 검증된 커밋의 sha 태그를 배포하고 새 색의 /api/health가 백엔드까지 정상 응답한 뒤에만 nginx를 전환한다. 이전 색은 정지만 하므로 rollback으로 다시 켤 수 있다. 동시 배포는 잠금으로 차단한다. 다른 프로젝트의 컨테이너와 이미지는 정리하지 않는다.

참고: [Spring Security 인증 구조](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/architecture.html), [JJWT 공식 구현 안내](https://github.com/jwtk/jjwt), [nginx upstream resolve](https://nginx.org/en/docs/http/ngx_http_upstream_module.html). nginx의 동적 DNS resolve를 위해 1.27.5를 사용한다.

## 조별 서버 검증 후속 기록

team2@cloud.newlecture.com에서 DevMozip을 127.0.0.1:3002로 검증했다. linker는 다른 조 서비스라 변경하지 않는다. 처음의 3102 설정은 기동 전에 3002로 정정했다. develop 소스의 Docker 빌드·실제 PostgreSQL/Redis 인증·블루그린 전환과 롤백을 확인했다. main 배포와 공개 도메인 연결은 아직 하지 않았다. 자세한 상태와 미완료 항목은 [서버 검증 기록](server-validation.md)에 남긴다.

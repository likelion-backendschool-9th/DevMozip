# DevMozip 인증·배포 환경 준비

> 최신 정식 배포 상태·운영 명령은 [운영 안내](deployment-operations.md)를 참조하세요. 아래 미완료 항목은 해당 검증 단계의 기록입니다.

코드는 develop에서 검증한 뒤 main에 병합한다. 아래 외부 설정을 준비하기 전에는 main에 배포 변경을 반영하지 않는다. [팀 공유 글](team-auth-deployment.md)의 Mock/서버 인증 구분을 먼저 읽는다. 실제 도메인 데이터는 여전히 Mock이다.

## 1. 로컬 — 팀원 각자

Docker Desktop을 설치·실행하고 WSL을 쓰면 Settings의 WSL Integration을 켠다. 저장소 루트에서 다음을 실행한다.

```bash
cp .env.example .env
```

DB_PASSWORD, JWT_SECRET, AUTH_USER_PASSWORD, AUTH_ADMIN_PASSWORD의 예제 자리표시를 임의의 고유 값으로 교체한다. JWT_SECRET은 `openssl rand -hex 32`로 만들 수 있다. 비밀값을 채팅·커밋·로그에 공유하지 않는다. 나머지 로컬 기본값은 유지해도 된다: COMPOSE_PROJECT_NAME=devmozip, DB_NAME=devmozip, DB_USER=devmozip, DB_PORT=5432, FRONTEND_PORT=3000, BACKEND_PORT=8080, HTTP_PORT=80, HTTPS_PORT=443. 포트 충돌 시 바꾼다. DB_PORT는 호스트 공개 포트이며 컨테이너 내부는 항상 5432다.

프론트 로그인은 JWT+BFF 서버 인증을 사용한다. AUTH_USER_USERNAME 기본값은 member@devmozip.demo, AUTH_ADMIN_USERNAME은 admin@devmozip.demo다. 비밀번호는 새로 설정한 각 환경변수 값이다. 기존 Mock 데모 비밀번호·브라우저 가입 계정은 서버 계정으로 이관되지 않는다.

```bash
docker compose up -d
docker compose ps
docker compose logs backend frontend
```

http://localhost:3000에서 확인한다. Dockerfile의 첫 빌드에는 의존성 다운로드 시간이 걸린다. 서버 인증 모드에서 로그인 후 개발자 도구 Application → Cookies의 devmozip_access, devmozip_refresh 두 쿠키가 HttpOnly인지, Network에 브라우저가 backend:8080 또는 localhost:8080을 직접 부르는 요청이 없는지 확인한다. `curl -f http://localhost:3000/api/health`도 확인한다. Adminer가 필요하면 `docker compose --profile tools up -d` 후 http://localhost:8082를 연다.

프론트만 Mock 시연하려면 frontend에서 `npm ci && npm run dev`를 실행한다. 이 경우 .env 없이도 되며 기본 포트는 3000이다. frontend/.env.example을 frontend/.env.local로 복사하면 서버 인증 모드를 선택할 수 있다. BACKEND_URL은 서버 전용이며 기본 http://localhost:8080이다. 루트 .env는 Compose가 읽고 frontend/.env.local은 Next가 읽는다. 호스트 직접 백엔드 실행은 루트 .env 변수들을 실행 환경에 전달해야 한다.

선택 카카오 로그인:

루트 `.env.local`(Git 제외)에 KAKAO_REST_API, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI, DOMAINNAME을 넣는다. 형식은 `.env.example`을 따른다. `{DOMAINNAME}/{KAKAO_REDIRECT_URI}`는 카카오 콘솔에 등록한 Redirect URI와 정확히 같아야 한다. local 프로필이 이 파일을 읽도록 각자의 `application-local.yml`(Git 제외)에 아래를 추가하고 `backend` 폴더에서 `./gradlew bootRun`으로 실행한다.

```yaml
spring:
  config:
    import: optional:file:../.env.local[.properties]
```

값이 없으면 카카오 API만 503으로 응답하고 다른 기능은 그대로 동작한다. 서비스 주소에 HOSTNAME을 쓰지 않는다. Git Bash가 PC 이름으로 export해 파일 값을 덮어쓴다. 프론트엔드는 카카오 값을 쓰지 않으므로 `frontend/.env*`에 넣지 않는다.

선택 비밀번호 재설정 메일:

MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM을 넣는다. 형식은 `.env.example`을 따른다. Gmail은 2단계 인증을 켠 계정에서 [앱 비밀번호](https://myaccount.google.com/apppasswords)를 발급해 MAIL_PASSWORD에 공백 없이 넣고, MAIL_FROM을 MAIL_USERNAME과 같게 둔다. 구글 계정 비밀번호를 바꾸면 앱 비밀번호도 폐기되므로 다시 발급한다.

| 실행 방식 | 값을 읽는 위치 | 값이 없을 때 |
|---|---|---|
| `docker compose` | 루트 `.env` | 재설정 요청만 503 |
| `./gradlew bootRun` (local 프로필) | 루트 `.env.local` | 메일 대신 링크를 백엔드 콘솔에 출력. 서비스 주소 기본값은 `http://localhost:3000` |

`compose.yaml`은 Git 제외 파일이므로 compose로 실행하는 사람은 각자 backend의 environment에 아래 다섯 줄을 추가한 뒤 `docker compose up -d --force-recreate --no-deps backend`로 다시 만든다.

```yaml
MAIL_HOST: ${MAIL_HOST:-}
MAIL_PORT: ${MAIL_PORT:-587}
MAIL_USERNAME: ${MAIL_USERNAME:-}
MAIL_PASSWORD: ${MAIL_PASSWORD:-}
MAIL_FROM: ${MAIL_FROM:-}
```

계약과 동작은 [비밀번호 재설정·변경 API](password-reset-api.md)를 참조한다.

선택 로컬 HTTPS:

- Windows: `choco install mkcert`, macOS: `brew install mkcert`, Ubuntu: `sudo apt install mkcert`.
- `mkcert -install`을 한 번 실행한다. WSL에서는 브라우저가 Windows 인증서 저장소를 사용하므로 Windows 쪽에 설치하고 Windows 터미널에서 -install한다.
- 저장소에 certs 디렉터리를 만들고 그 안에서 `mkcert -cert-file localhost.pem -key-file localhost-key.pem localhost 127.0.0.1 ::1`로 발급한다. Windows에서 발급했다면 두 파일을 저장소 certs/로 복사한다.
- `docker compose --profile https up -d` 후 https://localhost를 연다. certs/는 커밋하지 않는다.

호스트에서 `./gradlew test` 등을 직접 실행하기 전 `docker compose stop backend`를 실행한다. 컨테이너 bootRun과 호스트 Gradle이 backend/build를 동시에 쓰면 충돌할 수 있다.

## 2. GitHub — 팀에서 한 번

저장소: https://github.com/likelion-backendschool-9th/DevMozip

Settings → Actions → General → Workflow permissions에서 Read and write permissions를 설정한다. 조직 정책이 막으면 관리자에게 요청한다. 워크플로의 build-push job에도 packages: write 권한이 있다.

배포 전용 키를 개인 PC에서 만든다. 개인용 SSH 키를 공유하지 않고 필요시 배포 키만 교체할 수 있게 분리한다.

```bash
ssh-keygen -t ed25519 -C "deploy" -f deploy-key -N ""
```

Settings → Secrets and variables → Actions → New repository secret에 다음 세 값을 등록한다.

| 이름 | 값 |
|---|---|
| DEPLOY_HOST | cloud.newlecture.com |
| DEPLOY_USER | team2 |
| DEPLOY_SSH_KEY | deploy-key 개인 키 파일 전체 내용, 마지막 줄바꿈 포함 |

공개 키는 아래 호스트 등록에 사용한다. 개인 키는 등록과 접속 확인 후 안전하게 삭제한다. 저장소에 키를 넣지 않는다.

첫 build-push 뒤 소유 조직의 Packages에서 devmozip-backend, devmozip-frontend 각각 Package settings → Danger Zone → Change visibility → Public으로 바꾼다. 이미지 전체 이름은 ghcr.io/likelion-backendschool-9th/devmozip-backend와 ghcr.io/likelion-backendschool-9th/devmozip-frontend다. 비공개 유지 시 호스트에서 read:packages 권한이 있는 별도 자격증명으로 `docker login ghcr.io`를 실행한다. 조직 정책에 따라 공개 전환 가능 여부를 확인한다.

권장: main 브랜치에 Require a pull request before merging과 필수 CI 체크를 설정한다. develop 검증 → main PR 병합 → 배포 순서로 운영한다. develop은 배포하지 않으며 workflow_dispatch도 테스트만 실행한다.

## 3. 호스트 — 팀 계정으로 한 번

호스트 관리자가 team<N> 계정, devmozip.newlecture.com, 호스트 nginx/TLS 인증서, 포트 3002, 홈의 .env 초안을 준비했는지 확인한다. 이는 이번 코드 작업으로 생성되지 않는다. 팀 계정에 Docker Compose와 flock(util-linux) 실행 권한이 필요하다.

```bash
ssh-copy-id -i deploy-key.pub team2@cloud.newlecture.com
ssh -i deploy-key team2@cloud.newlecture.com 'docker compose version'
```

ssh-copy-id가 안 되면 관리자에게 공개 키 한 줄을 /home/team2/.ssh/authorized_keys에 추가해 달라고 요청한다.

호스트에서 다음을 실행한다.

```bash
git clone https://github.com/likelion-backendschool-9th/DevMozip.git ~/DevMozip
cd ~/DevMozip
git switch main
# 기존 검증 DB와 연결할 때는 비밀번호를 유지한다.
if [ -f ~/DevMozip-validation/.env ]; then
  cp ~/DevMozip-validation/.env ~/DevMozip/.env
else
  cp .env.example ~/DevMozip/.env
fi
chmod 600 ~/DevMozip/.env
```

경로는 워크플로의 `cd ~/DevMozip`과 정확히 같아야 한다. 비공개 저장소라면 호스트의 git 읽기 인증도 별도로 준비한다.

.env의 IMAGE_PREFIX는 **ghcr.io/likelion-backendschool-9th/devmozip**(소문자), IMAGE_TAG는 latest로 설정한다. 자동 배포는 검증된 커밋 sha 태그로 덮어쓴다. COMPOSE_PROJECT_NAME은 팀별 고유 값, TEAM_PORT는 할당된 3002이다. DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET, AUTH_USER_USERNAME, AUTH_USER_PASSWORD, AUTH_ADMIN_USERNAME, AUTH_ADMIN_PASSWORD를 채운다. DB 이름/사용자 기본 예시는 devmozip이다. 비밀번호·JWT_SECRET은 팀마다 새로 만든다. JWT_SECRET이 같으면 다른 팀 토큰의 서명을 신뢰할 수 있어 위험하다. 운영에서는 AUTH_MODE=server가 고정된다. 카카오 로그인을 쓰려면 KAKAO_REST_API, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI, DOMAINNAME도 채우고 운영 Redirect URI를 카카오 콘솔에 등록한다. 값이 없으면 카카오 기능만 꺼진다. 비밀번호 재설정 메일을 쓰려면 MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM과 DOMAINNAME을 채운다. DOMAINNAME은 메일 링크의 주소가 되므로 운영 주소여야 한다. 값이 없으면 재설정 요청만 503이다.

```bash
docker compose -f compose.prod.yaml --profile blue --profile green config --quiet
```

필수 변수 누락 시 명령이 실패해야 한다. config 출력 전체에는 비밀값이 포함될 수 있어 공유하지 않는다. 운영은 nginx의 127.0.0.1:TEAM_PORT:80만 공개하며 DB·Redis·백엔드·프론트 포트는 외부에 열지 않는다. 호스트 nginx가 Host, X-Real-IP, X-Forwarded-For, X-Forwarded-Proto=https, X-Forwarded-Host를 전달해야 한다. X-Real-IP는 비밀번호 재설정 요청의 IP 제한에도 쓰이므로 클라이언트가 보낸 값을 그대로 넘기지 말고 실제 접속 IP로 덮어써야 한다. 운영 쿠키는 Secure이므로 HTTPS로 접속한다. DB 스키마/업무 테이블을 만들거나 변경하지 않는다.

## 4. 첫 배포와 확인

외부 설정을 마치고 develop 검증 후 사용자 요청/리뷰에 따라 main으로 병합·푸시한다. Actions에서 test → build-push → deploy 순서를 확인한다. 첫 deploy가 이미지 비공개로 denied면 2절의 공개 전환/호스트 로그인 후 Re-run jobs한다. 더 최신 main 커밋이 있으면 이전 작업 재실행 대신 최신 커밋의 작업을 사용한다. 배포 스크립트는 현재 main HEAD와 CI 커밋 일치를 확인한다.

https://devmozip.newlecture.com에서 임시 인증 계정으로 로그인한다. 호스트에서 다음을 실행한다.

```bash
cd ~/DevMozip
bash deploy/blue-green.sh status
```

작은 수정을 develop에서 검증하고 main에 병합해 배포 중 새로고침을 확인한다. 이 무중단 확인은 실제 호스트에서 수행해야 한다. 되돌리기는 다음과 같다.

```bash
bash deploy/blue-green.sh rollback
IMAGE_TAG=sha-xxxxxxx bash deploy/blue-green.sh deploy
```

rollback은 정지된 이전 컨테이너를 그대로 다시 켠다. 특정 커밋 배포는 위 sha-xxxxxxx를 GHCR에 존재하는 실제 7자리 커밋 태그로 바꾼다. 첫 배포에는 이전 색이 없으므로 rollback할 수 없다. frontend/backend 새 색의 헬스체크에 실패하면 활성 색은 유지한다. 다른 프로젝트의 컨테이너·이미지·볼륨은 지우지 않는다.

## 5. 흔한 첫 실패와 해결

| 증상 | 확인·해결 |
|---|---|
| build-push denied: permission_denied | Workflow permissions와 조직 패키지 정책 |
| deploy Permission denied (publickey) | authorized_keys 등록, DEPLOY_SSH_KEY 전체 내용·마지막 줄바꿈 |
| cd: ~/DevMozip: No such file | 호스트에 정확한 경로로 clone |
| compose pull denied/unauthorized | 패키지 공개 또는 호스트 GHCR 로그인 |
| manifest unknown | IMAGE_PREFIX 소문자·오타, 실제 이미지 태그 존재 여부 |
| 로그인 후 세션이 사라짐 | 운영 이미지는 Secure 쿠키이므로 호스트 HTTPS 도메인으로 접속 |
| gradlew FileHasher 오류 | docker compose stop backend 후 호스트 Gradle 실행 |
| next build가 node 없음/WSL 오류 | Linux용 Node 24를 PATH 앞에 배치; Windows npm을 WSL에서 호출하지 않기 |
| /api/health 503 | 해당 색 backend/frontend 로그, DB·Redis 연결, JWT_SECRET 32바이트 이상, 두 임시 계정 비밀번호 설정 |
| 로그아웃 실패 안내 | Redis/백엔드 복구 후 재시도; 토큰 폐기 실패를 성공으로 표시하지 않음 |

## 현재 서버 확인 결과 (2026-11-03)

실제 SSH 대상은 team2@cloud.newlecture.com이다. DevMozip에 배정된 포트는 **3002**, COMPOSE_PROJECT_NAME은 **devmozip**이다. linker는 다른 조 서비스이므로 컨테이너·볼륨·도메인·nginx 설정을 변경하지 않는다.

현재 develop 작업 소스를 /home/team2/DevMozip-validation에서 빌드해 실행했다. 운영 Git clone/main 배포와 구분한다. 해당 폴더의 .env에는 서버에서 생성한 임시 인증 비밀번호·DB 비밀번호·JWT 비밀키가 있으며 권한은 600이다. 값을 출력하거나 저장소에 복사하지 않는다. 나중에 같은 devmozip DB 볼륨을 운영 폴더에서 사용할 때는 이 DB 비밀번호를 보존해야 한다. DB 환경변수만 바꿔도 이미 초기화된 PostgreSQL 사용자의 비밀번호가 변경되지는 않는다. 운영 이미지 경로는 ghcr.io/likelion-backendschool-9th/devmozip로 바꾼다.

로컬 전용 SSH 키 경로는 ~/.ssh/devmozip-deploy이며 공개 키가 서버 team2 계정에 등록돼 있다. 개인 키 내용은 문서나 저장소에 넣지 않는다. 이 키는 현재 서버 작업에도 사용하므로 GitHub Secret 등록 전후에 무작정 삭제하지 않는다.

검증 서비스는 127.0.0.1:3002에만 바인딩한다. 공개 도메인은 아직 연결하지 않았다. 기존 linker 도메인을 DevMozip으로 바꾸지 않는다. 상세 결과는 [서버 검증 기록](server-validation.md)을 참고한다.

## 도메인 확정 후 남은 설정

공개 주소는 https://devmozip.newlecture.com이며 DNS는 cloud.newlecture.com의 서버 주소로 해석됨을 확인했다. 현재 호스트는 sudo 비밀번호 인증을 요구한다. 서버에서 다음 한 번을 실행하면 DevMozip 가상 호스트와 TLS를 설정한다. 기존 다른 조 가상 호스트는 수정하지 않는다.

```bash
sudo bash ~/DevMozip-validation/deploy/setup-host.sh
```

스크립트는 먼저 3002의 헬스를 확인하고 nginx 설정 검사 뒤 reload한다. Certbot의 기존 계정으로 인증서를 발급/설치하며 계정이 없으면 그 오류를 보고한다. 이 경우 관리자에게 인증서 계정 등록을 요청한다. 확인 URL은 `https://devmozip.newlecture.com/api/health`다. 스크립트의 근거는 [Certbot 공식 명령 안내](https://eff-certbot.readthedocs.io/en/stable/man/certbot.html)다.

GitHub 연결 도구는 이 비공개 저장소에 대해 404를 반환했고 서버의 HTTPS git 인증도 설정돼 있지 않았다. GitHub 자동 배포 전에 로컬 push 인증, 서버의 저장소 읽기 인증, 저장소 Secrets 3개 등록이 필요하다. HTTPS 인증 토큰이나 개인 키를 채팅·커밋에 넣지 않는다.

운영 워크플로는 deploy job의 packages:read GITHUB_TOKEN으로 호스트에서 GHCR 로그인하며 임시 DOCKER_CONFIG를 배포 종료 시 제거한다. 따라서 패키지를 Public으로 바꾸거나 호스트에 장기 PAT를 저장하지 않아도 자동 배포가 가능하다. 수동 pull/특정 태그 배포에는 별도의 GHCR 읽기 인증이 필요할 수 있다.

조직 정책 확인 결과 Deploy Key 등록과 기본 Workflow write 권한 변경이 금지돼 있었다. 조직 정책은 변경하지 않는다. 명시된 job packages:write/read 및 contents:read 권한을 사용한다. 서버 git pull은 배포 작업의 GITHUB_TOKEN과 임시 GIT_ASKPASS로 인증하며 토큰과 helper는 배포 종료 시 함께 제거한다. 서버 읽기용 별도 키는 등록하지 않았으며 실제 git 인증에 사용하지 않는다.

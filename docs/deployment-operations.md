# DevMozip 운영·자동 배포 안내

2026-11-03 정식 배포를 완료했다. 공개 주소는 **https://devmozip.newlecture.com**이다. 아래 내용은 최초 검증 단계의 미완료 기록보다 최신 상태다.

## 현재 구성

- 서버: team2@cloud.newlecture.com, Docker 프로젝트 이름 devmozip.
- 정식 Git 작업 폴더: /home/team2/DevMozip, main 브랜치.
- 공개 HTTPS → 호스트 nginx → 127.0.0.1:3002 → 팀 nginx → 활성 색 frontend/backend → 공유 PostgreSQL·Redis.
- linker는 다른 조 서비스이므로 변경·정지·정리하지 않는다.
- 인증서 발급과 Certbot 자동 갱신 예약을 확인했다. 최초 인증서 만료일은 2027-02-01이다.
- 최초 정식 배포 커밋은 080c7d9(PR #23 병합), 이미지는 ghcr.io/likelion-backendschool-9th/devmozip-backend:sha-080c7d9 및 devmozip-frontend:sha-080c7d9였다. 최초 활성 색은 green이다. 이후 배포마다 색과 sha 태그는 바뀐다.

## 변경부터 배포까지

1. develop에서 작업·검증하고 push한다. 이 단계는 테스트만 실행한다.
2. develop → main PR을 만들고 검증 후 병합한다.
3. main push 이벤트로 test → build-push(backend/frontend) → deploy가 자동 실행된다.
4. 검증된 커밋의 sha 이미지가 쉬는 색에 기동되고, 프론트 BFF를 통한 백엔드 헬스체크가 통과하면 nginx가 전환된다. 이전 색은 정지 상태로 보존된다.

수동 workflow_dispatch는 검증만 한다. main에 문서만 수정해 push해도 현재 워크플로는 배포를 실행한다. 단순히 GitHub의 어느 브랜치를 업데이트하든 해당 브랜치가 배포되는 방식이 아니다.

최초 전체 성공 실행: https://github.com/likelion-backendschool-9th/DevMozip/actions/runs/41566024989

저장소의 DEPLOY_HOST, DEPLOY_USER, DEPLOY_SSH_KEY Secrets를 등록했다. 비공개 GHCR pull과 서버 git pull은 배포 작업별 GITHUB_TOKEN을 사용한다. 임시 Docker 설정과 GIT_ASKPASS는 배포 종료 시 제거한다. 서버에 장기 GitHub 토큰은 저장하지 않는다. 조직의 Deploy Key 금지·기본 workflow write 변경 금지 정책은 변경하지 않았다. job의 명시적 권한으로 실제 이미지 발행과 배포가 성공했다.

## 상태와 장애 확인

```bash
ssh team2@cloud.newlecture.com
cd ~/DevMozip
bash deploy/blue-green.sh status
curl -f https://devmozip.newlecture.com/api/health
docker compose -f compose.prod.yaml --profile blue --profile green ps -a
```

헬스는 HTTP 200과 {"status":"UP"}가 정상이다. status 첫 줄의 IMAGE_TAG는 현재 셸/설정값이며, 실제 실행 버전은 아래 컨테이너 표의 Image sha 태그로 확인한다.

현재 활성 색의 로그를 확인한다. 예를 들어 green이 활성일 때:

```bash
docker compose -f compose.prod.yaml logs --tail=100 backend-green frontend-green nginx
```

/home/team2/DevMozip-validation은 이전 소스 검증 폴더다. 현재 운영은 ~/DevMozip에서만 관리한다. 이전 폴더에서 배포 스크립트를 다시 실행하면 같은 Docker 프로젝트를 오래된 구성으로 변경할 수 있으므로 실행하지 않는다.

## 로그인과 데이터

임시 USER 기본 이메일은 member@devmozip.demo, ADMIN은 admin@devmozip.demo다. 비밀번호는 서버 ~/DevMozip/.env의 AUTH_USER_PASSWORD, AUTH_ADMIN_PASSWORD에 생성된 값이다. 본인 서버 터미널에서만 확인하고 채팅·Git·공개 문서에 붙이지 않는다. 기존 Mock 공용 비밀번호와 브라우저 가입 계정은 운영 로그인에 사용할 수 없다.

로그아웃은 헤더 프로필 옆의 로그아웃 버튼으로 실행한다. 다른 계정으로 바꾸려면 로그아웃 후 다시 로그인한다. 로그인 상태는 운영 쿠키 정책(액세스·리프레시 HttpOnly 쿠키)을 따른다.

업무 데이터는 계속 Mock이다. JWT+BFF 인증과 회원가입은 users 테이블을 쓰지만 실제 SNS·메시지 API는 구현하지 않았다. 프리미엄 구독·결제는 포트원 API로 동작하며(아래 "프리미엄 결제"), 결제가 서버 역할을 USER/PREMIUM으로 바꾼다. 화면의 이전 구독 데모 상태는 서버 역할을 바꾸지 않는다.

.env, 기존 PostgreSQL 볼륨, JWT_SECRET과 DB 비밀번호를 임의로 초기화하지 않는다. DB_PASSWORD 환경변수만 바꿔도 기존 DB 사용자의 비밀번호가 변경되는 것은 아니다. 비밀값 교체는 영향 범위를 확인하고 진행한다.

## 비밀번호 재설정 메일

계약은 [비밀번호 재설정·변경 API](password-reset-api.md)를 참조한다. 재설정 메일은 Gmail SMTP로 보낸다.

- 값: 서버 ~/DevMozip/.env의 MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, DOMAINNAME. .env는 Git에 없어 배포로 전달되지 않으므로 서버에서 직접 넣는다. 값을 채팅·Git·공개 문서에 붙이지 않는다.
- 값이 없으면 재설정 요청만 503이고 다른 기능은 정상이다.
- SMTP 장애는 /actuator/health에 영향을 주지 않으므로 헬스체크로 알 수 없다. 메일이 오지 않으면 활성 색의 백엔드 로그에서 `비밀번호 재설정 메일 발송 실패`를 찾는다. 로그에는 예외 클래스 이름만 남는다.
- `MailAuthenticationException`이면 앱 비밀번호를 확인한다. 구글 계정 비밀번호를 바꾸면 앱 비밀번호도 폐기된다.
- 호스트 nginx가 X-Real-IP를 실제 접속 IP로 설정해야 IP 제한이 동작한다. 호스트 설정은 저장소에 없다.
- 공용 데모 계정의 비밀번호를 아는 사람은 실제로 바꿀 수 있다. 바뀌면 이후 로그인에 영향이 있으므로 변경 사실을 팀에 공유한다.

## 프리미엄 결제

계약은 [프리미엄 구독·결제 API](premium-payment-api.md), 계획과 남은 운영 항목은 [구현 계획](premium-payment-plan.md) 8단계를 참조한다.

- 값: 서버 ~/DevMozip/.env의 PORTONE_STORE_ID, PORTONE_API_SECRET, TOSS_CHANNEL_KEY, KAKAOPAY_CHANNEL_KEY, PAYMENT_ENCRYPTION_KEY, DOMAINNAME. PAYMENT_ENCRYPTION_KEY는 서버에서 `openssl rand -base64 32`로 만들고 안전한 곳에 보관한다. 바꾸면 저장된 빌링키를 읽을 수 없다.
- 값이 없으면 결제만 503이고 다른 기능은 정상이다.
- 테스트 채널은 실제 청구가 없다. 실제 청구에는 토스페이먼츠 자동결제 계약, 카카오페이 가맹 계약, 포트원 실연동 채널 전환이 필요하다.
- 매월 결제는 백엔드 스케줄러가 10분마다 실행한다(`payment.renewal-cron`). 블루그린의 두 백엔드가 함께 실행해도 한 구독을 한 번만 결제한다.
- 결과를 모르는 매월 결제는 구독이 `RENEWING`, 결제가 `PENDING`으로 남는다. 백엔드 로그의 `매월 결제 결과를 알 수 없어`에 있는 주문 번호로 포트원 결제 조회를 확인해 처리한다.
- 해지·결제 실패 후 빌링키 삭제에 실패하면 로그 `빌링키 삭제 실패`가 남는다. 결제는 일어나지 않지만 포트원 콘솔에서 빌링키를 삭제한다.

## 롤백

```bash
cd ~/DevMozip
bash deploy/blue-green.sh rollback
```

롤백은 남겨 둔 이전 컨테이너를 다시 기동하므로 일반적으로 새 이미지 pull이 필요 없다. 첫 정식 배포 직후 정지된 이전 색에는 develop-check 이미지가 남아 있을 수 있으며, 이는 롤백용 보존 상태다. 현재 서비스가 그 검증 이미지를 사용한다는 의미는 아니다.

특정 태그 재배포는 `IMAGE_TAG=sha-실제7자리커밋 bash deploy/blue-green.sh deploy`로 한다. private GHCR 이미지를 새로 받아야 하므로 수동 실행에는 별도의 단기 read:packages 인증이 필요하다. 자동 배포의 임시 인증은 작업 종료 후 남지 않는다.

## 검증 근거

백엔드 테스트 10개, 프론트 테스트 446개, 셸 대역 테스트 4개 및 빌드/타입/Compose 검증이 통과했다. 실제 PostgreSQL·Redis에서 토큰 발급·권한·폐기를 확인했고, 사전 전환/롤백 중 헬스체크는 87회 실패 0이었다.

정식 main 이미지에서도 실제 Chromium으로 HTTPS 로그인, Secure/HttpOnly/SameSite=Lax 두 쿠키, 서버 세션, USER 관리자 페이지 차단, 액세스 쿠키가 없을 때 재발급, 화면의 로그아웃 버튼으로 두 쿠키 삭제를 확인했다. 이 결과가 모든 부하·장기 연결의 무중단을 보증하는 것은 아니다.

# DevMozip 조별 서버 검증 (2026-11-03)

> 최신 정식 배포 상태·운영 명령은 [운영 안내](deployment-operations.md)를 참조하세요. 아래 미완료 항목은 해당 검증 단계의 기록입니다.

## 확정된 대상과 정정

- SSH: team2@cloud.newlecture.com, 기본 포트 22. 배포용 공개 키 등록 후 비대화식 접속 확인.
- Docker 29.1.3, Compose 2.40.3. team2 계정에서 Docker 실행 가능.
- 프로젝트 이름: devmozip. 서버 검증 폴더: /home/team2/DevMozip-validation.
- DevMozip의 지정 포트: **3002**. 처음 선택한 3102 설정은 사용자가 지정한 포트로 정정했다. 3102에서 컨테이너를 기동한 적은 없으므로 서비스 롤백 대상은 없었다. .env의 포트·프로젝트 설정을 수정한 뒤 3002로 시작했다.
- linker는 다른 조 서비스다. 기존 linker 컨테이너, 3004 포트, linker.newlecture.com 도메인과 호스트 nginx 설정을 수정·정지·삭제하지 않았다.

## 실제 실행한 검증

| 검증 | 결과 |
|---|---|
| 개발 compose + override + https/tools 프로필 config --quiet | 성공. 실제 개발 컨테이너 전체 up은 미수행 |
| 운영 compose blue/green 프로필 config --quiet | 성공 |
| backend/frontend Docker 이미지 빌드 | 성공. Java 21, Node 24 멀티스테이지·비root 런타임 |
| 운영 compose의 PostgreSQL·Redis·blue frontend/backend·nginx 기동 | 성공 |
| 서버의 127.0.0.1:3002 /, /login, /api/health | 모두 HTTP 200 |
| 토큰 없는 보호 API | 실제 백엔드 401 |
| 잘못된 비밀번호 | 실제 백엔드 401 |
| USER 로그인 및 /api/auth/me | 성공, USER |
| USER의 관리자 API 요청 | 403 |
| 리프레시 토큰으로 Bearer 인증 | 401 |
| Redis 리프레시 토큰 갱신 | 로그아웃 전 성공 |
| 로그아웃 및 같은 리프레시 토큰 재사용 | 204 후 401. 실제 Redis 폐기 확인 |
| ADMIN 로그인 및 /api/auth/me | 성공, ADMIN |
| blue → green 배포 → blue rollback | 실제 스크립트로 성공 |
| 전환·롤백 중 연속 헬스체크 | 87회, 실패 0. 동일 develop 이미지로 전환 기능을 검증한 결과이며 모든 부하·장기 요청의 무중단을 보증하는 테스트는 아님 |
| 포트 수정 후 로컬 npm run build | 성공 |

이미지는 서버에서 로컬 빌드한 devmozip-backend:develop-check와 devmozip-frontend:develop-check를 사용했다. GHCR에서 받은 main 이미지가 아니다. SKIP_PULL=1로 검증했다. 최종 활성 색은 blue, green은 롤백을 위해 컨테이너를 보존한 채 정지했다. PostgreSQL·Redis·nginx와 blue frontend/backend는 실행 상태로 둔다.

비밀값은 서버의 검증 폴더 .env에만 600 권한으로 저장했다. 토큰과 비밀번호를 검증 출력에 남기지 않았다. 원래 빌드했던 devmozip-validation-* 이미지 태그는 실행 중인 서비스가 아니며 다른 프로젝트 이미지 정리는 수행하지 않았다.

## 아직 수행하지 않은 항목

Git 커밋·푸시·main 병합, GitHub Secrets 등록과 Actions 실행, GHCR 발행, 운영 Git clone 경로로 이전, DevMozip 전용 공개 도메인/TLS 연결, 실제 브라우저 HTTPS 로그인은 미수행이다. 3002는 보안상 루프백에만 열었으므로 cloud.newlecture.com:3002로 외부 접속하는 구조가 아니다. 공개 접속에는 배정받은 DevMozip 도메인의 호스트 nginx 연결이 필요하다.

서버에서 상태를 확인하려면 다음을 실행한다.

```bash
cd ~/DevMozip-validation
bash deploy/blue-green.sh status
curl -f http://127.0.0.1:3002/api/health
```

운영 전환 시 검증 .env의 DB 비밀번호를 보존한다. COMPOSE_PROJECT_NAME=devmozip인 같은 DB 볼륨을 새 비밀번호로 재초기화하려 하지 않는다. 인증 임시 사용자 비밀번호 전달/교체는 별도로 처리하고 저장소에 넣지 않는다.

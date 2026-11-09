# 개발 DB 연결·실행

WSL의 frontend/backend/Redis는 로컬 Docker에서 실행하고, DB는 팀 서버의 PostgreSQL 개발 DB를 사용한다. 앱 배포와는 별개이며 같은 DB에 연결한 팀원끼리는 데이터를 공유한다.

## 개인 설정

루트의 기존 `.env`에서 다음 값을 설정한다. 비밀번호·계정은 팀에서 받은 값을 사용하며 `.env`는 커밋하지 않는다.

```dotenv
DB_HOST=cloud.newlecture.com
DB_PORT=5442
DB_NAME=devmozip
DB_USER=팀에서_받은_DB계정
DB_PASSWORD='팀에서_받은_DB비밀번호'
```

개인 `compose.override.yaml`은 `SPRING_PROFILES_ACTIVE: default`, `SPRING_JPA_HIBERNATE_DDL_AUTO: validate`, `SPRING_SQL_INIT_MODE: never`를 사용한다. H2·테이블 자동 변경·데모 데이터 삽입을 사용하지 않는다. DB_HOST를 생략하면 현재 Compose는 로컬 postgres로 연결하므로 주의한다. 개인 Compose·Spring 설정도 기존 Git 제외 정책을 유지한다.

## Docker Compose로 실행

Docker Desktop과 해당 배포판의 WSL Integration을 켜고 **SSH 서버가 아닌 내 PC의 WSL 프로젝트 루트**에서 실행한다.

```bash
docker compose config --quiet
docker compose up -d --wait redis
docker compose up -d --no-deps backend frontend
docker compose ps
docker compose logs --tail=80 backend
```

위 명령은 팀의 원격 개발 PostgreSQL을 사용하는 현재 .env 기준이다. Redis가 준비된 뒤 백엔드·프론트만 시작하며, 사용하지 않는 로컬 postgres 서비스는 시작하지 않는다. 앱 이미지 재빌드가 필요하면 두 번째 명령에 `--build`를 추가한다.

`docker compose up -d`를 서비스 지정 없이 실행하면 compose.yaml의 postgres 의존성까지 시작한다. 이때 생기는 devmozip-postgres-1은 이 PC의 로컬 Compose DB 이름이며 이름만으로 운영 배포용을 뜻하지 않는다. 백엔드의 실제 DB 연결 대상은 .env의 DB_HOST/DB_PORT/DB_NAME을 확인한다. 현재 개발 DB는 cloud.newlecture.com:5442/devmozip이며 서버 쪽 컨테이너 이름은 로컬 docker ps에 표시되지 않는다. 현재 프로젝트에는 postgres-dev라는 로컬 서비스가 없다.

재부팅 후 `docker ps`가 비어 있으면 실행 중인 컨테이너가 없는지 다음처럼 확인한다.

```bash
docker ps -a
docker compose ps -a
```

Exited는 컨테이너가 삭제된 것이 아니라 정지된 상태다. 위 시작 명령으로 다시 실행한다. 기존 데이터 볼륨은 유지하며 `down -v`로 초기화하지 않는다.

프론트는 기본 `http://localhost:3000`, 백엔드 헬스는 `http://localhost:8080/actuator/health`다. 설정만 바꿔 백엔드를 재생성하려면 `docker compose up -d --no-deps backend`를 실행한다. 기존 Compose는 사용하지 않는 로컬 postgres도 실행할 수 있지만, 원격 DB 연결에는 사용하지 않는다. 기존 볼륨·데이터는 삭제하지 않는다.

Windows의 기존 H2 실행은 개인 application-local.yml과 backend의 `./gradlew bootRun`(PowerShell: `.\gradlew.bat bootRun`), frontend의 `npm run dev`를 유지한다. SPRING_PROFILES_ACTIVE를 지정하지 않은 별도 터미널을 사용한다.

연결 실패는 주소·포트·DB명·계정·방화벽부터, Schema-validation 오류는 실제 DDL과 엔티티부터 비교한다. 오류 회피를 위해 update/create로 바꾸지 않는다. 확인된 범위·남은 검증은 [AI 진행 현황](ai-progress.md#postgresql-개발-db-연결-2026-11-05)을 따른다.

## WSL에서 Gradle로 직접 실행

`SPRING_PROFILES_ACTIVE=default ./gradlew bootRun`은 Docker Compose 실행이 아니다. Gradle/Spring은 루트 `.env`를 자동으로 읽지 않으므로 DB_PASSWORD가 비어 SCRAM 인증에서 실패할 수 있다. 이후의 Dialect 오류는 DB 연결 실패의 후속 오류다. 이 경우 dialect를 강제로 지정하거나 ddl-auto를 update/create로 바꾸지 않는다.

프로젝트 루트에서 다음 도구를 사용한다. Docker Compose CLI가 루트 `.env`와 개인 override를 해석한 설정을 JVM 환경변수로 전달하므로 비밀번호의 따옴표·특수문자를 셸로 실행하거나 직접 복사할 필요가 없다. 설정값·비밀번호·토큰은 출력하지 않는다.

```bash
cd ~/workspace/DevMozip
python3 scripts/dev-postgres.py backend
```

현재 위치가 backend라면 `python3 ../scripts/dev-postgres.py backend`다. 도구는 default 프로필·Hibernate validate·SQL 초기화 never를 사용한다. 운영 설정과 기존 H2 local 프로필은 변경하지 않는다. 백엔드는 별도 터미널에서 계속 실행하며 Ctrl+C로 종료한다. 같은 포트의 Compose 백엔드와 직접 bootRun은 동시에 실행하지 않는다.

로그인의 리프레시 토큰 저장소는 Redis다. 직접 bootRun은 컨테이너 DNS 이름 redis 대신 호스트의 127.0.0.1로 접속한다. 개인 `compose.override.yaml`에 다음 항목이 필요하다. 실행 도구는 Redis PING을 확인하고 필요하면 `docker compose up -d redis`를 실행한다.

```yaml
services:
  redis:
    ports: ['127.0.0.1:${REDIS_PORT:-6379}:6379']
```

Docker 데몬 연결 오류가 나오면 Windows Docker Desktop의 엔진 실행과 Settings → Resources → WSL Integration의 현재 배포판 활성화를 확인한다. WSL에서 `docker info`가 성공해야 Redis/Compose를 시작할 수 있다. `docker compose config --quiet` 성공만으로 데몬 실행이 확인되는 것은 아니다.

## 터미널에서 실제 DB 조회

Docker 데몬이 꺼져 있어도 Compose CLI와 psql이 있으면 아래 조회는 가능하다. psql이 없으면 Ubuntu/WSL에 `sudo apt install postgresql-client`로 설치한다. 도구의 DB 연결은 읽기 전용이다.

```bash
cd ~/workspace/DevMozip
python3 scripts/dev-postgres.py check
python3 scripts/dev-postgres.py db
```

첫 명령은 연결한 DB·스키마·서버 버전과 추천 요청/결과 개수를 보여 준다. 두 번째 명령은 실제 PostgreSQL 터미널을 연다.

```sql
\dt public.ai_*
SELECT id, ai_conversation_id, ai_message_id, status, conditions
FROM ai_recommendation_requests ORDER BY id;
SELECT id, recommended_request_id, recommended_user_id,
       recommended_project_id, match_score, rank, basic_reason
FROM ai_recommendations ORDER BY recommended_request_id, rank, id;
\q
```

한 번만 조회하려면 다음처럼 실행한다.

```bash
python3 scripts/dev-postgres.py db --command 'SELECT id, status FROM ai_recommendation_requests ORDER BY id;'
```

## Postman으로 저장된 추천 조회

[Postman 컬렉션](postman/devmozip-ai.postman_collection.json)을 Import한다. 컬렉션 Variables에서 baseUrl(기본 http://localhost:8080), demoEmail(member@devmozip.demo), demoPassword(해당 회원의 실제 로그인 비밀번호)를 설정한다. 현재 데모 회원은 기존 초기 데이터의 `Devmozip123!`로 로그인됨을 확인했다. `.env`의 AUTH_USER_PASSWORD는 기존 DB 회원 비밀번호를 자동으로 바꾸지 않는다. **DB_PASSWORD는 PostgreSQL 접속용이며 회원 로그인 비밀번호와 다르다.** 컬렉션 파일에는 비밀번호·토큰을 저장하지 않았다.

1. 백엔드 상태: GET `/actuator/health`.
2. 로그인: POST `/api/v1/auth/login`, JSON `{"username":"{{demoEmail}}","password":"{{demoPassword}}"}`. 성공한 accessToken을 컬렉션 변수에 자동 저장한다.
3. 내 대화 목록과 대화 상세를 조회한다. 저장된 데모 conversationId는 1이다.
4. GET `/api/v1/ai/conversations/1/recommendations`로 최신 요청(id=2), `?requestId=1`로 과거 요청을 조회한다. 새 요청이 추가되면 최신 ID는 달라질 수 있다.

조회 API는 Authorization의 Bearer Token에 accessToken이 필요하다. 로그인하지 않으면 401이며 타인의 대화는 404다. 컬렉션은 조회·로그인만 하며 새 추천을 생성하지 않는다. 데모는 요청 2건·결과 28건(요청마다 개발자 6명·프로젝트 8건)이다. 전체 사용자 데이터는 조회하지 않는다.

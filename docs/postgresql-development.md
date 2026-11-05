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

## 실행

Docker Desktop과 해당 배포판의 WSL Integration을 켜고 **SSH 서버가 아닌 내 PC의 WSL 프로젝트 루트**에서 실행한다.

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=80 backend
```

프론트는 기본 `http://localhost:3000`, 백엔드 헬스는 `http://localhost:8080/actuator/health`다. 설정만 바꿔 백엔드를 재생성하려면 `docker compose up -d --no-deps backend`를 실행한다. 기존 Compose는 사용하지 않는 로컬 postgres도 실행할 수 있지만, 원격 DB 연결에는 사용하지 않는다. 기존 볼륨·데이터는 삭제하지 않는다.

Windows의 기존 H2 실행은 개인 application-local.yml과 backend의 `./gradlew bootRun`(PowerShell: `.\gradlew.bat bootRun`), frontend의 `npm run dev`를 유지한다. SPRING_PROFILES_ACTIVE를 지정하지 않은 별도 터미널을 사용한다.

연결 실패는 주소·포트·DB명·계정·방화벽부터, Schema-validation 오류는 실제 DDL과 엔티티부터 비교한다. 오류 회피를 위해 update/create로 바꾸지 않는다. 확인된 범위·남은 검증은 [AI 진행 현황](ai-progress.md#postgresql-개발-db-연결-2026-11-05)을 따른다.

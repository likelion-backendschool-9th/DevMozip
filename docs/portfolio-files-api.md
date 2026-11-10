# 포트폴리오 파일 업로드 — #113

## 범위와 선행

- feat/113-portfolio-file-upload는 미병합 CRUD #105의 feat/88-portfolio-crud에서 분기했다. 선행 병합 후 develop 기준으로 통합한다.
- 일반 회원도 본인 포트폴리오 이미지·첨부를 업로드한다. 별도 프리미엄 작성 제한은 없다.
- 기존 프로필 이미지 업로드 API는 변경하지 않는다. 기존 범용 POST /api/v1/files 구현은 없어 포트폴리오용 경계를 추가했다.
- 새 DB 테이블·컬럼이나 공유 DB 스키마 변경은 없다. portfolios.thumbnail_url, description의 문서 images, attachments JSONB(name/url)를 사용한다.

## API 및 저장

- POST /api/v1/files: 로그인 필요, multipart/form-data의 file 한 개. 사용자 ID는 입력받지 않는다.
- 성공 201: {id,url,name,contentType,size}. id는 서버가 정하는 portfolio_{회원 id}_{yyyyMMddHHmmss}(이관한 이전 파일은 UUID), url은 동일 출처 /api/files/{id}이다. 메타데이터의 소유자·실제 서버 경로는 응답하지 않는다.
- GET /api/v1/files/{id}: 파일 권한을 확인한다. 프론트 BFF GET /api/files/{id}가 로그인 쿠키를 백엔드 인증 헤더로 전달한다.
  - FILE_ACCEL_REDIRECT=true(nginx가 있는 운영): 파일 내용 없이 X-Accel-Redirect: /internal-files/{id}.{확장자}와 Content-Type·Content-Disposition·Cache-Control만 응답한다. BFF는 이 헤더가 요청한 id의 이미지·PDF 파일을 가리킬 때만 넘기고, nginx가 내부 전용 경로에서 파일을 보낸다.
  - false(기본값, nginx 없이 실행): 권한 확인 뒤 백엔드가 바이트를 응답한다.
- 이미지 PNG/JPEG/WebP 256KB 이하, 실제 이미지 디코딩·1,600만 화소 제한. PDF는 10MB 이하, MIME·확장자·PDF 서명 확인. 악성 문서 검사/내용 분석을 제공하는 기능은 아니다.
- PDF는 attachment 다운로드, 이미지는 inline. Cache-Control no-store, nosniff 및 sandbox 헤더 사용.
- 전체 multipart 요청 한도 11MB, 파일 한도 10MB. 기존 프로필 이미지의 서비스 256KB 제한은 유지된다. BFF도 실제 읽은 바이트 기준으로 11MB를 제한한다.
- 경로 조작·제어 문자가 들어간 이름과 손상/위장 이미지를 거절하며 원래 이름을 저장 경로로 사용하지 않는다.

## 파일 보존과 접근 권한

- 저장 위치: FILE_STORAGE. 기본 ../public/files(backend 폴더에서 실행할 때 프로젝트 루트의 public/files), 운영 /public/files. {id}.{확장자}와 {id}.json(소유자/원래 이름/형식/크기)을 함께 보존한다(2026-11-11 변경, 아래 "저장 위치 변경과 기존 파일 이관").
- 같은 회원이 같은 초에 다시 올려 이름이 겹치면 다음 초 이름으로 저장한다. 덮어쓰지 않는다.
- public/files는 nginx의 **내부 전용 경로**(/internal-files/, internal)로만 연결한다. /images/처럼 권한 확인 없이 여는 location이나 public/images 하위에 두면 안 된다.
- 업로드 직후 및 비공개 파일은 업로더만 조회 가능하다. 다른 사용자·비로그인은 파일 존재를 노출하지 않고 404.
- 원래 업로더의 ACTIVE/PUBLIC 포트폴리오 대표 이미지에 연결되면 비로그인도 표시한다.
- PUBLIC 포트폴리오의 본문 이미지·첨부는 기존 상세 정책에 따라 PREMIUM/ADMIN 또는 본인에게만 제공한다. 다른 사용자 포트폴리오에 URL을 복사해도 원래 소유자의 연결이 없으면 공개되지 않는다.
- 저장 시 /api/files/ 경로는 본인 업로드 파일인지 검사한다. PDF를 대표/갤러리 이미지로 연결하지 못한다.
- UI의 이미지 삭제/PDF 연결 해제는 포트폴리오 연결만 제거한다. 서버 파일 삭제·자동 정리 API는 없다. 취소·부분 업로드 실패 시 미연결 파일도 보존한다. 프로젝트 제출 파일 보존 정책 합의 전 자동 삭제하지 않는다.
- 영속 볼륨·다중 백엔드 인스턴스는 같은 파일 디렉터리를 공유해야 한다. 운영은 compose.prod.yaml에서 blue·green backend가 서버 프로젝트 폴더의 public을 함께 연결하고, deploy/blue-green.sh가 public/files를 만들고 소유자를 맞춘다(2026-11-11). 이전 폴더의 파일은 scripts/migrate-upload-storage.py로 옮긴다(아래 "기존 파일 이관").

## 화면 흐름

기존 대표 이미지·갤러리 선택 → uploadPortfolioFile → Next BFF → Controller → PortfolioFileService/Impl → LocalPortfolioFileStorage.

선택 시 서버 업로드 후 반환 URL을 기존 문서/초안에 반영한다. PDF 첨부 UI는 최대 10개를 선택할 수 있으며, 기존 API의 첨부 최대 100개 계약은 호환을 위해 유지한다. 문서 저장 → 본인 파일 확인 → 기존 포트폴리오 저장. 재조회 시 이미지·첨부를 복원한다. 업로드 중 저장 버튼을 막고 실패 시 입력·기존 파일은 보존한다. 기존 Mock 작성기는 기존 로컬 이미지 처리 방식을 유지한다.

## 검증

최종 실행 결과는 docs/ai-progress.md의 #113 기록을 따른다. 원래 백엔드 전체 테스트는 선행 develop의 AI fixture 2곳 타입 오류로 컴파일 실패했다. 임시 Gradle init으로 해당 테스트 소스 2개만 제외한 실행과 원래 실행을 구분한다. Docker 미실행으로 건너뛴 결과를 PostgreSQL 검증 성공으로 간주하지 않는다.

## 실제 로컬·공유 DB 확인 (2026-11-10)

- 기존 개발 premium 계정 로그인, 검증용 PRIVATE 포트폴리오 ID 3 수정 화면에서 대표 이미지·갤러리 이미지 각 1건 및 2MB PDF 업로드.
- 업로드 중 저장 버튼 차단, 완료 후 활성화 및 기존 저장 API로 파일 연결 저장 확인.
- 상세 화면 이미지 표시와 PDF 200 다운로드(Content-Disposition attachment, 2,097,483바이트), 새로고침 후 유지 확인.
- 비로그인 상태에서 같은 비공개 파일 주소 GET은 404. 공유 DB에서 ID 3/user_id 5/PRIVATE, thumbnail_url, attachments 1건, 문서 버전 확인.
- 다른 기존 사용자 데이터·프로젝트 지원서·스키마는 변경하지 않았다. 검증 파일은 로컬 서버 저장소에, URL 연결은 기존 검증용 DB 행에 남겨두었다. 다른 팀원 환경에서 이 파일을 표시하려면 같은 저장소를 공유해야 한다.

### 프로젝트 제출본의 파일 열람

제출 스냅샷이 보관한 파일은 원본 포트폴리오의 수정·삭제 후에도 기존 저장소에서 읽는다. 업로더 본인 외에는 제출자의 파일과 해당 프로젝트 주최자가 일치하고, 주최자가 PREMIUM/ADMIN일 때만 제출본 참조를 근거로 허용한다. 다른 프리미엄 회원이나 익명에게 스냅샷만으로 파일을 공개하지 않는다. 원본 공개 참조의 기존 정책은 유지한다. 파일 정리·물리 삭제 정책을 추가할 때는 스냅샷 참조도 보존 대상으로 확인해야 한다.

## 저장 위치 변경과 기존 파일 이관 (2026-11-11)

사용자 요청으로 업로드 파일을 프로젝트 폴더의 public/ 아래로 모았다. 포트폴리오 파일은 public/files(FILE_STORAGE)에, 프로필·배너 이미지는 게시글 이미지와 같은 public/images(IMAGE_STORAGE)에 저장한다(프로필 쪽은 docs/profile-local-images.md "저장 위치 변경").

- 파일 이름: portfolio_{회원 id}_{yyyyMMddHHmmss}.{확장자}와 같은 이름의 .json. 시각은 업로드한 때의 한국 시각(초 단위)이다. 여러 회원이 같은 초에 올려도 겹치지 않게 회원 id를 넣었다.
- 전송: 권한 확인은 백엔드가 기존 정책대로 하고, nginx가 X-Accel-Redirect를 받아 내부 전용 경로(/internal-files/)에서 파일을 보낸다. 응답 헤더(Content-Type·Content-Disposition·Cache-Control)는 백엔드 값을 그대로 쓰고, nginx가 nosniff와 Content-Security-Policy: sandbox를 붙인다. /internal-files/를 직접 요청하면 404다.
- 없앤 설정: PORTFOLIO_FILE_STORAGE(portfolio.files.directory).

### 환경별 설정

| 환경 | 설정 |
| --- | --- |
| backend 폴더에서 실행, local 프로필 | 기본값 ../public/images, ../public/files(프로젝트 루트). FILE_ACCEL_REDIRECT는 기본값 false라 백엔드가 파일 내용을 응답한다 |
| 개발 compose (compose.override.yaml) | backend에 ./public:/public이 연결돼 있으면 추가 설정이 필요 없다(컨테이너 /app 기준 기본값이 /public/images·/public/files). 연결이 없으면 파일이 컨테이너 안에 저장되어 컨테이너를 새로 만들 때 사라진다. Git 제외 로컬 파일이라 팀원이 각자 확인한다 |
| 로컬 HTTPS nginx (compose.yaml의 https 프로필) | nginx에 ./public/images:/usr/share/nginx/images:ro와 ./public/files:/usr/share/nginx/files:ro를 연결하고 backend에 FILE_ACCEL_REDIRECT: "true"를 준다. 이때 3000번으로 바로 접속하면 포트폴리오 파일이 빈 응답이 되므로 nginx를 거치는 주소로만 확인한다. Git 제외 로컬 파일이라 팀원이 각자 추가한다 |
| 운영 (compose.prod.yaml) | backend에 IMAGE_STORAGE=/public/images, FILE_STORAGE=/public/files, FILE_ACCEL_REDIRECT=true. nginx에 두 폴더를 읽기 전용으로 연결한다. deploy/blue-green.sh가 배포마다 두 폴더를 만들고 소유자를 backend 이미지의 app 사용자로 맞춘다. 볼륨 설정이 바뀌어 다음 배포의 docker compose up -d nginx에서 nginx 컨테이너가 다시 만들어진다 |

### 기존 파일 이관

scripts/migrate-upload-storage.py를 한 번 실행한다.

- 프로필·배너: users에서 이전 주소(.../api/v1/profile-images/{UUID}.{확장자})를 찾는다. 이전 폴더에 파일이 있으면 public/images/{profile|banner}_{회원 id}_{시각}.{확장자}로 복사하고 DB 값을 /images/{새 이름}으로 바꾼다. 시각은 이전 파일의 수정 시각이다. 파일이 없는 주소는 바꾸지 않고 목록으로 보여 준다.
- 포트폴리오: 이전 폴더의 {UUID}.bin·{UUID}.json을 public/files/{UUID}.{확장자}·{UUID}.json으로 복사한다. 주소 /api/files/{UUID}와 DB 값은 그대로다.
- --apply가 없으면 확인만 한다. DB 변경은 한 트랜잭션이고, 실패하면 이번에 복사한 프로필 이미지를 지운다. 이전 파일은 지우지 않는다. 다시 실행하면 이미 옮긴 항목은 건너뛴다.
- DB는 psql로 연결한다(--psql, 기본값 psql). 공유 DB에서 실행하기 전에는 담당자 확인을 받는다.

운영 서버 순서:

1. 배포한다. 새 버전은 이전 주소의 파일을 읽지 않으므로 이관 전까지 이전 프로필 이미지와 포트폴리오 파일이 보이지 않는다.
2. 이전 색 backend 컨테이너는 지우지 않고 멈춰 두므로, 그 안의 이전 파일을 꺼낸다. 이전 색은 bash deploy/blue-green.sh status로 확인한다. 폴더가 없으면 그 환경에는 옮길 파일이 없다.
   `docker cp devmozip-backend-<이전 색>-1:/app/uploads/profile-images ./old-profile-images`
   `docker cp devmozip-backend-<이전 색>-1:/app/uploads/portfolio-files ./old-portfolio-files`
3. 확인 실행 뒤 --apply를 붙여 다시 실행한다. public/ 폴더가 app 사용자 소유라 sudo로 실행한다.
   `sudo python3 scripts/migrate-upload-storage.py --psql "docker compose -f compose.prod.yaml exec -T postgres psql -U <DB_USER> -d <DB_NAME>" --profile-dir ./old-profile-images --portfolio-dir ./old-portfolio-files`

참고: 이전 운영 설정에는 프로필·포트폴리오 폴더 설정이 없어 컨테이너 안 /app/uploads를 썼다. /app은 root 소유이고 backend는 app 사용자로 실행되므로 운영 업로드가 저장되지 않았을 수 있다. 파일 기준 추정이며 서버에서는 확인하지 않았다. 운영 배포·이관과 공유 개발 DB 이관은 실행하지 않았다.

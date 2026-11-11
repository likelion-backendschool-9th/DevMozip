# 로컬 폴더 기반 프로필·배너 이미지

## 범위와 API

로그인한 회원만 자기 프로필·배너 이미지를 변경/삭제한다.
Authentication.getName()의 이메일을 사용하며 요청에서 사용자 ID나 저장 경로를 받지 않는다.
PNG/JPEG/WebP 각각 256KiB(262,144바이트)까지 허용한다.
확장자나 MIME만 믿지 않고 ImageIO로 실제 형식과 디코딩을 검증한다.
WebP는 TwelveMonkeys imageio-webp 3.15.0 플러그인을 사용한다.
압축된 큰 이미지의 과도한 메모리 사용을 막기 위해 1,600만 화소를 초과하면 거부한다.

| 동작 | API |
| --- | --- |
| 프로필 이미지 업로드/교체 | PUT /api/v1/users/me/images/profile |
| 배너 이미지 업로드/교체 | PUT /api/v1/users/me/images/banner |
| 프로필 이미지 삭제 | DELETE /api/v1/users/me/images/profile |
| 배너 이미지 삭제 | DELETE /api/v1/users/me/images/banner |

이미지 파일은 게시글 이미지와 같이 /images/{파일 이름}으로 nginx(없으면 백엔드 PublicImageController)가 제공한다.
이전 조회 API GET /api/v1/profile-images/{파일명}은 2026-11-11에 없앴다 (아래 "저장 위치 변경").

업로드는 multipart/form-data의 file 파트를 사용한다. 삭제 요청에는 파일/본문이 필요 없다.
업로드/삭제 성공 시 200과 {profileImage, bannerImage}를 응답한다. 미등록/삭제 값은 NULL이다.
이미지 변경은 별도 API이므로 기존 기본 정보 PUT에서 이미지 문자열을 바인딩하지 않는다.
두 이미지 중 하나만 바꾸면 다른 이미지와 연락 시간·권한·비밀번호 등은 유지한다.
Member의 DynamicUpdate로 변경한 컬럼만 갱신한다.
같은 회원의 이미지 변경/삭제 요청은 DB 쓰기 잠금으로 직렬화한다.

## 저장과 정리

- 저장 폴더: IMAGE_STORAGE. 기본 ../public/images(backend 폴더에서 실행할 때 프로젝트 루트의 public/images), 운영 /public/images. 게시글 이미지와 같은 폴더이며 파일 이름 접두어로 구분한다.
- 원본 파일명은 사용하지 않고 {profile|banner}_{회원 id}_{업로드 시각 yyyyMMddHHmmss}.{검증한 확장자}로 저장한다. 같은 회원이 같은 초에 다시 올려 겹치면 다음 초 이름을 쓰고 덮어쓰지 않는다.
- DB profile_image/banner_image에는 /images/{파일 이름}을 저장한다.
- 경로 문자열이나 외부 파일을 업로드 요청으로 지정할 수 없다.
- 이미지는 권한 확인 없이 제공한다. 정지 회원의 이미지도 주소를 알면 보이고, 지운 이미지도 브라우저 캐시에 최대 1일 남을 수 있다(게시글 이미지와 같은 정책).
- 응답은 확장자에 맞는 image MIME, nosniff, 1일 캐시다.
- 새 파일 기록 후 DB 변경이 실패하면 새 파일을 정리한다.
- 교체/삭제의 DB 커밋이 성공한 뒤에만 사용하지 않는 이전 파일을 정리한다.
- 다른 회원/배너가 계속 참조하는 파일, 외부 URL, 저장소 밖의 파일은 삭제하지 않는다.
- 후처리 정리가 실패하면 성공한 DB 변경은 유지하고 서버 로그에 파일 정리 실패를 남긴다.
- 서버 강제 종료 등으로 남은 미참조 파일은 후속 정리 대상이다. 별도 배치는 이번 범위에 없다.

## 로컬 실행 설정

저장 폴더를 바꾸려면 IMAGE_STORAGE를 준다. 2026-11-11까지 쓰던 PROFILE_IMAGE_DIRECTORY·PROFILE_IMAGE_PUBLIC_BASE_URL은 없앴다.

```bash
IMAGE_STORAGE=/absolute/path/to/public/images
```

DB에는 같은 출처 경로(/images/...)만 저장하므로 공개 주소 설정이 필요 없다.
업로드 폴더(/public/images/)는 Git에서 제외했다.
컨테이너로 실행할 때 파일을 보존하려면 프로젝트 폴더의 public을 연결한다(compose.override.yaml·compose.prod.yaml의 ./public:/public).
S3·외부 저장소는 추가하지 않았다.

기존 인증·DB 실행 설정이 필요하다. 제공된 local H2 설정만으로 인증 담당 브랜치가 자동 통합되지는 않는다.
H2 초기 회원 데이터에 직군이 없으면 공개 프로필 조회가 404일 수 있으므로 직군 연결을 별도로 준비한다.
업로드/삭제는 직군 설정 여부와 관계없이 인증된 회원에 대해 동작한다.

## 오류

- 400: 빈 파일, 손상/형식 불일치, 잘못된 이미지 종류, 초과 화소.
- 401: 비로그인/유효하지 않은 JWT.
- 403: 수정 불가 계정 상태.
- 404: 없는 회원, 없는 이미지 파일(/images/).
- 413: 크기 초과(애플리케이션 및 Servlet multipart 제한).
- 415: PNG/JPEG/WebP 이외의 MIME.
- 409: DB 저장 제약 위반.
- 503: 로컬 파일 저장 실패.

## 변경 파일

LocalProfileImageStorage: 형식·크기 검증, UUID 파일명, 디렉터리 경계, 파일 읽기/삭제.
ProfileImageService: 본인 조회, 업로드·교체·삭제, DB 트랜잭션에 따른 파일 정리.
ProfileImageController/ProfileImageFileController: multipart 수정/삭제 및 공개 파일 응답.
MemberRepository: 이미지 변경 잠금·사용 중/공개 이미지 확인.
Member: 이미지 변경 시 다른 컬럼 덮어쓰기를 줄이는 DynamicUpdate.
SecurityConfig/ProfileExceptionHandler: 공개 파일 GET 및 프로필 전용 오류 처리.
build.gradle/application.yaml/.gitignore: WebP 디코더·로컬 경로 설정·업로드 제외.
관련 단위/통합 테스트. 연락 시간 변경은 같은 브랜치의 별도 문서를 참고한다.

## 검증 범위

임시 폴더와 H2를 사용한다. 운영 DB·실제 사용자 이미지·외부 저장소에 접근하지 않는다.
PNG/JPEG/WebP 실제 디코딩, 위장/손상/크기/화소/경로 검증,
본인 변경, 교체·삭제, 공유 참조 보호, DB 실패 시 새 파일 정리 및 이전 파일 유지,
파일 쓰기 실패, 비로그인/위조 토큰/정지 회원 차단을 확인한다.
실제 임시 HTTP 서버로 multipart 업로드·이미지 다운로드와 Servlet 크기 제한도 검증한다.
프론트 수정 창/BFF 업로드 연결과 실제 PostgreSQL·운영 배포 검증은 후속 작업이다.

참고: https://github.com/haraldk/TwelveMonkeys — ImageIO WebP 읽기 및 플러그인 사용법.

최종 검증: 백엔드 전체 118개 테스트·빌드 통과, 프론트 npm run build 통과.

## Docker 출처 검사 보정 (2026-11-10)

이미지 PUT/DELETE와 프로필 쓰기 공통 profileRequestGuard는 브라우저 Origin을 요청의 Host 및 프로토콜과 비교한다. Next 내부 바인딩 주소(0.0.0.0:3000)를 브라우저 주소로 오인하지 않도록 hostname/port를 함께 교체하며, x-forwarded-host만으로 검증을 우회하지 않는다. 다른 사이트 요청 차단과 기존 이미지 형식·크기·인증 검증은 유지한다.

## 저장 위치 변경 (2026-11-11)

사용자 요청으로 프로필·배너 이미지를 게시글 이미지와 같은 public/images(IMAGE_STORAGE)에 저장하고 nginx가 /images/로 제공하도록 바꿨다. 환경별 설정과 이관 절차는 docs/portfolio-files-api.md "저장 위치 변경과 기존 파일 이관"에 함께 둔다.
업로드·삭제 API, 형식·크기·화소 검증, 본인 확인, DB 커밋 뒤 이전 파일 정리, 공유 참조 보호는 그대로다.
ProfileImageFileController와 MemberRepository.existsPublicImage를 없앴고, LocalProfileImageStorage는 저장 폴더·파일 이름·주소 형식만 바뀌었다.
이전 주소(.../api/v1/profile-images/{UUID}.{확장자})의 DB 값과 파일은 scripts/migrate-upload-storage.py로 옮긴다.

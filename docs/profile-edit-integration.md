# 프로필 수정 화면 API 연동

기존 MY WORKSPACE 사이드바, 상단 프로필 카드, 기본 정보 수정 모달과 이미지 편집 UI를 유지한다. Mock 모드의 기존 동작도 유지한다.

## 동작 흐름

1. 서버 세션은 기존 GET /api/v1/users/me 응답의 username·role을 사용한다.
2. 로그인한 사용자 전용 GET /api/v1/users/me/profile로 실제 회원 ID와 프로필을 읽는다. 데모 ID를 소유권 판단에 사용하지 않는다.
3. 내 프로필 메뉴는 /profile/me에서 본인 응답을 표시한다. 직군 미입력 회원도 편집 화면에 접근할 수 있다.
4. 공개 /users/{tag}에서는 본인 조회 응답의 ID와 일치할 때 수정 버튼을 활성화한다.
5. 수정 모달의 직군 선택지는 DB 기반 GET /api/v1/users/me/profile/job-categories를 사용한다.
6. PUT /api/v1/users/me/profile로 저장 후 카드·사이드바를 갱신한다. 공개 프로필에서 태그를 바꾸면 새 URL로 이동한다.
7. 이미지 변경·삭제는 각각 PUT multipart / DELETE /api/v1/users/me/images/{profile|banner}로 즉시 저장한다. 텍스트 폼의 취소와 별개임을 모달에 안내한다.

## 통신 경계

브라우저는 /api/profiles/me/profile, /api/profiles/me/profile/job-categories, /api/profiles/me/images/{kind} BFF만 호출한다. JWT는 기존 HttpOnly 쿠키에서 서버가 전달한다. 사용자 ID·이메일·권한·신뢰 온도는 수정 요청의 허용 목록에서 제외한다. 변경 요청은 동일 Origin을 확인한다. 응답은 no-store이며 내부 오류·인증 정보는 노출하지 않는다.

경력 입력은 텍스트 입력창으로 개월 수를 받으며 DB의 INT 형식은 유지한다. 0은 신입, 빈칸은 NULL이다. 연락 시간의 명시적 빈 문자열은 삭제를 뜻한다. 이미지 파일은 원본 File을 multipart로 전송하며 크기·유형·실제 이미지 검증은 기존 백엔드 정책을 따른다.

## 백엔드 보완

`feat/profile-api-integration`의 backend에 본인 프로필 GET과 직군 목록 GET을 추가했다. 기존 username·role 응답 계약과 공개 계정 정책은 유지한다. 본인 조회만 직군 미입력을 허용하며, 저장할 때는 유효한 직군을 선택해야 한다.

프론트와 필요한 최신 인증·프로필 백엔드 코드를 기존 `feat/profile-api-integration` 브랜치에 함께 모았다. 같은 작업 디렉터리의 frontend와 backend를 사용하면 된다.

## 로컬 확인

- 프론트 전체 테스트 및 프로덕션 빌드 실행
- 백엔드 전체 테스트 및 빌드 실행 (Docker가 필요한 PostgreSQL 컨텍스트 테스트는 환경에 따라 건너뜀)
- 임시 H2 데이터와 별도 로컬 HTTP 서버에서 BFF → JWT 필터 → 서비스 → JPA 저장·재조회 확인
- 비로그인 수정 401, 다른 Origin 수정 403, 변경 전 태그 404 확인
- 경력 0·NULL, 연락 시간 저장·삭제, 프로필·배너 이미지 multipart 업로드·파일 조회·삭제 확인
- HTTP 검증은 별도로 서명한 임시 테스트 토큰을 사용했다. 실제 로그인 발급 흐름은 백엔드 통합 테스트에서 검증한다.
- 실제 PostgreSQL·Redis와 브라우저 수동 클릭 검증은 별도로 진행해야 한다.

## 실행 설정

프론트는 AUTH_MODE=server, BACKEND_URL을 백엔드 주소로 설정한다. 백엔드 로컬 프로필에는 H2 접속 설정, JWT_SECRET, 직군 및 테스트 회원 데이터가 필요하다. 실제 비밀값은 Git에 추가하지 않는다. 경력·contact_hours·이미지 컬럼은 기존 프로필 백엔드의 스키마 요구사항을 따른다.

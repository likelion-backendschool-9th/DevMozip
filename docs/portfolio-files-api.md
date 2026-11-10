# 포트폴리오 파일 업로드 — #113

## 범위와 선행

- feat/113-portfolio-file-upload는 미병합 CRUD #105의 feat/88-portfolio-crud에서 분기했다. 선행 병합 후 develop 기준으로 통합한다.
- 일반 회원도 본인 포트폴리오 이미지·첨부를 업로드한다. 별도 프리미엄 작성 제한은 없다.
- 기존 프로필 이미지 업로드 API는 변경하지 않는다. 기존 범용 POST /api/v1/files 구현은 없어 포트폴리오용 경계를 추가했다.
- 새 DB 테이블·컬럼이나 공유 DB 스키마 변경은 없다. portfolios.thumbnail_url, description의 문서 images, attachments JSONB(name/url)를 사용한다.

## API 및 저장

- POST /api/v1/files: 로그인 필요, multipart/form-data의 file 한 개. 사용자 ID는 입력받지 않는다.
- 성공 201: {id,url,name,contentType,size}. id는 서버 UUID, url은 동일 출처 /api/files/{id}이다. 메타데이터의 소유자·실제 서버 경로는 응답하지 않는다.
- GET /api/v1/files/{id}: 파일 권한 확인 후 바이트 반환. 프론트 BFF GET /api/files/{id}가 로그인 쿠키를 백엔드 인증 헤더로 전달한다.
- 이미지 PNG/JPEG/WebP 256KB 이하, 실제 이미지 디코딩·1,600만 화소 제한. PDF는 10MB 이하, MIME·확장자·PDF 서명 확인. 악성 문서 검사/내용 분석을 제공하는 기능은 아니다.
- PDF는 attachment 다운로드, 이미지는 inline. Cache-Control no-store, nosniff 및 sandbox 헤더 사용.
- 전체 multipart 요청 한도 11MB, 파일 한도 10MB. 기존 프로필 이미지의 서비스 256KB 제한은 유지된다. BFF도 실제 읽은 바이트 기준으로 11MB를 제한한다.
- 경로 조작·제어 문자가 들어간 이름과 손상/위장 이미지를 거절하며 원래 이름을 저장 경로로 사용하지 않는다.

## 파일 보존과 접근 권한

- 저장 위치: portfolio.files.directory 또는 PORTFOLIO_FILE_STORAGE. 기본 ./uploads/portfolio-files. UUID.bin과 UUID.json(소유자/원래 이름/형식/크기)을 서버에 함께 보존한다.
- 공개 이미지용 IMAGE_STORAGE와 분리된 **비공개 디렉터리**다. nginx 정적 공개 폴더나 public/images 하위에 두면 안 된다.
- 업로드 직후 및 비공개 파일은 업로더만 조회 가능하다. 다른 사용자·비로그인은 파일 존재를 노출하지 않고 404.
- 원래 업로더의 ACTIVE/PUBLIC 포트폴리오 대표 이미지에 연결되면 비로그인도 표시한다.
- PUBLIC 포트폴리오의 본문 이미지·첨부는 기존 상세 정책에 따라 PREMIUM/ADMIN 또는 본인에게만 제공한다. 다른 사용자 포트폴리오에 URL을 복사해도 원래 소유자의 연결이 없으면 공개되지 않는다.
- 저장 시 /api/files/ 경로는 본인 업로드 파일인지 검사한다. PDF를 대표/갤러리 이미지로 연결하지 못한다.
- UI의 이미지 삭제/PDF 연결 해제는 포트폴리오 연결만 제거한다. 서버 파일 삭제·자동 정리 API는 없다. 취소·부분 업로드 실패 시 미연결 파일도 보존한다. 프로젝트 제출 파일 보존 정책 합의 전 자동 삭제하지 않는다.
- 영속 볼륨·다중 백엔드 인스턴스는 같은 비공개 파일 디렉터리를 공유해야 한다. 로컬 기본 폴더만으로 컨테이너 재생성/블루그린 간 보존을 보장하지 않는다. 배포 설정/정책 변경은 이번 범위에서 하지 않는다.

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

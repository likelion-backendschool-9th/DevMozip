# 프로필 상단 조회 연결 (#25)

## 범위와 흐름

#21 백엔드가 포함된 feat/21-profile-detail에서 분기했다.
브라우저 → Next.js `/api/profiles/{slug}` → 기존 backendFetch → Spring Boot `/api/v1/users/{slug}` 순서다.
백엔드 주소·JWT는 기존 서버 통신 경계를 사용하며 클라이언트 코드에 전달하지 않는다.

- 개발 AUTH_MODE=mock: 기존 프로필 상단·수정 창·하단 5개 탭·저장소 동작 유지. 프로필 API 호출 없음.
- AUTH_MODE=server 또는 운영 빌드: URL의 userId 세그먼트를 태그(slug)로 사용하여 공개 프로필 조회.
- 성공 응답을 검증하고 허용된 공개 필드만 전달한다. 실패 시 Mock 데이터로 대체하지 않는다.
- 모든 응답은 no-store. 백엔드 요청은 기존 5초 타임아웃 사용.
- 로딩·404·권한 오류·통신 오류와 재시도 제공. 경로 변경·언마운트 시 이전 요청 취소.
- career: 18 → 1년 6개월, 0 → 신입, NULL → 경력 미입력.
- 빈 이미지·이미지 로드 실패 시 기본 아바타·배너 표시. http(s) 공개 URL 또는 같은 출처 경로만 사용.
- 이미지 상대 경로는 프론트 출처 기준이다. 백엔드 전용 파일 경로는 브라우저에서 접근 가능한 URL로 제공해야 한다.
- AVAILABLE은 협업 가능, UNAVAILABLE은 쉬는 중으로 표시하며 NULL·알 수 없는 값은 미등록으로 표시한다. 실제 ENUM 정책은 팀 확인 필요.

## 의도적으로 제공하지 않는 항목

기존 프로필 카드 배치와 5개 탭을 서버 모드에서도 유지한다.
가입일·연락 시간은 조회 연결 전, 팔로우 수는 —로 표시하고 프로필 수정·팔로우 버튼은 비활성화한다.
소개 탭의 자기소개·기술 스택·관심 분야·외부 링크 배치를 유지한다.
나머지 탭은 기존 제목과 콘텐츠 영역에 조회 연결 전 안내를 표시한다.
조회하지 않은 정보에 다른 사용자의 Mock 내용이나 가짜 0개를 표시하지 않는다. PRO 여부도 임의로 부여하지 않는다.
Mock 모드는 기존 기능을 유지한다.
현재 인증 응답은 username/role만 있고 실제 users.id/tag가 없다.
따라서 서버 모드에서 임시 Mock ID로 본인 여부·개인 Aside를 추정하지 않는다.
헤더의 내 프로필 자동 이동을 완성하려면 인증/내 정보 API의 실제 tag 제공이 후속으로 필요하다.
운영 사용자를 이메일로 Mock 프로필에 연결하거나 타인의 프로필로 자동 대체하지 않는다.
프로필 수정·하단 탭 API·PostgreSQL 스키마 변경은 이번 범위가 아니다.

## 변경 파일

- app/api/profiles/[slug]/route.ts: BFF 조회와 오류 처리
- types/profileDetail.ts: 공개 조회 타입
- lib/profiles/profileDetail.ts: 응답 검증·BFF 요청·경력/상태/이미지 표시 규칙
- app/(user)/users/[userId]/page.tsx: 모드에 따른 조회 화면 선택
- 같은 경로의 _components/ApiProfileContent.tsx, ApiProfileHeader.tsx, ApiProfileTabs.tsx: 조회 상태·실제 프로필·기존 화면 틀 표시
- app/(user)/_components/AccountLayout.tsx: 서버 모드의 Mock 소유권 추정 제거
- components/profile/ProfileAvatar.tsx: 이미지 오류 콜백 지원
- 관련 계약·BFF·화면 테스트와 테스트 전용 fixture

## 실행

기존 Mock 데모는 frontend에서 npm run dev로 실행한다.
서버 조회는 백엔드 실행 환경과 실제 users/job_categories 데이터가 준비된 뒤 다음으로 실행한다.

```bash
cd frontend
AUTH_MODE=server BACKEND_URL=http://localhost:8080 npm run dev
```

`/users/{실제태그}`에 직접 접속한다. 실제 DB에도 career 컬럼이 있어야 한다.
운영은 기존 JWT+BFF 정책을 따르며 클라이언트용 환경 변수나 CORS 설정을 추가하지 않았다.

## 검증 결과 (2026-11-03)

- 프론트 테스트 495개 통과, npm run build 성공.
- 임시 H2 백엔드(127.0.0.1:18025)와 Next.js 프로덕션 서버(127.0.0.1:13025)를 실행해 실제 BFF HTTP 확인.
- 정상 경력 18개월·신입 0개월 사용자 조회: 200, 잠금·미존재: 404.
- Safari에서 API 데이터의 닉네임·직군·1년 6개월·소개·38.5°C와 기본 이미지 확인.
- Safari에서 404 안내와 백엔드 종료 시 통신 실패·재시도 버튼 확인. 백엔드 재시작 후 재시도 클릭으로 프로필 복원 확인.
- 실제 운영 PostgreSQL/Redis 연결 및 운영 사용자 본인 프로필 이동은 미검증.
- 검증 데이터와 H2 실행 설정은 /private/tmp에만 두며 저장소 운영 설정에 추가하지 않았다.

# DevMozip · 데브모집

**Next.js 16.4.3 + TypeScript + App Router** 기반의 기획 발표용 프론트엔드 프로토타입입니다. 기존 로그인·온보딩·프로필·프로젝트·SNS·채팅·리뷰·대화형 개발자 추천을 Mock 데이터와 브라우저 상태로 시연합니다. 로그인은 백엔드 JWT+BFF 인증을 사용합니다. [팀 인증·브랜치 안내](docs/team-auth-deployment.md)와 [배포 준비](docs/setup-after-ai.md)를 참고하세요.

운영 주소: https://devmozip.newlecture.com · [자동 배포·운영 안내](docs/deployment-operations.md)

## Mock 데이터 켜기·끄기

상단의 ‘Mock 데이터 끄기’는 기존 페이지·검색 필터·탭·버튼·폼을 유지하고 샘플 콘텐츠만 비웁니다. OFF에서 직접 작성한 데이터는 별도 저장하며 다시 켜면 원래 데모 상태가 복원됩니다. 실제 API 모드로 전환하는 기능은 아닙니다. [동작 범위·검증 결과](docs/mock-visibility.md)를 참고하세요.

## 실행

검증 환경은 Node.js 24.22.1입니다. Next.js 요구사항은 Node.js 20.9 이상이며 Node.js 22.12 이상 사용을 권장합니다.

```bash
cd frontend
npm ci
npm run dev
```

[개발 서버](http://localhost:3000)를 엽니다. 기본 포트는 3000입니다. LAN/Windows 포트 전달은 [기존 안내](docs/lan-access.md)를 참고하세요. 실제 다른 PC 접속은 이번 마이그레이션에서 재검증하지 않았습니다.

프로덕션 실행:

```bash
cd frontend
npm run build
npm run start
```

[프로덕션 서버](http://localhost:3000). Next.js 서버가 페이지/정적 파일/RSC 내비게이션을 제공합니다. 업무 데이터는 Mock이며 운영 로그인은 백엔드·Redis가 필요합니다. Vite의 정적 `dist` 실행 방식은 제거했습니다. 개발/프로덕션을 함께 실행할 때는 `--port`로 포트를 분리하세요. 운영 Secure 쿠키는 HTTPS 환경에서 확인합니다.

```bash
npm run typecheck
npm test
```

로그인은 백엔드 인증(JWT+BFF)을 사용하므로 개발 서버에서도 백엔드가 실행 중이어야 합니다. 서버 전용 BACKEND_URL은 `frontend/.env.example`을 참고하세요. Axios 인스턴스는 후속 API 교체를 위한 준비 코드이며 현재 UI에서 호출하지 않습니다. 로고·Pretendard 글꼴은 `public`에서 제공하고 외부 글꼴 CDN을 사용하지 않습니다.

## 구조

```text
frontend/
  app/
    layout.tsx, globals.css, ClientProviders.tsx, error.tsx, not-found.tsx
    page.tsx, _components/
    login/, signup/, forgot-password/, onboarding/
    users/[userId]/page.tsx, _components/
    profile/me/{likes,bookmarks}/
    settings/account/
    projects/page.tsx, new/, [projectId]/
      manage/, reviews/write/
    my-projects/, my-projects/[projectId]/manage/
    matching/, feed/, posts/, following/
    messages/, chats/[projectId]/, notifications/, premium/, admin/
  components/       여러 경로에서 재사용하는 UI와 인접 CSS Module
  lib/              상태 Context·도메인 검증·저장·URL 도우미
  mocks/            데모 원본·선택지·임시 정책
  types/            도메인 타입
  public/           로고·글꼴
  tests/            화면·파일 경로 검사와 Next 내비게이션 테스트 fixture
  next.config.ts, next-env.d.ts, tsconfig.json, vitest.config.ts
backend/             Spring Boot 인증·권한·헬스체크 (업무 API 없음)
scripts/             실제 Chromium 검증과 기존 LAN 보조 스크립트
docs/                정책·Mock 구조·API 후보·시연·검증 기록
```

`src/pages`, `src/features`, `src/services`, `src/styles`, `src/app/providers`, `src/app/router`와 Vite 진입점은 제거했습니다. 라우팅은 `app/**/page.tsx`가 담당합니다. `mocks/routes.ts`는 화면명/메뉴 메타데이터이며 실행 라우터 설정이 아닙니다.

## 브라우저 상태와 렌더링

루트 레이아웃과 page/metadata는 Server Component이고, 폼·버튼 동작·Context는 Client Component입니다. 서버와 첫 클라이언트 렌더에는 같은 로딩 상태를 표시한 뒤 Provider를 마운트하여 localStorage/sessionStorage를 복원합니다. 저장 데이터가 있는 화면을 서버 HTML과 다르게 즉시 렌더하거나 초기값으로 덮어쓰지 않습니다. Mock 기능은 JavaScript 실행 후 표시되며 이번에 본문 전체 SSR/SEO를 구현한 것은 아닙니다.

기존 저장 키/버전은 유지합니다. 같은 브라우저에서만 상태가 공유되며 기기 간 동기화는 없습니다. 저장소 손상/차단/용량 오류를 처리하고, 저장할 수 없을 때는 현재 세션의 변경을 유지하며 안내합니다. [Mock 데이터 구조](docs/mock-data.md)를 확인하세요.

## 데모 계정

| 계정 | 이메일 | 기본 역할 |
|---|---|---|
| 김하늘 | member@devmozip.demo | 일반 회원 |
| 이서준 | premium@devmozip.demo | 프리미엄 / Pocket 주최자 |
| 데브모집 관리자 | admin@devmozip.demo | 관리자 |

위 계정은 서버 DB에 등록되어 있고, 로컬 H2(local 프로필)에서는 `members_data.sql`이 시작 시 넣습니다. 공통 데모 비밀번호는 `Devmozip123!`입니다. 다른 계정으로 바꾸려면 헤더의 로그아웃 후 다시 로그인합니다. 구독 데모 상태는 로그아웃/새로고침 후 유지합니다. 시연 상태를 처음으로 되돌리려면 브라우저 사이트 데이터(localStorage·쿠키)를 삭제합니다. 회원가입은 서버 계정을 만들고 바로 로그인합니다. 프로필 표시는 아직 Mock이라 새 계정도 첫 번째 데모 프로필로 보입니다.

## 기능

- 서버 계정 로그인/로그아웃·회원가입·4단계 온보딩·프로필/헤더 반영
- 본인 Aside·좋아요 날짜·글/프로젝트 북마크·계정 공개/비공개·Mock 연동/설정
- 프로필 상단 + 소개/포트폴리오/내 글/활동/받은 리뷰 5개 탭
- 프로젝트 검색/모집/지원/취소·주최자 승인/팀원/기간/상태/종료 관리
- 종료 알림·기간 연장·요청 링크 기반 팀원 리뷰·3일 재알림·버블/신뢰 온도
- SNS 글/댓글/이미지/기술 태그·좋아요·신고·퍼가기·공유·팔로우
- 1:1/프로젝트 메시지·알림 읽음·강퇴 투표/과반수/접근 제한
- 대화형 개발자/유사 프로젝트 추천·복수 분야/직군별 인원·기록 복원/삭제
- 무료 월 15회/프리미엄 월 100회, 결과 생성만 차감·한국 시각 월 변경
- 프리미엄 구독·정기결제(포트원 테스트 채널: 토스페이먼츠·카카오페이), 해지, 결제 내역, 프리미엄 전용 공고 생성, 관리자 Mock 운영
- 1920/1440/1280/1024/768/390px 대응, 작은 화면 개인 메뉴/AI 기록 접기

추천/운영은 데모입니다. 실제 Gemini·WebSocket은 구현하지 않았습니다. 카카오 로그인·가입·연동은 실제 카카오 API를 사용합니다. 프리미엄 결제는 포트원 V2 API(테스트 채널)를 사용하며 실제 청구는 라이브 계약 후 가능합니다([프리미엄 구독·결제 API](docs/premium-payment-api.md)). 원문 비밀번호는 계정 설정에 저장하지 않습니다. 지원 취소 패널티 수치는 안내용이며 실제 차감하지 않습니다.

## 검증과 문서

최종 결과는 **타입/빌드 성공, 단위 테스트 363개 통과, 실제 Next.js 브라우저·HTTP 검사 398개 통과**입니다. 기존 기능·6개 해상도·동적 경로·저장소 복원·hydration을 확인했습니다. 이전 단계 보고서는 당시 Vite 구조/검증의 기록으로 유지하며 현재 기준은 [Next.js 마이그레이션 보고서](docs/changes-nextjs-migration.md)입니다.

- [분석·계획](docs/nextjs-migration-plan.md)
- [페이지 목록](docs/frontend-pages.md) · [라우팅](docs/routing.md)
- [현재 아키텍처](docs/architecture.md) · [Mock 구조](docs/mock-data.md)
- [미확정 정책](docs/decisions-pending.md) · [향후 API 후보](docs/api-plan.md)
- [발표 시나리오](docs/demo-scenarios.md)

기본 공고 날짜는 2026년 Mock입니다. Pocket 마감은 2026-11-02 한국 시각 끝이므로 이후 시연은 기간 연장이나 새 공고를 사용하세요. 프로젝트 날짜 시뮬레이션은 AI 월별 사용량의 실제 브라우저 시각과 별개입니다.

현재 요청한 Next.js 마이그레이션만 진행했습니다. 커밋/푸시는 별도 명시 요청이 있을 때만 수행합니다.

포트폴리오 자유 편집: `/portfolios/new`에서 작성하고 `/portfolios/[portfolioId]/edit`에서 수정합니다. 섹션 드래그, Markdown/HTML, 초안 복원 및 검증 결과는 [작성기 문서](docs/portfolio-composer.md)를 참고하세요.

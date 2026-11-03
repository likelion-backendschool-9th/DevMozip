# 5차 수정 — 헤더·반응형·통합 검증

2026-10-30. 기존 1~4차 Mock 기능을 유지하고 헤더와 공통 레이아웃을 정리했습니다. 프론트엔드만 실행하여 시연할 수 있습니다. 백엔드·DB·실제 API·WebSocket·스케줄러는 추가하거나 변경하지 않았습니다.

## 구현

- 헤더 오른쪽 검색 입력창을 제거하고 **통합 검색 아이콘 → 검색 페이지**로 연결했습니다. 개발자/프로젝트 선택·검색어·필터·검색 결과 상세 이동을 유지합니다.
- 알림·쪽지·프로필을 같은 높이/간격으로 정렬하고 프로필 드롭다운이 화면 밖으로 나가지 않도록 제한했습니다. 작은 화면은 로고/아이콘과 내비게이션을 두 행으로 배치합니다.
- 본문 100%/최대 너비와 유동적인 간격, Grid/Flex·`auto-fit`/`minmax`·`clamp`/rem을 적용했습니다. 카드 수와 Aside 옆 본문이 가용 너비에 맞춰 바뀝니다.
- 800px 이하에서 개인 Aside를 **개인 계정 메뉴** 버튼으로, AI 기록을 **대화 기록** 버튼으로 접거나 펼칩니다. 이동/새로고침 후 작은 화면은 기본 접힘입니다. 데스크톱은 기존 Aside/기록 구조를 유지합니다.
- 프로필 상단과 5개 탭, 온보딩 4단계, 내 프로젝트/주최자 관리, 상세/지원 모달, AI 조건/결과, SNS, 단체 채팅, 관리자 통계/메뉴를 조정했습니다.
- 긴 소개/URL/카드 문구 줄바꿈, 컨트롤의 최소 너비, 이미지 비율, 모달 최대 너비/높이와 내부 스크롤을 정리했습니다. 페이지 전체의 가로 넘침을 강제로 숨기는 방식은 사용하지 않았습니다. SNS 슬라이드·긴 비교 표·대화의 필요한 내부 스크롤은 유지합니다.
- 화면 이동 시 본문 포커스는 유지하면서 추가 포커스 스크롤을 방지합니다. 기존 로그인·구독·지원·투표·리뷰·추천 데이터 스키마/버전은 변경하지 않았습니다.

## 변경 파일

| 구분 | 파일 | 역할 |
|---|---|---|
| 공통 헤더 | `frontend/src/components/layout/AppLayout.tsx` | 검색창 제거/검색 아이콘, 이동 시 포커스 처리 |
| 개인 계정 Aside | `frontend/src/components/layout/AccountLayout.tsx` | 접근성 이름/펼침 상태/연결 메뉴 |
| AI 채팅 | `frontend/src/pages/MatchingPage.tsx` | 작은 화면 대화 기록 메뉴 |
| 공통 반응형 | `frontend/src/styles/responsive.css` | 너비·그리드·폰트·줄바꿈·페이지별 반응형 |
| 스타일 진입 | `frontend/src/main.tsx` | 공통 반응형 CSS 적용 |
| 기존 화면 테스트 | `frontend/src/pages/SearchPage.test.tsx` | 페이지 검색창 1개와 헤더 이동 아이콘 검증 |
| 반응형 검사 | `scripts/verify-responsive.mjs` | 6개 너비의 화면/상태와 가로 넘침·헤더 겹침/정책 측정 |
| 해상도별 조작 검사 | `scripts/verify-responsive-interactions.mjs` | 검색·Aside·기록·모달·지원/승인/채팅·종료/리뷰·긴 문구 |
| 기존 통합 검사 | `scripts/verify-presentation.mjs` | 저장소 오류 상황에서도 새 검색 아이콘 경로로 이동 |

문서: `README.md`, `docs/frontend-pages.md`, `docs/routing.md`, `docs/mock-data.md`, `docs/architecture.md`, `docs/api-plan.md`, `docs/decisions-pending.md`, `docs/demo-scenarios.md`, 이 보고서. 근거는 `docs/artifacts/revision-5/`에 저장했습니다.

## 빌드·단위 검증

- `cd frontend && npm run build`: TypeScript 및 Vite 성공. 최종 빌드에 청크 크기 경고 없음.
- `cd frontend && npm test`: **25개 파일, 361개 통과**. 라우팅 중복/정적·동적 매칭, 상태 검증/저장, 리뷰·투표·권한·월 한도 등 기존 검사를 포함합니다.
- 명시 라우트 28개와 404를 유지했습니다. 페이지/경로 표의 과거 관리자 공고 생성 허용 표기를 현재 프리미엄 전용 정책으로 바로잡았습니다.
- 이번에는 백엔드 빌드를 재실행하지 않았습니다. 프론트 검증용 `localhost:4173`을 사용하고 백엔드를 시작하지 않았습니다.

## 실제 브라우저 검사

Chromium, 높이 1000px, 한국 locale/timezone의 격리 브라우저를 사용했습니다. 발표자의 기존 localStorage는 변경하지 않았습니다. 최종 집계는 아래 원본 JSON에 근거합니다.

| 검사 | 결과 | 근거 |
|---|---|---|
| 6개 해상도 × 화면/상태 36개 | 216/216 | [반응형 JSON](artifacts/revision-5/responsive/report.json) |
| 6개 해상도 × 실제 조작 흐름 7개 | 42/42 | [조작 JSON](artifacts/revision-5/interactions/report.json) |
| 기존 페이지 및 A~E 통합 흐름 | 48/48 | [회귀 JSON](artifacts/revision-5/regression/report.json) |
| 1차 개인 계정/온보딩 | 12/12 | [계정 JSON](artifacts/revision-5/account-regression/report.json) |
| 2차 프로젝트/리뷰 | 12/12 | [프로젝트 JSON](artifacts/revision-5/project-regression/report.json) |
| 3차 강퇴/모집 권한 | 12/12 | [강퇴 JSON](artifacts/revision-5/chat-regression/report.json) |
| 4차 대화형 AI/사용량 | 13/13 | [AI JSON](artifacts/revision-5/matching-regression/report.json) |

총 **355개 브라우저 검사 통과**. 각 보고서의 콘솔 오류·런타임 오류·비즈니스 XHR/fetch 요청은 0건입니다. 기존 97개 회귀 검사는 1440×1000에서, 추가 258개는 아래 6개 너비에서 수행했습니다.

| 화면 너비 | 화면/상태 측정 | 실제 조작 | 페이지 가로 넘침/헤더 겹침 |
|---|---|---|---|
| 1920px | 36/36 | 7/7 | 없음 |
| 1440px | 36/36 | 7/7 | 없음 |
| 1280px | 36/36 | 7/7 | 없음 |
| 1024px | 36/36 | 7/7 | 없음 |
| 768px | 36/36 | 7/7 | 없음 |
| 390px | 36/36 | 7/7 | 없음 |

화면 측정에는 메인, 본인 프로필 5개 탭, 공개 프로필, 좋아요/북마크/설정, 내 프로젝트/주최자 관리, 프로젝트 목록/상세/작성, AI 초기/결과/조건, SNS 목록/상세/작성, 단체/1:1 채팅, 알림, 검색, 프리미엄, 관리자 대시보드/회원/키워드, 로그인/가입/404와 **실제 온보딩 입력 4단계**를 포함합니다. 온보딩은 데모 완료 안내를 찍는 대신 격리 계정 상태에 유효 초안을 넣어 입력 화면을 확인했습니다.

실제 조작은 각 너비에서 검색 아이콘→프로젝트 검색→상세, 개인 메뉴 이동/5개 탭, 알림/쪽지/프로필 드롭다운, AI 추천/기록 복원/삭제 모달, 무료 작성 제한/지원→승인→채팅, 종료 예정일 알림→기간 연장→프로젝트 종료→팀원 평가→버블/온도, 긴 소개/연락 시간/URL 저장·줄바꿈을 확인했습니다. 768/390px는 Chromium 터치 에뮬레이션도 사용했습니다.

기존 회귀로 좋아요/북마크 즉시 연동, 신규 가입→온보딩→프로필, 계정 비공개, 지원 취소/팀원, 3일 리뷰 재알림/기한/중복 제출, 강퇴 과반수/대상자·중복 투표 차단/접근 제한, 무료 공고 제한·프리미엄 활성화, AI 복수 분야/직군·계정별 기록·15/100회·월 경계, localStorage 손상/차단/용량 오류를 확인했습니다.

## 화면 근거

- [390px 메인](artifacts/revision-5/responsive/390-home.png), [개인 프로필/접힌 Aside](artifacts/revision-5/responsive/390-profile.png), [AI 기록 메뉴](artifacts/revision-5/responsive/390-matching-results.png)
- [390px 온보딩](artifacts/revision-5/responsive/390-onboarding-1.png), [관리자](artifacts/revision-5/responsive/390-admin.png), [단체 채팅](artifacts/revision-5/responsive/390-chat.png)
- [1024px 관리자](artifacts/revision-5/responsive/1024-admin.png), [1440px 메인](artifacts/revision-5/responsive/1440-home.png), [1920px 프로필](artifacts/revision-5/responsive/1920-profile.png)
- [390px 팀원 리뷰 입력](artifacts/revision-5/interactions/390-review-write.png), [긴 소개/URL](artifacts/revision-5/interactions/390-long-profile.png)

## 수정한 검증 실패와 남은 사항

헤더 검색창을 제거한 뒤 기존 저장소 오류 검사가 이전 입력창을 찾는 실패가 있어 새 검색 아이콘/페이지 검색 경로로 바꿨습니다. 추가 조작 스크립트의 알림 제목/전송 버튼 이름과 초기 프로필이 아직 저장되지 않은 테스트 준비 절차도 실제 화면/저장 흐름에 맞춰 수정했습니다. 최종 전체 재실행 결과 미통과 항목은 없습니다.

실제 스마트폰 하드웨어, iOS Safari/Firefox, 가상 키보드·브라우저 확대, 1000px 외 높이의 모든 조합, 실제 다른 PC/LAN 접속은 이번에 검증하지 않았습니다. 에뮬레이션 결과를 해당 환경 검증으로 간주하지 않습니다.

Mock은 같은 브라우저에서만 공유합니다. 실제 API/AI/결제·서버 권한/스케줄러·다중 기기 동기화는 구현하지 않았습니다. 강퇴 후 프로젝트 참여 상태, 해지 후 기존 주최자 관리, 리뷰 기간/온도와 추천 점수 등 기존 미확정 정책은 [정책 문서](decisions-pending.md)에 유지했습니다. 현재 5차만 완료했으며 후속 작업은 시작하지 않았습니다.

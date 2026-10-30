# 4차 수정 — 대화형 AI 추천

2026-10-30. 기존 React/TypeScript/Vite, 프로필·프로젝트 Mock과 계정별 월 사용량을 재사용했습니다. 실제 Gemini/GPT 등 AI API, 백엔드, DB, WebSocket은 구현하지 않았습니다.

## 구현

- 메인은 설명 입력/추천 시작/서비스 안내만 제공합니다. 시작 시 `/matching`의 새 대화에 설명을 첫 사용자 메시지로 전달합니다.
- 왼쪽 새 대화/기록/제목/생성 날짜/삭제, 중앙 메시지·추가 질문·결과, 하단 입력/전송으로 구성했습니다. Enter 전송, Shift+Enter 줄바꿈, 한글 조합 중 전송 방지를 지원합니다.
- 프로젝트 분야 복수 선택과 기타 직접 입력/태그 삭제. 관심 분야/기술 복수 선택. 모집 직군 복수 선택과 직군별 1~20명, 기타 직군 직접 입력/삭제를 제공합니다.
- 설명, 프로젝트 분야, 관심 분야, 기술, 직군/인원, 경력, 기간 중 부족한 항목을 질문합니다. 알려진 기술/분야/직군 별칭과 `백엔드 2명`, `3년 이상`, `3개월` 등의 Mock 규칙을 분석합니다. 임의 자연어 전체를 이해하는 AI는 아닙니다. 조건 설정으로 직접 보완할 수 있습니다.
- 필수 조건이 처음 충족되면 결과를 생성합니다. 이후 일반 메시지는 기록/안내만 제공하며 ‘조건으로 추천 받기’ 또는 추천 요청 메시지로 재생성합니다. 조건 선택·추가 질문·일반 메시지·기록 조회·삭제는 차감하지 않습니다.
- 개발자/유사 프로젝트 점수에 복수 분야·관심·기술·직군/모집 인원·요구 경력을 반영합니다. 프로필의 복수 직군도 비교합니다. 기간은 프로필에 일정 데이터가 없으므로 협의 필요로 표시합니다. 직접 일치가 없는 개발자도 낮은 점수의 탐색 후보로 포함될 수 있습니다.
- 점수는 데모 계산임을 표시합니다. 개발자 카드에서 직군·기술·경력·신뢰 온도, 프로필 이동, 협업 제안을 제공합니다. 제안은 기존 Mock 메신저에 즉시 반영됩니다.
- 상세 이유는 기존 프리미엄/관리자 열람 권한을 재사용하며 무료는 잠금 UI를 표시합니다. 프로젝트 공고 생성의 3차 ‘프리미엄만’ 정책은 유지합니다.
- 대화별 사용자 입력·AI 응답·추천 당시 결과/조건을 저장합니다. 현재 계정 기록만 표시하고 비로그인 기록은 별도 공간을 사용합니다. 비공개 프로필은 저장 결과에서도 현재 공개 권한에 따라 숨깁니다.
- 기존 `devmozip:home:v1`에 대화를 추가했습니다. 이전 단일 추천은 대화로 이관하며 사용량은 유지합니다. 대화를 삭제해도 차감량은 돌아오지 않고 삭제 기록이 재생성되지 않습니다. 손상된 대화는 제외하고 저장 실패는 기존 세션 유지/토스트로 안내합니다. 전체 데모 초기화는 대화도 초기화합니다.
- 무료 월 15회, 프리미엄 월 100회. 결과와 차감량을 같은 Home 상태 변경으로 저장합니다. 한국 시각 월 키로 분리하고 월 변경/탭 복귀 시 현재 한도를 갱신합니다. 다른 브라우저/기기와는 동기화하지 않습니다.

## 변경 파일

`frontend/src/` 기준입니다.

| 영역 | 파일 |
|---|---|
| 타입·Mock 선택지/분석 별칭 | `types/matchingChat.ts`, `mocks/matchingChat.ts`, `types/home.ts`, `mocks/home.ts` |
| 분석·추천·검증·복원/이관 | `services/matchingChatService.ts`, `services/homeService.ts` |
| 공통 사용량·대화 상태 | `app/providers/HomeProvider.tsx` |
| 메인 시작·조건·카드·제안 | `features/matching/MatchingStart.tsx`, `features/matching/ChatConditions.tsx`, `features/matching/ChatResults.tsx` |
| 페이지·경로·스타일 | `pages/HomePage.tsx`, `pages/MatchingPage.tsx`, `app/router/AppRouter.tsx`, `styles/matching-chat.css`, `main.tsx` |
| 테스트 | `services/matchingChatService.test.ts`, `pages/FollowingMatching.test.tsx` |

추가: `scripts/verify-matching-chat.mjs`. 기존 `scripts/verify-presentation.mjs`의 A 시나리오 진입 부분을 새 대화 UI에 맞췄습니다. README·페이지 목록·시연 순서·정책·이 보고서를 갱신했습니다. 기존 라우트 `/matching`을 사용하며 새 의존성을 설치하지 않았습니다.

## 검증

- `npm run build`: TypeScript 및 Vite 성공. 추천 페이지 지연 로딩으로 최종 메인 JS는 488.43 kB, 청크 크기 경고 없음.
- `npm test`: 25개 파일, 361개 통과. 신규 18개는 복수 조건 추출·추가 질문·조건 누적·순위 변화·경력/인원·본인 제외·무료/프리미엄 한도·월 경계·일반 대화 비차감·복원/손상/이관·비보안 HTTP ID 생성을 검사합니다.

실제 Chromium 1440×1000의 격리 브라우저에서 **97개 검사 모두 통과**했습니다(신규 13 + 기존 회귀 84). 각 보고서의 콘솔 오류·런타임 오류·비즈니스 XHR/fetch 요청은 0건입니다. 메인 전달, 조건 설정, 실제 협업 제안 전송, 기록 복원/삭제, 무료 15회·프리미엄 100회 제한, 한국 시각 월 변경을 확인했습니다. 발표자의 기존 localStorage는 사용하지 않았습니다.

| 검사 | 근거 |
|---|---|
| 대화형 추천 13/13 | [JSON](artifacts/revision-4/report.json) |
| 기존 페이지/A~E 48/48 | [JSON](artifacts/revision-4/regression/report.json) |
| 강퇴/모집 권한 12/12 | [JSON](artifacts/revision-4/chat-regression/report.json) |
| 개인 공간/온보딩 12/12 | [JSON](artifacts/revision-4/account-regression/report.json) |
| 프로젝트/리뷰 12/12 | [JSON](artifacts/revision-4/project-regression/report.json) |

[대화 화면](artifacts/revision-4/initial-chat.png), [복수 조건](artifacts/revision-4/multi-conditions.png), [추천 결과](artifacts/revision-4/recommendation-results.png), [프리미엄 상세 이유](artifacts/revision-4/premium-reasons.png).

초기 검증에서 개편 전 단일 폼을 가정한 화면 테스트를 새 대화 구조로 수정했습니다. 실제 브라우저에서는 설명/선택 상자의 접근성 이름을 보완한 뒤 재검증했습니다. 일반 HTTP에서 `crypto.randomUUID`가 없는 경우는 기존 Mock ID 유틸로 처리하고 대화 스크롤은 메시지 영역 안으로 제한했습니다. 최종 실패 항목은 없습니다. 실제 LAN 기기 접속 자체는 이번에 재검증하지 않았습니다.

## 범위와 한계

서비스 정책으로 확정한 AI 점수/추천 품질이 아닙니다. 개발자 점수는 기술 최대 24점, 분야 최대 18점, 관심 최대 12점, 직군/모집 인원 가점, 경력 충족·협업 상태를 조합하여 20~98점으로 제한합니다. 인원은 수요 가중치이며 실제 인원 충원이나 자동 팀 편성을 보장하지 않습니다. 임의 분야/직군의 직접 일치 데이터가 없으면 탐색 후보를 표시합니다. 기간의 실제 일정 매칭은 후속 정책/데이터가 필요합니다.

백엔드 기반 인증/사용량 원자성·기기 간 기록 동기화·실제 AI 응답·결제는 이번 범위에 없습니다. 4차만 진행했습니다.

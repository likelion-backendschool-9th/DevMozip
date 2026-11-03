# 3차 수정 완료 보고서

2026-10-30. 이번 범위는 단체 채팅 강퇴 투표와 프로젝트 모집 권한입니다. 백엔드·DB·실제 API·WebSocket·스케줄러는 변경하거나 구현하지 않았습니다.

## 구현 결과

- 팀원 목록에서 대상/사유 선택 후 투표 생성. 찬성/반대/전체 유권자/과반수 진행 상태와 종료 이력을 표시합니다.
- 대상자 제외, 계정당 1회, 요청자 자동 찬성 없음. `찬성 > 유권자 / 2`일 때만 가결됩니다. 정확히 절반이면 미달입니다.
- 가결 시 채팅 멤버/메신저 목록에서 제외하고 메시지 읽기/쓰기 및 직접 URL 접근을 제한합니다. 기존 방 기록은 다른 팀원에게 유지합니다. 결과 알림은 투표/수신자별 중복을 막습니다.
- 새로고침/멤버 동기화 후에도 채팅 제외 상태가 유지됩니다. 기존 프로젝트 참여/활동/리뷰 자격은 임시 유지합니다.
- 프리미엄만 공고 생성 가능. 무료 회원의 작성 버튼은 안내 모달을 열며 같은 계정의 데모 구독 활성화 후 작성할 수 있습니다. 관리자도 생성 우회는 불가합니다.
- 해당 공고의 프리미엄 주최자는 지원자가 제출한 포트폴리오 상세를 모달로 확인합니다. 해지 시 잠금, 재활성화 시 복원됩니다. 다른 지원서/작성자 포트폴리오는 열지 않습니다.
- 무료 조회/검색/지원/취소/북마크/참여 및 기존 프로젝트 관리·팀원·알림·리뷰 흐름을 유지합니다.

## 변경 파일

경로는 `frontend/src/` 기준입니다.

| 역할 | 파일 |
|---|---|
| 투표 타입·선택지·임시 정책 | `types/chatKick.ts`, `mocks/chatKick.ts`, `types/project.ts`, `mocks/project.ts` |
| 투표 검증·집계·퇴장·결과 알림·저장 검증 | `services/chatKickService.ts`, `services/projectService.ts`, `services/projectManagementService.ts`, `services/projectSelectors.ts`, `app/providers/ProjectProvider.tsx` |
| 구독/지원자 상세 권한 | `services/projectPermissionsService.ts`, `features/project/PremiumProjectGate.tsx`, `features/project/ProjectCreateButton.tsx`, `features/project/ApplicantPortfolioButton.tsx` |
| 채팅 접근·투표 UI | `services/messagingService.ts`, `features/chat/KickVotePanel.tsx`, `pages/ProjectChatPage.tsx`, `styles/chat-kick.css`, `main.tsx` |
| 모집/관리/참여 화면 | `pages/ProjectsPage.tsx`, `pages/MyProjectsPage.tsx`, `pages/CreateProjectPage.tsx`, `pages/ProjectManagePage.tsx`, `pages/MyProjectManagePage.tsx`, `pages/PremiumPage.tsx`, `features/project/ProjectDateSimulator.tsx` |
| 관리 페이지 지연 로딩 | `app/router/AppRouter.tsx` |
| 테스트 | `services/chatKickService.test.ts`, `services/projectService.test.ts` |

추가 검증 스크립트: `scripts/verify-chat-kick.mjs`. 문서: README, 페이지 목록, 미확정 정책, 향후 API, 시연 순서, 이 보고서와 `docs/artifacts/revision-3/` 실제 검증 기록. 새 라우트나 서버 의존성은 추가하지 않았습니다.

## 실제 검증 결과

- `cd frontend && npm run build`: TypeScript 및 Vite 성공. 관리 페이지를 지연 로딩하여 메인 JS 483.93 kB이며 최종 빌드에 청크 크기 경고가 없습니다.
- `cd frontend && npm test`: 24개 파일, **343개 통과**. 대상/중복/외부 사용자 차단, 홀수·짝수 과반수, 반대·만료·기권, 주최자 대상, 참여자 변경 취소, 채팅 접근 제한, 재초대 방지, 알림 중복, 저장 검증/이전 데이터 호환, 구독/제출 포트폴리오 범위를 검사했습니다. 강퇴 후 프로젝트 종료 리뷰 대상 유지도 검증했습니다.
- 실제 Chromium, 프로덕션 미리보기 `localhost:4173`, 1440×1000, 격리 브라우저에서 **84개 통과**. 발표자의 기존 localStorage는 사용하지 않았습니다.

| 검사 | 결과 | 근거 |
|---|---|---|
| 3차 강퇴·권한 | 12/12 | [JSON](artifacts/revision-3/report.json) |
| 2차 프로젝트·리뷰 회귀 | 12/12 | [JSON](artifacts/revision-3/project-regression/report.json) |
| 1차 계정·온보딩 회귀 | 12/12 | [JSON](artifacts/revision-3/account-regression/report.json) |
| 기존 화면·A~E 흐름 회귀 | 48/48 | [JSON](artifacts/revision-3/regression/report.json) |

각 보고서의 콘솔 오류·런타임 오류·비즈니스 XHR/fetch 요청은 0건입니다. 단체 채팅에서 중복 투표 버튼 차단, 대상자 투표 차단, 1/2 미달, 반대 집계, 72시간 만료, 1/1 가결, 자동 퇴장, 새로고침 접근 제한과 결과 알림을 실제로 확인했습니다. 무료 지원→승인→프리미엄 주최자 상세 열람 및 구독 해지/재활성화도 확인했습니다.

시각 확인: [프리미엄 안내](artifacts/revision-3/premium-gate.png), [과반수 미달](artifacts/revision-3/vote-half.png), [가결](artifacts/revision-3/kick-passed.png), [강퇴 대상 접근 제한](artifacts/revision-3/kicked-denied.png).

초기 검증 중 기존 관리자 공고 생성 허용 테스트가 새 정책과 충돌하여 관리자 불가 기대값으로 수정했습니다. 브라우저 스크립트의 동일 이름 닫기 버튼 선택 충돌도 대상을 명시하여 수정 후 전체 재실행했습니다. 최종 미통과 항목은 없습니다.

## 임시 정책·남은 사항

- 주최자 동일 1표, 72시간, 기권 분모 유지, 방당 진행 1개, 구성 변경/종료 시 취소는 임시 정책입니다.
- 강퇴 후 프로젝트 참여 상태·주최자 승계·복귀 및 재투표 정책은 미확정입니다. 이번에는 채팅만 제외하고 프로젝트 참여/역할/리뷰 자격을 유지합니다. 자동 재초대/수동 복귀 UI는 제공하지 않습니다.
- 해지한 기존 주최자의 기존 공고 관리는 임시 유지하되 신규 공고/지원자 상세는 제한합니다. 최종 해지 정책은 미확정입니다.
- 기존 날짜 시뮬레이터로 만료를 검사합니다. 실제 서버 시각, 동시성/원자성, 인증/인가, WebSocket 연동은 향후 작업입니다. 브라우저 Mock은 보안 경계가 아닙니다.

[정책 상세](decisions-pending.md), [시연 순서](demo-scenarios.md), [향후 서버 계약 후보](api-plan.md). 3차만 완료했으며 후속 단계는 시작하지 않았습니다.

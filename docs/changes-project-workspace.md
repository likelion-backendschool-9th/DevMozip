# 프론트엔드 2차 수정 — 내 프로젝트·기간 알림·종료 후 리뷰

현재 요청한 2차 작업만 구현했습니다. 1차 개인 공간·비공개 계정·온보딩과 기존 모집/지원/승인/채팅/활동 데이터를 유지했습니다. 백엔드·DB·실제 API·서버 스케줄러는 변경하지 않았습니다.

## 구현 완료

| 요구 | 구현 |
|---|---|
| 내 프로젝트 | `/my-projects` 전체/내 모집/내 참여/지원 중/종료 탭, 주최자·역할·상태·모집/개발 기간·현재 인원·관리/채팅 연결 |
| 역할별 관리 | `/my-projects/:projectId/manage`: 주최자는 기존 공고/지원/팀원/상태/연장/시작/종료 관리, 참여자는 참여 정보·역할·상태·채팅·리뷰 알림 |
| 기존 경로 | `/projects/:projectId/manage` 주최자 경로 유지, ActivityTab/헤더에 내 프로젝트 이동 연결 |
| 기간 종료 알림 | 예정 개발 종료일을 넘긴 미종료 프로젝트의 주최자에게 기존 종료일·연장 안내·수정/종료 버튼 생성 |
| 기간 갱신 | 연장/종료 후 이전 안내 처리 완료, 새 종료일 경과 시 새 안내, 동일 프로젝트/종료일 중복 방지 |
| 종료 리뷰 요청 | 실제 종료 시각과 팀원 기준 계정별 요청·임시 토큰·만료·사용 상태, 알림의 작성 링크 |
| 프로필 리뷰 | 받은 리뷰·평균·버블·온도·별 색상 **조회 전용**. 직접 작성 폼 및 기존 UI 작성 경로 제거 |
| 리뷰 작성 | `/projects/:projectId/reviews/write?request=...&token=...`, 본인 제외 팀원별 별점 필수·키워드 선택·제출 상태·전체 제출 |
| 권한/중복 | 종료 상태·참여·수신자·프로젝트/요청/토큰·기한·사용 여부·중복 평가 Mock 검증 |
| 재알림 | 미완료자에게 3일 구간별 1회, 완료/만료 시 중단, 새로고침/동일 날짜/되돌리기 중복 방지 |
| 날짜 시연 | 내 프로젝트/관리/리뷰 화면의 날짜 선택·+3일·실제 날짜 복원, 현재 적용 시각 상단 배너 |
| 결과 연동 | 기존 ProfileProvider 리뷰에 묶음 반영하여 평균·버블·신뢰 온도·별 색상에 즉시 반영 |

평가에 자유 서술형 입력은 없습니다. 팀원 일부의 필수값이 잘못되면 아무 평가도 부분 반영하지 않습니다. 이전에 제출된 개별 평가는 유지하고 남은 대상만 전체 제출할 수 있습니다.

## 임시 정책과 저장

- `mocks/projectWorkspace.ts`의 임시 상수: 리뷰 작성 기간 **30일**, 재알림 **3일(72시간)**, 초기 온도 **36.5°C**, 계산 **36.5 + Σ(별점 − 3) × 0.5**, 0~100°C 제한/소수 첫째 자리. 최종 계산식·기한·색상 정책은 미확정입니다.
- 예정 개발 종료일은 한국 시각 해당 일 23:59:59.999까지 정상입니다. 알림은 프로젝트/기존 종료일로 중복을 막습니다. 해결된 알림은 이력과 링크를 유지하고 안 읽은 개수에서 제외합니다.
- 리뷰 대상은 종료 시점의 본인 외 팀원으로 고정합니다. 단독 팀은 요청하지 않습니다. 기존 고정 종료 Mock과 받은 리뷰는 그대로 보존하고 소급 요청하지 않습니다. 실제 종료 처리 시각이 기록된 기존/신규 프로젝트부터 요청을 생성합니다.
- 기존 `devmozip:recruitment:v1`에 `reviewRequests`, 선택적 `simulationDate`, 알림 액션/해결/요청 메타데이터를 확장했습니다. 기존 저장값은 기본값을 보충하며 공고·지원·북마크·팀원·채팅 데이터를 유지합니다. 예전 관리 종료의 리뷰 알림은 새 링크 메타데이터로 연결하여 동일 요청을 중복 생성하지 않습니다.
- 리뷰 본문 데이터는 기존 `devmozip:profiles:v1`에 저장합니다. 요청 완료·알림 해결 상태는 프로젝트 저장소에 남습니다. 재로드 시 실제 평가 기록을 기준으로 완료를 동기화합니다. 저장 실패로 완료 메타데이터만 남으면 기한 내 남은 평가를 재시도할 수 있게 복원합니다.
- 시뮬레이션 날짜는 브라우저 계정 전환/새로고침 후에도 유지합니다. 실제 날짜 복원은 생성 이력을 취소하지 않습니다. 전체 데모 초기화는 기존과 같이 전체 상태를 복원합니다.
- 비교는 앱 로드/작업/수동 날짜 변경 및 앱이 열린 동안 기존 60초 확인에서 수행합니다. 실제 백그라운드/서버 스케줄러·푸시는 없습니다. 활성 기간을 건너뛰면 누락된 3일 구간의 안내를 보완하고 만료 후에는 추가하지 않습니다.

## 실제 검증 결과

| 검사 | 결과 |
|---|---|
| `npm run build` | TypeScript/Vite 성공, 경고 없음, 주 JS 491.60KB |
| `npm test` | **23개 파일 / 323개 테스트 통과** |
| 2차 신규 서비스 검사 | 20개: 내 프로젝트 필터·기간 경계/갱신/중복·요청·전체 검증·저장 복구·3일/완료/만료 |
| 2차 실제 Chromium | **12개 통과**, 1440×1000 |
| 1차 계정/비공개/온보딩 회귀 | **12개 통과** |
| 기존 A~E/화면/저장 회귀 | **48개 통과**, D를 요청 링크/전체 평가 흐름으로 갱신 |
| 브라우저 콘솔/런타임 오류 | 세 실행 모두 0건 |
| XHR/fetch 비즈니스 요청 | 세 실행 모두 0건 |
| 백엔드 | 변경·실행·재빌드 없음 |

실제 2차 흐름: 비로그인 안내 → 내 프로젝트 5탭/무관한 계정 제한 → 지원/내 지원/승인/참여자 채팅 → 2027-03-04 종료일 경과 알림 → 기간 연장/기존 알림 해결 → 모집 완료/프로젝트 시작 → 2027-04-04 새 알림/종료 버튼 → 본인 요청 링크 → 프로필 직접 작성 없음/타인·변조 링크 차단 → +3일 재알림/중복 없음 → 전원 별점/선택 키워드/전체 제출/새로고침·중복 방지 → 평균/버블/온도 → +6일 완료자 제외/미완료자만 안내 → 종료+31일 만료/작성 차단.

최종 검사에서 실패한 항목은 없습니다. 기존 테스트 3개는 제거된 프로필 직접 작성/옛 프로필 리뷰 링크를 기대해 중간에 실패했으며 새 요구인 조회 전용/프로젝트 요청 링크를 검증하도록 갱신한 후 전체 통과했습니다. 스크린샷은 지연 로딩된 작성 화면의 제목/제출 버튼이 나타난 뒤 저장하도록 검증 대기도 보완했습니다.

## 검증 근거와 화면

- [2차 12개 결과](artifacts/revision-2/report.json)
- [1차 12개 회귀 결과](artifacts/revision-2/account-regression/report.json)
- [기존 48개 회귀 결과](artifacts/revision-2/regression/report.json)
- [내 프로젝트](artifacts/revision-2/my-projects.png)
- [기간 안내 해결](artifacts/revision-2/deadline-resolved.png)
- [주최자 리뷰 요청 현황](artifacts/revision-2/host-review-status.png)
- [팀원별 평가](artifacts/revision-2/review-write.png)
- [전체 제출 완료](artifacts/revision-2/review-submitted.png)
- [프로필 결과](artifacts/revision-2/profile-review-result.png)
- [기간 만료](artifacts/revision-2/review-expired.png)

일반 브라우저의 기존 데이터를 건드리지 않는 격리된 Chromium 컨텍스트와 프론트 프로덕션 미리보기 `http://localhost:4173`에서 검증했습니다. 다른 실제 기기/모바일/Safari/Firefox 검증은 수행하지 않았습니다.

## 변경 파일

`frontend/src/` 신규:

- `types/projectReview.ts`, `mocks/projectWorkspace.ts`
- `services/projectNoticeService.ts`, `services/projectNoticeView.ts`, `services/projectDeadlineService.ts`, `services/projectReviewService.ts`, `services/myProjectsService.ts`
- `features/project-workspace/ProjectDateSimulator.tsx`, `features/project-workspace/ReviewRequestStatus.tsx`
- `pages/MyProjectsPage.tsx`, `pages/MyProjectManagePage.tsx`, `pages/ProjectReviewWritePage.tsx`
- `styles/project-workspace.css`, `services/projectWorkspace.test.ts`

기존 수정:

- `types/project.ts`, `types/profile.ts`, `types/messaging.ts`, `mocks/project.ts`: 요청/알림/날짜/묶음 평가 타입 및 기본값.
- `services/projectManagementService.ts`, `services/projectService.ts`, `services/profileService.ts`, `services/messagingService.ts`: 기간 동기화·요청/알림·이관·전체 평가·프로필 결과·해결 알림.
- `app/providers/ProjectProvider.tsx`, `ProfileProvider.tsx`, `MessagingProvider.tsx`: 공통 시각·날짜 시뮬레이션·리뷰 전체 제출·기존 직접 작성 제한·메시지/알림 상태.
- `pages/ProjectManagePage.tsx`, `ProjectChatPage.tsx`, `ProjectDetailPage.tsx`, `ProjectsPage.tsx`, `NotificationsPage.tsx`: 주최/기간/리뷰 동작과 공통 시각·알림 버튼/해결 표시.
- `features/review/ProfileReviews.tsx`: 받은 리뷰 조회 전용.
- `features/project/ProjectNotices.tsx`, `ApplicationModal.tsx`, `CreateProjectForm.tsx`, `RecruitmentCard.tsx`: 알림 링크/액션·기간 기준.
- `features/user/ActivityTab.tsx`, `components/layout/AppLayout.tsx`: 내 프로젝트 연결·시뮬레이션 배너.
- `app/router/AppRouter.tsx`, `mocks/routes.ts`, `main.tsx`: 신규 3경로와 스타일.
- `pages/ProfilePage.test.tsx`, `pages/ProjectManagePage.test.tsx`, `services/messagingService.test.ts`: 조회 전용/요청 링크 검증.

검증/문서:

- `scripts/verify-project-workspace.mjs` 신규, `scripts/verify-presentation.mjs`의 D 갱신.
- `docs/artifacts/revision-2/`의 실제 결과/스크린샷.
- `README.md`, `docs/frontend-pages.md`, `docs/decisions-pending.md`, `docs/demo-scenarios.md`, `docs/api-plan.md`, 본 보고서.

## 실행 및 재현

```bash
cd frontend
npm run dev
# 또는
npm run build
npm run preview -- --port 4173 --strictPort
```

Playwright가 준비된 환경에서 저장소 루트:

```bash
PLAYWRIGHT_BROWSERS_PATH=/tmp/devmozip-browser/browsers \
DEVMOZIP_PLAYWRIGHT_MODULE=/tmp/devmozip-browser/node_modules/playwright/index.mjs \
  node scripts/verify-project-workspace.mjs
```

도구는 이전 검증과 같은 `/tmp` 설치를 재사용했고 프로젝트 의존성은 추가하지 않았습니다. 자세한 수동 순서는 [발표 시나리오](demo-scenarios.md)의 2차 절을 참고하세요. 기존 공고 지원 마감이 지난 날짜에 실행한다면 모집 기간/테스트 기준을 조정해야 합니다.

## 미구현 및 후속 보안 사항

임시 URL은 실제 인증/인가·서명 토큰이 아니며 프론트 데이터 조작을 막는 보안 경계가 아닙니다. 향후 서버가 로그인 수신자, 종료 당시 멤버, 대상, 만료/사용 상태를 검증하고 안전한 토큰 저장/폐기와 프로젝트·작성자·대상 고유 제약 및 원자적 제출 처리를 해야 합니다. 만료 링크 재발급/리뷰 수정/분쟁·탈퇴·삭제/리마인더의 실제 운영 정책은 미확정입니다.

실제 API/서버 알림/백그라운드 스케줄러/DB는 의도적으로 구현하지 않았습니다. 다음 수정 단계는 시작하지 않았습니다.

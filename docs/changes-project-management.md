# 주최자 관리 구현 보고

## 구현 기능

- 주최자 전용 관리 페이지: 지원자/팀원/운영 및 알림 탭, 내 공고 선택, 공고 수정/삭제.
- 기존 프리미엄 공고 작성 폼을 수정 모드에서도 사용. 기존 지원자·팀원의 직군/정원을 보호합니다.
- 지원서/제출 포트폴리오·멘트 조회, 승인/거절, 승인 중복·정원 초과 방지.
- 승인 시 지원 상태, 팀원, 총/직군별 인원, 채팅 멤버 준비 상태 및 승인 알림을 함께 반영. 승인 후 취소 시 모두 해제.
- 상태 전환/재모집/기간 연장/종료. 종료 전 대기 지원서 처리, 종료 후 변경/취소 차단.
- 대기 지원자/기존 또는 승인 팀원이 있으면 삭제 제한. 빈 공고 삭제 시 목록·검색·활동에서 제외하고 관련 사용자 알림 유지.
- 지원/승인/거절/취소/수정/상태/기간/삭제/종료·리뷰 알림. 현재 계정의 프로젝트 알림 화면과 헤더 표시.
- 모집 기한 경과 시 관리자 알림. 프로젝트/마감일당 중복 방지. 브라우저 시작/동작/60초 주기 확인만 수행.
- 종료 시각 저장과 동적 협업 이력 연결. 기존 프로필 리뷰 화면에서 즉시 리뷰 가능하며 중복/본인/비협업자/기간 조건 유지.
- 기존 localStorage v1의 지원/북마크를 유지하면서 새 관리 필드를 기본값으로 이관합니다. 공통 초기화에 포함합니다.

## 변경 파일

src 경로는 frontend 기준입니다.

신규:
- src/services/projectSelectors.ts, projectManagementService.ts, projectValidation.ts
- src/services/projectManagementService.test.ts
- src/features/project/ProjectNotices.tsx
- src/pages/ProjectManagePage.tsx, ProjectManagePage.test.tsx, ProjectNotificationsPage.tsx
- src/styles/project-management.css

수정:
- src/types/project.ts, src/types/profile.ts
- src/mocks/project.ts (추가 관리 초기 상태와 이름이 있는 기존 팀원)
- src/services/projectService.ts, profileService.ts
- src/app/providers/ProjectProvider.tsx, ProfileProvider.tsx
- src/app/router/AppRouter.tsx, src/main.tsx
- src/features/project/CreateProjectForm.tsx, ApplicationModal.tsx, RecruitmentCard.tsx
- src/features/user/ActivityTab.tsx, src/features/review/ProfileReviews.tsx
- src/pages/ProjectDetailPage.tsx, src/components/layout/AppLayout.tsx
- README.md, docs/frontend-pages.md, docs/decisions-pending.md, docs/demo-scenarios.md, docs/changes-project-management.md

## 상태와 정책

- 상태: 모집 중 → 모집 완료 → 진행 중 → 종료. 모집 완료에서 재모집 가능, 모집 단계 조기 종료도 가능. 종료는 되돌릴 수 없습니다.
- 승인 인원은 원본 기존 인원에 더해 계산해 수정/새로고침 시 이중 합산을 방지합니다.
- 지원 취소는 종료 전까지 허용하며 마감 임박 확인/실제 온도 미차감 정책을 유지합니다.
- 종료 후 리뷰는 실제 종료 시각부터 30일. 활동에는 계획 기간과 별도 실제 종료일을 표시합니다.
- 채팅은 멤버 ID 상태만 준비했습니다. 실제 방/메시지/WebSocket은 구현하지 않았습니다.
- 알림 화면은 프로젝트 관리 이벤트만 다룹니다. 읽음 처리/SNS 알림/실제 관리자 대시보드는 다음 단계 범위입니다.
- 기한 경과 알림은 앱 실행 중 확인하는 Mock 동작이며 서버 스케줄러·자동 삭제는 없습니다.
- 삭제 제한과 상태 전환/재지원 정책은 임시 프로토타입 규칙으로 미확정 문서에 기록했습니다.

## 검증

- 최종 npm run build: TypeScript + Vite 빌드 성공.
- 최종 npm test: 12개 파일, 161개 통과 (기존 136개 + 관리 서비스 15개 + 렌더링 10개).
- 연결 시나리오 테스트: 일반 지원 → 프리미엄 승인 → 팀원/채팅 준비/승인 알림 → 모집 완료/진행/종료 → 일반 계정 리뷰 권한 및 실제 Mock 리뷰 등록 → 전체 저장 복원.
- 추가 검증: 비주최자/관리자 권한 제한, 중복 승인·정원, 거절, 승인 후 취소, 수정 알림·인원 이중 계산 방지, 직군/정원 축소 제한, 대기 지원자 처리 제한, 기간 연장, 관리자 알림 중복, 종료 후 변경·취소 방지, 취소한 멤버 리뷰 제한, 삭제 알림/목록 제외, 기존 v1 이관.
- 렌더링: 주최자 지원 목록/심사, 팀원/채팅 준비, 계정별 승인 알림/상세, 운영 탭, 종료 리뷰 링크/프로필 선택, 종료 비활성화, 취소 반영, 관리자 기한 알림.
- localhost:5173의 관리 페이지/프로젝트 알림 및 관리 모듈 HTTP 200.
- 실제 브라우저 클릭·배치·계정 전환 통합 검증은 도구가 없어 미수행. 서비스 시나리오와 정적 렌더링 테스트 및 HTTP 응답 확인으로 구분합니다.
- 의존성/백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 수정한 실패

정원 초과 승인 테스트가 이미 정원이 찬 뒤 두 번째 지원서를 제출해 지원 단계에서 차단되었습니다. 두 지원서를 대기 상태로 먼저 만든 뒤 첫 승인과 초과 승인을 검증하도록 조건을 정정했습니다. 이후 전체 테스트 통과.

## 미구현 / 범위 외

실제 API/DB/인증/AI/결제, 실제 채팅, SNS 알림, 관리자 대시보드, 팀원 강제 추방은 추가하지 않았습니다. 현재 요청한 주최자 관리 단계만 진행했습니다.

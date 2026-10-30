# 메신저·알림 구현 보고서

## 구현

- 1:1 대화 목록/검색·사용자별 대화·메시지·읽음·협업 제안·사용자 신고. 검색의 협업 제안 및 프로젝트 주최자 연락을 실제 Mock 대화에 연결했습니다.
- 모집 시작 시 프로젝트 채팅방 생성, 승인/취소 시 초대/퇴장, 멤버 목록, 메시지. 종료 시 기본 읽기 전용 보관 또는 주최자의 명시적 삭제 선택을 제공합니다.
- 팔로우·좋아요·댓글·퍼가기·프로젝트 지원/승인/거절/수정/종료·리뷰 요청·팔로잉 활동 알림을 통합했습니다. 현재 계정의 개별 읽음/안 읽음·전체 읽음 및 필터, 헤더 개수를 연결했습니다.
- 메시지·읽음·신고는 `devmozip:messaging:v1`, 알림 원본은 기존 프로필/프로젝트 저장소에 유지합니다. 기존 저장 데이터를 새 필드와 함께 읽습니다.

## 변경 파일

경로는 `frontend/src/` 기준입니다.

- 신규 타입/데이터/서비스: `types/messaging.ts`, `mocks/messaging.ts`, `services/messagingService.ts`, `services/activityNoticeService.ts`.
- 신규 상태/UI: `app/providers/MessagingProvider.tsx`, `features/message/ChatWindow.tsx`, `features/message/UserReportDialog.tsx`, `pages/MessagesPage.tsx`, `pages/ProjectChatPage.tsx`, `pages/NotificationsPage.tsx`, `styles/messaging.css`.
- 연결: `main.tsx`, `app/router/AppRouter.tsx`, `components/layout/AppLayout.tsx`, `pages/ProjectNotificationsPage.tsx`, `pages/ProjectDetailPage.tsx`, `pages/ProjectManagePage.tsx`, `features/search/DeveloperResults.tsx`.
- 기존 상태 확장: `types/profile.ts`, `mocks/profile.ts`, `services/profileService.ts`, `services/socialService.ts`, `types/project.ts`, `mocks/project.ts`, `services/projectService.ts`, `services/projectManagementService.ts`.
- 검증: `services/messagingService.test.ts`, `pages/MessagesPage.test.tsx` 신규. 기존 페이지 렌더링 테스트에는 MessagingProvider를 연결하고 팀원 표시 기대값을 갱신했습니다.
- 문서: `README.md`, `docs/frontend-pages.md`, `docs/demo-scenarios.md`, `docs/decisions-pending.md`, 이 보고서.

## 실행 및 검증 결과

- `npm run build`: TypeScript 및 Vite 빌드 성공. 메신저/프로젝트 채팅 지연 로딩으로 주 번들은 492.76 kB이며 청크 크기 경고가 없습니다.
- `npm test`: 19개 파일, 244개 테스트 통과. 이번 단계 서비스 16개와 화면 렌더링 10개 테스트를 추가했습니다. 메시지·권한·수신자 읽음·알림 수·계정 분리·초대/퇴장·종료 보관/삭제·저장 복원을 검증합니다.
- 실행 중 Vite 서버의 `/messages`, `/chats/pocket`, `/notifications`: HTTP 200 확인. SPA 응답 확인이며 실제 브라우저 클릭 검증을 의미하지 않습니다.
- 백엔드 및 의존성은 변경하지 않았으며 이번 단계에서는 백엔드 빌드를 다시 수행하지 않았습니다.
- 최종 검사에서 실패한 항목은 없습니다. 실제 브라우저의 시각/클릭 검증은 수행하지 않았습니다.

## 범위 및 남은 사항

서버·WebSocket·푸시·외부 API는 구현하지 않았습니다. 같은 브라우저에서 데모 계정을 전환하는 방식이며 다른 컴퓨터의 상태는 동기화되지 않습니다. 사용자 신고는 Mock 기록만 저장합니다. 종료 시 채팅 삭제는 되돌리기 UI가 없으며 전체 데모 초기화로 복원할 수 있습니다. 임시 정책은 `decisions-pending.md`, 시연 순서는 `demo-scenarios.md`를 참고하세요. 다음 단계는 시작하지 않았습니다.

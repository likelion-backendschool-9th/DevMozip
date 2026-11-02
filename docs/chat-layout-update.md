# 공통 메신저 레이아웃

- 1:1 및 프로젝트 채팅은 공통 MessengerLayout과 ConversationSidebar를 사용한다.
- 왼쪽에서 1:1 메신저 / 프로젝트 채팅을 전환하고, 해당 종류의 방만 검색·조회한다. 각 채팅 화면을 열면 그 종류의 목록이 기본 선택된다.
- 프로젝트 목록에는 모집 중 / 모집 완료 / 프로젝트 진행 중 / 프로젝트 종료 상태와 시작 전·진행 중·종료 구분, 채팅방 인원, 안 읽은 메시지 수를 표시한다. 기존 데이터에서 상태가 없는 경우 기존 프로젝트 관리 규칙과 동일하게 모집 중으로 표시한다.
- 프로젝트 채팅 상단의 멤버 버튼 → 멤버 모달 → 대상자의 강퇴 투표 요청 → 투표 확인 모달 순서로 진행한다. 멤버 목록은 기본적으로 숨긴다.
- 강퇴 투표는 채팅 대화 영역 안에서 유지한다. 권한, 중복 투표 제한, 강퇴 알림 등 상태 처리 로직은 변경하지 않았다.
- 모바일에서는 목록을 채팅 위로 배치하며 목록 높이를 제한해 스크롤할 수 있다.

주요 변경 파일: components/messaging/ConversationSidebar.tsx 및 CSS Module, MessengerLayout.tsx 및 CSS Module, messages/_components/MessagesScreen.tsx, chats/[projectId]/_components/ProjectChatScreen.tsx, MessagesPage.test.tsx, verify-chat-kick.mjs, verify-chat-layout.mjs. 기존 MessagesScreen.module.css는 공통 MessengerLayout.module.css로 이동했다.

## 검증

- npm test: 27개 파일, 376개 테스트 통과.
- npm run build: TypeScript 검사 포함 성공.
- 실제 Chromium: 목록 종류 전환, 프로젝트 상태, 검색과 빈 결과, 프로젝트 ↔ 1:1 이동, 멤버 열기/닫기와 투표 진입 확인.
- 1920 / 1440 / 1280 / 1024 / 768 / 390px 가로 넘침 없음 확인.
- 기존 강퇴/권한 브라우저 시나리오 12개 통과(멤버 모달 진입 절차 반영).
- 결과 및 캡처: artifacts/chat-layout/layout-report.json, kick-report.json, 1440.png, 390.png.

백엔드 및 실제 API 변경 없음. 커밋·푸시는 별도 요청 시 수행한다.

## 추가 조정: 기본 투표 영역 숨김 및 채팅 확대

- KickVotePanel은 해당 방의 투표 기록이 없으면 렌더링하지 않는다. 투표가 생성되면 채팅 내부에 나타나고, 완료된 투표도 기록으로 유지한다.
- 공통 ChatWindow의 대화 높이를 PC 기준 기존 45dvh(최대 32rem)에서 65dvh(최소 34rem, 최대 54rem)로 확대했다. 작은 화면은 최소 28rem, 최대 44rem이다. 1:1과 프로젝트 채팅에 함께 적용한다.
- 변경 파일: KickVotePanel.tsx, ChatWindow.module.css, verify-chat-layout.mjs.
- 빌드(TypeScript 검사 포함), 376개 테스트 통과. Chromium에서 빈 투표 영역이 없는지, PC 대화 높이, 6개 해상도 가로 넘침 및 투표 생성·진행·강퇴 시나리오 12개 통과를 확인했다.

## 추가 조정: 이동 링크 정리 및 멤버별 신고

- 메신저 화면의 개발자 찾기·개발자 탐색 링크와 프로젝트 채팅의 프로젝트 상세 링크를 제거했다. 채팅 헤더의 프로젝트 제목도 상세 페이지로 연결하지 않는다. 접근 제한 화면에서는 메신저로 돌아갈 수 있다.
- 프로젝트 채팅 멤버 목록에서 본인을 제외한 사용자별 신고 버튼을 제공한다. 멤버 목록을 닫고 신고 확인창을 열며, 대상 이름과 사유를 표시한다.
- 기존 UserReportDialog를 components/messaging으로 이동하여 1:1과 프로젝트 채팅에서 함께 사용한다. Mock 신고 저장과 계정당 대상 사용자별 중복 신고 제한은 기존 로직을 유지한다.
- 변경 파일: MessagesScreen.tsx, ProjectChatScreen.tsx, 공통 UserReportDialog.tsx, scripts/verify-chat-member-report.mjs.
- 검증: 376개 테스트 통과. 실제 Chromium에서 main 영역의 검색/프로젝트 상세 링크 제거, 멤버별 신고, 대상 표시, 본인 신고 버튼 제외, 새로고침 후 중복 신고 차단 확인. pageerror 0건. 최종 npm run build(TypeScript 검사 포함) 성공.

## PR 브랜치 검증

프로필 HTML 편집 PR #5를 제외한 브랜치에서 npm test 26개 파일·363개 테스트와 npm run build가 통과했다. 위의 376개 결과는 프로필 HTML 편집 테스트 13개도 포함된 작업 디렉터리의 결과다. 브라우저 검증도 해당 작업 디렉터리에서 수행했다.

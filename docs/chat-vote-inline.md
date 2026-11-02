# 채팅방 내부 강퇴 투표

강퇴 투표 현황을 페이지 하단에서 채팅방의 스크롤 가능한 메시지 목록 안으로 옮겼다. 투표 카드 아래에는 기존 메시지 입력창이 유지된다. 새 투표가 생성되면 메시지 목록 하단으로 스크롤한다. 과반수, 중복 투표, 대상자 투표 제한, 강퇴 및 알림 상태 로직은 변경하지 않았다.

변경 파일:
- frontend/components/messaging/ChatWindow.tsx: 대화 내부 콘텐츠 슬롯 및 새 투표 스크롤 반영
- frontend/app/chats/[projectId]/_components/ProjectChatScreen.tsx: 투표를 대화 내부에 연결
- frontend/app/chats/[projectId]/_components/KickVotePanel.module.css: 채팅 폭에 맞는 카드 간격
- scripts/verify-chat-kick.mjs: 투표가 메시지 목록 안에 있는지 검증

검증:
- npm test: 27개 파일, 376개 테스트 통과
- npm run build: TypeScript 검사 포함 성공
- 실제 Chromium: 기존 강퇴/권한 시나리오 12개 통과. 투표 생성, 정확히 절반 미달, 중복 차단, 만료, 과반수 강퇴, 대상자 접근 제한과 알림 확인. 투표가 message-history 내부에 있으며 페이지 하단에 중복 표시되지 않는지 확인.
- 결과: artifacts/chat-vote-inline/report.json

이번 변경에 대한 모바일 해상도별 브라우저 검증은 별도로 수행하지 않았다. 백엔드와 실제 API 변경 없음.

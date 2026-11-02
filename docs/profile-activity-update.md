# 활동 탭 링크 및 배치

- 프로젝트 제목을 상세 링크로 제공한다. 제목 옆 화살표는 제거했다.
- 제목에 hover 밑줄 및 키보드 포커스 표시를 제공하고 글자 크기를 높였다.
- 본인에게 표시되는 내 프로젝트 관리 버튼을 활동 목록 아래 오른쪽에 배치했다.

변경 파일: frontend/app/users/[userId]/_components/ActivityTab.tsx, ActivityTab.module.css

검증: 테스트 36개 파일·418개 통과, npm run build 성공(TypeScript 검사 포함). Chromium에서 프로젝트 제목 상세 이동, 관리 버튼 하단 배치 및 이동, 타인 관리 버튼 미표시를 확인했다. 1440/768/390px에서 가로 넘침과 페이지 실행 오류가 없었다. 실제 모바일 기기는 검증하지 않았다. 커밋·푸시는 하지 않았다.

[결과](artifacts/profile-activity/report.json) · [PC](artifacts/profile-activity/1440.png) · [모바일](artifacts/profile-activity/390.png)

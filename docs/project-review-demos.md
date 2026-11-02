# 지원자·리뷰 목업 보강

## 시연 방법

1. 프리미엄 회원으로 전환하고 `/projects/pocket/manage` 또는 `/projects/devgarden/manage`의 지원자 탭에서 **지원자 데모 추가**를 누른다.
2. Pocket은 백엔드 검토 대기 2건, DevGarden은 프론트엔드 대기 2건과 백엔드 거절 1건을 제공한다. LearnLoop 주최자 계정에는 취소 예시 1건을 제공한다. 기존 지원서·팀원은 덮어쓰지 않는다.
3. 승인·거절은 기존 기능을 이용한다. 승인 결과는 정원·팀원·채팅·알림에 연결된다.
4. 일반 또는 프리미엄 데모 계정의 본인 프로필 리뷰 탭에서 **리뷰 데모 추가**를 누른다. DevLog·StudyBridge 협업 이력에 맞는 9개 예시를 추가한다. 기존 리뷰 1건은 유지한다.
5. 김하늘은 받은 리뷰 4개, 평균 4.5점, 신뢰 온도 39.5°C를 표시한다. 별점만 있는 리뷰 및 여러 키워드 분포를 포함한다. 다른 관련 프로필에도 받은 리뷰가 반영된다.

두 버튼 모두 여러 번 눌러도 같은 프로젝트·사용자의 지원서 또는 같은 협업 평가를 중복 추가하지 않는다. 사용자가 처리한 결과를 되돌리지 않는다. 모든 상태는 기존 localStorage 저장 방식을 이용하며 전체 데모 초기화 후 버튼으로 다시 추가할 수 있다. 리뷰 작성 폼은 프로필에 추가하지 않았으며 실제 리뷰 작성은 기존 종료 프로젝트 요청 링크만 이용한다.

## 주요 변경 파일

- frontend/mocks/projectApplicants.ts, reviewDemos.ts: 목업 원본
- frontend/lib/projects/projectApplicantDemo.ts 및 테스트: 지원자 추가와 상태 연동
- frontend/lib/projects/projectService.ts, frontend/types/project.ts: 액션 연결
- frontend/lib/profiles/profileService.ts, frontend/types/profile.ts: 리뷰 데모 액션
- frontend/app/projects/[projectId]/manage/_components/ProjectManageScreen.tsx
- frontend/app/users/[userId]/_components/ProfileReviews.tsx
- scripts/verify-project-review-demos.mjs

## 검증

- npm test --prefix frontend: 37개 파일·422개 테스트 통과.
- npm run build --prefix frontend: 빌드 및 TypeScript 검사 성공. 중단된 지원자 작업에 있던 신규 액션 타입 오류를 수정했다.
- Chromium: 지원자 추가·중복 방지·승인·거절·새로고침 복원, 리뷰 추가·중복 방지·복원, 김하늘 리뷰 4개·온도·차트 확인.
- 지원자 1440/390px 및 리뷰 390px 가로 넘침 없음, 페이지 실행 오류 0건.
- 기존 액션 테스트에서 승인 후 팀원·채팅·알림 반영 확인. 실제 모바일 기기 및 전체 업무 흐름은 재검증하지 않았다.
- 백엔드·실제 API 변경 및 커밋·푸시는 하지 않았다.

[브라우저 결과](artifacts/project-review-demos/report.json) · [지원자](artifacts/project-review-demos/applicants-1440.png) · [모바일 리뷰](artifacts/project-review-demos/reviews-390.png)

## 지원자 포트폴리오 버튼 정리

지원자 카드의 별도 `지원자 포트폴리오 상세` 버튼과 전용 모달 컴포넌트·CSS를 제거했다. 작성자 프로필 및 포트폴리오 탭 이동 링크를 유지한다. 변경 파일은 ProjectManageScreen.tsx와 제거된 ApplicantPortfolioButton.tsx, ApplicantPortfolioButton.module.css이다.

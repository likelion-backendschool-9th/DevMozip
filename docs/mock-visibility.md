## 2026-11-11 표시 정책

Mock 전환 버튼과 공통 상단·홈·푸터의 샘플 데이터 표시 안내를 숨기며, 기본값과 서버 렌더링 스냅샷은 OFF다. 표시 설정 키는 `devmozip:mock-visibility:v2`, 값은 `{version:2, enabled:boolean}`이다. 과거 v1의 ON 선택은 적용하지 않으며 기존 시연 데이터와 실제 API 연결은 유지한다. Mock 제거 작업은 취소했으며 아래 내용은 이전 전환 기능의 설계 기록이다.

# Mock 데이터 켜기·끄기

2026-11-05. 사용자 정정에 따라 **페이지는 그대로 유지하고 샘플 데이터만 제거**하는 방식으로 수정했다. 기존의 API 연결 안내 화면으로 페이지를 대체하는 동작은 제거했다. 실제 API 전환이나 백엔드 변경은 없다.

## 사용 방법

상단 ‘Mock 데이터 끄기 / 켜기’를 누른다. 기본값 ON. 선택은 `devmozip:mock-visibility:v1`의 `{version:1,enabled:boolean}`로 저장한다.

- ON: 원래 데모 데이터와 기존 수정 상태를 사용한다.
- OFF: 동일한 메인·목록·검색·필터·탭·버튼·작성 폼을 유지하고 샘플 개발자·프로젝트·게시글·댓글·리뷰·포트폴리오·채팅·결제 예시를 비운다. 기본 선택지·정책·UI 설명·입력 placeholder는 유지한다.
- OFF에서 직접 작성한 데이터는 `devmozip:empty-mode:v1:<원래 키>`에 별도로 저장한다. ON 데이터와 섞거나 덮어쓰지 않으며 OFF 복귀 시 다시 읽는다.
- 인증 세션·쿠키·기존 실제 API 프로필 조회/수정은 유지한다. 미로그인/권한 제한도 유지한다. 기본 데모 회원을 탐색 목록에 추가하지 않고, OFF에서 프론트 데이터 작성 시 필요한 본인 레코드는 샘플 소개·기술 등을 채우지 않은 최소 정보로 준비한다.
- 데이터 모드 변경 시 해당 도메인 캐시와 화면만 다시 구성한다. 서버 인증 세션 캐시는 초기화하지 않는다. 새로고침 및 다른 탭의 모드 변경을 지원한다.
- localStorage를 사용할 수 없으면 현재 탭에서만 전환하고 안내한다. 저장 데이터 손상 시 OFF는 빈 상태로 복구하며 샘플을 재주입하지 않는다.

프로젝트·메시지 동기화도 빈 작업 공간을 대상으로 동일하게 동작한다. 직접 만든 프로젝트·글 등은 이 공간 안에서 기존 프론트 상태 로직으로 처리한다. API를 새로 연결하거나 프론트 시연 기능을 실제 서버 기능으로 바꾸는 것은 아니다. 이미 존재하는 샘플 상세 URL은 OFF 데이터에 해당 항목이 없으면 기존 ‘찾을 수 없음’ 화면을 보여준다. 빈 목록은 실제 DB가 0건이라는 의미가 아니다.

## 주요 변경 파일

- `frontend/lib/demo/mockVisibility.ts`: 표시 설정·공유 모드·샘플 선택·모드별 저장 키.
- `frontend/lib/state/mockVisibilityStore.ts`, `frontend/lib/localStore.ts`: 모드 변경과 도메인 캐시 재로딩. 인증 세션 제외.
- `frontend/lib/{profiles,projects,matching,messaging,administration,account}`의 상태 저장 서비스: 빈 초기 상태와 저장소 분리.
- `frontend/lib/projects/projectSelectors.ts`: OFF에서 기본 프로젝트·팀원 샘플 제외.
- `frontend/lib/state/{authStore,profileStore,projectStore,homeStore,messagingStore}.ts`: 모드별 데이터 재구성.
- `frontend/app/_components/MockVisibility.tsx`와 CSS Module: 상단 토글·같은 화면 재마운트·안내 문구.
- `frontend/app/_components/{AppLayout,ProjectPreview,FeedPreview,ClientReady}.tsx`, `frontend/app/{layout,page}.tsx`: 기존 화면 유지, 빈 상태 및 ON/OFF 문구.
- `frontend/app/admin/ai/page.tsx`, `frontend/app/(user)/settings/privacy/page.tsx`: 오류 로그·결제 수단 샘플 제외.
- `frontend/lib/demo/{mockVisibility,emptyWorkspace}.test.ts`, hydration 테스트: 상태 분리·원본 보존·복원·저장 오류·SSR 검증.

## 검증

- frontend `npm test`: 50개 파일·533개 통과.
- frontend `npm run build`: 성공(TypeScript 포함).
- Chromium 프로덕션 화면에서 메인 프로젝트·SNS 샘플 제거와 폼 유지, 프로젝트 검색/필터/작성 버튼, SNS 검색/카테고리/작성 버튼, 개발자 탐색 필터, AI 대화 입력/새 대화 유지 확인.
- ON→OFF 전환 후 기존 도메인 localStorage 내용 보존, 새로고침 후 OFF 유지, ON 복귀 시 원래 샘플 제목 복원 확인. 단위 테스트에서는 OFF 직접 작성 데이터의 별도 저장·복원과 인증 세션 캐시 유지도 확인.
- 1440px·390px 메인 가로 넘침 없음. 브라우저 pageerror 없음. 결과: `/tmp/empty-mode-report.json`, 화면: `/tmp/empty-mode-screens`.
- 브라우저 시나리오는 비로그인/통제된 세션 응답을 사용했다. 실제 로그인 후 모든 작성 기능의 전체 브라우저 회귀는 수행하지 않았다. 실제 팀 DB·API 추가 연동은 이번 검증 범위가 아니다.
- 백엔드 변경 없음. 커밋·푸시·PR 없음.

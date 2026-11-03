# 통합 검색 구현 보고

## 기능

- 헤더/검색 페이지에 공통 SearchBar를 적용했습니다. 왼쪽 개발자/프로젝트 선택과 검색어를 같은 URL 상태로 연결합니다.
- 개발자 텍스트 검색: 닉네임, 아이디, 직군, 기술, 경력, 관심 분야. 상세 필터: 직군, 경력, 다중 기술, 관심 분야, 협업 가능/쉬는 중. 전체/로그인 사용자 팔로잉 범위.
- 네임카드: 프로필 이미지, 닉네임/직군/경력/소개/기술/관심 분야/협업 상태/실제 공통 온도, 프로필 이동, 팔로우·언팔로우, 협업 제안 모달. 본인 카드는 내 프로필 링크를 제공합니다.
- 프로필 수정과 계정별 팔로우는 기존 ProfileProvider를 사용합니다. 언팔로우하면 팔로잉 검색에서 즉시 제외됩니다. 새로고침 후에도 기존 공통 저장소를 통해 유지됩니다.
- 프로젝트명/기술/모집 직군/분야 검색과 다중 기술·직군·분야·상태 필터. 상태가 생략된 기존 Mock 공고는 모집 중으로 표시합니다.
- 대소문자/전각 문자를 정규화하고 공백 구분 검색어를 모두 포함하는 결과를 표시합니다. 상세 필터와 선택 기술도 AND 조건입니다.
- 검색 조건은 URL에 유지합니다. 대상 전환은 검색어만 유지하고 기존 필터를 비웁니다. 같은 대상에서 재검색하면 상세 필터를 유지합니다.
- 선택 필터 제거, 상세 필터 초기화, 빈 결과/로그인 안내, 검색 조건 전체 초기화.
- 협업 제안은 프로젝트명과 10자 이상 내용 검증 후 데모 확인 토스트를 표시합니다. 실제 쪽지 전송/저장으로 표현하지 않으며 메시지 기능은 구현하지 않았습니다.

## 변경 파일

아래 src 경로는 frontend 기준입니다.

신규:
- src/types/search.ts
- src/mocks/search.ts
- src/services/searchService.ts, searchService.test.ts
- src/components/common/SearchBar.tsx
- src/features/search/SearchFilters.tsx, DeveloperResults.tsx, ProjectResults.tsx
- src/pages/SearchPage.tsx, SearchPage.test.tsx
- src/styles/search.css

수정:
- src/components/layout/AppLayout.tsx: 기존 검색 폼을 공통 컴포넌트로 교체
- src/app/router/AppRouter.tsx: 실제 검색 화면 라우팅
- src/main.tsx: 검색 스타일 연결
- README.md, docs/frontend-pages.md, docs/demo-scenarios.md, docs/changes-search.md

## 검증

- npm run build 성공: TypeScript 검사 및 Vite 프로덕션 빌드.
- npm test: 8개 파일, 106개 통과 (기존 84개 + 서비스 16개 + 화면 정적 렌더링 6개).
- 서비스 테스트: URL 정규화/대상 전환/조건 보존, 닉네임/대소문자/전각 검색, 조건 조합, 기술 AND, 협업 여부, 팔로잉·로그인 조건, 프로필 수정·팔로우 반영, 프로젝트 상태, 빈 결과, 원본 비변경.
- 렌더링 테스트: 두 검색창 URL 표시 일치, 네임카드 링크/팔로우/제안, 활성 필터, 비로그인 팔로잉 안내, 본인 카드, 빈 결과/초기화.
- 실행 중인 localhost:5173에서 개발자/프로젝트 검색 경로 및 SearchPage 모듈 HTTP 200 확인.
- 실제 브라우저의 클릭·배치·뒤로가기/새로고침 통합 검증은 도구가 없어 미수행. 정적 렌더링/서비스 테스트/HTTP 응답 확인과 구분합니다.
- 빌드/테스트 실패 없음. 의존성 및 백엔드 변경 없음. 백엔드 빌드는 이번에 재실행하지 않았습니다.

## 범위 외

공고 상세/쪽지 전송/실제 검색 API/DB/AI/인증 서버는 구현하지 않았습니다. 기존 로고·레이아웃·로그인·프로필·메인·LAN 공유 설정을 보존했습니다. 다음 단계는 진행하지 않았습니다.

# 탐색 자동완성·복수 선택

## 변경

- 개발자 관심 분야와 기술 스택, 프로젝트 분야와 기술 스택을 검색형 복수 선택으로 통일했다.
- 입력한 문자열을 포함하는 등록 항목을 추천하며 기본 옵션과 현재 프로필·프로젝트의 항목을 함께 제공한다. 자유 입력으로 검색에 없는 항목을 새로 만드는 UI는 제공하지 않는다.
- 클릭 또는 Enter로 선택, 태그의 X로 해제, 방향키 이동과 Escape 닫기, 빈 추천 안내를 제공한다. 자동완성 목록은 아래로 펼쳐 필터 간 겹침을 피한다.
- 분야는 OR(선택 중 하나 이상), 기술은 AND(선택 전부 포함)로 결합한다. 나머지 조건은 기존대로 함께 적용한다.
- 반복 interest/field/stack URL 매개변수를 지원해 새로고침·공유·브라우저 이력 복원을 유지한다. 기존 단일 분야 링크도 호환한다. 등록된 사용자 정의 항목을 검색할 수 있도록 값 길이·개수 검증으로 변경했다.
- 검색창 왼쪽 대상 선택에 7.5rem 너비를 확보해 프로젝트 텍스트와 선택 화살표가 겹치지 않도록 했다.

## 변경 파일

- frontend/app/search/_components/SearchMultiSelect.tsx 및 CSS Module
- frontend/app/search/_components/SearchFilters.tsx
- frontend/app/search/_components/SearchScreen.tsx
- frontend/app/search/_components/SearchBar.module.css
- frontend/types/search.ts
- frontend/lib/search/searchService.ts 및 테스트
- scripts/verify-search-autocomplete.mjs

## 검증

- npm test --prefix frontend: 37개 파일·424개 테스트 통과.
- npm run build --prefix frontend: Next.js 빌드·TypeScript 검사 성공.
- Chromium: 양쪽 대상에서 분야·기술 자동완성 복수 선택, URL·새로고침 복원, 개별 필터 해제, Enter·Escape, 추천 없음 안내 확인.
- 1920/1440/1280/1024/768/390px에서 문서 가로 넘침 없음. 대상 선택 너비 120px 이상 확인. PC·모바일 캡처 확인. 페이지 실행 오류 0건.
- 실제 모바일 기기·스크린리더 및 전체 서비스 업무 흐름은 재검증하지 않았다. 커밋·푸시는 하지 않았다.

[결과](artifacts/search-autocomplete/report.json) · [PC](artifacts/search-autocomplete/1440.png) · [모바일](artifacts/search-autocomplete/390.png)

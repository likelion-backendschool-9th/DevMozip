# 프로필 소개 레이아웃 및 HTML 편집

- 소개 / 오른쪽 정보 카드 비율은 넓은 화면에서 2.2:1이다. 소개의 기본 최소 높이는 34rem이며 긴 내용에 따라 늘어난다.
- 오른쪽에는 기술 스택 → 관심 분야 → 외부 링크를 세로로 표시한다. 콘텐츠 공간이 좁아지면 소개 아래로 이동한다.
- 소개 편집은 일반 텍스트 / HTML, 기본 서식 삽입 도구, 미리보기를 제공한다. 최대 20,000자이며 기존 일반 텍스트 데이터는 그대로 표시한다.
- Profile.introductionFormat은 선택 필드(`text` / `html`)다. 기존 저장 데이터에는 이 필드가 없어도 된다. 기존 localStorage 키와 버전을 유지한다.
- HTML은 저장 및 렌더링 시 sanitize-html의 허용 목록을 적용한다. 제목, 목록, 표, 코드, 이미지, 링크 및 제한된 인라인 서식을 지원한다. 스크립트, 이벤트 속성, iframe, 외부 스타일, 위치 지정 스타일은 제거한다. 이미지와 링크는 http/https 주소만 허용한다.
- 백엔드 및 실제 API 변경은 없다.

## 검증 결과

- `npm test --prefix frontend`: 27개 파일, 376개 테스트 통과.
- `npm run typecheck --prefix frontend`: 통과.
- `npm run build --prefix frontend`: 통과.
- 실제 Chromium: 1920 / 1440 / 1280 / 1024 / 768 / 390px에서 정보 순서, 가로 넘침, 배치 확인.
- 넓은 편집 모달, HTML 미리보기, 저장·새로고침 복원, 위험 코드 제거, 기존 5개 탭 전환 확인. 브라우저 pageerror 0건.
- 결과: [검증 JSON](artifacts/profile-introduction/report.json), [PC 화면](artifacts/profile-introduction/desktop.png), [모바일 화면](artifacts/profile-introduction/mobile.png).
- 재실행: Playwright 설치 환경에서 개발 서버 실행 후 `DEVMOZIP_PLAYWRIGHT_MODULE=<Playwright 모듈 경로> node scripts/verify-profile-introduction.mjs`. 필요한 경우 `DEVMOZIP_BASE_URL` 지정.

이번 브라우저 검증은 프로필 소개 변경 범위에 한정한다. 다른 서비스 전체 흐름은 이번 작업에서 재검증하지 않았다.

# 1단계 검증 결과 (초기 기록)

현재 전체 프로토타입의 최종 결과는 [최종 검증 보고서](final-verification.md)를 확인하세요. 아래 수치는 초기 단계 당시의 기록입니다.

검증일: 2026-10-30 (Asia/Seoul).

| 항목 | 결과 |
|---|---|
| npm install | 성공, package-lock.json 생성 |
| npm run build | 성공 (TypeScript 검사 + Vite 7.3.7 프로덕션 빌드) |
| npm test | 성공, Vitest 5.0.5 / 2개 파일 / 29개 테스트 |
| npm audit | 테스트 도구 업데이트 후 취약점 0건 |
| 경로 구성 | 19개 경로 매칭/중복/정적 new 경로 우선순위 및 잘못된 경로 자동 테스트 통과 |
| localStorage 설정 파싱 | 정상/손상/버전 불일치/타입 불일치 복원 테스트 통과 |
| Vite 실행 | 127.0.0.1:5173 기동 성공 |
| HTTP 응답 | 홈과 /projects/demo-project HTML 응답 200 |
| Gradle build | 성공, 7개 태스크 실행 |
| 백엔드 테스트 | contextLoads 1개 성공, 실패/오류 0 |
| 백엔드 실행 | Java 21, Spring Boot 4.1.2, Tomcat 8080 기동 성공 |

## 해결한 실패와 경고

- 기본 샌드박스에서 외부 DNS 접근 및 로컬 포트 리슨이 제한되어 실패. 권한을 적용한 설치/기동/HTTP 검증은 성공했습니다.
- Initializr 버전 표기 `4.1.2.RELEASE` 생성 요청은 HTTP 500. 메타데이터 표시 버전인 `4.1.2`로 정정해 정상 다운로드했습니다.
- 최초 npm 설치에서 Vitest 3 계열 의존성 취약점 3건. Vitest 5.0.5로 업데이트한 뒤 취약점 0건, 빌드/테스트 재통과.
- npm에서 esbuild 설치 스크립트 승인 안내가 있었지만 실제 빌드는 정상 완료했습니다.
- Spring 기본 테스트에서 Mockito 동적 에이전트/CDS 경고가 있으나 컨텍스트 테스트와 빌드는 통과했습니다.
- `.git` 디렉터리가 있으나 현재 환경에서 유효한 Git 저장소로 인식되지 않아 `git status`를 사용할 수 없습니다. 파일 기준 변경 목록을 별도 기록했습니다. Git 초기화/커밋/푸시는 수행하지 않았습니다.

## 검증 한계

브라우저 자동화/사용자 미리보기 도구가 없어 실제 렌더링 스크린샷, 마우스/키보드 조작, 모달 포커스, 새로고침 저장 유지 및 반응형 화면은 브라우저에서 검증하지 않았습니다.
HTTP 200은 SPA HTML 응답 확인이며 React 렌더링 검증을 의미하지 않습니다.
저장소 파싱 및 경로 자동 테스트는 통과했지만 브라우저 통합 테스트를 대신하지 않습니다.
로그인, 프리미엄 전환, 검색/필터, 지원/승인, SNS 퍼가기, 프로필 탭, 리뷰 차트, 알림은 이후 단계 범위로 구현·테스트하지 않았습니다.

최초 검증용 서버는 종료했으며, 이후 사용자 요청으로 프론트엔드 개발 서버를 다시 실행했습니다. 아래 재검증 시점에 http://localhost:5173/ 응답 200을 확인했습니다. 실행 방법은 README를 참조하세요.

## 초기 개발 환경 재검증 (사용자 재요청)

- 기존 파일을 먼저 확인했으며 소스 및 사용자 제공 SVG 로고를 보존했습니다.
- 참고 GitHub API 재확인 결과 HTTP 404로 구조 분석 불가. 기본 폴더 구조를 유지합니다.
- React/TypeScript/Vite, React Router, Axios가 이미 설치되어 있어 재설치하지 않았습니다.
- app/pages/features/components/services/mocks/types/styles, .gitignore, .env.example, README, 공통 Axios 및 라우터 설정 존재 확인.
- `npm run build` 재성공, `npm test` 29개 재통과.
- `GRADLE_USER_HOME=/tmp/devmozip-gradle ./gradlew build --no-daemon` 재성공. 7개 태스크는 UP-TO-DATE이며 이번에는 백엔드 테스트를 재실행하지 않고 기존 성공 결과를 재사용했습니다.
- 실행 중인 Vite 개발 서버 `http://localhost:5173/` HTTP 200 확인.
- 백엔드 main 소스는 BackendApplication.java와 application.yml뿐입니다. 실제 서비스 기능, Controller/Service/Repository/DTO/Entity/API 및 외부 연동은 추가하지 않았습니다.
- 이번 변경 파일은 검증 기록인 `docs/validation.md`뿐입니다. 초기 환경 완료 조건을 충족해 코드 변경은 필요하지 않았습니다.
- 브라우저 시각 검증은 수행하지 않았으며 다음 단계는 진행하지 않았습니다.

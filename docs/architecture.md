# 구조 분석 및 결정

## 현재 디렉터리

초기 확인 시 `.github/workflows/notify.yml` 외 애플리케이션 파일은 없었습니다.
기존 워크플로는 수정하지 않았습니다. 기존 프론트엔드/백엔드 기능은 없었습니다.

## 참고 저장소

https://github.com/LuvZian/superOfTheKing.git

2026-10-30 확인: GitHub 웹과 `https://api.github.com/repos/LuvZian/superOfTheKing` 모두 404.
비공개 여부나 저장소 존재 여부는 단정할 수 없습니다. frontend/backend 분리, React 구조, 라우팅, API 호출, 공통 컴포넌트, CSS, Spring 패키지 및 DTO 구조는 **분석 불가**입니다.
사용자가 지정한 기본 구조를 적용했습니다. 참고 프로젝트의 카페 기능/디자인은 사용하지 않았습니다.

## 현재 구성

- React Router의 중첩 레이아웃으로 공통 헤더/푸터 유지.
- pages는 화면 조합, features/setup은 경로 디렉터리 UI, components는 레이아웃·모달·토스트 담당.
- mocks는 화면 메타데이터, services는 저장/통신 경계, types는 공통 타입.
- 현재 Context는 Prototype/Auth/Profile/Project/Messaging/Home으로 나뉩니다. 인증·프로필/SNS·프로젝트/리뷰 요청·메시지·AI 대화/사용량을 기존 서비스 검증과 함께 공유합니다. [Mock 구조](mock-data.md)를 참고하세요.
- Axios는 환경변수 기반 인스턴스만 정의하며 호출하지 않음.
- 백엔드는 초기 생성한 Java 21/Spring Boot/Gradle 기본 틀을 유지합니다. 현재 버전은 backend의 빌드/Wrapper 파일을 기준으로 합니다. 5차에서는 수정하거나 재빌드하지 않았습니다.
- Spring Web MVC 및 기본 테스트 의존성만 포함. DB·인증·외부 SDK 미포함.

## 향후 백엔드 구조 (계획만)

`com.devmozip.backend` 아래 controller, service, repository, domain, config, common 및 도메인별 dto/{request,response}를 분리합니다. 관리 기능은 admin에서 별도 관리합니다.
현재 해당 패키지 및 Java 파일은 생성하지 않습니다. 실제 비즈니스 구현은 별도 요청 이후 진행합니다.

## 5차 반응형 구조

페이지별 기존 CSS 뒤에서 `styles/responsive.css`가 공통 너비·Grid/Flex·카드·작은 화면 동작을 정리합니다. 컨테이너는 100%/max-width, 카드에는 auto-fit/minmax, 간격/폰트에는 clamp/rem을 사용합니다. 개인 Aside와 AI 기록은 각 컴포넌트의 화면 로컬 토글 상태를 사용하며 도메인 저장 스키마는 바꾸지 않습니다.

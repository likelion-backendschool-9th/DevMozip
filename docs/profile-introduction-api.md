# 상세 자기소개 조회·수정 API (#52)

## 범위

상단 한 줄 소개(users.summary)와 독립된 상세 자기소개만 구현한다. 개발 직군·기술 스택·관심 분야 enum 연동과 외부 링크는 제외한다. 기존 소개 카드·사이드바·일반 텍스트/HTML 편집기·미리보기를 재사용한다.

## 스키마

사용자가 ERD에 두 컬럼을 추가했다고 확인했다. 코드는 아래 컬럼 기준이며 임시 H2에서 검증했다. ERD는 사용자가 관리하고, 실제 PostgreSQL 컬럼 반영은 아직 확인하지 않았다.

- users.introduction: TEXT, nullable
- users.introduction_format: VARCHAR(10), nullable. text / html만 지원하며 기존 NULL은 text로 조회한다.
- 공통 Member의 기존 생성자와 Builder 호출은 유지하고 updateIntroduction 메서드로 두 필드만 수정한다.

실제 PostgreSQL에 컬럼이 없는 경우 적용할 수 있는 예시(미실행):

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS introduction TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS introduction_format VARCHAR(10);
```

## 계약

| 메서드·경로 | 설명 | 인증 |
| --- | --- | --- |
| GET /api/v1/users/{slug}/introduction | ACTIVE 회원의 공개 상세 소개 | 불필요 |
| GET /api/v1/users/me/introduction | 본인의 상세 소개 | 필요 |
| PUT /api/v1/users/me/introduction | 본인의 상세 소개 수정 | 필요 |

정적 /me/introduction GET을 공개 와일드카드보다 먼저 보호한다. 본인은 Authentication.getName() 이메일로만 조회한다. ACTIVE·WARNED 계정은 본인 조회·수정이 가능하고 SUSPENDED는 403이다. 공개 조회의 미존재·WARNED·SUSPENDED는 동일한 404다. 다른 사용자 ID나 이메일을 본문으로 지정할 수 없다.

PUT 요청:

```json
{"introduction":"안녕하세요. 함께 개발하고 싶습니다.","format":"text"}
```

응답(허용된 두 필드만 포함):

```json
{"introduction":"안녕하세요. 함께 개발하고 싶습니다.","format":"text"}
```

- 미작성 응답은 introduction: null, format: text다.
- 삭제는 introduction을 빈 문자열로 명시한다. 공백만 있거나 HTML 정리 후 빈 값이면 NULL로 저장하고 형식은 text로 정리한다.
- 누락·NULL 입력과 지원하지 않는 형식은 400이다. 누락을 삭제로 간주하지 않는다.
- 원문과 HTML 정리 후 저장 문자열 모두 최대 20,000자다. 정리 과정에서 HTML 엔티티가 늘어나 한도를 넘으면 400으로 거절한다.
- 일반 텍스트의 줄바꿈과 문자 그대로의 HTML 코드는 보존한다. 출력 시 React가 텍스트로 이스케이프한다.

## HTML 정책

[OWASP Java HTML Sanitizer 공식 문서](https://github.com/OWASP/java-html-sanitizer)를 참고해 20261011.1을 사용했다. 정규식으로 HTML을 파싱하지 않는다.

- 제목·문단·강조·목록·인용·코드·표·이미지·링크와 라이브러리가 허용하는 인라인 CSS를 지원한다.
- script·iframe·SVG·이벤트 속성·위치 고정 등 허용하지 않은 요소와 속성을 제거한다.
- 링크와 이미지 URL은 사용자 정보가 없는 절대 http/https 주소만 허용한다. 상대 경로·프로토콜 생략·javascript/data URL은 제거한다.
- 저장 전에 필터링하고, 읽을 때도 레거시 HTML을 필터링한다. 조회가 DB 값을 수정하지는 않는다.
- 프론트의 기존 sanitize-html 렌더링·미리보기 정책을 유지한다. 서버가 더 제한하는 서식은 저장 응답에 맞춰 표시한다.

## 프론트 통신과 화면

- BFF: GET /api/profiles/{slug}/introduction, GET/PUT /api/profiles/me/introduction
- 기존 backendFetch가 HttpOnly JWT 쿠키를 서버에서 전달한다. 수정 BFF는 Origin을 확인하고 introduction·format만 전달한다.
- 공개 태그와 본인 조회를 구분하며 응답은 no-store다. 실패를 미작성 값으로 바꾸지 않고 오류와 재시도를 표시한다.
- 본인임이 확인되고 소개 조회가 완료된 경우에만 수정 버튼을 표시한다.
- 기존 IntroductionEditor의 텍스트/HTML·작성/미리보기·서식 버튼·글자 수를 유지한다.
- 저장 중 중복 제출·입력·닫기를 제한하고 실패 시 입력을 유지한다. 성공 응답으로 카드 내용을 갱신한다.
- 전용 브랜치는 최신 develop 기반 feat/52-profile-introduction이다. 헤더 PR #41은 아직 미병합이므로 이번 브랜치에 포함하지 않았다.

## 검증

- 백엔드 전체 테스트 150개 중 149개 통과, Docker가 필요한 PostgreSQL 컨텍스트 테스트 1개 건너뜀. Gradle build 통과.
- 프론트 전체 테스트 518개 및 프로덕션 빌드 통과.
- 미작성·텍스트·HTML·삭제·20,000/20,001자·본인 권한·공개 상태·허용 필드·HTML/CSS 정리와 기존 API 회귀를 검증했다.
- 실제 PostgreSQL·Redis 연결은 미검증이다. 브라우저 검증은 임시 H2 회원만 사용한다.
- Safari에서 실제 로그인 후 일반 텍스트 작성·미리보기·저장·새로고침 재조회, HTML 형식 전환·서식 미리보기·저장·공개 API 조회를 확인했다. 상단 한 줄 소개와 사이드바·소개 탭 배치는 유지된다.

## 별도 회원가입 변경과의 관계

회원가입 PR #54가 develop에 병합되어 직군·기술 스택·관심 분야 목록과 경력 String 구조를 최신 기준으로 반영했다. 공통 Member의 자기소개 두 필드와 updateIntroduction을 함께 보존했다. build.gradle은 기존 validation 의존성을 한 번만 유지하고 자기소개 HTML 정리 의존성을 추가했다. 같은 부분을 양쪽 브랜치에서 수정해 생긴 Git 충돌이며 enum 자체의 오류는 아니다. 프론트의 jobCategories 목록·경력 문자열 계약과 기존 테스트도 최신 develop의 구현을 유지한다.

## 최신 develop 충돌 해결 검증

- 기준: origin/develop 7cf0b1b (회원가입 #54 및 프로젝트 문서 #55 병합 이후)
- 충돌 파일: backend/build.gradle, backend/src/main/java/com/devmozip/backend/entity/Member.java
- validation 의존성 이동과 자기소개 HTML 정리 의존성 추가를 함께 반영했다.
- Member의 jobCategories·skills·interests 목록, career String 및 회원가입 생성자를 유지하면서 introduction·introductionFormat 필드와 변경 메서드를 보존했다.
- 같은 필드 인접 영역을 각 브랜치에서 편집해 발생한 Git 충돌을 해결했으며, 자기소개 저장 형식은 기존 text/html 문자열을 유지한다.
- 프론트 테스트 520개 및 빌드 통과. 백엔드 전체 Gradle build 통과. 실제 DB 스키마 적용은 수행하지 않았다.

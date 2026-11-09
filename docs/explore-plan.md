# 개발자 탐색 백엔드·연동 계획

WBS 6.0 개발자 탐색(6.1~6.10)을 실제 백엔드에 연결하기 위한 단계별 계획이다. 통합 검색 API(`GET /api/v1/search`)로 백엔드 구조를 먼저 만들고, 그다음 `/explore` 화면이 그 API를 쓰도록 연결한다.

- 각 단계는 사용자가 그 단계를 요청할 때만 시작한다. 한 단계가 끝나도 다음 단계를 자동으로 시작하지 않는다.
- 이 문서는 계획이며 구현 승인이 아니다. 탐색 도메인 구현을 시작할 때 `AGENTS.md`의 승인 범위를 함께 갱신한다.
- API 경로와 메서드는 WBS를 따른다. WBS와 다르게 정한 항목은 1절에 적는다. 이전 API 초안(`docs/api-plan.md`의 `/search`)은 사용하지 않는다.
- 6.6 팔로우와 6.7 1:1 대화는 WBS대로 다른 기능의 API를 그대로 쓴다. 이 계획은 탐색 화면에서 그 API를 부르는 부분만 다룬다.

관련 계획과 PR (2026-11-09 기준, 모두 develop 미병합):

| 이름 | 위치 | 이 계획과의 관계 |
|---|---|---|
| 팔로우 계획 | `feat/94-follows` 브랜치의 `docs/follow-plan.md` | `follows` 엔티티(2단계), 9.1·9.2 API(3단계), 팔로우 버튼(4단계) |
| 메신저 | PR #90 (`feat/78-messages`) | 11.5 대화 시작 API, `StartChatButton` |
| 프로젝트 북마크 | PR #93 (`feat/56-project-bookmarks`, #96에도 포함) | 7.16 API, `ProjectBookmarkButton`·`projectBookmarkStore` |
| 활동/리뷰/신뢰 온도 | WBS 5.0 작업 | 6단계(고급 검색)의 선행 작업 |

## 1. 범위와 결정 사항

### WBS 대응

| 코드 | 작업 | 페이지 | API (WBS) | 이 계획의 처리 |
|---|---|---|---|---|
| 6.1 | 탐색 페이지·검색 유형 전환 | `/explore` | `GET /api/v1/search?type={developers\|projects}&q={keyword}` | 검색 API(2·3단계), 화면 연결(5단계). 유형 전환은 현재 화면 동작을 유지한다 |
| 6.2 | 개발자 상세 필터 검색 | `/explore?type=developers` | `GET /api/v1/search?type=developers&<query>` | 2단계 |
| 6.3 | 프로젝트 상세 필터 검색 | `/explore?type=projects` | `GET /api/v1/search?type=projects&<query>` | 3단계. 검색 기능 안에 프로젝트 탐색 전용 조회를 만든다(1절 결정) |
| 6.4 | 개발자 카드 목록·프로필 정보 표시 | `/explore?type=developers` | 6.2와 같다 | 2단계 응답, 5단계 카드 |
| 6.5 | 전체·팔로잉 개발자 탭 | `/explore?type=developers&tab={all\|following}` | `GET /api/v1/search?type=developers&following={true\|false}&<query>` | 4단계. 팔로우 계획의 `follows`가 있어야 한다 |
| 6.6 | 개발자 카드 팔로우·언팔로우 | `/explore?type=developers` | `POST`·`DELETE /api/v1/users/{slug}/follow` | 팔로우 계획의 9.1·9.2 API. 이 계획은 카드의 처음 상태(4단계)와 버튼 연결(5단계) |
| 6.7 | 개발자 카드 1:1 대화 제안 | `/explore?type=developers` → `/messages/chats/{id}` | `POST /api/v1/messages/chats` | 메신저 11.5 API(#90). 5단계에서 버튼 연결 |
| 6.8 | 프로젝트 탐색 목록 내 북마크 표시 | `/explore?type=projects` | 6.3과 같다 | 7.16 북마크 기능(#93)을 탐색 카드에서 쓴다(1절 결정) |
| 6.9 | 검색 결과 상세 페이지 이동 | `/users/{slug}`, `/projects/{id}` | 별도 API 불필요 | 5단계 카드 링크 |
| 6.10 | 검색 결과 수 및 조회 쿼리 처리 | `/explore` | 6.1과 같다 | 2·3단계 응답의 `totalCount`, 5단계의 화면 URL → API 쿼리 변환 |

### 결정 사항

| 날짜 | 결정 |
|---|---|
| 2026-11-09 | 백엔드 검색 API를 먼저 구현하고, 프론트엔드가 그 API를 쓰도록 연결한다. |
| 2026-11-09 | 검색 결과는 페이지 이동 방식(페이지네이션)으로 나눠 보여 준다(4절 "공통"). 목업은 결과를 한 번에 모두 보여 준다. |
| 2026-11-09 | 고급 검색(신뢰 온도 조건·정렬)은 작업 목록에 넣되 6단계로 미룬다. 1~5단계 PR에는 넣지 않는다. 그 PR이 develop에 병합되고 WBS 5.0(활동/리뷰/신뢰 온도) 작업도 develop에 병합된 뒤, develop에서 새 브랜치를 만들어 작업하고 별도 PR을 올린다. 고급 검색은 WBS 6.x에 행이 없지만 목업(`AdvancedSearch.tsx`, PRO 표시)과 `docs/frontend-pages.md`("고급 온도 조건/정렬은 프리미엄·관리자")에 있다. |
| 2026-11-09 | 6.8 북마크는 #93의 `ProjectBookmarkButton`과 프로젝트 북마크 상태(`projectBookmarkStore`)를 탐색 카드에서 그대로 쓴다. 검색 응답에는 북마크 여부를 넣지 않는다. 이 연결은 #93이 develop에 병합되고, 그 develop을 작업 브랜치에 병합한 뒤 시작한다. |
| 2026-11-09 | 6단계 전까지 고급 검색 영역은 지금 화면 그대로 둔다. |
| 2026-11-09 | 6.3 프로젝트 탐색은 검색 기능 안에 프로젝트 탐색 전용 조회(`ProjectSearchRepository`)를 새로 만든다. 7.1 목록 조회 코드(`ProjectQueryService`·`ProjectQueryRepository`)는 쓰지 않는다. `/explore`와 `/projects` 화면, `GET /api/v1/search`와 `GET /api/v1/projects`는 지금처럼 따로 둔다. 검색 조건은 현재 탐색 화면(목업)과 같게 유지한다(4절 "프로젝트 검색"). |

## 2. 데이터

새 테이블과 컬럼은 없어 ERD 변경도 없다. 이 기능에 해당하는 ERD 이미지는 받지 않았으므로 ERD와의 대조는 하지 않았다. 아래는 현재 엔티티 기준이다(확인일 2026-11-09).

| 테이블 | 엔티티 | 이 계획에서 쓰는 것 | 상태 |
|---|---|---|---|
| users | `Member` (인증 담당) | 개발자 검색 대상과 카드 정보 | develop에 있다. 고치지 않는다 |
| follows | `Follow` (팔로우 계획 2단계) | 팔로잉 탭, 카드의 팔로우 여부 | 엔티티 없음 |
| projects, project_members | `Project`, `ProjectMember` (프로젝트 담당) | 프로젝트 검색과 카드 정보, 참여 인원 | develop에 있다. 검색 Repository가 SQL로 읽기만 하고, 엔티티와 프로젝트 코드는 고치지 않는다 |
| bookmarks | `Bookmark` (#93) | 프로젝트 카드의 북마크. 화면이 #93의 버튼으로만 쓰고, 검색 API는 읽지 않는다 | 미병합. #93 문서에 따르면 팀 PostgreSQL에는 DDL을 실행했다 |

### users 컬럼과 화면

| 컬럼 | 형식 | 쓰는 곳 |
|---|---|---|
| tag | VARCHAR(100), 유일 | 카드 링크 `/users/{tag}`, 팔로우·대화 대상 `{slug}`, 검색어 |
| nickname | VARCHAR(50), 유일 | 카드 이름, 검색어, 정렬 |
| profile_image | VARCHAR(255) | 카드 이미지 |
| job_categories | JSONB 문자열 배열 | 직군 필터, 검색어, 카드 |
| career | VARCHAR(30) | 경력 필터, 검색어, 카드 |
| summary | TEXT | 카드 소개(한 줄 소개) |
| skills | JSONB 문자열 배열 | 기술 스택 필터, 검색어, 카드 |
| interests | JSONB 문자열 배열 | 관심 분야 필터, 검색어, 카드 |
| collaboration_status | VARCHAR | 협업 가능 여부 필터, 카드 배지 |
| trust_score | NUMERIC(5,2) | 카드의 신뢰 온도, 고급 검색 |
| status | VARCHAR(20) | 검색 대상 조건 |

- 직군·기술 스택·관심 분야는 가입과 프로필 수정에서 백엔드 enum(`JobCategory`·`Skill`·`Interest`)의 문구만 저장된다. 필터 값도 이 문구를 그대로 쓴다.
- 경력은 enum이 아니다. 가입·프로필 수정 화면의 선택지 문구(`mocks/auth.ts`의 `careerOptions`, 예: `3년 차`)를 30자 이내 문자열로 저장한다. 경력 필터는 문구가 같은 회원을 찾는다(목업과 같다).
- 협업 가능 상태는 5개(`AVAILABLE`·`RESTING`·`BUSY`·`OPEN_TO_OFFERS`·`UNAVAILABLE`)다. 목업의 협업 가능 여부(`협업 가능`·`협업 쉬는 중`)는 `AVAILABLE`과 그 밖의 상태(값 없음 포함)로 나눈다. 프로필 상단의 "협업 가능" 표시(`ApiProfileHeader`), 팔로우 계획의 `available=true`와 같은 기준이다.
- 신뢰 온도는 가입 때 36.50으로 저장된다(`MemberService`). 카드는 이 값을 표시한다. 5.5가 이 컬럼을 갱신하는지는 1단계에서 담당자에게 확인하고, 고급 검색(6단계)을 시작할 때 5.0 작업 결과로 다시 확인한다.

### 검색 대상 회원

- `status = ACTIVE`이고, 태그가 있고, 직군이 하나 이상인 회원만 나온다. 3.1 프로필 조회(`ProfileService.findBySlug`)가 찾을 수 있는 회원과 같으므로 카드를 누르면 항상 프로필이 열린다. 태그는 프로필 수정(3.2)에서 정하므로, 태그를 정하지 않은 회원은 나오지 않는다.
- 로그인한 본인도 나온다. 목업처럼 본인 카드에는 "내 프로필 보기"만 둔다.
- 목업의 비공개 계정 처리(`canViewProfile`)는 `users`에 공개 여부 컬럼이 없어 옮기지 않는다(팔로우 계획과 같다).

## 3. 백엔드 구조

기존 계층형 패키지(`backend/src/main/java/com/devmozip/backend`)를 따른다. 탭 들여쓰기와 생성자 주입을 쓰고, 요청·응답 DTO는 record로 만든다. 엔티티를 API 응답으로 직접 쓰지 않는다. Service는 프로젝트 기능처럼 인터페이스와 `service/impl` 구현으로 나눈다.

| 위치 | 파일 (예정) | 역할 |
|---|---|---|
| controller | SearchController | `GET /api/v1/search`. `type`에 따라 개발자·프로젝트 Service로 나눈다 |
| controller | SearchExceptionHandler | `SearchController` 전용 예외 처리(아래 "오류 응답") |
| dto/search | DeveloperSearchQuery, ProjectSearchQuery | 요청 쿼리와 검증 |
| dto/search | DeveloperSearchItemResponse, ProjectSearchItemResponse, SearchPageResponse | 응답 |
| service | DeveloperSearchService | 검색 조건 정리, 팔로잉 탭 로그인 확인, 고급 검색 권한 확인(6단계) |
| service | ProjectSearchService | 검색 조건 정리(모집 직군 값 변환, 오늘 날짜), 응답 변환 |
| repository | DeveloperSearchRepository | `users` 조건 검색, 수 세기, 페이지, 팔로우 여부 |
| repository | ProjectSearchRepository | `projects` 조건 검색, 수 세기, 페이지, 참여 인원 |

`dto/project/ProjectSearchRequest`(7.1)와 헷갈리지 않도록 탐색 요청은 `ProjectSearchQuery`로 이름 짓는다.

### 조회 방식

- 두 검색 Repository는 `NamedParameterJdbcTemplate`로 SQL을 쓴다(`ProjectQueryRepository`와 같은 방식). JSONB 배열 조건은 `jsonb_array_elements_text`·`jsonb_array_elements`의 `EXISTS` 조건으로 만들고, 수 세기와 페이지를 DB에서 처리한다. 회원·공고 전체를 메모리로 읽지 않는다.
- 검색어는 Java에서 NFKC 정규화와 소문자 변환을 한 뒤 공백으로 나눈다. DB 값은 `lower()`로 비교하고 `%`·`_`는 이스케이프한다(`ProjectQueryRepository`와 같다).
- `follows`(4단계)는 SQL로 읽는다. 엔티티는 팔로우 계획에서 만든다.
- 프로젝트 검색은 7.1 조회 코드를 쓰지 않고 `ProjectSearchRepository`가 `projects`·`project_members`를 읽는다(1절 결정).
- 공개 범위는 7.1 목록과 같다: `deleted_at IS NULL`이고 `is_public = true`인 공고만 나온다. 7.1과 따로 구현하므로 프로젝트 쪽 공개 규칙이 바뀌면 함께 맞춰야 한다. 3단계 테스트에서 7.1 목록과 공개 범위가 같은지 확인하고, 프로젝트 담당자에게 공유한다.
- 모집 직군 값 변환(화면 라벨 ↔ 코드)은 프로젝트의 `ProjectRoleValues`를 호출만 한다. 오늘 날짜는 프로젝트 기능과 같은 Asia/Seoul 기준이다.

### 요청자 식별

- 공개 GET이지만 토큰이 있으면 JWT 필터가 인증 정보를 채운다. 인증 정보가 있으면 `Authentication.getName()`의 이메일로 회원을 찾고, 없으면 비로그인으로 처리한다. 요청 쿼리로 받은 사용자 ID는 신뢰하지 않는다.
- 피드·팔로우 계획의 요청자 식별 구성요소를 함께 쓴다. 먼저 구현하는 쪽에서 만든다. 프로젝트의 `ProjectCurrentUserProvider`, AI의 `CurrentUserProvider`는 각 담당 코드이므로 고치거나 가져다 쓰지 않는다.

### 공개 경로

- 비로그인 사용자도 검색할 수 있다(`docs/frontend-pages.md`: 탐색은 공개). 팔로잉 탭과 고급 검색(6단계)만 로그인이 필요하다.
- 공통 `SecurityConfig`에 `GET /api/v1/search`를 `permitAll`로 추가한다. 공통 인증 설정이므로 바꾸기 전에 팀에 공유한다. 팔로잉 탭과 고급 검색의 로그인·권한 확인은 Service에서 한다.

### 오류 응답

- Service는 `ResponseStatusException`을 던지고, 응답 본문은 공통 `ErrorResponse` 형식으로 맞춘다.
- `type`이 없거나 다른 값인 경우처럼 공통 `GlobalExceptionHandler`가 처리하지 않는 요청 오류는 500이 될 수 있다. 공통 처리기는 고치지 않고 `SearchExceptionHandler`(검색 Controller 전용, `LikedPostsExceptionHandler`와 같은 방식)에서 400으로 바꾼다.
- 400 입력 오류, 401 로그인 필요(팔로잉 탭, 6단계의 고급 검색), 403 고급 검색 권한 없음(6단계).

### DB 반영

- 이 계획은 새 엔티티를 만들지 않으므로 서버를 재시작해도 테이블이 생기지 않는다. `follows`는 팔로우 계획, `bookmarks`는 #93에서 생긴다.
- 인덱스는 ERD에 없으므로 추가하지 않는다.

## 4. API

### 공통

| 항목 | 내용 |
|---|---|
| 경로 | `GET /api/v1/search` (공개) |
| `type` | 필수. `developers`·`projects` |
| `q` | 100자 이내(현재 검색창 입력 제한과 같다) |
| `page`, `size` | `page`는 0부터, `size`는 기본 20·최대 100(피드·팔로우·프로젝트 목록과 같다) |
| 응답 | `items`, `page`, `size`, `totalCount`, `totalPages` |

- `totalCount`는 검색 조건을 적용한 전체 수다. 화면의 "N명의 개발자"·"N개의 프로젝트"에 쓴다(6.10).
- 페이지 이동(1절 결정): 화면은 `/projects` 목록(`ApiProjectList`)처럼 이전·다음 버튼과 현재/전체 페이지를 보여 주고, 누른 페이지만 요청해 목록을 바꾼다. 결과가 한 페이지 이하이면 이동 영역을 숨긴다.
- 현재 페이지는 화면 URL의 `page`에 둔다(0부터, `/projects`와 같다). 새로고침·공유·뒤로 가기로 같은 페이지가 열린다. 검색어·필터·탭이 바뀌면 첫 페이지로 돌아간다.
- 전체 페이지보다 큰 `page`는 빈 목록과 실제 `totalCount`·`totalPages`로 응답한다. 화면은 마지막 페이지로 이동할 수 있게 안내한다.

### 개발자 검색 (6.2·6.4·6.5)

| 파라미터 | 값 | 화면 URL | 조건 |
|---|---|---|---|
| `q` | 100자 이내 | `q` | 공백으로 나눈 단어가 모두 닉네임·태그·직군·경력·기술 스택·관심 분야 중 어딘가에 포함된다. 대소문자를 구분하지 않는다(목업과 같다) |
| `jobCategory` | 직군 문구 하나 | `job` | 직군 목록에 포함 |
| `career` | 30자 이내 | `career` | 경력 문구가 같다 |
| `interests` | 관심 분야 문구, 반복 | `interest` | 하나 이상 일치 |
| `skills` | 기술 스택 문구, 반복 | `stack` | 모두 포함 |
| `available` | `true`·`false` | `available=yes`·`no` | `true`는 `AVAILABLE`, `false`는 그 밖의 상태 |
| `following` | `true`·`false` | `tab=following` | `true`면 요청자가 팔로우한 회원만(4단계) |

- `interests`·`skills`는 각각 30개까지, 값은 30자 이내로 받는다(현재 화면 URL 검증 `parseSearch`와 같다).
- 목록에 없는 문구는 따로 거절하지 않는다. 그 값을 가진 회원이 없으므로 조건에 맞는 결과가 없다(목업과 같다).
- 팔로잉 탭: 비로그인으로 `following=true`를 보내면 401로 응답한다. 화면은 비로그인이면 요청하지 않고 지금의 로그인 안내를 보여 준다. 자기 자신은 팔로우할 수 없으므로 팔로잉 탭에 본인은 나오지 않는다.
- 정렬: 닉네임 오름차순, 같으면 회원 id 오름차순(목업은 한국어 기준 닉네임 순). 영문과 한글이 섞일 때의 순서는 DB 정렬 규칙(collation)에 따라 목업과 다를 수 있어 1단계에서 확인한다.

항목:

```json
{
  "tag": "dev-tag",
  "nickname": "개발자",
  "profileImage": null,
  "jobCategories": ["백엔드 개발자"],
  "career": "2년 차",
  "summary": "한 줄 소개",
  "skills": ["Java", "Spring Boot"],
  "interests": ["핀테크"],
  "collaborationStatus": "AVAILABLE",
  "trustScore": 36.5,
  "followed": false
}
```

- `followed`는 요청자가 이 회원을 팔로우하는지다. 4단계에서 추가하며, 비로그인이면 `false`다.
- 이메일, 회원 id, 연락 가능 시간, 결제 정보는 넣지 않는다. 본인 카드인지는 화면이 내 프로필의 태그(`/api/profiles/me`)와 비교해 판단한다(팔로우 계획과 같다).

### 고급 검색 (6단계)

1~5단계에는 넣지 않는다(1절 결정). 아래는 현재 목업 기준의 예정 내용이며, 6단계를 시작할 때 5.0 작업 결과(신뢰 온도를 저장하는 곳과 계산 방식)에 맞춰 다시 정한다.

| 파라미터 | 값 | 조건 |
|---|---|---|
| `minTrustScore` | 숫자. 화면 선택지는 목업과 같은 36.5·38·40 | 신뢰 온도가 이 값 이상 |
| `sort` | `nickname`(기본)·`trust` | `trust`는 신뢰 온도 높은 순, 같으면 닉네임 순 |

- 프리미엄 회원과 관리자만 쓸 수 있다(목업은 무료가 아닌 계정에 연다). 이 값을 보낸 비로그인 요청은 401, 그 밖의 회원은 403으로 응답한다. 화면은 지금처럼 권한이 없으면 요금제 안내를 보여 주고 값을 보내지 않는다.
- 신뢰 온도가 없는 회원은 36.5로 본다(목업과 같다).

### 프로젝트 검색 (6.3·6.8)

서버는 탐색 전용 조회(`ProjectSearchRepository`)로 공고를 찾는다(1절 결정). 조건은 현재 탐색 화면(`lib/search/searchService.ts`의 `searchProjects`)과 같다.

| 파라미터 | 값 | 화면 URL | 조건 |
|---|---|---|---|
| `q` | 100자 이내 | `q` | 공백으로 나눈 단어가 모두 제목·분야·모집 직군·기술 스택 중 어딘가에 포함된다. 대소문자를 구분하지 않는다(목업과 같다) |
| `jobRole` | 모집 직군 하나 | `role` | 모집 직군에 포함. 화면 URL은 라벨이고 보낼 때 코드로 바꾼다 |
| `status` | 아래 "모집 상태"의 값 하나 | `status` | 아래 "모집 상태" |
| `interests` | 분야 문구, 반복 | `field` | 하나 이상 일치 |
| `techStacks` | 기술 스택 문구, 반복 | `stack` | 모두 포함 |

- `interests`·`techStacks`는 각각 30개까지, 값은 30자 이내로 받는다(현재 화면 URL 검증 `parseSearch`와 같다). 목록에 없는 문구는 거절하지 않고, 그 값을 가진 공고가 없으므로 결과가 없다.
- 모집 직군: `recruitment_slots`의 `roleCode`에 기본 직군은 코드(`BACKEND` 등), 직접 입력한 직군은 그 문구로 저장된다. 검색어 단어가 기본 직군 라벨(`ProjectJobRole`의 label, 예: `백엔드`)에 포함되면 그 코드로도 찾고, 직접 입력한 직군은 저장된 문구로 찾는다. `jobRole` 값은 7.1과 같이 `ProjectRoleValues`로 바꿔 비교한다.
- 정렬: 작성 시각 최신순, 같으면 id 내림차순(목업도 작성 시각 최신순이다).
- 공개 범위: 비공개·삭제된 공고는 나오지 않는다(3절 "조회 방식").

모집 상태 (목업 `projectStatus`와 같은 계산):

| 값 | 화면 라벨 | 조건 |
|---|---|---|
| `RECRUITING` | 모집 중 | `status = RECRUITING`이고 모집 마감일(`recruitment_end`)이 오늘 이후(당일 포함) |
| `RECRUITMENT_CLOSED` | 모집 마감 | `status = RECRUITING`이고 모집 마감일이 지났다 |
| `RECRUITMENT_COMPLETED` | 모집 완료 | `status = RECRUITMENT_COMPLETED` |
| `IN_PROGRESS` | 프로젝트 진행 중 | `status = IN_PROGRESS` |
| `COMPLETED` | 프로젝트 종료 | `status = COMPLETED` |

`RECRUITMENT_CLOSED`는 DB에 저장하는 값이 아니라 검색 API에서만 쓰는 값이다. `projects.status`와 `ProjectStatus`는 바꾸지 않는다.

항목:

```json
{
  "id": 3,
  "title": "프로젝트 제목",
  "summary": "한 줄 소개",
  "imageUrl": null,
  "status": "RECRUITING",
  "jobRoles": ["BACKEND", "FRONTEND"],
  "techStacks": ["Spring Boot", "React"],
  "interests": ["핀테크"],
  "totalCapacity": 4,
  "memberCount": 2,
  "developmentStart": "2026-12-01",
  "developmentEnd": "2027-02-28",
  "createdAt": "2026-11-09T10:00:00"
}
```

- `status`는 위 "모집 상태"의 값이다. 카드 배지와 필터가 같은 기준을 쓴다.
- `jobRoles`는 `recruitment_slots`의 `roleCode` 목록(중복 제거)이다. 화면은 `projectRoleLabel`로 라벨을 표시한다.
- `memberCount`는 `project_members`의 `ACTIVE` 인원이다(7.1 목록과 같은 기준).
- 주최자 회원 id, 조회수, 북마크 여부는 넣지 않는다. 카드의 북마크는 #93의 프로젝트 북마크 상태로 표시한다(1절 결정).

### 카드에서 부르는 다른 API (6.6·6.7·6.8)

| 코드 | API | 내용 |
|---|---|---|
| 6.6 | `POST`·`DELETE /api/v1/users/{slug}/follow` | 팔로우 계획 9.1·9.2. 응답의 최종 상태(`followed` 등)로 버튼을 맞춘다. 이미 팔로우한 상태의 POST도 현재 상태를 응답한다 |
| 6.7 | `POST /api/v1/messages/chats` | 메신저 11.5. `{ "targetSlug": "{tag}" }` → `{ "id": 12 }`. 이미 있는 방이면 그 방을 돌려준다. 화면은 `/messages/chats/{id}`로 이동한다 |
| 6.8 | `POST`·`DELETE /api/v1/projects/{id}/bookmark` | 7.16(#93). 탐색 목록에서도 같은 기능을 쓴다 |

## 5. 프론트엔드 연결

현재 `/explore` 화면(`app/explore/page.tsx`)은 Mock 데이터 켜기·끄기에 따라 다르게 동작한다.

- Mock 켜기: Mock 프로필·프로젝트를 `lib/search/searchService.ts`로 걸러 보여 준다. 팔로우는 `lib/state/profileStore.ts`를 거쳐 localStorage `devmozip:profiles:v1`에 저장된다.
- Mock 끄기: 개발자 탐색은 Mock 데이터가 비어 결과가 항상 없다. 프로젝트 탐색은 탐색 화면 대신 `/projects`의 `ApiProjectList`를 그대로 보여 준다(프로젝트 API 연동 때 추가된 분기).

연결 방침:

- Mock 켜기 화면, Mock 카드(`DeveloperResults`·`ProjectResults`), `searchService.ts`는 그대로 둔다. Mock 끄기에서만 API를 쓴다.
- 두 모드의 화면 구조와 디자인(머리말, 검색창, 왼쪽 필터, 탭, 결과 수, 선택한 필터, 빈 결과)을 같게 유지한다. 프로젝트 탐색의 `ApiProjectList` 분기는 탐색 화면의 API 결과로 바꾼다(프로젝트 담당자 공유).
- 화면 URL은 지금 형식(`type`, `q`, `job`, `career`, `interest`, `stack`, `available`, `tab`, `role`, `field`, `status`)을 유지하고 `page`를 더한다. 새로고침·공유·뒤로 가기 복원도 지금과 같다. 서버 결과는 localStorage에 저장하지 않는다.

### 파일

| 위치 | 파일 (예정) | 역할 |
|---|---|---|
| BFF | `app/api/search/route.ts` | `GET /api/v1/search`로 전달. 허용한 쿼리만 넘기고, 로그인 상태면 기존 `backendFetch`가 토큰을 붙인다. 사용자 ID는 보내지 않는다 |
| 타입 | `types/searchApi.ts` | 요청·응답 타입. Mock용 `types/search.ts`는 그대로 둔다 |
| 변환·해석 | `lib/search/searchApi.ts` | 화면 필터(`parseSearch` 결과) → API 쿼리, 응답 검증, 오류 문구 |
| 개발자 카드 | `app/explore/_components/ApiDeveloperResults.tsx` | Mock 카드와 같은 모양 |
| 프로젝트 카드 | `app/explore/_components/ApiProjectResults.tsx` | Mock 카드와 같은 모양 |
| 수정 | `app/explore/page.tsx`, `app/explore/_components/SearchFilters.tsx` | 모드별 분기. 필터 선택지를 페이지에서 받도록 바꾼다 |

### 화면 URL → API 쿼리 (6.10)

| 화면 URL | 개발자 | 프로젝트 |
|---|---|---|
| `q` | `q` | `q` |
| `job` | `jobCategory` | — |
| `career` | `career` | — |
| `interest` | `interests` | — |
| `field` | — | `interests` |
| `stack` | `skills` | `techStacks` |
| `available=yes`·`no` | `available=true`·`false` | — |
| `tab=following` | `following=true` | — |
| `role`(라벨) | — | `jobRole`(코드. `lib/projects/projectChoices.ts`의 `projectRoleCode`) |
| `status`(라벨) | — | `status`(4절 "모집 상태"의 값) |
| `page` | `page` | `page` |

검색 유형을 바꾸면 검색어만 남기고 필터를 비우는 지금 동작(`changeSearchTarget`)을 유지한다(6.1).

### 필터 선택지

- 개발 직군·기술 스택·관심 분야: 백엔드 enum을 쓴다. 비로그인도 쓸 수 있는 가입 선택지(`GET /api/v1/auth/signup/options`, 기존 `signupOptionsAction`)에서 받는다. Mock 끄기에서는 Mock 프로필의 값을 섞지 않는다.
- 경력: `careerOptions`(가입 화면과 같은 목록).
- 프로젝트 모집 직군·분야·기술 스택: `ApiProjectList`와 같은 출처(모집 직군은 `roleLabels`, 분야·기술 스택은 프로젝트 선택지 또는 가입 선택지)를 쓴다.
- 프로젝트 모집 상태: 지금 탐색 화면의 5개(`mocks/search.ts`의 `projectStatusOptions`)를 그대로 쓴다.
- `SearchFilters`가 Mock 저장소에서 선택지를 만드는 부분을 페이지에서 받도록 바꾼다. Mock 켜기의 선택지는 지금과 같다.

### 개발자 카드 (6.4·6.6·6.7·6.9)

- 표시: 이미지, 닉네임, 직군·경력, 한 줄 소개, 기술 스택, 관심 분야, 협업 상태, 신뢰 온도. 프로필 상단(`ApiProfileHeader`)과 같은 함수(`profileImageUrl`, `formatCareer`, `collaborationLabel`)를 쓰고, 신뢰 온도가 없으면 "—"로 표시한다.
- 이동: 닉네임·이미지를 누르면 `/users/{tag}`로 간다(6.9).
- 버튼: 본인 카드는 "내 프로필 보기"만 둔다. 다른 회원 카드에는 팔로우·언팔로우와 메시지 보내기를 둔다. 비로그인의 팔로우 버튼은 지금처럼 로그인 화면으로 보낸다.
- 팔로우(6.6): 처음 상태는 응답의 `followed`다. 누르면 9.1·9.2를 부르고 응답의 최종 상태로 맞추며, 요청 중에는 버튼을 막는다. 팔로잉 탭에서 언팔로우하면 목업처럼 카드를 바로 빼고, 현재 페이지를 다시 받아 결과 수와 페이지를 맞춘다. 다시 받은 페이지가 비어 있으면 앞 페이지로 이동한다. 버튼은 팔로우 계획 4단계에서 만드는 팔로우 버튼을 `components`에 두고 프로필 상단과 함께 쓴다.
- 메시지 보내기(6.7): #90의 `StartChatButton`(`app/(user)/users/[userId]/_components`)을 `components`로 옮겨 프로필 상단과 함께 쓴다. 누르면 11.5를 부르고 `/messages/chats/{id}`로 이동하며, 비로그인 처리도 프로필 상단과 같다. 지금 목업 카드에는 대화 버튼이 없다(협업 제안 UI를 지운 뒤 다시 두지 않았다). WBS 6.7에 따라 API 화면에 둔다.

### 프로젝트 카드 (6.8·6.9)

- 표시: 목업 카드와 같은 항목(분야, 모집 상태, 제목, 요약, 기술 스택, 모집 직군, 인원 `memberCount`/`totalCapacity`, 진행 기간). 모집 상태는 응답의 `status`를 5개 라벨로 바꿔 표시한다. 목업의 표시 글자(`mark`)는 API에 없으므로 `ApiProjectList`처럼 제목 첫 글자를 쓴다.
- 이동: 제목과 상세 버튼은 `/projects/{id}`로 간다(6.9).
- 북마크(6.8): #93의 `ProjectBookmarkButton`을 그대로 쓴다(1절 결정). 비로그인이면 #93과 같이 로그인 화면으로 보낸다.
  - 저장 단위: 탐색 카드에서 저장하는 것은 프로젝트 북마크(7.16)다. `bookmarks`는 프로젝트와 게시글이 함께 쓰는 테이블이지만, 프로젝트 북마크는 `project_id`만, 게시글 북마크(8.8)는 `post_id`만 채운 별개의 행이다(#93의 DDL은 대상 하나만 채우도록 제약을 둔다). 북마크함도 프로젝트 탭(16.2)과 게시글 탭(16.3)을 따로 조회하므로, 탐색 카드에서 저장한 공고는 프로젝트 탭에만 나온다.
  - 표시: 버튼은 내 프로젝트 북마크 목록(`GET /api/v1/projects/bookmarks`)을 읽어 `projectBookmarkStore`에 두며, 이 상태에는 프로젝트 북마크만 들어 있다. 프로젝트 상세, `/projects` 목록, 북마크함 프로젝트 탭이 이미 이 상태를 함께 쓰므로 탐색 카드에서 저장·해제해도 세 화면의 값이 맞는다.

### 상태 처리

- 결과 수는 `totalCount`로 표시하고, 불러오는 중에는 "조회 중…"을 보여 준다.
- 검색 조건이나 페이지가 바뀌면 그 페이지를 다시 받고, 이전 요청의 늦은 응답은 버린다.
- 빈 결과, 오류와 재시도, 팔로잉 탭 비로그인 안내는 지금 화면 문구를 쓴다.
- Mock 끄기에서는 결과 아래의 "Mock 데이터 기반 검색" 안내 문구를 보이지 않는다.
- 고급 검색 영역: 6단계 전에는 지금 화면 그대로 둔다(1절 결정). 이 기간에는 Mock 끄기에서 고른 값을 서버로 보내지 않으므로 검색 결과에 반영되지 않는다. 6단계에서 연결한다.

### 바꾸지 않는 화면

- 헤더 검색창(`SearchBar`)은 `/explore`로 이동만 하므로 바꾸지 않는다.
- `/projects` 목록(`ApiProjectList`)은 7.1을 그대로 쓰며 바꾸지 않는다.

## 6. 단계별 진행

모든 단계에서 `frontend`의 `npm run build`와 관련 테스트를 실행한다. 백엔드 코드를 바꾼 단계는 `backend`의 `./gradlew test`도 실행한다. 실행한 명령, 성공·실패, 실행하지 못한 이유를 7절에 기록한다.

### 1단계 — 정책 확정

- [x] 백엔드 API를 먼저 만들고 프론트엔드를 연결하는 순서 (2026-11-09)
- [x] 페이지 이동 방식, 고급 검색을 6단계로 분리 (2026-11-09)
- [x] #93 북마크 버튼 사용과 시작 조건, 6단계 전 고급 검색 영역 유지 (2026-11-09)
- [x] 6.3 프로젝트 탐색 전용 조회 (2026-11-09)
- [ ] 실제 DB 확인: 검색 대상 회원 수(`ACTIVE`·태그·직군), 닉네임 정렬 규칙, `recruitment_slots`의 `roleCode` 값(기본 코드·직접 입력), `follows`·`bookmarks` 테이블 유무
- [ ] 공유: 프로젝트 담당자(6.3이 `projects`·`project_members`를 직접 읽고 공개 범위 규칙을 7.1과 함께 맞춰야 하는 점, 검색 API에서만 쓰는 `RECRUITMENT_CLOSED`, 탐색 화면의 `ApiProjectList` 분기 교체, #93 북마크 버튼 사용), 5.5 담당자(`users.trust_score` 갱신 여부), 팀(`SecurityConfig` 공개 GET)

완료 조건: DB 확인과 공유가 끝나 있다.

### 2단계 — 개발자 검색 API (6.1·6.2·6.4·6.10)

- [ ] 요청자 식별 구성요소(피드·팔로우와 공유), DTO, `DeveloperSearchRepository`, `DeveloperSearchService`, `SearchController`, `SearchExceptionHandler`
- [ ] `SecurityConfig`에 공개 GET 반영 (팀 공유 후)
- [ ] Testcontainers PostgreSQL 테스트: 검색어 단어 조합·대소문자·`%`·`_`, 필터별·조합 조건, 검색 대상 조건(`WARNED`·`SUSPENDED`·태그 없음·직군 없음 제외), 정렬, 페이지·`totalCount`·`totalPages`·전체보다 큰 페이지, `type` 누락·잘못된 값과 형식 오류의 400, 비로그인 검색
- [ ] API 문서 `docs/search-api.md` 작성

완료 조건: `./gradlew test` 통과. JSONB 조회이므로 H2로 대신하지 않는다. Docker가 없어 Testcontainers 테스트가 건너뛰어지면 통과로 보고하지 않는다.

### 3단계 — 프로젝트 검색 API (6.3·6.8)

- [ ] `ProjectSearchRepository`, `ProjectSearchService`, DTO, `SearchController`의 `type=projects`
- [ ] Testcontainers PostgreSQL 테스트: 검색어 단어 조합(제목·분야·모집 직군 라벨과 직접 입력 직군·기술 스택), 필터별·조합 조건, 모집 상태 5개(마감일 당일과 다음 날 경계 포함), 비공개·삭제 공고 제외(같은 데이터에서 7.1 목록과 공개 범위가 같은지), 참여 인원 수, 정렬, 페이지·`totalCount`·`totalPages`, 형식 오류 400
- [ ] API 문서 갱신

완료 조건: 2단계와 같다.

### 4단계 — 팔로잉 탭·팔로우 상태 (6.5·6.6)

선행: 팔로우 계획 2단계(`Follow` 엔티티).

- [ ] `following=true` 조건과 항목의 `followed`
- [ ] 테스트: 비로그인 `following=true` 401, 요청자 기준 `followed`(다른 회원 사이의 팔로우 관계는 반영하지 않음), 언팔로우한 회원 제외, 팔로잉 탭과 다른 필터의 조합
- [ ] API 문서 갱신

### 5단계 — 프론트엔드 연결 (6.1~6.10)

선행: 2~4단계, 팔로우 계획 3·4단계(9.1·9.2 API, BFF, 팔로우 버튼), #90(대화 시작), #93(북마크). 병합되지 않은 선행 작업의 버튼은 병합 후 연결한다. 북마크 버튼은 #93이 develop에 병합되고 그 develop을 작업 브랜치에 병합한 뒤에 연결을 시작한다(1절 결정).

- [ ] BFF, 타입, 쿼리 변환·응답 해석과 테스트
- [ ] Mock 끄기의 개발자·프로젝트 결과, 필터 선택지, 결과 수, 페이지 이동
- [ ] 팔로우·메시지 보내기·북마크 버튼
- [ ] 로딩·빈 결과·오류·비로그인 처리, Mock 켜기 화면 회귀 테스트
- [ ] 실제 백엔드·PostgreSQL로 확인: 개발자 검색 → 필터 → 페이지 이동 → 팔로잉 탭 → 팔로우·언팔로우 → 메시지 보내기 → 프로젝트 검색 → 북마크(북마크함 프로젝트 탭 확인) → 상세 이동

완료 조건: 빌드·테스트 결과와 실제 서버 연결 확인 여부를 따로 기록한다. Mock 테스트 통과는 실제 DB 검증이 아니다. 1~5단계를 PR로 올릴 때 6단계 코드는 넣지 않는다.

### 6단계 — 고급 검색 (신뢰 온도 조건·정렬)

선행: 1~5단계 PR과 WBS 5.0(활동/리뷰/신뢰 온도) 작업이 모두 develop에 병합되어 있다. develop에서 새 브랜치를 만들어 작업하고 별도 PR을 올린다(1절 결정).

- [ ] 5.0 작업 결과 확인: 신뢰 온도를 저장하는 곳(`users.trust_score` 갱신 여부)과 계산 방식. 4절 "고급 검색"의 조건·정렬을 그에 맞춰 다시 정한다
- [ ] 백엔드: `minTrustScore`·`sort=trust`, 프리미엄·관리자 확인
- [ ] 테스트: 조건·정렬, 신뢰 온도가 없는 회원, 다른 필터·페이지와의 조합, 비로그인 401, 무료 회원 403
- [ ] 프론트엔드: Mock 끄기 화면의 고급 검색 영역 연결(권한이 없으면 요금제 안내, 값을 보내지 않음), 화면 상태 → API 쿼리 변환
- [ ] API 문서 갱신

완료 조건: 2·5단계와 같다.

## 7. 진행 기록

| 날짜 | 단계 | 내용 |
|---|---|---|
| 2026-11-09 | 계획 | 이 문서 작성. WBS 6.1~6.10, 현재 `/explore` Mock 화면, 백엔드 회원·프로젝트 조회 코드, 팔로우 계획·메신저(#90)·프로젝트 북마크(#93) 브랜치와 대조. 코드 변경 없음. |
| 2026-11-09 | 1단계 | 목록을 페이지 이동 방식으로 결정(응답의 `hasNext`를 `totalPages`로 바꿈, 화면 URL에 `page` 추가). 고급 검색을 6단계로 분리해 5.0 작업 병합 뒤 별도 브랜치·PR로 진행하기로 함. 프로젝트 결과 조회 방식과 북마크 저장 단위(프로젝트·게시글 별개 행) 설명을 보완하고, 6단계 전 고급 검색 영역 표시를 남은 결정에 추가. 코드 변경 없음. |
| 2026-11-09 | 1단계 | 6.3을 검색 기능 안의 프로젝트 탐색 전용 조회로 결정(7.1 조회 코드 미사용, 목업의 검색어·선택 개수·모집 상태 5개 유지, 검색 API 전용 값 `RECRUITMENT_CLOSED`). 6.8은 #93의 북마크 버튼을 쓰고, #93이 develop에 병합된 뒤 그 develop을 작업 브랜치에 병합하고 시작하기로 결정. 6단계 전 고급 검색 영역은 지금 화면 그대로 두기로 결정. 남은 결정 없음. 코드 변경 없음. |

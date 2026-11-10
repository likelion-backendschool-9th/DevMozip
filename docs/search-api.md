# 통합 검색 API (WBS 6.1~6.10)

[개발자 탐색 계획](explore-plan.md) 2~4단계에서 구현한 API다. 결정 근거와 단계별 기록은 계획 문서에 있다. 고급 검색(신뢰 온도 조건·정렬, 계획 6단계)은 들어 있지 않다.

## 공통

`GET /api/v1/search?type={developers|projects}&...`

- 공개 GET이다(`SecurityConfig`). 토큰이 있으면 요청자 기준 값(`followed`)을 채우고, 만료되거나 잘못된 토큰은 비로그인으로 처리한다.
- 요청자는 JWT의 이메일로만 찾는다(`CurrentMemberProvider`). 쿼리의 사용자 ID는 쓰지 않는다.
- 쿼리는 Controller가 그대로 읽는다. 스프링 바인딩은 값 하나에 든 쉼표를 목록으로 나누므로 쓰지 않는다. `skills=Java,Spring Boot`는 값 하나(`Java,Spring Boot`)다.
- 하나만 받는 값(`type`, `q`, `page` 등)이 두 번 이상 오면 400이다. 반복 값은 앞뒤 공백을 지우고, 비어 있으면 빼고, 중복은 하나로 본다.
- 빈 값(`q=`, `available=`)은 조건이 없는 것으로 본다. 알 수 없는 쿼리는 무시한다.
- 오류는 공통 `ErrorResponse` 형식(`status`, `error`, `message`, `path`, `timestamp`)이다. `message`는 화면에 그대로 보여 줄 수 있는 문장이다.
- JSONB 함수와 ICU 정렬 규칙(`ko-x-icu`)을 쓰므로 PostgreSQL에서만 동작한다. H2(`local` 프로필)에서는 검색이 동작하지 않는다.

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `type` | 없음(필수) | `developers` 또는 `projects`. 없거나 다른 값이면 400 |
| `q` | 없음 | 100자 이내. NFKC 정규화와 소문자 변환 뒤 공백으로 나눈 단어가 모두 어딘가에 포함되어야 한다. 대소문자 구분 없음. `%`·`_`는 글자 그대로 찾는다 |
| `page` | `0` | 0 이상의 정수 |
| `size` | `20` | 1~100 |

응답:

```json
{ "items": [], "page": 0, "size": 20, "totalCount": 0, "totalPages": 0 }
```

- `totalCount`는 검색 조건을 적용한 전체 수, `totalPages`는 `ceil(totalCount / size)`다.
- 전체 페이지보다 큰 `page`는 빈 `items`와 실제 `totalCount`·`totalPages`로 응답한다.
- 수와 목록은 한 트랜잭션(REPEATABLE READ)에서 읽는다.

## 개발자 검색 (6.2·6.4·6.5)

`GET /api/v1/search?type=developers`

검색 대상은 프로필 조회(`GET /api/v1/users/{slug}`)가 찾을 수 있는 회원과 같다: `status = ACTIVE`이고, 태그가 있고(빈 문자열 제외), 직군이 하나 이상이다. 로그인한 본인도 나온다.

| 쿼리 | 조건 |
|---|---|
| `q` | 닉네임·태그·경력·직군·기술 스택·관심 분야 중 어딘가에 모든 단어가 포함된다. 한 줄 소개는 찾지 않는다 |
| `jobCategory` | 30자 이내. 직군 목록에 이 문구가 있다 |
| `career` | 30자 이내. 경력 문구가 같다(예: `3년 차`) |
| `interests` | 반복, 30개·각 30자 이내. 하나 이상 일치 |
| `skills` | 반복, 30개·각 30자 이내. 모두 포함 |
| `available` | `true`·`false`. `true`는 `collaboration_status = AVAILABLE`, `false`는 그 밖의 상태와 값 없음 |
| `following` | `true`·`false`. `true`면 요청자가 팔로우한 회원만. 비로그인이면 401 |

- 직군·경력·관심 분야·기술 스택은 저장된 문구 그대로(대소문자 구분) 비교한다. 목록에 없는 문구는 거절하지 않고 결과가 없다.
- 정렬: 닉네임의 한국어 정렬(`COLLATE "ko-x-icu"`), 같으면 회원 id 오름차순. 기호·숫자·한글·영문 순이며 목업의 `localeCompare(..., 'ko')`와 같다.
- 자기 자신은 팔로우할 수 없으므로 팔로잉 탭에 본인은 나오지 않는다.

```json
{
  "items": [
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
      "trustScore": 36.50,
      "followed": false
    }
  ],
  "page": 0, "size": 20, "totalCount": 1, "totalPages": 1
}
```

- `followed`는 요청자가 이 회원을 팔로우하는지다. 비로그인이면 `false`다. 다른 회원 사이의 팔로우 관계는 반영하지 않는다.
- 이메일, 회원 id, 연락 가능 시간, 결제 정보는 넣지 않는다. 본인 카드인지는 화면이 내 프로필의 태그와 비교해 판단한다.
- `trustScore`는 `users.trust_score` 값이다(가입 때 36.50). 갱신 방식은 WBS 5.5 담당 작업이다.

## 프로젝트 검색 (6.3·6.8)

`GET /api/v1/search?type=projects`

탐색 전용 조회(`ProjectSearchRepository`)이며 7.1 목록 API(`GET /api/v1/projects`)와 따로 둔다. 공개 범위는 7.1 비로그인 목록과 같다: `deleted_at IS NULL`이고 `is_public = true`인 공고만 나온다.

| 쿼리 | 조건 |
|---|---|
| `q` | 제목·분야·모집 직군·기술 스택 중 어딘가에 모든 단어가 포함된다. 기본 직군은 라벨(예: `백엔드`, `UI/UX`)로 찾고 코드 글자(`BACKEND`)로는 찾지 않는다. 직접 입력한 직군은 저장된 문구로 찾는다 |
| `jobRole` | 30자 이내. 모집 직군 코드·라벨 또는 직접 입력한 직군. `ProjectRoleValues`로 바꿔 대소문자 구분 없이 비교한다(7.1과 같다). 제어 문자가 있으면 400 |
| `status` | 아래 모집 상태 값 하나. 다른 값이면 400 |
| `interests` | 반복, 30개·각 30자 이내. 하나 이상 일치(대소문자 구분 없음) |
| `techStacks` | 반복, 30개·각 30자 이내. 모두 포함(대소문자 구분 없음) |

모집 상태(오늘 날짜는 Asia/Seoul 기준, 목업 `projectStatus`와 같은 계산):

| 값 | 화면 라벨 | 조건 |
|---|---|---|
| `RECRUITING` | 모집 중 | `status = RECRUITING`이고 `recruitment_end`가 오늘 이후(당일 포함) |
| `RECRUITMENT_CLOSED` | 모집 마감 | `status = RECRUITING`이고 `recruitment_end`가 지났다 |
| `RECRUITMENT_COMPLETED` | 모집 완료 | `status = RECRUITMENT_COMPLETED` |
| `IN_PROGRESS` | 프로젝트 진행 중 | `status = IN_PROGRESS` |
| `COMPLETED` | 프로젝트 종료 | `status = COMPLETED` |

`RECRUITMENT_CLOSED`는 검색 API에서만 쓰는 값이다. `projects.status`와 `ProjectStatus`는 바꾸지 않았다.

- 정렬: 작성 시각 최신순, 같으면 id 내림차순.

```json
{
  "items": [
    {
      "id": 3,
      "title": "프로젝트 제목",
      "summary": "한 줄 소개",
      "imageUrl": "https://example.com/p.png",
      "status": "RECRUITING",
      "jobRoles": ["BACKEND", "보안 엔지니어"],
      "techStacks": ["Spring Boot", "React"],
      "interests": ["핀테크"],
      "totalCapacity": 4,
      "memberCount": 2,
      "developmentStart": "2026-12-01",
      "developmentEnd": "2027-02-28",
      "createdAt": "2026-11-09T10:00:00"
    }
  ],
  "page": 0, "size": 20, "totalCount": 1, "totalPages": 1
}
```

- `status`는 위 모집 상태 값이다.
- `jobRoles`는 `recruitment_slots`의 `roleCode` 목록(중복 제거, 저장 순서)이다. 기본 직군은 코드, 직접 입력한 직군은 그 문구다.
- `totalCapacity`는 주최자 1명과 모집 인원의 합(7.1과 같다), `memberCount`는 `project_members`의 `ACTIVE` 인원이다.
- 주최자 회원 id, 조회수, 북마크 여부는 넣지 않는다. 카드의 북마크는 프로젝트 북마크 API(7.16)와 그 상태를 쓴다.

## 오류

| 상태 | 경우 |
|---|---|
| 400 | `type` 없음·다른 값, 하나만 받는 값의 중복, `q` 100자 초과, 값 30자 초과, 반복 값 30개 초과, `available`·`following`이 `true`·`false`가 아님, `status`가 다른 값, `jobRole`에 제어 문자, `page`·`size` 형식·범위 오류 |
| 401 | 비로그인 `following=true` |

## 프론트엔드 BFF

`GET /api/search` (`frontend/app/api/search/route.ts`)

- 위 쿼리 중 `type`별로 허용한 값만 확인해 넘긴다(`lib/search/searchApi.ts`의 `searchProxyQuery`). 형식이 틀리면 백엔드를 부르지 않고 400이다. 사용자 ID 같은 다른 값은 넘기지 않는다.
- 로그인 상태면 `backendFetch`가 쿠키의 토큰을 붙인다.
- 응답은 형식을 확인해 알려진 필드만 돌려준다. 백엔드 오류 본문은 넘기지 않고 400·401은 상태별 문구, 그 밖의 오류·연결 실패·형식 오류는 502로 답한다.

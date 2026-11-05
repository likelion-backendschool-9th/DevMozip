# 프로필 외부 링크 조회·수정 — #64

기존 소개 탭의 외부 링크 영역을 실제 프로필 API와 연결한다. GitHub·블로그 입력과 추가 링크의 이름·URL 행, 추가·삭제 버튼, 기존 AboutTab/ProfileEditor CSS 배치를 재사용한다. 기본 정보·자기소개·기술 스택·관심 분야의 동작은 유지한다.

## 브랜치와 범위

`feat/64-profile-external-links`는 선행 PR #60이 미병합인 상태에서 `feat/profile-skills-interests`의 7a369cc를 기준으로 분리했다. PR 작성 시 선행 브랜치를 base로 두면 외부 링크 변경만 비교할 수 있다. #60 병합 후 develop으로 base를 변경하고 변경 목록·충돌을 다시 확인한다. 이번 단계에서 커밋·푸시·PR 생성은 수행하지 않았다.

## 기존 컬럼과 저장 형식

Member.externalLinks는 기존 `users.external_links` JSON 컬럼에 매핑된 String이다. 엔티티 타입·DDL·ERD는 변경하지 않고, 기존 필드만 갱신하는 메서드를 추가했다. 실제 PostgreSQL의 저장 데이터 형태는 이번 단계에서 확인하지 않았다.

이 컬럼의 JSON 내부 계약이 기존 서버 코드에 없어서, 기존 프론트의 GitHub·블로그·추가 링크 구성에 맞춰 다음 객체를 사용한다. 운영 데이터가 다른 구조라면 별도의 호환 정책 확인이 필요하다. 읽을 수 없는 기존 데이터를 빈 목록으로 숨기거나 자동 변환하지 않는다.

```json
{
  "github": "https://github.com/devmozip",
  "blog": "https://example.com/blog",
  "externalLinks": [
    {"label": "포트폴리오", "url": "https://example.com/portfolio"}
  ]
}
```

- GitHub·블로그는 각 500자 이내의 URL 또는 빈 문자열이다. 빈 문자열로 해당 링크를 삭제한다.
- 추가 링크는 최대 10개. 이름은 공백 제거 후 비어 있을 수 없고 원본 길이 40자 이내, URL은 500자 이내다. 입력 순서를 유지한다.
- PUT은 세 필드 모두 필수다. 누락·NULL은 400이며 기존 값을 지우지 않는다. 모든 필드를 비우려면 `{"github":"","blog":"","externalLinks":[]}`를 명시한다.
- DB NULL·빈 문자열은 미등록 응답으로 정규화한다. 정상 저장은 위 JSON 객체로 한다. 알려지지 않은 JSON 구조·위험 URL·깨진 데이터는 조회 오류로 반환하고 DB는 수정하지 않는다.
- URL은 절대 http/https 주소만 허용한다. javascript/data, 상대 주소, 사용자 이름·비밀번호 포함 URL, 역슬래시·제어 문자, 잘못된 호스트·포트는 거절한다. 외부 서버로 URL을 요청하거나 미리보기를 수집하지 않는다.

## API

| 메서드 | 경로 | 정책 |
| --- | --- | --- |
| GET | `/api/v1/users/{slug}/links` | 비로그인 가능. ACTIVE만 공개 |
| GET | `/api/v1/users/me/links` | 로그인 본인. ACTIVE·WARNED |
| PUT | `/api/v1/users/me/links` | 로그인 본인. ACTIVE·WARNED |

Controller는 인증 주체의 이메일을 Service로 전달한다. Service는 기존 MemberRepository.findByEmail을 사용하며 본문의 ID·이메일·권한으로 소유자를 선택할 수 없다. 저장 전에 전체 검증하고 외부 링크 필드만 갱신한다. 응답은 링크 세 필드만 포함한다. 기존 `/users/me` 인증 응답은 변경하지 않는다.

공개 미존재·미공개 상태는 동일한 404, 미인증 본인 경로는 401, 수정 불가 계정은 403, 잘못된 입력은 400이다. 본인 GET 인증 규칙이 공개 wildcard보다 먼저 적용된다. 해석할 수 없는 기존 JSON은 500이며 BFF는 내부 정보를 숨긴 502로 전달한다.

## 프론트

- BFF `/api/profiles/{slug}/links`, `/api/profiles/me/links`가 기존 backendFetch로 인증 정보를 전달한다. 사용자 식별·인증 정보는 요청 본문으로 받지 않는다.
- 수정은 같은 Origin의 JSON 요청만 허용한다. 요청·응답 필드를 제한하고 no-store로 조회한다. 프론트와 서버 모두 URL·개수·길이를 검증한다.
- 기존 소개 탭의 기술 스택·관심 분야 아래에 외부 링크를 표시한다. 로그인한 본인으로 확인된 경우만 수정 버튼을 표시한다.
- 링크는 새 탭으로 열고 `rel="noopener noreferrer"`를 적용한다. 이름은 React 텍스트로 렌더링한다.
- 조회 중·조회 실패·미등록을 구분한다. 실패는 다시 시도할 수 있으며 읽기 실패 상태에서 편집하지 않는다. 사용자 변경 시 진행 중인 조회를 취소한다.
- 저장 실패 시 입력을 유지하고 오류를 표시한다. 저장 중에는 추가·삭제·제출을 비활성화하고 성공 응답으로 화면을 갱신한다. 새로고침 시 API에서 다시 조회한다.

## 검증

- backend `./gradlew test --console=plain`: 211개 중 210개 통과, Docker/PostgreSQL 컨텍스트 1개 건너뜀. 신규 ProfileLinksIntegrationTest 17개 통과.
- frontend `npm test`: 569개 통과. `npm run build`: 통과.
- 통합 테스트: JSON 저장·재조회, NULL·전체 삭제, 공개·본인 인증, 계정 상태, 요청자 위조 방지, 다른 회원·필드 보존, URL 제한, 누락·NULL·길이·개수 경계, 잘못된 기존 JSON 처리.
- 프론트 테스트: 요청·응답 필드 제한, 공개·본인 경로, Origin·URL 검증, 삭제, 오류 전달, 기존 편집 폼·소개 탭 배치 유지.
- 실제 로컬 HTTP 서버 + H2: 본인 저장·재조회·비로그인 공개 조회 200, 위험 URL 400 및 기존 값 보존, 전체 삭제 및 재조회 확인. 기존 테스트 프로필·선택 정보·자기소개는 재시작 전에 보관한 후 복원했다.
- Safari 수동 UI 확인은 웹 페이지 재로드·자동화 오류로 완료하지 못했다. HTTP 검증을 브라우저 편집 검증으로 보고하지 않는다. 실제 PostgreSQL·운영 데이터 호환성은 미검증.

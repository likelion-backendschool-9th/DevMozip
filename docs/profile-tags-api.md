# 프로필 기술 스택·관심 분야 조회·수정

소개 탭의 기존 사이드 영역에 회원가입에서 저장한 기술 스택과 관심 분야를 표시한다. 기존 자기소개, 기본 정보 수정, 헤더, 사이드바와 외부 링크의 배치를 유지한다.

## 데이터와 선택지

- 기존 `users.skills`, `users.interests` JSON 컬럼을 재사용한다. 신규 컬럼·테이블·마이그레이션 및 ERD 변경은 없다. 운영 DB의 실제 스키마는 이번 작업에서 확인하지 않았다.
- `Skill`, `Interest` enum의 label이 저장 값과 검증 기준이다. 프론트에 별도 허용 값 상수를 만들지 않는다.
- 기존 `GET /api/v1/auth/signup/options`와 `signupOptionsAction`을 재사용한다.
- 과거 NULL은 조회 시 빈 배열로 응답한다. 두 목록을 모두 명시하는 PUT이며, 빈 배열은 해당 목록 전체 해제다. 필드 누락·NULL은 400이며 기존 저장 값을 삭제하지 않는다.
- 각 목록 최대 30개, 항목 최대 50자. 공백 제거·중복 제거 후 enum의 label과 정확히 비교한다. 등록되지 않은 직접 입력은 허용하지 않는다.

## API

| 메서드 | 경로 | 접근 |
| --- | --- | --- |
| GET | `/api/v1/users/{slug}/options` | 비로그인 가능, ACTIVE 계정만 공개 |
| GET | `/api/v1/users/me/options` | 인증된 본인, ACTIVE·WARNED |
| PUT | `/api/v1/users/me/options` | 인증된 본인, ACTIVE·WARNED |

요청·응답 형태:

```json
{"skills":["Java","Spring Boot"],"interests":["AI"]}
```

응답에는 이 두 목록만 포함한다. 이메일·권한·비밀번호는 반환하지 않는다. 소유자는 `Authentication.getName()`의 이메일로 기존 `MemberRepository.findByEmail()`을 호출해 결정한다. 본문의 ID·이메일로 소유자를 지정할 수 없다. 기존 인증 확인 API `/api/v1/users/me`는 변경하지 않는다.

공개 미존재·비공개 상태 계정은 동일한 404, 미인증은 401, 수정 불가 상태는 403, 유효하지 않은 선택 값은 400이다. 자기소개와 동일한 상태 정책을 따른다. `/me/options` 인증 규칙은 공개 경로 규칙보다 먼저 선언한다.

## 프론트 연결

- BFF: `/api/profiles/{slug}/options`, `/api/profiles/me/options`.
- 기존 `backendFetch`의 인증 쿠키 처리 및 오류 응답 정제를 재사용한다. 요청·응답의 필드를 제한하며 조회는 no-store다. 수정은 같은 출처의 JSON 요청만 허용한다.
- 본인 확인이 완료된 화면에서만 수정 버튼을 표시한다. 공개 프로필은 읽기 전용이다.
- 기술 스택·관심 분야의 수정 버튼은 두 목록을 함께 편집하는 모달을 연다. 기존 `ProfileTagEditor`의 선택 UI를 재사용하되 직접 입력은 숨긴다. 기존 Mock 화면의 직접 입력 동작은 유지한다.
- 선택지 조회 실패는 재시도를 제공하며 저장을 비활성화한다. 저장 실패 시 선택 내용과 오류를 유지한다. 성공 응답으로 화면을 갱신한다. 새로고침하면 API에서 다시 조회한다.
- 로딩·조회 실패·등록된 정보 없음은 구분한다. 프로필 변경 시 이전 조회를 취소한다.
- 외부 링크는 아직 API에 연결하지 않았다.

## 검증

- `frontend`: `npm test` — 543개 통과. `npm run build` — 통과.
- `backend`: `./gradlew test --console=plain` — 194개 중 193개 통과, Docker/PostgreSQL 컨텍스트 테스트 1개 건너뜀.
- 새 H2/JPA/MockMvc 통합 테스트 14개: 기존 저장 값 공개 조회, NULL 정규화, 저장 후 재조회, 전체 해제, 중복·공백 정리, 다른 회원·기본 필드 보존, 인증 경로 우선순위, 계정 상태, enum 미등록 값·누락·NULL·개수 제한 검증.
- 프론트 신규 테스트 18개: 공개·본인 통신 구분, BFF 필드 제한·출처 검사·오류 전달, 잘못된 응답 거절, 직접 입력 UI 분리.
- 실제 PostgreSQL, 브라우저 수동 조작 및 운영 환경 검증은 미실행.

## 명칭 구분

사용자 아이디인 `Member.tag`와 구분하기 위해 백엔드 DTO·Service·Controller·통합 테스트는 `ProfileOptionsRequest`, `ProfileOptionsResponse`, `ProfileOptionsService`, `ProfileOptionsController`, `ProfileOptionsIntegrationTest`로 명명한다. 저장 필드는 기존 skills/interests이며 사용자 아이디의 필수 입력 정책은 변경하지 않는다. 백엔드 API 및 프론트 BFF·호출 경로도 `/options`로 통일한다. 응답은 선택지 전체가 아닌 해당 회원이 저장한 skills/interests이며, 허용 선택지 전체는 기존 회원가입 options API에서 가져온다.

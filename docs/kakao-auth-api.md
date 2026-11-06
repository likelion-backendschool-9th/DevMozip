# 카카오 인증 API

카카오 로그인·가입·연동과 비밀번호 설정의 계약이다. 환경변수는 [환경 준비 안내](setup-after-ai.md)를 참조한다.

## 구조

브라우저는 BFF만 호출하고, 카카오와의 통신은 백엔드가 맡는다.

| 구분 | 내용 |
|---|---|
| 회원 식별 | 카카오 회원번호(`https://kapi.kakao.com/v2/user/me` 응답의 `id`)를 `users.kakao_id`에 저장한다. 서비스의 회원 ID(`users.id`)와 별개다. |
| 카카오 토큰 | 요청을 처리하는 동안만 쓰고 저장하지 않는다. |
| 로그인 상태 | 카카오 인증을 마치면 이메일 로그인과 같은 DevMozip JWT를 발급한다. |
| 설정이 없을 때 | 카카오 API만 503으로 응답하고 다른 인증은 그대로 동작한다. |

## BFF

| 항목 | 내용 |
|---|---|
| `GET /api/auth/kakao/callback` | 카카오 콘솔에 등록하는 Redirect URI. `state`를 대조하고 시작한 의도대로 백엔드를 호출한 뒤 쿠키를 저장하고 이동한다. |
| `startKakaoAction(intent, next)` | 서버 액션. `state`를 쿠키에 저장하고 인가 주소를 돌려준다. `intent`는 `login`, `signup`, `link`, `unlink`다. |
| `signupAction(input, { kakao: true })` | 서버 액션. 가입 토큰 쿠키를 붙여 비밀번호 없이 가입하고 바로 로그인한다. |
| `kakaoAccountStatusAction()` | 서버 액션. 연동 상태와 비밀번호 설정 여부를 돌려준다. |
| `setPasswordAction(input)` | 서버 액션. 비밀번호 없는 카카오 회원의 첫 비밀번호를 저장한다. |

쿠키는 모두 HttpOnly·SameSite=Lax다.

| 쿠키 | 내용 | 수명 |
|---|---|---|
| `devmozip_kakao_flow` | `state`, 의도, 이동할 경로 | 10분, 콜백에서 삭제 |
| `devmozip_kakao_signup` | 가입 토큰 | 30분, 가입하면 삭제 |

콜백은 결과를 `?kakao=` 코드로 화면에 알리고, 문구는 화면이 코드로 정한다.

| 시작 의도 | 결과 화면 | 코드 |
|---|---|---|
| `login` | `/login` (성공하면 `next` 경로) | `cancelled`, `invalid-state`, `failed`, `suspended`, `unavailable` |
| `signup` | `/signup` | 위 코드와 `signup`, `already-registered` |
| `link`, `unlink` | `/settings/privacy` | `linked`, `unlinked`, `conflict`, `unlink-blocked`, `cancelled`, `invalid-state`, `failed`, `unavailable` |

## 백엔드 API

| 메서드·경로 | 인증 | 요청 | 성공 응답 |
|---|---|---|---|
| `GET /api/v1/auth/kakao` | 없음 | `state` | 200 `{authorizeUrl}` |
| `GET /api/v1/auth/kakao/callback` | 없음 | `code`, `intent`(`login` 기본값, `signup`) | 200 `{status, ...}` |
| `POST /api/v1/auth/signup` | 없음 | 가입 항목, 선택 `kakaoSignupToken` | 201 |
| `GET /api/v1/users/me/social-accounts` | 필요 | 없음 | 200 `{kakaoLinked, passwordSet}` |
| `POST /api/v1/users/me/social-accounts/{providerId}` | 필요 | `{code}` | 204 |
| `DELETE /api/v1/users/me/social-accounts/{providerId}` | 필요 | `code` | 204 |
| `PATCH /api/v1/users/me/password` | 필요 | `{newPassword, confirmPassword}`. 비밀번호가 있는 회원은 `currentPassword`도 필수 | 204 |

`providerId`는 제공자 이름 `kakao`다. 대상 회원은 항상 토큰의 주인이며, 요청 값으로 회원이나 카카오 회원번호를 지정할 수 없다.

### 콜백 응답

| `status` | 상황 | 함께 오는 값 |
|---|---|---|
| `LOGIN` | 등록된 회원이 `login`으로 시작 | `accessToken`, `refreshToken`, `tokenType` |
| `SIGNUP_REQUIRED` | 등록되지 않은 카카오 계정 | `kakaoSignupToken` |
| `ALREADY_REGISTERED` | 등록된 회원이 `signup`으로 시작 | 없음. 로그인시키지 않는다. |

### 회원가입

| 구분 | 이메일 가입 (토큰 없음) | 카카오 가입 (토큰 있음) |
|---|---|---|
| 비밀번호 | 필수 | 받지 않음. 함께 보내면 400 |
| `password_hash` | 저장 | 비움 |
| `kakao_id` | 비움 | 가입 토큰에서 확인한 카카오 회원번호 저장 |
| 응답 | `{id, email, nickname}` | 같은 값과 `accessToken`, `refreshToken`, `tokenType` |

가입 토큰은 `type=kakao_signup`인 JWT(30분)이며 API 인증이나 토큰 재발급에 쓸 수 없다.

### 연동·해제·비밀번호 설정

| 동작 | 허용 조건 |
|---|---|
| 연동 | 비밀번호 유무와 관계없다. 이미 연동한 회원이거나 다른 회원이 쓰는 카카오 계정이면 409다. |
| 해제 | 비밀번호가 있어야 한다. 연동한 카카오 계정으로 다시 인증하면 카카오 연결을 끊고 `kakao_id`를 비운다. |
| 비밀번호 설정 | `kakao_id`가 있고 `password_hash`가 없으면 현재 비밀번호 없이 저장한다. 규칙은 가입과 같다. 비밀번호가 있는 회원의 변경은 [비밀번호 재설정·변경 API](password-reset-api.md)를 따른다. |

### 실패 응답

| 상태 | 상황 |
|---|---|
| 400 | 카카오가 인가 코드를 거절함, 알 수 없는 `intent`, 입력 규칙 위반 |
| 401 | 가입 토큰 만료·위조, 로그인 필요 |
| 403 | 정지된 회원 |
| 404 | 지원하지 않는 `providerId` |
| 409 | 이메일·닉네임·카카오 계정 중복, 비밀번호 없는 회원의 해제, 다른 카카오 계정으로 인증한 해제, 설정할 수 없는 계정의 비밀번호 설정 |
| 502 | 카카오 서버 연결 실패, 필요한 값이 없는 카카오 응답 |
| 503 | 카카오 환경변수 미설정 |

## 제한

- 사용자가 카카오 계정 페이지에서 직접 연결을 끊어도 웹훅이 없어 `kakao_id`는 남는다.
- 카카오 회원번호는 개인정보이므로 회원 탈퇴를 구현할 때 함께 파기해야 한다.

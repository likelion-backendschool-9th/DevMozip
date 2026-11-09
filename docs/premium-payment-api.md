# 프리미엄 구독·결제 API

WBS 13.0 프리미엄/결제와 17.4~17.6 개인정보 설정의 결제 정보에 쓰는 계약이다. 토스페이먼츠·카카오페이는 포트원 V2 채널로 연결하며, 백엔드는 포트원 API만 호출한다. 단계별 계획과 결정 근거는 [구현 계획](premium-payment-plan.md)에 있다.

## BFF

브라우저는 Next.js Route Handler만 호출한다. 공통 처리는 [lib/subscription/server.ts](../frontend/lib/subscription/server.ts)에 있다. 사용자 ID는 백엔드로 보내지 않고, 백엔드는 쿠키의 액세스 토큰으로 요청자를 정한다.

| BFF 경로 | 백엔드 | 내용 |
|---|---|---|
| `GET /api/subscription/plans` | `GET /api/v1/subscription/plans` | 공개 |
| `GET /api/subscription/me` | `GET /api/v1/subscription/me` | |
| `POST /api/subscription/cancel` | `POST /api/v1/subscription/cancel` | 같은 사이트 확인, 시간 제한 25초 |
| `POST /api/payments/checkout` | `POST /api/v1/payments/checkout` | `planId`·`provider`만 넘긴다 |
| `POST /api/payments/confirm` | `POST /api/v1/payments/confirm` | PC 결제창 결과. 성공하면 토큰 재발급, 시간 제한 45초 |
| `GET /api/payments/callback` | `POST /api/v1/payments/confirm` | 모바일 결제창의 결과 주소(`redirectUrl`). 성공하면 토큰 재발급 후 `/subscription/manage?payment=success`, 실패·취소면 `/subscription/checkout?payment=<코드>`로 이동 |
| `GET /api/payments/me` | `GET /api/v1/payments/me` | `page`·`size` 범위 확인 |
| `GET /api/payment-methods/me` | `GET /api/v1/payment-methods/me` | 204를 그대로 전달 |

- 같은 사이트 확인: 쓰기 요청의 `Origin` 호스트를 `X-Forwarded-Host`(없으면 `Host`)와 비교한다. standalone 서버의 `request.url`은 실제 주소와 관계없이 `localhost`로 만들어지므로 쓰지 않는다.
- 토큰 재발급: 결제 직후의 액세스 토큰에는 이전 역할이 남아 있다. 승인에 성공하면 리프레시 쿠키로 새 액세스 토큰을 받아 쿠키를 바꾼다. 재발급은 DB의 역할(`PREMIUM`)을 읽는다.
- 결과 코드: URL에는 코드만 싣고 문구는 화면이 정한다. `cancelled`(결제창 취소), `failed`(400·500), `unknown`(502, 결제 결과 미확인), `unavailable`(503), `already-subscribed`(409), `not-allowed`(403), `expired`(404), `login`(401).
- 포트원 SDK: 결제 페이지에서 `https://cdn.portone.io/v2/browser-sdk.js`를 불러온다. PC는 IFRAME 결제창의 결과를 받아 `/api/payments/confirm`으로 보내고, 모바일은 결제창이 `/api/payments/callback`으로 이동한다.

## 백엔드 API

| WBS | 메서드·경로 | 인증 | 요청 | 성공 응답 |
|---|---|---|---|---|
| 13.1 | `GET /api/v1/subscription/plans` | 없음 | 없음 | 200 `[{id, name, price, currency, billingCycle, featureLimits}]` |
| 13.2 (WBS 외 추가) | `POST /api/v1/payments/checkout` | 필요 | `{planId, provider}` | 201 `{orderId, provider, storeId, channelKey, billingKeyMethod, issueName, customerId, redirectUrl}` |
| 13.2, 15.3 | `POST /api/v1/payments/confirm` | 필요 | `{orderId, billingKey}` | 200 구독 상태 |
| 13.3 | `GET /api/v1/subscription/me` | 필요 | 없음 | 200 구독 상태 |
| 13.4 | `POST /api/v1/subscription/cancel` | 필요 | 없음 | 200 구독 상태(`CANCEL_REQUESTED`) |
| 13.5, 17.4 | `GET /api/v1/payments/me` | 필요 | `page`(0부터, 기본 0), `size`(1~100, 기본 20) | 200 `{items, page, size, hasNext}` |
| 17.5 | `GET /api/v1/payment-methods/me` | 필요 | 없음 | 200 `{provider, display}`, 없으면 204 |

- 요청자는 인증 정보(액세스 토큰의 이메일)로만 정한다. 사용자 ID와 금액은 요청으로 받지 않는다. 금액은 상품 가격으로 서버가 정하며, 요청 본문에 `amount`를 넣어도 쓰지 않는다.
- `provider`: `TOSS_PAYMENTS`·`KAKAO_PAY`. `billingKeyMethod`는 각각 `CARD`·`EASY_PAY`다.
- 금액은 원 단위 정수다. 시각은 한국 시간대가 붙은 ISO-8601(예: `2026-11-09T12:00:00+09:00`)이다.

### 구독 상태

`{status, premium, plan, startsAt, currentPeriodEnd, nextBillingAt, cancelRequestedAt, endedAt, paymentMethod}`

| status | 의미 |
|---|---|
| `NONE` | 구독한 적이 없다 |
| `ACTIVE` | 이용 중. `nextBillingAt`에 매월 결제한다 |
| `RENEWING` | 이번 달 결제를 처리하고 있다 |
| `CANCEL_REQUESTED` | 해지 예약. `currentPeriodEnd`까지 이용한다 |
| `EXPIRED` | 해지 예약 후 이용 기간이 끝났다 |
| `CANCELED` | 매월 결제가 실패해 구독이 취소됐다 |

- `status`는 가장 최근 구독의 상태다. `premium`은 현재 역할이 `PREMIUM`인지이며, 결제 기록 없이 `PREMIUM`인 계정은 `NONE`이면서 `premium: true`다.
- `nextBillingAt`은 `ACTIVE`일 때만 있다. `paymentMethod`는 이용 기간 안의 구독에만 담는다.

### 결제 준비와 승인

1. `checkout`이 주문 번호(`orderId`)와 구매자 ID(`customerId`)를 만들고, 회원·상품·금액과 함께 결제 대기 정보로 15분 동안 보관한다.
2. 화면이 포트원 브라우저 SDK `PortOne.requestIssueBillingKey()`를 부른다. `orderId`를 `issueId`로, `customerId`를 `customer.customerId`로 넘긴다. 모바일은 `redirectUrl`(`{DOMAINNAME}/api/payments/callback?orderId=...`)로 돌아온다.
3. `confirm`이 결제 대기 정보를 꺼내면서 지운다. 같은 주문의 두 번째 요청과 다른 회원의 요청은 404다.
4. 포트원에 빌링키를 조회해 `status`가 `ISSUED`이고, `issueId`가 이 주문 번호이며, 채널 키가 고른 결제 수단의 채널인지 확인한다.
5. 포트원 `POST /payments/{orderId}/billing-key`로 첫 달을 결제한다.
6. 회원 행을 잠그고 다시 확인한 뒤 구독(`ACTIVE`), 결제(`PAID`), 결제 수단(빌링키 암호화)을 저장하고 역할을 `PREMIUM`으로 바꾼다. 액세스 토큰에는 이전 역할이 남아 있으므로 BFF가 토큰을 재발급한다.

실패할 때의 처리는 다음과 같다.

| 상황 | 처리 |
|---|---|
| 다른 주문으로 발급한 빌링키 | 400. 우리 주문의 빌링키가 아니므로 삭제하지 않는다 |
| 다른 채널·삭제된 빌링키 | 400. 빌링키를 삭제한다 |
| 결제대행사 거절 | 400. 빌링키를 삭제하고 구독을 만들지 않는다 |
| 결제 결과를 알 수 없음(응답 없음·서버 오류) | 502. 결제 취소를 시도하고 빌링키를 삭제한다 |
| 결제 후 다른 결제가 먼저 끝남 | 409. 이번 결제를 취소하고 빌링키를 삭제한다 |
| 결제 후 저장 실패 | 500. 결제를 취소하고 빌링키를 삭제한다 |

### 해지

회원을 잠그고 `ACTIVE` 구독을 `CANCEL_REQUESTED`로 바꾼 뒤 결제 수단을 비운다. 커밋한 뒤 포트원 빌링키를 삭제하며, 삭제가 실패해도 해지는 유지된다(스케줄러가 해지 예약 구독을 결제하지 않는다). 역할은 이용 기간이 끝날 때 `USER`로 바뀐다.

### 오류 응답

기존 `ErrorResponse` 형식이다.

| 상태 | 상황 |
|---|---|
| 400 | 입력 오류, 판매하지 않는 상품, 지원하지 않는 결제 수단, 확인에 실패한 빌링키, 결제대행사 거절, 페이지 범위 |
| 401 | 로그인 필요 |
| 403 | `USER`·`PREMIUM`이 아닌 역할(관리자 등), 정지 회원 |
| 404 | 없거나 만료된 결제 대기 정보, 다른 회원의 주문 |
| 409 | 이미 이용 중(`PREMIUM` 역할 또는 이용 중 구독, 해지 예약 포함), 해지할 구독 없음, 이미 해지 예약됨, 이번 달 결제 처리 중 |
| 500 | 결제 후 저장 실패(결제는 취소함) |
| 502 | 포트원 연결 실패, 결제 결과를 알 수 없음 |
| 503 | 결제 수단의 설정(채널 키 등)이 없음 |

## 환경변수

`.env`(compose)와 `.env.local`(`bootRun`의 local 프로필)에 둔다. 값이 없어도 서버는 뜨고 설정이 없는 결제 수단의 결제만 503이다.

| 변수 | 용도 |
|---|---|
| `PORTONE_STORE_ID` | 포트원 상점 ID. 브라우저 SDK에 쓰는 공개 값 |
| `PORTONE_API_SECRET` | 포트원 V2 API Secret. 비밀값 |
| `TOSS_CHANNEL_KEY`, `KAKAOPAY_CHANNEL_KEY` | 포트원 채널 키. 공개 값 |
| `PAYMENT_ENCRYPTION_KEY` | 빌링키 암호화 키(base64 32바이트). 직접 만들며 바꾸면 저장된 빌링키를 읽을 수 없다 |
| `DOMAINNAME` | 모바일 결과 주소. 기존 변수 |

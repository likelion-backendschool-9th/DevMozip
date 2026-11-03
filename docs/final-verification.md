# 최종 검증 및 디자인 정리

검증일: 2026-10-30, Asia/Seoul. PC 1440×1000, Node.js 24.22.1, Playwright 1.64.1의 Chromium.

## 결과

**프론트엔드 프로덕션 빌드만 실행하여 A~E 핵심 흐름을 실제 브라우저에서 시연했습니다.** 백엔드·결제·AI·WebSocket 연결은 추가하지 않았습니다.

| 검사 | 실제 결과 |
|---|---|
| `frontend/npm run build` | TypeScript 및 Vite 성공, 빌드 경고 없음 |
| `frontend/npm test` | 21개 파일, 279개 테스트 통과 |
| 프로덕션 미리보기 | `npm run preview -- --port 4173 --strictPort` 실행 |
| 실제 브라우저 | 48개 검사 모두 통과 |
| 1440px 화면 | 경로/탭/빈 상태/권한 조건 37개 검사, 문서 가로 너비 모두 1440px |
| 브라우저 콘솔 오류 | 0건 |
| 페이지 런타임 오류 | 0건 |
| 실패한 네트워크 요청 | 0건 |
| XHR/fetch 비즈니스 요청 | 0건 |
| 소스 점검 | `fetch`, `XMLHttpRequest`, `WebSocket`, `EventSource`, Axios 호출 및 공통 API import 없음 |
| 백엔드 | 변경 없음, 이번 프론트 단계에서 빌드/실행 재검증하지 않음 |

실제 경로·제목·너비·각 검사 결과는 [원본 JSON](artifacts/final-verification/report.json)에 있습니다. 기본 Mock 37개 화면은 빈 `main` 여부와 가로 넘침을 검사하고 주요 화면은 스크린샷을 저장했습니다. 모든 임의 입력/브라우저/해상도의 무결함을 의미하지 않습니다.

## A~E 실사용 흐름

| 흐름 | 검증한 동작 | 결과 |
|---|---|---|
| A | 추천 입력/추가 조건 → 추천 카드 → 프로필 → 쪽지의 협업 제안 → 전송 → 새로고침 | 통과 |
| B | 프로젝트 검색 → Pocket 상세 → 포트폴리오 지원 → 주최자 전환/승인 → 팀원 → 채팅 전송 → 지원자 전환 후 메시지 확인 | 통과 |
| C | TypeScript 게시글 → 좋아요 → 댓글 → 새 퍼가기 → 내 글/원본 작성자 → 새로고침 | 통과 |
| D | Pocket 종료 → 실제 승인 협업자의 5점/키워드 리뷰 → 버블 표시 → 온도 36.5→37.5°C → 새로고침 | 통과 |
| E | 무료 공고 작성 차단 → 같은 계정 데모 구독 활성화 → 유효 기간/직군/기술 공고 작성 → 상세/새로고침 | 통과 |

추가 검사: 알림 전체 읽음/안 읽음·헤더 개수, SNS 가로 슬라이드·빈 검색 결과, 긴 프로필 문구·긴 모달 스크롤·Escape·5개 탭 상단 유지, 전체 데모 초기화·계정 3종 전환, 손상된 localStorage 복원, 저장소 읽기/쓰기/삭제 차단 및 용량 오류 상태의 세션 내 메시지 전송.

## 디자인 및 수정

- 외부 글꼴이 없는 Chromium에서 한글이 사각형으로 표시되어 **Pretendard Variable을 로컬 자산으로 포함**했습니다. 약 2.06MB이며 [OFL 라이선스](../frontend/src/assets/fonts/OFL.txt)를 함께 제공합니다. 글꼴 CDN 요청 없이 표시합니다.
- `presentation.css`에서 공통 글꼴·보조 글자 크기·메신저 대비·버튼/입력/카드/모달 기준을 정리했습니다. 화이트·연회색과 보라·시안 포인트를 유지합니다.
- 헤더 요소 축소·검색 최소 너비, 카드/긴 문자열 줄바꿈, 가로 탭 스크롤, 모달 최대 높이/내부 스크롤, 버튼/토스트의 긴 문구를 보완했습니다. 기존 의도된 피드 요약/미리보기 말줄임은 유지합니다.
- 동적 상세 페이지가 모두 “화면 준비”로 표시하던 문서 제목을 수정했습니다. 정적 `new`/동적 상세/수정/404 제목을 8개 추가 테스트로 검증합니다.
- 관리자 댓글 삭제의 브라우저 기본 확인 창을 기존 공통 모달로 통일했습니다.
- 지원 취소 안내의 “채팅 연동 준비” 문구를 현재 구현인 채팅방 멤버로 정리했습니다.

## 스크린샷

- [메인](artifacts/final-verification/home.png), [프로필](artifacts/final-verification/profile.png), [메신저](artifacts/final-verification/flow-a.png)
- [프로젝트 채팅](artifacts/final-verification/flow-b.png), [퍼간 글](artifacts/final-verification/flow-c.png)
- [리뷰·버블·온도](artifacts/final-verification/flow-d.png), [구독 후 작성 공고](artifacts/final-verification/flow-e.png)
- [프리미엄](artifacts/final-verification/premium.png), [관리자](artifacts/final-verification/admin.png), [없는 경로](artifacts/final-verification/404.png)

## 변경 파일

- `frontend/src/styles/presentation.css` 신규: 모든 화면의 발표용 통합 보정.
- `frontend/src/assets/fonts/PretendardVariable.woff2`, `OFL.txt` 신규: 한글 로컬 표시 및 라이선스.
- `frontend/src/main.tsx`: 통합 스타일 적용.
- `frontend/src/services/routeService.ts` 신규, `components/layout/AppLayout.tsx`, `app/router/routes.test.ts`: 실제 경로별 제목과 정적 경로 우선순위.
- `frontend/src/features/admin/AdminContent.tsx`: 공통 댓글 삭제 모달.
- `frontend/src/features/project/ApplicationModal.tsx`: 채팅 안내 문구.
- `scripts/verify-presentation.mjs` 신규: 격리 Chromium의 화면/흐름/오류/요청 검사와 결과 저장.
- `docs/artifacts/final-verification/`: 실제 스크린샷 14장과 JSON 결과.
- `README.md`, `docs/frontend-pages.md`, `docs/demo-scenarios.md`, `docs/api-plan.md`, `docs/decisions-pending.md`, `docs/validation.md`, 본 보고서: 현재 완료 범위와 실행/검증/향후 작업 구분.

## 재현

프론트엔드를 빌드하고 미리보기를 실행한 상태에서 저장소 루트에서 수행합니다. Playwright는 검증 도구로 `/tmp`에 별도 설치했으며 프로젝트 실행 의존성에는 추가하지 않았습니다. 최초 도구/Chromium 설치에는 네트워크가 필요합니다.

```bash
npm install --prefix /tmp/devmozip-browser --no-audit --no-fund playwright
PLAYWRIGHT_BROWSERS_PATH=/tmp/devmozip-browser/browsers \
  /tmp/devmozip-browser/node_modules/.bin/playwright install chromium

PLAYWRIGHT_BROWSERS_PATH=/tmp/devmozip-browser/browsers \
DEVMOZIP_PLAYWRIGHT_MODULE=/tmp/devmozip-browser/node_modules/playwright/index.mjs \
DEVMOZIP_BASE_URL=http://localhost:4173 \
  node scripts/verify-presentation.mjs
```

격리된 브라우저 컨텍스트에서만 저장/초기화를 수행하므로 발표자의 일반 브라우저 데이터는 변경하지 않습니다. 기본 출력은 `docs/artifacts/final-verification`이며 `DEVMOZIP_VERIFY_OUTPUT`으로 바꿀 수 있습니다. 현재 테스트의 Pocket 시연은 2026-11-02 마감 전 기준입니다. 이후 실행은 모집 기간/테스트 대상의 갱신이 필요합니다.

## 중간 실패 및 한계

- 초기 화면 검사는 lazy 화면의 제목이 나타나기 전에 판정해 실패했습니다. 실제 `main h1`을 기다리도록 검증 코드를 수정했습니다.
- 일부 흐름 검사는 정확한 label 문자열/화면 전환 대기를 잘못 지정하거나 초기부터 이미 퍼간 글을 선택해 실패했습니다. 실제 화면과 초기 데이터에 맞게 선택·대기·새 퍼가기 대상을 고친 후 최종 전체 48개가 통과했습니다.
- 브라우저 초기 `about:blank`에서 검사용 저장소 주입이 일으킨 오류는 HTTP 문서에서만 주입하도록 고쳤습니다. 최종 콘솔/페이지 오류는 0건입니다.
- 포트 확인용 `ss`는 기본 샌드박스의 netlink 권한 제한으로 상태를 확인하지 못했습니다. 포트가 닫혔다고 보고하지 않습니다. 이번 작업에서 백엔드를 기동하지 않았으며 실제 브라우저 비즈니스 요청 0건으로 프론트 자체 실행을 확인했습니다.
- 화면 검증은 Chromium PC 1440×1000 기준입니다. Safari/Firefox/실제 모바일·발표자의 다른 PC 환경은 직접 검증하지 않았습니다.
- 실제 OAuth·인증·메일·AI·결제·DB·REST·실시간 서버·이미지 업로드·서버 인가는 의도적으로 미구현입니다. 브라우저 저장이 차단되면 새로고침 후 보존할 수 없습니다.
- 기존 공고 날짜와 리뷰 기간은 고정 초기 Mock입니다. 발표일 경과 시 [시연 안내](demo-scenarios.md)에 따라 연장/현재 날짜 신규 공고를 사용하세요.

현재 단계의 요청만 완료했으며 다음 개발 단계는 시작하지 않았습니다.

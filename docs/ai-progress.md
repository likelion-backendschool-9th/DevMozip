# AI 추천 개발 진행 현황

마지막 갱신: 2026-11-03. 새 채팅에서는 [AGENTS.md](../AGENTS.md), [기능 명세](ai-matching.md), [API 명세](ai-api-spec.md), 이 문서 순으로 읽고 실제 코드와 Git 상태를 다시 확인한다. 아래 예정 단계는 실행 승인이 아니다.

## 현재 상태

이번 요청은 문서 작성만이다. AI Controller·Service·Repository·DTO·Entity, 실제 추천 API·DB 연동·외부 AI 연결은 구현하지 않았다. 기존 JWT 인증·권한·헬스체크·배포 인프라는 AI 기능과 별개로 존재한다.

점검 당시 브랜치는 `feat/ai-conversation`이다. 작업 전부터 `frontend/next-env.d.ts` 변경이 있었으며 이번 작업에서 수정·되돌림·커밋하지 않는다. 새 채팅은 브랜치와 변경 상태를 재확인하고 팀 브랜치 규칙에 따라 develop 대상 검증을 진행한다. 최초 문서 작업에서는 브랜치 전환·병합·커밋·푸시를 하지 않았다. 후속 사용자 요청으로 현재 `feat/ai-conversation` 브랜치에 문서 4개만 커밋한다. 푸시·병합은 수행하지 않는다.

## 단계별 진행

세부 구현 단계·순서는 아직 사용자 확정 전이며 아래는 후속 작업을 나누기 위한 예정 구분이다.

| 단계 | 상태 | 완료 내용 / 진입 전 필요한 사항 |
|---|---|---|
| 사전 점검 | 저장소 기준 완료, DB 대조 미완료 | 버전·구조·인증 코드·Mock 정책 차이를 확인했다. ERD/DDL과 실제 DB는 미확인이다. |
| 맥락·정책 문서화 | 문서 작성 완료, 검증 결과는 아래 참조 | AGENTS 및 AI 문서 3개에 확정 정책·미확정 계약·진행 상태를 분리했다. |
| ERD·연관 도메인 계약 확인 | 미착수 | 실제 스키마, 사용자 ID, 프로필/기술/프로젝트/포트폴리오/구독 데이터 계약 확보 필요 |
| API 계약 정리 | 메서드·경로 반영, 세부 DTO 미확정 | 사용자 제공 API 10개의 메서드·경로·역할을 문서화했다. 상세 분석 경로는 `detailed-analysis`로 정정했다. JSON 계약·응답 상태는 TODO다. |
| API 해당 단계 구현 | 미착수 | 사용자가 첫 구현 범위를 지정해야 한다. |
| 추천 생성·사용량 처리 | 미착수 | 추천 기준·성공 기준·동시성·월 경계·멱등성 확정 필요 |
| 프리미엄 추가 분석 | 미착수 | 구독·포트폴리오 접근 계약과 AI 연결 승인 필요 |
| 프론트 연결·통합 검증 | 미착수 | 별도 요청 범위 확정 필요. 현재 Mock 흐름 보존 |

## 확인한 기반과 정책 차이

- `backend/build.gradle`: Spring Boot 4.1.2, Java toolchain 21, Spring Data JPA·Security·Redis 의존성.
- `backend/gradle/wrapper/gradle-wrapper.properties`: Gradle 9.8.2.
- `backend/src/main/resources/application.yaml`: PostgreSQL 연결 설정, `ddl-auto: none`, Redis·JWT 설정.
- `compose.yaml`·`compose.prod.yaml`: PostgreSQL 17, Redis 7-alpine. 실제 가동 버전을 조회한 결과는 아니다.
- 백엔드는 `com.devmozip.backend`와 `auth` 패키지가 중심이다. 업무 엔티티와 AI API는 확인되지 않았다.
- 저장소에서 ERD·DDL 및 AI 테이블 5개의 매핑 코드를 찾지 못했다. 실제 DB에 테이블이 없다고 단정하지 않는다.
- 인증 principal은 username 문자열이며 임시 인메모리 계정이다. 실제 사용자 ID·구독 상태와의 연결이 없다.
- 프론트 `matchingChatService.ts`에는 메시지 전송으로 Mock 추천과 차감을 실행하는 분기가 있다. 이번의 '추천 실행에서만 생성·차감' 정책과 차이가 있다.
- `docs/api-plan.md`의 `/matching/...` 후보와 이번 `/api/v1/ai` 기준은 다르다. 기존 초안과 Mock 필드를 확정 API로 옮기지 않는다.

## 이번 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `AGENTS.md` | AI 문서 읽기 순서, 단계별 승인 범위, 기존 구조·컨벤션, 팀 코드 경계와 검증·기록 규칙 |
| `docs/ai-matching.md` | 확정 정책, 입력·로그인 흐름, 직군·기술 스택·경험 기반 적합도, 등급·월 한도, 현재 화면 경로 확인과 데이터 TODO |
| `docs/ai-api-spec.md` | 사용자 제공 API 10개의 메서드·경로·동작, 인증·차감, 요청/응답의 논리 구조와 미확정 DTO·오류 계약 |
| `docs/ai-progress.md` | 사전 점검·진행 상태·변경 파일·검증·남은 문제·다음 작업 |

## 검증 결과

### 최초 문서 구성 시 검증

- 저장소 설정·문서·인증 코드·Mock 추천 코드 읽기 및 엔티티/ERD 관련 검색을 수행했다.
- 기본 PATH의 Windows npm으로 `npm run build`와 추천 관련 테스트를 시도했으나 `WSL 1 is not supported` 및 `Could not determine Node.js install directory` 오류로 실행되지 않았다. 설치된 Linux Node 경로 `/home/binac/.nvm/versions/node/v24.22.1/bin`을 PATH 앞에 두고 재실행했다. 프로젝트 설정 파일은 바꾸지 않았다.
- `frontend`에서 Linux Node로 `npm run build`: 실패(종료 코드 1). 컴파일은 성공했으나 TypeScript 단계에서 `Could not parse output from TypeScript's --showConfig.`가 발생했다. 전체 빌드 성공이 아니며 원인 해결은 이번 문서 작업 범위에서 진행하지 않았다.
- `frontend`에서 Linux Node로 `npm test -- lib/matching/matchingService.test.ts lib/matching/matchingChatService.test.ts lib/matching/homeService.test.ts`: 성공, 3개 파일·39개 테스트 통과. 기존 프론트 Mock 회귀 확인이며 백엔드 AI 검증이 아니다.
- 네 문서의 상대 Markdown 링크 12개 대상 파일 존재를 확인했다. `git diff --check`도 통과했다.
- 기존 `BackendApplicationTests.java`는 인증 관련 테스트 코드다. 이번에 실행한 결과가 아니며 AI 테스트가 아니다.
- ERD/실제 DB 스키마 비교, AI API 인증·추천·사용량·프리미엄·외부 AI 통합 테스트는 미수행이다. 대상 구현이 없다.

### 후속 URL/API 표 반영 시 검증

- 추가 변경은 `docs/ai-api-spec.md`, `docs/ai-matching.md`, `docs/ai-progress.md`의 문서 3개다. 기능 코드·라우트·인증 설정은 변경하지 않았다.
- 사용자 제공 API 10개의 메서드·경로·역할을 반영했다. `/ai/matching`과 현재 `/matching`의 차이 및 `detailed-analysi` 철자를 TODO로 남겼다. JSON 계약은 임의로 확정하지 않았다.
- API 표 10개 행 존재 및 세 문서의 로컬 링크 대상 검사를 통과했다. `git diff --check`도 통과했다.
- Linux Node 경로를 사용해 `frontend`의 `npm run build`를 재실행했으나 컴파일 후 동일한 TypeScript `--showConfig` 출력 파싱 오류로 실패했다(종료 코드 1). 빌드가 생성한 `next-env.d.ts`는 실행 직전 내용으로 복원했다.
- 기능 테스트는 이번 후속 문서 수정에서 재실행하지 않았다. 위 39개 테스트 통과는 최초 문서 구성 시 결과다. 백엔드 AI 구현·통합 검증은 여전히 미수행이다.

### 경로 확인·오타 정정 및 문서 커밋

- 현재 `frontend/app/ai/matching/page.tsx`와 화면 이동 링크에서 `/ai/matching`을 확인했다. 앞선 현재 경로 `/matching` 안내를 정정하고 경로 이전 TODO를 해소했다. 기능 코드는 수정하지 않았다.
- 사용자가 상세 분석 경로의 오타를 확인했다. 최종 경로는 `/api/v1/ai/recommendations/{id}/detailed-analysis`다.
- 요청·응답 JSON 필드와 오류 계약의 의미를 API 문서에 설명했다. 설명용 필드 예시는 확정 DTO가 아니며 세부 데이터 계약은 TODO로 유지한다.
- 사용자 요청에 따라 AGENTS.md 및 AI 문서 3개를 현재 브랜치에 커밋한다. 기존 `frontend/next-env.d.ts` 변경은 포함하지 않는다.
- 문서 로컬 링크, API 표 10개 행, 정정된 상세 분석 경로, 실제 `/ai/matching` 페이지 파일 존재 검사 및 `git diff --check`를 통과했다. 기능 테스트는 재실행하지 않았다.
- Linux Node로 `frontend`에서 `npm run build`를 재실행했다. 컴파일 후 기존과 동일한 TypeScript `--showConfig` 출력 파싱 오류로 실패했다(종료 코드 1). `next-env.d.ts`는 실행 직전 내용으로 보존했다. 이 실패는 미해결 상태이며 문서 커밋을 빌드 통과로 보고하지 않는다.

## 남은 문제와 다음 작업

1. ERD/DDL 위치와 실제 테이블·컬럼·관계를 확보한다. `ai_conversations`, `ai_messages`, `ai_recommendation_requests`, `ai_recommendations`, `ai_usages`는 사용자 제시 이름이며 검증 전이다.
2. 타 팀의 사용자 ID, 공개 프로필·기술 스택, 프로젝트, 포트폴리오 접근 권한, 구독 상태 제공 계약을 확인한다. 부족한 필드·엔티티는 추가 전에 보고한다.
3. 사용자가 첫 구현 단계의 범위를 지정한 뒤 제공된 API 표를 기준으로 해당 단계 DTO·오류 계약을 구체화한다. 화면은 현재 코드와 일치하는 `/ai/matching`, 상세 분석 API는 `/api/v1/ai/recommendations/{id}/detailed-analysis`로 확정되었다.
4. 자연어 병합 의미, 추천 비교 항목·가중치, 빈/부분 결과 성공 기준, 월 경계·구독 변경·동시 요청·재시도 정책을 필요한 단계 전에 확정한다.
5. 비로그인 초안 보존·로그인 후 복원/저장과 기존 Mock 정책 차이를 후속 연결 단계에서 검토한다.
6. 프론트 빌드의 TypeScript `--showConfig` 출력 파싱 실패 원인은 별도 확인이 필요하다. 이번에 성공으로 보고하지 않는다.

새 채팅에 전달할 요청 예시: “AGENTS.md와 docs/ai-matching.md, docs/ai-api-spec.md, docs/ai-progress.md를 먼저 읽고, 현재 코드·Git 상태를 확인한 뒤 이번에 지정한 단계만 진행하세요. TODO는 임의로 확정하지 말고 누락된 데이터 계약을 먼저 보고하세요.”

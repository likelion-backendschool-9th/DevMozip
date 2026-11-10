# 포트폴리오 공개 범위 변경 — #116

## 범위와 흐름

feat/116-portfolio-visibility는 미병합 업로드 #115(feat/113-portfolio-file-upload, 8079707)에서 분기했다. 기존 CRUD·업로드·Mock 화면을 보존하며 이 단계는 공개 범위 전환만 구현한다.

본인 상세의 공개 범위 선택 → 명시적 저장 → Next PATCH /api/portfolios/{id}/visibility → Spring Controller → PortfolioMutationService/Impl → 잠금·수정 버전·지원서 참조 확인 → Repository UPDATE → 최신 상세 응답.

## API

PATCH /api/v1/portfolios/{id}/visibility

```json
{
  "visibility": "PUBLIC",
  "expectedUpdatedAt": "2026-11-10T12:00:00.123456"
}
```

- 인증된 일반 회원도 본인 포트폴리오를 변경할 수 있다. ACTIVE/WARNED 기준은 기존 변경 API와 같다. 타인의 기록은 ADMIN도 변경할 수 없으며 미존재와 동일한 404.
- PUBLIC·PRIVATE enum만 허용. 다른 값·null은 400. 기존 수정 시각을 그대로 보내고, 과거 DB의 updated_at=NULL은 명시적 null로 전달한다.
- 성공 200 + 기존 상세 DTO. visibility와 updated_at만 변경하고 제목·본문·첨부·작성자·생성 시각은 유지한다.
- 행 잠금 뒤 expectedUpdatedAt을 비교한다. 다르면 409, 시각은 한국 시각 마이크로초 단위로 갱신한다. 이후 오래된 작성 초안 PUT도 409가 되어 범위 변경을 덮어쓰지 못한다.
- 현재 수정 버전에서 이미 같은 공개 값이면 수정 시각을 바꾸지 않고 현재 상세를 반환한다. 충돌/응답 유실 시 자동으로 재시도하거나 강제로 덮어쓰지 않는다.
- BFF는 같은 출처·JSON·실제 바이트 크기를 검사하고 visibility/expectedUpdatedAt만 전달한다. ID·소유권·응답 공개 값 불일치는 502.

## 제출 정책과 파일 권한

- 제출 이력이 확인되면 409로 변경을 보류한다. 제출 후 변경 허용 여부는 프로젝트 담당자 합의 전까지 임의 확정하지 않는다. 참조 저장소를 읽지 못하면 503으로 중단한다.
- 프로젝트 지원 구현도 같은 포트폴리오 행 잠금으로 공개 여부 확인과 제출을 처리해야 한다. 담당 PR 통합 시 동시 제출·변경 계약을 함께 검증해야 한다. 이 기능은 프로젝트 지원서·스냅샷·파일을 변경하지 않는다.
- 공개 전환 시 프로필의 공개 목록에 표시된다. 상세는 기존 프리미엄/본인 정책을 따른다.
- 비공개 전환 시 이후 타인 목록·상세 및 연결 파일의 신규 접근을 기존 조회/파일 권한에서 차단한다. 이미 다운로드한 파일까지 회수하는 기능은 아니다.
- 파일 삭제나 재업로드 없이 기존 연결을 유지한다.

## 화면

본인 상세 상단에 공개 범위 선택과 저장 버튼을 추가했다. 현재 값이면 저장 불가, 처리 중 선택·중복 요청 차단. 성공 시 응답으로 상세의 공개 표시·수정 버전 갱신 및 완료 안내. 실패 시 선택값을 유지하고 최신 내용 다시 불러오기 제공. 등록 화면의 초기 선택과 PUT의 공개 범위 유지 계약은 그대로다.

## 검증과 현재 실행 제한 (2026-11-10)

- 프론트 전체 1,246개 통과. 새 BFF 필드 제한·출처·ID·소유권·응답 범위·409, 요청 버전·자동 재시도 금지 및 본인 UI 조건 확인.
- 원래 backend gradlew test는 develop의 RecommendationSummaryRequest.java/public record RecommendationRequestSummary 불일치에서 compileJava 실패. 담당자 소스를 변경하지 않았다.
- 저장소 외부 임시 Gradle init에서 그 파일만 제외하고 동일 내용을 올바른 파일명으로 복사해 검증했다. 이 조건에서 실제 PostgreSQL 관련 50개(변경 27·조회 18·파일 5) 건너뜀 없이 통과. 일반 빌드·CI 성공을 뜻하지 않는다.
- 소유자 전환·공개 목록/타인 상세 차단, 잘못된 enum·타인·오래된 버전, 동일 값 no-op·NULL 시각, 제출 이력/저장소 미준비 보류, 동시 요청 200/409, 비공개 전환 후 대표 이미지 404를 검증했다.
- 최신 클래스가 반영된 로컬 개발 서버는 payments.currency의 실제 CHAR/bpchar와 Entity의 VARCHAR 기대 타입 불일치로 schema validate 부팅 실패. 공유 DB·결제 Entity를 임의 변경하지 않았으며 현재 기능의 실제 공유 DB 브라우저 전환은 미실행이다. 원본 컴파일/스키마 문제 해결 후 실계정 검증 필요.

# 프로필 연락 가능 시간 조회·수정

feat/profile-contact-hours는 미병합 feat/profile-update의 231a939에서 분기했다.
사용자가 ERD에 추가한 users.contact_hours VARCHAR(100) NULL을 기준으로 한다.
ERD와 운영 DB는 이 작업에서 변경하지 않았다.

## API 계약

- GET /api/v1/users/{slug}: 기존 공개 프로필 응답에 contactHours(문자열 또는 NULL) 추가.
- PUT /api/v1/users/me/profile: 기존 본인 수정 요청에 contactHours 선택 필드 추가.
- 수정 대상은 기존 Authentication.getName()의 이메일로 조회한다.
- 연락 시간은 자유 입력이다. 예: 평일 19:00~22:00 · 주말 협의.
- 앞뒤 공백 제거 후 최대 100자. 초과하면 400이며 다른 수정도 저장하지 않는다.
- 연락 시간 필드 누락 또는 NULL: 기존 저장 값 유지. 새 필드를 모르는 기존 클라이언트와 호환한다.
- 빈 문자열 또는 공백 문자열: 저장 값을 NULL로 지운다.
- 응답에서는 미등록을 NULL로 반환한다.

```json
{
  "nickname": "개발자",
  "tag": "developer-tag",
  "jobCategories": ["백엔드 개발자"],
  "summary": "함께 개발해요",
  "career": "2년 차",
  "collaborationStatus": "AVAILABLE",
  "contactHours": "평일 19:00~22:00 · 주말 협의"
}
```

연락 시간만 변경하는 별도 PATCH API가 아니다. 기존 PUT의 다른 필수 필드도 함께 보내야 한다.
다른 선택 필드의 기존 PUT 정책은 그대로이며, contactHours는 누락 시 유지하는 확장 필드다.

## 변경 파일

- entity/Member: contactHours 필드·컬럼 매핑 및 Builder 지원.
- dto/profile/ProfileUpdateRequest: 선택 입력, 100자 검증·공백 제거.
- dto/profile/ProfileDetailResponse: 공개 조회·수정 응답에 contactHours 포함.
- service/ProfileUpdateService: 기존 값 유지·변경·삭제 처리.
- 조회·수정 관련 단위/HTTP/DB 통합 테스트와 API 문서.

## 검증

H2 기반 실제 저장·재조회 및 공개 GET으로 연락 시간 응답을 검증한다.
100자 성공·101자 거부, 공백 제거, 누락/NULL 보존, 명시적 빈 문자열 삭제,
다른 회원 미변경 및 DB 커밋 실패 시 연락 시간을 포함한 롤백을 확인했다.
백엔드 전체 테스트 90개와 빌드, 프론트 npm run build가 통과했다.

## 남은 작업

실제 PostgreSQL에도 contact_hours VARCHAR(100) NULL 컬럼 적용이 필요하다.
ERD 변경과 실제 DB 스키마 변경은 별개이며 이 작업에서 ALTER TABLE을 실행하지 않았다.
인증 담당 브랜치와 병합할 때 Member의 새 필드를 함께 보존해야 한다.
프론트의 조회 BFF 허용 필드/타입·연락 시간 표시·수정 창 저장은 별도 프론트 연동 작업이다.
이후 사용자 요청에 따라 같은 브랜치에서 로컬 폴더 기반 이미지 업로드·교체·삭제를 추가했다. docs/profile-local-images.md를 참고한다.

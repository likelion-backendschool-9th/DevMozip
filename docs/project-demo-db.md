# 프로젝트 DB 데모 데이터

2026-11-05 사용자 요청으로 팀 PostgreSQL `cloud.newlecture.com:5442/devmozip`에 적용했다. 다른 도메인 테이블은 생성·수정하지 않았다.

## 현재 SQL 적용 순서

신규 DB는 project-schema.sql → review-keywords.sql → review-keyword-selections.sql → project-demo-data.sql 순서로 적용한다. reviews에는 selected_keywords 컬럼이 없고 선택값은 연결 테이블에 넣는다. 기존 DB의 JSONB 제거는 review-keyword-selections.sql로 이관한 뒤 reviews-normalize-keywords.sql로 일치 검증 후 처리한다. 샘플 데이터 SQL은 기존 데이터가 있는 팀 DB에 재실행하지 않았다.

## 적용 내역

| 테이블 | 삽입 건수 | 원본 |
|---|---:|---|
| projects | 8 | frontend/mocks/home.ts, project.ts |
| project_members | 21 | project.ts seedProjectMembers 우선, profile.ts collaborations 보완 |
| project_applications | 6 | projectApplicants.ts |
| reviews | 9 | reviewDemos.ts |

제출 포트폴리오 연결은 실제 portfolios 참조가 없어 넣지 않았다. 강퇴 투표·개인 투표 기록은 원본 초기 상태가 비어 있어 넣지 않았다. SNS·회원·인증·포트폴리오·채팅 테이블은 건드리지 않았다.

## 변환 기준

- [적용 SQL](sql/project-demo-data.sql). 단일 트랜잭션이며 동일 ID 재삽입 시 전체 실패한다. UPDATE/DELETE 및 시퀀스 조작은 없다.
- Mock 사용자 ID는 SQL 주석에 명시한 -910001~-910006 임시 ID로 매핑했다. 실제 회원이나 로그인 계정이 아니며 사용자 FK 결합 전 실제 회원 ID로 별도 연결해야 한다.
- 프로젝트·멤버·지원서·리뷰 ID는 사용자 정정 요청에 따라 양수로 변경했다. 실제 DB 자동 생성 시퀀스에서 ID를 발급했고 내부 FK도 함께 변경했다. 사용자 참조 ID만 임시 음수로 남긴다. 적용 SQL은 project-positive-ids.sql이며 원본 seed SQL도 양수 ID 및 시퀀스 보정으로 수정했다.
- 모집 직군·상태는 기존 백엔드 enum 코드로 변환했다. 날짜는 원본을 유지하여 일부 모집과 리뷰 기한은 이미 지났다. +09:00 시각은 서울 현지 timestamp without time zone으로 변환했다.
- 주최자는 HOST/job_role=NULL, 나머지는 PARTICIPANT로 등록했다. 모집 슬롯 capacity는 기존 Mock 슬롯의 모집 인원을 유지하며 청사진의 총 정원은 주최자 1명을 별도로 더한다.
- 원본에 없는 필수 대표 이미지는 기존 프론트 자산 `http://localhost:3000/portfolio-cover.svg`를 공통 데모 값으로 사용했다. 외부 접속 주소에 따라 교체가 필요하다.
- 원본 거절 지원서에 별도 사유 필드가 없어 원본 소개의 일정 불일치 내용을 한 줄 거절 사유로 보완했다.
- 종료 시각은 종료일 23:59:59, 리뷰 만료는 그 시각+30일, 날짜만 있는 리뷰 제출은 해당일 12:00:00으로 보완했다. 리뷰 토큰 해시는 작동하는 초대 링크가 아닌 데모 표식의 SHA-256 값이며 리뷰는 모두 제출 완료 상태다.

## 실제 검증

- INSERT 44건 및 COMMIT 성공. 사후 행 수: 8/21/6/9, 나머지 3개 테이블 0행.
- 주최자 멤버 누락·리뷰 프로젝트 소속 불일치/자기평가·지원 직군 불일치·리뷰 별점/제출 기한 오류 모두 0건.
- 프로젝트 상태는 모집 중 4개, 모집 완료 1개, 진행 중 1개, 종료 2개.
- DB 조회 검증만 수행했다. 프론트 Mock과 DB는 자동 동기화되지 않으며 실제 로그인 계정으로 프로젝트 수정·지원하는 통합 시연은 검증하지 않았다.
- 제품 소스 변경 없음. 직전 프론트 빌드의 TypeScript --showConfig 파싱 실패는 미해결이며 이번 SQL 데이터 삽입에서 재실행하지 않았다. Gradle 테스트는 미실행이다.

## 양수 ID 변경 검증

프로젝트 1~8, 팀원 1~21, 지원서 1~6, 리뷰 1~9로 변환했다. 데이터 44행과 내부 참조를 유지하며 하나의 트랜잭션으로 완료했다. 기존 음수 ID는 더 이상 사용하지 않는다. 회원 ID 매핑은 실제 회원 결합 시 필요하다.

## 2026-11-05 실제 회원 연결

사용자가 일반 회원 ID를 1로 변경하고 누락된 Mock 회원을 users에 추가하도록 요청했다. 실행 SQL은 `docs/sql/project-member-links.sql`이다. 이미 적용한 일회성 변경이며 재실행하지 않는다.

| 회원 | 현재 ID | 변경 |
|---|---:|---|
| 김하늘 | 1 | 기존 0에서 이동 |
| 이서준 | 5 | 기존 1과의 충돌을 피하려고 다음 시퀀스 ID로 이동 |
| 데브모집 관리자 | 2 | 유지 |
| 이유나 | 6 | Mock에서 추가 |
| 박민준 | 7 | Mock에서 추가 |
| 최서연 | 8 | Mock에서 추가 |
| 정지호 | 9 | Mock에서 추가 |

정지 회원(3), 기존 별도 회원(4)은 유지했다. 총 users 9행, 시퀀스 마지막 값 9다. 새 개발자는 기존 Mock의 직군·경력·기술·관심 분야·한 줄 소개·신뢰 온도를 저장했고 role=USER/status=ACTIVE다. 데모 이메일은 각 태그@devmozip.demo이며 기존 데모 계정의 BCrypt 해시를 SQL 내부에서 참조했다. 비밀번호 원문이나 해시는 새 SQL/문서에 넣지 않았다.

프로젝트 주최자 8개·팀원 21개·지원서 6개의 임시 회원 참조를 실제 users ID로 변환했다. 리뷰의 reviewer_member_id/reviewee_member_id는 project_members ID이므로 변경하지 않았다. AI의 기존 회원 참조 보존 UPDATE는 대상 0행이었다. 각 회원 참조의 미연결 건수 0, 프로젝트 8개 및 조회수 합 2215 유지 확인.

이전 seed/ID 전환 SQL과 음수 ID 설명은 당시 적용 기록이다. 현재 DB에는 위 실제 회원 연결이 적용되어 있으므로 이전 SQL을 다시 실행하지 않는다. DB 참조 연결과 별개로 ProjectCurrentUserProvider의 실제 인증 회원 공급 코드는 아직 변경하지 않았다.

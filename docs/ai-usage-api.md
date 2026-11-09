# STEP 7 — AI 월 사용량 API·동시성 계약

기준일: 2026-11-10. 구현은 현재 작업 트리 기준이다. 공유 DB 마이그레이션·배포와 실제 Gemini 검증은 별개다.

## API

`GET /api/v1/ai/usage`는 인증 정보로 현재 사용자를 식별한다. 사용자 ID를 받지 않는다. 응답은 `Cache-Control: no-store`이며 브라우저 BFF는 `GET /api/ai/usage`다.

```json
{
  "usageMonth": "2026-11",
  "usedCount": 4,
  "limit": 5,
  "remainingCount": 1,
  "pendingCount": 1,
  "availableCount": 0
}
```

| 필드 | 계약 |
|---|---|
| usageMonth | Asia/Seoul 기준 현재 YYYY-MM |
| usedCount | 해당 월 신규 추천 성공 횟수 |
| limit | 현재 DB 회원 역할·유효 구독으로 판정한 5 또는 100 |
| remainingCount | max(0, limit - usedCount) |
| pendingCount | 해당 월 처리 중인 예약 횟수 |
| availableCount | max(0, limit - usedCount - pendingCount) |

일반 회원은5회다. PREMIUM/ADMIN 역할과 유효 구독이 모두 있어야100회이며 관리자 무제한 예외는 없다. 구독 조회는6B와 동일하게 최신 starts_at/id 기록을 사용한다. 상태 ACTIVE/RENEWING/CANCEL_REQUESTED이고 starts_at <= 현재 KST < current_period_end이며 ended_at이 없거나 미래여야 한다. 유효 구독이 없으면5회다. 정지·이용 불가 계정은403, 미인증은401이다. 구독 저장소 오류를 무료 요금제로 숨기지 않는다.

구독 변경 시 같은 달 usedCount를 유지한다. 무료3회 후 가입하면3/100이다. 프리미엄20회 후 무료 전환이면20/5이며 잔여·실행 가능 횟수는0이다. 이미 유효하게 예약한 요청은 처리 중 구독이 끝나도 완료를 허용한다.

## 차감·멱등성

- 새 추천을 정상 완료하면 결과가0건이어도 요청당1회다. 개발자·프로젝트를 합쳐1회이며 한쪽 실패 시 전체 FAILED·부분 저장 롤백·미차감이다.
- 대화 생성·메시지·조건 변경·과거 결과 조회·상세 분석·추천 실패는 미차감이다.
- 기존 대화별 Idempotency-Key와 입력 비교·409 정책을 보존한다. 기존 SUCCESS/PENDING/FAILED의 재전송은 추가 예약·차감·계산을 만들지 않는다. quota 검사보다 기존 키 확인을 먼저 하므로 소진 후에도 기존 결과를 반환한다.
- FAILED의 실제 재실행은 새 키를 사용한다. 신규 요청의 한도429는 요청 행·예약을 만들지 않으며 프론트는 미생성 요청의 대기 키를 제거한다. 네트워크 응답 유실은 기존 키를 유지한다.
- 요청을 최초 처리할 때 기록한 KST 수신 시각으로 월을 고정한다. 사용량 행 준비·잠금 대기 이후 월이 바뀌어도 해당 수신 월에 귀속한다. 완료 시각의 월을 다시 계산하지 않는다. 기존 행을 초기화하지 않고 새 월 행을 사용한다.

신규 추천 실행에서 자리 부족은429다. 서버 응답은 다음 구조이며 remainingCount가 남아 있어도 pendingCount가 자리를 차지하면 같은 오류 코드로 처리 중임을 안내한다.

```json
{
  "status": 429,
  "code": "AI_MONTHLY_LIMIT_EXCEEDED",
  "message": "이번 달 추천 횟수를 모두 사용했습니다.",
  "usage": {
    "usageMonth": "2026-11",
    "usedCount": 5,
    "limit": 5,
    "remainingCount": 0,
    "pendingCount": 0,
    "availableCount": 0
  }
}
```

BFF는429와 code를 유지하고 내부 필드를 제외한 안내를 제공한다. 사용량 세부값은 별도 usage GET에서 읽는다. Gemini 메시지 분석의429는 기존 경로로 전달하고 월 추천 한도와 구별한다. 프론트는 서버 사용량을 조회·표시하며 로컬 역할/Mock 카운터로 대체하지 않는다. 마운트·계정/추천 처리 상태 변경·창 포커스·명시 새로고침에서 조회한다. 클라이언트 표시가 오래돼도 실행 허용 여부는 서버가 최종 판단한다.

## 스키마와 적용 경계

읽기 전용으로 확인한 공유 PostgreSQL17.11에는 ai_usages가 없고 ai_recommendation_requests의 ai_usage_id/reservation_expires_at도 없다. 제공된 사용량 ERD에는 pending_count가 없으므로 합의한 추가안이 필요하다. 원격 ERD 편집은 수행하지 않았다.

명시 적용 파일: [sql/ai-usages.sql](sql/ai-usages.sql). 공유 DB에는 실행하지 않았다. 회원·프로젝트·포트폴리오·구독 테이블은 변경하지 않는다. 과거 요청에 소급 차감하지 않는다.

| 테이블 | 적용 내용 |
|---|---|
| ai_usages | id/user_id/usage_month CHAR(7)/used_count/pending_count/created_at/updated_at. 사용자·월 UNIQUE, 카운터 음수 금지, users FK |
| ai_recommendation_requests | nullable ai_usage_id FK, nullable reservation_expires_at, 두 값의 동시 존재/부재 CHECK, PENDING 조회 인덱스 |

관계는 ai_usages 1:N ai_recommendation_requests의 **비식별관계**다. 기존 요청은 두 값이NULL이며 과거 PENDING에는 만료 정리를 소급 적용하지 않는다. 새 요청만 연결과 만료 시각을 기록한다. limit과 remaining/available은 컬럼이 아니다. 시각 컬럼은 기존 AI와 동일한 TIMESTAMP WITHOUT TIME ZONE이며 신규 사용량·예약 시간은 명시 KST로 계산한다.

배포 전에 DB 담당자가 SQL을 검토·적용하고 사용량 행·제약을 확인해야 한다. 기존 공통 ddl-auto 설정은 이번에 수정하지 않았다. Hibernate 자동 update만으로 SQL의 전체 제약·인덱스까지 반영됐다고 보지 않는다. 스키마 미적용 상태에서 새 서버를 기동하지 않는다.

## 트랜잭션과 예약 회복

1. 월 행 최초 생성은 별도 트랜잭션으로 처리한다. UNIQUE 충돌 시 해당 트랜잭션을 끝내고 재조회한다. 실패한 PostgreSQL 트랜잭션 안에서 계속 질의하지 않는다.
2. 접수는 대화 잠금 → 기존 키/입력 확인 → 월 사용량 행 잠금 → 기존 예약 확인 → 한도 검사 → PENDING 생성+pending 증가를 한 트랜잭션으로 수행한다.
3. 계산·Gemini 호출 중에는 DB 트랜잭션과 잠금을 유지하지 않는다.
4. 완료는 대화 → 월 사용량 → 요청 잠금 순서로 재검증한다. 만료되지 않은 PENDING만 결과 전체+SUCCESS+pending 감소+used 증가를 함께 커밋한다.
5. 실패는 원래 결과 트랜잭션을 롤백하고 별도 트랜잭션으로 FAILED+pending 감소를 기록한다. DB 장애로 실패 기록도 불가하면 예약은 이후 만료 정리로 회복한다.
6. 삭제는 같은 대화 잠금 아래 사용량 ID 순으로 예약을 반환한다. native 요청 삭제 전에 변경을 flush하고 삭제 대상 요청을 detach해 JPA 잔여 참조를 제거한다. 삭제 자체가 실패하면 예약 반환도 롤백된다. 기존 성공 used는 보존한다.

예약 수명 기본값은 **30분**, 설정은 `app.ai.usage.reservation-ttl`(양수 Duration, 예: PT30M)이다. 후보 전체 배치로 길어질 수 있는 기본 추천에6B의180초를 복사하지 않고, 현재 BFF300초보다 긴 보수적 초기값을 선택했다. 실제 후보 규모·지연은 미측정이므로 운영 조정이 필요하다. 이 값은 외부 호출의 전체 실행 제한이나 호출 취소를 의미하지 않는다.

정리는 usage GET·추천 POST·추천 결과 GET에서 사용자 전체 월을 대상으로 실행한다. 별도 스케줄러는 없다. 요청이 없으면 만료 행은 DB에 남아 있다가 다음 접근에서 정산한다. 각 월 사용량 행과 PENDING 요청을 잠그고 FAILED+예약 반환을 한 번만 커밋한다. 늦게 끝난 작업은 잠금 후 이미 완료/만료된 상태를 읽어 결과 저장·차감을 하지 않는다. 같은 키가 다시 실행되지는 않는다.

조회와 실행에서 계정 인증을 먼저 수행한다. 만료 정리는 대화 잠금을 새로 얻지 않고 사용량 → 요청 순서만 사용해 완료·삭제 경로와 순환 대기를 만들지 않는다. 여러 인스턴스가 같은 DB를 쓰더라도 기준은 DB 행 잠금이다. 외부 AI 호출의 exactly-once나 분산 환경 실증 완료를 주장하지 않는다.

테스트 명령·최종 결과와 한계는 [ai-progress.md](ai-progress.md)의 STEP7 기록을 따른다.

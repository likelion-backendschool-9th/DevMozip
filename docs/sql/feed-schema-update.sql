-- 피드 2단계(docs/feed-plan.md) 반영 전에 이미 만들어진 DB를 맞추는 검토/수동 적용용 SQL. 아직 어느 DB에도 실행하지 않았다.
-- ddl-auto: update는 기존 CHECK 제약과 인덱스 이름을 바꾸지 않으므로, 새 엔티티로 서버를 재시작하기 전에 실행한다.
-- 실행 대상: reports 또는 posts가 이미 있는 DB(공유 개발 DB, 운영 DB). 새로 만드는 DB에는 필요 없다.
-- 2026-11-10 공유 개발 DB 읽기 확인: reports 0행, reports_status_check가 RECEIVED·REVIEWING·RESOLVED·REJECTED만 허용.
--   posts 0행, 인덱스 이름이 feeds_*(feeds 테이블 이름 변경 전 이름)이다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

-- 1. 신고 초기 상태를 RECEIVED에서 AWATING으로 바꾼다 (Report.Status). 기존 RECEIVED 행도 AWATING으로 옮긴다.
ALTER TABLE public.reports DROP CONSTRAINT IF EXISTS reports_status_check;
UPDATE public.reports SET status = 'AWATING' WHERE status = 'RECEIVED';
ALTER TABLE public.reports ADD CONSTRAINT reports_status_check
    CHECK (status IN ('AWATING', 'REVIEWING', 'RESOLVED', 'REJECTED'));

-- 2. posts 인덱스 이름을 엔티티(@Index)와 같게 바꾼다. 이름이 다르면 Hibernate가 같은 인덱스를 하나 더 만든다.
ALTER INDEX IF EXISTS public.feeds_created_id_idx RENAME TO posts_created_id_idx;
ALTER INDEX IF EXISTS public.feeds_author_created_idx RENAME TO posts_author_created_idx;
ALTER INDEX IF EXISTS public.feeds_original_idx RENAME TO posts_original_idx;

COMMIT;

-- 위 합성 자료만 삭제한다. 사용자 요청으로 제거할 때만 명시 실행한다.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('devmozip-6b-portfolio-demo-v1'));
DELETE FROM portfolios p USING users u WHERE p.user_id=u.id
AND u.email IN ('member@devmozip.demo','premium@devmozip.demo','minjun@devmozip.demo','yuna@devmozip.demo','seoyeon@devmozip.demo','jiho@devmozip.demo')
AND p.title ~ '^\[6B 합성 데모 [0-9]{2}\]' AND p.description LIKE '[합성 데모 자료:%'
RETURNING p.id,p.user_id,p.title;
COMMIT;

-- 제출 이력을 유지한다. 기존 포트폴리오/지원 데이터를 변경하지 않는다.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM public.project_application_portfolios l LEFT JOIN public.portfolios p ON p.id=l.portfolio_id WHERE p.id IS NULL) THEN
  RAISE EXCEPTION '존재하지 않는 포트폴리오 참조를 먼저 확인해야 합니다.';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.project_application_portfolios'::regclass AND conname='fk_project_application_portfolio') THEN
  ALTER TABLE public.project_application_portfolios ADD CONSTRAINT fk_project_application_portfolio FOREIGN KEY(portfolio_id) REFERENCES public.portfolios(id) ON DELETE RESTRICT;
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_project_application_portfolio ON public.project_application_portfolios(portfolio_id);
COMMIT;

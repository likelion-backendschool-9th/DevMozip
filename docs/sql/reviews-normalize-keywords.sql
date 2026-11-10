-- 기존 환경용: 연결값이 완전히 일치할 때만 reviews.selected_keywords를 제거한다.
-- 적용 순서: review-keywords.sql → review-keyword-selections.sql → 이 파일.
BEGIN;
LOCK TABLE public.reviews IN ACCESS EXCLUSIVE MODE;
LOCK TABLE public.review_keyword_selections IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.review_keywords IN SHARE MODE;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='reviews' AND column_name='selected_keywords') THEN
  IF EXISTS (
   SELECT 1 FROM public.reviews r WHERE
    COALESCE((SELECT jsonb_agg(v.name ORDER BY v.name) FROM jsonb_array_elements_text(r.selected_keywords) v(name)), '[]'::jsonb)
    <> COALESCE((SELECT jsonb_agg(k.name ORDER BY k.name) FROM public.review_keyword_selections s JOIN public.review_keywords k ON k.id=s.keyword_id WHERE s.review_id=r.id), '[]'::jsonb)
  ) THEN RAISE EXCEPTION '원본 리뷰 키워드와 연결 값이 다릅니다. 삭제하지 않습니다.'; END IF;
  ALTER TABLE public.reviews DROP COLUMN selected_keywords;
 END IF;
END $$;
COMMIT;

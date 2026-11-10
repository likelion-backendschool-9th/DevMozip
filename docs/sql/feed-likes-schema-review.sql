-- 검토/수동 적용용. 2026-11-09 공유 개발 DB에 적용 완료. 재실행하지 않는다.
-- 선행: public.users, public.feeds가 있어야 한다.
-- 프로젝트용 공통 likes는 수정하지 않는다. post_likes를 중복 생성하지 않는다.
-- 최신 피드 설계대로 복합 PK를 사용하므로 별도 id/updated_at은 두지 않는다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE TABLE public.feed_likes (
    user_id BIGINT NOT NULL REFERENCES public.users(id),
    feed_id BIGINT NOT NULL REFERENCES public.feeds(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT feed_likes_pkey PRIMARY KEY (user_id, feed_id)
);

-- 회원별 최신 좋아요 목록 및 피드별 좋아요 수 조회.
CREATE INDEX feed_likes_user_created_idx
    ON public.feed_likes(user_id, created_at DESC, feed_id DESC);
CREATE INDEX feed_likes_feed_idx ON public.feed_likes(feed_id);

COMMENT ON TABLE public.feed_likes IS '피드 좋아요. 회원과 피드 조합은 한 번만 저장';
COMMENT ON COLUMN public.feed_likes.user_id IS '좋아요한 사용자 ID';
COMMENT ON COLUMN public.feed_likes.feed_id IS '좋아요 대상 feeds.id';
COMMENT ON COLUMN public.feed_likes.created_at IS '좋아요한 시각. 취소 후 재등록 시 새 시각';
COMMIT;

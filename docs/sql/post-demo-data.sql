-- 피드 데모 데이터 (PostgreSQL). frontend/mocks/home.ts·profile.ts의 Mock 게시글 8개와 퍼간 글 2개를 옮겼다. 실행 여부는 사용자가 정한다.
-- local(H2) 프로필용 같은 내용은 backend/src/main/resources/posts_data.sql(Git 제외)이다.
-- 작성자는 members_data.sql의 데모 회원을 이메일로 찾는다. 회원이 없으면 그 행은 넣지 않는다.
--   Mock demo-user·yuna·seoyeon → member@devmozip.demo (김하늘)
--   Mock demo-premium·minjun·jiho → premium@devmozip.demo (이서준)
-- 퍼간 글: Mock rp-haneul은 원본(yuna → member)이 퍼간 사람과 같아지므로 premium이 퍼간 글로 바꿨다. rp-yuna는 member가 퍼간 글이다.
-- 카테고리는 코드 값으로 바꿨다(정보 INFO, 피드백 FEEDBACK, 홍보 PROMO, 자유 COMMUNITY). 작성 시각·조회수는 mocks/social.ts의 postMetadata다.
-- 좋아요·댓글 수는 Mock 숫자일 뿐 실제 행이 없어 옮기지 않는다. 이미지 파일이 없으므로 image_urls는 비운다.
-- 같은 작성자·제목의 원본 글, 같은 사람의 같은 원본 퍼가기가 있으면 넣지 않으므로 여러 번 실행해도 된다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'INFO', 'React 컴포넌트, 어디까지 분리하시나요?',
       '최근 사이드 프로젝트를 진행하며 정리한 컴포넌트 설계 기준을 공유합니다. 재사용성과 가독성 사이에서 고민하고 있다면 함께 이야기해요.',
       '["React", "컴포넌트 설계"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 180, 'VISIBLE', TIMESTAMP '2026-10-29 10:00:00', TIMESTAMP '2026-10-29 10:00:00'
FROM public.users u
WHERE u.email = 'member@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = 'React 컴포넌트, 어디까지 분리하시나요?');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'FEEDBACK', '첫 API 설계 문서, 피드백 부탁드려요',
       '팀 프로젝트의 API 명세를 작성하고 있어요. 일관된 응답 구조와 에러 처리에 대한 경험을 듣고 싶습니다.',
       '["Spring Boot", "API"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 217, 'VISIBLE', TIMESTAMP '2026-10-28 10:00:00', TIMESTAMP '2026-10-28 10:00:00'
FROM public.users u
WHERE u.email = 'premium@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '첫 API 설계 문서, 피드백 부탁드려요');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'PROMO', '매일 10분, AI와 함께 공부하는 루틴',
       '작은 학습 도우미의 프로토타입을 완성했어요. 직접 사용해 보시고 의견을 나눠주실 분을 찾습니다.',
       '["AI", "사이드 프로젝트"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 254, 'VISIBLE', TIMESTAMP '2026-10-27 10:00:00', TIMESTAMP '2026-10-27 10:00:00'
FROM public.users u
WHERE u.email = 'member@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '매일 10분, AI와 함께 공부하는 루틴');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'COMMUNITY', '이번 주말엔 어떤 걸 만들고 계신가요?',
       '퇴근 후 조금씩 만들던 서비스를 드디어 친구들에게 보여줬어요. 여러분의 작은 성취도 궁금합니다!',
       '["개발 일상", "회고"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 291, 'VISIBLE', TIMESTAMP '2026-10-26 10:00:00', TIMESTAMP '2026-10-26 10:00:00'
FROM public.users u
WHERE u.email = 'premium@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '이번 주말엔 어떤 걸 만들고 계신가요?');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'INFO', 'TypeScript로 폼 상태 안전하게 다루기',
       '복잡해지는 입력 폼에서 놓치기 쉬운 타입과 검증 패턴을 정리해봤습니다.',
       '["TypeScript", "폼"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 328, 'VISIBLE', TIMESTAMP '2026-10-25 10:00:00', TIMESTAMP '2026-10-25 10:00:00'
FROM public.users u
WHERE u.email = 'member@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = 'TypeScript로 폼 상태 안전하게 다루기');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'COMMUNITY', '좋은 협업은 작은 약속에서 시작하더라고요',
       '주간 회고 15분을 도입하고 달라진 팀의 이야기를 기록해요.',
       '["협업", "팀 문화"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 365, 'VISIBLE', TIMESTAMP '2026-10-24 10:00:00', TIMESTAMP '2026-10-24 10:00:00'
FROM public.users u
WHERE u.email = 'premium@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '좋은 협업은 작은 약속에서 시작하더라고요');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'COMMUNITY', '첫 협업 프로젝트를 마치고 배운 것들',
       'DevLog를 마무리하며 함께한 동료들에게 감사 인사를 전합니다.
작은 약속을 꾸준히 지키는 것이 가장 큰 힘이 되었어요.',
       '["회고", "협업"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 402, 'VISIBLE', TIMESTAMP '2026-10-23 10:00:00', TIMESTAMP '2026-10-23 10:00:00'
FROM public.users u
WHERE u.email = 'member@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '첫 협업 프로젝트를 마치고 배운 것들');

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, NULL, 'POST', 'INFO', '팀 API 설계에서 먼저 합의한 세 가지',
       '이름 짓기, 오류 처리, 변경을 기록하는 방법.
문서로 작은 기준을 맞춰두니 협업이 훨씬 편해졌습니다.',
       '["API", "팀 문화"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 439, 'VISIBLE', TIMESTAMP '2026-10-22 10:00:00', TIMESTAMP '2026-10-22 10:00:00'
FROM public.users u
WHERE u.email = 'premium@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.author_id = u.id AND p.type = 'POST' AND p.title = '팀 API 설계에서 먼저 합의한 세 가지');

-- 퍼간 글. 카테고리는 원본을 따르고 제목·태그·이미지는 비운다.
INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, o.id, 'REPOST', o.category, NULL, '협업 중에도 참고하기 좋은 기준이네요!',
       '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 0, 'VISIBLE', TIMESTAMP '2026-10-30 09:00:00', TIMESTAMP '2026-10-30 09:00:00'
FROM public.users u
JOIN public.users oa ON oa.email = 'member@devmozip.demo'
JOIN public.posts o ON o.author_id = oa.id AND o.type = 'POST' AND o.deleted_at IS NULL AND o.title = 'React 컴포넌트, 어디까지 분리하시나요?'
WHERE u.email = 'premium@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts r WHERE r.author_id = u.id AND r.original_post_id = o.id AND r.deleted_at IS NULL);

INSERT INTO public.posts (author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
SELECT u.id, o.id, 'REPOST', o.category, NULL, '서로의 관점을 나누는 리뷰를 응원합니다.',
       '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 0, 'VISIBLE', TIMESTAMP '2026-10-30 09:30:00', TIMESTAMP '2026-10-30 09:30:00'
FROM public.users u
JOIN public.users oa ON oa.email = 'premium@devmozip.demo'
JOIN public.posts o ON o.author_id = oa.id AND o.type = 'POST' AND o.deleted_at IS NULL AND o.title = '첫 API 설계 문서, 피드백 부탁드려요'
WHERE u.email = 'member@devmozip.demo'
  AND NOT EXISTS (SELECT 1 FROM public.posts r WHERE r.author_id = u.id AND r.original_post_id = o.id AND r.deleted_at IS NULL);

COMMIT;

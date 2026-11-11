import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AdminCommentsView, AdminPostsView } from '@/app/admin/posts/_components/ApiAdminPosts';
import { restrictAction } from '@/lib/administration/adminPosts';
import type { AdminComment, AdminPost } from '@/types/adminApi';

const noop = () => {};
const post: AdminPost = { id: 10, type: 'POST', title: '스프링 질문', content: '본문', authorTag: 'author', authorNickname: '작성자', visibility: 'VISIBLE', createdAt: '2026-11-10T10:00:00' };
const comment: AdminComment = { id: 20, postId: 10, postTitle: '스프링 질문', content: '광고 댓글', authorTag: 'reader', authorNickname: '독자', createdAt: '2026-11-10T10:30:00' };
const ready = <T,>(items: T[]) => ({ status: 'ready' as const, data: { items, totalElements: items.length, page: 0, totalPages: 1 } });
const posts = (load: Parameters<typeof AdminPostsView>[0]['load'], busy = false) =>
    renderToStaticMarkup(<AdminPostsView load={load} busy={busy} onRetry={noop} onPage={noop} onRestrict={noop} onDelete={noop} />);

describe('서버 게시글·댓글 관리 화면 (14.6·14.7)', () => {
    it('게시글의 공개 범위에 맞는 숨김·복원 버튼을 보여 준다', () => {
        const html = posts(ready([post, { ...post, id: 11, visibility: 'RESTRICTED' }, { ...post, id: 12, visibility: 'HIDDEN' }]));
        expect(html).toContain('href="/feeds/10"');
        expect(html).toContain('작성자 · 공개 · 2026-11-10 10:00');
        expect(html).toContain('관리자 숨김');
        expect(html).toContain('공개 복원');
        expect(html).toContain('작성자 숨김');
        expect(restrictAction({ ...post, visibility: 'HIDDEN' })).toEqual({ restricted: true, label: '관리자 숨김' });
        expect(restrictAction({ ...post, visibility: 'RESTRICTED' })).toEqual({ restricted: false, label: '공개 복원' });
    });
    it('퍼간 글은 제목 대신 표시하고, 요청 중에는 버튼을 막는다', () => {
        const html = posts(ready([{ ...post, type: 'REPOST', title: null, content: '좋은 글이에요' }]), true);
        expect(html).toContain('퍼간 글');
        expect(html).toContain('좋은 글이에요');
        expect(html).toMatch(/<button[^>]*disabled[^>]*>관리자 숨김/);
    });
    it('빈 목록과 오류를 안내한다', () => {
        expect(posts(ready([]))).toContain('조건에 맞는 게시글이 없습니다.');
        expect(posts({ status: 'error', message: '관리자만 이용할 수 있어요.' })).toContain('다시 시도');
    });
    it('댓글은 게시글 링크와 삭제 버튼을 보여 준다', () => {
        const html = renderToStaticMarkup(<AdminCommentsView load={ready([comment])} onRetry={noop} onPage={noop} onDelete={noop} />);
        expect(html).toContain('광고 댓글');
        expect(html).toContain('href="/feeds/10"');
        expect(html).toContain('댓글 삭제');
        expect(html).not.toContain('관리자 숨김');
    });
});

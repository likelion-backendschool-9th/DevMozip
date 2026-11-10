import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { apiComment, apiPost } from '@/tests/helpers/feedApiFixture';
import { GET as list, POST as create } from './route';
import { GET as recommended } from './recommands/route';
import { GET as following } from './following/route';
import { GET as detail, PUT as update } from './[id]/route';
import { POST as remove } from './[id]/delete/route';
import { PATCH as like } from './[id]/likes/route';
import { DELETE as unbookmark, POST as bookmark } from './[id]/bookmark/route';
import { GET as comments, POST as comment } from './[id]/comments/route';
import { DELETE as cancelRepost, POST as repost } from './[id]/reposts/route';
import { POST as reportPost } from './[id]/reports/route';
import { DELETE as deleteComment, PUT as editComment } from '../comments/[id]/route';
import { POST as reportComment } from '../comments/[id]/reports/route';

const context = (id = '7') => ({ params: Promise.resolve({ id }) });
const request = (path: string, init: RequestInit = {}, origin = 'http://localhost') =>
    new Request(`http://localhost${path}`, { ...init, headers: { Origin: origin, ...init.headers } });
const page = (items: unknown[] = [apiPost()], extra: Record<string, unknown> = {}) =>
    ({ items, page: 0, size: 20, hasNext: false, totalCount: items.length, ...extra });
const jsonInit = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const png = (size = 8) => new File([new Uint8Array(size)], 'a.png', { type: 'image/png' });
function multipart(post: unknown, images: File[] = [], extra: [string, string | Blob][] = []) {
    const form = new FormData();
    form.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }));
    images.forEach(image => form.append('images', image));
    extra.forEach(([key, value]) => form.append(key, value));
    return form;
}
const sent = (index = 0) => vi.mocked(backendFetch).mock.calls[index];
beforeEach(() => vi.mocked(backendFetch).mockReset());

describe('피드 목록 BFF', () => {
    it('허용한 조건만 정해진 순서로 넘기고 필요한 필드만 no-store로 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page()));
        const response = await recommended(request('/api/feeds/recommands?category=INFO&q=react&seed=42'));
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        const body = await response.json();
        expect(body.items[0].author).not.toHaveProperty('email');
        expect(sent()[0]).toBe('/api/v1/feeds/recommands?category=INFO&q=react&sort=recommended&page=0&size=20&seed=42');
    });
    it('목록마다 기본 정렬이 다르다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(page([])));
        await list(request('/api/feeds'));
        await following(request('/api/feeds/following'));
        expect(sent(0)[0]).toBe('/api/v1/feeds?sort=latest&page=0&size=20&seed=0');
        expect(sent(1)[0]).toBe('/api/v1/feeds/following?sort=latest&page=0&size=20&seed=0');
    });
    it('잘못된 조건은 백엔드에 보내지 않고, 응답 페이지가 다르면 502다', async () => {
        expect((await list(request('/api/feeds?userId=3'))).status).toBe(400);
        expect((await list(request('/api/feeds?sort=oldest'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([], { page: 1 })));
        expect((await list(request('/api/feeds'))).status).toBe(502);
    });
    it('백엔드 오류는 상태별 문구로 바꾸고 서버 내부 응답은 넘기지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'SQL error detail' }, { status: 401 }));
        const response = await following(request('/api/feeds/following'));
        expect(response.status).toBe(401);
        expect((await response.json()).message).not.toContain('SQL');
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('boom', { status: 500 }));
        expect((await list(request('/api/feeds'))).status).toBe(502);
        vi.mocked(backendFetch).mockRejectedValueOnce(new Error('down'));
        expect((await list(request('/api/feeds'))).status).toBe(502);
    });
});

describe('게시글 작성·수정 BFF', () => {
    it('글 내용의 허용 필드와 이미지만 multipart로 다시 만들어 보낸다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(apiPost(), { status: 201 }));
        const body = multipart({ category: 'INFO', title: '제목', content: '본문', techStacks: [], interests: [], authorId: 9, viewCount: 99 }, [png()]);
        const response = await create(request('/api/feeds', { method: 'POST', body }));
        expect(response.status).toBe(201);
        const [path, init] = sent();
        expect(path).toBe('/api/v1/feeds');
        expect(init?.method).toBe('POST');
        const forwarded = init?.body as FormData;
        expect(JSON.parse(await (forwarded.get('post') as Blob).text()))
            .toEqual({ category: 'INFO', title: '제목', content: '본문', techStacks: [], interests: [] });
        expect(forwarded.getAll('images')).toHaveLength(1);
        expect((forwarded.get('images') as File).name).toBe('image-1');
    });
    it('다른 사이트·잘못된 형식·이미지 조건 위반은 백엔드에 보내지 않는다', async () => {
        const body = () => multipart({ title: '제목' });
        expect((await create(request('/api/feeds', { method: 'POST', body: body() }, 'https://evil.example'))).status).toBe(403);
        expect((await create(request('/api/feeds', jsonInit('POST', { title: '제목' })))).status).toBe(415);
        expect((await create(request('/api/feeds', { method: 'POST', body: multipart({}, [png(), png(), png(), png()]) }))).status).toBe(400);
        expect((await create(request('/api/feeds', { method: 'POST', body: multipart({}, [png(512 * 1024 + 1)]) }))).status).toBe(400);
        expect((await create(request('/api/feeds', { method: 'POST', body: multipart({}, [new File(['<svg/>'], 'a.svg', { type: 'image/svg+xml' })]) }))).status).toBe(400);
        expect((await create(request('/api/feeds', { method: 'POST', body: multipart({}, [], [['userId', '3']]) }))).status).toBe(400);
        const noPost = new FormData();
        noPost.append('images', png());
        expect((await create(request('/api/feeds', { method: 'POST', body: noPost }))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('수정은 남길 이미지 목록도 보내고 작성자가 아니면 403 문구를 준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(apiPost()));
        await update(request('/api/feeds/7', { method: 'PUT', body: multipart({ title: '제목', keepImageUrls: ['/images/a_1.png'], visibility: 'VISIBLE' }) }), context());
        const [path, init] = sent();
        expect(path).toBe('/api/v1/feeds/7');
        expect(JSON.parse(await ((init?.body as FormData).get('post') as Blob).text())).toEqual({ title: '제목', keepImageUrls: ['/images/a_1.png'] });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'x' }, { status: 403 }));
        const forbidden = await update(request('/api/feeds/7', { method: 'PUT', body: multipart({ title: '제목' }) }), context());
        expect(forbidden.status).toBe(403);
        expect((await forbidden.json()).message).toBe('본인 게시글만 수정할 수 있어요.');
    });
});

describe('게시글 상세·삭제·반응 BFF', () => {
    it('상세는 view=true만 넘기고 다른 조건은 거절한다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(apiPost()));
        expect((await detail(request('/api/feeds/7?view=true'), context())).status).toBe(200);
        expect(sent()[0]).toBe('/api/v1/feeds/7?view=true');
        expect((await detail(request('/api/feeds/7'), context())).status).toBe(200);
        expect(sent(1)[0]).toBe('/api/v1/feeds/7');
        expect((await detail(request('/api/feeds/7?view=yes'), context())).status).toBe(400);
        expect((await detail(request('/api/feeds/7?userId=1'), context())).status).toBe(400);
        expect((await detail(request('/api/feeds/abc'), context('abc'))).status).toBe(404);
        expect(backendFetch).toHaveBeenCalledTimes(2);
    });
    it('삭제·좋아요·북마크는 같은 사이트 요청만 보내고 응답이 요청과 맞는지 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect((await remove(request('/api/feeds/7/delete', { method: 'POST' }), context())).status).toBe(204);
        expect(sent()).toEqual(['/api/v1/feeds/7/delete', expect.objectContaining({ method: 'POST' })]);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ postId: 7, liked: true, likeCount: 3 }));
        expect(await (await like(request('/api/feeds/7/likes', { method: 'PATCH' }), context())).json()).toEqual({ postId: 7, liked: true, likeCount: 3 });
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ postId: 8, liked: true, likeCount: 3 }));
        expect((await like(request('/api/feeds/7/likes', { method: 'PATCH' }), context())).status).toBe(502);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ postId: 7, bookmarked: true }));
        expect((await bookmark(request('/api/feeds/7/bookmark', { method: 'POST' }), context())).status).toBe(200);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ postId: 7, bookmarked: true }));
        expect((await unbookmark(request('/api/feeds/7/bookmark', { method: 'DELETE' }), context())).status).toBe(502);
        expect((await like(request('/api/feeds/7/likes', { method: 'PATCH' }, 'https://evil.example'), context())).status).toBe(403);
    });
});

describe('댓글·퍼가기·신고 BFF', () => {
    it('댓글은 content만 보내고 목록 작성자의 알 수 없는 필드를 버린다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json([apiComment()]));
        const listed = await (await comments(request('/api/feeds/7/comments'), context())).json();
        expect(listed[0].author).not.toHaveProperty('email');
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(apiComment(), { status: 201 }));
        expect((await comment(request('/api/feeds/7/comments', jsonInit('POST', { content: '댓글', authorId: 3 })), context())).status).toBe(201);
        expect(JSON.parse(String(sent(1)[1]?.body))).toEqual({ content: '댓글' });
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(apiComment()));
        await editComment(request('/api/comments/11', jsonInit('PUT', { content: '수정' })), context('11'));
        expect(sent(2)[0]).toBe('/api/v1/comments/11');
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect((await deleteComment(request('/api/comments/11', { method: 'DELETE' }), context('11'))).status).toBe(204);
        expect((await comment(request('/api/feeds/7/comments', { method: 'POST', body: 'content=x' }), context())).status).toBe(415);
    });
    it('퍼가기는 본문 없이도 보내고 중복은 목업 문구로 알린다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(apiPost({ id: 9, type: 'REPOST', title: null, imageUrls: [],
            original: { id: 7, available: true, category: 'INFO', title: '제목', author: apiPost().author } }), { status: 201 }));
        expect((await repost(request('/api/feeds/7/reposts', { method: 'POST' }), context())).status).toBe(201);
        expect(sent()[1]).toEqual({ method: 'POST', signal: expect.anything() });
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ message: 'x' }, { status: 409 }));
        const duplicate = await repost(request('/api/feeds/7/reposts', jsonInit('POST', { content: '의견' })), context());
        expect(duplicate.status).toBe(409);
        expect((await duplicate.json()).message).toBe('이미 내 프로필에 퍼간 글입니다.');
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect((await cancelRepost(request('/api/feeds/7/reposts', { method: 'DELETE' }), context())).status).toBe(204);
    });
    it('신고는 사유만 보내고 중복 신고를 알린다', async () => {
        const report = { id: 1, targetType: 'POST', targetId: 7, status: 'AWATING', createdAt: '2026-11-10T10:15:00+09:00' };
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(report, { status: 201 }));
        expect((await reportPost(request('/api/feeds/7/reports', jsonInit('POST', { reason: '기타', reporterId: 2 })), context())).status).toBe(201);
        expect(JSON.parse(String(sent()[1]?.body))).toEqual({ reason: '기타' });
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ message: 'x' }, { status: 409 }));
        const duplicate = await reportComment(request('/api/comments/11/reports', jsonInit('POST', { reason: '기타' })), context('11'));
        expect(duplicate.status).toBe(409);
        expect((await duplicate.json()).message).toBe('이미 신고한 내용입니다.');
        expect(sent(1)[0]).toBe('/api/v1/comments/11/reports');
    });
});

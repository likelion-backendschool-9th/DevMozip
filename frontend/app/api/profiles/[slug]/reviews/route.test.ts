import { beforeEach, expect, it, vi } from 'vitest';
const fetchBackend = vi.hoisted(() => vi.fn());
vi.mock('@/lib/auth/backend', () => ({ backendFetch: fetchBackend }));
import { GET } from './route';
const empty = { userId: 1, count: 0, average: 0, distribution: [0, 0, 0, 0, 0], keywords: [], items: [], page: 0, size: 20, totalPages: 0 };
const call = (slug = 'me', query = '') => GET(new Request('http://localhost/api/profiles/'+slug+'/reviews'+query), { params: Promise.resolve({ slug }) });
beforeEach(() => fetchBackend.mockReset());
it('본인·공개 리뷰를 해당 API로 전달하고 임의 신원 쿼리를 제외한다', async () => {
    fetchBackend.mockImplementation(() => Promise.resolve(Response.json(empty)));
    expect((await call('me', '?userId=999')).status).toBe(200);
    expect(fetchBackend).toHaveBeenCalledWith('/api/v1/users/me/reviews?page=0&size=20');
    const response = await call('writer'); expect(response.status).toBe(200); expect(response.headers.get('cache-control')).toBe('no-store');
    expect(fetchBackend).toHaveBeenCalledWith('/api/v1/users/writer/reviews?page=0&size=20');
});
it('잘못된 쿼리·경로는 백엔드에 보내지 않는다', async () => {
    expect((await call('me', '?size=0')).status).toBe(400); expect((await call('invalid.tag')).status).toBe(400);
    expect(fetchBackend).not.toHaveBeenCalled();
});
it('DB 오류·미인증을 보존하고 내부 오류는 숨긴다', async () => {
    for (const status of [401, 503]) {
        fetchBackend.mockResolvedValueOnce(Response.json({ message: 'secret sql' }, { status }));
        const response = await call(); expect(response.status).toBe(status); expect(await response.text()).not.toContain('secret sql');
    }
});
it('집계 또는 페이지가 잘못되면 성공으로 표시하지 않는다', async () => {
    fetchBackend.mockResolvedValueOnce(Response.json({ ...empty, page: 1 }));expect((await call()).status).toBe(502);
    fetchBackend.mockRejectedValueOnce(new Error('connection'));expect((await call()).status).toBe(502);
});

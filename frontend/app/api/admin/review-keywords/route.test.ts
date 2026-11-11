import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET, PUT } from './route';

const items = [{ id: 1, name: '소통이 원활해요', active: true }, { id: 3, name: '함께 성장해요', active: false }];
const put = (body: string, origin = 'http://localhost') =>
    PUT(new Request('http://localhost/api/admin/review-keywords', { method: 'PUT', body, headers: { 'Content-Type': 'application/json', Origin: origin } }));
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('리뷰 키워드 BFF (14.9)', () => {
    it('목록 형식을 확인해 넘긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: items.map(i => ({ ...i, createdAt: 'x' })) }));
        expect(await (await GET(new Request('http://localhost/api/admin/review-keywords'))).json()).toEqual({ items });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ id: 1, name: '소통', active: 'yes' }] }));
        expect((await GET(new Request('http://localhost/api/admin/review-keywords'))).status).toBe(502);
    });
    it('이름을 다듬어 활성 목록만 보낸다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items }));
        expect((await put('{"keywords":[" 소통이 원활해요 ","새 키워드"]}')).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/review-keywords',
            { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"keywords":["소통이 원활해요","새 키워드"]}' });
        await put('{"keywords":[]}');
        expect(vi.mocked(backendFetch).mock.calls[1][1]?.body).toBe('{"keywords":[]}');
    });
    it.each([
        ['{"keywords":["  "]}', '키워드는 1~30자로 입력해 주세요.'],
        [`{"keywords":["${'가'.repeat(31)}"]}`, '키워드는 1~30자로 입력해 주세요.'],
        ['{"keywords":["a","a "]}', '같은 키워드를 두 번 넣을 수 없어요.'],
        ['{"keywords":[1]}', '키워드는 1~30자로 입력해 주세요.'],
        ['{"keywords":"a"}', '키워드 목록을 확인해 주세요.'],
        ['{"keywords":[],"extra":1}', '키워드 목록을 확인해 주세요.'],
    ])('잘못된 본문 %s는 400이다', async (body, message) => {
        const response = await put(body);
        expect(response.status).toBe(400);
        expect((await response.json()).message).toBe(message);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 사이트 요청은 보내지 않는다', async () => {
        expect((await put('{"keywords":[]}', 'http://evil.example')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});

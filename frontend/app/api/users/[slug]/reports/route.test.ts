import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { POST } from './route';

const context = (slug = 'sample_dev') => ({ params: Promise.resolve({ slug }) });
const post = (body: unknown, origin = 'http://localhost', contentType = 'application/json') => new Request('http://localhost/api/users/sample_dev/reports', {
    method: 'POST', headers: { Host: 'localhost', Origin: origin, 'Content-Type': contentType }, body: typeof body === 'string' ? body : JSON.stringify(body) });
beforeEach(() => { vi.mocked(backendFetch).mockReset(); });

describe('사용자 신고 BFF', () => {
    it('사유만 전달하고 본문 없는 201을 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 201 }));
        const response = await POST(post({ reason: '사칭', reporterId: 1 }), context());
        expect(response.status).toBe(201);
        expect(await response.text()).toBe('');
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/users/sample_dev/reports');
        expect(init?.method).toBe('POST');
        expect(JSON.parse(String(init?.body))).toEqual({ reason: '사칭' });
    });
    it('다른 사이트와 JSON이 아닌 요청을 거부한다', async () => {
        expect((await POST(post({ reason: '사칭' }, 'https://evil.example'), context())).status).toBe(403);
        expect((await POST(post('reason=사칭', 'http://localhost', 'text/plain'), context())).status).toBe(415);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each(['../admin', 'bad?tag', ''])('잘못된 태그 %s를 전달하지 않는다', async slug => {
        expect((await POST(post({ reason: '사칭' }), context(slug))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([[{}], [{ reason: 1 }], [{ reason: '  ' }], [{ reason: 'a'.repeat(101) }], ['{깨진 JSON']])('잘못된 사유 %j를 전달하지 않는다', async input => {
        expect((await POST(post(input), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([
        [400, '본인이거나 허용되지 않은 신고 사유예요.'],
        [401, '로그인이 만료됐어요. 다시 로그인해 주세요.'],
        [404, '신고 대상을 찾을 수 없어요.'],
        [409, '이미 신고한 사용자예요.'],
    ])('백엔드 %i의 안내 문구를 반환한다', async (status, message) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal', { status }));
        const response = await POST(post({ reason: '사칭' }), context());
        expect(response.status).toBe(status);
        expect(await response.json()).toEqual({ message });
    });
    it('서버 오류와 연결 오류는 내부 내용 없이 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal details', { status: 500 }));
        const failed = await POST(post({ reason: '사칭' }), context());
        expect(failed.status).toBe(502);
        expect(await failed.text()).not.toContain('internal details');
        vi.mocked(backendFetch).mockRejectedValueOnce(new Error('http://internal-db:8080'));
        const disconnected = await POST(post({ reason: '사칭' }), context());
        expect(disconnected.status).toBe(502);
        expect(await disconnected.text()).not.toContain('internal-db');
    });
});

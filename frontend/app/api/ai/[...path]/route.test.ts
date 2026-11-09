import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, POST, DELETE } from './route';
const context = (path: string) => ({ params: Promise.resolve({ path: path.split('/') }) });
const post = (path: string, origin = 'http://localhost', key = 'request-key') => POST(new Request(`http://localhost/api/ai/${path}`, {
    method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json', 'Idempotency-Key': key, Authorization: 'Bearer untrusted' }, body: '{}' }), context(path));
beforeEach(() => vi.mocked(backendFetch).mockReset());
describe('AI BFF', () => {
    it('정해진 AI 경로만 전달하고 캐시하지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json([]));
        const response = await GET(new Request('http://localhost/api/ai/conversations'), context('conversations'));
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(vi.mocked(backendFetch).mock.calls[0][0]).toBe('/api/v1/ai/conversations');
        expect((await post('../auth/login')).status).toBe(404);
    });
    it('클라이언트 인증 헤더를 전달하지 않고 추천 키만 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ status: 'SUCCESS' }, { status: 201 }));
        expect((await post('conversations/1/recommendations')).status).toBe(201);
        expect(vi.mocked(backendFetch).mock.calls[0][1]?.headers).toEqual({ 'Content-Type': 'application/json', 'Idempotency-Key': 'request-key' });
    });
    it('외부 출처 변경·잘못된 키·조회 변조를 거절한다', async () => {
        expect((await post('conversations/sync', 'https://other.test')).status).toBe(403);
        expect((await post('conversations/1/recommendations', 'http://localhost', '')).status).toBe(400);
        expect((await GET(new Request('http://localhost/api/ai/conversations/1/recommendations?userId=2'), context('conversations/1/recommendations'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('내부 URL과 달라도 실제 Host의 동일 출처는 허용한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({}));
        const response = await POST(new Request('http://localhost/api/ai/conversations/sync', { method: 'POST',
            headers: { Origin: 'http://127.0.0.1:13000', Host: '127.0.0.1:13000', 'Content-Type': 'application/json' }, body: '{}' }), context('conversations/sync'));
        expect(response.status).toBe(200);
    });
    it('기존 공개 프로젝트 API에서 카드용 필드만 읽는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ project: { id: 9, isPublic: true, title: '공개 프로젝트', summary: '소개', description: '상세', techStacks: ['React'], recruitmentSlots: [{ role: 'BACKEND' }], hostEmail: 'private' } }));
        const response = await GET(new Request('http://localhost/api/ai/projects/9'), context('projects/9'));
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ id: 9, title: '공개 프로젝트', summary: '소개', description: '상세', imageUrl: null, stacks: ['React'], roles: ['BACKEND'] });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/projects/9');
    });
    it('비공개 프로젝트 응답을 카드 정보로 넘기지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ project: { id: 9, isPublic: false, title: 'private' } }));
        expect((await GET(new Request('http://localhost/api/ai/projects/9'), context('projects/9'))).status).toBe(502);
    });
    it('추천 실패 DTO와 삭제 204를 유지한다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ status: 'FAILED', requestId: 1 }, { status: 500 }))
            .mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect((await post('conversations/1/recommendations')).status).toBe(500);
        expect((await DELETE(new Request('http://localhost/api/ai/conversations/1', { method: 'DELETE', headers: { Origin: 'http://localhost' } }), context('conversations/1'))).status).toBe(204);
    });
    it.each([401, 403, 404, 409, 500])('오류 %s의 내부 내용을 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'private database details' }, { status }));
        const response = await GET(new Request('http://localhost/api/ai/conversations'), context('conversations'));
        expect(await response.text()).not.toContain('private');
        expect(response.status).toBe(status === 500 ? 502 : status);
    });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { PUT, DELETE } from './route';
const context = (kind = 'profile') => ({ params: Promise.resolve({ kind }) });
function upload(file: File, origin = 'http://localhost') {
    const body = new FormData(); body.append('file', file);
    return new Request('http://localhost/api/profiles/me/images/profile', { method: 'PUT', headers: { Origin: origin }, body });
}
beforeEach(() => { vi.stubEnv('AUTH_MODE', 'server'); vi.mocked(backendFetch).mockReset(); });
afterEach(() => vi.unstubAllEnvs());
describe('이미지 BFF', () => {
    it('원본 파일을 multipart로 전달하고 이미지 URL만 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ profileImage: '/new.png', bannerImage: null, private: true }));
        const response = await PUT(upload(new File(['png'], 'image.png', { type: 'image/png' })), context());
        expect(await response.json()).toEqual({ profileImage: '/new.png', bannerImage: null });
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/users/me/images/profile');
        expect(init?.body).toBeInstanceOf(FormData);
        expect((init?.body as FormData).get('file')).toBeInstanceOf(File);
        expect(init?.headers).toBeUndefined();
    });
    it('256KB 초과 파일을 전송하지 않는다', async () => {
        expect((await PUT(upload(new File([new Uint8Array(262145)], 'large.png', { type: 'image/png' })), context())).status).toBe(413);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('지원하지 않는 파일은 전송하지 않는다', async () => {
        expect((await PUT(upload(new File(['svg'], 'image.svg', { type: 'image/svg+xml' })), context())).status).toBe(415);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 출처와 잘못된 종류는 차단한다', async () => {
        const file = new File(['png'], 'image.png', { type: 'image/png' });
        expect((await PUT(upload(file, 'https://other.test'), context())).status).toBe(403);
        expect((await PUT(upload(file), context('other'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('배너 삭제는 전용 DELETE 경로를 사용한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ profileImage: null, bannerImage: null }));
        const request = new Request('http://localhost/api/profiles/me/images/banner', { method: 'DELETE', headers: { Origin: 'http://localhost' } });
        expect((await DELETE(request, context('banner'))).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/images/banner', { method: 'DELETE' });
    });
});

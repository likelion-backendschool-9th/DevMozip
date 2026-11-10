import { afterEach, describe, expect, it, vi } from 'vitest';
import { GET } from './route';

const context = (filename: string) => ({ params: Promise.resolve({ filename }) });
const fetchMock = vi.fn();
afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

describe('업로드 이미지 경로 (nginx 없이 실행할 때)', () => {
    it('실행 시 BACKEND_URL의 같은 경로를 불러 확장자에 맞는 형식으로 돌려준다', async () => {
        vi.stubGlobal('fetch', fetchMock);
        fetchMock.mockResolvedValue(new Response(new Uint8Array([1, 2, 3]), { headers: { 'Content-Type': 'text/html' } }));
        const response = await GET(new Request('http://localhost/images/feed_7_20261110101500_1.png'), context('feed_7_20261110101500_1.png'));
        expect(response.status).toBe(200);
        expect(response.headers.get('content-type')).toBe('image/png');
        expect(response.headers.get('x-content-type-options')).toBe('nosniff');
        expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
        expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/images/feed_7_20261110101500_1.png');
    });
    it('허용하지 않는 파일 이름은 백엔드에 묻지 않고 404다', async () => {
        vi.stubGlobal('fetch', fetchMock);
        for (const name of ['secret.txt', '..%2Fa.png', 'feed_1.svg', 'FEED_1.png', 'a.png'])
            expect((await GET(new Request('http://localhost/images/x'), context(name))).status).toBe(404);
        expect(fetchMock).not.toHaveBeenCalled();
    });
    it('백엔드에 없으면 404, 연결이 안 되면 502다', async () => {
        vi.stubGlobal('fetch', fetchMock);
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 404 }));
        expect((await GET(new Request('http://localhost/images/x'), context('feed_1_a.png'))).status).toBe(404);
        fetchMock.mockRejectedValueOnce(new Error('down'));
        expect((await GET(new Request('http://localhost/images/x'), context('feed_1_a.png'))).status).toBe(502);
    });
});

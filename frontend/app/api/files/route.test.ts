import { afterEach, describe, expect, it, vi } from 'vitest';
import { POST } from './route';
import { GET } from './[id]/route';
import { backendFetch } from '@/lib/auth/backend';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
const id = '12345678-1234-1234-1234-123456789abc';
const meta = { id, url: `/api/files/${id}`, name: 'a.png', contentType: 'image/png', size: 3 };
afterEach(() => vi.resetAllMocks());
function upload(origin = 'http://localhost:3000') {
    const data = new FormData(); data.set('file', new File(['png'], 'a.png', { type: 'image/png' })); data.set('userId', '999');
    return new Request('http://0.0.0.0:3000/api/files', { method: 'POST', headers: { Origin: origin, Host: 'localhost:3000' }, body: data });
}
describe('파일 업로드·읽기 BFF', () => {
    it('실제 Host 기준 출처를 검증하고 사용자 식별자를 전달하지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(meta, { status: 201 }));
        const response = await POST(upload()); expect(response.status).toBe(201); expect(await response.json()).toEqual(meta);
        const [, init] = vi.mocked(backendFetch).mock.calls[0]; expect((init?.body as FormData).has('userId')).toBe(false);
        expect(response.headers.get('Cache-Control')).toBe('no-store');
    });
    it('배포 HTTPS(기본 포트, 내부 URL :3000)에서 브라우저 Origin을 허용하고 내부 포트가 남은 Origin은 거절한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(meta, { status: 201 }));
        const deployed = (origin: string) => {
            const data = new FormData(); data.set('file', new File(['png'], 'a.png', { type: 'image/png' }));
            return new Request('https://0.0.0.0:3000/api/files', { method: 'POST', headers: { Origin: origin, Host: 'devmozip.newlecture.com' }, body: data });
        };
        expect((await POST(deployed('https://devmozip.newlecture.com'))).status).toBe(201);
        vi.mocked(backendFetch).mockClear();
        for (const origin of ['https://devmozip.newlecture.com:3000', 'http://devmozip.newlecture.com', 'https://evil.test'])
            expect((await POST(deployed(origin))).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 출처와 JSON 요청은 서버로 보내지 않는다', async () => {
        expect((await POST(upload('http://evil.test'))).status).toBe(403);
        expect((await POST(new Request('http://localhost/api/files', { method: 'POST', headers: { Origin: 'http://localhost', 'Content-Type': 'application/json' }, body: '{}' }))).status).toBe(415);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('파일이 없는 요청과 중복 파일을 거절한다', async () => {
        for (const count of [0, 2]) {
            const data = new FormData(); for (let i = 0; i < count; i++) data.append('file', new File(['x'], 'a.pdf', { type: 'application/pdf' }));
            expect((await POST(new Request('http://localhost/api/files', { method: 'POST', headers: { Origin: 'http://localhost' }, body: data }))).status).toBe(400);
        }
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('깨진 multipart와 Content-Length로 숨긴 초과 요청을 거절한다', async () => {
        const make = (body: BodyInit) => new Request('http://localhost/api/files', { method: 'POST',
            headers: { Origin: 'http://localhost', 'Content-Type': 'multipart/form-data; boundary=test', 'Content-Length': '1' }, body });
        expect((await POST(make('broken'))).status).toBe(400);
        expect((await POST(make(new Uint8Array(11 * 1024 * 1024 + 1)))).status).toBe(413);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('백엔드 오류의 내부 정보는 숨긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'internal disk path' }, { status: 503 }));
        const response = await POST(upload()); expect(response.status).toBe(502); expect(await response.text()).not.toContain('internal disk');
    });
    it('파일 읽기도 백엔드 권한 검사 결과를 유지하고 다운로드 헤더를 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('pdf', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename=a.pdf' } }));
        const response = await GET(new Request(`http://localhost/api/files/${id}`), { params: Promise.resolve({ id }) });
        expect(response.headers.get('Content-Disposition')).toContain('attachment'); expect(response.headers.get('X-Content-Type-Options')).toBe('nosniff');
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 404 }));
        expect((await GET(new Request(`http://localhost/api/files/${id}`), { params: Promise.resolve({ id }) })).status).toBe(404);
    });
    it('새 형식 id를 백엔드로 넘기고, nginx 내부 경로는 이 파일을 가리킬 때만 본문 없이 전달한다', async () => {
        const current = 'portfolio_5_20261111093000';
        const call = () => GET(new Request(`http://localhost/api/files/${current}`), { params: Promise.resolve({ id: current }) });
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { headers: { 'Content-Type': 'application/pdf',
            'Content-Disposition': 'attachment; filename=a.pdf', 'X-Accel-Redirect': `/internal-files/${current}.pdf` } }));
        let response = await call();
        expect(vi.mocked(backendFetch).mock.calls[0][0]).toBe(`/api/v1/files/${current}`);
        expect(response.status).toBe(200); expect(response.headers.get('X-Accel-Redirect')).toBe(`/internal-files/${current}.pdf`);
        expect(response.headers.get('Content-Disposition')).toContain('attachment'); expect(response.headers.get('Cache-Control')).toBe('no-store');
        expect(await response.text()).toBe('');
        for (const accel of [`/internal-files/${current}.json`, `/internal-files/${id}.pdf`, `/internal-files/../${current}.pdf`, `/images/${current}.pdf`]) {
            vi.mocked(backendFetch).mockResolvedValue(new Response(null, { headers: { 'Content-Type': 'application/pdf', 'X-Accel-Redirect': accel } }));
            response = await call(); expect(response.status).toBe(502); expect(response.headers.get('X-Accel-Redirect')).toBeNull();
        }
    });
    it('임의 ID와 HTML 응답을 차단한다', async () => {
        expect((await GET(new Request('http://localhost/api/files/bad'), { params: Promise.resolve({ id: 'bad' }) })).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
        vi.mocked(backendFetch).mockResolvedValue(new Response('<script>', { headers: { 'Content-Type': 'text/html' } }));
        expect((await GET(new Request(`http://localhost/api/files/${id}`), { params: Promise.resolve({ id }) })).status).toBe(502);
    });
});

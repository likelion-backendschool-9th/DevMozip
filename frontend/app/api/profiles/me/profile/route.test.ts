import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, PUT } from './route';
import { GET as jobs } from './job-categories/route';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
const url = 'http://localhost/api/profiles/me/profile';
const put = (body: unknown, origin = 'http://localhost') => PUT(new Request(url, { method: 'PUT', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
beforeEach(() => { vi.stubEnv('AUTH_MODE', 'server'); vi.mocked(backendFetch).mockReset(); });
afterEach(() => vi.unstubAllEnvs());
describe('본인 프로필 BFF', () => {
    it('본인 조회를 인증된 전용 경로로 전달하고 민감정보를 제외한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...apiProfileFixture, contactHours: '평일 저녁', email: 'private' }));
        const response = await GET();
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual({ ...apiProfileFixture, contactHours: '평일 저녁' });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/profile', {});
    });
    it('경력 문구·연락 시간 삭제를 전달하되 사용자 ID와 권한은 전달하지 않는다', async () => {
        const input = { nickname: '개발자', tag: 'new-tag', jobCategories: ['백엔드 개발자'], summary: '', career: '신입 / 학습 중', collaborationStatus: 'RESTING', contactHours: '' };
        vi.mocked(backendFetch).mockResolvedValue(Response.json(apiProfileFixture));
        expect((await put({ ...input, id: 999, email: 'other', role: 'ADMIN', trustScore: 100 })).status).toBe(200);
        const call = vi.mocked(backendFetch).mock.calls[0];
        expect(call[0]).toBe('/api/v1/users/me/profile');
        expect(JSON.parse(String(call[1]?.body))).toEqual(input);
    });
    it('다른 출처의 저장 요청을 차단한다', async () => {
        expect((await put({}, 'https://other.test')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([400, 401, 403, 404, 409])('백엔드 오류 %s 상태는 유지하고 내부 정보는 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('secret stacktrace', { status }));
        const response = await put({});
        expect(response.status).toBe(status);
        expect(await response.text()).not.toContain('secret');
    });
    it('직군 목록은 문자열 배열로 반환하고 잘못된 응답은 거부한다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(['백엔드 개발자']));
        expect(await (await jobs()).json()).toEqual(['백엔드 개발자']);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json([{ id: 7 }]));
        expect((await jobs()).ok).toBe(false);
    });
});


it('신규 가입자의 tag=null 응답은 내 프로필에서 502로 변환하지 않는다', async () => {
    vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...apiProfileFixture, tag: null, email: 'private' }));
    const response = await GET();
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ ...apiProfileFixture, tag: null });
});
it('연결 오류와 응답 형식 오류를 구분하며 내부 응답은 숨긴다', async () => {
    vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...apiProfileFixture, tag: '../invalid', passwordHash: 'private' }));
    const invalid = await GET(); expect(invalid.status).toBe(502);
    expect(await invalid.json()).toEqual({ message: '프로필 서버의 응답 형식을 확인할 수 없어요.' });
    vi.mocked(backendFetch).mockRejectedValue(new Error('private connection'));
    const unavailable = await GET(); expect(unavailable.status).toBe(502);
    expect(await unavailable.json()).toEqual({ message: '프로필 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.' });
});

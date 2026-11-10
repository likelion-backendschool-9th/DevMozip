import { expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { profileRequestGuard } from './profileProxy';
const request = (origin: string | null, host = 'localhost:3000', extra: Record<string, string> = {}) => new Request('http://0.0.0.0:3000/api/profiles/me/profile', {
    method: 'PATCH', headers: { Host: host, ...(origin === null ? {} : { Origin: origin }), ...extra },
});
it('Docker 내부 주소와 달라도 브라우저 Host·Origin이 같으면 허용한다', () => {
    expect(profileRequestGuard(request('http://localhost:3000'))).toBeNull();
    expect(profileRequestGuard(new Request('https://internal:3000/api/profiles/me/profile', { method: 'PATCH', headers: { Host: 'devmozip.newlecture.com', Origin: 'https://devmozip.newlecture.com' } }))).toBeNull();
});
it('다른 사이트·포트·프로토콜·누락/손상 Origin은 차단한다', () => {
    for (const origin of ['https://other.test', 'http://localhost:3001', 'https://localhost:3000', 'null', 'http://localhost:3000/path', null])
        expect(profileRequestGuard(request(origin))?.status).toBe(403);
});
it('위조 forwarded-host 및 잘못된 Host로 출처 검사를 우회할 수 없다', () => {
    expect(profileRequestGuard(request('http://other.test', 'localhost:3000', { 'x-forwarded-host': 'other.test' }))?.status).toBe(403);
    for (const host of ['user@localhost:3000', 'localhost:3000/path', 'localhost:3000,other.test', 'localhost:3000#x'])
        expect(profileRequestGuard(request('http://localhost:3000', host))?.status).toBe(403);
});
it('Host 없는 표준 Request와 요청 없는 기존 GET 호출은 기존대로 처리한다', () => {
    expect(profileRequestGuard()).toBeNull();
    expect(profileRequestGuard(new Request('http://localhost:3000/path', { headers: { Origin: 'http://localhost:3000' } }))).toBeNull();
});

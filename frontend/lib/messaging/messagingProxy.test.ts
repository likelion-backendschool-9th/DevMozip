import { describe, expect, it } from 'vitest';
import { messagingWriteGuard } from './messagingProxy';

// 컨테이너의 Next 서버는 HOSTNAME=0.0.0.0으로 떠서 request.url이 브라우저 주소와 다르다.
const CONTAINER_URL = 'http://0.0.0.0:3000/api/messages/chats/1/read';
const write = (headers: Record<string, string>, url = CONTAINER_URL) =>
    new Request(url, { method: 'PATCH', headers: { 'Content-Type': 'application/json', ...headers }, body: '{}' });
const status = (request: Request) => messagingWriteGuard(request)?.status ?? 'pass';

describe('메신저 쓰기 요청의 같은 사이트 확인', () => {
    it('request.url이 아니라 브라우저가 보낸 Host와 Origin을 비교한다', () => {
        expect(status(write({ Host: 'localhost:3000', Origin: 'http://localhost:3000' }))).toBe('pass');
        expect(status(write({ Host: 'localhost:13300', Origin: 'http://localhost:13300' }))).toBe('pass');
    });

    it('nginx를 거친 요청은 X-Forwarded-Host로 확인한다', () => {
        expect(status(write({ Host: 'frontend-blue:3000', 'X-Forwarded-Host': 'devmozip.newlecture.com', Origin: 'https://devmozip.newlecture.com' }))).toBe('pass');
        expect(status(write({ Host: 'devmozip.newlecture.com', 'X-Forwarded-Host': 'devmozip.newlecture.com, proxy', Origin: 'https://devmozip.newlecture.com' }))).toBe('pass');
    });

    it('다른 사이트, Origin이 없거나 잘못된 요청은 403으로 막는다', () => {
        expect(status(write({ Host: 'localhost:3000', Origin: 'https://evil.example' }))).toBe(403);
        expect(status(write({ Host: 'localhost:3000', 'X-Forwarded-Host': 'devmozip.newlecture.com', Origin: 'https://evil.example' }))).toBe(403);
        expect(status(write({ Host: 'localhost:3000', Origin: 'http://localhost:3001' }))).toBe(403);
        expect(status(write({ Host: 'localhost:3000' }))).toBe(403);
        expect(status(write({ Host: 'localhost:3000', Origin: 'null' }))).toBe(403);
        expect(status(write({ Origin: 'http://localhost:3000' }))).toBe(403);
    });

    it('JSON이 아닌 요청은 415로 막는다', () => {
        const request = new Request(CONTAINER_URL, { method: 'PATCH', headers: { Host: 'localhost:3000', Origin: 'http://localhost:3000', 'Content-Type': 'text/plain' }, body: '{}' });
        expect(status(request)).toBe(415);
    });
});

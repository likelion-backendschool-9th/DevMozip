import { parseProfileLinks } from '@/lib/profiles/profileLinksApi';
import { proxyProfile, profileRequestGuard, profileError } from '@/lib/profiles/profileProxy';
export async function GET() {
    return proxyProfile('/api/v1/users/me/links', {}, parseProfileLinks);
}
export async function PUT(request: Request) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    if (!request.headers.get('content-type')?.startsWith('application/json')) return profileError(415, 'JSON 형식이 필요합니다.');
    let body;
    try { body = parseProfileLinks(await request.json()); }
    catch { return profileError(400, '외부 링크의 이름과 http·https 주소를 확인해 주세요.'); }
    return proxyProfile('/api/v1/users/me/links', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body) }, parseProfileLinks);
}

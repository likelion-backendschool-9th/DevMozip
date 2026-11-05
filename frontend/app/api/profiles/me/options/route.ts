import { parseProfileOptions } from '@/lib/profiles/profileOptionsApi';
import { proxyProfile, profileRequestGuard, profileError } from '@/lib/profiles/profileProxy';
export async function GET() {
    return proxyProfile('/api/v1/users/me/options', {}, parseProfileOptions);
}
export async function PUT(request: Request) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    if (!request.headers.get('content-type')?.startsWith('application/json')) return profileError(415, 'JSON 형식이 필요합니다.');
    let body;
    try { body = parseProfileOptions(await request.json()); }
    catch { return profileError(400, '기술 스택과 관심 분야를 목록으로 입력해 주세요.'); }
    return proxyProfile('/api/v1/users/me/options', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body) }, parseProfileOptions);
}

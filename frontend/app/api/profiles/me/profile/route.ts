import { parseProfileDetail, parseOwnProfileDetail } from '@/lib/profiles/profileDetail';
import { profileRequestGuard, profileError, proxyProfile } from '@/lib/profiles/profileProxy';
export async function GET() {
    return profileRequestGuard() ?? proxyProfile('/api/v1/users/me/profile', {}, parseOwnProfileDetail);
}
export async function PUT(request: Request) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    if (!request.headers.get('content-type')?.startsWith('application/json')) return profileError(415, 'JSON 형식이 필요합니다.');
    try {
        const input = await request.json();
        if (!input || typeof input !== 'object' || Array.isArray(input)) return profileError(400, '입력 내용을 확인해 주세요.');
        // 인증 주체와 권한·신뢰 온도 등은 클라이언트로부터 전달하지 않는다.
        const { nickname, tag, jobCategories, summary, career, collaborationStatus, contactHours } = input;
        return proxyProfile('/api/v1/users/me/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nickname, tag, jobCategories, summary, career, collaborationStatus, contactHours }) }, parseProfileDetail);
    } catch { return profileError(400, '입력 내용을 확인해 주세요.'); }
}

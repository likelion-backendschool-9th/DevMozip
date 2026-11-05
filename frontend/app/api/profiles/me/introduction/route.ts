import { parseIntroduction } from '@/lib/profiles/profileIntroductionApi';
import { proxyProfile, profileRequestGuard, profileError } from '@/lib/profiles/profileProxy';
export async function GET() {
    return proxyProfile('/api/v1/users/me/introduction', {}, parseIntroduction);
}
export async function PUT(request: Request) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    if (!request.headers.get('content-type')?.startsWith('application/json')) return profileError(415, 'JSON 형식이 필요합니다.');
    try {
        const input = await request.json();
        if (!input || typeof input.introduction !== 'string' || input.introduction.length > 20000
            || !['text', 'html'].includes(input.format)) return profileError(400, '자기소개는 20,000자 이내의 일반 텍스트 또는 HTML로 입력해 주세요.');
        return proxyProfile('/api/v1/users/me/introduction', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ introduction: input.introduction, format: input.format }) }, parseIntroduction);
    } catch { return profileError(400, '입력 내용을 확인해 주세요.'); }
}

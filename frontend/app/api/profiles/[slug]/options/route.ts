import { validProfileSlug } from '@/lib/profiles/profileDetail';
import { parseProfileOptions } from '@/lib/profiles/profileOptionsApi';
import { proxyProfile, profileError } from '@/lib/profiles/profileProxy';
export async function GET(_request: Request, context: { params: Promise<{ slug: string }> }) {
    const { slug } = await context.params;
    if (!validProfileSlug(slug)) return profileError(400, '올바른 사용자 태그가 필요합니다.');
    return proxyProfile(`/api/v1/users/${encodeURIComponent(slug)}/options`, {}, parseProfileOptions);
}

import { jsonRequest, messagingError, messagingWriteGuard, proxyMessaging, readJsonObject } from '@/lib/messaging/messagingProxy';
import { validProfileSlug } from '@/lib/profiles/profileDetail';

const REASON_ERROR = '신고 사유를 확인해 주세요.';

// 사유 목록의 기준은 서버다. 여기서는 형식만 확인하고 허용 여부는 백엔드가 판단한다.
export async function POST(request: Request, context: { params: Promise<{ slug: string }> }) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const { slug } = await context.params;
    if (!validProfileSlug(slug)) return messagingError(400, '신고 대상을 확인해 주세요.');
    const reason = (await readJsonObject(request))?.reason;
    if (typeof reason !== 'string' || !reason.trim() || reason.length > 100) return messagingError(400, REASON_ERROR);
    return proxyMessaging(`/api/v1/users/${encodeURIComponent(slug)}/reports`, jsonRequest('POST', { reason }), undefined, {
        400: '본인이거나 허용되지 않은 신고 사유예요.',
        404: '신고 대상을 찾을 수 없어요.',
        409: '이미 신고한 사용자예요.',
    });
}

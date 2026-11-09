import { parseFollowNotification } from '@/lib/profiles/followApi';
import { followPath, followSlugError, proxyFollow } from '@/lib/profiles/followProxy';
import { profileError, profileRequestGuard } from '@/lib/profiles/profileProxy';

// 내가 팔로우한 {slug}의 활동 알림 켜기·끄기(9.5). 새 게시글·새 프로젝트 모집 알림을 함께 바꾼다.
export async function PUT(request: Request, context: { params: Promise<{ slug: string }> }) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    const { slug } = await context.params;
    const invalid = followSlugError(slug);
    if (invalid) return invalid;
    if (!request.headers.get('content-type')?.startsWith('application/json')) return profileError(415, 'JSON 형식이 필요합니다.');
    let body;
    try { body = parseFollowNotification(await request.json()); }
    catch { return profileError(400, '알림 설정 값을 확인해 주세요.'); }
    return proxyFollow(followPath(slug, 'notify'), { method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body) }, parseFollowNotification, value => value.activityNotificationEnabled === body.activityNotificationEnabled,
        { 400: '알림 설정 값을 확인해 주세요.' });
}

import { profileRequestGuard, profileError, proxyProfile } from '@/lib/profiles/profileProxy';
type Context = { params: Promise<{ kind: string }> };
async function forward(request: Request, context: Context, upload: boolean) {
    const blocked = profileRequestGuard(request);
    if (blocked) return blocked;
    const { kind } = await context.params;
    if (!['profile', 'banner'].includes(kind)) return profileError(400, '올바른 이미지 종류가 필요합니다.');
    let body: FormData | undefined;
    if (upload) {
        if (Number(request.headers.get('content-length') ?? 0) > 1024 * 1024) return profileError(413, '이미지는 256KB 이하로 선택해 주세요.');
        try {
            const input = await request.formData();
            const file = input.get('file');
            if (!(file instanceof File) || file.size === 0) return profileError(400, '이미지 파일을 선택해 주세요.');
            if (file.size > 256 * 1024) return profileError(413, '이미지는 256KB 이하로 선택해 주세요.');
            if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) return profileError(415, 'PNG·JPEG·WebP 이미지를 선택해 주세요.');
            body = new FormData(); body.append('file', file);
        } catch { return profileError(400, '이미지 파일을 확인해 주세요.'); }
    }
    return proxyProfile(`/api/v1/users/me/images/${kind}`, { method: upload ? 'PUT' : 'DELETE', ...(body ? { body } : {}) }, value => {
        const result = value as Record<string, unknown>;
        if (!result || ![result.profileImage, result.bannerImage].every(v => v === null || typeof v === 'string')) throw new Error('이미지 응답 오류');
        return { profileImage: result.profileImage, bannerImage: result.bannerImage };
    });
}
export const PUT = (request: Request, context: Context) => forward(request, context, true);
export const DELETE = (request: Request, context: Context) => forward(request, context, false);

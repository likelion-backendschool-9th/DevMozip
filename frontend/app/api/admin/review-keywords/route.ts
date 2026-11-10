import { adminJson } from '@/lib/administration/adminApi';
import { checkKeywordsBody, parseAdminKeywords } from '@/lib/administration/adminKeywords';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

const parse = (raw: unknown) => ({ items: parseAdminKeywords(raw) });

// WBS 14.9 리뷰 키워드 목록 로드.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    return proxyAdmin('/api/v1/admin/review-keywords', {}, parse);
}

// WBS 14.9 리뷰 키워드 목록 수정. 활성으로 둘 이름 목록을 보낸다.
export async function PUT(request: Request) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const body = checkKeywordsBody(await readAdminBody(request));
    if (typeof body === 'string') return adminError(400, body);
    return proxyAdmin('/api/v1/admin/review-keywords', adminJson('PUT', body), parse);
}

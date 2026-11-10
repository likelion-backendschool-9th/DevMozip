import { adminQuery, parseAdminPage, withQuery } from '@/lib/administration/adminApi';
import { parseAdminReport, reportQueryRules } from '@/lib/administration/adminReports';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.8 신고 내역. 공고·팀원·사용자·게시글·댓글 신고를 한 목록으로 본다(D5).
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, reportQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/reports', query), {}, raw => parseAdminPage(raw, parseAdminReport));
}

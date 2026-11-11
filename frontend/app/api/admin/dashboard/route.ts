import { parseAdminDashboard } from '@/lib/administration/adminDashboard';
import { adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.1 관리자 대시보드.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    return proxyAdmin('/api/v1/admin/dashboard', {}, parseAdminDashboard);
}

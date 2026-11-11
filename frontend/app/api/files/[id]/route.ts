import { backendFetch } from '@/lib/auth/backend';
import { PORTFOLIO_FILE_ID } from '@/lib/portfolios/portfolioFiles';
import { portfolioError } from '@/lib/portfolios/portfolioProxy';

const TYPES: Record<string, string> = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'application/pdf': 'pdf' };

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
    const { id } = await context.params;
    if (!PORTFOLIO_FILE_ID.test(id)) return portfolioError(404, '파일을 찾을 수 없습니다.');
    try {
        const response = await backendFetch(`/api/v1/files/${id}`);
        if (!response.ok) return portfolioError([401,403,404].includes(response.status) ? response.status : 502, '파일을 찾을 수 없거나 열람 권한이 없습니다.');
        const type = response.headers.get('content-type')?.split(';')[0];
        if (!type || !TYPES[type]) return portfolioError(502, '파일 형식을 확인할 수 없습니다.');
        const headers: Record<string, string> = {
            'Content-Type': type, 'Content-Disposition': response.headers.get('content-disposition') || 'attachment',
            'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': 'sandbox',
        };
        // nginx가 있는 환경: 백엔드가 권한을 확인한 뒤 파일 위치만 준다. nginx가 이 헤더를 받아 내부 전용 경로에서 파일을 보낸다.
        // 이 요청의 파일 하나만 가리키는지 확인하고 넘긴다 (docs/portfolio-files-api.md).
        const accel = response.headers.get('x-accel-redirect');
        if (accel !== null) {
            if (accel !== `/internal-files/${id}.${TYPES[type]}`) return portfolioError(502, '파일 위치를 확인할 수 없습니다.');
            await response.body?.cancel();
            return new Response(null, { headers: { ...headers, 'X-Accel-Redirect': accel } });
        }
        return new Response(response.body, { headers });
    } catch { return portfolioError(502, '파일 서버에 연결하지 못했어요.'); }
}

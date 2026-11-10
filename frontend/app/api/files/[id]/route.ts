import { backendFetch } from '@/lib/auth/backend';
import { portfolioError } from '@/lib/portfolios/portfolioProxy';

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
    const { id } = await context.params;
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) return portfolioError(404, '파일을 찾을 수 없습니다.');
    try {
        const response = await backendFetch(`/api/v1/files/${id}`);
        if (!response.ok) return portfolioError([401,403,404].includes(response.status) ? response.status : 502, '파일을 찾을 수 없거나 열람 권한이 없습니다.');
        const type = response.headers.get('content-type')?.split(';')[0];
        if (!type || !['image/png','image/jpeg','image/webp','application/pdf'].includes(type)) return portfolioError(502, '파일 형식을 확인할 수 없습니다.');
        return new Response(response.body, { headers: {
            'Content-Type': type, 'Content-Disposition': response.headers.get('content-disposition') || 'attachment',
            'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': 'sandbox',
        } });
    } catch { return portfolioError(502, '파일 서버에 연결하지 못했어요.'); }
}

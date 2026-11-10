import { backendFetch } from '@/lib/auth/backend';
import { portfolioWriteGuard } from '@/lib/portfolios/portfolioWriteProxy';
import { portfolioError } from '@/lib/portfolios/portfolioProxy';
import { parsePortfolioFile, PDF_MAX } from '@/lib/portfolios/portfolioFiles';

export async function POST(request: Request) {
    const blocked = portfolioWriteGuard(request, false); if (blocked) return blocked;
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('multipart/form-data;')) return portfolioError(415, '파일 업로드 형식을 확인해 주세요.');
    try {
        const reader = request.body?.getReader(); if (!reader) return portfolioError(400, '파일을 선택해 주세요.');
        const parts: Uint8Array[] = []; let size = 0;
        while (true) {
            const { done, value } = await reader.read(); if (done) break;
            size += value.length; if (size > PDF_MAX + 1024 * 1024) { await reader.cancel(); return portfolioError(413, '파일 크기 제한을 초과했습니다.'); }
            parts.push(value);
        }
        const bytes = new Uint8Array(size); let offset = 0; for (const part of parts) { bytes.set(part, offset); offset += part.length; }
        let form: FormData;
        try { form = await new Response(bytes, { headers: { 'Content-Type': request.headers.get('content-type')! } }).formData(); }
        catch { return portfolioError(400, '파일 업로드 요청을 확인해 주세요.'); }
        const file = form.get('file');
        if (!(file instanceof File) || form.getAll('file').length !== 1 || file.size > PDF_MAX) return portfolioError(file instanceof File && file.size > PDF_MAX ? 413 : 400, '파일 한 개를 선택해 주세요.');
        const forwarded = new FormData(); forwarded.set('file', file);
        const response = await backendFetch('/api/v1/files', { method: 'POST', body: forwarded, signal: AbortSignal.timeout(30000) });
        if (!response.ok) {
            const messages: Record<number, string> = { 400: '파일 이름과 내용을 확인해 주세요.', 401: '로그인 후 업로드해 주세요.', 403: '파일 업로드 권한이 없습니다.', 413: '이미지는 256KB, PDF는 10MB 이하여야 합니다.', 415: 'PNG·JPEG·WebP·PDF만 업로드할 수 있습니다.' };
            return portfolioError(messages[response.status] ? response.status : 502, messages[response.status] || '파일 저장소에 연결하지 못했어요.');
        }
        return Response.json(parsePortfolioFile(await response.json()), { status: 201, headers: { 'Cache-Control': 'no-store' } });
    } catch { return portfolioError(502, '파일 업로드를 완료하지 못했어요. 저장 상태를 확인하고 다시 시도해 주세요.'); }
}

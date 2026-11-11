import 'server-only';
import { portfolioError } from './portfolioProxy';
const MAX = 2 * 1024 * 1024;
export function portfolioWriteGuard(request: Request, body = true) {
    // Next의 Docker 내부 URL 대신 브라우저가 요청한 Host를 비교한다.
    // x-forwarded-host는 출처 검증에 사용하지 않는다.
    const target = new URL(request.url);
    const host = request.headers.get('host');
    if (host) {
        try {
            const external = new URL(`${target.protocol}//${host}`);
            if (external.host !== host || external.username || external.password || external.pathname !== '/' || external.search || external.hash) throw new Error('host');
            // host 대입은 포트 없는 값이면 내부 포트(:3000)를 남긴다. HTTPS 기본 포트 주소가 맞도록 hostname과 port를 함께 바꾼다.
            target.hostname = external.hostname;
            target.port = external.port;
        } catch { return portfolioError(403, '같은 사이트에서 요청해 주세요.'); }
    }
    if (request.headers.get('origin') !== target.origin) return portfolioError(403, '같은 사이트에서 요청해 주세요.');
    if (body && !request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return portfolioError(415, 'JSON 형식이 필요합니다.');
    return null;
}
export async function portfolioWriteInput(request: Request): Promise<Record<string, unknown>> {
    const reader = request.body?.getReader(); if (!reader) throw new Error('body');
    const parts: Uint8Array[] = []; let size = 0;
    while (true) { const { done, value } = await reader.read(); if (done) break; size += value.byteLength;
        if (size > MAX) { await reader.cancel(); throw new RangeError('size'); } parts.push(value); }
    const bytes = new Uint8Array(size); let offset = 0; for (const part of parts) { bytes.set(part, offset); offset += part.length; }
    const input: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('body');
    const v = input as Record<string, unknown>;
    const { title, thumbnailUrl, topic, summary, description, role, techStack, attachments, visibility, document, expectedUpdatedAt } = v;
    return { title, thumbnailUrl, topic, summary, description, role, techStack, attachments, visibility, document, expectedUpdatedAt };
}

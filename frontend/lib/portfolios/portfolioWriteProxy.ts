import 'server-only';
import { portfolioError } from './portfolioProxy';
const MAX = 2 * 1024 * 1024;
export function portfolioWriteGuard(request: Request, body = true) {
    if (request.headers.get('origin') !== new URL(request.url).origin) return portfolioError(403, '같은 사이트에서 요청해 주세요.');
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

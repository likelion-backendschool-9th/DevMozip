export interface PortfolioFile { id: string; url: string; name: string; contentType: string; size: number }
export const IMAGE_MAX = 256 * 1024;
export const PDF_MAX = 10 * 1024 * 1024;
export function parsePortfolioFile(value: unknown): PortfolioFile {
    if (!value || typeof value !== 'object') throw new Error('파일 응답 형식을 확인할 수 없어요.');
    const v = value as Record<string, unknown>;
    if (typeof v.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v.id)
        || v.url !== `/api/files/${v.id}` || typeof v.name !== 'string' || !v.name || v.name.length > 255
        || !['image/png', 'image/jpeg', 'image/webp', 'application/pdf'].includes(v.contentType as string)
        || typeof v.size !== 'number' || !Number.isSafeInteger(v.size) || v.size <= 0
        || v.size > (v.contentType === 'application/pdf' ? PDF_MAX : IMAGE_MAX)) throw new Error('파일 응답 형식을 확인할 수 없어요.');
    return { id: v.id, url: v.url as string, name: v.name, contentType: v.contentType as string, size: v.size };
}
export async function uploadPortfolioFile(file: File, kind: 'image' | 'pdf' = 'image', signal?: AbortSignal): Promise<PortfolioFile> {
    const types = kind === 'image' ? ['image/png', 'image/jpeg', 'image/webp'] : ['application/pdf'];
    if (!types.includes(file.type)) throw new Error(kind === 'image' ? 'PNG·JPEG·WebP 이미지를 선택해 주세요.' : 'PDF 파일을 선택해 주세요.');
    if (!file.size || file.size > (kind === 'image' ? IMAGE_MAX : PDF_MAX)) throw new Error(kind === 'image' ? '이미지는 256KB 이하여야 합니다.' : 'PDF는 10MB 이하여야 합니다.');
    const form = new FormData(); form.set('file', file);
    const response = await fetch('/api/files', { method: 'POST', body: form, cache: 'no-store', signal });
    if (!response.ok) {
        const value = await response.json().catch(() => null);
        throw new Error(typeof value?.message === 'string' ? value.message : '파일 업로드에 실패했어요. 입력은 유지됩니다.');
    }
    const uploaded = parsePortfolioFile(await response.json());
    if (!types.includes(uploaded.contentType)) throw new Error('업로드 결과의 파일 형식을 확인해 주세요.');
    return uploaded;
}

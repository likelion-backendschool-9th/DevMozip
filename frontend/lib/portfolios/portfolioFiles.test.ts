import { afterEach, describe, expect, it, vi } from 'vitest';
import { IMAGE_MAX, PDF_MAX, parsePortfolioFile, uploadPortfolioFile } from './portfolioFiles';
const id = '12345678-1234-1234-1234-123456789abc';
const value = () => ({ id, url: `/api/files/${id}`, name: 'image.png', contentType: 'image/png', size: 10 });
afterEach(() => vi.unstubAllGlobals());
describe('포트폴리오 서버 파일 업로드', () => {
    it('FormData로 업로드하고 서버 경로만 반환한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json(value(), { status: 201 })); vi.stubGlobal('fetch', fetch);
        const file = new File(['png'], 'image.png', { type: 'image/png' });
        expect(await uploadPortfolioFile(file)).toEqual(value());
        const [path, init] = fetch.mock.calls[0]; expect(path).toBe('/api/files');
        expect(init.body.get('file')).toBe(file); expect(init.headers).toBeUndefined();
    });
    it.each([
        ['text/html', 'a.html', 1, 'image'], ['image/png', 'a.png', IMAGE_MAX + 1, 'image'],
        ['application/pdf', 'a.pdf', PDF_MAX + 1, 'pdf'], ['application/pdf', 'a.pdf', 0, 'pdf'],
    ] as const)('형식·크기 오류는 업로드하지 않는다 (%s/%i)', async (type, name, size, kind) => {
        const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
        await expect(uploadPortfolioFile(new File([new Uint8Array(size)], name, { type }), kind)).rejects.toThrow();
        expect(fetch).not.toHaveBeenCalled();
    });
    it.each([{ ...value(), url: 'https://evil.test/file' }, { ...value(), id: '../bad' }, { ...value(), size: -1 }, { ...value(), contentType: 'text/html' }])('응답 경로·형식 조작을 거절한다', raw => {
        expect(() => parsePortfolioFile(raw)).toThrow();
    });
    it('실패를 성공으로 바꾸지 않고 재제출은 사용자에게 맡긴다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json({ message: '로그인이 필요합니다.' }, { status: 401 })); vi.stubGlobal('fetch', fetch);
        await expect(uploadPortfolioFile(new File(['png'], 'a.png', { type: 'image/png' }))).rejects.toThrow('로그인이 필요합니다.');
        expect(fetch).toHaveBeenCalledTimes(1);
    });
});

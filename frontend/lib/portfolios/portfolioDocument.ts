import type { PortfolioBlock, PortfolioInput } from '@/types/profile';
export interface PortfolioDocument {
    schema: 'devmozip.portfolio.v1'; start: string; end: string; teamSize: string | null; fields: string[]; blocks: PortfolioBlock[];
}
const obj = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('문서 형식 오류'); return v as Record<string, unknown>; };
const string = (v: unknown, max: number): string => { if (typeof v !== 'string' || v.length > max) throw new Error('문서 텍스트 오류'); return v; };
const url = (v: unknown) => {
    const s = string(v, 2048);
    if (!s || /[\x00-\x20\x7f\\]/.test(s)) throw new Error('문서 링크 오류');
    if (s.startsWith('/') && !s.startsWith('//')) return s;
    const u = new URL(s); if (!['http:', 'https:'].includes(u.protocol) || u.username || u.password) throw new Error('문서 링크 오류'); return s;
};
export function parsePortfolioDocument(value: unknown, draft = false): PortfolioDocument {
    const v = obj(value);
    if (v.schema !== 'devmozip.portfolio.v1' || !Array.isArray(v.fields) || v.fields.length > 30
        || !Array.isArray(v.blocks) || v.blocks.length < 1 || v.blocks.length > 20) throw new Error('문서 형식 오류');
    const start = string(v.start, 7), end = string(v.end, 7);
    if (!draft && (!/^\d{4}-(0[1-9]|1[0-2])$/.test(start) || !/^\d{4}-(0[1-9]|1[0-2])$/.test(end) || end < start)) throw new Error('진행 기간 오류');
    const fields = v.fields.map(x => string(x, 40));
    if (new Set(fields.map(x => x.trim().toLowerCase())).size !== fields.length || !draft && fields.some(x => !x.trim())) throw new Error('분야 중복 오류');
    let total = 0;
    const blocks = v.blocks.map(value => {
        const b = obj(value), id = string(b.id, 80), title = string(b.title, 80), content = string(b.content, 20000);
        total += content.length;
        if (!/^[a-zA-Z0-9_-]{1,80}$/.test(id) || !draft && !title.trim() || !['text', 'gallery', 'links'].includes(b.kind as string)
            || !['text', 'markdown', 'html'].includes(b.format as string) || !draft && total > 150000) throw new Error('섹션 형식 오류');
        if (b.images !== undefined && (!Array.isArray(b.images) || b.images.length > 6)
            || b.links !== undefined && (!Array.isArray(b.links) || b.links.length > 6)) throw new Error('첨부 개수 오류');
        const images = ((b.images || []) as unknown[]).map(value => { const i = obj(value); return { src: draft ? string(i.src, 400000) : url(i.src), caption: string(i.caption, 150) }; });
        const links = ((b.links || []) as unknown[]).map(value => { const l = obj(value); return { label: string(l.label, 40), url: draft ? string(l.url, 2048) : url(l.url) }; });
        if (!draft && links.some(l => !l.label.trim())) throw new Error('링크 이름 오류');
        const source = b.source == null ? undefined : b.source as PortfolioBlock['source'];
        if (source !== undefined && !['description', 'contribution', 'features', 'challenge', 'outcome'].includes(source)) throw new Error('섹션 연결 오류');
        return { id, title, content, kind: b.kind as PortfolioBlock['kind'], format: b.format as PortfolioBlock['format'], images, links, ...(source ? { source } : {}) };
    });
    if (new Set(blocks.map(b => b.id)).size !== blocks.length || new Set(blocks.filter(b => b.source).map(b => b.source)).size !== blocks.filter(b => b.source).length) throw new Error('섹션 중복 오류');
    return { schema: v.schema, start, end, teamSize: v.teamSize == null ? null : string(v.teamSize, 100), fields, blocks };
}
export function portfolioDocument(input: PortfolioInput): PortfolioDocument {
    return parsePortfolioDocument({ schema: 'devmozip.portfolio.v1', start: input.start, end: input.end, teamSize: input.teamSize || null, fields: input.fields || [], blocks: input.blocks });
}
export function apiPortfolioErrors(input: PortfolioInput): string[] {
    const errors: string[] = [];
    if (!input.title.trim() || input.title.length > 100) errors.push('프로젝트명은 1~100자입니다.');
    if (!input.summary.trim() || input.summary.length > 200) errors.push('한 줄 소개는 1~200자입니다.');
    if (!input.role.trim() || input.role.length > 100) errors.push('담당 역할은 1~100자입니다.');
    if (!input.stacks.length || input.stacks.length > 30 || input.stacks.some(s => !s.trim() || s.length > 100)
        || new Set(input.stacks.map(s => s.trim().toLowerCase())).size !== input.stacks.length) errors.push('기술 스택은 중복 없이 1~30개 선택해 주세요.');
    try { portfolioDocument(input); } catch (e) { errors.push(e instanceof Error ? e.message : '본문을 확인해 주세요.'); }
    if (input.coverImage) { try { url(input.coverImage); } catch { errors.push('대표 이미지는 저장된 이미지 URL을 사용해 주세요. 파일 업로드는 후속 기능입니다.'); } }
    return errors;
}

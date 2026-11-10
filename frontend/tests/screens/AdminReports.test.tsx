import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AdminReportsView, HandleForm } from '@/app/admin/reports/_components/ApiAdminReports';
import { reportTargetHref } from '@/lib/administration/adminReports';
import type { AdminReport } from '@/types/adminApi';

const noop = () => {};
const base: AdminReport = {
    id: 5, type: 'POST', reporter: { id: 2, tag: 'reporter', nickname: '신고자', withdrawn: false },
    target: { projectId: null, projectTitle: null, user: { id: 3, tag: 'target', nickname: '대상자', withdrawn: false }, postId: 40, commentId: null, content: '신고된 글', deleted: false },
    reason: '스팸', status: 'AWATING', resolution: null, handledByNickname: null, handledAt: null, createdAt: '2026-11-10T10:00:00',
};
const view = (items: AdminReport[]) => renderToStaticMarkup(<AdminReportsView load={{ status: 'ready', data: { items, totalElements: items.length, page: 0, totalPages: 1 } }}
    onRetry={noop} onPage={noop} onHandle={noop} />);

describe('서버 신고 내역 화면 (14.8)', () => {
    it('종류·대상·신고자와 처리 버튼을 보여 준다', () => {
        const html = view([base]);
        expect(html).toContain('게시글');
        expect(html).toContain('href="/feeds/40"');
        expect(html).toContain('대상 회원: 대상자');
        expect(html).toContain('신고자 신고자 · 접수 · 2026-11-10 10:00');
        expect(html).toContain('검토 및 처리');
    });
    it('처리가 끝난 신고는 결과와 처리자를 보여 주고 버튼이 없다', () => {
        const html = view([{ ...base, status: 'RESOLVED', resolution: '숨김 처리', handledByNickname: '운영자', handledAt: '2026-11-10T11:00:00' }]);
        expect(html).toContain('처리 결과: 숨김 처리');
        expect(html).toContain('처리자 운영자 · 2026-11-10 11:00');
        expect(html).not.toContain('검토 및 처리');
    });
    it('탈퇴 회원과 삭제된 대상을 표시하고 링크를 두지 않는다', () => {
        const report: AdminReport = { ...base, type: 'USER', reporter: { ...base.reporter, tag: null, withdrawn: true },
            target: { ...base.target, postId: null, content: null, user: { id: 3, tag: null, nickname: '대상자', withdrawn: true } } };
        const html = view([report, { ...base, id: 6, target: { ...base.target, deleted: true } }]);
        expect(html).toContain('신고자 신고자 (탈퇴)');
        expect(html).toContain('<b>대상자 (탈퇴)</b>');
        expect(html).toContain('대상이 삭제되었습니다.');
        expect(reportTargetHref({ ...base, target: { ...base.target, deleted: true } })).toBeNull();
        expect(reportTargetHref({ ...base, type: 'PROJECT', target: { ...base.target, projectId: 30, user: null } })).toBe('/projects/30');
        expect(reportTargetHref({ ...base, type: 'COMMENT', target: { ...base.target, commentId: 50 } })).toBe('/feeds/40');
    });
    it('완료·기각은 결과를 입력해야 저장할 수 있다', () => {
        const form = (decision: 'REVIEWING' | 'RESOLVED', note = '') => renderToStaticMarkup(
            <HandleForm report={base} decision={decision} note={note} busy={false} error="" onDecision={noop} onNote={noop} onSubmit={noop} />);
        expect(form('REVIEWING')).not.toMatch(/<button[^>]*disabled/);
        expect(form('RESOLVED')).toMatch(/<button[^>]*disabled[^>]*>결과 저장/);
        expect(form('RESOLVED')).toContain('다시 처리할 수 없습니다');
        expect(form('RESOLVED', '안내 완료')).not.toMatch(/<button[^>]*disabled/);
    });
});

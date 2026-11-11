'use client';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { AdminActionError, AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { adminJson, adminRequest, adminTime, parseAdminPage } from '@/lib/administration/adminApi';
import {
    parseAdminReport, partyName, reportFinal, reportStatuses, reportStatusLabels, reportTargetHref, reportTypeLabels, reportTypes, RESOLUTION_MAX,
    type HandleBody,
} from '@/lib/administration/adminReports';
import { useAdminAction, useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminPage, AdminReport } from '@/types/adminApi';

const parsePage = (raw: unknown) => parseAdminPage(raw, parseAdminReport);

/** Mock 토글 OFF의 신고 내역 처리 (WBS 14.8, D5). 다섯 종류의 신고를 한 목록에서 처리한다. */
export function ApiAdminReports() {
    const [filters, setFilters] = useState({ type: '', status: '' });
    const [page, setPage] = useState(0);
    const query = new URLSearchParams({ page: String(page), size: '20' });
    for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
    const { load, reload } = useAdminResource(`/reports?${query}`, parsePage);
    const [selected, setSelected] = useState<AdminReport | null>(null);
    const [decision, setDecision] = useState<HandleBody['status']>('REVIEWING');
    const [note, setNote] = useState('');
    const action = useAdminAction();

    const change = (next: Partial<typeof filters>) => { setFilters(f => ({ ...f, ...next })); setPage(0); };
    const open = (report: AdminReport) => { setSelected(report); setDecision('REVIEWING'); setNote(''); action.clearError(); };
    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (!selected) return;
        const body = { status: decision, ...(note.trim() ? { resolution: note.trim() } : {}) };
        const done = await action.run(() => adminRequest(`/reports/${selected.id}`, adminJson('PATCH', body)));
        if (done) { setSelected(null); reload(); }
    };

    return (
        <section className={cls('governance-panel admin-main')} aria-label="신고 내역 처리">
            <h2>신고 내역 처리</h2>
            <p>공고·팀원·사용자·게시글·댓글 신고를 검토합니다. 처리 결과 저장만으로 제재·숨김·삭제하지 않습니다. 조치가 필요하면 회원·공고·게시글 관리에서 따로 합니다.</p>
            <div className={cls('admin-filters')}>
                <label>
                    신고 종류
                    <select className="form-input" value={filters.type} onChange={e => change({ type: e.target.value })}>
                        <option value="">전체</option>
                        {reportTypes.map(t => <option key={t} value={t}>{reportTypeLabels[t]}</option>)}
                    </select>
                </label>
                <label>
                    처리 상태
                    <select className="form-input" value={filters.status} onChange={e => change({ status: e.target.value })}>
                        <option value="">전체</option>
                        {reportStatuses.map(s => <option key={s} value={s}>{reportStatusLabels[s]}</option>)}
                    </select>
                </label>
            </div>
            <AdminReportsView load={load} onRetry={reload} onPage={setPage} onHandle={open} />
            {selected && (
                <Modal title="신고 검토 결과" onClose={() => { if (!action.busy) setSelected(null); }}>
                    <HandleForm report={selected} decision={decision} note={note} busy={action.busy} error={action.error}
                        onDecision={setDecision} onNote={setNote} onSubmit={submit} />
                </Modal>
            )}
        </section>
    );
}

export function AdminReportsView({ load, onRetry, onPage, onHandle }:
    { load: AdminLoad<AdminPage<AdminReport>>; onRetry: () => void; onPage: (page: number) => void; onHandle: (report: AdminReport) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry}>
            {data => (
                <>
                    <h3>신고 <small>{data.totalElements}건</small></h3>
                    {!data.items.length && <p>조건에 맞는 신고가 없습니다.</p>}
                    {data.items.map(report => <ReportRow key={report.id} report={report} onHandle={onHandle} />)}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

function ReportRow({ report, onHandle }: { report: AdminReport; onHandle: (report: AdminReport) => void }) {
    const href = reportTargetHref(report);
    const { target } = report;
    const title = target.projectTitle ?? target.content ?? (target.user ? partyName(target.user) : `#${report.id}`);
    return (
        <article className={cls('admin-row')}>
            <div>
                <span className="demo-pill">{reportTypeLabels[report.type]}</span>{' '}
                {href ? <Link href={href}>{title}</Link> : <b>{title}</b>}
                {target.deleted && <small>대상이 삭제되었습니다.</small>}
                {target.user && report.type !== 'USER' && <small>대상 회원: {partyName(target.user)}</small>}
                <p className="preserve-text">{report.reason}</p>
                <small>신고자 {partyName(report.reporter)} · {reportStatusLabels[report.status]} · {adminTime(report.createdAt)}</small>
                {report.resolution && <small>처리 결과: {report.resolution}</small>}
                {report.handledAt && <small>처리자 {report.handledByNickname ?? '-'} · {adminTime(report.handledAt)}</small>}
            </div>
            <div className={cls('governance-actions')}>
                {!reportFinal(report) && <Button variant="secondary" onClick={() => onHandle(report)}>검토 및 처리</Button>}
            </div>
        </article>
    );
}

export function HandleForm({ report, decision, note, busy, error, onDecision, onNote, onSubmit }: {
    report: AdminReport; decision: HandleBody['status']; note: string; busy: boolean; error: string;
    onDecision: (value: HandleBody['status']) => void; onNote: (value: string) => void; onSubmit: (event: FormEvent) => void;
}) {
    const required = decision !== 'REVIEWING';
    return (
        <form className="profile-form" onSubmit={onSubmit}>
            <p>{reportTypeLabels[report.type]} 신고 · {reportStatusLabels[report.status]}</p>
            <label>
                처리 상태
                <select className="form-input" value={decision} onChange={e => onDecision(e.target.value as HandleBody['status'])}>
                    {(['REVIEWING', 'RESOLVED', 'REJECTED'] as const).map(s => <option key={s} value={s}>{reportStatusLabels[s]}</option>)}
                </select>
            </label>
            <label>
                처리 결과 {required ? '(필수)' : '(선택)'}
                <textarea className="form-input" rows={4} maxLength={RESOLUTION_MAX} required={required} value={note} onChange={e => onNote(e.target.value)} />
            </label>
            {required && <p>완료·기각한 신고는 다시 처리할 수 없습니다.</p>}
            <AdminActionError message={error} />
            <Button type="submit" disabled={busy || (required && !note.trim())}>{busy ? '저장 중…' : '결과 저장'}</Button>
        </form>
    );
}

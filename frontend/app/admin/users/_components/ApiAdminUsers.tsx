'use client';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { AdminActionError, AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { adminJson, adminRequest, adminTime, parseAdminPage } from '@/lib/administration/adminApi';
import {
    memberRoleLabels, memberRoles, memberStatusLabels, memberStatuses, parseAdminMember, sanctionActionLabels, sanctionActions, SANCTION_REASON_MAX,
} from '@/lib/administration/adminMembers';
import { useAdminAction, useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminMember, AdminMemberStatus, AdminPage } from '@/types/adminApi';

const parsePage = (raw: unknown) => parseAdminPage(raw, parseAdminMember);
type Target = { member: AdminMember; status: AdminMemberStatus };

/** Mock 토글 OFF의 회원 관리 (WBS 14.2·14.3). 서버의 회원 목록과 제재 API를 쓴다. */
export function ApiAdminUsers() {
    const [draft, setDraft] = useState('');
    const [filters, setFilters] = useState({ q: '', status: '', role: '' });
    const [page, setPage] = useState(0);
    const query = new URLSearchParams({ page: String(page), size: '20' });
    for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
    const { load, reload } = useAdminResource(`/users?${query}`, parsePage);
    const [target, setTarget] = useState<Target | null>(null);
    const [reason, setReason] = useState('');
    const action = useAdminAction();

    const change = (next: Partial<typeof filters>) => { setFilters(current => ({ ...current, ...next })); setPage(0); };
    const search = (event: FormEvent) => { event.preventDefault(); change({ q: draft.trim() }); };
    const open = (member: AdminMember, status: AdminMemberStatus) => { setReason(''); action.clearError(); setTarget({ member, status }); };
    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (!target?.member.tag) return;
        const body = target.status === 'ACTIVE' ? { status: target.status } : { status: target.status, reason: reason.trim() };
        const done = await action.run(() => adminRequest(`/users/${encodeURIComponent(target.member.tag!)}/ban`, adminJson('PATCH', body)));
        if (done) { setTarget(null); reload(); }
    };

    return (
        <>
            <section className={cls('governance-panel admin-main')} aria-label="회원 관리">
                <form className={cls('admin-filters')} onSubmit={search}>
                    <label className="admin-filter-wide">
                        검색
                        <input className="form-input" value={draft} onChange={e => setDraft(e.target.value)} placeholder="닉네임·이메일·태그" />
                    </label>
                    <label>
                        상태
                        <select className="form-input" value={filters.status} onChange={e => change({ status: e.target.value })}>
                            <option value="">전체</option>
                            {memberStatuses.map(s => <option key={s} value={s}>{memberStatusLabels[s]}</option>)}
                        </select>
                    </label>
                    <label>
                        역할
                        <select className="form-input" value={filters.role} onChange={e => change({ role: e.target.value })}>
                            <option value="">전체</option>
                            {memberRoles.map(r => <option key={r} value={r}>{memberRoleLabels[r]}</option>)}
                        </select>
                    </label>
                    <Button type="submit" variant="secondary">검색</Button>
                </form>
                <AdminUsersView load={load} onRetry={reload} onPage={setPage} onSanction={open} />
            </section>
            {target && (
                <Modal title="회원 상태 변경" onClose={() => { if (!action.busy) setTarget(null); }}>
                    <SanctionForm target={target} reason={reason} busy={action.busy} error={action.error} onReason={setReason} onSubmit={submit} />
                </Modal>
            )}
        </>
    );
}

export function AdminUsersView({ load, onRetry, onPage, onSanction }:
    { load: AdminLoad<AdminPage<AdminMember>>; onRetry: () => void; onPage: (page: number) => void; onSanction: (member: AdminMember, status: AdminMemberStatus) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry}>
            {data => (
                <>
                    <h2>회원 목록 <small>{data.totalElements}명</small></h2>
                    <p>경고는 안내 상태이며, 정지는 해당 계정의 로그인과 기능 사용을 바로 막습니다. 탈퇴한 회원은 목록에 나오지 않습니다.</p>
                    {!data.items.length && <p>조건에 맞는 회원이 없습니다.</p>}
                    {data.items.map(member => (
                        <article className={cls('admin-row')} key={member.id}>
                            <div>
                                {member.tag ? <Link href={`/users/${member.tag}`}>{member.nickname}</Link> : <b>{member.nickname}</b>}{' '}
                                {member.role && <span className="demo-pill">{memberRoleLabels[member.role]}</span>}
                                <small>
                                    {member.email ?? '이메일 없음'} · {member.status ? memberStatusLabels[member.status] : '-'}
                                    {!member.tag && ' · 태그 없음(제재하려면 회원이 태그를 정해야 합니다)'}
                                </small>
                                {member.latestSanction && (
                                    <small>
                                        최근 조치: {sanctionLabel(member.latestSanction.type)} · {adminTime(member.latestSanction.startsAt)} · {member.latestSanction.adminNickname}
                                        {member.latestSanction.reason && <> · 사유: {member.latestSanction.reason}</>}
                                        {member.latestSanction.revokedAt && <> · 해제 {adminTime(member.latestSanction.revokedAt)}</>}
                                    </small>
                                )}
                            </div>
                            <div className={cls('governance-actions')}>
                                {sanctionActions(member).map(status => (
                                    <Button variant="secondary" key={status} onClick={() => onSanction(member, status)}>{sanctionActionLabels[status]}</Button>
                                ))}
                            </div>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

const sanctionLabel = (type: 'WARNED' | 'SUSPENDED') => ({ WARNED: '경고', SUSPENDED: '정지' })[type];

export function SanctionForm({ target, reason, busy, error, onReason, onSubmit }:
    { target: Target; reason: string; busy: boolean; error: string; onReason: (value: string) => void; onSubmit: (event: FormEvent) => void }) {
    const required = target.status !== 'ACTIVE';
    return (
        <form className="profile-form" onSubmit={onSubmit}>
            <p>{target.member.nickname} · {sanctionActionLabels[target.status]}</p>
            {target.status === 'SUSPENDED' && <p>정지하면 이 회원의 로그인과 모든 기능 사용이 바로 막힙니다.</p>}
            {required ? (
                <label>
                    사유 (필수)
                    <textarea className="form-input" rows={4} maxLength={SANCTION_REASON_MAX} required value={reason} onChange={e => onReason(e.target.value)} />
                </label>
            ) : <p>해제하면 지금 유효한 경고·정지가 철회됩니다. 제재 기록은 남습니다.</p>}
            <AdminActionError message={error} />
            <Button type="submit" disabled={busy || (required && !reason.trim())}>{busy ? '변경 중…' : '변경 확인'}</Button>
        </form>
    );
}

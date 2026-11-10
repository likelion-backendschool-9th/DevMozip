'use client';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { AdminActionError, AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { adminJson, adminRequest, adminTime, parseAdminPage } from '@/lib/administration/adminApi';
import { parseAdminProject, projectDeletable, projectStatuses, projectStatusLabels } from '@/lib/administration/adminProjects';
import { useAdminAction, useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminPage, AdminProject } from '@/types/adminApi';

const parsePage = (raw: unknown) => parseAdminPage(raw, parseAdminProject);

/** Mock 토글 OFF의 프로젝트 공고 관리 (WBS 14.4·14.5). */
export function ApiAdminProjects() {
    const [draft, setDraft] = useState('');
    const [filters, setFilters] = useState({ q: '', status: '', restricted: '' });
    const [page, setPage] = useState(0);
    const query = new URLSearchParams({ page: String(page), size: '20' });
    for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
    const { load, reload } = useAdminResource(`/projects?${query}`, parsePage);
    const [removing, setRemoving] = useState<AdminProject | null>(null);
    const action = useAdminAction();

    const change = (next: Partial<typeof filters>) => { setFilters(f => ({ ...f, ...next })); setPage(0); };
    const search = (event: FormEvent) => { event.preventDefault(); change({ q: draft.trim() }); };
    const restrict = async (project: AdminProject) => {
        const done = await action.run(() => adminRequest(`/projects/${project.id}/visibility`, adminJson('PATCH', { restricted: !project.restricted })));
        if (done) reload();
    };
    const remove = async () => {
        if (!removing) return;
        const done = await action.run(() => adminRequest(`/projects/${removing.id}`, adminJson('PUT', { action: 'DELETE' })));
        if (done) { setRemoving(null); reload(); }
    };

    return (
        <section className={cls('governance-panel admin-main')} aria-label="프로젝트 공고 관리">
            <h2>프로젝트 공고 관리</h2>
            <p>관리자 숨김 공고는 목록·검색·북마크에서 빠지고 현재 팀원만 열 수 있습니다. 주최자는 관리자 숨김을 풀 수 없습니다. 삭제는 모집 중인 공고만 되며 되돌릴 수 없습니다.</p>
            <form className={cls('admin-filters')} onSubmit={search}>
                <label className="admin-filter-wide">
                    검색
                    <input className="form-input" value={draft} onChange={e => setDraft(e.target.value)} placeholder="공고 제목" />
                </label>
                <label>
                    상태
                    <select className="form-input" value={filters.status} onChange={e => change({ status: e.target.value })}>
                        <option value="">전체</option>
                        {projectStatuses.map(s => <option key={s} value={s}>{projectStatusLabels[s]}</option>)}
                    </select>
                </label>
                <label>
                    관리자 숨김
                    <select className="form-input" value={filters.restricted} onChange={e => change({ restricted: e.target.value })}>
                        <option value="">전체</option>
                        <option value="true">숨김</option>
                        <option value="false">숨기지 않음</option>
                    </select>
                </label>
                <Button type="submit" variant="secondary">검색</Button>
            </form>
            {!removing && <AdminActionError message={action.error} />}
            <AdminProjectsView load={load} busy={action.busy} onRetry={reload} onPage={setPage} onRestrict={restrict}
                onDelete={project => { action.clearError(); setRemoving(project); }} />
            {removing && (
                <Modal title="공고를 삭제할까요?" onClose={() => { if (!action.busy) setRemoving(null); }}>
                    <p>{removing.title}</p>
                    <p>삭제하면 되돌릴 수 없고, 목록과 상세에서 바로 사라집니다. 지원·팀원 이력은 남습니다.</p>
                    <AdminActionError message={action.error} />
                    <div className={cls('governance-actions')}>
                        <Button variant="secondary" disabled={action.busy} onClick={() => setRemoving(null)}>취소</Button>
                        <Button disabled={action.busy} onClick={remove}>{action.busy ? '삭제 중…' : '삭제'}</Button>
                    </div>
                </Modal>
            )}
        </section>
    );
}

export function AdminProjectsView({ load, busy, onRetry, onPage, onRestrict, onDelete }: {
    load: AdminLoad<AdminPage<AdminProject>>; busy: boolean; onRetry: () => void; onPage: (page: number) => void;
    onRestrict: (project: AdminProject) => void; onDelete: (project: AdminProject) => void;
}) {
    return (
        <AdminLoadView load={load} onRetry={onRetry}>
            {data => (
                <>
                    <h3>공고 <small>{data.totalElements}개</small></h3>
                    {!data.items.length && <p>조건에 맞는 공고가 없습니다.</p>}
                    {data.items.map(project => (
                        <article className={cls('admin-row')} key={project.id}>
                            <div>
                                <Link href={`/projects/${project.id}`}>{project.title}</Link>{' '}
                                {project.restricted && <span className="demo-pill">관리자 숨김</span>}
                                <small>
                                    {project.hostNickname ?? '탈퇴한 회원'} · {projectStatusLabels[project.status]} · {project.isPublic ? '공개' : '비공개'} · 모집 마감 {project.recruitmentEnd} · 등록 {adminTime(project.createdAt)}
                                </small>
                            </div>
                            <div className={cls('governance-actions')}>
                                <Button variant="secondary" disabled={busy} onClick={() => onRestrict(project)}>{project.restricted ? '숨김 해제' : '관리자 숨김'}</Button>
                                {projectDeletable(project) && <Button variant="secondary" disabled={busy} onClick={() => onDelete(project)}>삭제</Button>}
                            </div>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

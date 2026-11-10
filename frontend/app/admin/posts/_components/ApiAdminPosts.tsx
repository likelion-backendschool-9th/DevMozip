'use client';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { AdminActionError, AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { adminJson, adminRequest, adminTime, parseAdminPage } from '@/lib/administration/adminApi';
import {
    parseAdminComment, parseAdminPost, postVisibilities, postVisibilityLabels, restrictAction, type AdminPostTarget,
} from '@/lib/administration/adminPosts';
import { useAdminAction, useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminComment, AdminPage, AdminPost } from '@/types/adminApi';

const parsePosts = (raw: unknown) => parseAdminPage(raw, parseAdminPost);
const parseComments = (raw: unknown) => parseAdminPage(raw, parseAdminComment);
type Removing = { target: AdminPostTarget; id: number; label: string };

/** Mock 토글 OFF의 게시글·댓글 관리 (WBS 14.6·14.7). 서버의 관리자 숨김·삭제 API를 쓴다. */
export function ApiAdminPosts() {
    const [target, setTarget] = useState<AdminPostTarget>('POST');
    const [draft, setDraft] = useState('');
    const [filters, setFilters] = useState({ q: '', visibility: '' });
    const [page, setPage] = useState(0);
    const query = new URLSearchParams({ target, page: String(page), size: '20' });
    if (filters.q) query.set('q', filters.q);
    if (target === 'POST' && filters.visibility) query.set('visibility', filters.visibility);
    const posts = useAdminResource<AdminPage<AdminPost> | AdminPage<AdminComment>>(`/posts?${query}`, target === 'POST' ? parsePosts : parseComments);
    const [removing, setRemoving] = useState<Removing | null>(null);
    const action = useAdminAction();

    const switchTarget = (next: AdminPostTarget) => { setTarget(next); setPage(0); action.clearError(); };
    const search = (event: FormEvent) => { event.preventDefault(); setFilters(f => ({ ...f, q: draft.trim() })); setPage(0); };
    const restrict = async (post: AdminPost) => {
        const done = await action.run(() => adminRequest(`/posts/${post.id}/visibility`, adminJson('PATCH', { restricted: restrictAction(post).restricted })));
        if (done) posts.reload();
    };
    const remove = async () => {
        if (!removing) return;
        const done = await action.run(() => adminRequest(`/posts/${removing.id}?target=${removing.target}`, adminJson('PUT', { action: 'DELETE' })));
        if (done) { setRemoving(null); posts.reload(); }
    };

    return (
        <section className={cls('governance-panel admin-main')} aria-label="게시글·댓글 관리">
            <h2>게시글 및 댓글 관리</h2>
            <p>관리자 숨김은 작성자 외에는 피드·상세에서 보이지 않게 합니다. 작성자는 관리자 숨김을 풀 수 없습니다. 삭제는 되돌릴 수 없습니다.</p>
            <div className={cls('admin-tabs')} role="group" aria-label="관리 대상">
                <Button variant="secondary" aria-pressed={target === 'POST'} onClick={() => switchTarget('POST')}>게시글</Button>
                <Button variant="secondary" aria-pressed={target === 'COMMENT'} onClick={() => switchTarget('COMMENT')}>댓글</Button>
            </div>
            <form className={cls('admin-filters')} onSubmit={search}>
                <label className="admin-filter-wide">
                    검색
                    <input className="form-input" value={draft} onChange={e => setDraft(e.target.value)} placeholder={target === 'POST' ? '제목·내용' : '댓글 내용'} />
                </label>
                {target === 'POST' && (
                    <label>
                        공개 범위
                        <select className="form-input" value={filters.visibility} onChange={e => { setFilters(f => ({ ...f, visibility: e.target.value })); setPage(0); }}>
                            <option value="">전체</option>
                            {postVisibilities.map(v => <option key={v} value={v}>{postVisibilityLabels[v]}</option>)}
                        </select>
                    </label>
                )}
                <Button type="submit" variant="secondary">검색</Button>
            </form>
            {!removing && <AdminActionError message={action.error} />}
            {target === 'POST'
                ? <AdminPostsView load={posts.load as AdminLoad<AdminPage<AdminPost>>} busy={action.busy} onRetry={posts.reload} onPage={setPage}
                    onRestrict={restrict} onDelete={post => { action.clearError(); setRemoving({ target: 'POST', id: post.id, label: post.title ?? '퍼간 글' }); }} />
                : <AdminCommentsView load={posts.load as AdminLoad<AdminPage<AdminComment>>} onRetry={posts.reload} onPage={setPage}
                    onDelete={comment => { action.clearError(); setRemoving({ target: 'COMMENT', id: comment.id, label: comment.content }); }} />}
            {removing && (
                <Modal title={removing.target === 'POST' ? '게시글을 삭제할까요?' : '댓글을 삭제할까요?'} onClose={() => { if (!action.busy) setRemoving(null); }}>
                    <p className="preserve-text">{removing.label}</p>
                    <p>삭제하면 되돌릴 수 없고, 피드와 관련 화면에서 바로 사라집니다.</p>
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

export function AdminPostsView({ load, busy, onRetry, onPage, onRestrict, onDelete }: {
    load: AdminLoad<AdminPage<AdminPost>>; busy: boolean; onRetry: () => void; onPage: (page: number) => void;
    onRestrict: (post: AdminPost) => void; onDelete: (post: AdminPost) => void;
}) {
    return (
        <AdminLoadView load={load} onRetry={onRetry}>
            {data => (
                <>
                    <h3>게시글 <small>{data.totalElements}개</small></h3>
                    {!data.items.length && <p>조건에 맞는 게시글이 없습니다.</p>}
                    {data.items.map(post => (
                        <article className={cls('admin-row')} key={post.id}>
                            <div>
                                <Link href={`/feeds/${post.id}`}>{post.title ?? '퍼간 글'}</Link>
                                {post.content && <p className="preserve-text">{post.content}</p>}
                                <small>{post.authorNickname} · {postVisibilityLabels[post.visibility]} · {adminTime(post.createdAt)}</small>
                            </div>
                            <div className={cls('governance-actions')}>
                                <Button variant="secondary" disabled={busy} onClick={() => onRestrict(post)}>{restrictAction(post).label}</Button>
                                <Button variant="secondary" disabled={busy} onClick={() => onDelete(post)}>삭제</Button>
                            </div>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

export function AdminCommentsView({ load, onRetry, onPage, onDelete }: {
    load: AdminLoad<AdminPage<AdminComment>>; onRetry: () => void; onPage: (page: number) => void; onDelete: (comment: AdminComment) => void;
}) {
    return (
        <AdminLoadView load={load} onRetry={onRetry}>
            {data => (
                <>
                    <h3>댓글 <small>{data.totalElements}개</small></h3>
                    {!data.items.length && <p>조건에 맞는 댓글이 없습니다.</p>}
                    {data.items.map(comment => (
                        <article className={cls('admin-row')} key={comment.id}>
                            <div>
                                <p className="preserve-text">{comment.content}</p>
                                <small>
                                    {comment.authorNickname} · <Link href={`/feeds/${comment.postId}`}>{comment.postTitle ?? '퍼간 글'}</Link> · {adminTime(comment.createdAt)}
                                </small>
                            </div>
                            <div className={cls('governance-actions')}>
                                <Button variant="secondary" onClick={() => onDelete(comment)}>댓글 삭제</Button>
                            </div>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

'use client';
import styles from './Comments.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { ReportDialog } from '@/components/posts/ReportDialog';
export function Comments({ postId }: {
    postId: string;
}) { const { user } = useAuth(); const { state, act } = useProfiles(); const [body, setBody] = useState(''); const [error, setError] = useState(''); const [edit, setEdit] = useState<{
    id: string;
    body: string;
} | null>(null); const [remove, setRemove] = useState<string | null>(null); const [report, setReport] = useState<string | null>(null); const comments = state.social.comments.filter(c => c.postId === postId); return <section className={[styles.scope, "sns-comments"].join(" ")} id="comments"><h2>댓글 <span>{comments.length}</span></h2>{user ? <form onSubmit={e => { e.preventDefault(); if (!body.trim()) {
    setError('댓글을 입력해 주세요.');
    return;
} if (act({ type: 'social-comment-create', postId, body })) {
    setBody('');
    setError('');
} }}><label className={[styles.scope, "sr-only"].join(" ")} htmlFor="new-comment">댓글 입력</label><Textarea id="new-comment" value={body} maxLength={1000} rows={3} onChange={e => { setBody(e.target.value); setError(''); }} placeholder="존중하는 대화로 경험을 나눠주세요."/>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<Button type="submit">댓글 등록</Button></form> : <p className={[styles.scope, "auth-info"].join(" ")}><Link href="/login">로그인하고 댓글을 남겨보세요 →</Link></p>}{!comments.length && <div className={[styles.scope, "empty-state"].join(" ")}>첫 번째 의견을 남겨주세요.</div>}<div className={[styles.scope, "comment-list"].join(" ")}>{comments.map(c => { const author = state.profiles[c.authorId]; const owner = user?.id === c.authorId; const reported = state.social.reports.some(r => r.reporterId === user?.id && r.targetType === 'comment' && r.targetId === c.id); return <article className={[styles.scope, "sns-comment"].join(" ")} key={c.id}><Link href={`/users/${c.authorId}`}><ProfileAvatar name={author?.nickname || '작성자'} color={author?.color || 'lavender'} small/></Link><div><header><Link href={`/users/${c.authorId}`}><strong>{author?.nickname || '작성자'}</strong></Link><time>{new Date(c.createdAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}{c.updatedAt ? ' · 수정됨' : ''}</time></header>{edit?.id === c.id ? <form onSubmit={e => { e.preventDefault(); if (act({ type: 'social-comment-edit', id: c.id, body: edit.body }))
    setEdit(null); }}><Textarea aria-label="댓글 수정" maxLength={1000} rows={3} value={edit.body} onChange={e => setEdit({ ...edit, body: e.target.value })}/><div className={[styles.scope, "inline-actions"].join(" ")}><Button type="button" variant="secondary" onClick={() => setEdit(null)}>취소</Button><Button type="submit">수정 저장</Button></div></form> : <p className={[styles.scope, "preserve-text"].join(" ")}>{c.body}</p>}<div className={[styles.scope, "inline-actions"].join(" ")}>{owner ? <><button onClick={() => setEdit({ id: c.id, body: c.body })}>수정</button><button onClick={() => setRemove(c.id)}>삭제</button></> : user ? <button disabled={reported} onClick={() => setReport(c.id)}>{reported ? '신고됨' : '댓글 신고'}</button> : <Link href="/login">로그인 후 신고</Link>}</div></div></article>; })}</div>{remove && <Modal title="댓글을 삭제할까요?" onClose={() => setRemove(null)}><div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" onClick={() => setRemove(null)}>취소</Button><Button onClick={() => { if (act({ type: 'social-comment-delete', id: remove }))
    setRemove(null); }}>삭제</Button></div></Modal>}{report && <ReportDialog targetType="comment" targetId={report} onClose={() => setReport(null)}/>}</section>; }

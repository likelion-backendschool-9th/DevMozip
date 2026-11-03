'use client';
import styles from './page.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { adminStyles } from '../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

export default function AdminPostsPage() {
    const { state, moderate } = useProfiles();
    const { administration, administer } = useAuth();
    const [query, setQuery] = useState('');
    const [removing, setRemoving] = useState<string | null>(null);

    return (
        <section className={[adminStyles.scope, 'governance-panel admin-main'].join(' ')} aria-label="게시글·댓글 관리">
            <label className={[adminStyles.scope, 'admin-search'].join(' ')}>
                검색
                <input
                    className={[adminStyles.scope, 'form-input'].join(' ')}
                    value={query}
                    onChange={e => setQuery(e.target.value)}
                    placeholder="이름·이메일 또는 콘텐츠 검색"
                />
            </label>
            <h2>게시글 및 댓글 관리</h2>
            <p>게시글 숨김은 피드·메인·프로필에 반영됩니다. 댓글 삭제는 확인 후 반영합니다.</p>
            {state.posts
                .filter(p => (p.title + p.body).toLowerCase().includes(query.toLowerCase()))
                .map(p => (
                    <article className={[styles.scope, 'admin-row'].join(' ')} key={p.id}>
                        <div>
                            <Link href={`/feeds/${p.id}`}>{p.title}</Link>
                            <small>
                                {state.profiles[p.authorId]?.nickname} · {p.hidden ? '숨김' : '공개'}
                            </small>
                        </div>
                        <Button
                            variant="secondary"
                            onClick={() => moderate({ type: 'post-visibility', id: p.id, hidden: !p.hidden })}
                        >
                            {p.hidden ? '공개 복원' : '글 숨김'}
                        </Button>
                    </article>
                ))}
            <h3>댓글</h3>
            {state.social.comments
                .filter(c => c.body.includes(query))
                .map(c => (
                    <article className={[styles.scope, 'admin-row'].join(' ')} key={c.id}>
                        <div>
                            <Link href={`/feeds/${c.postId}`}>{c.body}</Link>
                            <small>{state.profiles[c.authorId]?.nickname}</small>
                        </div>
                        <Button variant="secondary" onClick={() => setRemoving(c.id)}>
                            댓글 삭제
                        </Button>
                    </article>
                ))}
            <h3>SNS 신고 접수</h3>
            {!state.social.reports.length && <p>접수된 SNS 신고가 없습니다.</p>}
            {state.social.reports.map(r => (
                <article className={[styles.scope, 'admin-row'].join(' ')} key={r.id}>
                    <div>
                        <b>
                            {r.targetType === 'post' ? '게시글' : '댓글'} 신고 · {r.reason}
                        </b>
                        <small>
                            {r.targetId} · {administration.resolutions[`social:${r.id}`]?.status || '미처리'}
                        </small>
                    </div>
                    <Button
                        variant="secondary"
                        onClick={() =>
                            administer({
                                type: 'resolve',
                                reportId: `social:${r.id}`,
                                status: 'resolved',
                                note: '콘텐츠 검토 완료',
                            })
                        }
                    >
                        검토 완료
                    </Button>
                </article>
            ))}
            {removing && (
                <Modal title="댓글을 삭제할까요?" onClose={() => setRemoving(null)}>
                    <p>댓글 삭제는 해당 게시글과 관련 화면에 함께 반영됩니다.</p>
                    <div className={[styles.scope, 'modal-actions'].join(' ')}>
                        <Button variant="secondary" onClick={() => setRemoving(null)}>
                            취소
                        </Button>
                        <Button
                            onClick={() => {
                                if (moderate({ type: 'comment-delete', id: removing })) setRemoving(null);
                            }}
                        >
                            삭제
                        </Button>
                    </div>
                </Modal>
            )}
        </section>
    );
}

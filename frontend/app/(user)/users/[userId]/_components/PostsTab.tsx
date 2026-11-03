'use client';
import styles from './PostsTab.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { PostOwnerActions } from '@/components/posts/PostOwnerActions';
import { Repeat2, Plus } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Input';
import { PostCard } from '@/components/posts/PostCard';
import { postCreatedAt } from '@/lib/social/socialService';
export function PostsTab({ profileId }: {
    profileId: string;
}) { const { user, canViewProfile } = useAuth(); const { state, act } = useProfiles(); const owner = user?.id === profileId; const [edit, setEdit] = useState<{
    id: string;
    opinion: string;
} | null>(null); const [remove, setRemove] = useState<string | null>(null); const posts = state.posts.filter(p => p.authorId === profileId && (!p.hidden || owner)).sort((a, b) => postCreatedAt(b).localeCompare(postCreatedAt(a))); const reposts = state.reposts.filter(r => r.ownerId === profileId && (!r.hidden || owner)); return <div className={[styles.scope, "profile-posts"].join(" ")}><div className={[styles.scope, "tab-section-title"].join(" ")}><div><h2>생각과 경험의 기록</h2><p>직접 쓴 글과 함께 나누고 싶은 동료들의 이야기</p></div>{owner && <Link className="button" href="/feeds/new"><Plus size={17}/>새 게시글 작성</Link>}</div>{!posts.length && !reposts.length && <div className={[styles.scope, "empty-state"].join(" ")}>표시할 글이 없습니다.</div>}{posts.map(p => <PostCard key={p.id} post={p}/>)}{reposts.map(r => { const original = state.posts.find(p => p.id === r.originalId); const readable = original && canViewProfile(original.authorId) && (!original.hidden || original.authorId === user?.id); return <Card className={[styles.scope, "profile-section sns-repost-card"].join(" ")} key={r.id}><div className={[styles.scope, "profile-post-head"].join(" ")}><Repeat2 size={16}/><span>퍼간 글{r.hidden ? ' · 숨김' : ''}</span>{owner && <PostOwnerActions hidden={!!r.hidden} removeLabel="퍼가기 취소" onToggle={() => act({ type: 'repost-hide', id: r.id })} onEdit={() => setEdit({ id: r.id, opinion: r.opinion })} onRemove={() => setRemove(r.id)}/>}</div>{r.opinion && <p className={[styles.scope, "preserve-text"].join(" ")}>{r.opinion}</p>}{readable ? <><Link className={[styles.scope, "original-author"].join(" ")} href={`/users/${original.authorId}`}>원본 작성자 · {state.profiles[original.authorId]?.nickname || '작성자'}</Link><Link className={[styles.scope, "original-post-link"].join(" ")} href={`/feeds/${original.id}`}>원본 게시글 보기 →</Link><PostCard post={original}/></> : <p>{original && !canViewProfile(original.authorId) ? '원본 게시글이 비공개로 전환되어 표시할 수 없습니다.' : '원본 게시글이 삭제되었거나 숨김 처리되어 표시할 수 없습니다.'}</p>}</Card>; })}{edit && <Modal title="퍼가기 의견 수정" onClose={() => setEdit(null)}><form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); if (act({ type: 'repost-edit', id: edit.id, opinion: edit.opinion }))
    setEdit(null); }}><Textarea aria-label="퍼가기 의견" rows={4} maxLength={1000} value={edit.opinion} onChange={e => setEdit({ ...edit, opinion: e.target.value })}/><Button type="submit">수정 저장</Button></form></Modal>}{remove && <Modal title="퍼가기를 취소할까요?" onClose={() => setRemove(null)}><p>내 프로필의 퍼간 글만 삭제하고 원본은 유지합니다.</p><div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" onClick={() => setRemove(null)}>유지</Button><Button onClick={() => { if (act({ type: 'repost-delete', id: remove }))
    setRemove(null); }}>퍼가기 취소</Button></div></Modal>}</div>; }

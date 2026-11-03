'use client';
import styles from './PostCard.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Eye, ExternalLink } from 'lucide-react';
import { useProfiles } from '@/lib/state/profileStore';
import { useAuth } from '@/lib/state/authStore';
import { postCreatedAt, postMetrics } from '@/lib/social/socialService';
import { safeExternalUrl } from '@/lib/utils/externalUrl';
import { Card } from '@/components/common/Card';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { PostOwnerActions } from './PostOwnerActions';
import { PostActions } from '@/components/posts/PostActions';
import type { ProfilePost } from '@/types/profile';
export function PostCard({ post: p, full = false }: {
    post: ProfilePost;
    full?: boolean;
}) { const { user, canViewProfile } = useAuth(); const { state, act } = useProfiles(); const navigate = useNavigate(); const [remove, setRemove] = useState(false); const author = state.profiles[p.authorId]; const owner = user?.id === p.authorId; const metrics = postMetrics(state, p); if (!canViewProfile(p.authorId))
    return null; return <Card className={[styles.scope, `sns-post-card ${full ? 'full-post' : ''}`].join(" ")}><header className={[styles.scope, "sns-author"].join(" ")}><Link href={`/users/${p.authorId}`}><ProfileAvatar name={author?.nickname || '작성자'} color={author?.color || 'lavender'} small/></Link><div><Link href={`/users/${p.authorId}`}><strong>{author?.nickname || '작성자'}</strong></Link><span>{author?.job} · {new Date(postCreatedAt(p)).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}{p.updatedAt ? ' · 수정됨' : ''}</span></div><span className={[styles.scope, "sns-category"].join(" ")}>{p.category}{p.hidden ? ' · 숨김' : ''}</span>{owner && <PostOwnerActions hidden={!!p.hidden} onToggle={() => act({ type: 'post-hide', id: p.id })} onRemove={() => setRemove(true)} editHref={`/feeds/${p.id}/edit`}/>}</header>{full ? <h1>{p.title}</h1> : <Link href={`/feeds/${p.id}`}><h2>{p.title}</h2></Link>}<p className={[styles.scope, `preserve-text sns-post-body ${full ? '' : 'post-excerpt'}`].join(" ")}>{p.body}</p>{(p.images?.length || 0) > 0 && <div className={[styles.scope, "sns-images"].join(" ")}>{p.images!.map(i => <div key={i.id}><img src={i.url} alt={i.name} loading="lazy"/></div>)}</div>}<div className={[styles.scope, "tags"].join(" ")}>{p.tags.map(t => <span key={t}>#{t}</span>)}</div>{!!p.interests?.length && <div className={styles.interests} aria-label="관심 분야">{p.interests.map(field=><span key={field}>{field}</span>)}</div>}{(p.github || p.external) && <div className={[styles.scope, "sns-links"].join(" ")}>{([{ label: 'GitHub', value: p.github }, { label: '외부 링크', value: p.external }]).map(l => l.value && safeExternalUrl(l.value) ? <a href={safeExternalUrl(l.value)} target="_blank" rel="noreferrer" key={l.label}>{l.label}<ExternalLink size={12}/></a> : null)}</div>}<div className={[styles.scope, "sns-post-meta"].join(" ")}><span><Eye size={13}/>조회 {metrics.views}</span>{!full && <Link href={`/feeds/${p.id}`}>전체 글 보기 →</Link>}</div><PostActions post={p}/>{remove && <Modal title="게시글을 삭제할까요?" onClose={() => setRemove(false)}><p>댓글과 반응도 삭제되며, 연결된 퍼간 글에는 원본 삭제 안내가 표시됩니다.</p><div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" onClick={() => setRemove(false)}>취소</Button><Button onClick={() => { if (act({ type: 'social-delete', id: p.id })) {
    setRemove(false);
    if (full)
        navigate('/feeds');
} }}>삭제</Button></div></Modal>}</Card>; }

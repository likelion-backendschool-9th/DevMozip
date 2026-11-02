'use client';
import styles from './DeveloperResults.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { ArrowUpRight, MessageSquare, Star, UserPlus } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { useMessaging } from '@/lib/state/MessagingProvider';
import { usePrototype } from '@/lib/state/PrototypeProvider';
import { temperature, starColor } from '@/lib/profiles/profileService';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Input, Textarea } from '@/components/common/Input';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import type { Profile } from '@/types/profile';
function ProposalModal({ profile, onClose }: {
    profile: Profile;
    onClose: () => void;
}) { const { user } = useAuth(); const { act: messageAct } = useMessaging(); const navigate = useNavigate(); const [title, setTitle] = useState(''); const [message, setMessage] = useState(''); const [error, setError] = useState(''); return <Modal title={`${profile.nickname}님에게 협업 제안`} onClose={onClose}>{user ? <form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); if (!title.trim() || message.trim().length < 10) {
    setError('프로젝트명과 10자 이상의 제안 내용을 입력해 주세요.');
    return;
} const result = messageAct({ type: 'send-direct', targetId: profile.id, body: message, kind: 'proposal', proposalTitle: title }); if (result.ok) {
    onClose();
    navigate(`/messages?conversation=${encodeURIComponent(result.conversationId || '')}`);
}
else
    setError(result.error || '제안하지 못했습니다.'); }}><p className={[styles.scope, "auth-hint"].join(" ")}>프로필 연락 가능 시간: {profile.contact || '미등록'}</p><label>프로젝트명<Input value={title} onChange={e => { setTitle(e.target.value); setError(''); }} maxLength={100} placeholder="함께하고 싶은 프로젝트"/></label><label>제안 내용<Textarea rows={4} value={message} onChange={e => { setMessage(e.target.value); setError(''); }} maxLength={1000} placeholder="어떤 역할로 함께하고 싶은지 알려주세요."/></label><p className={[styles.scope, "auth-hint"].join(" ")}>협업 제안이 브라우저의 Mock 1:1 대화에 저장됩니다. 실제 서버에는 전송하지 않습니다.</p>{error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" type="button" onClick={onClose}>취소</Button><Button type="submit">협업 제안 보내기</Button></div></form> : <><p>협업 제안은 로그인 후 이용할 수 있습니다.</p><Link href="/login" className={[styles.scope, "button"].join(" ")} onClick={onClose}>로그인하기</Link></>}</Modal>; }
export function DeveloperResults({ items }: {
    items: Profile[];
}) { const { user } = useAuth(); const { state, act } = useProfiles(); const [proposal, setProposal] = useState<Profile | null>(null); return <><div className={[styles.scope, "search-developer-grid"].join(" ")}>{items.map(profile => { const owner = user?.id === profile.id; const followed = !!user && !!state.following[user.id]?.includes(profile.id); const temp = temperature(state.reviews, profile.id); return <Card key={profile.id} className={[styles.scope, "search-developer-card"].join(" ")}><div className={[styles.scope, "search-card-top"].join(" ")}><Link href={`/users/${profile.id}`} aria-label={`${profile.nickname} 프로필`}><ProfileAvatar name={profile.nickname} color={profile.color}/></Link><span className={[styles.scope, `availability-badge ${profile.available ? '' : 'unavailable'}`].join(" ")}>{profile.available ? '협업 가능' : '협업 쉬는 중'}</span></div><Link className={[styles.scope, "search-person-name"].join(" ")} href={`/users/${profile.id}`}><h3>{profile.nickname}</h3><ArrowUpRight size={15}/></Link><span className={[styles.scope, "search-person-job"].join(" ")}>{profile.job} · {profile.career}</span><p className={[styles.scope, "search-person-bio"].join(" ")}>{profile.bio || '아직 작성한 소개가 없습니다.'}</p><div className={[styles.scope, "tags"].join(" ")}>{profile.stacks.map(s => <span key={s}>{s}</span>)}</div><div className={[styles.scope, "search-person-interests"].join(" ")}>{profile.interests.map(i => <span key={i}>#{i}</span>)}</div><div className={[styles.scope, "search-card-trust"].join(" ")} style={{ color: starColor(temp) }}><Star size={12} fill="currentColor"/>{temp.toFixed(1)}°C <span>신뢰 온도</span></div><div className={[styles.scope, "search-card-actions"].join(" ")}>{owner ? <Link className={[styles.scope, "button secondary"].join(" ")} href={`/users/${profile.id}`}>내 프로필 보기</Link> : <>{user ? <Button variant={followed ? 'secondary' : 'primary'} aria-pressed={followed} onClick={() => act({ type: 'follow', targetId: profile.id })}><UserPlus size={13}/>{followed ? '언팔로우' : '팔로우'}</Button> : <Link className={[styles.scope, "button"].join(" ")} href="/login"><UserPlus size={13}/>팔로우</Link>}<Button variant="secondary" onClick={() => setProposal(profile)}><MessageSquare size={13}/>협업 제안</Button></>}</div></Card>; })}</div>{proposal && <ProposalModal profile={proposal} onClose={() => setProposal(null)}/>}</>; }

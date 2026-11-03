'use client';
import styles from './ProfileHeader.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { Bell, CalendarDays, Clock3, MessageSquare, Pencil, Star, UserPlus } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { starColor, temperature } from '@/lib/profiles/profileService';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { FollowSettings } from '@/app/(user)/users/[userId]/_components/FollowSettings';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import type { Profile } from '@/types/profile';
export function ProfileHeader({ profile, onEdit }: {
    profile: Profile;
    onEdit: () => void;
}) {
    const { user, publicUsers, canViewProfile } = useAuth();
    const { state, act } = useProfiles();
    const [noticeOpen, setNoticeOpen] = useState(false);
    const [list, setList] = useState<'followers' | 'following' | null>(null);
    const owner = user?.id === profile.id;
    const followers = Object.keys(state.following).filter(id => state.following[id].includes(profile.id));
    const following = state.following[profile.id] || [];
    const followed = !!user && !!state.following[user.id]?.includes(profile.id);
    const temp = temperature(state.reviews, profile.id);
    return <section className={[styles.scope, "profile-top"].join(" ")}><div className={[styles.scope, `profile-banner ${profile.color}`].join(" ")} aria-label="프로필 배너">{profile.banner?<img className={styles.bannerImage} src={profile.banner} alt={`${profile.nickname} 배너 이미지`}/>:<><span aria-hidden="true">{'{'} build together. {'}'}</span><i /><i /></>}</div><div className={[styles.scope, "profile-identity"].join(" ")}><ProfileAvatar name={profile.nickname} color={profile.color} image={profile.avatar}/><div className={[styles.scope, "profile-top-actions"].join(" ")}>{owner ? <Button variant="secondary" onClick={onEdit}><Pencil size={14}/>프로필 수정</Button> : <>{user ? <Button variant={followed ? 'secondary' : 'primary'} onClick={() => act({ type: 'follow', targetId: profile.id })}><UserPlus size={14}/>{followed ? '팔로잉 · 취소' : '팔로우'}</Button> : <Link className={[styles.scope, "button"].join(" ")} href="/login">로그인하고 팔로우</Link>}<Link className={[styles.scope, "button secondary"].join(" ")} href={user ? `/messages/chats?userId=${profile.id}` : '/login'}><MessageSquare size={14}/>쪽지</Link>{followed && <Button variant="secondary" onClick={() => setNoticeOpen(true)}><Bell size={14}/>알림 설정</Button>}</>}</div><div className={[styles.scope, "profile-heading"].join(" ")}><h1>{profile.nickname} {publicUsers.find(u => u.id === profile.id)?.role === 'premium' && <span className={[styles.scope, "premium-label"].join(" ")}>PRO</span>}</h1><span>@{profile.handle}</span>{profile.available ? <span className={[styles.scope, "availability-badge"].join(" ")}><i />협업 가능</span> : <span className={[styles.scope, "availability-badge unavailable"].join(" ")}>현재 협업 쉬는 중</span>}</div><p className={[styles.scope, "profile-job"].join(" ")}>{profile.jobs?.length ? profile.jobs.join(' · ') : profile.job} <span>·</span> {profile.career}</p><p className={[styles.scope,"profile-bio"].join(" ")}>{profile.bio}</p><div className={[styles.scope, "profile-meta"].join(" ")}><span><CalendarDays size={13}/>가입일 {profile.joinedAt}</span><span><Clock3 size={13}/>{profile.contact || '연락 시간 미등록'}</span></div><div className={[styles.scope, "profile-social"].join(" ")}><button onClick={() => setList('followers')}>팔로워 <b>{followers.length}</b></button><button onClick={() => setList('following')}>팔로잉 <b>{following.length}</b></button><div className={[styles.scope, "trust-temperature"].join(" ")} style={{ color: starColor(temp) }}><Star size={17} fill="currentColor"/><strong>{temp.toFixed(1)}°C</strong><span>신뢰 온도</span></div></div></div>{list && <Modal title={list === 'followers' ? '팔로워' : '팔로잉'} onClose={() => setList(null)}><div className={[styles.scope, "profile-people"].join(" ")}>{(list === 'followers' ? followers : following).filter(id => canViewProfile(id)).map(id => { const p = state.profiles[id]; return p ? <Link key={id} href={`/users/${id}`} onClick={() => setList(null)}><ProfileAvatar name={p.nickname} color={p.color} small/><div><strong>{p.nickname}</strong><small>{p.job} · {p.available ? '협업 가능' : '협업 쉬는 중'}</small></div></Link> : null; })}{(list === 'followers' ? followers : following).length === 0 && <p>아직 연결된 개발자가 없습니다.</p>}</div><Link className={[styles.scope, "text-link"].join(" ")} href={`/users/${profile.id}/follows?tab=${list}`} onClick={() => setList(null)}>전체 목록에서 검색·알림 설정 →</Link></Modal>}{noticeOpen && <FollowSettings targetId={profile.id} onClose={() => setNoticeOpen(false)}/>}</section>;
}

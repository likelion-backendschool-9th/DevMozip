'use client';
import styles from './DeveloperResults.module.css';
import Link from 'next/link';
import { ArrowUpRight, Star, UserPlus } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { temperature, starColor } from '@/lib/profiles/profileService';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import type { Profile } from '@/types/profile';
export function DeveloperResults({ items }: {
    items: Profile[];
}) { const { user } = useAuth(); const { state, act } = useProfiles(); return <><div className={[styles.scope, "search-developer-grid"].join(" ")}>{items.map(profile => { const owner = user?.id === profile.id; const followed = !!user && !!state.following[user.id]?.includes(profile.id); const temp = temperature(state.reviews, profile.id); return <Card key={profile.id} className={[styles.scope, "search-developer-card"].join(" ")}><div className={[styles.scope, "search-card-top"].join(" ")}><Link href={`/users/${profile.id}`} aria-label={`${profile.nickname} 프로필`}><ProfileAvatar name={profile.nickname} color={profile.color}/></Link><span className={[styles.scope, `availability-badge ${profile.available ? '' : 'unavailable'}`].join(" ")}>{profile.available ? '협업 가능' : '협업 쉬는 중'}</span></div><Link className={[styles.scope, "search-person-name"].join(" ")} href={`/users/${profile.id}`}><h3>{profile.nickname}</h3><ArrowUpRight size={15}/></Link><span className={[styles.scope, "search-person-job"].join(" ")}>{profile.job} · {profile.career}</span><p className={[styles.scope, "search-person-bio"].join(" ")}>{profile.bio || '아직 작성한 소개가 없습니다.'}</p><div className={[styles.scope, "tags"].join(" ")}>{profile.stacks.map(s => <span key={s}>{s}</span>)}</div><div className={[styles.scope, "search-person-interests"].join(" ")}>{profile.interests.map(i => <span key={i}>#{i}</span>)}</div><div className={[styles.scope, "search-card-trust"].join(" ")} style={{ color: starColor(temp) }}><Star size={12} fill="currentColor"/>{temp.toFixed(1)}°C <span>신뢰 온도</span></div><div className={[styles.scope, "search-card-actions"].join(" ")}>{owner ? <Link className={[styles.scope, "button secondary"].join(" ")} href={`/users/${profile.id}`}>내 프로필 보기</Link> : <>{user ? <Button variant={followed ? 'secondary' : 'primary'} aria-pressed={followed} onClick={() => act({ type: 'follow', targetId: profile.id })}><UserPlus size={13}/>{followed ? '언팔로우' : '팔로우'}</Button> : <Link className={[styles.scope, "button"].join(" ")} href="/login"><UserPlus size={13}/>팔로우</Link>}</>}</div></Card>; })}</div></>; }

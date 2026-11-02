'use client';
import styles from './ProfileScreen.module.css';
import { AccountLayout } from '@/components/layout/AccountLayout';
import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { profileTabs } from '@/mocks/profile';
import { ProfileHeader } from '@/app/users/[userId]/_components/ProfileHeader';
import { ProfileEditor, type ProfileEditSection } from '@/app/users/[userId]/_components/ProfileEditor';
import { AboutTab } from '@/app/users/[userId]/_components/AboutTab';
import { PortfolioTab } from '@/app/users/[userId]/_components/PortfolioTab';
import { PostsTab } from '@/app/users/[userId]/_components/PostsTab';
import { ActivityTab } from '@/app/users/[userId]/_components/ActivityTab';
import { ProfileReviews } from '@/app/users/[userId]/_components/ProfileReviews';
function ProfileContent({ userId }: {
    userId: string;
}) { const { user, canViewProfile, accountArea } = useAuth(); const { state } = useProfiles(); const [params, setParams] = useSearchParams(); const requested = params.get('tab'); const tab = profileTabs.find(t => t.id === requested)?.id || 'about'; const [editing, setEditing] = useState<ProfileEditSection|null>(null); const profile = Object.hasOwn(state.profiles, userId) ? state.profiles[userId] : undefined; if (!profile)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>프로필을 찾을 수 없어요.</h1><p>존재하지 않는 사용자이거나 초기화된 Mock 계정입니다.</p><Link href="/" className={[styles.scope, "button"].join(" ")}>메인으로 돌아가기</Link></section></div>; const owner = user?.id === userId; if (!canViewProfile(userId))
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>비공개 계정입니다.</h1><p>이 사용자의 프로필 정보와 활동은 공개되지 않습니다.</p><Link className={[styles.scope, "button secondary"].join(" ")} href="/search">개발자 탐색으로</Link></section></div>; const select = (id: string) => { const next = new URLSearchParams(params); next.set('tab', id); setParams(next, { replace: true }); }; const content = <div className={[styles.scope, "container profile-page"].join(" ")}><div className={[styles.scope, "profile-breadcrumb"].join(" ")}><Link href="/">DevMozip</Link><span>/</span>개발자 프로필</div>{owner && accountArea.accounts[userId]?.isPrivate && <div className={[styles.scope, "account-state-banner"].join(" ")} role="status">비공개 계정 · 나에게만 프로필이 표시됩니다. <Link href="/settings/account">공개 설정 변경 →</Link></div>}<ProfileHeader profile={profile} onEdit={() => setEditing('basic')}/><div className={[styles.scope, "profile-tabs"].join(" ")} role="tablist" aria-label="프로필 정보">{profileTabs.map((t, i) => <button role="tab" id={`profile-tab-${t.id}`} aria-controls={`profile-panel-${t.id}`} aria-selected={tab === t.id} tabIndex={tab === t.id ? 0 : -1} key={t.id} className={[styles.scope, tab === t.id ? 'active' : ''].join(" ")} onClick={() => select(t.id)} onKeyDown={e => { let next = i; if (e.key === 'ArrowRight')
    next = (i + 1) % profileTabs.length;
else if (e.key === 'ArrowLeft')
    next = (i + profileTabs.length - 1) % profileTabs.length;
else if (e.key === 'Home')
    next = 0;
else if (e.key === 'End')
    next = profileTabs.length - 1;
else
    return; e.preventDefault(); select(profileTabs[next].id); document.getElementById(`profile-tab-${profileTabs[next].id}`)?.focus(); }}>{t.label}</button>)}</div><section className={[styles.scope, "profile-tab-panel"].join(" ")} role="tabpanel" id={`profile-panel-${tab}`} aria-labelledby={`profile-tab-${tab}`} tabIndex={0}>{tab === 'about' && <AboutTab profile={profile} owner={owner} onEdit={section => setEditing(section)}/>} {tab === 'portfolio' && <PortfolioTab profileId={userId}/>} {tab === 'posts' && <PostsTab profileId={userId}/>} {tab === 'activity' && <ActivityTab profileId={userId}/>} {tab === 'reviews' && <ProfileReviews profileId={userId}/>}</section>{editing && owner && <ProfileEditor profile={profile} section={editing} onClose={() => setEditing(null)}/>}</div>; return owner ? <AccountLayout>{content}</AccountLayout> : content; }
export function ProfilePage() { const { userId = '' } = useParams<{
    projectId: string;
    postId: string;
    userId: string;
}>(); return <ProfileContent key={userId} userId={userId}/>; }

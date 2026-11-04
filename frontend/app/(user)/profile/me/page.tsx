'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { useOwnProfile } from '../../_components/OwnProfileProvider';
import { ProfileApiResult } from '../../users/[userId]/_components/ApiProfileContent';
import { ApiProfileEditor } from '../../users/[userId]/_components/ApiProfileEditor';
import styles from '../../users/[userId]/page.module.css';
export default function MyProfilePage() {
    const { user } = useAuth();
    const own = useOwnProfile();
    const [editing, setEditing] = useState(false);
    if (!user) return <section className="planned-card"><h1>로그인이 필요해요.</h1><Link href="/login?next=/profile/me">로그인하기</Link></section>;
    return <div className={`${styles.scope} container profile-page`}>
        <div className={`${styles.scope} profile-breadcrumb`}><Link href="/">DevMozip</Link><span>/</span>개발자 프로필</div>
        {own.error ? <section className="planned-card" role="alert"><p>{own.error}</p><button className="button secondary" onClick={own.retry}>다시 시도</button></section>
            : <ProfileApiResult result={own.profile ? { status: 'ready', profile: own.profile } : { status: 'loading' }} onRetry={own.retry} onEdit={own.profile ? () => setEditing(true) : undefined} />}
        {editing && own.profile && <ApiProfileEditor profile={own.profile} onClose={() => setEditing(false)} onSaved={own.update} />}
    </div>;
}

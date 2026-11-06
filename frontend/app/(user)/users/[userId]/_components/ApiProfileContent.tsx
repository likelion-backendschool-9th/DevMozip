'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { fetchProfileDetail, ProfileRequestError } from '@/lib/profiles/profileDetail';
import type { OwnProfileDetail } from '@/types/profileDetail';
import { ApiProfileTabs } from './ApiProfileTabs';
import { ApiProfileEditor } from './ApiProfileEditor';
import { useOwnProfile } from '../../../_components/OwnProfileProvider';
import { ApiProfileHeader } from './ApiProfileHeader';
import styles from '../page.module.css';

type Result = { status: 'loading' } | { status: 'ready'; profile: OwnProfileDetail } | { status: 'error'; code: number };

export function ProfileApiResult({ result, onRetry, onEdit }: { result: Result; onRetry: () => void; onEdit?: () => void }) {
    if (result.status === 'loading') return <section className="planned-card" role="status" aria-live="polite">프로필 정보를 불러오고 있어요…</section>;
    if (result.status === 'ready') return <><ApiProfileHeader key={`${result.profile.id}:${result.profile.profileImage}:${result.profile.bannerImage}`} profile={result.profile} onEdit={onEdit} />
        <ApiProfileTabs nickname={result.profile.nickname} slug={result.profile.tag ?? (onEdit ? `own-${result.profile.id}` : undefined)} canEdit={!!onEdit} /></>;
    const missing = result.code === 404;
    const denied = result.code === 401 || result.code === 403;
    return <section className="planned-card" role="alert">
        <h1>{missing ? '프로필을 찾을 수 없어요.' : denied ? '프로필을 조회할 권한이 없어요.' : '프로필을 불러오지 못했어요.'}</h1>
        <p>{missing ? '존재하지 않거나 공개되지 않은 프로필입니다.' : denied ? '로그인 상태와 접근 권한을 확인해 주세요.' : '잠시 후 다시 시도해 주세요.'}</p>
        {!missing && <button className="button secondary" onClick={onRetry}>다시 시도</button>}
        <Link href="/explore" className="button secondary">개발자 탐색으로</Link>
    </section>;
}

export function ApiProfileContent({ slug }: { slug: string }) {
    const [result, setResult] = useState<Result>({ status: 'loading' });
    const [attempt, setAttempt] = useState(0);
    const [editing, setEditing] = useState(false);
    const own = useOwnProfile();
    useEffect(() => {
        const controller = new AbortController();
        void fetchProfileDetail(slug, controller.signal).then(profile => {
            if (!controller.signal.aborted) setResult({ status: 'ready', profile });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ status: 'error', code: error instanceof ProfileRequestError ? error.status : 502 });
        });
        return () => controller.abort();
    }, [slug, attempt]);
    return <div className={`${styles.scope} container profile-page`}>
        <div className={`${styles.scope} profile-breadcrumb`}><Link href="/">DevMozip</Link><span>/</span>개발자 프로필</div>
        {editing && result.status === 'ready' && own.profile?.id === result.profile.id && <ApiProfileEditor profile={result.profile} onClose={() => setEditing(false)} onSaved={profile => setResult({ status: 'ready', profile })} />}
        <ProfileApiResult result={result} onEdit={result.status === 'ready' && own.profile?.id === result.profile.id ? () => setEditing(true) : undefined} onRetry={() => { setResult({ status: 'loading' }); setAttempt(value => value + 1); }} />
    </div>;
}

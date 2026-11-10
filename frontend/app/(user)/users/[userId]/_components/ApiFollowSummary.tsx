'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Bell } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { FollowButton } from '@/components/profile/FollowButton';
import { fetchFollowStatus, isPrivateAccountError, PRIVATE_ACCOUNT_MESSAGE } from '@/lib/profiles/followApi';
import { useAuth } from '@/lib/state/authStore';
import type { FollowStatus } from '@/types/followApi';
import type { OwnProfileDetail } from '@/types/profileDetail';
import { useOwnProfile } from '../../../_components/OwnProfileProvider';
import { ApiFollowSettings } from './ApiFollowSettings';
import styles from './ApiFollowSummary.module.css';

type StatusState = { status: 'loading' } | { status: 'ready'; value: FollowStatus } | { status: 'error'; message: string } | { status: 'private' };

/**
 * 프로필 상단의 팔로워·팔로잉 수와 팔로우 버튼. 수를 누르면 /users/{tag}/follows 목록으로 이동한다.
 * 본인 프로필에는 팔로우 버튼을 두지 않고, 태그가 없으면 목록을 열 수 없으므로 버튼을 막는다.
 */
export function ApiFollowSummary({ profile }: { profile: OwnProfileDetail }) {
    const { user } = useAuth();
    const own = useOwnProfile();
    const slug = profile.tag;
    const [attempt, setAttempt] = useState(0);
    const key = `${slug ?? ''}:${user?.email ?? ''}:${attempt}`;
    const [result, setResult] = useState<{ key: string; state: StatusState }>({ key, state: { status: 'loading' } });
    const [actionError, setActionError] = useState('');
    const [settingsOpen, setSettingsOpen] = useState(false);

    useEffect(() => {
        if (!slug) return;
        const controller = new AbortController();
        void fetchFollowStatus(slug, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ key, state: { status: 'ready', value } });
        }).catch(error => {
            if (controller.signal.aborted) return;
            // 비공개 계정(WBS 17.7)은 다시 시도해도 같으므로 오류 대신 안내만 보여 준다.
            setResult({ key, state: isPrivateAccountError(error) ? { status: 'private' }
                : { status: 'error', message: error instanceof Error ? error.message : '팔로우 정보를 불러오지 못했어요.' } });
        });
        return () => controller.abort();
    }, [slug, key]);

    if (!slug) return <>
        <button disabled title="프로필 수정에서 사용자 아이디를 정하면 볼 수 있어요.">팔로워 <b>—</b></button>
        <button disabled title="프로필 수정에서 사용자 아이디를 정하면 볼 수 있어요.">팔로잉 <b>—</b></button>
        <span className={styles.hint}>프로필 수정에서 사용자 아이디(태그)를 정하면 팔로워·팔로잉을 볼 수 있어요.</span>
    </>;

    const state = result.key === key ? result.state : { status: 'loading' as const };
    const value = state.status === 'ready' ? state.value : null;
    const update = (next: Partial<FollowStatus>) => {
        if (value) setResult({ key, state: { status: 'ready', value: { ...value, ...next } } });
    };
    // 로그인했지만 내 프로필을 아직 모르면 본인 여부를 알 수 없으므로 버튼을 잠시 숨긴다.
    const mine = !!own.profile && own.profile.id === profile.id;
    const ownKnown = !user || !!own.profile || !own.loading;
    const list = (tab: 'followers' | 'following') => `/users/${encodeURIComponent(slug)}/follows?tab=${tab}`;

    return <>
        <Link className={styles.countLink} href={list('followers')} aria-busy={state.status === 'loading'}>팔로워 <b>{value ? value.followerCount : '—'}</b></Link>
        <Link className={styles.countLink} href={list('following')} aria-busy={state.status === 'loading'}>팔로잉 <b>{value ? value.followingCount : '—'}</b></Link>
        {state.status === 'error' && <span className={styles.error} role="alert">
            {state.message} <button className={styles.retry} onClick={() => setAttempt(v => v + 1)}>다시 시도</button>
        </span>}
        {state.status === 'private' && <span className={styles.hint} role="status">{PRIVATE_ACCOUNT_MESSAGE}</span>}
        {!mine && ownKnown && value && <span className={styles.actions}>
            {user
                ? <>
                    <FollowButton slug={slug} name={profile.nickname} followed={value.followed}
                        onChange={next => { setActionError(''); update(next); }}
                        onError={message => setActionError(message)} />
                    {value.followed && <Button variant="secondary" onClick={() => setSettingsOpen(true)} aria-label={`${profile.nickname} 알림 설정`}>
                        <Bell size={14} />알림 설정
                    </Button>}
                </>
                : <Link className="button" href={`/login?next=${encodeURIComponent(`/users/${slug}`)}`}>로그인하고 팔로우</Link>}
        </span>}
        {actionError && <span className={styles.error} role="alert">{actionError}</span>}
        {settingsOpen && value?.followed && <ApiFollowSettings slug={slug} name={profile.nickname} enabled={value.activityNotificationEnabled}
            onSaved={enabled => update({ activityNotificationEnabled: enabled })} onClose={() => setSettingsOpen(false)} />}
    </>;
}

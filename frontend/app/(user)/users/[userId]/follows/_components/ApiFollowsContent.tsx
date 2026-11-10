'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Search } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { useQueryState } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { fetchProfileDetail, ProfileRequestError, validProfileSlug } from '@/lib/profiles/profileDetail';
import {
    appendFollowUsers, fetchFollowList, fetchFollowStatus, FOLLOW_PAGE_SIZE, FOLLOW_QUERY_MAX, followTab, followUserName, isPrivateAccountError,
} from '@/lib/profiles/followApi';
import type { FollowResult, FollowStatus, FollowUser } from '@/types/followApi';
import { useOwnProfile } from '../../../../_components/OwnProfileProvider';
import { ApiFollowSettings } from '../../_components/ApiFollowSettings';
import { FollowListResult, type FollowListState } from './FollowListResult';
import styles from '../page.module.css';
import local from './FollowListResult.module.css';

type ProfileState = { status: 'loading' } | { status: 'ready'; nickname: string } | { status: 'error'; code: number };
const scope = (name: string) => [styles.scope, name].join(' ');
const message = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;

/** 비공개 계정(WBS 17.7)의 목록을 다른 사람이 열었을 때. 문구는 Mock 목록 화면과 같다. */
export function FollowsPrivateNotice() {
    return <div className={scope('container planned')}>
        <section className={scope('planned-card')}>
            <h1>비공개 계정입니다.</h1>
            <p>이 사용자의 팔로워와 팔로잉 목록은 공개되지 않습니다.</p>
            <Link className={scope('button secondary')} href="/explore">개발자 탐색으로</Link>
        </section>
    </div>;
}

/**
 * /users/{tag}/follows: 팔로워(9.3)·팔로잉(9.4) 목록. 탭·검색어·협업 가능 필터는 URL(?tab=&q=&available=yes)에 두고 서버 목록 요청에 넘긴다.
 * 목록은 공개이고, 팔로우·알림 설정은 로그인한 사용자에게만 보인다.
 */
export function ApiFollowsContent({ slug }: { slug: string }) {
    const { user } = useAuth();
    const own = useOwnProfile();
    const [params, setParams] = useQueryState();
    const tab = followTab(params);
    const q = params.get('q') ?? '';
    const available = params.get('available') === 'yes';
    const validSlug = validProfileSlug(slug);
    const email = user?.email ?? '';

    const [profileAttempt, setProfileAttempt] = useState(0);
    const profileKey = `${slug}:${profileAttempt}`;
    const [profile, setProfile] = useState<{ key: string; state: ProfileState }>({ key: profileKey, state: { status: 'loading' } });
    useEffect(() => {
        if (!validSlug) return;
        const controller = new AbortController();
        void fetchProfileDetail(slug, controller.signal).then(value => {
            if (!controller.signal.aborted) setProfile({ key: profileKey, state: { status: 'ready', nickname: value.nickname } });
        }).catch(error => {
            if (!controller.signal.aborted) setProfile({ key: profileKey, state: { status: 'error', code: error instanceof ProfileRequestError ? error.status : 502 } });
        });
        return () => controller.abort();
    }, [slug, validSlug, profileKey]);

    // 비공개 계정은 요청자에 따라 볼 수 있는지가 달라지므로 대상과 로그인 계정을 함께 키로 쓴다.
    const viewerKey = `${slug}:${email}`;
    const [hiddenKey, setHiddenKey] = useState<string | null>(null);

    // 탭의 수. 팔로우를 바꾸면 다시 받아 맞춘다(내 목록이면 내 팔로잉 수가 바뀐다).
    const [statusVersion, setStatusVersion] = useState(0);
    const [counts, setCounts] = useState<FollowStatus | null>(null);
    useEffect(() => {
        if (!validSlug) return;
        const controller = new AbortController();
        void fetchFollowStatus(slug, controller.signal).then(value => {
            if (!controller.signal.aborted) setCounts(value);
        }).catch(error => {
            // 수를 받지 못하면 탭에 —를 표시하고 목록은 그대로 보여 준다. 비공개 계정이면 안내 화면으로 바꾼다.
            if (!controller.signal.aborted && isPrivateAccountError(error)) setHiddenKey(viewerKey);
        });
        return () => controller.abort();
    }, [slug, validSlug, statusVersion, viewerKey]);

    const [listAttempt, setListAttempt] = useState(0);
    const listKey = `${slug}:${tab}:${q.trim()}:${available}:${email}:${listAttempt}`;
    const [list, setList] = useState<{ key: string; state: FollowListState }>({ key: listKey, state: { status: 'loading' } });
    useEffect(() => {
        if (!validSlug) return;
        const controller = new AbortController();
        // 검색어를 입력하는 동안에는 잠시 기다렸다가 마지막 값만 요청한다.
        const timer = setTimeout(() => {
            void fetchFollowList(slug, tab, { q, available, page: 0, size: FOLLOW_PAGE_SIZE }, controller.signal).then(page => {
                if (!controller.signal.aborted) setList({ key: listKey, state: { status: 'ready', items: page.items, hasNext: page.hasNext,
                    totalCount: page.totalCount, page: page.page, more: 'idle' } });
            }).catch(error => {
                if (controller.signal.aborted) return;
                if (isPrivateAccountError(error)) setHiddenKey(viewerKey);
                setList({ key: listKey, state: { status: 'error', message: message(error, '목록을 불러오지 못했어요.') } });
            });
        }, q.trim() ? 250 : 0);
        return () => { clearTimeout(timer); controller.abort(); };
    }, [slug, validSlug, tab, q, available, listKey, viewerKey]);

    const [actionError, setActionError] = useState('');
    const [settings, setSettings] = useState<(FollowUser & { tag: string }) | null>(null);

    if (!validSlug || profile.key === profileKey && profile.state.status === 'error' && profile.state.code === 404) {
        return <div className={scope('container planned')}>
            <section className={scope('planned-card')}>
                <h1>사용자를 찾을 수 없어요.</h1>
                <p>존재하지 않거나 공개되지 않은 프로필입니다.</p>
                <Link className={scope('button')} href="/explore">개발자 탐색</Link>
            </section>
        </div>;
    }

    if (hiddenKey === viewerKey) return <FollowsPrivateNotice />;

    const profileState = profile.key === profileKey ? profile.state : { status: 'loading' as const };
    const listState: FollowListState = list.key === listKey ? list.state : { status: 'loading' };
    const update = (key: string, value: string) => {
        const next = new URLSearchParams(params);
        if (value) next.set(key, value);
        else next.delete(key);
        setParams(next, { replace: key === 'q' });
    };
    const patchItem = (tag: string, patch: Partial<FollowUser>) => setList(prev => {
        const state = prev.state;
        if (state.status !== 'ready') return prev;
        return { ...prev, state: { ...state, items: state.items.map(item => item.tag === tag ? { ...item, ...patch } : item) } };
    });
    const changed = (tag: string, result: FollowResult) => {
        setActionError('');
        patchItem(tag, { followed: result.followed, activityNotificationEnabled: result.activityNotificationEnabled });
        setStatusVersion(v => v + 1);
    };
    async function loadMore() {
        const key = listKey;
        const current = list.key === key ? list.state : null;
        if (!current || current.status !== 'ready' || current.more === 'loading') return;
        setList({ key, state: { ...current, more: 'loading' } });
        try {
            const page = await fetchFollowList(slug, tab, { q, available, page: current.page + 1, size: FOLLOW_PAGE_SIZE });
            setList(prev => {
                const state = prev.state;
                if (prev.key !== key || state.status !== 'ready') return prev;
                return { key, state: { ...state, items: appendFollowUsers(state.items, page.items), hasNext: page.hasNext,
                    totalCount: page.totalCount, page: page.page, more: 'idle' } };
            });
        } catch (error) {
            setList(prev => {
                const state = prev.state;
                if (prev.key !== key || state.status !== 'ready') return prev;
                return { key, state: { ...state, more: 'error', moreMessage: message(error, '다음 목록을 불러오지 못했어요.') } };
            });
        }
    }
    // 로그인했지만 내 프로필을 아직 모르면 undefined로 두어 버튼을 잠시 숨긴다.
    const ownTag = !user ? null : own.profile ? own.profile.tag : own.loading ? undefined : null;
    const tabs = [
        { id: 'following' as const, label: '팔로잉', count: counts?.followingCount },
        { id: 'followers' as const, label: '팔로워', count: counts?.followerCount },
    ];

    return <div className={scope('container following-page')}>
        <header className={scope('section-heading')}>
            <div>
                <div className={scope('eyebrow')}>YOUR DEVELOPER NETWORK</div>
                <h1>{profileState.status === 'ready' ? `${profileState.nickname}님의 연결` : '개발자 연결'}</h1>
                <p>관심사를 나누고, 함께 성장할 동료들을 만나보세요.</p>
            </div>
            <Link className={scope('text-link')} href={`/users/${encodeURIComponent(slug)}`}>프로필 보기 →</Link>
        </header>
        {profileState.status === 'error' && <div className={scope('planned-card')} role="alert">
            <p>프로필 정보를 불러오지 못했어요.</p>
            <Button variant="secondary" onClick={() => setProfileAttempt(v => v + 1)}>다시 시도</Button>
        </div>}
        <div className={scope('following-tabs tabs')}>
            {tabs.map(t => <button key={t.id} className={scope(tab === t.id ? 'active' : '')} aria-pressed={tab === t.id} onClick={() => update('tab', t.id)}>
                {t.label} <b>{t.count ?? '—'}</b>
            </button>)}
        </div>
        <div className={scope('following-toolbar')}>
            <div>
                <Search size={16} />
                <Input type="search" aria-label="팔로잉 또는 팔로워 검색" placeholder="닉네임, 태그, 직군, 기술로 검색" maxLength={FOLLOW_QUERY_MAX}
                    value={q} onChange={event => update('q', event.target.value)} />
            </div>
            <label className={scope('checkbox-label')}>
                <input type="checkbox" checked={available} onChange={event => update('available', event.target.checked ? 'yes' : '')} />
                협업 가능한 개발자만
            </label>
            <span role="status">{listState.status === 'ready' ? `${listState.totalCount}명` : '—'}</span>
        </div>
        {actionError && <p className={local.alert} role="alert">{actionError}</p>}
        <FollowListResult state={listState} filtered={!!q.trim() || available} signedIn={!!user} ownTag={ownTag}
            loginHref={`/login?next=${encodeURIComponent(`/users/${slug}/follows?tab=${tab}`)}`}
            onRetry={() => setListAttempt(v => v + 1)} onMore={() => void loadMore()} onChange={changed}
            onError={setActionError} onSettings={setSettings} />
        {settings && <ApiFollowSettings slug={settings.tag} name={followUserName(settings)} enabled={settings.activityNotificationEnabled}
            onSaved={enabled => patchItem(settings.tag, { activityNotificationEnabled: enabled })} onClose={() => setSettings(null)} />}
    </div>;
}

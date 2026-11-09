'use client';
import Link from 'next/link';
import { Bell } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { FollowButton } from '@/components/profile/FollowButton';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { followUserName } from '@/lib/profiles/followApi';
import { formatCareer, profileImageUrl } from '@/lib/profiles/profileDetail';
import type { FollowResult, FollowUser } from '@/types/followApi';
import styles from '../page.module.css';
import local from './FollowListResult.module.css';

export type FollowListState =
    | { status: 'loading' }
    | { status: 'error'; message: string }
    | { status: 'ready'; items: FollowUser[]; hasNext: boolean; totalCount: number; page: number; more: 'idle' | 'loading' | 'error'; moreMessage?: string };

const scope = (name: string) => [styles.scope, name].join(' ');

/**
 * 팔로워·팔로잉 목록 본문. 태그가 없는 사람은 프로필 링크와 팔로우 버튼 없이 보여 준다.
 * @param ownTag 내 태그. 로그인했지만 아직 모르면 undefined이고, 그동안 버튼을 숨긴다.
 */
export function FollowListResult({ state, filtered, signedIn, ownTag, loginHref, onRetry, onMore, onChange, onError, onSettings }: {
    state: FollowListState;
    filtered: boolean;
    signedIn: boolean;
    ownTag: string | null | undefined;
    loginHref: string;
    onRetry: () => void;
    onMore: () => void;
    onChange: (tag: string, result: FollowResult) => void;
    onError: (message: string) => void;
    onSettings: (user: FollowUser & { tag: string }) => void;
}) {
    if (state.status === 'loading') return <p className={local.state} role="status">목록을 불러오는 중…</p>;
    if (state.status === 'error') return <div className={scope('empty-state')} role="alert">
        <strong>{state.message}</strong>
        <Button variant="secondary" onClick={onRetry}>다시 시도</Button>
    </div>;
    if (!state.items.length) return <div className={scope('empty-state')}>
        <strong>표시할 개발자가 없습니다.</strong>
        <p>{filtered ? '검색어나 필터를 바꿔 보세요.' : '새로운 개발자를 찾아 연결해 보세요.'}</p>
        <Link className={scope('button secondary')} href="/explore?type=developers">개발자 탐색</Link>
    </div>;
    return <>
        <div className={scope('following-list')}>
            {state.items.map((user, index) => {
                const name = followUserName(user);
                const tag = user.tag;
                const href = tag ? `/users/${encodeURIComponent(tag)}` : null;
                const available = user.collaborationStatus === 'AVAILABLE';
                const avatar = <ProfileAvatar name={name} color="lavender" image={profileImageUrl(user.profileImage)} />;
                const heading = <h2>{name}<small>{tag ? `@${tag}` : '사용자 아이디 미설정'}</small></h2>;
                return <Card className={scope('following-person')} key={tag ?? `untagged-${index}`}>
                    {href ? <Link href={href}>{avatar}</Link> : avatar}
                    <div className={scope('following-person-info')}>
                        {href ? <Link href={href}>{heading}</Link> : heading}
                        <p>{user.jobCategories.join(', ') || '직군 미입력'} · {formatCareer(user.career)}</p>
                        <div className={scope('tags')}>
                            {user.skills.map((skill, i) => <span key={`${skill}-${i}`}>{skill}</span>)}
                        </div>
                        {signedIn && user.followed && <p className={scope('follow-notice-summary')}>
                            활동 알림 {user.activityNotificationEnabled ? '켜짐' : '꺼짐'}
                        </p>}
                    </div>
                    <span className={scope(`availability-badge ${available ? '' : 'unavailable'}`)}>{available ? '협업 가능' : '협업 쉬는 중'}</span>
                    <div className={scope('following-person-actions')}>
                        {!tag || (signedIn && ownTag === undefined) ? null
                            : tag === ownTag ? <Link className={scope('button secondary')} href={href!}>내 프로필</Link>
                            : signedIn ? <>
                                <FollowButton slug={tag} name={name} followed={user.followed}
                                    onChange={result => onChange(tag, result)} onError={onError} />
                                {user.followed && <Button variant="secondary" onClick={() => onSettings({ ...user, tag })} aria-label={`${name} 알림 설정`}>
                                    <Bell size={14} />알림 설정
                                </Button>}
                            </>
                            : <Link className={scope('button')} href={loginHref}>로그인하고 팔로우</Link>}
                    </div>
                </Card>;
            })}
        </div>
        {(state.hasNext || state.more === 'error') && <div className={local.more}>
            {state.more === 'error' && <p className={local.alert} role="alert">{state.moreMessage}</p>}
            <Button variant="secondary" disabled={state.more === 'loading'} onClick={onMore}>
                {state.more === 'loading' ? '불러오는 중…' : state.more === 'error' ? '다시 불러오기' : '더 보기'}
            </Button>
        </div>}
    </>;
}

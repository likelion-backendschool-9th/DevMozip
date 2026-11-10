'use client';
import Link from 'next/link';
import { ArrowUpRight, Star, UserPlus } from 'lucide-react';
import styles from './DeveloperResults.module.css';
import { Card } from '@/components/common/Card';
import { FollowButton } from '@/components/profile/FollowButton';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { StartChatButton } from '@/components/profile/StartChatButton';
import { followUserName } from '@/lib/profiles/followApi';
import { collaborationLabel, formatCareer, profileImageUrl } from '@/lib/profiles/profileDetail';
import { starColor } from '@/lib/profiles/profileService';
import type { FollowResult } from '@/types/followApi';
import type { DeveloperSearchItem } from '@/types/searchApi';

const scope = (name: string) => [styles.scope, name].join(' ');

/**
 * Mock 끄기의 개발자 카드(WBS 6.4·6.6·6.7·6.9). 모양은 Mock 카드(DeveloperResults)와 같고, 표시는 프로필 상단과 같은 함수를 쓴다.
 * 본인 카드에는 "내 프로필 보기"만 두고, 다른 회원 카드에는 팔로우와 메시지 보내기를 둔다.
 * @param ownTag 내 태그. 로그인했지만 아직 모르면 undefined이고, 그동안 버튼을 숨긴다.
 */
export function ApiDeveloperResults({ items, signedIn, ownTag, onFollowChange, onFollowError }: {
    items: DeveloperSearchItem[];
    signedIn: boolean;
    ownTag: string | null | undefined;
    onFollowChange: (tag: string, result: FollowResult) => void;
    onFollowError: (message: string, status: number) => void;
}) {
    return <div className={scope('search-developer-grid')}>{items.map(item => {
        const name = followUserName(item);
        const href = `/users/${encodeURIComponent(item.tag)}`;
        const available = item.collaborationStatus === 'AVAILABLE';
        return <Card key={item.tag} className={scope('search-developer-card')}>
            <div className={scope('search-card-top')}>
                <Link href={href} aria-label={`${name} 프로필`}><ProfileAvatar name={name} color="lavender" image={profileImageUrl(item.profileImage)} /></Link>
                <span className={scope(`availability-badge ${available ? '' : 'unavailable'}`)}>{collaborationLabel(item.collaborationStatus)}</span>
            </div>
            <Link className={scope('search-person-name')} href={href}><h3>{name}</h3><ArrowUpRight size={15} /></Link>
            <span className={scope('search-person-job')}>{item.jobCategories.join(', ') || '직군 미입력'} · {formatCareer(item.career)}</span>
            <p className={scope('search-person-bio')}>{item.summary?.trim() || '아직 작성한 소개가 없습니다.'}</p>
            <div className={scope('tags')}>{item.skills.map((skill, index) => <span key={`${skill}-${index}`}>{skill}</span>)}</div>
            <div className={scope('search-person-interests')}>{item.interests.map((interest, index) => <span key={`${interest}-${index}`}>#{interest}</span>)}</div>
            <div className={scope('search-card-trust')} style={{ color: starColor(item.trustScore ?? 36.5) }}>
                <Star size={12} fill="currentColor" />{item.trustScore === null ? '—' : `${item.trustScore.toFixed(1)}°C`} <span>신뢰 온도</span>
            </div>
            <div className={scope('search-card-actions')}>
                {signedIn && ownTag === undefined ? null
                    : item.tag === ownTag ? <Link className={scope('button secondary')} href={href}>내 프로필 보기</Link>
                    : <>
                        {signedIn
                            ? <FollowButton slug={item.tag} name={name} followed={item.followed}
                                onChange={result => onFollowChange(item.tag, result)} onError={onFollowError} />
                            : <Link className={scope('button')} href="/login"><UserPlus size={13} />팔로우</Link>}
                        <StartChatButton slug={item.tag} />
                    </>}
            </div>
        </Card>;
    })}</div>;
}

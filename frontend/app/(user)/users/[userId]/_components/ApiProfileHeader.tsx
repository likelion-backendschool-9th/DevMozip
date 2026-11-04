'use client';
import { useState } from 'react';
import { CalendarDays, Clock3, Pencil, Star } from 'lucide-react';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { collaborationLabel, formatCareer, profileImageUrl } from '@/lib/profiles/profileDetail';
import { starColor } from '@/lib/profiles/profileService';
import type { ProfileDetail } from '@/types/profileDetail';
import styles from './ProfileHeader.module.css';

export function ApiProfileHeader({ profile, onEdit }: { profile: ProfileDetail; onEdit?: () => void }) {
    const [avatarFailed, setAvatarFailed] = useState(false);
    const [bannerFailed, setBannerFailed] = useState(false);
    const avatar = profileImageUrl(profile.profileImage);
    const banner = profileImageUrl(profile.bannerImage);
    const scope = (name: string) => `${styles.scope} ${name}`;
    return <section className={scope('profile-top')} aria-label="프로필 기본 정보">
        <div className={scope('profile-banner lavender')} aria-label="프로필 배너">
            {banner && !bannerFailed
                ? <img className={styles.bannerImage} src={banner} alt={`${profile.nickname} 배너 이미지`} onError={() => setBannerFailed(true)} />
                : <><span aria-hidden="true">{'{ build together. }'}</span><i /><i /></>}
        </div>
        <div className={scope('profile-identity')}>
            <ProfileAvatar name={profile.nickname} color="lavender" image={!avatarFailed ? avatar : undefined} onImageError={() => setAvatarFailed(true)} />
            <div className={scope('profile-top-actions')}>
                <button className="button secondary" disabled={!onEdit} onClick={onEdit} title={onEdit ? undefined : "본인 프로필에서 수정할 수 있어요."}><Pencil size={14} />프로필 수정</button>
            </div>
            <div className={scope('profile-heading')}>
                <h1>{profile.nickname}</h1><span>@{profile.tag}</span>
                <span className={scope(`availability-badge${profile.collaborationStatus === 'AVAILABLE' ? '' : ' unavailable'}`)}>
                    {collaborationLabel(profile.collaborationStatus)}
                </span>
            </div>
            <p className={scope('profile-job')}>{profile.jobCategory.name || '직군 미입력'} <span>·</span> {formatCareer(profile.career)}</p>
            <p className={scope('profile-bio')}>{profile.summary?.trim() || '한 줄 소개가 없습니다.'}</p>
            <div className={scope('profile-meta')}>
                <span><CalendarDays size={13} />가입일 · 조회 연결 전</span>
                <span><Clock3 size={13} />{profile.contactHours || '연락 가능 시간 미입력'}</span>
            </div>
            <div className={scope('profile-social')}>
                <button disabled title="팔로워 조회 연결 전">팔로워 <b>—</b></button>
                <button disabled title="팔로잉 조회 연결 전">팔로잉 <b>—</b></button>
                <div className={scope('trust-temperature')} style={{ color: starColor(profile.trustScore ?? 36.5) }}>
                    <Star size={17} fill="currentColor" /><strong>{profile.trustScore === null ? '—' : `${profile.trustScore.toFixed(1)}°C`}</strong><span>신뢰 온도</span>
                </div>
            </div>
        </div>
    </section>;
}

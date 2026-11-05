'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useOwnProfile } from '@/app/(user)/_components/OwnProfileProvider';
import { profileImageUrl } from '@/lib/profiles/profileDetail';
import styles from './AppLayout.module.css';

export function HeaderProfileLink() {
    const { profile, loading } = useOwnProfile();
    const [failedImage, setFailedImage] = useState<string | null>(null);
    const nickname = profile?.nickname.trim() || '';
    const image = profileImageUrl(profile?.profileImage ?? null);
    const showImage = !!image && image !== failedImage;
    const title = nickname ? `${nickname}님의 프로필` : '내 프로필로 이동';
    return <Link className={`${styles.scope} ${styles.profileLink} icon-button header-profile-link`}
        href="/profile/me" aria-label="내 프로필로 이동" title={title} aria-busy={loading || undefined}>
        <span className={`${styles.scope} small-avatar lavender`}>
            <img className={styles.headerAvatarImage} src={showImage ? image : '/profile_logo.png'}
                alt={showImage ? `${nickname} 프로필 이미지` : 'DevMozip 기본 프로필 이미지'}
                onError={showImage ? () => setFailedImage(image) : undefined} />
        </span>
    </Link>;
}

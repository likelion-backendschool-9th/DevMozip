'use client';
import Link from 'next/link';
import { adminStyles as styles } from '../_components/AdminShell';
import { temperature } from '@/lib/profiles/profileService';
import { useProfiles } from '@/lib/state/profileStore';

export default function AdminTrustScoresPage() {
    const { state: profiles } = useProfiles();

    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="신뢰 온도 관리">
            <h2>신뢰 온도 현황</h2>
            <p>초기값 36.5°C · 기존 협업 리뷰 기준의 Mock 계산입니다.</p>
            {Object.values(profiles.profiles).map(p => (
                <div className={[styles.scope, 'admin-row'].join(' ')} key={p.id}>
                    <Link href={`/users/${p.id}?tab=reviews`}>{p.nickname}</Link>
                    <b>{temperature(profiles.reviews, p.id).toFixed(1)}°C</b>
                </div>
            ))}
        </section>
    );
}

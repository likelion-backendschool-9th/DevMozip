'use client';
import Link from 'next/link';
import { adminStyles as styles } from '../../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';

/** Mock 토글 ON의 프리미엄 구독 현황. 데모 계정과 결제 예시를 보여 준다. */
export function MockAdminSubscriptions() {
    const { publicUsers, administration } = useAuth();
    const subscribed = publicUsers.filter(u => u.role === 'premium');

    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="구독 현황 관리">
            <h2>프리미엄 구독 현황</h2>
            <p>프리미엄 {subscribed.length}명 · 실제 청구 금액 ₩0</p>
            {publicUsers
                .filter(u => u.role !== 'admin')
                .map(u => (
                    <div className={[styles.scope, 'admin-row'].join(' ')} key={u.id}>
                        <Link href={`/users/${u.id}`}>{u.nickname}</Link>
                        <span>
                            {u.role === 'premium' ? '구독 활성' : '무료 / 해지'} ·{' '}
                            {administration.subscriptions[u.id]?.updatedAt.slice(0, 10) || '기본 데모 상태'}
                        </span>
                    </div>
                ))}
            <h3>결제 내역 예시 / 데모 전환 기록</h3>
            {administration.payments.map(p => (
                <div className={[styles.scope, 'admin-row'].join(' ')} key={p.id}>
                    <span>
                        {p.date} · {publicUsers.find(u => u.id === p.userId)?.nickname}
                    </span>
                    <span>
                        {p.status} · ₩{p.amount.toLocaleString()}
                    </span>
                </div>
            ))}
        </section>
    );
}

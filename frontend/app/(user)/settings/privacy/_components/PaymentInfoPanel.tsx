'use client';
import styles from '../page.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { PaymentHistoryList } from '@/app/subscription/history/_components/PaymentHistoryList';
import { fetchMySubscription, fetchPaymentHistory, fetchPaymentMethod } from '@/lib/subscription/subscriptionClient';
import { STATUS_LABELS, isCurrentSubscription } from '@/lib/subscription/subscriptionView';
import type { MySubscription, PaymentHistoryItem, PaymentMethodSummary } from '@/types/subscription';

const RECENT_COUNT = 3;
const LOAD_ERROR = '결제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

/** 개인정보 설정의 결제 정보(17.4), 결제 수단(17.5), 구독 정보(17.6). 구독 관리 페이지와 같은 값을 보여 준다 */
export function PaymentInfoView({ role, subscription, paymentMethod, payments }: {
    role: string | undefined;
    subscription: MySubscription;
    paymentMethod: PaymentMethodSummary | null;
    payments: PaymentHistoryItem[];
}) {
    const current = isCurrentSubscription(subscription.status);
    const label = role === 'admin' ? '관리자' : current ? STATUS_LABELS[subscription.status] : subscription.premium ? '프리미엄 이용 중' : '무료 요금제';
    return (
        <>
            <dl className={[styles.scope, 'account-details'].join(' ')}>
                <div>
                    <dt>현재 구독</dt>
                    <dd>{label}</dd>
                </div>
                <div>
                    <dt>결제 수단</dt>
                    <dd>{paymentMethod?.display ?? '등록된 결제 수단 없음'}</dd>
                </div>
            </dl>
            <h3>최근 결제 내역</h3>
            <PaymentHistoryList items={payments.slice(0, RECENT_COUNT)} emptyText="표시할 결제 내역이 없습니다." />
        </>
    );
}

export function PaymentInfoPanel({ role }: { role: string | undefined }) {
    const [data, setData] = useState<{ subscription: MySubscription; paymentMethod: PaymentMethodSummary | null; payments: PaymentHistoryItem[] } | null>(null);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        Promise.all([fetchMySubscription(), fetchPaymentMethod(), fetchPaymentHistory(0)])
            .then(([subscription, paymentMethod, history]) => { if (active) setData({ subscription, paymentMethod, payments: history.items }); })
            .catch(() => { if (active) setError(LOAD_ERROR); });
        return () => { active = false; };
    }, []);
    return (
        <section className={[styles.scope, 'account-panel'].join(' ')}>
            <h2>결제 정보</h2>
            {error ? (
                <p className={[styles.scope, 'form-error'].join(' ')} role="alert">{error}</p>
            ) : !data ? (
                <p className={[styles.scope, 'auth-hint'].join(' ')} role="status">결제 정보를 불러오고 있어요…</p>
            ) : (
                <PaymentInfoView role={role} {...data} />
            )}
            <div className={[styles.scope, 'governance-actions'].join(' ')} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 16 }}>
                <Link className={[styles.scope, 'button secondary'].join(' ')} href="/subscription/manage">구독 관리</Link>
                <Link className={[styles.scope, 'button secondary'].join(' ')} href="/subscription/history">전체 결제 내역</Link>
            </div>
        </section>
    );
}

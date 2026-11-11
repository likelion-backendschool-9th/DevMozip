'use client';
import { useState } from 'react';
import Link from 'next/link';
import { AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { adminTime } from '@/lib/administration/adminApi';
import {
    formatMoney, parsePaymentPage, parseSubscriptionPage, paymentStatuses, paymentStatusLabels, subscriptionStatuses, subscriptionStatusLabels,
} from '@/lib/administration/adminBilling';
import { partyName } from '@/lib/administration/adminReports';
import { useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminParty, AdminPayment, AdminPaymentSummary, AdminSubscription, AdminSubscriptionSummary, AdminSummaryPage } from '@/types/adminApi';

/** Mock 토글 OFF의 구독·결제 관리 (WBS 14.12·14.13). 조회만 하며 환불·취소는 없다. */
export function ApiAdminSubscriptions() {
    const [subscriptionStatus, setSubscriptionStatus] = useState('');
    const [subscriptionPage, setSubscriptionPage] = useState(0);
    const [paymentStatus, setPaymentStatus] = useState('');
    const [paymentPage, setPaymentPage] = useState(0);
    const subscriptionQuery = new URLSearchParams({ page: String(subscriptionPage), size: '20' });
    if (subscriptionStatus) subscriptionQuery.set('status', subscriptionStatus);
    const paymentQuery = new URLSearchParams({ page: String(paymentPage), size: '20' });
    if (paymentStatus) paymentQuery.set('status', paymentStatus);
    const subscriptions = useAdminResource(`/subscriptions?${subscriptionQuery}`, parseSubscriptionPage);
    const payments = useAdminResource(`/payments?${paymentQuery}`, parsePaymentPage);

    return (
        <section className={cls('governance-panel admin-main')} aria-label="구독 현황 관리">
            <h2>프리미엄 구독 현황</h2>
            <p>포트원으로 결제한 구독과 결제 기록입니다. 이 화면에서는 조회만 하며 환불·해지를 하지 않습니다. 탈퇴한 회원의 기록도 남아 있습니다.</p>
            <div className={cls('admin-filters')}>
                <label>
                    구독 상태
                    <select className="form-input" value={subscriptionStatus} onChange={e => { setSubscriptionStatus(e.target.value); setSubscriptionPage(0); }}>
                        <option value="">전체</option>
                        {subscriptionStatuses.map(s => <option key={s} value={s}>{subscriptionStatusLabels[s]}</option>)}
                    </select>
                </label>
            </div>
            <SubscriptionsView load={subscriptions.load} onRetry={subscriptions.reload} onPage={setSubscriptionPage} />
            <h3>결제 내역</h3>
            <div className={cls('admin-filters')}>
                <label>
                    결제 상태
                    <select className="form-input" value={paymentStatus} onChange={e => { setPaymentStatus(e.target.value); setPaymentPage(0); }}>
                        <option value="">전체</option>
                        {paymentStatuses.map(s => <option key={s} value={s}>{paymentStatusLabels[s]}</option>)}
                    </select>
                </label>
            </div>
            <PaymentsView load={payments.load} onRetry={payments.reload} onPage={setPaymentPage} />
        </section>
    );
}

const memberLink = (member: AdminParty) => member.tag ? <Link href={`/users/${member.tag}`}>{partyName(member)}</Link> : <b>{partyName(member)}</b>;

export function SubscriptionsView({ load, onRetry, onPage }:
    { load: AdminLoad<AdminSummaryPage<AdminSubscriptionSummary, AdminSubscription>>; onRetry: () => void; onPage: (page: number) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry} loadingText="구독을 불러오고 있어요.">
            {data => (
                <>
                    <div className={cls('admin-summary')}>
                        <span className="demo-pill">이용 기간 안의 구독 {data.summary.current}건</span>
                        {subscriptionStatuses.map(s => <span className="demo-pill" key={s}>{subscriptionStatusLabels[s]} {data.summary.statusCounts[s]}</span>)}
                    </div>
                    {!data.items.length && <p>조건에 맞는 구독이 없습니다.</p>}
                    {data.items.map(item => (
                        <article className={cls('admin-row')} key={item.id}>
                            <div>
                                {memberLink(item.member)}
                                <small>{item.planName} · {subscriptionStatusLabels[item.status]} · 시작 {adminTime(item.startsAt)} · 기간 끝 {adminTime(item.currentPeriodEnd)}</small>
                                {(item.cancelRequestedAt || item.endedAt) && <small>해지 요청 {adminTime(item.cancelRequestedAt)} · 종료 {adminTime(item.endedAt)}</small>}
                            </div>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

export function PaymentsView({ load, onRetry, onPage }:
    { load: AdminLoad<AdminSummaryPage<AdminPaymentSummary, AdminPayment>>; onRetry: () => void; onPage: (page: number) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry} loadingText="결제 내역을 불러오고 있어요.">
            {data => (
                <>
                    <div className={cls('admin-summary')}>
                        {data.summary.paidTotals.length
                            ? data.summary.paidTotals.map(t => <span className="demo-pill" key={t.currency}>결제 완료 합계 {formatMoney(t.amount, t.currency)}</span>)
                            : <span className="demo-pill">결제 완료 합계 ₩0</span>}
                    </div>
                    {!data.items.length && <p>조건에 맞는 결제가 없습니다.</p>}
                    {data.items.map(item => (
                        <article className={cls('admin-row')} key={item.id}>
                            <div>
                                {memberLink(item.member)}
                                <small>{paymentStatusLabels[item.status]} · {item.method ?? item.provider} · 주문 {item.orderId} · 결제 {adminTime(item.paidAt)}</small>
                            </div>
                            <span>{formatMoney(item.amount, item.currency)}</span>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

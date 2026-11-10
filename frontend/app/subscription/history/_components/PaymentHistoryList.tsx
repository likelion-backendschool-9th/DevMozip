import styles from '../../_components/SubscriptionPage.module.css';
import { PAYMENT_STATUS_LABELS, formatDateTime, formatWon } from '@/lib/subscription/subscriptionView';
import type { PaymentHistoryItem, PaymentStatus } from '@/types/subscription';

const badgeClass: Partial<Record<PaymentStatus, string>> = { PAID: styles.badgePaid, FAILED: styles.badgeFailed };

/** 결제 내역 목록(13.5, 17.4). 비어 있으면 안내 문구를 보여 준다 */
export function PaymentHistoryList({ items, emptyText = '결제 내역이 없습니다.' }: { items: PaymentHistoryItem[]; emptyText?: string }) {
    if (!items.length) return <p>{emptyText}</p>;
    return (
        <ul className={styles.history}>
            {items.map(item => (
                <li key={item.id}>
                    <time dateTime={item.occurredAt}>{formatDateTime(item.occurredAt)}</time>
                    <span>{item.planName} · {item.paymentMethod}</span>
                    <span className={[styles.badge, badgeClass[item.status] ?? ''].join(' ')}>{PAYMENT_STATUS_LABELS[item.status]}</span>
                    <b>{formatWon(item.amount)}</b>
                </li>
            ))}
        </ul>
    );
}

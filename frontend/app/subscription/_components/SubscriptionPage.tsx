import styles from './SubscriptionPage.module.css';
import type { ReactNode } from 'react';
import { SubscriptionTabs } from './SubscriptionTabs';

/** 결제·구독 관리·결제 내역 페이지의 공통 머리말과 탭 */
export function SubscriptionPage({ eyebrow, title, description, tab, children }: {
    eyebrow: ReactNode;
    title: string;
    description: string;
    tab?: string;
    children: ReactNode;
}) {
    return (
        <div className={styles.page}>
            <header className={styles.heading}>
                <span className={styles.eyebrow}>{eyebrow}</span>
                <h1>{title}</h1>
                <p>{description}</p>
            </header>
            {tab && <SubscriptionTabs current={tab} />}
            {children}
        </div>
    );
}
